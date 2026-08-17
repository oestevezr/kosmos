package com.kosmos.atlas.sim.population;

/** Byte-coded building categories (spec §33 MVP 0.2/0.3, §21 industry chains, §24 utilities). */
public final class BuildingType {

    public static final byte RESIDENTIAL = 0;
    public static final byte COMMERCIAL = 1;
    public static final byte INDUSTRIAL = 2;
    public static final byte POWER_PLANT = 3;
    public static final byte WATER_TOWER = 4;

    // --- MVP 0.3 production chain (spec §7, §21) ---
    /** Extracts Food; requires a fertile tile. */
    public static final byte FARM = 5;
    /** Extracts Timber; requires a forested tile with the timber resource flag. */
    public static final byte LUMBER_CAMP = 6;
    /** Extracts Ore or Fuel depending on the tile's mineral resource flags. */
    public static final byte MINE = 7;
    /** Extracts ConstructionMaterials directly from a stone-flagged tile. */
    public static final byte QUARRY = 8;
    /** Consumes Ore, produces Steel. */
    public static final byte STEEL_MILL = 9;
    /** The player-placed external-market gateway (spec §29) — imports/exports goods up to a capacity. */
    public static final byte TRADE_DEPOT = 10;

    // --- MVP 0.5 (Port, spec §17) ---
    /** Higher-capacity coastal trade gateway — see {@code com.kosmos.atlas.sim.trade.PortRegistry} for its extra fields. */
    public static final byte PORT = 11;

    // --- Tiered utility services (TheOtown-inspired: unlock bigger/costlier tiers as the city
    // grows, see docs/roadmap.md's "Servicios cívicos por tiers" section). POWER_PLANT/WATER_TOWER
    // above are tier 1 of their category; economics (cost/maintenance/capacity/radius/unlock
    // population) for every tier live in com.kosmos.atlas.sim.economy.BuildingEconomics, not here.
    /** Electricity tier 2 — unlocked once the city is populous enough (see BuildingEconomics). */
    public static final byte POWER_PLANT_HYDRO = 12;
    /** Electricity tier 3. */
    public static final byte POWER_PLANT_NUCLEAR = 13;
    /** Water tier 2. */
    public static final byte WATER_TREATMENT_PLANT = 14;
    /** Water tier 3. */
    public static final byte DESALINATION_PLANT = 15;

    // --- Prosperity/luxury civic services (Fase 2 of the tiered-service system, see
    // docs/roadmap.md). Unlike Electricity/Water, these have no population-capacity concept — they
    // only feed PopulationSystem's satisfaction ceiling, since "how much population does a
    // hospital serve" has no natural unit (spec §20's understandable-not-hyperrealistic MVP). ---
    /** Healthcare tier 1. */
    public static final byte CLINIC = 16;
    /** Healthcare tier 2 — unlocked once the city is populous enough (see BuildingEconomics). */
    public static final byte HOSPITAL = 17;
    /** Fire safety tier 1. */
    public static final byte VOLUNTEER_FIRE_BRIGADE = 18;
    /** Fire safety tier 2. */
    public static final byte FIRE_STATION = 19;
    /** Sanitation tier 1. */
    public static final byte WASTE_COLLECTION = 20;
    /** Sanitation tier 2. */
    public static final byte INCINERATOR = 21;
    /** No tier 2 — the user described this and Park/Museum below as services that "already grant
     *  the maximum level" once built. */
    public static final byte CEMETERY = 22;
    /** Luxury — no tier 2, satisfaction-ceiling coverage only. */
    public static final byte PARK = 23;
    /** Luxury — the one civic building that generates its own revenue (tourism), see BuildingEconomics. */
    public static final byte MUSEUM = 24;

    // --- Remaining civic services + Central Bank/City Hall (see docs/roadmap.md). Police and
    // Education follow the same 2-tier prosperity pattern as Healthcare/Fire/Sanitation. ---
    /** Public safety tier 1. */
    public static final byte POLICE_OUTPOST = 25;
    /** Public safety tier 2. */
    public static final byte POLICE_STATION = 26;
    /** Education tier 1. */
    public static final byte SCHOOL = 27;
    /** Education tier 2. */
    public static final byte UNIVERSITY = 28;
    /** No tier 2 — same as Cemetery. */
    public static final byte CHURCH = 29;
    /** Not a coverage source (no radius/satisfaction effect) — the physical requirement for
     *  {@code RequestCityLoanCommand} to let this city act as a lender. */
    public static final byte CENTRAL_BANK = 30;
    /** Auto-placed by {@code FoundCityCommand} at the founding tile — never built via
     *  {@code BuildCivicBuildingCommand} directly. Not a coverage source. */
    public static final byte CITY_HALL = 31;
    /** MVP 0.6's first slice (Regional Passenger Transport) — a cargo-only gateway this pass, same
     *  role as {@link #PORT} but landlocked and population-gated (spec §19: "a small town should
     *  not automatically support an international airport"). Passenger capacity is deliberately
     *  not modeled yet — see {@code docs/roadmap.md}. */
    public static final byte AIRPORT = 32;
    /** MVP 0.6's second slice: a fourth cargo gateway, same {@code MarketSystem} treatment as
     *  Port/Airport but domestic — no coastal requirement, no population gate, no customs bonus
     *  (spec §18: rail "excels at bulk cargo"). Registers a {@link com.kosmos.atlas.sim.trade.NodeType#STATION} node. */
    public static final byte RAIL_TERMINAL = 33;
    /** The bus depot ("central") — owns a capacity of simultaneous routes
     *  ({@link com.kosmos.atlas.sim.economy.BuildingEconomics#capacity}), not itself a coverage
     *  source or a graph node. See {@code docs/roadmap.md}'s bus-route mechanic. */
    public static final byte BUS_DEPOT = 34;
    /** A bus stop — a coverage-only building like Fase 2's civic services, except its coverage only
     *  activates while it's a stop on at least one active bus route (see {@code UtilitySystem}).
     *  Registers a {@link com.kosmos.atlas.sim.trade.NodeType#BUS_STOP} node. */
    public static final byte BUS_STOP = 35;

    /** One past the highest constant above — sizes BuildingEconomics's per-type tables. */
    public static final int COUNT = 36;

    private BuildingType() {
    }
}
