# Procedural Regional City Builder — SPEC

## 0. Working Title
**Project Atlas City**

## 1. Vision

Build a 2D/isometric city-builder inspired by games such as TheoTown, but designed around a **procedurally generated regional world** rather than isolated maps.

The core fantasy is:

> Build a city that exists inside a larger persistent world, where geography, resources, logistics, trade and player-founded settlements matter.

Ports, airports, highways and railways must not be decorative abstractions. They must connect the player's city to actual regional production, consumption, migration and trade flows.

The first version should prioritize:

- low hardware requirements;
- mobile support;
- modest desktop/laptop support;
- deterministic procedural generation;
- finite worlds at launch;
- efficient streaming;
- simulation Level of Detail (Simulation LOD);
- persistent player modifications;
- scalable architecture that could later support much larger or effectively endless worlds.

---

# 2. Product Pillars

## Foundational Rule — The World Starts Empty

The default game mode begins with a **virgin procedural world**.

The seed determines nature, not civilization.

```text
SEED
  ↓
terrain
rivers
coasts
forests
resources
fertility
  ↓
EMPTY WORLD
  ↓
PLAYER
  ↓
cities
roads
railways
ports
airports
industry
```

Civilization is the player's creation.

## Initial World State

Every new standard game starts at:

```text
Population: 0
Cities: 0
Buildings: 0
Roads: 0
Railways: 0
Ports: 0
Airports: 0
Power infrastructure: 0
Water infrastructure: 0
Industry: 0
Agriculture: 0
```

The only generated content is natural:

```text
terrain
water
rivers
coastlines
forests
fertility
stone
timber
ore
fuel resources
fishing grounds
climate potential
natural crossings
natural harbors
```

There are no ruins, abandoned roads, villages, towns, cities, borders, prebuilt ports, or artificial structures in the standard mode.

The player's first meaningful action is not "manage a city"; it is:

> Choose where civilization begins.


## 2.1 Geography Matters

The procedural generator must produce meaningful geography:

- coastlines;
- rivers;
- lakes;
- plains;
- forests;
- hills;
- mountains;
- fertile areas;
- mineral deposits;
- natural harbors.

Terrain must influence city development.

Examples:

- flat fertile plains favor agriculture;
- mountains favor mining but increase transport costs;
- deep coasts favor ports;
- rivers reduce transport costs and improve settlement attractiveness;
- valleys constrain urban expansion;
- islands make shipping and aviation strategically important.

---

## 2.2 Infrastructure Has Purpose

Infrastructure must participate in real systems.

### Roads
Used for:
- commuting;
- freight;
- regional travel;
- service coverage;
- migration.

### Rail
Used for:
- bulk freight;
- commuters;
- intercity passengers;
- resource transport.

### Ports
Used for:
- imports;
- exports;
- bulk freight;
- container freight;
- passenger ferries where applicable.

### Airports
Used for:
- long-distance passengers;
- tourism;
- business travel;
- migration;
- high-value cargo.

Infrastructure capacity and congestion should affect the economy.

---

## 2.3 The City Is Not an Island

The player operates inside a larger regional economy, but the playable world begins **completely uninhabited**.

The procedural generator creates:

- geography;
- resources;
- rivers;
- coastlines;
- forests;
- mineral deposits;
- fertility;
- natural transport opportunities.

It does **not** create cities, towns or permanent settlements.

Every city, town, industrial area, port, airport and transport corridor inside the playable world is created by the player.

External economic demand may exist abstractly beyond the world boundaries so that international trade can function without requiring pre-generated cities to be physically generated.

The player's cities should be able to specialize.

Examples:

- industrial port city;
- mining town;
- agricultural hub;
- tourism city;
- logistics center;
- university/technology city;
- commuter satellite city.

---

## 2.4 Large World, Small Active Simulation

The entire world must **not** run at full simulation fidelity.

The engine must use multiple simulation levels.

### Tier 0 — Active Area
Highest fidelity.

Simulates:
- individual road vehicles where visible;
- active trains;
- nearby ships/aircraft;
- buildings;
- utilities;
- local traffic;
- citizens in simplified/aggregated form;
- construction;
- service coverage.

### Tier 1 — Active Region
Medium fidelity.

Simulates:
- road traffic aggregated by corridor;
- rail demand;
- district-level population;
- employment;
- production;
- service demand;
- freight flows.

### Tier 2 — Distant Region
Low fidelity.

Simulates:
- total population;
- employment;
- production;
- consumption;
- imports/exports;
- city growth;
- macro travel demand.

### Tier 3 — Dormant World
Very low fidelity.

Only stores or derives:
- settlement summary;
- economy summary;
- resources;
- scheduled macro-events;
- trade relationships.

No individual entities exist.

---

# 3. Initial World Size

The first release should intentionally use finite worlds.

Recommended presets:

| Preset | World Tiles | Target |
|---|---:|---|
| Small | 1024 × 1024 | low-end mobile |
| Medium | 2048 × 2048 | standard mobile / low-end PC |
| Large | 4096 × 4096 | PC / high-end mobile |
| Experimental | 8192 × 8192 | opt-in |

The exact physical scale per tile can be adjusted during prototyping.

Recommended starting abstraction:

**1 tile = 16 meters**

This gives approximately:

- Small: 16.4 × 16.4 km
- Medium: 32.8 × 32.8 km
- Large: 65.5 × 65.5 km
- Experimental: 131 × 131 km

However, the simulation should avoid loading the full grid.

---

# 4. World Partitioning

Divide the world into chunks.

Recommended initial chunk size:

**32 × 32 tiles**

Example:

