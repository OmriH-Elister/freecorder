package com.omri.healthscanner;

/** Rejects scans that did not contain a sustained detected face. */
public final class ScanQualityGate {
    private ScanQualityGate() {}

    public static boolean accept(int totalFrames, int faceFrames, double faceSpanSeconds) {
        if (totalFrames < 120 || faceFrames < 120 || faceSpanSeconds < 15.0) return false;
        return faceFrames / (double) totalFrames >= 0.60;
    }
}
