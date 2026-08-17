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

## Ayuntamiento, Banco Central, Policía/Educación/Iglesias — ✅ Completo (fuera de la secuencia MVP del spec, pedido explícito del usuario)

### Motivación
El usuario pidió implementar el resto de lo que había quedado pendiente y sin confirmar tras la
Fase 2: Ayuntamiento, Banco Central, y Policía/Educación/Iglesias — junto con la densidad evolutiva
estilo TheOtown, que se separó explícitamente en un plan aparte por no tener enganche arquitectónico
con `BuildingEconomics`/`UtilitySystem` (queda documentada como pendiente, sin implementar todavía).

### Policía y Educación — repetición mecánica exacta del patrón de Fase 2
`POLICE_OUTPOST`→`POLICE_STATION` y `SCHOOL`→`UNIVERSITY`, mismo patrón de 2 tiers que Salud/
Bomberos/Saneamiento (encaja con spec §23: "crime" y "education" listados junto a "healthcare"
como factores de atractivo). `CHURCH` sin tier 2, igual que Cementerio. Los 3 se suman a
`PopulationSystem.PROSPERITY_MASK` (techo de satisfacción 85) — cero lógica nueva, solo 3 filas en
`BuildingEconomics`, 3 bits en `WorldConstants`, 3 categorías más en `UtilitySystem` y
`BuildCivicBuildingCommand`.

### Banco Central — gate físico para `RequestCityLoanCommand`
Ya se había discutido como la mecánica natural: hoy cualquier ciudad próspera puede prestarle a
otra sin construir nada. `BuildingType.CENTRAL_BANK` no es fuente de cobertura (radio 0, no entra
en `UtilitySystem`) — su único efecto es un nuevo chequeo en `RequestCityLoanCommand` antes de los
de prosperidad/tesorería: la ciudad prestamista debe tener un Banco Central activo
(`BuildingRegistry.hasActiveBuildingOfType`, nuevo método público, mismo patrón O(edificios) que
`residentialPopulationOfCity` de Fase 2). Nuevo `CommandResult.REJECTED_LENDER_HAS_NO_CENTRAL_BANK`.

### Ayuntamiento — auto-colocado por `FoundCityCommand`, no comprable
Se había concluido que el Ayuntamiento es conceptualmente redundante (`FoundCityCommand` ya "es"
la fundación de la ciudad). En vez de dejarlo puramente decorativo o inventar un comando aparte,
`FoundCityCommand` ahora coloca un `BuildingType.CITY_HALL` gratis en el mismo tile de fundación —
le da un rol real (marca la ciudad en el mapa, tiene mantenimiento) sin duplicar la fundación.
`CITY_HALL` queda deliberadamente **fuera** de `BuildCivicBuildingCommand.isKnownCivicType` — no
es comprable directamente. Esto exigió agregarle a `FoundCityCommand` el chequeo de tile ocupado
que no tenía hasta ahora (`REJECTED_TILE_OCCUPIED`) — antes fundar sobre un tile con un edificio ya
existente lo pisaría en silencio. No hay regla de "no se puede demoler el Ayuntamiento" — se deja
demolible como cualquier edificio, ya que `CityRegistry` no depende de que siga existiendo.

**Efecto colateral real**: cualquier escenario que construyera una carretera exactamente en el
mismo tile donde se fundó la ciudad ahora choca con el Ayuntamiento (`REJECTED_TILE_OCCUPIED`).
Tanto `HeadlessMain`'s `--bench city` como `WorldManagerCityGrowthTest` fundaban la ciudad en el
primer tile de su tramo de carretera — se ajustaron para reservar ese tile solo para el Ayuntamiento
y empezar la carretera un tile después.

### Fuera de alcance de esta fase (documentado, no implementado)
- **Densidad evolutiva estilo TheOtown** (casas chicas → rascacielos, variantes aleatorias) —
  arquitectura distinta, sin relación con lo anterior. Queda como el único pendiente real del pedido
  original del usuario sobre edificios cívicos/densidad.
- Capacidad/demanda real para Policía/Educación/Iglesia — mismo criterio que Fase 2.
- Prevenir la demolición del Ayuntamiento — no pedido.