```text
WORLD
┌─────┬─────┬─────┬─────┐
│ C00 │ C10 │ C20 │ C30 │
├─────┼─────┼─────┼─────┤
│ C01 │ C11 │ C21 │ C31 │
├─────┼─────┼─────┼─────┤
│ C02 │ C12 │ C22 │ C32 │
└─────┴─────┴─────┴─────┘
```

Each chunk must be addressable by:

```text
chunk_x
chunk_y
```

Chunk generation must be deterministic:

```text
chunk = Generate(world_seed, chunk_x, chunk_y)
```

The same:

- seed;
- generator version;
- chunk coordinates;

must always produce the same virgin chunk.

---

# 5. Procedural Generation

## 5.1 Generation Inputs

```text
WorldSeed
GeneratorVersion
WorldSize
ClimatePreset
SeaLevel
ResourceDensity
ExternalMarketStrength
```

---

## 5.2 Generation Pipeline

Suggested order:

```text
Seed
 ↓
Continental shape
 ↓
Elevation
 ↓
Water / coastline
 ↓
Rivers / drainage
 ↓
Temperature
 ↓
Humidity
 ↓
Biomes
 ↓
Soil fertility
 ↓
Natural resources
 ↓
Natural harbor suitability
 ↓
Habitation suitability
 ↓
Natural transport corridors
 ↓
Player-founded development potential
```

---

## 5.3 Noise Layers

Use deterministic layered noise.

Potential layers:

- continentalness;
- elevation;
- erosion approximation;
- temperature;
- humidity;
- fertility;
- mineral likelihood;
- forest density.

Possible implementations:

- OpenSimplex;
- FastNoiseLite;
- custom deterministic value noise.

Avoid expensive world-generation algorithms in the MVP.

---

# 6. Rivers

Rivers are strategically important and should not be purely decorative.

MVP behavior:

- generated using elevation-aware paths;
- flow downhill toward ocean/lakes;
- increase fertility nearby;
- create bridge requirements;
- increase settlement attractiveness;
- optionally support navigable waterways later.

Do not simulate fluid dynamics.

---

# 7. Resources

Resources exist geographically.

Initial resources:

### Agriculture
- fertile soil;
- grain;
- livestock suitability.

### Forestry
- timber.

### Mining
- coal;
- iron;
- stone.

### Energy
- oil/gas placeholder if desired;
- wind suitability;
- solar suitability.

### Maritime
- fishing grounds.

Resource extraction creates freight demand.

Resources must also constrain infrastructure progression.

For example:

```text
timber
→ simple bridges / early structures

stone + gravel
→ improved roads / foundations

steel
→ railways / bridges / heavy industry

cement / concrete
→ modern roads / bridges / dense construction

fuel / energy
→ mechanized construction / logistics
```

This means geographic resource access influences how quickly and cheaply the player's civilization can expand.

Example:

```text
Iron Mine
   ↓
2,000 t/day ore
   ↓
Rail / Road
   ↓
Steel Mill
   ↓
Steel
   ↓
Factory / Export
```

---

# 8. Virgin World Principle

The playable world begins with **zero cities and zero permanent settlements**.

The procedural generator may create only natural and geographic features such as:

- terrain;
- rivers;
- lakes;
- coastlines;
- forests;
- fertility;
- mineral deposits;
- fishing grounds;
- natural harbors;
- mountain passes.

The generator must never pre-place:

- cities;
- towns;
- industrial zones;
- roads;
- railways;
- ports;
- airports;
- permanent human infrastructure.

The core early-game decision is:

> Where should the first city be founded?

The player must evaluate geography and resources before selecting a location.

Examples:

```text
Fertile valley
→ agricultural city opportunity

Deep natural harbor
→ future port city opportunity

Iron + coal nearby
→ industrial city opportunity

Mountain pass
→ future regional transport corridor
```

---

# 9. Player Cities

The player creates all urban settlements inside the playable world.

Initial MVP:

The player begins with **no city at all**.

The player must first establish a settlement location. Population starts at zero and only appears after the minimum conditions for habitation exist, such as access, shelter, water and basic employment or subsistence.

The first settlement can therefore grow organically from infrastructure rather than appearing instantly as a predefined city.

Future progression:

- additional player-founded towns;
- multiple municipalities;
- specialized industrial settlements;
- regional governance;
- interconnected metropolitan areas;
- multiple controlled cities.

All permanent urbanization must be the result of player decisions.

Player modifications must override procedural terrain.

---

# 10. Persistence Model

Do not save untouched world tiles individually.

Store:

```text
WorldSeed
GeneratorVersion
WorldSettings
PlayerChanges
SimulationState
```

Virgin terrain is regenerated from the seed.

Changed chunks store deltas.

Example:

```text
ChunkDelta {
  chunk_x
  chunk_y
  terrain_changes[]
  placed_buildings[]
  removed_objects[]
  infrastructure[]
}
```

Potential rule:

If a chunk becomes heavily modified, save a compact chunk snapshot rather than thousands of individual deltas.

---

# 11. Entity Persistence

Avoid persistent simulation of unnecessary entities.

Entities may have:

```text
TRANSIENT
PERSISTENT
AGGREGATED
```

Examples:

### Transient
- decorative cars;
- pedestrians;
- wildlife.

### Persistent
- player-owned train;
- named vehicle;
- important shipment;
- service vehicle currently executing a task.

### Aggregated
- distant freight flows;
- commuters;
- long-distance passengers.

---

# 12. Infrastructure Progression

Transport infrastructure must evolve gradually rather than appearing as fully modern options from the beginning.

The player should unlock or justify higher infrastructure tiers through:

