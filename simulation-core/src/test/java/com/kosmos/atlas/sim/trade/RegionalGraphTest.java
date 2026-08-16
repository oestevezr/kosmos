package com.kosmos.atlas.sim.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionalGraphTest {

    @Test
    void addNodeIsRetrievableAndActive() {
        RegionalGraph graph = new RegionalGraph();
        int id = graph.addNode(NodeType.EXTERNAL_MARKET, 10, 20);

        assertTrue(graph.isNodeActive(id));
        assertEquals(NodeType.EXTERNAL_MARKET, graph.nodeType(id));
        assertEquals(10, graph.nodeTileX(id));
        assertEquals(20, graph.nodeTileY(id));
    }

    @Test
    void removeNodeMakesItInactiveAndReusable() {
        RegionalGraph graph = new RegionalGraph();
        int id = graph.addNode(NodeType.EXTERNAL_MARKET, 5, 5);
        graph.removeNode(id);

        assertFalse(graph.isNodeActive(id));

        int reused = graph.addNode(NodeType.PORT, 1, 1);
        assertEquals(id, reused, "the freed id should be reused, keeping ids dense");
    }

    @Test
    void nearestNodeOfTypeFindsClosestMatchAndIgnoresOtherTypes() {
        RegionalGraph graph = new RegionalGraph();
        graph.addNode(NodeType.EXTERNAL_MARKET, 100, 100); // far
        int near = graph.addNode(NodeType.EXTERNAL_MARKET, 1, 1);
        graph.addNode(NodeType.PORT, 0, 0); // closer in distance but wrong type

        int found = graph.nearestNodeOfType(0, 0, NodeType.EXTERNAL_MARKET);
        assertEquals(near, found);
    }

    @Test
    void nearestNodeOfTypeReturnsMinusOneWhenNoneExist() {
        RegionalGraph graph = new RegionalGraph();
        assertEquals(-1, graph.nearestNodeOfType(0, 0, NodeType.EXTERNAL_MARKET));
    }

    @Test
    void distanceTilesMatchesEuclideanDistance() {
        RegionalGraph graph = new RegionalGraph();
        int id = graph.addNode(NodeType.EXTERNAL_MARKET, 0, 0);
        assertEquals(5.0, graph.distanceTiles(id, 3, 4), 0.001); // 3-4-5 triangle
    }

    @Test
    void addEdgeGrowsAndReportsCount() {
        RegionalGraph graph = new RegionalGraph(4, 1);
        int a = graph.addNode(NodeType.EXTERNAL_MARKET, 0, 0);
        int b = graph.addNode(NodeType.PORT, 10, 0);
        for (int i = 0; i < 10; i++) {
            graph.addEdge(a, b, 10f, 100, 0.5f);
        }
        assertEquals(10, graph.edgeCount());
    }
}
