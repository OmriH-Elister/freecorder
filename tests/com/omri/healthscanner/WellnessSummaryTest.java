package com.omri.healthscanner;

public final class WellnessSummaryTest {
    public static void main(String[] args) {
        neverProducesCompositeHealthScore();
        withholdsUnvalidatedPhysiologicalNumbers();
        handlesPartialResultsWithoutContradiction();
        alwaysStatesAppearanceIsNotAnalyzedForAnemia();
        System.out.println("WellnessSummaryTest passed");
    }

    private static void neverProducesCompositeHealthScore() {
        WellnessSummary.Result result = WellnessSummary.compose(
                available(72.0, 0.95), available(15.0, 0.95),
                NutritionAssessment.evaluate(180.0, 75.0, 0.0, false));
        assertTrue(!result.scoreAvailable, "unvalidated composite score must never be shown");
    }

    private static void withholdsUnvalidatedPhysiologicalNumbers() {
        WellnessSummary.Result result = WellnessSummary.compose(
                available(72.0, 0.95), available(15.0, 0.95),
                NutritionAssessment.Result.unavailable("Not entered"));
        assertTrue(!result.summary.contains("72"), "pulse number must be withheld");
        assertTrue(!result.summary.contains("15"), "breathing number must be withheld");
        assertContains(result.summary, "not displayed", "withholding explanation");
        assertContains(result.summary, "not clinically validated", "validation limitation");
    }

    private static void handlesPartialResultsWithoutContradiction() {
        WellnessSummary.Result result = WellnessSummary.compose(
                available(72.0, 0.95),
                SignalAnalyzer.Estimate.unavailable("No stable breathing pattern"),
                NutritionAssessment.Result.unavailable("Not entered"));
        assertTrue(!result.summary.toLowerCase().contains("no camera estimate is shown"),
                "partial internal results must not produce contradictory wording");
        assertContains(result.summary, "not displayed", "consistent public behavior");
    }

    private static void alwaysStatesAppearanceIsNotAnalyzedForAnemia() {
        WellnessSummary.Result result = WellnessSummary.compose(
                available(72.0, 0.95), available(15.0, 0.95),
                NutritionAssessment.evaluate(180.0, 75.0, 0.0, false));
        String note = result.anemiaNote.toLowerCase();
        assertContains(note, "does not analyze", "appearance limitation");
        assertContains(note, "not assessed", "anemia limitation");
    }

    private static SignalAnalyzer.Estimate available(double bpm, double quality) {
        return WellnessSummary.testEstimate(bpm, quality);
    }

    private static void assertContains(String actual, String fragment, String label) {
        if (actual == null || !actual.toLowerCase().contains(fragment.toLowerCase())) {
            throw new AssertionError(label + ": expected to contain " + fragment + " but got " + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
