# CLAUDE.md — Atlas City (KOSMOS)

City-builder isométrico 2D sobre un mundo regional procedural, en Java 17 + Gradle + libGDX.
Núcleo de simulación determinista y agnóstico de renderizado; cliente libGDX como capa delgada
de presentación. Ver la visión completa en `spec_procedural_citybuilder(3).md` — **no hace falta
releerlo entero para trabajar en el proyecto**, ver la sección "Qué leer y cuándo" abajo.

## Qué leer y cuándo (evita releer todo el contexto)

| Necesitas... | Lee esto, no el resto |
|---|---|
| Reglas de arquitectura ya decididas y por qué (aislamiento del core, estado derivado vs autoritativo, versionado de saves, hilos) | `docs/architecture.md` |
| Qué se ha optimizado, con qué cifras medidas, y qué falta por optimizar | `docs/architecture.md` §10 (pasada de validación) |
| Plan de las próximas fases (MVP 0.3 en adelante) y las decisiones de secuencia ya tomadas | `docs/roadmap.md` |
| Visión completa del juego, secciones no implementadas aún (rios, puertos, tráfico, política...) | `spec_procedural_citybuilder(3).md` — grep por `^# ` para ir a la sección exacta, no lo leas entero |
| Estado actual del proyecto (qué fases están hechas) | Este archivo, sección "Estado actual" |

Regla práctica: si la pregunta es "¿por qué se hizo así?" o "¿qué se decidió?", la respuesta casi
siempre está en `docs/architecture.md` o `docs/roadmap.md`, no en releer el spec ni en re-explorar
el código fuente.

## Estado actual

- **Fase 1 (MVP 0.1 — Terrain Sandbox)**: completa. Mundo procedural determinista, streaming de
  chunks, cámara isométrica, comandos de terreno, guardado binario versionado, benchmarks.
- **Fase 2 (MVP 0.2 — Basic City)**: completa. Zonificación, carreteras (acceso local),
  electricidad/agua (grafo de alcanzabilidad), población/empleo agregados por edificio, impuestos.
  El cliente (`game-client`) todavía **no dibuja** zonas/carreteras/edificios — solo terreno.
- **Pasada de validación/optimización post-Fase 2**: completa. Ver `docs/architecture.md` §10.
- **MVP 0.3 (Regional Economy) en adelante**: solo planificado (`docs/roadmap.md`), sin código.
  Hay 3 preguntas abiertas ahí que conviene resolver antes de implementar (alcance del
  `TradeDepot`, cuántos bienes desde el arranque, `PortRegistry` vs columnas en `BuildingRegistry`).

## Estructura del proyecto

```
simulation-core/   Java puro. CERO dependencias de libGDX/LWJGL/Android — regla forzada por la
                    tarea Gradle `checkCoreIsolation` (falla el build si se viola).
  sim/world/        Chunks SoA, streaming (ChunkManager), terreno, generación procedural (gen/)
  sim/commands/     Command bus, journal, comandos de terreno (terrain/) y de ciudad (city/)
  sim/population/   BuildingRegistry (agregados por edificio), PopulationSystem
  sim/transport/    RoadNetwork (acceso local por adyacencia — NO es el grafo regional)
  sim/utility/      UtilitySystem (flood-fill de electricidad/agua)
  sim/economy/      GovernmentFinance, GovernmentFinanceSystem
  sim/persistence/  Formato binario propio (magic+versión+CRC32C+escritura atómica), nunca
                    ObjectOutputStream. Un archivo por tipo de estado (world.meta, chunks/*.delta,
                    settlements.dat, ...)
  sim/util/         LongIntHashMap, Histogram — primitivas sin boxing para las rutas calientes

game-client/        libGDX. render/ (IsoProjection, ChunkMesh, WorldRenderer), camera/, ui/,
                    presentation/ (AtlasGame). Depende de simulation-core, nunca al revés.
platform-desktop/   Entry point LWJGL3. Casi vacío a propósito.
headless-runner/    CLI que corre WorldManager sin gráficos. `--bench chunkgen|city|smoke`.
benchmark/          JMH. Un archivo por sistema medido (ChunkGeneration, Noise, LongIntHashMap,
                    RoadNetwork, UtilitySystem, PopulationSystem, GovernmentFinance).
docs/                architecture.md (reglas + validación), roadmap.md (plan MVP 0.3+)
```

