package com.omri.healthscanner;

public final class ScanQualityGateTest {
    public static void main(String[] args) {
        rejectsScanWithoutFaceCoverage();
        rejectsBriefFaceAppearance();
        acceptsSustainedFaceCoverage();
        System.out.println("ScanQualityGateTest passed");
    }

    private static void rejectsScanWithoutFaceCoverage() {
        assertTrue(!ScanQualityGate.accept(300, 0, 20.0), "no-face scan must be rejected");
    }

    private static void rejectsBriefFaceAppearance() {
        assertTrue(!ScanQualityGate.accept(300, 80, 6.0), "brief face appearance must be rejected");
    }

    private static void acceptsSustainedFaceCoverage() {
        assertTrue(ScanQualityGate.accept(300, 240, 18.0), "sustained face coverage should pass");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
