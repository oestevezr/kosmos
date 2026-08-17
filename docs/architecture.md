# Atlas City — Arquitectura (Fase 1)

Este documento fija las reglas arquitectónicas establecidas en el scaffold. Es normativo: un
cambio que las viole debe pasar por una revisión explícita, no colarse en un PR de features.

Referencias `§N` apuntan a las secciones de `spec_procedural_citybuilder(3).md`.

## 1. Aislamiento de `simulation-core`

`simulation-core` es Java puro. No puede depender de libGDX, LWJGL, OpenGL ni APIs de Android
(§37). Esto se aplica mecánicamente: la tarea Gradle `checkCoreIsolation` (ver `build.gradle.kts`
raíz) falla el build si aparece cualquier dependencia con "badlogicgames", "lwjgl", "android" o
"libgdx" en su classpath de compilación.

La consecuencia práctica: la simulación completa corre en `headless-runner` sin ventana, sin
GPU y sin audio. Esto es lo que permite simular décadas a velocidad máxima para benchmarking y
balance económico (§35).

## 2. Determinismo

`seed + generatorVersion + chunkCoord` produce siempre el mismo chunk, en cualquier hilo, en
cualquier orden (§32). Esto se logra con:

- `SplitMix64` sin estado global — nunca `java.util.Random`.
- `RngStream` deriva cada valor directamente de sus coordenadas; no hay RNG secuencial compartido
  entre chunks.
- `DeterministicNoise` usa `StrictMath` en vez de `Math`, porque el JLS solo garantiza resultados
  bit-idénticos entre plataformas para `StrictMath`.

`GoldenChunkHashTest` fija un hash de referencia para un seed y coordenada concretos. Si falla
tras un cambio al generador, ese cambio invalidó silenciosamente todos los saves existentes —
la respuesta correcta es subir `GeneratorVersion.CURRENT`, nunca "arreglar" el hash sin más.

## 3. Layout de datos

Todo el estado por-tile vive en arrays primitivos paralelos (Structure of Arrays), nunca en
`class Tile` ni en un objeto por ciudadano/viaje (§42.1, §42.2). `ChunkPool` recicla instancias de
`Chunk` para que cargar/descargar chunks en régimen permanente no asigne memoria.

## 4. Modelo de hilos

```
RENDER THREAD   input, cámara, UI, culling, draw
SIM THREAD      tick fijo, comandos, scheduler
WORLD WORKERS   generación procedural (ChunkManager)
I/O WORKER      guardado (futuro: async explícito; hoy el guardado es síncrono y debe moverse
                a un executor dedicado antes de exponerlo en el bucle de render)
```

Comunicación por colas (`CommandBus` SPSC, cola de resultados de `ChunkManager` MPSC), nunca por
locks globales de grano grueso (§40).

## 5. Comandos como única vía de mutación

El cliente nunca muta el estado de simulación directamente (§38). Construye un `Command`, lo
envía por `CommandBus`, y el núcleo lo valida y aplica (o rechaza) en `Command.apply(...)`. Esta
misma validación se ejecuta en vivo y al reproducir el `CommandJournal` — no hay una ruta de
validación "solo para UX" separada de la autoritativa.

## 6. Persistencia

Solo se guardan los chunks modificados (`Chunk.isDirty()`); el terreno virgen se regenera desde
la semilla (§10). Formato binario propio, nunca `ObjectOutputStream`. Ver la sección de seguridad
más abajo.

## 7. Seguridad

Un save es entrada no confiable desde el momento en que puede haberse corrompido en un crash o
haber sido editado a mano. Controles aplicados en `sim.persistence`:

1. **Sin serialización nativa de Java.** Lector/escritor binario explícito en
   `BinaryBlockIO`/`WorldMeta`/`ChunkDeltaIO`. Elimina CWE-502 por construcción.
2. **Longitudes validadas antes de asignar memoria.** `BinaryBlockIO.readBlock` rechaza cualquier
   longitud declarada mayor que `maxLength` antes de crear el array — un save manipulado no puede
   forzar una asignación de gigabytes. Ver `SaveCorruptionFuzzTest`.
3. **CRC32C por bloque + magic + versión de formato.** Cualquier corrupción produce
   `SaveCorruptedException`, nunca una carga parcial silenciosa.
