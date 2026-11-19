package com.omri.healthscanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Frequency-domain estimator for a slowly sampled camera colour trace. */
public final class SignalAnalyzer {
    private SignalAnalyzer() {}

    public static final class Sample {
        public final double seconds;
        public final double value;

        public Sample(double seconds, double value) {
            this.seconds = seconds;
            this.value = value;
        }
    }

    public static final class Estimate {
        public final boolean available;
        public final double ratePerMinute;
        public final double quality;
        public final String reason;

        private Estimate(boolean available, double ratePerMinute, double quality, String reason) {
            this.available = available;
            this.ratePerMinute = ratePerMinute;
            this.quality = quality;
            this.reason = reason;
        }

        public static Estimate unavailable(String reason) {
            return new Estimate(false, Double.NaN, 0.0, reason);
        }

        static Estimate available(double ratePerMinute, double quality) {
            return new Estimate(true, ratePerMinute, quality, "");
        }
    }

    public static Estimate estimate(List<Sample> input, double minHz, double maxHz) {
        if (input == null || input.size() < 60) {
            return Estimate.unavailable("Not enough camera samples");
        }
        List<Sample> samples = new ArrayList<>(input);
        samples.sort((a, b) -> Double.compare(a.seconds, b.seconds));
        double maximumGap = 0.0;
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            if (!Double.isFinite(sample.seconds) || !Double.isFinite(sample.value)) {
                return Estimate.unavailable("Camera samples were invalid");
            }
            if (i > 0) {
                double gap = sample.seconds - samples.get(i - 1).seconds;
                if (gap <= 0.0) return Estimate.unavailable("Camera timestamps were invalid");
                maximumGap = Math.max(maximumGap, gap);
            }
        }
        if (maximumGap > 0.50) {
            return Estimate.unavailable("Camera frame timing was too irregular");
        }
        double start = samples.get(0).seconds;
        double duration = samples.get(samples.size() - 1).seconds - start;
        if (duration < 10.0 || minHz <= 0.0 || maxHz <= minHz) {
            return Estimate.unavailable("Scan was too short");
        }

        int n = samples.size();
        double sumT = 0.0;
        double sumV = 0.0;
        for (Sample sample : samples) {
            sumT += sample.seconds - start;
            sumV += sample.value;
        }
        double meanT = sumT / n;
        double meanV = sumV / n;
        double covariance = 0.0;
        double varianceT = 0.0;
        for (Sample sample : samples) {
            double t = sample.seconds - start;
            covariance += (t - meanT) * (sample.value - meanV);
            varianceT += (t - meanT) * (t - meanT);
        }
        double slope = varianceT == 0.0 ? 0.0 : covariance / varianceT;

        double[] detrended = new double[n];
        double variance = 0.0;
        for (int i = 0; i < n; i++) {
            double t = samples.get(i).seconds - start;
            double v = samples.get(i).value - (meanV + slope * (t - meanT));
            detrended[i] = v;
            variance += v * v;
        }
        if (variance / n < 1e-8) {
            return Estimate.unavailable("Signal variation was too small");
        }

        double stepHz = Math.max(0.005, 1.0 / duration);
        double bestHz = 0.0;
        double bestPower = -1.0;
        double totalPower = 0.0;
        List<Double> powers = new ArrayList<>();
        for (double frequency = minHz; frequency <= maxHz + 1e-9; frequency += stepHz) {
            double real = 0.0;
            double imaginary = 0.0;
            for (int i = 0; i < n; i++) {
                double t = samples.get(i).seconds - start;
                double window = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / Math.max(1, n - 1));
                double angle = 2.0 * Math.PI * frequency * t;
                double value = detrended[i] * window;
                real += value * Math.cos(angle);
                imaginary -= value * Math.sin(angle);
            }
            double power = real * real + imaginary * imaginary;
            powers.add(power);
            totalPower += power;
            if (power > bestPower) {
                bestPower = power;
                bestHz = frequency;
            }
        }
        if (powers.isEmpty() || bestPower <= 0.0) {
            return Estimate.unavailable("No periodic signal found");
        }
        if (bestHz * duration < 3.0) {
            return Estimate.unavailable("Too few cycles were observed for a reliable estimate");
        }
        if (bestHz <= minHz + stepHz * 1.25 || bestHz >= maxHz - stepHz * 1.25) {
            return Estimate.unavailable("The strongest signal was at the analysis boundary");
        }
        List<Double> sorted = new ArrayList<>(powers);
        Collections.sort(sorted);
        double median = sorted.get(sorted.size() / 2);
        double ratio = bestPower / Math.max(1e-12, median);
        double peakFraction = bestPower / Math.max(1e-12, totalPower);
        boolean respirationBand = maxHz <= 0.60;
        double requiredRatio = respirationBand ? 18.0 : 12.0;
        double requiredFraction = respirationBand ? 0.30 : 0.18;
        if (ratio < requiredRatio || peakFraction < requiredFraction) {
            return Estimate.unavailable("Camera signal lacked a stable dominant rhythm");
        }
        double ratioQuality = Math.min(1.0, ratio / (requiredRatio * 2.0));
        double concentrationQuality = Math.min(1.0,
                peakFraction / Math.min(0.95, requiredFraction * 2.0));
        double quality = Math.max(0.0, Math.min(1.0,
                0.5 * ratioQuality + 0.5 * concentrationQuality));
        return new Estimate(true, bestHz * 60.0, quality, "");
    }
}