### Persistencia
Sin bump de formato — los 3 bits de servicio nuevos ya caben en el `int` ensanchado en la Fase 2 de
servicios cívicos.

---

## Contaminación (mecánica de intensidad acumulativa) — ✅ Completo (fuera de la secuencia MVP del spec, pedido explícito del usuario)

### Motivación
El usuario pidió que ciertos edificios contaminen y otros (parques) reduzcan la contaminación, como
contrapeso al sistema de cobertura por radio de los servicios cívicos (ya implementado, sin cambios
en esta fase). A diferencia de la cobertura —binaria, dentro/fuera de radio— la contaminación se
acumula: tres decisiones se cerraron con el usuario antes de diseñar: (1) modelo acumulativo por
intensidad, sin decaimiento por distancia, fuentes solapadas suman; (2) el efecto es bajar el techo
de satisfacción de `PopulationSystem`, no un multiplicador de crecimiento aparte; (3) alcance
acotado: contaminan `INDUSTRIAL`/`STEEL_MILL`/`MINE`/`QUARRY`/`POWER_PLANT` (solo tier 1, no
Hidro/Nuclear)/`INCINERATOR`; solo `PARK` reduce (bosques naturales descartados a propósito).

### Diseño: reutiliza el flood-fill de `UtilitySystem`, no un sistema nuevo
`UtilitySystem.floodFillFromSources` (el mismo BFS multi-fuente acotado por radio que ya alimenta
cobertura de electricidad/agua/servicios cívicos) ganó un parámetro `pollutionDelta`: en vez de
solo OR-ear un bit de `serviceFlags`, ahora también puede sumar (saturando) a
`Chunk.pollutionLevel` (`short[]` nuevo, capa derivada como `serviceFlags`, nunca persistida ni
marca el chunk dirty). `BuildingEconomics` ganó dos columnas —`pollutionIntensity`/
`pollutionRadiusTiles`, con su propio radio, independiente de `coverageRadiusTiles`— y una fila por
tipo polutor/reductor. `PARK` hace dos flood-fills separados: el de cobertura existente (bit
`SERVICE_PARK`) y uno nuevo de contaminación (delta negativo), porque su radio de cobertura (15) y
su radio de reducción de contaminación (12) no tienen por qué coincidir.

`PopulationSystem.growExistingBuildings` resta la contaminación del tile (recortada a `[0,100]`) del
techo de satisfacción ya calculado, con un piso de 10 (nunca cero duro). **Los edificios
industriales son inmunes a su propia contaminación** — sin esta excepción, toda zona industrial se
autoestrangularía al techo mínimo apenas apareciera, y el escenario `--bench city` (que zonifica
industria junto a residencial/comercial) dejaría de crecer.

### Decisión de diseño no confirmada explícitamente con el usuario: un solo eje, no dos
El pedido original mencionaba contaminación **y** ruido por separado. Se implementó un único eje
("contaminación") en vez de dos arrays paralelos: con el alcance de fuentes elegido, el ruido
tendría exactamente las mismas fuentes que la contaminación (industria/incineradora/planta), así
que un segundo eje duplicaría BFS y memoria sin cambiar ninguna decisión del jugador. Un eje de
ruido propio se justifica el día que exista una fuente ruidosa pero no contaminante —carreteras
principales, puerto, aeropuerto— y eso ya requiere que esos elementos entren al modelo de cobertura
(MVP 0.5+ en adelante). **Deuda documentada, no implementada.**

### Fuera de alcance
- Eje de ruido separado (ver arriba).
- Bosques/`RESOURCE_TIMBER` naturales como reductor pasivo — descartado explícitamente por el usuario.
- Efecto de la contaminación en tesoro, salud o mortalidad — solo el techo de satisfacción.
- Contaminación bloqueando el asentamiento de un tile zonificado — `settleEmptyZonedTiles` no cambia.

### Persistencia
`Chunk.pollutionLevel` **no se persiste** — es 100% derivado, se recomputa en la primera pasada de
`UtilitySystem` tras cargar un mundo. Sin bump de `ChunkDeltaIO.FORMAT_VERSION`.