- population;
- available materials;
- economic output;
- construction capacity;
- engineering capability;
- traffic demand;
- terrain difficulty.

The intended progression is:

```text
Trail / footpath
    ↓
Dirt road
    ↓
Gravel / compacted road
    ↓
Basic paved street
    ↓
Improved urban avenue
    ↓
Regional road
    ↓
High-capacity highway
    ↓
Expressway / controlled-access motorway
```

The same principle applies to crossings:

```text
Natural ford
    ↓
Simple wooden bridge
    ↓
Basic stone / concrete bridge
    ↓
Reinforced bridge
    ↓
Large-span bridge
    ↓
Major viaduct / interchange structure
```

And later to rail:

```text
Basic freight rail
    ↓
Mixed-use regional rail
    ↓
Double-track corridor
    ↓
Electrified rail
    ↓
High-capacity passenger rail
    ↓
High-speed rail
```

Infrastructure tiers must have meaningful differences in:

```text
construction cost
required resources
maintenance
capacity
speed
terrain tolerance
vehicle compatibility
durability
```

Examples:

### Dirt Road
Requires:
- labor;
- basic tools;
- local soil/gravel.

Characteristics:
- cheap;
- low capacity;
- low speed;
- strongly affected by terrain and weather abstractions;
- ideal for early farms, logging and mines.

### Basic Pavement
Requires:
- aggregate;
- binder/asphalt or equivalent material;
- improved construction capability.

Characteristics:
- higher speed;
- higher capacity;
- more expensive maintenance;
- supports denser urban development.

### Highway
Requires:
- large quantities of construction materials;
- advanced engineering;
- major investment;
- sufficient transport demand.

Characteristics:
- high regional capacity;
- limited-access behavior;
- expensive bridges/interchanges;
- inappropriate for a tiny settlement.

The game must avoid a simple "level unlock" whenever possible.

Prefer:

```text
resources + demand + capability
→ infrastructure becomes viable
```

rather than:

```text
City Level 5
→ highway magically unlocked
```

---

# 13. Transportation Model

Transport should operate at two levels.

## 12.1 Local Transport

Simulates roads and movement inside active areas.

Core variables:

```text
capacity
speed
congestion
travel_time
maintenance_cost
```

Avoid simulating every citizen as a permanent agent.

Generate visible traffic from actual aggregated demand.

---

## 12.2 Regional Transport

Represent routes as a graph.

```text
Node
- city
- port
- station
- airport
- industrial zone

Edge
- highway
- road
- railway
- sea route
- air route
```

Each edge has:

```text
distance
capacity
cost
travel_time
congestion
reliability
```

---

# 14. Freight System

Goods must physically/logically move through the network.

Initial goods:

```text
Food
Timber
Ore
Steel
Fuel
ConsumerGoods
Machinery
ConstructionMaterials
```

Possible later expansion:

```text
Electronics
Chemicals
Vehicles
Medicine
LuxuryGoods
```

Freight unit:

```text
Shipment {
  origin
  destination
  commodity
  quantity
  route
  departure_time
  ETA
}
```

---

# 15. Freight LOD

When far away:

```text
Shipment #2481
Iron: 900 t
Origin: Mine A
Destination: Steel Mill B
ETA: 3h
```

No physical train exists.

When approaching the player's active simulation zone:

```text
Shipment
  ↓
Train visual entity
  ↓
moves across loaded railway
```

When leaving:

```text
Train
  ↓
Aggregate Shipment
```

This is critical for scalability.

---

# 16. Passenger Model

Passengers are aggregate populations, not permanent individual agents.

Trip purposes:

- commuting;
- education;
- shopping;
- tourism;
- business;
- migration.

Demand can be modeled as matrices between zones.

Example:

```text
Residential District A
→ Industrial District B
4,200 trips/day
```

Visible cars/buses/trains are representations of underlying demand.

---

# 17. Ports

Ports require access to navigable coastline.

Port variables:

```text
berths
cargo_capacity
passenger_capacity
storage
rail_connection
road_connection
customs_efficiency
```

Imports and exports must originate from actual regional/world demand.

Ports can become bottlenecks.

Example:

```text
Demand: 18,000 t/day
Port capacity: 12,000 t/day

Result:
- delayed cargo;
- increased prices;
- industry shortages;
```

---

# 18. Railways

Rail must excel at:

- bulk cargo;
- medium/long-distance passenger travel;
- high corridor capacity.

Rail nodes:

- passenger station;
- freight terminal;
- intermodal terminal;
- mine loader;
- port rail yard.

Rail simulation does not need every train globally instantiated.

---

# 19. Airports

Airports should primarily support:

- business travel;
- tourism;
- migration;
- long-distance passengers;
- high-value freight.

Airport viability depends on:

```text
population
wealth
tourism
business activity
regional accessibility
```

A small town should not automatically support an international airport.

Air routes may connect:

- player-founded cities;
- multiple airports inside the playable world;
- abstract external destinations beyond the world boundary.

External destinations do not require physical pre-generated cities.

---

# 20. Economy

The MVP economy should be understandable rather than hyper-realistic.

Core model:

```text
Production
→ Logistics
→ Consumption
```

Each player-founded city or economic zone has:

```text
Production[Good]
Demand[Good]
Inventory[Good]
Price[Good]
```

Simplified price behavior:

```text
High supply + low demand → lower price
Low supply + high demand → higher price
```

Transport cost contributes to final price.

---

# 21. Industry Chains

Initial chains:

## Agriculture

```text
Farm
→ Food Processor
→ Commercial / Export
```

## Construction

```text
Stone
→ Construction Materials
→ Buildings
```

