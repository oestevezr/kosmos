# Atlas City — Roadmap ajustado (desde MVP 0.3)

Este documento valida el plan de fases del spec (`spec_procedural_citybuilder(3).md` §33,
MVP 0.3 en adelante) contra lo que ya sabemos tras construir Fase 1 (Terrain Sandbox) y Fase 2
(Basic City), y contra las prácticas de `disciplines:save-systems` y
`disciplines:performance-optimization`. No es una re-escritura del spec — es una capa de
decisiones concretas y ajustes de secuencia que el spec deja abiertos a propósito.

Cada fase indica: qué añade el spec, qué ajusto y por qué, qué sistemas/paquetes nuevos implica,
y qué hay que medir antes de darla por cerrada.

---

## Lección transversal de Fase 1-2 que se aplica a partir de aquí

El hallazgo más caro de la pasada de validación anterior fue `UtilitySystem`: un sistema de
recomputo completo (flood-fill) sin caché incremental resultó ser ~40× más lento que `RoadNetwork`
(que sí tiene salto por versión), y boxear su cola/mapa por llamada lo empeoraba aún más — medido
en **13.55s → 0.98s** una vez corregido. La lección se generaliza así:

> **Todo sistema nuevo que recorra "todo lo cargado" en cada tick es sospechoso de ser el cuello
> de botella hasta que se mida lo contrario — y el benchmark JMH se escribe en el mismo cambio que
> el sistema, no después.**

MVP 0.4 (grafo de flete) es, con diferencia, el candidato más probable a repetir este patrón a
mayor escala que `UtilitySystem`. Se marca explícitamente abajo.

Segunda lección, de `disciplines:save-systems`: nuestro patrón actual (magic + versión de formato
+ bloque CRC32C + escritura atómica temp→rename, con migración explícita si el formato cambia) ya
es exactamente lo que la skill recomienda como referencia — no hay que inventar nada nuevo para
`economy.dat`/`routes.dat`, solo seguir replicando `WorldMeta`/`BuildingRegistryIO`.

Tercera lección, de `BuildingRegistry`: cuando un id necesita **varios campos por categoría fija y
pequeña** (aquí: inventario por tipo de bien), la SoA correcta no es "un array por categoría"
indefinidamente — a partir de ~4-8 categorías es mejor un único array aplanado indexado por
`id * N_CATEGORIAS + categoria`. Menos arrays que crecer/copiar en paralelo, mejor localidad de
caché. Se especifica así abajo para el inventario de bienes.

---

## MVP 0.3 — Regional Economy — ✅ Completo

Implementado tal como se decidió abajo: `GoodType` (8 bienes), `GoodsLedger`, `MarketSystem`,
`RegionalGraph` (`sim.trade`), `BuildProductionBuildingCommand` (Farm/Lumber Camp/Mine/Quarry/
Steel Mill/Trade Depot), persistencia en `economy.dat`. Cubierto por `MarketSystemTest`,
`GoodsLedgerTest`, `RegionalGraphTest`, `BuildProductionBuildingCommandTest`,
`GoodsLedgerRoundTripTest`. Las dos primeras preguntas abiertas (§ al final de este documento) se
resolvieron: `TradeDepot` placeable desde el día 1, los 8 bienes completos desde el arranque.

### Qué pide el spec
Recursos, producción, consumo, bienes, nodos económicos regionales, gateways de mercado externo,
importaciones/exportaciones (§20, §21, §29). Precio simplificado por oferta/demanda; el costo de
transporte contribuye al precio final.

### Ajuste de secuencia (el más importante de este documento)
El spec dice "el costo de transporte contribuye al precio" en 0.3, pero el grafo regional de
nodos/aristas con capacidad y costo no llega hasta 0.4 (Freight). Implementar 0.3 primero sin
ningún concepto de distancia/ruta obliga a un hack de "costo de transporte" que 0.4 tendría que
tirar y rehacer.

**Ajuste**: mover la *estructura* del grafo regional (`RegionalGraph`: nodos + aristas con
distancia/capacidad/costo/tiempo de viaje — spec §13.2) a 0.3, como infraestructura pura sin
tráfico ni camiones todavía. 0.3 la usa solo para calcular `distancia → costo de transporte` en el
precio; 0.4 le añade flujo real (shipments, congestión, cuellos de botella) sobre la misma
estructura. Así no se reescribe nada, solo se le añade comportamiento encima — coherente con cómo
ya separamos `RoadNetwork` (acceso local) de lo que sería el grafo regional (nunca los confundimos
en Fase 2, ver javadoc de `RoadNetwork`).

### Ajuste sobre "gateways de mercado externo"
El spec no ata el gateway externo a ninguna infraestructura física hasta puertos/aeropuertos
(0.5/0.6). Para no bloquear 0.3 en fases futuras, propongo un building placeable simple —
`TradeDepotCommand`, mismo patrón que `BuildPowerPlantCommand`/`BuildWaterTowerCommand` — que actúa
como nodo de "mercado externo" en el `RegionalGraph` desde el primer momento, con una capacidad de
importación/exportación limitada y configurable. Puertos (0.5) y aeropuertos (0.6) se convierten
más adelante en gateways especializados con más capacidad y bienes/pasajeros restringidos por tipo,
no en un concepto nuevo.