### Nota de medición
Resuelto: la máquina solo tenía JDK 11 en el `PATH` (el fork de JMH fallaba con
`UnsupportedClassVersionError` aunque el toolchain de compilación sí resolvía 17). Se instaló y
registró JDK 17 como default del sistema. `UtilitySystemBenchmark` corrido: **2334.5 µs/op**, sin
cambio perceptible frente a la cifra pre-contaminación (~2.4 ms/op) — la pasada nueva reutiliza el
mismo `frontier`/`depthOf`, no agrega un recorrido. Cifra registrada en `docs/architecture.md` §10.

---

## Densidad evolutiva de edificios — ✅ Completo (fuera de la secuencia MVP del spec, pedido explícito del usuario)

### Motivación
Último pendiente real del pedido original sobre edificios cívicos/densidad, diferido dos veces por
no tener enganche arquitectónico con `BuildingEconomics`/`UtilitySystem`. Antes de esto,
`PopulationSystem` crecía cada edificio hasta un tope plano (`RESIDENTIAL_CAPACITY=60`,
`JOB_CAPACITY=40`) y ahí se quedaba para siempre: una ciudad con servicios de lujo y una sin ellos
terminaban con edificios idénticos. Tres decisiones se cerraron con el usuario: disparador orgánico
(lleno + satisfecho, no desbloqueo global ni densidad pintada por el jugador), representación como
un campo `densityLevel` por edificio (no `BuildingType` nuevos), y los cuatro efectos: capacidad,
impuestos, requisito de servicios más exigente, semilla de variante visual.

### Diseño: el techo de satisfacción ya existente *es* el requisito de servicios
`PopulationSystem.satisfactionCeiling` ya produce tres mesetas (60 sin prosperidad, 85 con
prosperidad, 100 con lujo) que la contaminación ya baja (ver sección anterior). En vez de inventar
un chequeo de cobertura nuevo, `BuildingDensity` (nuevo, `sim.population`) define umbrales de
satisfacción por nivel —promoción 75/92, degradación 65/88, con histéresis para que un edificio no
oscile cerca del límite— así que "un rascacielos necesita mejor cobertura que una casa" cae solo del
modelo existente. Subir de nivel exige además estar lleno (`population >= capacidad del nivel
actual`), que da la demora natural sin un contador de ticks nuevo.

`BuildingRegistry` gana un `byte[] densityLevel` (mismo molde que `satisfactionPercent`).
`PopulationSystem.growExistingBuildings` llama a `updateDensityLevel` justo después de actualizar
satisfacción y antes de crecer — promueve o degrada como máximo un nivel por tick, recortando
población/empleos a la capacidad del nivel nuevo en una degradación. `growResidential`/
`growWorkplace` leen la capacidad de `BuildingDensity` en vez de las constantes planas
(`RESIDENTIAL_CAPACITY`/`JOB_CAPACITY` se conservan como los valores del nivel 0, sin romper nada
que las usaba). `GovernmentFinanceSystem` pondera cada edificio por `BuildingDensity.wageMultiplier`
antes de sumarlo a la base gravable — un rascacielos tributa 1.8× por habitante que una casa chica,
sin tocar la firma de `GovernmentFinance.collectRevenue`. `variantIndex(tileX, tileY, level,
variantCount)` es un hash entero puro y determinista (no almacenado — el mundo ya es determinista
desde la semilla), listo para que `game-client` elija sprite el día que dibuje edificios.

### Fuera de alcance
- Renderizado: `game-client` sigue dibujando solo terreno; `variantIndex` no tiene consumidor
  todavía.
- Bumpear la versión de render del chunk al subir de nivel — no hay nada que redibujar hoy. Primer
  enganche pendiente cuando el cliente empiece a dibujar edificios.
- Densidad pintada por el jugador (zonas de densidad baja/media/alta) — descartada explícitamente.
- Que `incomeLevel` (ya existente, sin uso desde spec §22) haga algo — sigue sin escritor, es un
  concepto distinto (riqueza de residentes) y no se mezcló con `densityLevel`.

### Persistencia
`BuildingRegistryIO.FORMAT_VERSION` 3 → 4, `densityLevel` como un byte más por edificio activo.
Saves viejos no cargan.

