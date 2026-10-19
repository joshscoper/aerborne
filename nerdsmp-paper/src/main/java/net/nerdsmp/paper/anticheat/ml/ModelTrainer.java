package net.nerdsmp.paper.anticheat.ml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Trains and validates the combat model. Validation is grouped by player: every window from one player sits in the
 * same fold, so the model is always scored on players it never saw. That measures what matters (catching new
 * cheaters without hurting new legitimate players) instead of how well it memorised individuals.
 */
public final class ModelTrainer {
    public record Sample(UUID player, double[] features, int label) { }

    public record Requirements(int minCheatWindows, int minCheatPlayers, int minLegitWindows, int minLegitPlayers,
                               double targetPrecision, double minRecall) {
        public static Requirements defaults() {
            return new Requirements(100, 10, 300, 30, 0.995D, 0.25D);
        }
    }

    /**
     * Outcome of a training run. {@code autonomyReady} is true only when the data requirements are met and the
     * out-of-fold precision at {@code threshold} reaches the target.
     */
    public record Result(GradientBoostedTrees model, double threshold, double precision, double recall, double auc,
                         int cheatWindows, int cheatPlayers, int legitWindows, int legitPlayers, int trees,
                         boolean autonomyReady, String verdict) { }

    private ModelTrainer() {
    }

    public static Result train(List<Sample> samples, Requirements requirements, GradientBoostedTrees.Params params) {
        Set<UUID> cheaters = new HashSet<>();
        Set<UUID> legit = new HashSet<>();
        int cheatWindows = 0;
        for (Sample sample : samples) {
            if (sample.label() == 1) { cheatWindows++; cheaters.add(sample.player()); } else legit.add(sample.player());
        }
        int legitWindows = samples.size() - cheatWindows;
        if (cheatWindows < 5 || legitWindows < 5 || cheaters.size() < 2 || legit.size() < 2) {
            return new Result(null, 1.0D, 0.0D, 0.0D, 0.0D, cheatWindows, cheaters.size(), legitWindows, legit.size(), 0, false,
                "Label at least 5 cheating and 5 legitimate combat windows from 2+ players each before training.");
        }

        // Grouped folds: shuffle players, deal them round-robin into k folds, keeping cheat/legit balance.
        int folds = Math.min(5, Math.min(cheaters.size(), legit.size()));
        Map<UUID, Integer> fold = new HashMap<>();
        Random random = new Random(11L);
        assign(new ArrayList<>(cheaters), folds, fold, random);
        List<UUID> legitOnly = new ArrayList<>(legit);
        legitOnly.removeAll(cheaters);
        assign(legitOnly, folds, fold, random);

        double[] outOfFold = new double[samples.size()];
        int[] bestRounds = new int[folds];
        for (int k = 0; k < folds; k++) {
            List<Sample> trainSet = new ArrayList<>();
            List<Integer> validIndex = new ArrayList<>();
            for (int i = 0; i < samples.size(); i++) {
                if (fold.get(samples.get(i).player()) == k) validIndex.add(i); else trainSet.add(samples.get(i));
            }
            if (validIndex.isEmpty() || trainSet.isEmpty()) { bestRounds[k] = params.trees(); continue; }
            double[][] validX = new double[validIndex.size()][];
            int[] validY = new int[validIndex.size()];
            for (int j = 0; j < validIndex.size(); j++) { validX[j] = samples.get(validIndex.get(j)).features(); validY[j] = samples.get(validIndex.get(j)).label(); }
            int[] best = new int[1];
            GradientBoostedTrees model = fit(trainSet, params, validX, validY, best);
            bestRounds[k] = best[0];
            GradientBoostedTrees stopped = model.truncate(best[0]);
            for (int j = 0; j < validIndex.size(); j++) outOfFold[validIndex.get(j)] = stopped.probability(validX[j]);
        }
        Arrays.sort(bestRounds);
        int rounds = Math.max(10, bestRounds[bestRounds.length / 2]);
        GradientBoostedTrees finalModel = fit(samples, new GradientBoostedTrees.Params(rounds, params.depth(), params.learningRate(),
            params.lambda(), params.minChildWeight(), params.subsample(), params.bins(), params.seed()), null, null, null);

        int[] labels = samples.stream().mapToInt(Sample::label).toArray();
        double auc = auc(outOfFold, labels);
        double[] chosen = chooseThreshold(outOfFold, labels, requirements.targetPrecision());
        boolean enoughData = cheatWindows >= requirements.minCheatWindows() && cheaters.size() >= requirements.minCheatPlayers()
            && legitWindows >= requirements.minLegitWindows() && legit.size() >= requirements.minLegitPlayers();
        boolean accurate = chosen[1] >= requirements.targetPrecision() && chosen[2] >= requirements.minRecall();
        String verdict;
        if (!enoughData) verdict = String.format(java.util.Locale.ROOT,
            "Advisory only: needs %d cheating windows from %d players and %d legitimate windows from %d players before acting alone.",
            requirements.minCheatWindows(), requirements.minCheatPlayers(), requirements.minLegitWindows(), requirements.minLegitPlayers());
        else if (!accurate) verdict = String.format(java.util.Locale.ROOT,
            "Advisory only: on unseen players it reached %.1f%% precision and %.0f%% recall; autonomy needs %.1f%% precision and %.0f%% recall.",
            chosen[1] * 100, chosen[2] * 100, requirements.targetPrecision() * 100, requirements.minRecall() * 100);
        else verdict = String.format(java.util.Locale.ROOT,
            "Validated on unseen players: %.2f%% precision, %.0f%% recall at %.1f%% confidence. Autonomous actions enabled.",
            chosen[1] * 100, chosen[2] * 100, chosen[0] * 100);
        return new Result(finalModel, chosen[0], chosen[1], chosen[2], auc, cheatWindows, cheaters.size(), legitWindows, legit.size(),
            rounds, enoughData && accurate, verdict);
    }