## Steel

```text
Iron Mine
→ Steel Mill
→ Machinery / Construction
```

## Forestry

```text
Forest
→ Lumber Mill
→ Construction / Export
```

Chains provide natural reasons to build freight infrastructure.

---

# 22. Population

Population should be simulated primarily at building or district level.

A residential building may store:

```text
households
population
income_level
employment_rate
satisfaction
```

Do not create one permanent object per citizen in MVP.

This is important for mobile performance.

---

# 23. City Growth

City attractiveness can depend on:

```text
jobs
housing
services
transport
pollution
crime
taxes
education
healthcare
environment
cost_of_living
```

Population changes should generate new transport and goods demand.

---

# 24. Utilities

MVP:

- electricity;
- water;
- waste.

Possible later systems:

- sewage;
- telecommunications;
- heating;
- fuel.

Utility networks should use graph-based calculations where possible.

---

# 25. Simulation Tick

Do not update every system every frame.

Suggested scheduling:

```text
Render:
30–60 FPS

Local traffic:
5–10 updates/sec

Buildings:
1 update/sec

District economy:
1 update / 5 sec

Regional economy:
1 update / 30 sec

Distant settlements:
1 update / several in-game hours
```

Exact values must be benchmarked.

---

# 26. Time Compression

Suggested speeds:

```text
Paused
1×
2×
4×
8×
```

Potential desktop-only:

```text
16×
```

Simulation must remain deterministic enough for saves and debugging.

---

# 27. Camera / World Streaming

Maintain:

```text
ActiveChunkRadius
PreloadChunkRadius
UnloadRadius
```

Example:

```text
Loaded:
7 × 7 chunks

High-detail:
3 × 3 chunks
```

Values must adapt to hardware.

---

# 28. Hardware Profiles

On first launch benchmark or select profile.

## Low

Target:
- 3–4 GB RAM phone;
- integrated graphics;
- inexpensive laptop.

Settings:

```text
30 FPS target
small/medium worlds
reduced traffic visualization
small active chunk radius
low vegetation density
reduced animation
```

## Medium

```text
60/30 FPS option
medium/large worlds
normal traffic
larger active radius
```

## High

```text
large/experimental worlds
high traffic visualization
larger active region
more cosmetic entities
```

The simulation results should stay mostly identical across profiles.

Hardware settings should primarily change **visual fidelity and simulation representation**, not economic rules.

---

# 29. External Economy

The playable map can remain completely player-built while still supporting imports and exports.

The world boundary represents access to an abstract external economy.

Possible external gateways:

- highway exits;
- railway connections;
- maritime shipping lanes;
- air routes.

Example:

```text
PLAYABLE WORLD

┌──────────────────────────────────┐
│                                  │
│       Player City A              │
│           ●                      │
│           ║ rail                 │
│           ● Player City B ── ⚓   │
│                              │   │
└──────────────────────────────┼───┘
                               ↓
                         External Market
```

The external economy may provide:

```text
import demand
export demand
commodity prices
migration pressure
tourism demand
business demand
```

It should be simulated statistically rather than represented as physical off-map cities.

This provides realistic reasons for:

- ports;
- international airports;
- border highways;
- external rail corridors;

without violating the virgin-world principle.

---

# 30. Memory Budget

Design goal:

### Mobile
Keep runtime memory preferably under approximately:

```text
700–1200 MB
```

depending on platform/device.

### Desktop
Initial target:

```text
< 2 GB typical
```

World size should mostly affect disk usage and generation time, not active RAM usage.

---

# 31. Save Architecture

Suggested directory structure:

```text
save/
├── world.meta
├── economy.dat
├── settlements.dat
├── player.dat
├── chunks/
│   ├── 0_0.delta
│   ├── 0_1.delta
│   └── ...
└── routes.dat
```

`world.meta` stores:

```text
seed
generator_version
world_size
difficulty
creation_time
```

---

# 32. Versioned Generation

This is critical.

Never rely only on:

```text
seed
```

Use:

```text
seed + generator_version
```

If terrain algorithms change between releases, old worlds must still regenerate correctly.

Example:

```text
generator_version = 1
```

Future saves remain tied to generator v1 unless migrated explicitly.

---

# 33. Recommended MVP Scope

Do NOT begin with the entire vision.

## MVP 0.1 — Terrain Sandbox

Implement:

- deterministic world seed;
- finite procedural world;
- elevation;
- water;
- biomes;
- chunk streaming;
- camera;
- save/load;
- terrain modifications.

Success condition:

A low-end machine can explore a medium procedural world without loading it entirely into RAM.

---

## MVP 0.2 — Basic City

Add:

- roads;
- residential;
- commercial;
- industrial;
- electricity;
- water;
- population;
- jobs;
- basic taxes.

No regional economy yet.

---

## MVP 0.3 — Regional Economy

Add:

- resources;
- production;
- consumption;
- goods;
- regional economic nodes;
- external market gateways;
- imports/exports.

At this point the city exists inside a wider economy without requiring generated pre-generated cities.

---

## MVP 0.4 — Freight

Add:

- freight graph;
- trucks;
- rail freight;
- cargo terminals;
- shipments;
- bottlenecks.

---

## MVP 0.5 — Port

Add:

- coastline suitability;
- port construction;
- maritime trade;
- ships as LOD entities;
- import/export capacity.

---

## MVP 0.6 — Regional Passenger Transport

Add:

- intercity rail;
- buses;
- migration;
- tourism;
- airport prototype.

---

# 34. What NOT to Simulate Initially

Avoid:

