package net.nerdsmp.paper.anticheat.ml;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ModelTest {
    /** Synthetic combat windows: cheaters aim closer to the centre and click with repetitive rhythm. */
    private static List<ModelTrainer.Sample> population(int cheaters, int legit, int windows, double separation, long seed) {
        Random random = new Random(seed);
        List<ModelTrainer.Sample> samples = new ArrayList<>();
        for (int p = 0; p < cheaters + legit; p++) {
            UUID id = UUID.randomUUID();
            boolean cheat = p < cheaters;
            double personal = random.nextGaussian() * 0.3D;
            for (int w = 0; w < windows; w++) {
                double[] x = new double[Features.COUNT];
                for (int f = 0; f < x.length; f++) x[f] = random.nextGaussian() + personal;
                if (cheat) {
                    x[Features.AIM_OFFSET_MEAN] -= separation;
                    x[Features.CLICK_MODE] += separation;
                }
                samples.add(new ModelTrainer.Sample(id, x, cheat ? 1 : 0));
            }
        }
        return samples;
    }

    @Test void boostedTreesLearnAndExplainASeparablePattern() {
        var train = population(30, 30, 10, 3.0D, 1);
        var test = population(10, 10, 10, 3.0D, 2);
        double[][] x = train.stream().map(ModelTrainer.Sample::features).toArray(double[][]::new);
        int[] y = train.stream().mapToInt(ModelTrainer.Sample::label).toArray();
        double[] w = new double[x.length];
        java.util.Arrays.fill(w, 1.0D);
        var model = GradientBoostedTrees.train(x, y, w, GradientBoostedTrees.Params.defaults(), null, null, null);
        double[] scores = test.stream().mapToDouble(s -> model.probability(s.features())).toArray();
        int[] labels = test.stream().mapToInt(ModelTrainer.Sample::label).toArray();
        assertTrue(ModelTrainer.auc(scores, labels) > 0.97D);

        double[] cheat = test.getFirst().features();
        double[] reasons = model.explain(cheat);
        int top = 0;
        for (int f = 1; f < reasons.length; f++) if (reasons[f] > reasons[top]) top = f;
        assertTrue(top == Features.AIM_OFFSET_MEAN || top == Features.CLICK_MODE, "top reason was " + Features.NAMES.get(top));

        var copy = GradientBoostedTrees.fromJson(model.toJson());
        for (var sample : test) assertEquals(model.probability(sample.features()), copy.probability(sample.features()), 1.0E-12);
    }

    @Test void trainerUnlocksAutonomyOnlyWithEnoughCleanlySeparatedData() {
        var strong = ModelTrainer.train(population(15, 40, 10, 6.0D, 3), ModelTrainer.Requirements.defaults(), GradientBoostedTrees.Params.defaults());
        assertTrue(strong.autonomyReady(), strong.verdict());
        assertTrue(strong.precision() >= 0.995D && strong.recall() >= 0.25D);

        var tooFew = ModelTrainer.train(population(3, 3, 10, 6.0D, 4), ModelTrainer.Requirements.defaults(), GradientBoostedTrees.Params.defaults());
        assertFalse(tooFew.autonomyReady());
        assertNotNull(tooFew.model());

        var noisy = ModelTrainer.train(population(15, 40, 10, 0.2D, 5), ModelTrainer.Requirements.defaults(), GradientBoostedTrees.Params.defaults());
        assertFalse(noisy.autonomyReady(), "overlapping classes must not unlock autonomy: " + noisy.verdict());

        var empty = ModelTrainer.train(List.of(), ModelTrainer.Requirements.defaults(), GradientBoostedTrees.Params.defaults());
        assertNull(empty.model());
    }

    @Test void thresholdAndAucMaths() {
        double[] scores = {0.9, 0.8, 0.7, 0.4, 0.3};
        int[] labels = {1, 1, 0, 1, 0};
        double[] chosen = ModelTrainer.chooseThreshold(scores, labels, 1.0D);
        assertEquals(0.8D, chosen[0]);
        assertEquals(1.0D, chosen[1]);
        assertEquals(2.0D / 3.0D, chosen[2], 1.0E-9);
        assertEquals(1.0D, ModelTrainer.auc(new double[] {0.1, 0.9}, new int[] {0, 1}));
        assertEquals(0.5D, ModelTrainer.auc(new double[] {0.5, 0.5}, new int[] {0, 1}));
    }

    @Test void baselineFlagsOutliersOnlyAfterWarmup() {
        Baseline baseline = new Baseline();
        Random random = new Random(9);
        double[] odd = new double[Features.COUNT];
        odd[Features.CLICK_MODE] = 12.0D;
        assertEquals(0.0D, baseline.anomaly(odd, null));
        for (int i = 0; i < 200; i++) {
            double[] x = new double[Features.COUNT];
            for (int f = 0; f < x.length; f++) x[f] = random.nextGaussian();
            baseline.observe(x);
        }
        int[] top = new int[3];
        assertTrue(baseline.anomaly(odd, top) > 3.0D);
        assertEquals(Features.CLICK_MODE, top[0]);
    }
}