4. **Sanitización de nombre de mundo (CWE-22).** `WorldNameValidator` aplica una lista blanca
   `[A-Za-z0-9 _-]{1,64}` y además verifica que la ruta resuelta siga dentro de la raíz de saves,
   como segunda capa independiente.
5. **Escritura atómica.** `AtomicFileWriter`: escribe a `.tmp`, hace `fsync`, y solo entonces
   renombra de forma atómica sobre el fichero final. Un crash a mitad de guardado nunca corrompe
   el save anterior.
6. **La validación de comandos vive en el núcleo, no en la UI.** Es la base para anti-cheat y para
   un futuro modo multijugador — cualquier chequeo del lado del cliente es solo UX, no seguridad.
7. **Sin red, sin telemetría, sin ejecución de código externo en Fase 1.** El sistema de mods
   queda explícitamente fuera de alcance; cuando se implemente, debe hacerlo sandboxeado y sin
   acceso a `java.io`.
8. **Cadena de suministro.** Versiones fijadas en `gradle/libs.versions.toml`. Sin plugins de
   terceros más allá de libGDX y JMH.

Extensión pendiente y explícitamente anotada: si se añade compresión (LZ4/Zstd) a los saves, debe
llevar un límite de ratio de descompresión para evitar una zip bomb.

## 8. Qué queda fuera de Fase 1 (a propósito)

Población, economía, edificios, carreteras jugables, ríos, puertos naturales, módulo Android,
sistema de mods, compresión de saves. El corte de módulos ya deja sitio para cada uno sin
necesidad de reestructurar el proyecto.

## 9. Fase 2 — Basic City (MVP 0.2, §33)

Añade zonificación, carreteras, electricidad/agua, población y empleo agregados por edificio, e
impuestos básicos, siempre sobre la arquitectura de Fase 1 — sin abrir ningún atajo que la viole.

### 9.1 Capas nuevas en `Chunk`

`zoneType`, `roadType`, `buildingId`, `serviceFlags` — arrays paralelos igual que las capas
naturales (§42.1/§42.2). A diferencia de las capas naturales, nada las regenera desde la semilla:
`Chunk.reset(...)` debe limpiarlas explícitamente para que un chunk reciclado del pool no filtre
carreteras/edificios de su ocupante anterior a una coordenada virgen.

### 9.2 Estado derivado vs. estado autoritativo

`serviceFlags` (acceso a carretera, electricidad, agua) es **estado derivado**, recalculado por
`RoadNetwork` y `UtilitySystem` a partir de carreteras/edificios (que sí son autoritativos).
Recalcularlo **nunca** llama a `Chunk.markDirty()/markMutated()` — si lo hiciera, cada pasada se
auto-invalidaría en la siguiente (bucle de recomputación infinito) y forzaría al caché de render a
reconstruir chunks cuyo aspecto visual no cambió. Regla general: si un sistema puede reconstruir
un valor íntegramente a partir de otro estado autoritativo, ese valor no es autoritativo y no debe
disparar las mismas señales de "dirty" que una edición del jugador.

### 9.3 `BuildingRegistry`: agregados por edificio, no por ciudadano

Arrays SoA crecientes (`population`, `jobs`, `incomeLevel`, `employmentRatePercent`,
`satisfactionPercent`) indexados por un id estable de 1 hilera (§22, §42.3). `Chunk.buildingId`
guarda ese id directamente — nunca una referencia a objeto. Demoler tumba el slot y lo empuja a
una lista libre para reutilización; los ids activos nunca se renumeran.

### 9.4 Redes tipo grafo (§24)

- `RoadNetwork`: acceso por adyacencia directa (4 vecinos), con seguimiento de "dirty" por versión
  de chunk vía `LongIntHashMap` — un chunk sin ediciones desde la última pasada se salta entero.
- `UtilitySystem`: flood-fill multi-fuente acotado (`MAX_RANGE_TILES`) desde cada planta eléctrica
  / torre de agua activa. No es incremental por chunk (una fuente nueva puede iluminar chunks que
  no cambiaron) — recompute completo del área cargada a cadencia baja, documentado como candidato
  de optimización tras perfilar con una ciudad real (§49).