- one AI agent per citizen;
- globally persistent cars;
- globally persistent trains;
- detailed ocean navigation;
- individual household inventories;
- realistic financial banking;
- fluid simulation;
- dynamic weather physics;
- detailed political simulation;
- pre-generated cities;
- fully destructible terrain physics.

These can destroy mobile performance and development scope.

---

---

# 35. Technical Architecture Decision

Performance is a **product requirement**, not a late optimization pass.

The project should be built around two independent layers:

```text
                    ┌─────────────────────────┐
                    │     SIMULATION CORE     │
                    │       Pure Java         │
                    │                         │
                    │ world / economy         │
                    │ population / resources  │
                    │ trade / transport       │
                    │ politics / history      │
                    └────────────┬────────────┘
                                 │
                         commands + snapshots
                    ┌────────────┴────────────┐
                    │                         │
                    ▼                         ▼
          ┌───────────────────┐     ┌───────────────────┐
          │  HEADLESS RUNNER  │     │   GAME CLIENT     │
          │                   │     │      libGDX       │
          │ simulation tests  │     │                   │
          │ balancing         │     │ rendering / UI    │
          │ benchmarks        │     │ input / audio     │
          └───────────────────┘     └───────────────────┘
```

The simulation must be capable of running with **zero graphics, zero audio and zero libGDX dependency**.

The visual game and the headless simulator must execute the same rules.

This makes it possible to:

- simulate decades or centuries at maximum speed;
- test economic balance automatically;
- benchmark population and trade systems independently from rendering;
- reproduce bugs from a seed + command log;
- keep rendering code from contaminating simulation architecture.

---

# 36. Language and Framework Stack

## 36.1 Primary Language

**Java** is the primary language.

Recommended compatibility baseline:

```text
Java language/runtime baseline: Java 17
Build system: Gradle
```

The baseline can be raised later after Android and desktop compatibility are benchmarked.

Reasons for Java:

- strong fit for a deterministic simulation core;
- mature profiling ecosystem;
- efficient primitive arrays and compact data structures;
- good desktop and Android portability;
- existing expertise can be reused;
- easy creation of a headless simulation executable.

Do not depend on high-allocation object-oriented designs simply because Java supports them.

The hot simulation path should be primarily **data-oriented Java**.

---

## 36.2 Rendering / Game Framework

**libGDX** is the preferred game framework.

Use it for:

```text
window / lifecycle
input
2D rendering
texture management
audio
fonts
UI
desktop backend
Android backend
```

Recommended platform modules:

```text
desktop  → LWJGL3 backend
android  → libGDX Android backend
```

The game should use libGDX as a **thin presentation/platform layer**, not as the architecture of the simulation.

---

## 36.3 Supporting Libraries / Tools

Recommended:

```text
Gradle
JUnit 5
JMH                 desktop microbenchmarks
JFR / JMC           desktop JVM profiling
Android Profiler
Perfetto             mobile frame / CPU profiling
TexturePacker        sprite atlas generation
FastNoiseLite OR
custom deterministic noise
```

Optional after benchmarking:

```text
LZ4 / Zstd           save/chunk compression
```

Do not introduce a database, ORM, ECS framework, dependency-injection framework or serialization framework into the simulation hot path unless profiling demonstrates a real benefit.

---

# 37. Repository / Module Architecture

Recommended project layout:

```text
atlas-city/
│
├── simulation-core/
│   ├── world/
│   ├── population/
│   ├── economy/
│   ├── resources/
│   ├── industry/
│   ├── transport/
│   ├── trade/
│   ├── politics/
│   ├── history/
│   ├── commands/
│   └── persistence/
│
├── game-client/
│   ├── renderer/
│   ├── camera/
│   ├── input/
│   ├── ui/
│   ├── audio/
│   └── presentation/
│
├── platform-desktop/
├── platform-android/
│
├── headless-runner/
│
├── benchmark/
│
├── tools/
│   ├── world-viewer/
│   ├── save-inspector/
│   └── economy-debugger/
│
└── assets/
```

`simulation-core` must not import:

```text
libGDX
OpenGL
Android APIs
LWJGL
Scene2D
render classes
```

---

# 38. Command-Driven Simulation

The renderer and UI must never mutate simulation state directly.

Example:

The player draws a dirt road.

The client converts the interaction into:

```text
BuildRoadCommand {
    roadType: DIRT
    path: [...]
}
```

The Simulation Core evaluates:

```text
terrain validity
required materials
available labor
construction cost
engineering capability
ownership / policy rules
```

Then it applies or rejects the command.

Other examples:

```text
CreateZoneCommand
BuildBridgeCommand
BuildRailCommand
SetTaxPolicyCommand
CreatePortCommand
DemolishCommand
ChangeBudgetCommand
```

Architecture:

```text
PLAYER INPUT
     ↓
GAME CLIENT
     ↓
COMMAND
     ↓
SIMULATION CORE
     ↓
WORLD STATE
     ↓
EVENTS / SNAPSHOT / DIFF
     ↓
GAME CLIENT
     ↓
RENDER
```

This separation is mandatory.

---

# 39. Simulation State vs Presentation State

Maintain two different concepts.

## Simulation State

Authoritative.

Contains:

```text
terrain state
infrastructure
population aggregates
resource inventories
prices
production
employment
trade flows
policies
historical state
```

## Presentation State

Disposable and reconstructible.

Contains:

```text
visible cars
visible pedestrians
animation state
particles
screen labels
selection overlays
temporary UI state
```

A visual vehicle may represent hundreds of actual trips.

A pedestrian sprite does not need to represent a permanently simulated citizen.

If all presentation state is deleted, the simulation must remain valid.

