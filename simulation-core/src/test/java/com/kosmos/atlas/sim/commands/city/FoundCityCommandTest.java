package com.kosmos.atlas.sim.commands.city;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FoundCityCommandTest {

    private ChunkStore store;
    private BuildingRegistry buildings;
    private CityRegistry cities;

    @BeforeEach
    void setUp() {
        store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        store.put(chunk);
        buildings = new BuildingRegistry();
        cities = new CityRegistry();
    }

    private SimulationContext ctx() {
        return new SimulationContext(store, buildings, cities, 4096, 0);
    }

    @Test
    void foundingACityPlacesAFreeCityHallAtTheFoundingTile() {
        assertEquals(CommandResult.ACCEPTED, new FoundCityCommand(5, 5, "Testville").apply(ctx()));

        int cityId = cities.nearestCity(5, 5);
        int cityHallId = store.get(0, 0).buildingId[Chunk.tileIndex(5, 5)];
        assertNotEquals(WorldConstants.NO_BUILDING, cityHallId);
        assertEquals(BuildingType.CITY_HALL, buildings.type(cityHallId));
        assertEquals(cityId, buildings.cityId(cityHallId));
        assertEquals(0.0, cities.finance(cityId).treasuryBalance() - cities.difficulty().startingTreasury, 1e-9,
            "founding a city (and its City Hall) must not cost anything beyond the starting treasury");
    }

    @Test
    void foundingOnAnOccupiedTileIsRejected() {
        new FoundCityCommand(5, 5, "First").apply(ctx());

        assertEquals(CommandResult.REJECTED_TILE_OCCUPIED, new FoundCityCommand(5, 5, "Second").apply(ctx()));
    }

    @Test
    void cityHallCannotBeBuiltDirectlyViaBuildCivicBuildingCommand() {
        // CITY_HALL is intentionally excluded from BuildCivicBuildingCommand's known types — only
        // FoundCityCommand may create one.
        new FoundCityCommand(5, 5, "Testville").apply(ctx());

        assertEquals(CommandResult.REJECTED_INVALID_TERRAIN,
            new com.kosmos.atlas.sim.commands.economy.BuildCivicBuildingCommand(6, 5, BuildingType.CITY_HALL).apply(ctx()));
    }
}
