package net.nerdsmp.paper.anticheat.ml;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Binary gradient-boosted decision trees with logistic loss: the standard strongest model family for tabular data.
 * Training uses per-feature quantile bins so it stays fast on tens of thousands of windows. Each prediction can be
 * explained by summing how much every split on its path moved the score (Saabas attribution).
 */
public final class GradientBoostedTrees {
    public record Params(int trees, int depth, double learningRate, double lambda, double minChildWeight, double subsample, int bins, long seed) {
        public static Params defaults() {
            return new Params(300, 3, 0.08D, 1.0D, 2.0D, 0.8D, 32, 7L);
        }
    }

    /** Flattened tree: internal nodes have feature >= 0; leaves have feature -1. Values are already scaled by the learning rate. */
    record Tree(int[] feature, double[] threshold, int[] left, int[] right, double[] value) {
        double predict(double[] x) {
            int node = 0;
            while (feature[node] >= 0) node = x[feature[node]] <= threshold[node] ? left[node] : right[node];
            return value[node];
        }

        void explain(double[] x, double[] contributions) {
            int node = 0;
            while (feature[node] >= 0) {
                int next = x[feature[node]] <= threshold[node] ? left[node] : right[node];
                contributions[feature[node]] += value[next] - value[node];
                node = next;
            }
        }
    }

    private final double base;
    private final List<Tree> trees;
    private final int featureCount;

    private GradientBoostedTrees(double base, List<Tree> trees, int featureCount) {
        this.base = base;
        this.trees = List.copyOf(trees);
        this.featureCount = featureCount;
    }

    public int size() {
        return trees.size();
    }

    public double probability(double[] x) {
        return sigmoid(rawScore(x, trees.size()));
    }

    double rawScore(double[] x, int limit) {
        double score = base;
        for (int i = 0; i < limit; i++) score += trees.get(i).predict(x);
        return score;
    }

    /** Per-feature contribution to the log-odds of this prediction; positive values push towards "cheating". */
    public double[] explain(double[] x) {
        double[] contributions = new double[featureCount];
        for (Tree tree : trees) tree.explain(x, contributions);
        return contributions;
    }

    /** Returns a copy keeping only the first {@code count} trees (early stopping). */
    public GradientBoostedTrees truncate(int count) {
        return new GradientBoostedTrees(base, trees.subList(0, Math.max(1, Math.min(count, trees.size()))), featureCount);
    }

    /**
     * Trains on rows x with labels y (0/1) and per-row weights. If validation rows are given, returns the full model
     * and records the best iteration count in {@code bestIterations[0]} by validation log-loss.
     */
    public static GradientBoostedTrees train(double[][] x, int[] y, double[] weights, Params params,
                                             double[][] validX, int[] validY, int[] bestIterations) {
        int n = x.length;
        int features = x[0].length;
        double positive = 0.0D;
        double total = 0.0D;
        for (int i = 0; i < n; i++) { positive += weights[i] * y[i]; total += weights[i]; }
        double prior = Math.min(0.999D, Math.max(0.001D, positive / total));
        double base = Math.log(prior / (1.0D - prior));

        double[][] edges = binEdges(x, params.bins());
        int[][] binned = new int[n][features];
        for (int i = 0; i < n; i++) for (int f = 0; f < features; f++) binned[i][f] = bin(edges[f], x[i][f]);

        double[] score = new double[n];
        Arrays.fill(score, base);
        double[] validScore = validX == null ? null : new double[validX.length];
        if (validScore != null) Arrays.fill(validScore, base);
        double bestLoss = Double.MAX_VALUE;
        int best = 0;
        List<Tree> trees = new ArrayList<>();
        Random random = new Random(params.seed());
        double[] gradient = new double[n];
        double[] hessian = new double[n];
        for (int round = 0; round < params.trees(); round++) {
            for (int i = 0; i < n; i++) {
                double p = sigmoid(score[i]);
                gradient[i] = (p - y[i]) * weights[i];
                hessian[i] = Math.max(1.0E-6D, p * (1.0D - p)) * weights[i];
            }
            int[] rows = sample(n, params.subsample(), random);
            Tree tree = grow(rows, binned, edges, gradient, hessian, params);
            trees.add(tree);
            for (int i = 0; i < n; i++) score[i] += tree.predict(x[i]);
            if (validScore != null) {
                double loss = 0.0D;
                for (int i = 0; i < validX.length; i++) {
                    validScore[i] += tree.predict(validX[i]);
                    double p = Math.min(1.0D - 1.0E-9D, Math.max(1.0E-9D, sigmoid(validScore[i])));
                    loss -= validY[i] == 1 ? Math.log(p) : Math.log(1.0D - p);
                }
                if (loss < bestLoss - 1.0E-9D) { bestLoss = loss; best = round + 1; }
            }
        }
        if (bestIterations != null) bestIterations[0] = validScore == null ? trees.size() : Math.max(1, best);
        return new GradientBoostedTrees(base, trees, features);
    }

