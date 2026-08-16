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

## MVP 0.5 — Port

### Qué pide el spec
Idoneidad de costa, construcción de puertos, comercio marítimo, barcos como entidades LOD,
capacidad de importación/exportación (§17).

### Ajuste
Puerto = nuevo `BuildingType` + nodo especializado en `RegionalGraph` (ya existe desde 0.3) con
`berths/cargo_capacity/passenger_capacity/storage/customs_efficiency` como campos adicionales —
mismo patrón de extensión que ya usamos para `BuildingRegistry` (no hace falta un registro nuevo,
solo más columnas SoA opcionales o un registro secundario `PortRegistry` indexado por
`buildingId` si los campos no aplican a la mayoría de edificios — más limpio que engordar
`BuildingRegistry` con columnas que el 99% de edificios no usa).

Barcos como entidades LOD: reutilizan `ShipmentLODManager` de 0.4 (un barco es un shipment cuya
ruta pasa por una arista de tipo "ruta marítima" en el grafo) — no un sistema aparte.

Idoneidad de costa: derivable de las capas de terreno ya existentes desde Fase 1
(`TERRAIN_SHALLOW_WATER`/`TERRAIN_DEEP_WATER` adyacentes) sin generación adicional. El spec (§5.2)
menciona "natural harbor suitability" como paso de generación separado — se puede diferir
indefinidamente y aproximar con la regla de adyacencia simple hasta que el terreno cueste tenerlo.

### Qué medir
Nada nuevo estructuralmente caro si el punto anterior (grafo regional con dirty-tracking) se hizo
bien en 0.4 — puertos solo añaden nodos, no un algoritmo nuevo.

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

## Preguntas resueltas (MVP 0.3)

1. ~~`TradeDepot` como gateway temprano~~ — resuelto: placeable desde 0.3.
2. ~~Alcance de bienes en 0.3~~ — resuelto: los 8 bienes completos desde el arranque.

## Pregunta abierta para decidir antes de implementar MVP 0.5 (Port)

3. **`PortRegistry` como registro secundario** vs. columnas opcionales en `BuildingRegistry`: la
   recomendación de este documento es un registro secundario indexado por `buildingId`, pero es
   una decisión de diseño con impacto en cómo se hace la persistencia — vale la pena confirmarla
   antes de escribir `BuildingRegistryIO`-equivalente para puertos. No bloquea nada de MVP 0.4
   (Freight); solo hace falta resolverla cuando llegue el turno de Port.
