package com.omri.healthscanner;

public final class ScanStatusTextTest {
    public static void main(String[] args) {
        initialStateDoesNotClaimCameraReadiness();
        rejectedCaptureDoesNotImplyMeasurementsWereObtained();
        acceptedInternalPatternStillWithholdsNumbers();
        System.out.println("ScanStatusTextTest passed");
    }

    private static void initialStateDoesNotClaimCameraReadiness() {
        String text = ScanStatusText.initial().toLowerCase();
        assertContains(text, "not checked", "initial state");
        assertTrue(!text.contains("ready"), "initial state must not claim readiness");
    }

    private static void rejectedCaptureDoesNotImplyMeasurementsWereObtained() {
        String text = ScanStatusText.complete(false, false).toLowerCase();
        assertContains(text, "quality checks did not accept", "rejected capture");
        assertTrue(!text.contains("withheld"), "rejected capture must not imply a hidden measurement");
    }

    private static void acceptedInternalPatternStillWithholdsNumbers() {
        String text = ScanStatusText.complete(true, true).toLowerCase();
        assertContains(text, "numbers are not displayed", "accepted internal pattern");
        assertContains(text, "unvalidated", "validation warning");
    }

    private static void assertContains(String actual, String fragment, String label) {
        if (!actual.contains(fragment)) {
            throw new AssertionError(label + ": expected to contain " + fragment + " but got " + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