## Flujo de trabajo

```bash
./gradlew build                                  # compila todo + tests + checkCoreIsolation
./gradlew :simulation-core:test                   # solo tests del core (los más importantes)
./gradlew :platform-desktop:run                   # abre el cliente visual
./gradlew :headless-runner:run --args="--seed 819234 --size medium --bench city --years 100"
./gradlew :benchmark:jmh                          # todos los benchmarks (tarda varios minutos)
./gradlew :benchmark:jmh --args="-wi 1 -i 1 -f 1 NombreDelBenchmark"   # pasada rápida de verificación
```

**Toolchain**: JDK 17 vía `org.gradle.toolchains.foojay-resolver-convention` en
`settings.gradle.kts` — el build se auto-resuelve en cualquier máquina, no hace falta exportar
`JAVA_HOME` a mano para compilar. Excepción conocida: la JVM que arranca el **daemon** de Gradle
(no el toolchain de compilación) sigue tomando el `java` por defecto del `PATH`; si es < 17,
Gradle avisa pero funciona en 8.10. Si algún día se sube a Gradle 9, hará falta un JDK 17+ como
`java` por defecto en la máquina.

**Verificación visual del cliente**: lanzar con `./gradlew :platform-desktop:run` en background y
usar `screencapture -x` — el proceso `java` no siempre expone su ventana vía `System Events`/
Accessibility API (macOS), pero `screencapture` funciona con el permiso de grabación de pantalla
otorgado. No asumas que hace falta AppleScript para verificar visualmente.

## Convenciones que hay que seguir (no releer `docs/architecture.md` para esto, ya está aquí)

- **Todo dato por-tile o por-entidad en arrays primitivos paralelos (SoA)**, nunca un objeto por
  tile/edificio/ciudadano. Cuando una entidad necesita varios campos de una categoría fija y
  pequeña (p. ej. inventario por tipo de bien), usar un único array aplanado
  `id * N_categorias + categoria`, no N arrays paralelos.
- **El cliente nunca muta estado de simulación directamente.** Todo pasa por un `Command` validado
  en `SimulationContext.apply()`, nunca en la UI.
- **Estado derivado (recalculable desde estado autoritativo) nunca marca `Chunk.markDirty()`**
  al recomputarse — si lo hiciera, se auto-invalidaría en el siguiente ciclo. Ver
  `RoadNetwork`/`UtilitySystem` para el patrón correcto (dirty-tracking por versión, propio,
  desacoplado del `Chunk.version()`).
- **Todo sistema que recorra "todo lo cargado" cada tick es sospechoso de ser el cuello de botella
  hasta que se mida lo contrario.** El benchmark JMH se escribe en el mismo cambio que el sistema,
  no después — `UtilitySystem` costó una pasada de optimización entera por no hacerlo así la
  primera vez.
- **Todo formato de guardado nuevo**: magic + versión de formato + bloque CRC32C + escritura
  atómica (temp→fsync→rename atómico), igual que `WorldMeta`/`ChunkDeltaIO`/`BuildingRegistryIO`.
  Nunca `ObjectOutputStream`. Los nombres de mundo pasan por `WorldNameValidator` (lista blanca +
  verificación de que la ruta resuelta sigue dentro de la raíz de saves).
- **Toda longitud declarada en un archivo se valida contra un máximo antes de reservar memoria**
  (`BinaryBlockIO.readBlock`) — un save corrupto o manipulado no puede forzar una asignación de
  gigabytes.
- **Tests de presupuesto de asignación** (`AllocationBudgetTest`, `PopulationAndFinanceAllocationTest`,
  `UtilitySystemAllocationTest`) miden bytes/llamada con `ThreadMXBean.getThreadAllocatedBytes` en
  estado estable (tras warm-up). Cualquier sistema nuevo que corra en el `SimulationScheduler`
  debería tener uno equivalente si se espera que sea allocation-free.

## Cosas que ya se intentaron y se descartaron (no las reintentes)

- **Verificar el render por `System Events`/AppleScript**: falla con "acceso de ayuda no
  permitido" en este entorno incluso con permisos — usar `screencapture -x` directamente.
- **`jdtls` (LSP de Java) sin instalar aparte**: el plugin `jdtls-lsp` de Claude Code no trae el
  binario. Hace falta `brew install jdtls` una vez por máquina.