---

# 40. Threading Model

The game must avoid placing heavy simulation work on the rendering thread.

Recommended architecture:

```text
MAIN / RENDER THREAD
│
├── input
├── camera
├── UI
└── draw submission

SIMULATION THREAD
│
├── fixed simulation ticks
├── economy
├── population
├── local transport demand
└── commands

WORLD WORKERS
│
├── procedural chunk generation
├── path preprocessing
└── expensive background calculations

I/O WORKER
│
├── save
├── load
└── compression
```

Communication should use:

```text
command queues
event queues
double-buffered snapshots
compact diffs
```

Avoid coarse global locks.

The renderer should consume a stable read model while the simulation prepares the next state.

---

# 41. Simulation Scheduling

Not every subsystem deserves the same update frequency.

Target concept:

| System | Suggested cadence |
|---|---:|
| Camera / animation | every rendered frame |
| Nearby visual traffic | 10–30 Hz |
| Local traffic model | 5–10 Hz |
| Building operations | 1–2 Hz |
| Utility graph changes | event-driven + periodic validation |
| Local economy | 1 Hz or game-hour |
| Regional logistics | game-hour |
| Population demographics | game-day |
| Resource depletion | game-day |
| Government finance | game-day / month |
| Elections / political trends | game-week / month |
| Long-term history | game-month / year |

Use dirty flags and events whenever possible.

Example:

Do not recalculate the complete road network every simulation tick.

Instead:

```text
road built
   ↓
affected graph marked dirty
   ↓
recalculate affected component
```

The simulation must optimize **work avoided**, not merely make every calculation faster.

---

# 42. Memory Architecture

Memory efficiency is a first-class design constraint.

## 42.1 Forbidden Hot-Path Pattern

Avoid:

```java
class Tile {
    Terrain terrain;
    Biome biome;
    Resource resource;
    Building building;
    ...
}
```

for millions of tiles.

Avoid:

```text
1 citizen = 1 permanent Java object
1 trip = 1 permanent Java object
1 tile = 1 object
1 road segment = deep object graph
```

---

## 42.2 Preferred Data Layout

Prefer primitive arrays and IDs.

Example:

```text
terrainType[]      byte
biomeType[]        byte
elevation[]        short
fertility[]        byte
resourceFlags[]    int
buildingId[]       int
```

For large collections use **Structure of Arrays (SoA)** where appropriate:

```text
population[]
employment[]
income[]
education[]
health[]
```

instead of millions of objects containing those fields.

Use:

```text
byte / short where ranges permit
bitsets for boolean flags
integer IDs instead of object references
chunk-local indices
sparse storage for uncommon state
```

---

## 42.3 Population Representation

Never require one persistent object per resident.

Preferred hierarchy:

```text
BUILDING
  population aggregates
       ↓
DISTRICT
  demographic aggregates
       ↓
CITY
  macro aggregates
       ↓
REGION
  statistical aggregates
```

Example residential record:

```text
population:       184
workers:           93
children:          31
retired:           20
unemployed:         7
incomeBucket0:     28
incomeBucket1:     91
incomeBucket2:     65
```

Detailed named citizens may exist only for special gameplay features and must never be required for the base economy.

---

## 42.4 Allocation Policy

After loading/warm-up:

> Hot rendering and simulation loops should approach zero temporary heap allocations.

Use:

```text
reusable buffers
primitive collections
fixed arrays where practical
object pools for transient visual entities
preallocated command/event buffers
chunk-local scratch memory
```

Avoid in hot loops when they allocate:

```text
temporary collections
boxing
string construction
streams
short-lived lambdas
per-frame DTO creation
```

Garbage-collection pauses should be treated as frame-time bugs.

---

# 43. Memory Budgets

These are design targets and must be validated on real devices.

## Low-End Mobile Target

```text
3–4 GB device RAM class
Typical game working set target: 350–500 MB
Design-review threshold:       ~700 MB
```

## Standard Mobile

```text
Typical target: < 700 MB
```

## Desktop / Modest Laptop

```text
Typical target: < 1.2 GB
```

The world size must primarily affect:

```text
disk usage
generation workload
number of persisted modified chunks
```

It should **not scale linearly with active RAM**.

If doubling world dimensions approximately quadruples active memory, the architecture is wrong.

---

# 44. Rendering Architecture

The visual style should deliberately favor performance.

Target:

**2D/isometric tile + sprite rendering.**

Do not render the world as Minecraft-style voxel geometry.

The renderer should operate only on:

```text
visible chunks
visible structures
visible transport entities
visible overlays
```

Never render the entire simulated world.

---

## 44.1 Texture Strategy

Use sprite atlases aggressively.

Pipeline:

```text
individual sprites
     ↓
TexturePacker
     ↓
TextureAtlas
     ↓
SpriteBatch
```

Group assets to minimize texture switches.

Prefer a small number of atlas pages for the active scene.

Pixel-art assets should normally use lightweight filtering and avoid unnecessarily large source textures.

---

## 44.2 Batching

Rendering should minimize draw calls and GPU state changes.

Preferred:

```text
terrain batch
infrastructure batch
buildings batch
vehicles batch
effects batch
UI batch
```

Avoid:

```text
texture A
texture B
texture A
texture C
texture A
```

which repeatedly flushes batches.

---

## 44.3 Chunk Render Cache

Static or rarely changing chunk content should not be rebuilt every frame.

Each visible chunk can maintain compact render data:

```text
terrain geometry
road geometry
static decoration
building base geometry
```

Rebuild only when marked dirty.

Example:

```text
player builds road
      ↓
chunk 18,22 dirty
      ↓
rebuild road render data
      ↓
reuse until next modification
```

