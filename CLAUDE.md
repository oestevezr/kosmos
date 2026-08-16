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
- **Fase 3 (MVP 0.3 — Regional Economy)**: completa. 8 bienes (`GoodType`), edificios de
  producción gateados por los recursos naturales de Fase 1 (`BuildProductionBuildingCommand`:
  Farm/Lumber Camp/Mine/Quarry/Steel Mill), `TradeDepot` como gateway de mercado externo desde
  ya, `RegionalGraph` (adelantado desde MVP 0.4 a propósito, ver `docs/roadmap.md`), precio
  derivado de inventario + costo de transporte, persistencia en `economy.dat`. El cliente
  sigue sin dibujar nada de esto — todavía solo terreno.
- **Fase 4 (MVP 0.4 — Freight)**: completa, con alcance ajustado respecto al plan original (ver
  `docs/roadmap.md`). `ShipmentRegistry`/`ShipmentSystem` (SoA simple, sin hilos — se descartó el
  `ShipmentLODManager` estilo `ChunkManager` porque `game-client` no dibuja nada que un envío
  pueda "convertirse en" todavía). El comercio del `TradeDepot` ahora tiene demora real
  (ETA, pago en salida/entrega en llegada) y un tope de 3 envíos concurrentes por depósito
  (cuello de botella real, spec §17). `RegionalGraph.addEdge` ganó un campo `EdgeType`. Medido:
  `ShipmentSystem.tick` ~0.2–0.9 µs/op con 500 envíos activos. Persistencia en `routes.dat`.
- **Multi-ciudad + Sistema de préstamos**: completa (pedido explícito del usuario, fuera de la
  secuencia MVP del spec, ver `docs/roadmap.md`). `CityRegistry` — cada ciudad fundada
  (`FoundCityCommand`) posee su propia `GovernmentFinance`/`GoodsLedger`; territorio implícito por
  "ciudad más cercana" (`CityRegistry.nearestCity`), sin herramienta de fronteras. Todo comando que
  crea edificios ahora exige una ciudad fundada cerca (`REJECTED_NO_CITY_FOUNDED`). Persistencia en
  `cities.dat` (reemplaza el antiguo `economy.dat` de tesorería única). Préstamos: `LoanRegistry`/
  `LoanSystem` (solo acumula interés, sin auto-débito — MVP simple), `RequestExternalLoanCommand`
  (siempre disponible, interés fijo alto), `RequestCityLoanCommand` (gateado por prosperidad de la
  ciudad prestamista, tasa más baja cuanto más rica), `RepayLoanCommand`. Persistencia en
  `loans.dat`.
- **Fase 5 (MVP 0.5 — Port)**: completa, con alcance ajustado (ver `docs/roadmap.md`). Nuevo
  `BuildingType.PORT`; `PortRegistry` como registro secundario indexado por `buildingId`
  (berths/cargoCapacityPerTick/customsEfficiencyPercent — resuelto a favor de esto, no de columnas
  en `BuildingRegistry`). `BuildPortCommand` exige idoneidad de costa aproximada por adyacencia
  simple a 4 vecinos con agua (`TERRAIN_SHALLOW_WATER`/`TERRAIN_DEEP_WATER`), sin paso de
  generación dedicado. Registra un nodo `NodeType.PORT` real en `RegionalGraph` (declarado desde
  MVP 0.3, sin uso hasta ahora). `MarketSystem.runGateways` (antes `runTradeDepots`) ahora comercia
  tanto `TRADE_DEPOT` como `PORT`, cada uno con su propia capacidad/concurrencia/eficiencia
  aduanera. Barcos como entidades LOD descartados — mismo motivo que `ShipmentLODManager` en 0.4,
  `game-client` sigue sin dibujar nada de la economía. Persistencia en `ports.dat`.
- **MVP 0.6 (Regional Passenger Transport) en adelante**: solo planificado (`docs/roadmap.md`),
  sin código.

## Estructura del proyecto

```
simulation-core/   Java puro. CERO dependencias de libGDX/LWJGL/Android — regla forzada por la
                    tarea Gradle `checkCoreIsolation` (falla el build si se viola).
  sim/world/        Chunks SoA, streaming (ChunkManager), terreno, generación procedural (gen/)
  sim/commands/     Command bus, journal, comandos de terreno (terrain/), ciudad (city/) y
                    economía (economy/)
  sim/population/   BuildingRegistry (agregados por edificio, incluye campos de producción de
                    Fase 3 y cityId del dueño), PopulationSystem (totales por ciudad)
  sim/transport/    RoadNetwork (acceso local por adyacencia — NO es el grafo regional)
  sim/utility/      UtilitySystem (flood-fill de electricidad/agua)
  sim/city/         CityRegistry (una GovernmentFinance/GoodsLedger por ciudad fundada, spec §9)
  sim/economy/      GovernmentFinance/System, GoodType, GoodsLedger, MarketSystem,
                    LoanRegistry/LoanSystem/LoanLenderType (sistema de préstamos)
  sim/trade/        RegionalGraph (nodos/aristas — adelantado desde MVP 0.4, ver docs/roadmap.md),
                    ShipmentRegistry/System (envíos del TradeDepot/Port, sin streaming visual
                    todavía), PortRegistry (fila secundaria por buildingId de tipo PORT)
  sim/persistence/  Formato binario propio (magic+versión+CRC32C+escritura atómica), nunca
                    ObjectOutputStream. Un archivo por tipo de estado (world.meta, chunks/*.delta,
                    settlements.dat, cities.dat, routes.dat, loans.dat, ports.dat, ...)
  sim/util/         LongIntHashMap, Histogram — primitivas sin boxing para las rutas calientes

game-client/        libGDX. render/ (IsoProjection, ChunkMesh, WorldRenderer), camera/, ui/,
                    presentation/ (AtlasGame). Depende de simulation-core, nunca al revés.
platform-desktop/   Entry point LWJGL3. Casi vacío a propósito.
headless-runner/    CLI que corre WorldManager sin gráficos. `--bench chunkgen|city|smoke`.
benchmark/          JMH. Un archivo por sistema medido (ChunkGeneration, Noise, LongIntHashMap,
                    RoadNetwork, UtilitySystem, PopulationSystem, GovernmentFinance, LoanSystem).
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