Ambos son deliberadamente **locales**, no el grafo regional de nodos/aristas de §13.2 — ese grafo
pertenece a la fase de carga/transporte regional (§13, §14).

### 9.5 Asentamiento y crecimiento sin scripts (§9, §23)

`PopulationSystem` nunca coloca un edificio por decisión propia fuera de una regla observable: una
parcela zonificada con carretera + electricidad + agua genera un edificio semilla; el crecimiento
posterior está acoplado bidireccionalmente (residencial crece si hay empleo disponible en la
ciudad, comercial/industrial crecen si hay mano de obra) — un bucle de realimentación simple pero
real, no una tabla de niveles.

### 9.6 Comandos nuevos

`BuildRoadCommand`, `ZoneCommand`, `DemolishCommand`, `BuildPowerPlantCommand`,
`BuildWaterTowerCommand`, `SetTaxPolicyCommand` — todos bajo `sim.commands.city`, con ids de tipo
10–15 (los de terreno de Fase 1 usan 1–3; el hueco es intencional para futuras familias de
comandos). `SimulationContext` ahora puede portar opcionalmente un `BuildingRegistry` y un
`GovernmentFinance`; un comando que los necesita llama a `requireBuildings()`/`requireFinance()`
en vez de asumir que están presentes, para que un contexto de solo-terreno (p. ej. un benchmark
headless) siga siendo válido sin ellos.

### 9.7 Persistencia

`ChunkDeltaIO` sube a `FORMAT_VERSION = 2` (nuevo `EXPECTED_PAYLOAD_LENGTH`) para las cuatro capas
nuevas — un save de Fase 1 no es compatible con este formato, tal como exige el principio de
versionado explícito de §32 aplicado a los formatos de guardado. `BuildingRegistryIO` persiste
`settlements.dat` (listado en §31) con el mismo patrón magic+versión+bloque CRC32C que el resto de
la persistencia; los ids demolidos se serializan como tumbas para que la lista libre se reconstruya
correctamente al cargar.

### 9.8 Qué queda fuera de Fase 2 (a propósito)

Declive/emigración de edificios no atendidos, grafo regional de transporte, recursos naturales
alimentando industria (§7), tráfico visible, mercado externo (§29), UI de construcción en el
cliente (`game-client` aún no dibuja carreteras/zonas/edificios — sigue mostrando solo terreno).

## 10. Pasada de validación y optimización (post-Fase 2)

Revisión dirigida con `-Xlint:all` (limpio en los cinco módulos), intento de diagnósticos vía
`jdtls-lsp`, y auditoría manual de rutas calientes contra las reglas de §42. Tres problemas reales
encontrados y corregidos, verificados con la suite completa (53 tests) antes y después de cada uno:

1. **El build no era reproducible sin `JAVA_HOME` exportado a mano.** El JDK 17 de Homebrew es
   keg-only; sin él en el `PATH`, Gradle no podía resolver el toolchain (`Cannot find a Java
   installation... No locally installed toolchains match`). Reproducido con `unset JAVA_HOME &&
   ./gradlew compileJava` antes del fix. Corregido añadiendo el plugin
   `org.gradle.toolchains.foojay-resolver-convention` en `settings.gradle.kts`, que permite a
   Gradle auto-descubrir o auto-descargar un JDK 17 en cualquier máquina — sin rutas absolutas
   hardcodeadas. Verificado con `unset JAVA_HOME && ./gradlew clean build` en verde.
   *Pendiente, no corregido*: la JVM que arranca el propio daemon de Gradle (distinta del
   toolchain de compilación) sigue tomando el `java` por defecto del `PATH`; si ese es < 17,
   Gradle 8.10 avisa que Gradle 9 lo rechazará. No se hardcodeó una ruta para esto — requiere que
   la máquina tenga un JDK 17+ como `java` por defecto, o `JAVA_HOME` exportado.