Use cached vertex data / meshes where profiling demonstrates an advantage.

---

## 44.4 Camera Culling

Before drawing, determine the visible world rectangle.

Only process chunks intersecting the camera.

```text
WORLD
██████████████████████
██████┌────────┐██████
██████│ CAMERA │██████
██████└────────┘██████
██████████████████████
```

Only the camera rectangle plus a small preload margin should produce detailed render work.

---

## 44.5 Visual LOD

Zoom level changes what needs to exist visually.

### Street Level

```text
cars
pedestrians
animations
road markings
building details
```

### District Level

```text
fewer vehicles
reduced animation
simplified buildings
major traffic flows
```

### City Level

```text
building masses
major roads
rail
district overlays
no pedestrians
minimal individual cars
```

### Regional Level

```text
city footprints
transport corridors
ports
airports
resource overlays
trade flows
```

Simulation continues independently.

The renderer must not waste CPU/GPU drawing detail that occupies only a few pixels.

---

## 44.6 Scene Graph Rule

Scene2D may be used for:

```text
menus
HUD
dialogs
tooltips
management panels
```

Do **not** create a Scene2D `Actor` for every:

```text
tile
building
citizen
road segment
vehicle
```

The world renderer should use purpose-built compact render structures.

---

# 45. Frame-Time Targets

Smoothness is measured by frame time, not only average FPS.

## Default Target

```text
Desktop:          60 FPS
Mid/high mobile:  60 FPS where hardware allows
Low-end mobile:   stable 30 FPS minimum profile
```

Frame budgets:

```text
60 FPS → 16.67 ms
30 FPS → 33.33 ms
```

Recommended 60 FPS budget:

```text
input + UI          1–2 ms
render preparation  2–4 ms
GPU submission      3–6 ms
main-thread misc    1–2 ms
safety margin       remaining budget
```

Heavy economy/world simulation should not consume the main render-thread budget.

Prioritize:

```text
stable frame pacing
low input latency
no long GC pauses
no synchronous save spikes
no synchronous world-generation spikes
```

A stable 60 FPS experience is preferable to an average of 90 FPS with frequent stutters.

---

# 46. Adaptive Performance Profiles

The game may select or recommend a hardware profile.

## LOW

```text
30 FPS target
small/medium world recommended
small visible chunk radius
reduced decorative traffic
reduced pedestrians
reduced particles
lower animation frequency
smaller preload radius
```

## MEDIUM

```text
60 FPS preferred
medium/large worlds
normal visual traffic
normal chunk radius
```

## HIGH

```text
60+ FPS optional
large/experimental worlds
larger visible radius
more visual traffic
more cosmetic simulation
```

Important:

> Hardware profiles should change representation fidelity more than simulation rules.

The economy should not fundamentally produce different outcomes because one device renders fewer cars.

---

# 47. Save / Persistence Performance

Save format principles:

```text
seed
generator version
world configuration
simulation aggregates
player modifications
modified chunk deltas
history summaries
```

Do not serialize the entire virgin procedural world.

Do not serialize deep Java object graphs.

Prefer versioned compact binary structures.

Saving must be incremental where possible.

Example:

```text
dirty chunks
dirty economy state
dirty policies
dirty infrastructure
       ↓
async save queue
```

Avoid pausing the render thread for full-world saves.

Potential safety strategy:

```text
snapshot
+
journal / command log
```

so interrupted saves can recover safely.

---

# 48. Headless Simulation and Benchmark Mode

A first-class headless executable should exist early.

Example conceptual usage:

```text
atlas-headless
  --seed 819234
  --scenario benchmark-growth
  --years 100
  --speed unlimited
```

Output:

```text
Year 0
Population: 0

Year 10
Population: 8,420

Year 25
Population: 74,190

Year 50
Population: 510,882

Simulation time:
14.2 seconds

Peak heap:
312 MB
```

Headless tests should measure:

```text
ticks / second
years / second
peak heap
allocation rate
save size
pathfinding time
economic convergence
number of active shipments
population scaling
```

This mode is mandatory for long-term optimization.

---

# 49. Profiling Gates

No major system should be considered complete without profiling.

Performance gates should be created for:

```text
1k population
10k population
100k population
1M population
multiple player-founded cities
large freight networks
large road networks
maximum zoom-out
rapid camera movement
autosave during simulation
```

For every gate record:

```text
average frame time
p95 frame time
p99 frame time
heap usage
allocation rate
CPU usage
GPU usage
simulation ticks/sec
save size
```

Optimization decisions should be based on profiles rather than intuition.

---

# 50. Proposed Core Systems

```text
WorldManager
ChunkManager
ProceduralGenerator
TerrainSystem
SaveSystem

CommandBus
SimulationScheduler
SnapshotPublisher

CitySystem
BuildingSystem
ZoneSystem
PopulationSystem
UtilitySystem

EconomySystem
IndustrySystem
ResourceSystem
MarketSystem

TransportGraph
RoadSystem
BridgeSystem
RailSystem
PortSystem
AirportSystem

ShipmentSystem
PassengerDemandSystem

CityRegionSystem
RegionalSimulationSystem
ExternalMarketSystem

PolicySystem
GovernmentFinanceSystem
PoliticalPressureSystem
HistorySystem

SimulationLODManager
TimeManager
```

Politics should initially model **consequences of concrete policy choices**, not detailed individual political agents.

---

# 51. Core Data Flow