### Medición
`PopulationSystemBenchmark` (JDK 17 ya instalado, ver la nota de la sección anterior): **103.5
µs/op**, sube desde los ~43 µs/op de antes — el costo de `updateDensityLevel` evaluándose por
edificio activo cada tick. Sigue muy por debajo del presupuesto de sub-sistema (spec §41); no
ameritó una pasada de optimización. Cifra registrada en `docs/architecture.md` §10.

---

## MVP 0.6 — Regional Passenger Transport — ✅ Completo (con alcance ajustado, ver abajo)

### Qué pide el spec
Rail intercity, autobuses, migración, turismo, prototipo de aeropuerto (§16, §19).

### Ajuste de alcance (decidido con el usuario)
El MVP completo son varios sistemas nuevos a la vez (grafo de pasajeros, flujo real sobre aristas,
turismo). Se acotó esta pasada a **Migración + Aeropuerto** — los dos con patrón ya probado en el
código — y se dejó **rail intercity, autobuses y turismo** para una pasada aparte, porque requieren
el primer uso real de las aristas de `RegionalGraph` (`addEdge` nunca se llamó hasta ahora) y un
sistema de flujo de pasajeros nuevo que merece su propio diseño.

### Migración — ✅ Completo: ajuste de fórmula en `PopulationSystem`, sin sistema nuevo
`PopulationSystem.settleEmptyZonedTiles` sembraba cada tile residencial con una población fija
(`SEED_POPULATION = 6`) en cuanto cumplía las condiciones locales (calle+luz+agua). Ahora la escala
un `migrationMultiplier(cities, cityId)` con dos señales independientes, en rango `[0.5, 2.5]`:

- **`jobSurplusRatio`** (local, spec: "Industry affects jobs, jobs affect migration"): reutiliza
  los totales que `recomputeCityTotals` ya calcula cada tick, sin scan nuevo —
  `(totalJobs - totalResidentialPopulation) / totalJobs`, recortado a `[0,1]`.