    private static Tree grow(int[] rows, int[][] binned, double[][] edges, double[] g, double[] h, Params params) {
        int maxNodes = (1 << (params.depth() + 1)) - 1;
        int[] feature = new int[maxNodes];
        double[] threshold = new double[maxNodes];
        int[] left = new int[maxNodes];
        int[] right = new int[maxNodes];
        double[] value = new double[maxNodes];
        Arrays.fill(feature, -1);
        int[] count = {1};
        build(0, rows, 0, binned, edges, g, h, params, feature, threshold, left, right, value, count);
        return new Tree(Arrays.copyOf(feature, count[0]), Arrays.copyOf(threshold, count[0]), Arrays.copyOf(left, count[0]),
            Arrays.copyOf(right, count[0]), Arrays.copyOf(value, count[0]));
    }

    private static void build(int node, int[] rows, int depth, int[][] binned, double[][] edges, double[] g, double[] h, Params params,
                              int[] feature, double[] threshold, int[] left, int[] right, double[] value, int[] count) {
        double sumG = 0.0D;
        double sumH = 0.0D;
        for (int row : rows) { sumG += g[row]; sumH += h[row]; }
        value[node] = -sumG / (sumH + params.lambda()) * params.learningRate();
        if (depth >= params.depth() || sumH < 2.0D * params.minChildWeight()) return;
        double parentScore = sumG * sumG / (sumH + params.lambda());
        double bestGain = 1.0E-9D;
        int bestFeature = -1;
        int bestBin = -1;
        int features = edges.length;
        double[] binG = new double[params.bins() + 1];
        double[] binH = new double[params.bins() + 1];
        for (int f = 0; f < features; f++) {
            Arrays.fill(binG, 0.0D);
            Arrays.fill(binH, 0.0D);
            for (int row : rows) { binG[binned[row][f]] += g[row]; binH[binned[row][f]] += h[row]; }
            double leftG = 0.0D;
            double leftH = 0.0D;
            for (int b = 0; b < edges[f].length; b++) {
                leftG += binG[b];
                leftH += binH[b];
                double rightH = sumH - leftH;
                if (leftH < params.minChildWeight() || rightH < params.minChildWeight()) continue;
                double rightG = sumG - leftG;
                double gain = leftG * leftG / (leftH + params.lambda()) + rightG * rightG / (rightH + params.lambda()) - parentScore;
                if (gain > bestGain) { bestGain = gain; bestFeature = f; bestBin = b; }
            }
        }
        if (bestFeature < 0) return;
        double cut = edges[bestFeature][bestBin];
        int leftCount = 0;
        for (int row : rows) if (binned[row][bestFeature] <= bestBin) leftCount++;
        int[] leftRows = new int[leftCount];
        int[] rightRows = new int[rows.length - leftCount];
        int li = 0;
        int ri = 0;
        for (int row : rows) { if (binned[row][bestFeature] <= bestBin) leftRows[li++] = row; else rightRows[ri++] = row; }
        feature[node] = bestFeature;
        threshold[node] = cut;
        left[node] = count[0]++;
        right[node] = count[0]++;
        build(left[node], leftRows, depth + 1, binned, edges, g, h, params, feature, threshold, left, right, value, count);
        build(right[node], rightRows, depth + 1, binned, edges, g, h, params, feature, threshold, left, right, value, count);
    }

