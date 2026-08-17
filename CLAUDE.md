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
- **Sistema de dificultad**: completa (pedido explícito del usuario, fuera de la secuencia MVP
  del spec, ver `docs/roadmap.md`). `Difficulty` (enum `EASY`/`MEDIUM`/`HARD`, junto a
  `WorldManager` en `com.kosmos.atlas.sim`) — elegido a nivel de mundo, no por ciudad, opuesto a
  `HardwareProfile` (ese nunca cambia reglas de simulación; `Difficulty` no cambia nada más).
  Controla tesorería inicial (50K/25K/10K, inspirado en TheOtown), `growthRateMultiplier` de
  `PopulationSystem`, y `loanInterestRateMultiplier` de ambos tipos de préstamo — los umbrales de
  prosperidad de `RequestCityLoanCommand` NO se escalan (la tesorería inicial más baja ya hace el
  trabajo). `CityRegistry` es el portador de la dificultad del mundo (`cities.difficulty()`).
  `GoodsLedger.basePrice` ya no es parejo (`10.0` para los 8 bienes) — ahora difiere por
  profundidad de cadena de producción. `WorldManager(genSettings, profile, difficulty)`;
  `headless-runner` expone `--difficulty`. Persistencia: `cities.dat` gana la dificultad en su
  cabecera (`CityRegistryIO.FORMAT_VERSION` 2). "Oportunidades" (eventos aleatorios) quedó
  deliberadamente fuera de alcance — no existe ningún sistema de eventos en el proyecto.
- **Servicios cívicos por tiers — Fase 1 (Electricidad + Agua)**: completa (pedido explícito del
  usuario, fuera de la secuencia MVP del spec, ver `docs/roadmap.md`). Todo comando de
  construcción ahora cuesta dinero: `BuildingEconomics` (nuevo, `sim.economy`) es la tabla estática
  por `BuildingType` con costo/mantenimiento/capacidad/radio/población-de-desbloqueo. Electricidad
  y Agua ganaron 3 tiers cada una (`POWER_PLANT`→`POWER_PLANT_HYDRO`→`POWER_PLANT_NUCLEAR`,
  `WATER_TOWER`→`WATER_TREATMENT_PLANT`→`DESALINATION_PLANT`), desbloqueados por población de la
  ciudad. `BuildRoadCommand`/`ZoneCommand` ahora exigen ciudad fundada + cobran costo (RCI se paga
  al zonificar, no cuando el edificio nace solo). `UtilitySystem` calcula capacidad real (población
  servida vs. generada) por ciudad, expuesta como `powerCoverageRatio`/`waterCoverageRatio`;
  `PopulationSystem` multiplica el crecimiento por esos ratios. Nuevos `CommandResult`:
  `REJECTED_INSUFFICIENT_FUNDS`, `REJECTED_SERVICE_TIER_LOCKED`.
