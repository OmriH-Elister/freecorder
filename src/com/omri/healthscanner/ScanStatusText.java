package com.omri.healthscanner;

/** User-visible camera status wording kept separate for deterministic safety tests. */
public final class ScanStatusText {
    private ScanStatusText() {}

    public static String initial() {
        return "Camera not checked • tap start to test availability";
    }

    public static String complete(boolean pulsePatternAccepted, boolean breathingPatternAccepted) {
        if (!pulsePatternAccepted && !breathingPatternAccepted) {
            return "Capture ended • quality checks did not accept the camera trace";
        }
        return "Capture ended • unvalidated research analysis ran; physiological numbers are not displayed";
    }
}