    /** Quantile cut points per feature; bin b holds values <= edges[b], the last bin holds the rest. */
    private static double[][] binEdges(double[][] x, int bins) {
        int features = x[0].length;
        double[][] edges = new double[features][];
        double[] column = new double[x.length];
        for (int f = 0; f < features; f++) {
            for (int i = 0; i < x.length; i++) column[i] = x[i][f];
            double[] sorted = column.clone();
            Arrays.sort(sorted);
            double[] cuts = new double[bins];
            int unique = 0;
            for (int b = 1; b <= bins; b++) {
                double cut = sorted[Math.min(sorted.length - 1, (int) ((long) b * sorted.length / (bins + 1)))];
                if (unique == 0 || cut > cuts[unique - 1]) cuts[unique++] = cut;
            }
            edges[f] = Arrays.copyOf(cuts, unique);
        }
        return edges;
    }

    private static int bin(double[] edges, double value) {
        int index = Arrays.binarySearch(edges, value);
        return index >= 0 ? index : -index - 1;
    }

    private static int[] sample(int n, double fraction, Random random) {
        if (fraction >= 1.0D) { int[] all = new int[n]; for (int i = 0; i < n; i++) all[i] = i; return all; }
        int[] picked = new int[n];
        int size = 0;
        for (int i = 0; i < n; i++) if (random.nextDouble() < fraction) picked[size++] = i;
        if (size == 0) picked[size++] = random.nextInt(n);
        return Arrays.copyOf(picked, size);
    }

    static double sigmoid(double value) {
        return 1.0D / (1.0D + Math.exp(-value));
    }

    // ---- Persistence ----

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.addProperty("features", featureCount);
        root.addProperty("base", base);
        JsonArray array = new JsonArray();
        for (Tree tree : trees) {
            JsonObject node = new JsonObject();
            node.add("f", ints(tree.feature()));
            node.add("t", doubles(tree.threshold()));
            node.add("l", ints(tree.left()));
            node.add("r", ints(tree.right()));
            node.add("v", doubles(tree.value()));
            array.add(node);
        }
        root.add("trees", array);
        return root;
    }

    public static GradientBoostedTrees fromJson(JsonObject root) {
        if (root.get("format").getAsInt() != 1) throw new IllegalArgumentException("Unknown model format");
        List<Tree> trees = new ArrayList<>();
        for (var element : root.getAsJsonArray("trees")) {
            JsonObject node = element.getAsJsonObject();
            Tree tree = new Tree(toInts(node.getAsJsonArray("f")), toDoubles(node.getAsJsonArray("t")), toInts(node.getAsJsonArray("l")),
                toInts(node.getAsJsonArray("r")), toDoubles(node.getAsJsonArray("v")));
            int size = tree.feature().length;
            if (tree.threshold().length != size || tree.left().length != size || tree.right().length != size || tree.value().length != size) {
                throw new IllegalArgumentException("Corrupt tree");
            }
            trees.add(tree);
        }
        return new GradientBoostedTrees(root.get("base").getAsDouble(), trees, root.get("features").getAsInt());
    }

    private static JsonArray ints(int[] values) { JsonArray a = new JsonArray(); for (int v : values) a.add(v); return a; }
    private static JsonArray doubles(double[] values) { JsonArray a = new JsonArray(); for (double v : values) a.add(v); return a; }
    private static int[] toInts(JsonArray a) { int[] v = new int[a.size()]; for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsInt(); return v; }
    private static double[] toDoubles(JsonArray a) { double[] v = new double[a.size()]; for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsDouble(); return v; }
}
