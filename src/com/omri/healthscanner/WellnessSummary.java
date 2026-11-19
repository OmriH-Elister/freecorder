package com.omri.healthscanner;

import java.util.Locale;

/** Builds a concise research summary without producing a health score or diagnosis. */
public final class WellnessSummary {
    private WellnessSummary() {}

    public static final String ANEMIA_NOTE =
            "Anemia is not assessed. This app does not analyze sclera, skin, or other "
                    + "appearance for anemia; clinical assessment and a hemoglobin test are required.";

    public static final class Result {
        public final boolean scoreAvailable;
        public final int score;
        public final String summary;
        public final String anemiaNote;

        private Result(String summary, String anemiaNote) {
            this.scoreAvailable = false;
            this.score = 0;
            this.summary = summary;
            this.anemiaNote = anemiaNote;
        }
    }

    public static Result compose(SignalAnalyzer.Estimate pulse,
                                 SignalAnalyzer.Estimate respiration,
                                 NutritionAssessment.Result nutrition) {
        String nutritionText = nutrition != null && nutrition.available
                ? String.format(Locale.US,
                    "Entered-data recap: BMI %.1f. No risk classification or nutrition conclusion is made.",
                    nutrition.bmi)
                : "Body measurements are incomplete; no entered-data recap is shown.";
        String summary = "Camera capture may be used to study periodic colour patterns, but pulse and "
                + "breathing numbers are not displayed because this method is not clinically validated "
                + "and can be confounded by motion, lighting, exposure, and region-selection errors. "
                + nutritionText;
        return new Result(summary, ANEMIA_NOTE);
    }

    static SignalAnalyzer.Estimate testEstimate(double rate, double quality) {
        return SignalAnalyzer.Estimate.available(rate, quality);
    }
}
