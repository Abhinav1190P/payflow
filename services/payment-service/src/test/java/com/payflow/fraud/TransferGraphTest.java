package com.payflow.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferGraphTest {

    private final UUID a = new UUID(0, 1);
    private final UUID b = new UUID(0, 2);
    private final UUID c = new UUID(0, 3);
    private final UUID d = new UUID(0, 4);

    @Test
    void bfsFindsShortestPath() {
        var graph = new TransferGraph();
        graph.addEdge(a, b);
        graph.addEdge(b, c);
        graph.addEdge(a, c);
        assertThat(graph.shortestPath(a, c)).contains(java.util.List.of(a, c));
        assertThat(graph.shortestPath(c, a)).isEmpty();
    }

    @Test
    void dfsFindsCircularChain() {
        var graph = new TransferGraph();
        graph.addEdge(a, b);
        graph.addEdge(b, c);
        graph.addEdge(c, a);
        graph.addEdge(c, d);
        var cycles = graph.findCycles();
        assertThat(cycles).hasSize(1);
        assertThat(cycles.get(0)).hasSize(4);
        assertThat(cycles.get(0).get(0)).isEqualTo(cycles.get(0).get(3));
        assertThat(cycles.get(0)).contains(a, b, c).doesNotContain(d);
    }

    @Test
    void acyclicGraphHasNoCycles() {
        var graph = new TransferGraph();
        graph.addEdge(a, b);
        graph.addEdge(b, c);
        graph.addEdge(a, c);
        assertThat(graph.findCycles()).isEmpty();
    }

    @Test
    void addEdgeDetectingCycleReportsThePathBack() {
        var graph = new TransferGraph();
        assertThat(graph.addEdgeDetectingCycle(a, b)).isEmpty();
        assertThat(graph.addEdgeDetectingCycle(b, c)).isEmpty();
        assertThat(graph.addEdgeDetectingCycle(c, a)).contains(java.util.List.of(a, b, c));
        assertThat(graph.edgeCount()).isEqualTo(3);
    }

    @Test
    void repeatedEdgesAreCountedOnce() {
        var graph = new TransferGraph();
        graph.addEdge(a, b);
        graph.addEdge(a, b);
        assertThat(graph.edgeCount()).isEqualTo(1);
        assertThat(graph.nodeCount()).isEqualTo(2);
    }
}