- **`tradeActivityRatio`** (externa, spec §29 "migration pressure" / "should be simulated
  statistically rather than represented as physical off-map cities"): lee
  `GoodsLedger.exportedLastTick(good)` sumado sobre los 8 bienes — la "atracción del `MarketSystem`
  externo" prometida en el ajuste original, sin inventar ninguna entidad de mundo externo nueva.

Solo la siembra residencial se escala — comercial/industrial siguen con `SEED_JOBS` fijo (los
empleos no son migrantes). Sin nuevo parámetro en `PopulationSystem.tick`, sin campo persistido
nuevo, sin cola/presupuesto compartido entre tiles (cada una calcula su propio multiplicador de
forma independiente, evitando el problema de determinismo por orden de iteración que tendría un
budget compartido).

**Efecto real medido**: en el escenario `--bench city` (sin empleos al arranque), la población a
Year 100 bajó de 22 a 16 — es el comportamiento nuevo esperado (la siembra inicial ahora es más
chica sin superávit de empleos ni exportaciones), no una regresión.

### Aeropuerto — ✅ Completo: mismo patrón que `Port` (0.5), solo gateway de carga por ahora
`BuildingType.AIRPORT` (id 32, `COUNT` 32→33). En esta pasada es **solo un gateway de carga** —
pasajeros/turismo/migración por aire quedan fuera hasta la fase de flujo de pasajeros, igual que
`PortRegistry.passenger_capacity` ya quedó deliberadamente diferida en 0.5 (no se agrega una
columna sin lector).

- `AirportRegistry` (nuevo, `sim.trade`) — mismo molde que `PortRegistry`: `gates`/
  `cargoCapacityPerTick`/`customsEfficiencyPercent`, indexado por `buildingId`. Sin columna de
  pasajeros.
- `BuildAirportCommand` — calca `BuildPortCommand` con dos diferencias: **sin chequeo de costa**
  (un aeropuerto no necesita agua adyacente) y **gate de población** (`unlockPopulation = 3000`,
  más alto que Banco Central — spec §19: "a small town should not automatically support an
  international airport"), mismo patrón de tier-lock que `BuildCivicBuildingCommand`. Costo 15000
  (más caro que Puerto).
- `MarketSystem.runGateways` gana una tercera rama junto a `TRADE_DEPOT`/`PORT`, leyendo
  concurrencia/capacidad/bonus de aduana de `AirportRegistry` en vez de las constantes planas.
- **Bug fix necesario, no oportunista**: `DemolishCommand.removeMatchingGraphNode` solo borraba
  nodos `EXTERNAL_MARKET` — demoler un Puerto ya dejaba su nodo huérfano en `RegionalGraph` desde
  0.5. Con Aeropuerto como tercer tipo de gateway, se corrigió mapeando el tipo de edificio al tipo
  de nodo correspondiente (`TRADE_DEPOT→EXTERNAL_MARKET`, `PORT→PORT`, `AIRPORT→AIRPORT`).
- Persistencia: `airports.dat` (nuevo), mismo molde atómico que `ports.dat`. Ningún formato
  existente cambió de versión.

### Fuera de alcance de la primera pasada (Migración + Aeropuerto)
- Pasajeros de verdad (aristas de `RegionalGraph`, matrices de viaje spec §16, autobuses, rail
  intercity) — necesitaban `addEdge`/`travel_time`/`congestion`/`reliability`, hasta entonces
  inexistentes en la práctica (`addEdge` nunca se llamaba). **Resuelto en la segunda pasada, abajo.**
- `AirportRegistry`/`PortRegistry` `passengerCapacity` — sigue sin lector.
- Turismo como ingreso de ciudad. **Resuelto en la segunda pasada, abajo.**
- Requisito de "regional accessibility" del aeropuerto (spec §19) — simplificado a solo población,
  mismo criterio que ya se usó para Banco Central (solo tesorería).
- `MarketSystem.updateTransportCosts` sigue sin considerar nodos `PORT`/`AIRPORT`/`STATION` — gap
  preexistente de 0.5, no introducido por este cambio.

## Segunda pasada de MVP 0.6: Rail, rutas de autobús y turismo

### Rail — `RAIL_TERMINAL`, cuarto gateway de carga
Repite exactamente el patrón de `BuildAirportCommand`/`AirportRegistry` (spec §18: "excels at bulk
cargo") con tres diferencias: sin chequeo de costa (como Aeropuerto), sin gate de población (a
diferencia de Aeropuerto — solo el spec §19 "small town" aplica a aeropuertos, no a rail) y sin
bono de aduana (`StationRegistry` tiene solo `platforms`/`cargoCapacityPerTick`, no
`customsEfficiencyPercent` — es comercio doméstico entre ciudades del mismo mundo, no cruza una
frontera internacional). `MarketSystem.runGateways` gana una cuarta rama. Registra un nodo
`NodeType.STATION` (ya declarado desde MVP 0.3/0.5, sin uso hasta ahora). Persistencia:
`stations.dat`, mismo molde que `ports.dat`/`airports.dat`.

### Autobuses — la mecánica nueva: diseño de rutas real
Pedido explícito del usuario: no un número agregado, sino colocar una Central (`BUS_DEPOT`) y
Estaciones/paradas (`BUS_STOP`) y armar una ruta ordenada conectándolas. Es el **primer uso real**
de `RegionalGraph.addEdge` — Puerto/Aeropuerto/Rail son los tres nodos previos sin aristas.

- `NodeType.BUS_STOP` (nueva constante, distinta de `STATION` — "estación" en español nombra tanto
  la parada de bus como la terminal de tren; en inglés quedan como identificadores separados para
  evitar la ambigüedad).
- `BusRouteRegistry` (nuevo) — una ruta es un depósito + entre 2 y `MAX_STOPS_PER_ROUTE=6` paradas
  ordenadas. En vez de una lista de longitud variable (que rompería la convención SoA del proyecto),
  usa el mismo truco ya documentado en `CLAUDE.md` para categorías fijas y pequeñas: un array
  aplanado `id * MAX_STOPS_PER_ROUTE + slot`. **Sin persistencia** — sus rutas referencian aristas de
  `RegionalGraph`, que tampoco se persiste hoy (gap preexistente de Puerto/Aeropuerto/Rail);
  persistir uno sin el otro dejaría estado inconsistente al cargar.
- `CreateBusRouteCommand` — valida tipos/misma ciudad/tope de rutas por depósito
  (`BuildingEconomics.capacity(BUS_DEPOT)`, reutiliza la columna `CAPACITY` existente con un
  significado nuevo: "cuántas rutas simultáneas puede despachar"), luego llama
  `graph.addEdge(EdgeType.ROAD, ...)` una vez por cada par consecutivo de paradas. `EdgeType.ROAD`
  porque los autobuses corren sobre calles, no rieles.
- **Efecto de juego**: nuevo `WorldConstants.SERVICE_TRANSIT` (bit 12, ya cabe en el `int` existente
  de `Chunk.serviceFlags`, sin bump de formato). `UtilitySystem` solo da cobertura de tránsito a las
  paradas que efectivamente pertenecen a **una ruta activa** — una parada aislada sin ruta no da
  nada, recompensando mecánicamente diseñar rutas de verdad en vez de solo construir paradas
  sueltas. Esto exigió extraer la parte de BFS-desde-frontera-sembrada de `floodFillFromSources` a
  un método compartido (`runBfs`), reutilizado por un nuevo `floodFillFromRouteStops` que filtra por
  `busRoutes.isStopInAnyActiveRoute`. `PopulationSystem.PROSPERITY_MASK` gana `SERVICE_TRANSIT` —
  mismo techo 85 que el resto de servicios de prosperidad, cero lógica nueva.

### Turismo — ingreso de ciudad dentro de `GovernmentFinanceSystem`
Extiende el scan por-ciudad que `collectForOneCity` ya hace (no un sistema nuevo): cuenta edificios
`MUSEUM`+`PARK` activos de la ciudad (ya visitados en el mismo loop) y agrega
`min(atracciones, 5) * min(población, 5000) * 0.02` al ingreso cívico ya existente. **Simplificación
explícita**: es "tiene Museo/Parque activos", no "población dentro del radio de cobertura de
Museo/Parque" — esto último exigiría pasarle `ChunkStore` a `GovernmentFinanceSystem.tick` (que hoy
no lo recibe) por una ganancia marginal, así que se documenta como la lectura dada a "cobertura" en
vez de implementarla literalmente por radio.

### Fuera de alcance de esta segunda pasada
- Flujo/congestión real sobre las aristas de rutas — sin consumidor todavía, igual que las de
  Puerto/Aeropuerto/Rail. Base para una futura fase de "pasajeros de verdad" (spec §16).
- Persistencia de `BusRouteRegistry`/aristas de `RegionalGraph` (ver nota arriba).
- Reordenar/editar una ruta ya creada, o eliminarla sin demoler sus paradas.
- Turismo ponderado por radio de cobertura real (ver simplificación arriba).
- Autobuses visibles/animados — `game-client` sigue sin dibujar nada de esto.

---

## Renderizar zonas, carreteras y edificios en `game-client` — ✅ Completo (fuera de la secuencia MVP del spec, pedido explícito del usuario)

### Motivación
Tras ~15 fases de trabajo en `simulation-core`, `game-client` seguía dibujando solo terreno —
`Chunk.zoneType`/`roadType`/`buildingId` estaban disponibles sin ningún consumidor visual desde
Fase 2. No hay ningún asset de arte en el repo (`assets/atlas/` vacío); todo el terreno ya era color
sólido procedural vía `PlaceholderAtlasGenerator`. Esta pasada extiende exactamente ese mismo
mecanismo (más celdas de color) en vez de introducir un pipeline de arte real.

### Diseño: una sola malla por chunk, hasta 2 quads por tile
Spec §44.2/44.3 describen capas separadas (terrain/infrastructure/buildings/...) con caché propio
por capa. Se simplificó deliberadamente a una sola `ChunkMesh` por chunk que puede emitir **hasta 2
quads por tile**: el quad de terreno (como siempre) + un quad de superposición opcional —
carretera, o edificio (coloreado por categoría), o lote zonificado vacío (tinte semitransparente) —,
en ese orden de prioridad, mutuamente excluyentes en la práctica. Evita 4 buffers/draw-calls por
chunk y sigue funcionando con el único `chunk.version()` que ya existe: ni `roadType` ni `zoneType`
ni `buildingId` tienen dirty-tracking propio, así que una sola malla invalidada por ese mismo
`version()` ya cubre los tres. `ChunkMesh.MAX_QUADS_PER_CHUNK = TILES_PER_CHUNK * 2` reemplaza el
supuesto fijo de 1 quad/tile; `rebuild` ahora cuenta un `quadCount` real (varía por chunk) y
`render` dibuja solo esos índices.

`PlaceholderAtlasGenerator.Atlas` ganó `byZoneType`/`byRoadType`/`byBuildingCategory` sobre la misma
`Texture` (nunca dos binds por frame). Las 3 celdas de zona se hornean con **alfa ≈0.5 en el propio
Pixmap** (no en el vértice) — un lote vacío se lee como un tinte sobre el terreno, no un tile opaco;
el resto de las celdas quedan opacas. `buildingCategoryIndex(byte buildingType)` agrupa los 36
`BuildingType` en 8 categorías de color (Residencial/Comercial/Industrial/Utilidad/Cívico/Lujo/
Transporte/Institucional) para no necesitar 36 colores distintos.

`WorldRenderer.render`/`ChunkRenderCache.getOrBuild`/`ChunkMesh.rebuild` ganaron un parámetro
`BuildingRegistry` — necesario para saber el `BuildingType` real detrás de `buildingId` (no solo que
existe algo). Un solo call site en `AtlasGame.render()`: `world.buildings()` ya estaba a un método
de distancia. La invalidación sigue basada solo en `chunk.version()` — un edificio cambiando de
`densityLevel`/población no re-dispara un rebuild, correcto para esta pasada porque el color solo
depende del `BuildingType` (fijo desde que se construye), no del nivel de densidad. Encaje futuro
para altura/sprite por densidad.

**Bug latente arreglado, no oportunista**: `AtlasGame.render()` ya hacía `glEnable(GL_BLEND)` pero
nunca llamaba `glBlendFunc` — daba igual porque nada usaba alfa &lt; 1 hasta ahora. El tinte de zona
semitransparente sí lo necesita, así que se agregó `glBlendFunc(GL_SRC_ALPHA,
GL_ONE_MINUS_SRC_ALPHA)` junto al `glEnable` que ya estaba.

### Siembra de demo en `AtlasGame.create()`
El demo no fundaba ninguna ciudad ni colocaba nada — puro sandbox de terreno. Se agregó una siembra
mínima calcada de `HeadlessMain.runCityGrowthScenario` (mismo `findLandRun` + comandos vía
`world.submitCommand`): funda una ciudad, una carretera corta, 3 zonas (residencial/comercial/
industrial) y planta de luz/torre de agua. Verificado visualmente (`screencapture -x`): carretera
gris visible, tinte dorado semitransparente en el lote industrial aún vacío, edificios asentados en
verde/azul/caqui según categoría, 60 FPS estables, comandos 15/0 (igual que el escenario headless).

### Fuera de alcance
- Sprites/arte real — sigue siendo color sólido procedural, ahora con más colores.
- Altura/extrusión 3D de edificios por `densityLevel`.
- Capas de `pollutionLevel`/`serviceFlags` — son estado derivado que a propósito no marca el chunk
  dirty (para no auto-invalidar el caché de render en cada recomputo); necesitan su propio mecanismo
  de invalidación, no encajan en el `chunk.version()` de esta pasada.
- Conectividad visual de carreteras (esquinas/cruces) — color plano por tile, sin sprites direccionales.
- Rutas de autobús / aristas de `RegionalGraph` dibujadas.
- Overlay de datos (población, densidad, satisfacción) en `DebugOverlay`.

---

## Fundido al aparecer un chunk nuevo — ✅ Completo (fuera de la secuencia MVP del spec, pedido explícito del usuario)

### Motivación
El usuario preguntó si se podían "alinear los bordes de chunk como en TheoTown". El **contenido**
del terreno ya no tiene costura: `ProceduralGenerator` muestrea el ruido en coordenadas de tile
absolutas, no relativas al chunk, así que dos chunks vecinos generan terreno perfectamente continuo
— confirmado por inspección de código, sin necesidad de tocar `simulation-core`. Lo que faltaba era
pulir la **aparición**: `WorldRenderer` dibujaba un chunk recién integrado por
`ChunkManager.integrateReadyChunks` opaco y completo desde el primer frame — un "pop" instantáneo.

### Diseño: alpha-fade por chunk vía shader propio, no por vértice
Rehacer el color por vértice habría forzado un `rebuild()` en cada frame del fundido, rompiendo el
invariante central de `ChunkMesh` (solo se reconstruye cuando `chunk.version()` cambia). En vez de
eso, `WorldRenderer` reemplazó `SpriteBatch.createDefaultShader()` por un `ShaderProgram` propio
(`ChunkShaderSource`, GLSL inline — mismo criterio de "todo código-generado, sin assets" que
`PlaceholderAtlasGenerator`) con un uniform extra `u_chunkAlpha`, multiplicado en el canal alfa del
fragment shader y seteado una vez por chunk justo antes de su `mesh.render(shader)` — mismo costo
que ya tenía el draw-call por chunk. Reutiliza el blending (`GL_BLEND` +
`glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)`) que ya se había activado para el tinte de zona.

`ChunkMesh` gana `spawnedAtMillis` (vía `TimeUtils.millis()`, idioma estándar de libGDX) **seteado
una sola vez, en el constructor** — nunca en `rebuild()`. Clave: un chunk que ya está cargado y solo
cambia de contenido (el jugador construye una carretera) no vuelve a fundirse, solo el chunk que
recién aparece por streaming/pan. `currentAlpha()` hace un fundido lineal de 300ms.

### Bug de cámara encontrado y arreglado en la misma pasada
Al verificar con `screencapture -x`, la ventana mostraba mayormente negro con solo un triángulo de
mundo visible en una esquina — parecía una regresión del fundido, pero comparado directamente contra
el build ya commiteado (sin este cambio) el patrón era **idéntico**, descartándolo como causa
(confirmado también con una traza de diagnóstico temporal: el alfa por chunk ya estaba en 1.0). El
usuario confirmó la pista real: al mover la cámara con las flechas, el mapa aparecía normalmente —
o sea, el contenido estaba cargado, la cámara arrancaba apuntando a otro lado.

Causa real: `OrthographicCamera.setToOrtho(...)` resetea `camera.position` al centro del viewport en
píxeles cada vez que se llama — y LWJGL3 dispara un evento `resize()` inicial justo después de
`create()`, que vuelve a llamar `setToOrtho` y **pisa silenciosamente** cualquier centrado de cámara
hecho en `create()` antes del primer frame. `AtlasGame` gana `demoCenterTileX`/`demoCenterTileY`
(seteados por `seedDemoSettlement()` al terminar de sembrar) y `recenterCameraOnDemoSettlement()`,
llamado tanto al final de `seedDemoSettlement()` como al final de `resize()` — así sobrevive al
resize inicial. Verificado: la cámara ahora arranca centrada sobre el asentamiento demo sin necesitar
mover nada (`visible chunks: 6` cubriendo toda la ventana, antes solo `4` en una esquina).

### Fuera de alcance
- Fundido por edición de contenido — solo aplica a la primera aparición del chunk.
- Easing no lineal.
- Si el jugador redimensiona la ventana después de haber paneado manualmente, `resize()` vuelve a
  centrar sobre el asentamiento demo en vez de respetar el pan — aceptable por ahora, es wiring de
  demo, no una función real de cámara persistente.

---

## Preguntas resueltas

1. ~~`TradeDepot` como gateway temprano~~ — resuelto (MVP 0.3): placeable desde 0.3.
2. ~~Alcance de bienes en 0.3~~ — resuelto (MVP 0.3): los 8 bienes completos desde el arranque.
3. ~~`PortRegistry` como registro secundario vs. columnas opcionales en `BuildingRegistry`~~ —
   resuelto (MVP 0.5): registro secundario indexado por `buildingId`, ver la sección de MVP 0.5
   arriba para el detalle de implementación.
