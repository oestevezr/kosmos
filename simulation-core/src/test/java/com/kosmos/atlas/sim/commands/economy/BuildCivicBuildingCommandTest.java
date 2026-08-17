package com.kosmos.atlas.sim.commands.economy;

import com.kosmos.atlas.sim.city.CityRegistry;
import com.kosmos.atlas.sim.commands.CommandResult;
import com.kosmos.atlas.sim.commands.SimulationContext;
import com.kosmos.atlas.sim.economy.BuildingEconomics;
import com.kosmos.atlas.sim.population.BuildingRegistry;
import com.kosmos.atlas.sim.population.BuildingType;
import com.kosmos.atlas.sim.world.Chunk;
import com.kosmos.atlas.sim.world.ChunkStore;
import com.kosmos.atlas.sim.world.WorldConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BuildCivicBuildingCommandTest {

    private ChunkStore store;
    private BuildingRegistry buildings;
    private CityRegistry cities;
    private int cityId;

    @BeforeEach
    void setUp() {
        store = new ChunkStore(4);
        Chunk chunk = new Chunk();
        chunk.reset(0, 0);
        java.util.Arrays.fill(chunk.terrainType, WorldConstants.TERRAIN_PLAIN);
        store.put(chunk);
        buildings = new BuildingRegistry();
        cities = new CityRegistry();
        cityId = cities.create("Testville", 5, 5, 0);
    }

    private SimulationContext ctx() {
        return new SimulationContext(store, buildings, cities, 4096, 0);
    }

    @Test
    void clinicAcceptedImmediatelyNoTierGate() {
        assertEquals(CommandResult.ACCEPTED, new BuildCivicBuildingCommand(5, 5, BuildingType.CLINIC).apply(ctx()));
        int id = store.get(0, 0).buildingId[Chunk.tileIndex(5, 5)];
        assertNotEquals(WorldConstants.NO_BUILDING, id);
        assertEquals(BuildingType.CLINIC, buildings.type(id));
    }

    @Test
    void hospitalRejectedUntilPopulationUnlocksItThenAccepted() {
        assertEquals(CommandResult.REJECTED_SERVICE_TIER_LOCKED,
            new BuildCivicBuildingCommand(5, 5, BuildingType.HOSPITAL).apply(ctx()));

        int home = buildings.create(BuildingType.RESIDENTIAL, 20, 20, cityId);
        buildings.setPopulation(home, (int) BuildingEconomics.unlockPopulation(BuildingType.HOSPITAL));
        assertEquals(CommandResult.ACCEPTED, new BuildCivicBuildingCommand(5, 5, BuildingType.HOSPITAL).apply(ctx()));
    }

    @Test
    void rejectsWithoutEnoughFundsAndSpendsOnSuccess() {
        cities.finance(cityId).adjustTreasury(-cities.finance(cityId).treasuryBalance());
        assertEquals(CommandResult.REJECTED_INSUFFICIENT_FUNDS, new BuildCivicBuildingCommand(5, 5, BuildingType.CLINIC).apply(ctx()));

        cities.finance(cityId).adjustTreasury(BuildingEconomics.constructionCost(BuildingType.CLINIC));
        assertEquals(CommandResult.ACCEPTED, new BuildCivicBuildingCommand(5, 5, BuildingType.CLINIC).apply(ctx()));
        assertEquals(0.0, cities.finance(cityId).treasuryBalance(), 1e-9);
    }

    @Test
    void unknownBuildingTypeIsRejected() {
        assertEquals(CommandResult.REJECTED_INVALID_TERRAIN,
            new BuildCivicBuildingCommand(5, 5, BuildingType.RESIDENTIAL).apply(ctx()));
    }

    @Test
    void noCityFoundedNearbyIsRejected() {
        SimulationContext noCityCtx = new SimulationContext(store, buildings, new CityRegistry(), 4096, 0);
        assertEquals(CommandResult.REJECTED_NO_CITY_FOUNDED, new BuildCivicBuildingCommand(5, 5, BuildingType.CEMETERY).apply(noCityCtx));
    }

    @Test
    void occupiedTileIsRejected() {
        assertEquals(CommandResult.ACCEPTED, new BuildCivicBuildingCommand(5, 5, BuildingType.CLINIC).apply(ctx()));
        assertEquals(CommandResult.REJECTED_TILE_OCCUPIED, new BuildCivicBuildingCommand(5, 5, BuildingType.CEMETERY).apply(ctx()));
    }

    @Test
    void policeStationRejectedUntilPopulationUnlocksItThenAccepted() {
        assertEquals(CommandResult.ACCEPTED, new BuildCivicBuildingCommand(5, 5, BuildingType.POLICE_OUTPOST).apply(ctx()));
        assertEquals(CommandResult.REJECTED_SERVICE_TIER_LOCKED,
            new BuildCivicBuildingCommand(6, 5, BuildingType.POLICE_STATION).apply(ctx()));

        int home = buildings.create(BuildingType.RESIDENTIAL, 20, 20, cityId);
        buildings.setPopulation(home, (int) BuildingEconomics.unlockPopulation(BuildingType.POLICE_STATION));
        assertEquals(CommandResult.ACCEPTED, new BuildCivicBuildingCommand(6, 5, BuildingType.POLICE_STATION).apply(ctx()));
    }

    @Test
    void centralBankIsBuildableLikeAnyOtherCivicType() {
        int home = buildings.create(BuildingType.RESIDENTIAL, 20, 20, cityId);
        buildings.setPopulation(home, (int) BuildingEconomics.unlockPopulation(BuildingType.CENTRAL_BANK));

        assertEquals(CommandResult.ACCEPTED, new BuildCivicBuildingCommand(5, 5, BuildingType.CENTRAL_BANK).apply(ctx()));
    }

    @Test
    void cityHallIsRejectedAsAnUnknownTypeSincePlayersCannotBuildItDirectly() {
        assertEquals(CommandResult.REJECTED_INVALID_TERRAIN,
            new BuildCivicBuildingCommand(5, 5, BuildingType.CITY_HALL).apply(ctx()));
    }
}
