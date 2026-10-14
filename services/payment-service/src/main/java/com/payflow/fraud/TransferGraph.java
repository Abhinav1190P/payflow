package com.payflow.fraud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Concept: Graph (adjacency list) + BFS + DFS.
// Nodes are wallets, a directed edge A -> B means "A sent money to B". Money that travels in a circle
// (A -> B -> C -> A) is a classic laundering signal, so cycles are flagged as possible fraud.
// - BFS (shortestPath): after a new transfer A -> B, is there already a path B -> ... -> A? If yes, the new
//   edge closes a cycle. BFS finds the shortest such path. O(V + E) time, O(V) memory.
// - DFS (findCycles): full scan of the graph using white/grey/black colouring; reaching a grey node means
//   we found a back edge, i.e. a cycle. O(V + E).
// Thread safety: all methods are synchronized on this graph; transfers arrive from many threads.
public class TransferGraph {

    private final Map<UUID, Set<UUID>> adjacency = new HashMap<>();
    private int edges;

    public synchronized void addEdge(UUID from, UUID to) {
        if (adjacency.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to)) {
            edges++;
        }
        adjacency.computeIfAbsent(to, k -> new LinkedHashSet<>());
    }

    // Check and insert as one atomic step: if B -> ... -> A already exists, the new edge A -> B closes a cycle.
    // Doing the check and the add under one lock means two concurrent transfers cannot both miss the cycle.
    public synchronized Optional<List<UUID>> addEdgeDetectingCycle(UUID from, UUID to) {
        Optional<List<UUID>> pathBack = shortestPath(to, from);
        addEdge(from, to);
        return pathBack;
    }

    public synchronized Optional<List<UUID>> shortestPath(UUID from, UUID to) {
        if (!adjacency.containsKey(from) || !adjacency.containsKey(to)) {
            return Optional.empty();
        }
        Map<UUID, UUID> parent = new HashMap<>();
        Deque<UUID> queue = new ArrayDeque<>();
        Set<UUID> seen = new HashSet<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (current.equals(to)) {
                List<UUID> path = new ArrayList<>();
                for (UUID step = to; step != null; step = parent.get(step)) {
                    path.add(step);
                }
                Collections.reverse(path);
                return Optional.of(path);
            }
            for (UUID next : adjacency.getOrDefault(current, Set.of())) {
                if (seen.add(next)) {
                    parent.put(next, current);
                    queue.add(next);
                }
            }
        }
        return Optional.empty();
    }

    public synchronized List<List<UUID>> findCycles() {
        Map<UUID, Colour> colour = new HashMap<>();
        List<List<UUID>> cycles = new ArrayList<>();
        Deque<UUID> stack = new ArrayDeque<>();
        for (UUID node : adjacency.keySet()) {
            if (colour.getOrDefault(node, Colour.WHITE) == Colour.WHITE) {
                dfs(node, colour, stack, cycles);
            }
        }
        // Concept: Comparator chaining. Shortest cycles first, then a stable order by the first wallet id.
        cycles.sort(Comparator.<List<UUID>>comparingInt(List::size).thenComparing(c -> c.get(0)));
        return cycles;
    }

    private void dfs(UUID node, Map<UUID, Colour> colour, Deque<UUID> stack, List<List<UUID>> cycles) {
        colour.put(node, Colour.GREY);
        stack.push(node);
        for (UUID next : adjacency.getOrDefault(node, Set.of())) {
            Colour c = colour.getOrDefault(next, Colour.WHITE);
            if (c == Colour.WHITE) {
                dfs(next, colour, stack, cycles);
            } else if (c == Colour.GREY) {
                cycles.add(cycleEndingAt(next, stack));
            }
        }
        stack.pop();
        colour.put(node, Colour.BLACK);
    }

    private static List<UUID> cycleEndingAt(UUID start, Deque<UUID> stack) {
        List<UUID> cycle = new ArrayList<>();
        for (UUID n : stack) {
            cycle.add(n);
            if (n.equals(start)) {
                break;
            }
        }
        Collections.reverse(cycle);
        cycle.add(start);
        return cycle;
    }

    public synchronized int edgeCount() {
        return edges;
    }

    public synchronized int nodeCount() {
        return adjacency.size();
    }

    private enum Colour { WHITE, GREY, BLACK }
}
