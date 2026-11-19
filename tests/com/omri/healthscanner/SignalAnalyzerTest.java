package com.omri.healthscanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SignalAnalyzerTest {
    public static void main(String[] args) {
        detectsSyntheticPulseNear72Bpm();
        detectsSyntheticRespirationNear15Bpm();
        rejectsRespirationWithFewerThanThreeCycles();
        rejectsGaussianNoiseTraces();
        rejectsIrregularFrameTiming();
        rejectsTooFewSamples();
        System.out.println("SignalAnalyzerTest passed");
    }

    private static void detectsSyntheticPulseNear72Bpm() {
        List<SignalAnalyzer.Sample> samples = syntheticSignal(30.0, 15.0, 72.0, 15.0);
        SignalAnalyzer.Estimate estimate = SignalAnalyzer.estimate(samples, 0.75, 3.0);
        assertTrue(estimate.available, "pulse should be available");
        assertNear(72.0, estimate.ratePerMinute, 3.0, "pulse rate");
        assertTrue(estimate.quality >= 0.45, "pulse quality should be usable");
    }

    private static void detectsSyntheticRespirationNear15Bpm() {
        List<SignalAnalyzer.Sample> samples = syntheticSignal(20.0, 15.0, 72.0, 15.0);
        SignalAnalyzer.Estimate estimate = SignalAnalyzer.estimate(samples, 0.10, 0.50);
        assertTrue(estimate.available, "respiration should be available");
        assertNear(15.0, estimate.ratePerMinute, 2.0, "respiration rate");
    }

    private static void rejectsRespirationWithFewerThanThreeCycles() {
        List<SignalAnalyzer.Sample> samples = syntheticSignal(20.0, 15.0, 72.0, 6.0);
        SignalAnalyzer.Estimate estimate = SignalAnalyzer.estimate(samples, 0.10, 0.50);
        assertTrue(!estimate.available, "fewer than three observed cycles must be rejected");
    }

    private static void rejectsGaussianNoiseTraces() {
        Random random = new Random(4102026L);
        for (int trial = 0; trial < 100; trial++) {
            List<SignalAnalyzer.Sample> samples = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                double t = i / 15.0;
                samples.add(new SignalAnalyzer.Sample(t, 120.0 + random.nextGaussian()));
            }
            assertTrue(!SignalAnalyzer.estimate(samples, 0.75, 3.0).available,
                    "Gaussian noise must not produce a pulse");
            assertTrue(!SignalAnalyzer.estimate(samples, 0.10, 0.50).available,
                    "Gaussian noise must not produce respiration");
        }
    }

    private static void rejectsIrregularFrameTiming() {
        List<SignalAnalyzer.Sample> samples = new ArrayList<>();
        double t = 0.0;
        for (int i = 0; i < 180; i++) {
            if (i % 30 == 0) t += 0.8;
            else t += 1.0 / 15.0;
            samples.add(new SignalAnalyzer.Sample(t,
                    120.0 + Math.sin(2.0 * Math.PI * 1.2 * t)));
        }
        assertTrue(!SignalAnalyzer.estimate(samples, 0.75, 3.0).available,
                "large frame gaps must suppress a numeric estimate");
    }

    private static void rejectsTooFewSamples() {
        List<SignalAnalyzer.Sample> samples = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            samples.add(new SignalAnalyzer.Sample(i * 0.1, 100.0));
        }
        SignalAnalyzer.Estimate estimate = SignalAnalyzer.estimate(samples, 0.75, 3.0);
        assertTrue(!estimate.available, "short signals must be rejected");
    }

    private static List<SignalAnalyzer.Sample> syntheticSignal(
            double seconds, double fps, double pulseBpm, double respirationBpm) {
        List<SignalAnalyzer.Sample> samples = new ArrayList<>();
        int count = (int) (seconds * fps);
        for (int i = 0; i < count; i++) {
            double t = i / fps;
            double pulse = 1.0 * Math.sin(2.0 * Math.PI * (pulseBpm / 60.0) * t);
            double respiration = 1.8 * Math.sin(2.0 * Math.PI * (respirationBpm / 60.0) * t);
            double drift = 0.003 * i;
            samples.add(new SignalAnalyzer.Sample(t, 120.0 + pulse + respiration + drift));
        }
        return samples;
    }

    private static void assertNear(double expected, double actual, double tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