    private static void assign(List<UUID> players, int folds, Map<UUID, Integer> fold, Random random) {
        Collections.shuffle(players, random);
        for (int i = 0; i < players.size(); i++) fold.putIfAbsent(players.get(i), i % folds);
    }

    private static GradientBoostedTrees fit(List<Sample> rows, GradientBoostedTrees.Params params, double[][] validX, int[] validY, int[] best) {
        double[][] x = new double[rows.size()][];
        int[] y = new int[rows.size()];
        double positives = 0;
        for (int i = 0; i < rows.size(); i++) { x[i] = rows.get(i).features(); y[i] = rows.get(i).label(); positives += y[i]; }
        // Balance the classes so a rare cheat label still carries equal total weight.
        double negatives = rows.size() - positives;
        double[] weights = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) weights[i] = y[i] == 1 ? rows.size() / (2.0D * Math.max(1, positives)) : rows.size() / (2.0D * Math.max(1, negatives));
        return GradientBoostedTrees.train(x, y, weights, params, validX, validY, best);
    }

    /**
     * Lowest threshold whose out-of-fold precision meets the target (maximising recall). Returns
     * {threshold, precision, recall}; if no threshold qualifies, returns the most precise one found.
     */
    static double[] chooseThreshold(double[] scores, int[] labels, double target) {
        Integer[] order = new Integer[scores.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(scores[b], scores[a]));
        int positives = 0;
        for (int label : labels) positives += label;
        int truePositive = 0;
        int predicted = 0;
        double[] best = {1.0D, 0.0D, 0.0D};
        double[] mostPrecise = {1.0D, 0.0D, 0.0D};
        for (int i = 0; i < order.length; i++) {
            predicted++;
            truePositive += labels[order[i]];
            if (i + 1 < order.length && scores[order[i + 1]] == scores[order[i]]) continue; // ties share a threshold
            double precision = (double) truePositive / predicted;
            double recall = positives == 0 ? 0.0D : (double) truePositive / positives;
            double threshold = scores[order[i]];
            if (precision >= target && recall >= best[2]) best = new double[] {threshold, precision, recall};
            if (precision > mostPrecise[1] || (precision == mostPrecise[1] && recall > mostPrecise[2])) mostPrecise = new double[] {threshold, precision, recall};
        }
        return best[2] > 0.0D ? best : mostPrecise;
    }

    static double auc(double[] scores, int[] labels) {
        long positives = 0;
        long negatives = 0;
        for (int label : labels) if (label == 1) positives++; else negatives++;
        if (positives == 0 || negatives == 0) return 0.5D;
        Integer[] order = new Integer[scores.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(scores[a], scores[b]));
        double rankSum = 0.0D;
        int i = 0;
        while (i < order.length) {
            int j = i;
            while (j + 1 < order.length && scores[order[j + 1]] == scores[order[i]]) j++;
            double rank = (i + j) / 2.0D + 1.0D;
            for (int k = i; k <= j; k++) if (labels[order[k]] == 1) rankSum += rank;
            i = j + 1;
        }
        return (rankSum - positives * (positives + 1) / 2.0D) / ((double) positives * negatives);
    }
}
