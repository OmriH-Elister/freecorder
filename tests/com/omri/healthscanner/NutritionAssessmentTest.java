package com.omri.healthscanner;

public final class NutritionAssessmentTest {
    public static void main(String[] args) {
        calculatesBmiWithoutRiskClassification();
        remainsNeutralAcrossBodySizesAndAnswers();
        rejectsImpossibleMeasurements();
        rejectsNaNWeightLoss();
        System.out.println("NutritionAssessmentTest passed");
    }

    private static void calculatesBmiWithoutRiskClassification() {
        NutritionAssessment.Result result = NutritionAssessment.evaluate(180.0, 75.0, 0.0, false);
        assertTrue(result.available, "valid measurements should be accepted");
        assertNear(23.15, result.bmi, 0.05, "BMI");
        assertEquals("BMI calculation only", result.bodySizeLabel, "neutral label");
        assertContains(result.nutritionNote, "no risk classification", "neutral note");
    }

    private static void remainsNeutralAcrossBodySizesAndAnswers() {
        NutritionAssessment.Result lower = NutritionAssessment.evaluate(175.0, 52.0, 0.0, false);
        NutritionAssessment.Result changed = NutritionAssessment.evaluate(168.0, 68.0, 6.0, true);
        assertEquals("BMI calculation only", lower.bodySizeLabel, "lower BMI label");
        assertEquals(lower.nutritionNote, changed.nutritionNote, "answers must not trigger screening");
        assertTrue(!lower.nutritionNote.toLowerCase().contains("risk" )
                        || lower.nutritionNote.toLowerCase().contains("no risk classification"),
                "note must not classify nutrition risk");
    }

    private static void rejectsImpossibleMeasurements() {
        NutritionAssessment.Result result = NutritionAssessment.evaluate(20.0, 500.0, 0.0, false);
        assertTrue(!result.available, "impossible values should be rejected");
    }

    private static void rejectsNaNWeightLoss() {
        NutritionAssessment.Result result = NutritionAssessment.evaluate(
                175.0, 70.0, Double.NaN, false);
        assertTrue(!result.available, "NaN weight loss should be rejected");
    }

    private static void assertNear(double expected, double actual, double tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertContains(String actual, String fragment, String label) {
        if (actual == null || !actual.toLowerCase().contains(fragment.toLowerCase())) {
            throw new AssertionError(label + ": expected to contain " + fragment + " but got " + actual);
        }
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
