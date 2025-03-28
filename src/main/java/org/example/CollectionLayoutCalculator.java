package org.example;

import com.google.gson.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class CollectionLayoutCalculator {
    private static final int BOX_WIDTH = 180;
    private static final int BOX_HEIGHT = 60;
    private static final int LEVEL_VERTICAL_SPACING = 120;
    private static final int SIBLING_HORIZONTAL_SPACING = 60;
    private static final int GROUP_HORIZONTAL_SPACING = 400;

    public Map<String, Rectangle> calculateLayout(JsonObject jsonData) {
        Map<String, Rectangle> layout = new HashMap<>();
        Map<String, List<String>> hierarchy = buildHierarchy(jsonData);
        List<String> roots = findRoots(hierarchy);

        // Разделяем на две группы: Collection и Map
        List<String> collectionRoots = roots.stream()
                .filter(r -> r.contains("Collection") || r.contains("Iterable"))
                .collect(Collectors.toList());
        List<String> mapRoots = roots.stream()
                .filter(r -> r.contains("Map"))
                .collect(Collectors.toList());

        // Рассчитываем позиции для каждой группы
        int collectionX = 50;
        int mapX = collectionX + GROUP_HORIZONTAL_SPACING;
        int y = 50;

        // Обрабатываем Collection hierarchy
        for (String root : collectionRoots) {
            calculateSubtreeLayout(root, hierarchy, layout, collectionX, y, true);
        }

        // Обрабатываем Map hierarchy
        for (String root : mapRoots) {
            calculateSubtreeLayout(root, hierarchy, layout, mapX, y, true);
        }

        return layout;
    }

    private Dimension calculateSubtreeLayout(String node,
                                             Map<String, List<String>> hierarchy,
                                             Map<String, Rectangle> layout,
                                             int x, int y,
                                             boolean isRoot) {
        if (layout.containsKey(node)) {
            return new Dimension(0, 0);
        }

        List<String> children = hierarchy.getOrDefault(node, Collections.emptyList());
        if (children.isEmpty()) {
            Rectangle rect = new Rectangle(x, y, BOX_WIDTH, BOX_HEIGHT);
            layout.put(node, rect);
            return new Dimension(BOX_WIDTH, BOX_HEIGHT);
        }

        // Рекурсивно рассчитываем размеры поддеревьев
        List<Dimension> childDimensions = new ArrayList<>();
        for (String child : children) {
            Dimension dim = calculateSubtreeLayout(child, hierarchy, layout, x, y + LEVEL_VERTICAL_SPACING, false);
            childDimensions.add(dim);
        }

        // Рассчитываем общую ширину поддерева
        int totalWidth = childDimensions.stream()
                .mapToInt(d -> d.width)
                .sum() + SIBLING_HORIZONTAL_SPACING * (children.size() - 1);

        // Центрируем родительский узел над дочерними
        int parentX = x;
        if (!isRoot && totalWidth > BOX_WIDTH) {
            parentX = x + (totalWidth - BOX_WIDTH) / 2;
        }

        layout.put(node, new Rectangle(parentX, y, BOX_WIDTH, BOX_HEIGHT));

        // Позиционируем дочерние узлы
        int childX = x;
        for (int i = 0; i < children.size(); i++) {
            String child = children.get(i);
            Dimension dim = childDimensions.get(i);

            // Центрируем дочерний узел относительно его поддерева
            int centeredX = childX + (dim.width - BOX_WIDTH) / 2;
            layout.get(child).x = centeredX;

            childX += dim.width + SIBLING_HORIZONTAL_SPACING;
        }

        return new Dimension(totalWidth, LEVEL_VERTICAL_SPACING + BOX_HEIGHT);
    }

    private Map<String, List<String>> buildHierarchy(JsonObject jsonData) {
        Map<String, List<String>> hierarchy = new HashMap<>();

        // Обрабатываем интерфейсы
        JsonArray interfaces = jsonData.getAsJsonArray("interfaces");
        for (JsonElement element : interfaces) {
            JsonObject iface = element.getAsJsonObject();
            String name = iface.get("name").getAsString();
            JsonArray parents = iface.getAsJsonArray("parents");

            parents.forEach(p -> {
                String parent = p.getAsString();
                hierarchy.computeIfAbsent(parent, k -> new ArrayList<>()).add(name);
            });
        }

        // Обрабатываем реализации
        JsonArray implementations = jsonData.getAsJsonArray("implementations");
        for (JsonElement element : implementations) {
            JsonObject impl = element.getAsJsonObject();
            String name = impl.get("name").getAsString();
            String parent = impl.get("parent").getAsString();

            hierarchy.computeIfAbsent(parent, k -> new ArrayList<>()).add(name);
        }

        return hierarchy;
    }

    private List<String> findRoots(Map<String, List<String>> hierarchy) {
        Set<String> allChildren = new HashSet<>();
        hierarchy.values().forEach(allChildren::addAll);

        return hierarchy.keySet().stream()
                .filter(k -> !allChildren.contains(k))
                .collect(Collectors.toList());
    }

    public List<Connection> calculateConnections(Map<String, Rectangle> layout,
                                                 JsonObject jsonData) {
        List<Connection> connections = new ArrayList<>();

        // Обрабатываем интерфейсы
        JsonArray interfaces = jsonData.getAsJsonArray("interfaces");
        for (JsonElement element : interfaces) {
            JsonObject iface = element.getAsJsonObject();
            String name = iface.get("name").getAsString();
            JsonArray parents = iface.getAsJsonArray("parents");

            parents.forEach(p -> {
                String parent = p.getAsString();
                connections.add(new Connection(parent, name));
            });
        }

        // Обрабатываем реализации
        JsonArray implementations = jsonData.getAsJsonArray("implementations");
        for (JsonElement element : implementations) {
            JsonObject impl = element.getAsJsonObject();
            String name = impl.get("name").getAsString();
            String parent = impl.get("parent").getAsString();

            connections.add(new Connection(parent, name));
        }

        return connections;
    }

    public static class Connection {
        public final String from;
        public final String to;

        public Connection(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }
}