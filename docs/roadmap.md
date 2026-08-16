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

## MVP 0.3 — Regional Economy

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

## MVP 0.4 — Freight

### Qué pide el spec
Grafo de flete, camiones, flete ferroviario, terminales de carga, envíos (shipments), cuellos de
botella (§14, §15).

### Ajuste: reutilizar el patrón de streaming de `ChunkManager` para los shipments
El spec §15 describe el LOD de flete como: shipment abstracto lejos → se convierte en entidad
visual de tren/camión cerca del área activa → vuelve a ser shipment abstracto al alejarse. Esto es
**estructuralmente el mismo problema** que `ChunkManager` ya resuelve para chunks (generar/cargar
cerca del foco, expulsar lejos, sin mantener todo el mundo activo). Ajuste concreto: nombrar el
componente `ShipmentLODManager` y calcarle el modelo de `ChunkManager` — cola de prioridad por
distancia al área activa, integración acotada por tick (`integrateReadyChunks` → equivalente para
shipments), en vez de diseñarlo desde cero como un sistema nuevo.

### Ajuste de rendimiento (aplicando la lección transversal)
`RegionalGraph` con flujo real (congestión, capacidad, cuellos de botella) es el candidato más
serio a repetir el patrón `UtilitySystem`: es un grafo que puede necesitar recomputar
alcanzabilidad/congestión con cada envío. Ajuste: diseñarlo desde el principio con la misma
distinción que ya aplicamos en Fase 2 —
- estado **autoritativo** (aristas, capacidad declarada, shipments activos) → cambia por comando,
  marca dirty;
- estado **derivado** (congestión actual, costo efectivo de una arista) → recalculado, nunca marca
  dirty él mismo (la misma trampa que documentamos en `RoadNetwork`/`UtilitySystem`: si el
  recomputo derivado dispara su propia invalidación, se auto-perpetúa).

Y con seguimiento de "sucio" por arista (similar al `LongIntHashMap` de versión-por-chunk de
`RoadNetwork`) para no recalcular congestión de aristas por las que no pasó ningún envío nuevo
desde el último tick.

### Persistencia
`routes.dat` (ya nombrado en spec §31): shipments activos + estado de aristas. Los shipments en
tránsito son estado con el que hay que tener cuidado especial al cargar una partida — un shipment
cuya ruta ya no existe (arista demolida mientras el juego estaba cerrado) necesita una regla de
recuperación explícita (recalcular ruta o cancelarlo), no un crash al cargar.

### Qué medir antes de cerrar la fase
- Benchmark JMH de `ShipmentLODManager` y de recomputo de congestión, **incluido en el mismo PR**
  que los introduce — este es el punto donde de verdad importa no repetir el error de Fase 2.

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

## Preguntas abiertas para decidir contigo antes de implementar 0.3

1. **`TradeDepot` como gateway temprano**: ¿de acuerdo con introducirlo en 0.3 como propongo, o
   prefieres que 0.3 no tenga ningún gateway físico y el mercado externo sea puramente estadístico
   (sin building) hasta que exista una carretera fronteriza/puerto/aeropuerto real?
2. **Alcance de bienes en 0.3**: ¿los 8 bienes completos desde el principio, o empezar con un
   subconjunto (p. ej. Food/Timber/Ore/Steel) y añadir el resto en 0.4 según haga falta para
   flete?
3. **`PortRegistry` como registro secundario** vs. columnas opcionales en `BuildingRegistry`: la
   recomendación de este documento es un registro secundario indexado por `buildingId`, pero es
   una decisión de diseño con impacto en cómo se hace la persistencia — vale la pena confirmarla
   antes de escribir `BuildingRegistryIO`-equivalente para puertos.

Ninguna de estas bloquea el trabajo de exploración/diseño, pero sí conviene resolverlas antes de
fijar el formato binario de `economy.dat` (spec §32: los formatos de guardado también se
versionan explícitamente, así que ir y venir sobre el esquema después de implementarlo cuesta una
migración, no una edición).