2. **`ChunkManager.updateFocus` asignaba memoria en cada frame de render**, sin importar si la
   cámara había cambiado de chunk. `AtlasGame.render()` lo llama incondicionalmente cada frame; el
   método construía un `ArrayList<long[]>` nuevo y boxeaba cada coordenada candidata a expulsión
   en cada llamada — el ejemplo más claro en el proyecto de una ruta que sí es "caliente" en el
   sentido de spec §42.4 y no lo estaba tratando como tal. Corregido con (a) un `return` temprano
   cuando el chunk de foco no cambió desde la última llamada, y (b) buffers `int[]` reutilizados
   (dimensionados una vez a la capacidad del `ChunkStore`) en vez del `ArrayList<long[]>`, usando
   un `ChunkStore.ChunkVisitor` como campo de instancia único en vez de una lambda nueva por
   llamada. Cubierto por `ChunkManagerTest` (nuevo).

3. **`PopulationSystem`/`GovernmentFinanceSystem` boxeaban en cada tick.** Ambos acumulaban
   totales de ciudad dentro de una lambda de `BuildingRegistry.forEachActive`, lo que forzaba el
   truco clásico de "array de una celda" (`long[] x = {0}`) para sortear que Java no permite
   capturar variables locales mutables — tres arrays más una lambda por llamada, en un sistema que
   corre en un ciclo recurrente (cada 10 y 50 ticks respectivamente). Corregido reemplazando el
   `forEachActive` por un bucle indexado directo sobre `highWaterMark()`/`isActive(id)` (ambos ya
   públicos), eliminando tanto los arrays como la lambda. Bloqueado contra regresión por
   `PopulationAndFinanceAllocationTest` (nuevo), que mide bytes/llamada con
   `ThreadMXBean.getThreadAllocatedBytes` igual que `AllocationBudgetTest`.
   *Nota de alcance*: `growExistingBuildings` y `settleEmptyZonedTiles` siguen usando
   `forEachActive`/`store.forEach` con lambdas capturadoras — una instancia por llamada de
   sistema, no por edificio/chunk, así que el costo es órdenes de magnitud menor que el patrón
   corregido arriba y se deja como está.

### 10.1 Recomendaciones implementadas en una segunda pasada

Las cuatro propuestas que quedaron pendientes en la primera pasada se implementaron a
continuación, en el mismo orden de prioridad, y se verificaron con la suite completa (60 tests:
53 de `simulation-core` + 5 de `game-client` nuevos + 2 de asignación nuevos) más un lanzamiento
real del cliente desktop y una re-ejecución del escenario `--bench city` del headless-runner.

1. **`UtilitySystem` ya no reconstruye estructuras en cada recomputo.** `ArrayDeque<Long>` y
   `HashMap<Long,Integer>` reemplazados por `LongIntHashMap` (reutilizado, ya existía en
   `sim.util`) y un `LongQueue` circular de `long[]` nuevo, ambos como campos de instancia que
   solo crecen — nunca se encogen — así que tras un puñado de llamadas de calentamiento dejan de
   asignar memoria por completo. Bloqueado contra regresión por `UtilitySystemAllocationTest`
   (nuevo). Efecto medido de punta a punta: el mismo escenario `--bench city --years 50` pasó de
   **13.55 s a 0.98 s** de tiempo real (13.8×) solo con este cambio más las dos optimizaciones de
   la primera pasada — la prueba concreta de que el flood-fill full-recompute era, en efecto, el
   costo dominante del sistema, tal como sugería la intuición original.