- **Servicios cívicos por tiers — Fase 2 (Prosperidad + Lujo)**: completa (ver `docs/roadmap.md`).
  9 tipos nuevos en `BuildingType` (`COUNT` 25): Salud (`CLINIC`→`HOSPITAL`), Bomberos
  (`VOLUNTEER_FIRE_BRIGADE`→`FIRE_STATION`), Saneamiento (`WASTE_COLLECTION`→`INCINERATOR`), y
  `CEMETERY`/`PARK`/`MUSEUM` sin tier 2 (el usuario los describió como "ya otorgan el máximo
  nivel"). Ninguno tiene `CAPACITY` en `BuildingEconomics` — solo cobertura binaria, que ahora sube
  el **techo** de `satisfactionPercent` (sin cobertura 60, con prosperidad 85, con lujo 100) en
  `PopulationSystem`; el crecimiento por fin multiplica por `satisfactionPercent/100.0`, cerrando
  un ciclo abierto desde Fase 2 original (satisfacción se calculaba pero nunca se usaba). Museo es
  el único edificio cívico con ingreso propio (turismo). Nuevo `BuildCivicBuildingCommand`
  data-driven (como `BuildProductionBuildingCommand`) para los 9 tipos — reutiliza los
  `CommandResult` de Fase 1, no hizo falta ninguno nuevo. `Chunk.serviceFlags` pasó de `byte[]` a
  `int[]` (9 bits de servicio no caben en un byte) — `ChunkDeltaIO.FORMAT_VERSION` 3, saves viejos
  no cargan.
- **Ayuntamiento, Banco Central, Policía/Educación/Iglesias**: completa (ver `docs/roadmap.md`).
  Policía (`POLICE_OUTPOST`→`POLICE_STATION`) y Educación (`SCHOOL`→`UNIVERSITY`) repiten el
  patrón mecánico exacto de Fase 2; `CHURCH` sin tier 2. `BuildingType.COUNT` 32. **Banco Central**
  (`CENTRAL_BANK`) no es fuente de cobertura — su único efecto es un gate nuevo en
  `RequestCityLoanCommand`: la ciudad prestamista necesita uno activo
  (`BuildingRegistry.hasActiveBuildingOfType`, nuevo `CommandResult.
  REJECTED_LENDER_HAS_NO_CENTRAL_BANK`). **Ayuntamiento** (`CITY_HALL`) no se compra — se
  concluyó que era conceptualmente redundante con `FoundCityCommand`, así que ahora ese comando
  lo coloca gratis en el tile de fundación (y ganó el chequeo de tile ocupado que no tenía);
  `CITY_HALL` queda excluido de `BuildCivicBuildingCommand` a propósito. Sin bump de formato — los
  3 bits nuevos ya caben en el `int` de `Chunk.serviceFlags`. **Pendiente sin implementar**:
  densidad evolutiva estilo TheOtown (casas chicas → rascacielos) — arquitectura distinta, requiere
  su propio plan.
- **Contaminación (intensidad acumulativa)**: completa (ver `docs/roadmap.md`). Reutiliza el
  flood-fill BFS de `UtilitySystem` (nuevo parámetro `pollutionDelta` en `floodFillFromSources`,
  suma saturante en vez de OR de bit) en lugar de un sistema nuevo. `Chunk.pollutionLevel`
  (`short[]`, derivado, no persistido — sin bump de `ChunkDeltaIO.FORMAT_VERSION`).
  `BuildingEconomics` gana `pollutionIntensity`/`pollutionRadiusTiles` (radio propio, distinto de
  `coverageRadiusTiles`). Contaminan `INDUSTRIAL`/`STEEL_MILL`/`MINE`/`QUARRY`/`POWER_PLANT` (solo
  tier 1)/`INCINERATOR`; solo `PARK` reduce. `PopulationSystem` resta la contaminación del techo de
  satisfacción (piso 10); los edificios industriales son inmunes a su propia contaminación. Un solo
  eje cubre pollution+noise por ahora — eje de ruido separado queda como deuda documentada.
- **Densidad evolutiva de edificios**: completa (pedido explícito del usuario, fuera de la
  secuencia MVP del spec, ver `docs/roadmap.md`). Último pendiente real del pedido original sobre
  edificios cívicos/densidad. Reutiliza el techo de satisfacción ya existente de
  `PopulationSystem` como requisito de servicios — sin chequeo de cobertura nuevo.
  `BuildingRegistry.densityLevel` (nuevo `byte[]`) + `BuildingDensity` (nueva clase,
  `sim.population`): capacidad/umbrales de promoción-degradación (con histéresis)/multiplicador de
  impuestos por nivel, más un `variantIndex` determinista (no almacenado) listo para cuando
  `game-client` dibuje edificios. `PopulationSystem.updateDensityLevel` promueve un edificio lleno
  y satisfecho, degrada uno que perdió cobertura (recortando ocupantes a la nueva capacidad).
  `GovernmentFinanceSystem` pondera la base gravable por `wageMultiplier` del nivel.
  `BuildingRegistryIO.FORMAT_VERSION` 3→4, saves viejos no cargan.
- **MVP 0.6 (Regional Passenger Transport) — completo, con alcance ajustado**: ver
  `docs/roadmap.md` para el detalle de las dos pasadas. **Migración**: ajuste de fórmula en
  `PopulationSystem.settleEmptyZonedTiles` — la siembra residencial inicial se escala por
  `migrationMultiplier` (superávit local de empleos + actividad exportadora de `MarketSystem`/
  `GoodsLedger`, sin sistema nuevo ni campo persistido). **Aeropuerto**: `BuildingType.AIRPORT`,
  mismo patrón que Puerto (0.5) pero sin chequeo de costa y gateado por población
  (`unlockPopulation=3000`); `AirportRegistry` (sin `passenger_capacity` — sin lector todavía).
  **Rail**: `BuildingType.RAIL_TERMINAL`, cuarto gateway de `MarketSystem.runGateways` (junto a
  Trade Depot/Puerto/Aeropuerto), sin costa/población/aduana (comercio doméstico); `StationRegistry`
  (nuevo), nodo `NodeType.STATION`. **Autobuses**: la pieza nueva — `BUS_DEPOT` (central,
  `BuildingEconomics.capacity` = rutas simultáneas máximas) + `BUS_STOP` (parada, cobertura solo si
  pertenece a una ruta activa) + `BusRouteRegistry` (nuevo, SoA de ancho fijo — array aplanado
  `id*MAX_STOPS_PER_ROUTE+slot`, sin persistencia porque sus rutas referencian aristas de
  `RegionalGraph`, que tampoco se persiste) + `CreateBusRouteCommand`, el **primer uso real** de
  `RegionalGraph.addEdge` en el proyecto. Nuevo `WorldConstants.SERVICE_TRANSIT` en
  `PROSPERITY_MASK`; `UtilitySystem.floodFillFromSources` se partió en un `runBfs` compartido +
  `floodFillFromRouteStops` (filtra por `busRoutes.isStopInAnyActiveRoute`). **Turismo**: ingreso de
  ciudad dentro de `GovernmentFinanceSystem` (no un sistema nuevo) — proporcional a
  población×atracciones Museo/Parque activas, con topes. Se corrigió de paso un bug preexistente de
  0.5: `DemolishCommand` solo limpiaba nodos `EXTERNAL_MARKET` en `RegionalGraph`, dejando huérfano
  el nodo de cualquier Puerto demolido — ahora mapea cualquier tipo de edificio gateway a su
  `NodeType`. Persistencia: `airports.dat`/`stations.dat` (nuevos), sin bump de formatos existentes
  (`BusRouteRegistry` deliberadamente sin persistir).

## Estructura del proyecto

```
simulation-core/   Java puro. CERO dependencias de libGDX/LWJGL/Android — regla forzada por la
                    tarea Gradle `checkCoreIsolation` (falla el build si se viola).
  sim/              WorldManager (entry point), Difficulty (EASY/MEDIUM/HARD, a nivel de mundo)
  sim/world/        Chunks SoA, streaming (ChunkManager), terreno, generación procedural (gen/)
  sim/commands/     Command bus, journal, comandos de terreno (terrain/), ciudad (city/) y
                    economía (economy/)
  sim/population/   BuildingRegistry (agregados por edificio, incluye campos de producción de
                    Fase 3 y cityId del dueño), PopulationSystem (totales por ciudad)
  sim/transport/    RoadNetwork (acceso local por adyacencia — NO es el grafo regional)
  sim/utility/      UtilitySystem (flood-fill por tier + capacidad real de electricidad/agua)
  sim/city/         CityRegistry (una GovernmentFinance/GoodsLedger por ciudad fundada, spec §9)
  sim/economy/      GovernmentFinance/System, GoodType, GoodsLedger, MarketSystem,
                    LoanRegistry/LoanSystem/LoanLenderType (sistema de préstamos),
                    BuildingEconomics (costo/mantenimiento/capacidad/radio/desbloqueo por tipo)
  sim/trade/        RegionalGraph (nodos/aristas — adelantado desde MVP 0.4, ver docs/roadmap.md;
                    `addEdge` lo usa por primera vez CreateBusRouteCommand, MVP 0.6),
                    ShipmentRegistry/System (envíos del TradeDepot/Port/Airport/RailTerminal, sin
                    streaming visual todavía), PortRegistry/AirportRegistry/StationRegistry (fila
                    secundaria por buildingId), BusRouteRegistry (rutas de autobús, SoA de ancho
                    fijo, sin persistir — ver MVP 0.6 en docs/roadmap.md)
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
`java` por defecto en la máquina. También importa para **JMH**: el fork que ejecuta cada benchmark
usa el `java` del `PATH` directamente (no el toolchain), así que un `PATH` con JDK < 17 falla con
`UnsupportedClassVersionError` al correr `./gradlew :benchmark:jmh` — pasó en esta máquina hasta
que se instaló y registró JDK 17 como default (`sudo ln -sfn .../openjdk@17/libexec/openjdk.jdk
/Library/Java/JavaVirtualMachines/openjdk-17.jdk` + `JAVA_HOME`/`PATH` en `~/.zshrc`).

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