### Modelo de datos propuesto
- **Bienes**: 8 constantes byte en `WorldConstants` o una nueva `GoodType` (Food, Timber, Ore,
  Steel, Fuel, ConsumerGoods, Machinery, ConstructionMaterials — spec §14).
- **`GoodsLedger`** (nuevo, `sim.economy`): un único `int[]` de tamaño `capacity * 8` para
  inventario, otro para producción/tick, otro para demanda/tick — indexados por
  `buildingId * 8 + goodType`, en vez de 8 arrays paralelos por campo. Reutiliza el patrón de
  crecimiento por doblado de `BuildingRegistry`.
- **`MarketSystem`** (nuevo): agrega producción/consumo por ciudad/zona económica y deriva
  `Price[Good]` con la fórmula simple del spec (oferta↑demanda↓→precio↓). Igual que
  `GovernmentFinanceSystem`, corre a cadencia baja (candidato: cada 50 ticks, igual que finanzas) y
  se cablea en `WorldManager.scheduler`.
- Los edificios de producción (granja, mina, aserradero, planta de acero) son nuevos
  `BuildingType` — reutilizan `BuildingRegistry` tal cual, no hace falta un registro paralelo.
- Nuevo comando `SetProductionTargetCommand`o similar es opcional para 0.3; el spec no lo exige
  explícitamente, se puede diferir.

### Persistencia
`economy.dat` (ya nombrado en spec §31): `GoodsLedger` + tasas de `MarketSystem` + `RegionalGraph`,
mismo patrón magic+versión+CRC32C que `BuildingRegistryIO`.

### Qué medir antes de cerrar la fase
- `MarketSystem.tick` con un benchmark JMH desde el primer commit que lo introduce (no después,
  como pasó con `UtilitySystem`), sobre una ciudad con ~10 edificios de producción.
- Confirmar que `RegionalGraph` con solo 2-3 nodos (sin flete real todavía) no introduce costo
  medible — es la base sobre la que 0.4 construye, así que su forma debe quedar barata desde ya.

---

## MVP 0.4 — Freight — ✅ Completo (alcance ajustado, ver abajo)

### Qué pide el spec
Grafo de flete, camiones, flete ferroviario, terminales de carga, envíos (shipments), cuellos de
botella (§14, §15).