```text
PROCEDURAL WORLD
       │
       ▼
GEOGRAPHY + RESOURCES
       │
       ▼
PLAYER BUILDS INFRASTRUCTURE
       │
       ▼
ACCESS + LAND USE
       │
       ▼
POPULATION + PRODUCTION
       │
       ▼
CONSUMPTION + EMPLOYMENT
       │
       ▼
TRANSPORT / TRADE DEMAND
       │
       ▼
PRICES + COSTS + ACCESSIBILITY
       │
       ▼
SOCIAL / ECONOMIC CONSEQUENCES
       │
       ▼
POLITICAL PRESSURE
       │
       ▼
PLAYER DECISIONS
       └──────────────────────┐
                              │
                              └── feedback loop
```

The map is the physical interface through which the player changes the simulation.

---

# 52. Gameplay Example

A new game begins:

```text
Population: 0
Cities: 0
Infrastructure: 0
```

The procedural world contains only natural geography and resources:

```text
River
Coast
Forest
Fertile plain
Stone
Small iron deposit
```

The player chooses a location and builds the first dirt access road.

```text
dirt path
   ↓
basic housing area
   ↓
water access
   ↓
farm / forestry work
   ↓
habitation becomes viable
   ↓
first population arrives
```

The settlement grows.

A dirt road eventually becomes a bottleneck.

The player can:

```text
improve the road
build a bridge
create a second route
develop local materials
accept slower growth
```

Later, iron extraction becomes profitable.

The player chooses where to build:

```text
mine
freight road
rail terminal
steel industry
worker housing
```

Those spatial choices determine transport costs.

Transport costs affect prices.

Prices affect industry.

Industry affects jobs.

Jobs affect migration.

Migration affects housing.

Housing demand affects land value.

Land values and living conditions create political pressure.

Eventually the same world may contain:

```text
multiple player-founded cities
regional highways
rail corridors
ports
airports
industrial clusters
agricultural regions
economic inequality
resource depletion
political conflicts
historical consequences
```

None existed when the seed was created.

---

# 53. Procedural World Configuration Screen

Suggested options:

```text
WORLD NAME
SEED

WORLD SIZE
○ Small
● Medium
○ Large
○ Experimental

GEOGRAPHY
○ Flat
● Balanced
○ Mountainous
○ Islands
○ Continental

SEA LEVEL
Low ─────●──── High

RESOURCES
Scarce ───●── Abundant

EXTERNAL MARKET
Weak ─────●──── Strong
```

Show estimated hardware impact:

```text
Recommended profile
Estimated active RAM
Expected save growth
Recommended world size
```

World size and visual quality should be configurable separately.

---

# 54. Technical Success Metrics

Initial optimization targets:

```text
World starts with Population = 0
No pre-generated civilization

60 FPS target on modest desktop
30 FPS stable minimum profile on low-end mobile
60 FPS target on capable mobile

near-zero hot-loop allocations after warm-up
no full-world render traversal
no full-world simulation tick
no full-world save serialization

< 500 MB typical working-set goal on low-end mobile profile
< 1.2 GB typical desktop working-set goal

chunk generation off render thread
save/compression off render thread

seed + generator version reproduces virgin terrain
untouched chunks do not need to be persisted
simulation can run without graphics
```

The numerical targets are performance budgets, not promises. They must be verified continuously on representative hardware.

---

# 55. Design Rules

Every feature must answer these questions:

### Simulation

> Does this require one permanent object per citizen, trip or tile?

If yes, redesign it unless profiling proves the scale is safe.

### World

> Does this require processing the entire map?

If yes, redesign it around chunks, graphs, dirty regions or aggregation.

### Rendering

> Does this require drawing something the player cannot meaningfully see?

If yes, cull or replace it with LOD.

### Memory

> Does this create temporary objects every frame/tick?

If yes, remove the allocation from the hot path.

### Gameplay

> Does this decision produce consequences through the simulation?

If no, question whether the feature belongs in the game.

Prefer:

```text
Detailed nearby
↓
Aggregated at district/city scale
↓
Statistical regionally
```

---

# 56. First Development Milestone

The first prototype should contain only:

```text
Java simulation-core
+
libGDX client
+
headless runner
+
procedural terrain
+
chunk streaming
+
camera
+
seed persistence
+
basic terrain/resource layers
+
benchmark instrumentation
```

No population simulation is required yet.

The initial performance test should open and explore:

```text
1024²
2048²
4096²
```

worlds while measuring:

```text
RAM
allocation rate
CPU
GPU
frame time
chunk generation latency
camera-scroll stutter
save size
```

The first milestone is successful when a large procedural world feels trivial to navigate because only the required portion is active.

---

# 57. Second Development Milestone — First Civilization

Only after the world architecture is performant:

```text
Population = 0
    ↓
build dirt road
    ↓
designate first housing / subsistence areas
    ↓
create water / basic resource access
    ↓
population attraction
    ↓
first residents arrive
```

Implement:

```text
dirt roads
basic housing
basic agriculture
forestry
stone
simple resource inventories
basic jobs
population aggregates
```

The goal is to prove that civilization can emerge from player-built conditions without scripted settlement creation.

---

# 58. North Star

The game should ultimately allow this story:

> The world began with zero population and no infrastructure. I chose a river valley, cut the first dirt road through the forest and created the conditions for the first families to settle. As population and production grew, tracks became paved roads, crossings became bridges, and I founded new resource towns around forests, farms and mines. I connected them by rail, built ports and highways, changed taxation and public policy, survived resource shortages and economic crises, and eventually created a network of metropolitan regions whose economy, politics, inequalities, infrastructure and history were consequences of decisions made decades earlier.

The product is not merely the city.

**The product is the history produced by the simulation and the player's interventions in the world.**
