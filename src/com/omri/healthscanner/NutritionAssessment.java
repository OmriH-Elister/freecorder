package com.omri.healthscanner;

/** Transparent adult BMI and entered-data recap; not a nutrition diagnosis or screen. */
public final class NutritionAssessment {
    private NutritionAssessment() {}

    public static final class Result {
        public final boolean available;
        public final double bmi;
        public final String bodySizeLabel;
        public final String nutritionNote;
        public final String reason;

        private Result(boolean available, double bmi, String bodySizeLabel,
                       String nutritionNote, String reason) {
            this.available = available;
            this.bmi = bmi;
            this.bodySizeLabel = bodySizeLabel;
            this.nutritionNote = nutritionNote;
            this.reason = reason;
        }

        public static Result unavailable(String reason) {
            return new Result(false, Double.NaN, "Not calculated", "No nutrition conclusion", reason);
        }
    }

    public static Result evaluate(double heightCm, double weightKg,
                                  double recentWeightLossPercent, boolean lowIntake) {
        if (!Double.isFinite(heightCm) || !Double.isFinite(weightKg)
                || heightCm < 100.0 || heightCm > 250.0
                || weightKg < 25.0 || weightKg > 350.0
                || !Double.isFinite(recentWeightLossPercent)
                || recentWeightLossPercent < 0.0 || recentWeightLossPercent > 50.0) {
            return Result.unavailable("Enter plausible adult height, weight, and weight-loss values");
        }
        double metres = heightCm / 100.0;
        double bmi = weightKg / (metres * metres);
        return new Result(true, bmi, "BMI calculation only",
                "Entered measurements only; no risk classification or nutrition conclusion is made.", "");
    }
}