### Ajuste de alcance real (desviación deliberada de lo planificado aquí)
Lo planificado originalmente en esta sección proponía un `ShipmentLODManager` calcado de
`ChunkManager` (cola de prioridad, hilos worker, streaming de entidades visuales tren/camión cerca
de la cámara). Al llegar a implementarlo, `game-client` seguía sin dibujar nada más que terreno
— ni carreteras, ni edificios, mucho menos flete — así que construir la mitad "streaming a
representación visual" del patrón habría sido abstracción prematura sobre una necesidad que no
existe todavía (spec §55: "¿esto crea trabajo/complejidad que el jugador no puede ver
significativamente?"). Ajuste real:

- `ShipmentRegistry` (SoA, mismo patrón id-estable-con-lista-libre que `BuildingRegistry`) en vez
  de un manager con hilos — los envíos no necesitan generación asíncrona como los chunks, son
  creados instantáneamente por `MarketSystem` y liquidados por `ShipmentSystem.tick`.
- `ShipmentSystem` es una única pasada lineal sobre los envíos activos — sin cola de prioridad,
  sin worker threads. Medido: 0.18–0.89 µs/op con 500 envíos activos (`ShipmentSystemBenchmark`),
  órdenes de magnitud por debajo de cualquier presupuesto de frame — el diseño simple era
  correcto para esta escala, no hacía falta la maquinaria de `ChunkManager`.
- El comercio del `TradeDepot` (antes instantáneo en 0.3) ahora pasa por un envío real con
  ETA: una importación se paga en la salida pero los bienes llegan al inventario en el arribo; una
  exportación sale del inventario en la salida pero el ingreso se cobra en el arribo — la
  asimetría de spec §14/§15 (origin/destination/commodity/quantity/departure_time/ETA) aplicada
  al único punto donde ya teníamos tráfico real (importación/exportación), sin inventar
  camiones/trenes domésticos que no tienen ningún productor→consumidor distinto que conectar
  todavía (la economía sigue siendo un único `GoodsLedger` de ciudad, no por edificio).
- Cuello de botella real: cada `TradeDepot` limita a 3 envíos concurrentes
  (`MAX_CONCURRENT_SHIPMENTS_PER_DEPOT`) — si necesita comerciar más de 3 bienes a la vez, los
  que no caben esperan al siguiente tick, tal cual spec §17's "Demand: 18,000 t/day, Port
  capacity: 12,000 t/day → delayed cargo".
- `RegionalGraph.addEdge` ganó un campo `EdgeType` (ROAD/RAILWAY/SEA_ROUTE/AIR_ROUTE) para no
  tener que romper el formato otra vez cuando MVP 0.5/0.6 empiecen a crear aristas reales entre
  destinos distintos — sigue sin usarse por ningún sistema todavía, igual que en 0.3.

El streaming visual completo (`ShipmentLODManager` al estilo `ChunkManager`) queda pendiente para
cuando `game-client` efectivamente dibuje algo que un tren/camión pueda recorrer — construirlo
antes sería puro andamiaje sin usuario.

### Rendimiento — medido, no solo planificado
`ShipmentSystem.tick` (`ShipmentSystemBenchmark`, 500 envíos activos): 0.18 µs/op sin arribos,
0.89 µs/op liquidando todos a la vez. `MarketSystem.tick` con cadena de producción completa
(`MarketSystemBenchmark`): 23.5 µs/op. Ninguno de los dos necesitó la distinción
autoritativo/derivado con seguimiento de "sucio" que se planeaba aquí — no hay recomputo de
congestión en este alcance porque no hay flujo real sobre `RegionalGraph` todavía (ver el ajuste
de alcance arriba). Esa distinción sigue siendo la correcta el día que aristas reales con
congestión lleguen (0.5+), pero no había nada que optimizar prematuramente ahora.

### Persistencia
`routes.dat` (spec §31): envíos activos, vía `ShipmentRegistryIO` (mismo patrón magic+CRC32C+
escritura atómica). No hay aristas persistidas todavía (`RegionalGraph.addEdge` sigue sin
llamadas reales) así que no aplica el caso "arista demolida invalida un shipment en tránsito" que
se anticipaba aquí — se recupera cuando ese escenario exista de verdad.

---

## Multi-ciudad + Sistema de préstamos — ✅ Completo (fuera de la secuencia MVP del spec, pedido explícito del usuario)

### Motivación
Pregunta del usuario: una vez que existen varias ciudades fundadas por el jugador (spec §9), ¿cómo
se distingue qué dinero pertenece a cuál ciudad? Y sobre eso: un sistema de préstamos con dos tipos
de prestamista — el mercado externo simulado (siempre disponible, interés alto) y otras ciudades
fundadas (interés que depende de la simulación; ciudades prósperas pueden ofrecer crédito).

### Secuenciación decidida
El usuario eligió explícitamente "multi-ciudad primero, luego ambos préstamos" en vez de construir
préstamos sobre la ciudad única existente y migrar después — evita reescribir el sistema de
préstamos dos veces.

### Fundación multi-ciudad
`CityRegistry` (spec §9, §42.3): cada ciudad fundada por el jugador es una entidad de primera clase
— SoA growable con tombstone free-list (mismo patrón que `BuildingRegistry`/`ShipmentRegistry`),
pero cada slot posee una instancia real de `GovernmentFinance` (tesorería, tasas de impuesto) y
`GoodsLedger` (inventario/producción/precio) en vez de aplanar sus campos en más columnas — spec
§42.3 permite explícitamente un objeto por unidad en el nivel CITY de la jerarquía de agregación
porque el número de ciudades es pequeño y acotado, a diferencia de edificios o tiles.

Atribución de territorio: implícita, "ciudad fundada más cercana" (`CityRegistry.nearestCity`),
el mismo patrón Voronoi-like que `RegionalGraph.nearestNodeOfType` ya usa para comercio — no existe
ni se planea una herramienta de dibujar fronteras. Cada edificio, envío y política fiscal queda
atribuido a la ciudad más cercana a su tile en el momento de creación (`BuildingRegistry.cityId`,
`ShipmentRegistry.cityId`, `SetTaxPolicyCommand(cityId, ...)`).

`FoundCityCommand` es ahora el primer comando obligatorio de cualquier partida — todo comando que
crea edificios (`AbstractPlaceUtilityBuildingCommand`, `BuildProductionBuildingCommand`) o
recauda impuestos rechaza con `REJECTED_NO_CITY_FOUNDED` si no hay ninguna ciudad fundada cerca.

Persistencia: `cities.dat` vía `CityRegistryIO` reemplaza al antiguo `economy.dat` de tesorería
única — identidad de cada ciudad + tesorería + tasas de impuesto + inventario del `GoodsLedger`,
reutilizando `GoodsLedgerIO.writeInto/readInto` (extraídos del antiguo formato de archivo único
para poder embeberse por ciudad sin duplicar la lógica de codificación).

### Sistema de préstamos
**Nota de consistencia con el spec**: esto contradice directamente spec §34 ("What NOT to
Simulate Initially" lista explícitamente "avoid: realistic financial banking"). Es una desviación
deliberada, pedida explícitamente por el usuario — no un descuido ni una lectura errónea del spec.
El spec en sí queda sin tocar (es el documento de visión original, no se edita); esta nota existe
para que quien lea §34 después no asuma que el banking sigue fuera de alcance.

`LoanRegistry` (SoA, mismo patrón de tombstone free-list): cada préstamo activo registra tipo de
prestamista (`LoanLenderType.EXTERNAL_MARKET` o `CITY`), ciudad deudora, ciudad prestamista (0 si es
el mercado externo), principal, saldo pendiente, tasa de interés por acumulación, y tick de origen.

Deliberadamente simple, seleccionado como el patrón MVP ya establecido en `GovernmentFinance`
("balance is allowed to go negative — spec's MVP economy has no bankruptcy rule yet"): `LoanSystem`
solo acumula interés sobre el saldo cada tick (`balance += balance * rate`); nunca debita
automáticamente la tesorería del deudor. El jugador debe pagar activamente vía `RepayLoanCommand`.

- `RequestExternalLoanCommand`: siempre disponible para cualquier ciudad fundada, tasa fija alta
  (2% por acumulación), tope de monto (`MAX_AMOUNT` = 50 000).
- `RequestCityLoanCommand`: exige que la ciudad prestamista tenga tesorería ≥
  `PROSPERITY_MIN_TREASURY` (5 000) y que, tras el préstamo, le queden ≥ `MIN_RESERVE_AFTER_LENDING`
  (2 000) — una ciudad nunca puede ser forzada a prestar hasta quedar en problemas. La tasa de
  interés baja linealmente con la prosperidad del prestamista, desde `BASE_INTEREST_RATE_PER_ACCRUAL`
  (0.8%) hasta `MIN_INTEREST_RATE_PER_ACCRUAL` (0.2%) al llegar a `PROSPERITY_RATE_FLOOR_TREASURY`
  (50 000) — ciudades más ricas pueden permitirse ofrecer mejores tasas que el mercado externo.
- `RepayLoanCommand`: el pago se limita al saldo pendiente (no se puede sobrepagar); el dinero sale
  siempre de la tesorería del deudor y solo llega a una tesorería prestamista si el prestamista es
  otra ciudad — un pago a un préstamo externo simplemente sale de la economía simulada, igual que
  las importaciones de `MarketSystem`.

Persistencia: `loans.dat` vía `LoanRegistryIO`, mismo patrón magic+versión+CRC32C+escritura atómica.

### Rendimiento — medido, no solo planificado
`LoanSystem.tick` es un bucle indexado plano sobre `highWaterMark`, sin boxing ni asignación por
tick (spec §42.4) — mismo patrón ya validado en `GovernmentFinanceSystem`/`MarketSystem`.
`LoanSystemBenchmark` (500 préstamos activos) está escrito en este mismo cambio, siguiendo la
lección explícita de Fase 2 (`UtilitySystem`): medir un sistema nuevo cuando se introduce, no
después. (Ejecutarlo requiere el toolchain JDK 17 del proyecto — el runner JMH del entorno de
desarrollo usado aquí tenía JDK 11 en el `PATH`, incompatible con las clases compiladas; pendiente
de ejecutar con el toolchain correcto.)

---

## MVP 0.5 — Port — ✅ Completo (alcance ajustado, ver abajo)

### Qué pide el spec
Idoneidad de costa, construcción de puertos, comercio marítimo, barcos como entidades LOD,
capacidad de importación/exportación (§17).

### Decisión tomada sobre la pregunta abierta
Se confirmó la recomendación de este documento: `PortRegistry` como registro secundario indexado
por `buildingId` (no columnas nuevas en `BuildingRegistry`, no un id space propio con free-list —
la fila de un puerto simplemente crece los arrays hasta cubrir su `buildingId`, sin tombstoning
explícito, ya que ningún llamador lee `hasPort` sin haber comprobado antes que ese `buildingId`
sigue activo y es de tipo `PORT`). `BuildingType.PORT` es el nuevo tipo de edificio;
`RegionalGraph` gana su primer nodo `NodeType.PORT` real (ya declarado desde MVP 0.3 pero sin uso).

### Ajuste de alcance real (desviación deliberada de lo planificado aquí)
- **Berths/cargo_capacity/customs_efficiency**: implementados en `PortRegistry` con valores fijos
  al construir (`BuildPortCommand.DEFAULT_*` — 6 amarres, 75 unidades/tick, 50% eficiencia
  aduanera), no configurables por el jugador todavía; el spec no pide UI de configuración en 0.5.
- **`passenger_capacity`/`storage`**: omitidos del `PortRegistry` de esta fase. `storage` ya está
  cubierto por el `GoodsLedger` de la ciudad (spec §14's inventario no es por-edificio);
  `passenger_capacity` no tiene consumidor hasta que exista demanda de pasajeros (MVP 0.6) —
  añadirlo ahora sería una columna sin lector, la misma razón por la que `RegionalGraph.addEdge`
  se declaró sin usarse en 0.3/0.4 hasta que hizo falta.
- **Barcos como entidades LOD (`ShipmentLODManager`)**: descartado, igual que en 0.4 — `game-client`
  sigue sin dibujar nada de la economía (solo terreno), así que no hay "barco" visual al que un
  shipment pueda convertirse. `MarketSystem` trata un Puerto como un gateway de mayor capacidad
  reutilizando el mismo bucle de `ShipmentRegistry`/`ShipmentSystem` que ya mueve el comercio del
  `TradeDepot` — no hace falta un tipo de entidad nuevo para que el comercio marítimo funcione.
- **Idoneidad de costa**: implementada exactamente como se planeó — adyacencia simple a 4 vecinos
  ortogonales con `TERRAIN_SHALLOW_WATER`/`TERRAIN_DEEP_WATER` (`BuildPortCommand.isCoastal`), sin
  generación adicional. Un chunk vecino no cargado se trata conservadoramente como "no agua" desde
  ese lado — aproximación aceptable dado que el jugador normalmente construye cerca de su cámara,
  donde los chunks vecinos ya están cargados.

`MarketSystem.runGateways` (antes `runTradeDepots`) ahora itera tanto `TRADE_DEPOT` como `PORT`;
un Puerto usa su propia capacidad/concurrencia de `PortRegistry` en vez de las constantes fijas del
Trade Depot, y su eficiencia aduanera aplica un descuento en importación / prima en exportación
(hasta 10% en el 100% de eficiencia) — ver `MarketSystem.PORT_CUSTOMS_MAX_BONUS`.

### Persistencia
`ports.dat` (spec §31) vía `PortRegistryIO`, mismo patrón magic+versión+CRC32C+escritura atómica.
Solo se persisten los `buildingId` que tienen fila de puerto — un `boolean` por id, igual que
`BuildingRegistryIO`/`ShipmentRegistryIO`.

### Qué medir
Nada nuevo estructuralmente caro, confirmado — `runGateways` es el mismo bucle indexado de antes
con una rama condicional adicional por edificio; ningún algoritmo nuevo, solo más tipos de edificio
en un bucle ya medido (`MarketSystemBenchmark`).

---

## Sistema de dificultad — ✅ Completo (fuera de la secuencia MVP del spec, pedido explícito del usuario)

### Motivación
Pregunta del usuario: al iniciar un mundo, ¿con cuánto dinero arranca una ciudad? No existía
ningún concepto de dificultad — `GovernmentFinance.treasuryBalance` arrancaba en `0.0` sin más.
El usuario propuso montos inspirados en TheOtown (Fácil 50K / Medio 25K / Difícil 10K) y preguntó
si la dificultad también debería afectar créditos, oportunidades y crecimiento.

### Alcance acordado con el usuario
Antes de implementar se acotó el alcance con dos rondas de preguntas:
- **Dónde se elige**: a nivel de mundo (como `HardwareProfile`), no por ciudad — todas las
  ciudades fundadas en un mundo comparten la misma dificultad.
- **Qué escala**: tesorería inicial, términos de préstamo (interés) y tasa de crecimiento de
  población/empleo. **"Oportunidades" (eventos aleatorios) queda deliberadamente fuera** — no
  existe ningún sistema de eventos en el proyecto; introducirlo sería una fase aparte, no un
  efecto secundario de este cambio.
- **Moneda y precios**: el usuario señaló, con razón, que la economía nunca definió una moneda ni
  precios diferenciados por tipo de bien (`GoodsLedger.basePrice` era `10.0` parejo para los 8
  bienes). Se decidió mantener una sola divisa por mundo sin nombre propio (sigue siendo un
  `double` mostrado con "$" genérico — el multi-divisa por país es una expansión grande sin ningún
  consumidor todavía, contraria a spec §20's "económica entendible") pero sí rebalancear
  `basePrice` por profundidad de cadena de producción antes de fijar montos de dificultad sobre
  una base pareja artificialmente.

### Implementación
`Difficulty` (enum en `com.kosmos.atlas.sim`, junto a `WorldManager`) — el eje opuesto a
`HardwareProfile`: donde `HardwareProfile` explícitamente nunca cambia reglas de simulación,
`Difficulty` no cambia nada más. Tres niveles (`EASY`/`MEDIUM`/`HARD`) con `startingTreasury`
(50K/25K/10K), `growthRateMultiplier` (1.25/1.0/0.75) y `loanInterestRateMultiplier`
(0.75/1.0/1.5, aplicado tanto al mercado externo como a préstamos entre ciudades).

`CityRegistry` es el portador natural de este ajuste mundial — ya posee un `GovernmentFinance`
por ciudad, así que `CityRegistry.create()` aplica `difficulty.startingTreasury` a cada ciudad
recién fundada; `RequestExternalLoanCommand`/`RequestCityLoanCommand` leen
`cities.difficulty().loanInterestRateMultiplier` vía `ctx.requireCities()`; `PopulationSystem.tick`
ya recibía `cities`, así que lee `cities.difficulty().growthRateMultiplier` sin parámetros nuevos.
Deliberadamente **no** se escalan los umbrales de prosperidad de `RequestCityLoanCommand` — la
tesorería inicial más baja en Difícil ya hace más difícil alcanzarlos, un segundo multiplicador
sería redundante.

`WorldManager` gana un constructor de 3 argumentos (`WorldGenSettings, HardwareProfile,
Difficulty`); el de 2 argumentos existente sigue funcionando, con `Difficulty.MEDIUM` por defecto.
`headless-runner` expone `--difficulty easy|medium|hard`.

`GoodsLedger.DEFAULT_BASE_PRICE_BY_GOOD` reemplaza el `10.0` parejo: extraídos (Food/Timber/Ore/
Fuel/ConstructionMaterials) quedan baratos, Steel más caro que Ore (refinar pierde material, 8 Ore
→ 6 Steel), y ConsumerGoods/Machinery — que **ningún edificio produce todavía**, solo son
importables — quedan como los más caros hasta que una fase futura les dé un productor doméstico.

### Persistencia
`cities.dat` gana la dificultad del mundo en su cabecera (`CityRegistryIO.FORMAT_VERSION` 1→2) —
así una ciudad fundada después de cargar una partida sigue recibiendo la tesorería inicial
correcta, y los comandos de préstamo siguen leyendo el multiplicador de interés correcto.

---

## Servicios cívicos por tiers — Fase 1: Electricidad y Agua — ✅ Completo (fuera de la secuencia MVP del spec, pedido explícito del usuario)

### Motivación
El usuario preguntó si edificios cívicos (ayuntamiento, banco central, policía, hospital,
bomberos, educación, iglesias) ya estaban definidos — no lo estaban. Al diseñar la mejor mecánica
se llegó a reutilizar `satisfactionPercent` (`BuildingRegistry`), calculado desde Fase 2 pero nunca
consumido por nada. Al concretar el diseño, el usuario pidió algo inspirado en TheOtown: cada
servicio no es un edificio único sino **varios tiers desbloqueables por población de la ciudad**
(planta de luz chica y barata → hidroeléctrica → nuclear), cada uno con su propio costo de
construcción, mantenimiento, radio de cobertura y **capacidad real** (población servida vs.
generada, no solo "¿está en rango?"). También confirmó que, en teoría, todo cuesta dinero en el
juego — hasta este cambio, nada costaba nada (ni carreteras, ni fábricas, ni Trade Depot/Port).

Dado el tamaño del pedido completo (7+ categorías de servicio × 2-3 tiers cada una), se dividió
en fases explícitamente acordadas con el usuario. Esta es la Fase 1: construye toda la maquinaria
genérica (tiers, desbloqueo por población, capacidad/demanda, costo/mantenimiento) y la prueba de
punta a punta sobre las dos categorías esenciales ya existentes, Electricidad y Agua.

### Sistema de costos base (aplica a todo, no solo a los tiers nuevos)
- `BuildRoadCommand`/`ZoneCommand` ganaron resolución de ciudad (antes eran los únicos dos
  comandos de construcción que no la exigían — cierra un hueco real del refactor multi-ciudad) y
  costo: 50/tile de carretera; 150/200/200 al zonificar residencial/comercial/industrial
  (`ZoneCommand.RESIDENTIAL_COST`/etc.), gratis des-zonificar. El costo de las RCI vive
  deliberadamente aquí y no en `PopulationSystem.settleEmptyZonedTiles` (nacimiento orgánico, que
  sigue gratis) — "delimitar la zona es la infraestructura", decisión explícita del usuario.
- `BuildProductionBuildingCommand`/`BuildPortCommand`/`AbstractPlaceUtilityBuildingCommand` ganaron
  chequeo de fondos (`CommandResult.REJECTED_INSUFFICIENT_FUNDS`) contra
  `BuildingEconomics.constructionCost(type)`.
- Nuevo `CommandResult.REJECTED_INSUFFICIENT_FUNDS` y `REJECTED_SERVICE_TIER_LOCKED`.

### Tiers de Electricidad y Agua
`BuildingType.POWER_PLANT` (existente) es tier 1; se agregan `POWER_PLANT_HYDRO` (tier 2) y
`POWER_PLANT_NUCLEAR` (tier 3). Igual para agua: `WATER_TOWER` (tier 1) → `WATER_TREATMENT_PLANT`
→ `DESALINATION_PLANT`. Cada tier se desbloquea por población total de la ciudad
(`BuildingEconomics.unlockPopulation`, calculado con un scan de `BuildingRegistry` en el momento
de construir — el mismo patrón O(edificios) que ya usa `GovernmentFinanceSystem`, aceptable porque
es un comando raro del jugador, no un hot loop). 4 comandos nuevos, mismo patrón que
`BuildPowerPlantCommand`/`BuildWaterTowerCommand`: `BuildHydroelectricPlantCommand`,
`BuildNuclearPlantCommand`, `BuildWaterTreatmentPlantCommand`, `BuildDesalinationPlantCommand`.

`BuildingEconomics` (nuevo, `sim.economy`) — tabla estática por `BuildingType` con costo de
construcción, mantenimiento por acumulación (mismo ciclo que impuestos/interés de préstamos),
capacidad, radio de cobertura y población de desbloqueo. Mismo patrón que
`GoodsLedger.DEFAULT_BASE_PRICE_BY_GOOD`. También cubre el costo de construcción ya acordado para
Farm/Lumber Camp/Mine/Quarry/Steel Mill/Trade Depot/Port (capacidad/radio/desbloqueo en 0 para
estos — no son fuentes de cobertura de `UtilitySystem`).

### Capacidad real (población servida vs. generada)
`UtilitySystem` deja de ser solo "¿está en rango?": cada tier flood-fill con su propio radio
(`floodFillFromSources` ganó un parámetro de radio, antes usaba una constante compartida), y
además de eso calcula, por ciudad, `capacidad instalada / demanda` (población + empleos, misma
fórmula que `GovernmentFinanceSystem` ya usa para impuestos) como un ratio en `[0,1]`
(`powerCoverageRatio`/`waterCoverageRatio`). `PopulationSystem` multiplica el crecimiento por esos
ratios además del `growthRateMultiplier` de `Difficulty` — una ciudad que superó la capacidad de
su única planta chica crece más lento aunque el tile siga técnicamente "en rango". Esto evita una
reescritura completa a un sistema de carga/flujo real (spec §20: entendible, no hiperrealista) —
un solo scan extra por ciudad, no una simulación de red eléctrica.

`satisfactionPercent` no cambió en esta fase — el techo por tier de prosperidad/lujo queda para
Fase 2, cuando existan edificios de prosperidad/lujo contra los que probarlo.

### Fuera de alcance de esta fase (documentado, no implementado)
- **Fase 2**: Hospital, Bomberos, Basura+Incineradora, Cementerio, Parques, Museo — cada uno
  reusando exactamente esta misma maquinaria (`BuildingEconomics`, capacidad/desbloqueo por
  población, `UtilitySystem`), más el techo de satisfacción por tier de prosperidad/lujo y el
  ingreso de Museo (turismo). Esta fase sí necesitará ampliar `Chunk.serviceFlags` de `byte[]` a
  `int[]` (bump de formato de `ChunkDeltaIO`) porque agrega bits de servicio nuevos — Electricidad
  y Agua no lo necesitaron porque ya tenían sus bits desde Fase 2.
- Ayuntamiento, Banco Central, Policía/Educación/Iglesias — no confirmados para ninguna fase.
- Densidad evolutiva estilo TheOtown (edificios que crecen de casas chicas a rascacielos con
  variantes aleatorias, mencionado por el usuario como inspiración) — idea distinta, sin relación
  directa con el sistema de tiers de servicio; anotada como posible fase futura.

### Persistencia
Sin cambios de formato en esta fase — `BuildingEconomics` es una tabla estática (no se persiste),
y `Chunk.serviceFlags` sigue siendo `byte[]` porque Electricidad/Agua ya usaban sus bits desde
antes.

---

## Servicios cívicos por tiers — Fase 2: Prosperidad y Lujo — ✅ Completo (fuera de la secuencia MVP del spec, pedido explícito del usuario)

### Motivación
Continuación directa de la Fase 1: los servicios de **prosperidad** (hospital, bomberos,
recolección de basura, incineradoras, cementerios) y **lujo** (parques, museos) que se habían
diseñado conceptualmente antes del pivote a "tiers reales" pero quedaron documentados, no
implementados. A diferencia de Electricidad/Agua (que modulan el crecimiento vía una razón
capacidad/demanda), estos servicios no tienen una unidad de "capacidad" real que tenga sentido —
en su lugar, cierran el ciclo que `satisfactionPercent` (`BuildingRegistry`) dejó abierto desde
Fase 2 original del spec: calculado desde entonces, nunca consumido hasta ahora.

### Nuevos tipos de edificio
9 tipos nuevos en `BuildingType` (16→24, `COUNT` 16→25), 3 categorías con 2 tiers cada una y 2
categorías de lujo sin tiers:
- **Salud**: `CLINIC` (tier 1, siempre disponible) → `HOSPITAL` (tier 2, desbloquea a 1000 de
  población).
- **Bomberos**: `VOLUNTEER_FIRE_BRIGADE` → `FIRE_STATION` (desbloquea a 1000).
- **Saneamiento**: `WASTE_COLLECTION` → `INCINERATOR` (desbloquea a 800) — Basura+Incineradora
  modelados como 2 tiers de una sola categoría, no dos separadas, mismo criterio de simplificación
  que Electricidad/Agua.
- **Cementerio, Parque, Museo**: sin tier 2 — el usuario los describió como servicios que "ya
  otorgan los máximos niveles" al construirse, así que no hay una versión "más grande" con sentido
  todavía. Museo es el único edificio cívico que genera ingreso propio (turismo, neto +6/accrual
  tras su propio mantenimiento) — confirmado con el usuario en la ronda de preguntas de Fase 1.

Ninguno de estos 9 tipos tiene `CAPACITY` en `BuildingEconomics` (a diferencia de Electricidad/
Agua) — "¿cuánta población sirve un hospital?" no tiene una unidad natural (spec §20), así que solo
aportan cobertura binaria.

### Techo de satisfacción + crecimiento real
`UtilitySystem` gana 6 categorías de flood-fill más (mismo patrón que Electricidad/Agua, sin razón
de capacidad/demanda — solo el bit de cobertura). `PopulationSystem` reemplaza el
`+SATISFACTION_RECOVERY_STEP`/`-SATISFACTION_DECAY_STEP` plano (que dependía únicamente del gate
esencial) por moverse hacia un **techo** derivado de qué bits de prosperidad/lujo cubren el tile:
sin ninguno, 60; con ≥1 de prosperidad, 85; con ≥1 de lujo, 100. El cambio que cierra el ciclo:
`growResidential`/`growWorkplace` ahora multiplican el crecimiento por
`satisfactionPercent / 100.0`, además de `Difficulty.growthRateMultiplier` y los ratios de
capacidad de Electricidad/Agua — una ciudad sin ningún servicio de prosperidad queda permanentemente
limitada a ~60% de multiplicador de crecimiento hasta que el jugador construya alguno.

### `BuildCivicBuildingCommand`
Mismo patrón data-driven que `BuildProductionBuildingCommand`: un solo comando parametrizado por
`byte buildingType` para los 9 tipos nuevos en vez de 9 clases casi idénticas. Reutiliza
`REJECTED_SERVICE_TIER_LOCKED`/`REJECTED_INSUFFICIENT_FUNDS`/`REJECTED_NO_CITY_FOUNDED` ya
existentes desde Fase 1 — no hizo falta ningún `CommandResult` nuevo. El chequeo de población de
desbloqueo se extrajo de `AbstractPlaceUtilityBuildingCommand` (que lo tenía privado y duplicado
en potencia) a un método público `BuildingRegistry.residentialPopulationOfCity(int cityId)`,
compartido por ambos comandos.

### Persistencia — `Chunk.serviceFlags` de `byte[]` a `int[]` (bump de formato, anticipado en Fase 1)
3 bits usados + 6 nuevos = 9, no caben en un `byte` (8 bits) — Fase 1 ya documentó que esta fase
necesitaría el ensanche. `ChunkDeltaIO.FORMAT_VERSION` 2→3 (el bloque de `serviceFlags` pasa de un
`byte[]` en bloque a un loop `writeInt`/`readInt` por tile, mismo patrón que `buildingId`); saves
viejos quedan no-cargables — mismo criterio ya aplicado a `BuildingRegistryIO`/`ShipmentRegistryIO`/
`CityRegistryIO` en fases anteriores (sin build de release todavía).

### Fuera de alcance de esta fase (documentado, no implementado)
- Ayuntamiento, Banco Central, Policía, Educación, Iglesias — no confirmados para ninguna fase.
- Densidad evolutiva estilo TheOtown — sigue sin relación directa, anotada como posible fase futura.
- Capacidad/demanda real para prosperidad/lujo (ej. "un hospital atiende X pacientes") — sin unidad
  natural, se mantiene como cobertura binaria + techo de satisfacción.

---

## MVP 0.6 — Regional Passenger Transport

### Qué pide el spec
Rail intercity, autobuses, migración, turismo, prototipo de aeropuerto (§16, §19).

### Ajuste
Pasajeros reutilizan el mismo `RegionalGraph` de 0.3/0.4 con un segundo tipo de flujo (demanda de
pasajeros en vez de mercancía) sobre las mismas aristas — spec §16 ya lo describe como matrices de
viajes entre zonas, estructuralmente paralelo a los bienes de 0.3. Evitar la tentación de construir
un grafo de pasajeros aparte del de flete; son el mismo grafo con dos tipos de demanda.

Migración: extiende `PopulationSystem` — la tasa de asentamiento ya no depende solo de
condiciones locales (carretera+luz+agua, Fase 2) sino también de la atracción del `MarketSystem`
externo (demanda de migración de spec §29). Esto es un ajuste de fórmula dentro de
`PopulationSystem.tick`, no un sistema nuevo.

Aeropuerto: mismo patrón que puerto (0.5) — nodo especializado en el grafo, gateway al mercado
externo con capacidad propia, reemplaza gradualmente al `TradeDepot` de 0.3 en vez de competir con
él (una ciudad pequeña no soporta un aeropuerto internacional — spec §19 — así que el `TradeDepot`
sigue siendo válido para ciudades que nunca justifican uno).

---

## Preguntas resueltas

1. ~~`TradeDepot` como gateway temprano~~ — resuelto (MVP 0.3): placeable desde 0.3.
2. ~~Alcance de bienes en 0.3~~ — resuelto (MVP 0.3): los 8 bienes completos desde el arranque.
3. ~~`PortRegistry` como registro secundario vs. columnas opcionales en `BuildingRegistry`~~ —
   resuelto (MVP 0.5): registro secundario indexado por `buildingId`, ver la sección de MVP 0.5
   arriba para el detalle de implementación.