2. **Cuatro benchmarks JMH nuevos** (`RoadNetworkBenchmark`, `UtilitySystemBenchmark`,
   `PopulationSystemBenchmark`, `GovernmentFinanceBenchmark`) sobre una ciudad representativa
   construida por `BenchmarkCityFixture` (retícula de carreteras + zonas + fuentes de servicios,
   asentada y crecida antes de medir). Cifras de referencia a esa escala (5×5 chunks): `RoadNetwork`
   0.06 µs/op en estado estable frente a 369 µs/op en escaneo completo (~6500× — cuantifica
   exactamente lo que spec §41 llama "trabajo evitado"); `UtilitySystem` ~2.4 ms/op (confirmado
   como el sistema más caro, como anticipaba la nota de la primera pasada); `PopulationSystem`
   ~43 µs/op; `GovernmentFinance` ~3 µs/op.

   **Actualización tras contaminación + densidad evolutiva** (`docs/roadmap.md`): re-medido en la
   misma `BenchmarkCityFixture`, con JDK 17 real en el `PATH` (antes el fork de JMH fallaba en esta
   máquina por un JDK 11 desalineado con el toolchain — ver la nota de toolchain más abajo).
   `UtilitySystem.update` da **2334.5 µs/op**, sin cambio perceptible pese a la pasada de
   contaminación nueva (reutiliza el mismo BFS/frontier, no agrega un recorrido extra). `PopulationSystem.tick`
   sube a **103.5 µs/op** (de ~43 µs/op) — el costo real de `updateDensityLevel` evaluándose por
   cada edificio activo cada tick; sigue muy por debajo del presupuesto de sub-sistema (spec §41),
   no ameritó una pasada de optimización.

   **Actualización tras migración (MVP 0.6, primera pasada)**: `PopulationSystem.tick` da
   **104.96 µs/op** — sin cambio perceptible frente a la cifra de densidad evolutiva;
   `migrationMultiplier` es un loop de 8 iteraciones (`GoodType.COUNT`) que solo corre por cada tile
   que se asienta, no por cada edificio activo cada tick, así que su costo queda dentro del ruido de
   medición.

   **Actualización tras rail/rutas de autobús/turismo (MVP 0.6, segunda pasada)**:
   `UtilitySystem.update` da **2521.1 µs/op** (antes 2334.5 µs/op) y `PopulationSystem.tick` da
   **104.6 µs/op** (sin cambio). El aumento de `UtilitySystem` es plausible pero no concluyente con
   una sola iteración de warmup/medición (`-wi 1 -i 1`) — la nueva pasada de cobertura de tránsito
   solo corre cuando existe al menos un `BusRouteRegistry` no nulo con paradas activas, ausente en
   `BenchmarkCityFixture`; el aumento observado cae dentro del ruido esperable de una corrida de una
   sola muestra, no se investigó más a fondo por no bloquear la entrega. Pendiente: una corrida con
   más iteraciones (`-wi 5 -i 5`) si esta cifra se vuelve relevante más adelante.
3. **`WorldRenderer` ahora usa un `Mesh` estático por chunk** (`ChunkMesh`, nuevo) en vez de 1024
   llamadas a `SpriteBatch.draw` reenviadas cada frame — la caché de render de spec §44.3 tal cual
   está descrita, no solo posiciones de pantalla cacheadas en CPU. El vértice/UV se sube a la GPU
   una vez por chunk, solo cuando `chunk.version()` cambia; el shader (`SpriteBatch.createDefaultShader()`,
   reutilizado en vez de escribir uno a mano) y el bind de textura del atlas se hacen una vez por
   frame, fuera del bucle de chunks. `ChunkRenderCache` ahora también libera (`dispose()`) el mesh
   GPU de cada chunk expulsado — el código anterior no liberaba nada al expulsar porque
   `ChunkRenderData` no tenía recursos de GPU; con `Mesh` real, no liberar habría sido una fuga de
   memoria de GPU real, no solo de heap Java.
   *Verificación*: confirmado visualmente con `screencapture` una vez habilitado el permiso de
   grabación de pantalla — terreno isométrico dibujado correctamente (el característico patrón en
   escalera de diamantes), 60 FPS estables, `visible chunks: 4 / drawn tiles: 4096` en el overlay
   de debug coincidiendo con lo esperado, sin artefactos ni texturas volteadas. Confirma que el
   orden de vértices/UV elegido para `ChunkMesh` (bottom-left/top-left/top-right/bottom-right con
   `v`/`v2`, replicando `SpriteBatch.draw(TextureRegion, x, y, w, h)`) es correcto. También se
   verificó que el proceso corre sin excepciones y se apaga limpio, y `IsoProjectionTest` (nuevo)
   cubre automáticamente, sin necesidad de GPU, que la proyección/inversa que `ChunkMesh` usa para
   posicionar cada tile es matemáticamente correcta en un rango de coordenadas.
4. **`IsoProjectionTest`** (nuevo, `game-client/src/test`) — primer test automatizado del módulo
   `game-client`. Cubre proyección de origen, desplazamiento este/sur, elevación, y round-trip
   proyección→inversa en una rejilla de coordenadas.

El punto sobre el JDK del daemon de Gradle (JVM que arranca Gradle en sí, distinta del toolchain
de compilación) sigue documentado como limitación conocida en el punto 1 — no se resolvió con una
ruta hardcodeada a propósito.
