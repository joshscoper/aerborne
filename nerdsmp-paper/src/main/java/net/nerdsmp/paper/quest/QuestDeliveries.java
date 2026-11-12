package net.nerdsmp.paper.quest;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Allocates ordinary inventory items once across all deliveries, including overlapping alternatives. */
final class QuestDeliveries {
    record Allocation(int[] counts, Map<String, Integer> consumed) { }

    static Allocation allocate(QuestDefinition quest, Map<String, Integer> inventory) {
        List<String> materials = inventory.keySet().stream().sorted().toList();
        int objectives = quest.objectives().size();
        int sink = 1 + materials.size() + objectives;
        int[][] capacity = new int[sink + 1][sink + 1];
        int[][] flow = new int[sink + 1][sink + 1];
        for (int m = 0; m < materials.size(); m++) {
            capacity[0][1 + m] = Math.max(0, inventory.get(materials.get(m)));
            for (int i = 0; i < objectives; i++) {
                var objective = quest.objectives().get(i);
                if (objective.type() == QuestDefinition.Type.DELIVER && objective.matches(materials.get(m)))
                    capacity[1 + m][1 + materials.size() + i] = objective.count();
            }
        }
        for (int i = 0; i < objectives; i++) {
            var objective = quest.objectives().get(i);
            if (objective.type() == QuestDefinition.Type.DELIVER)
                capacity[1 + materials.size() + i][sink] = objective.count();
        }
        while (true) {
            int[] parent = new int[sink + 1];
            Arrays.fill(parent, -1);
            parent[0] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(0);
            while (!queue.isEmpty() && parent[sink] < 0) {
                int from = queue.remove();
                for (int to = 1; to <= sink; to++) {
                    if (parent[to] < 0 && capacity[from][to] > flow[from][to]) {
                        parent[to] = from;
                        queue.add(to);
                    }
                }
            }
            if (parent[sink] < 0) break;
            int amount = Integer.MAX_VALUE;
            for (int to = sink; to != 0; to = parent[to])
                amount = Math.min(amount, capacity[parent[to]][to] - flow[parent[to]][to]);
            for (int to = sink; to != 0; to = parent[to]) {
                flow[parent[to]][to] += amount;
                flow[to][parent[to]] -= amount;
            }
        }
        int[] counts = new int[objectives];
        for (int i = 0; i < objectives; i++) counts[i] = flow[1 + materials.size() + i][sink];
        Map<String, Integer> consumed = new LinkedHashMap<>();
        for (int m = 0; m < materials.size(); m++)
            if (flow[0][1 + m] > 0) consumed.put(materials.get(m), flow[0][1 + m]);
        return new Allocation(counts, Map.copyOf(consumed));
    }
}
