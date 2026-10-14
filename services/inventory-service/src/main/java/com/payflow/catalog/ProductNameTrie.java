package com.payflow.catalog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

// Concept: Trie (prefix tree) for autocomplete.
// Each node is one character; a path from the root spells a prefix. Every word of every product name is
// inserted, and the node where a word ends remembers the full product names containing that word.
// Typing "mo" walks two nodes, then a DFS below that node collects matching names.
// Big-O: insert O(L) per word of length L; lookup O(P) to reach the prefix node + O(k) to collect k
// results, independent of how many products exist. Children are kept in a TreeMap so results come out
// in alphabetical order.
public class ProductNameTrie {

    private static final class Node {
        private final TreeMap<Character, Node> children = new TreeMap<>();
        private final Set<String> names = new TreeSet<>();
    }

    private final Node root = new Node();
    private int words;

    public synchronized void add(String productName) {
        for (String word : productName.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}]+")) {
            if (word.isEmpty()) {
                continue;
            }
            Node node = root;
            for (char c : word.toCharArray()) {
                node = node.children.computeIfAbsent(c, k -> new Node());
            }
            if (node.names.add(productName)) {
                words++;
            }
        }
    }

    public synchronized List<String> complete(String prefix, int limit) {
        if (prefix == null || prefix.isBlank() || limit < 1) {
            return List.of();
        }
        Node node = root;
        for (char c : prefix.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            node = node.children.get(c);
            if (node == null) {
                return List.of();
            }
        }
        Set<String> results = new LinkedHashSet<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty() && results.size() < limit) {
            Node current = stack.pop();
            for (String name : current.names) {
                if (results.size() == limit) {
                    break;
                }
                results.add(name);
            }
            List<Node> kids = new ArrayList<>(current.children.values());
            for (int i = kids.size() - 1; i >= 0; i--) {
                stack.push(kids.get(i));
            }
        }
        return List.copyOf(results);
    }

    public synchronized int indexedWords() {
        return words;
    }

    Map<Character, ?> rootChildren() {
        return root.children;
    }
}
