package com.omri.healthscanner;

/** Maps Camera2 active-array face coordinates into an output frame. */
public final class FrameRegionMapper {
    private FrameRegionMapper() {}

    public static int[] map(int sensorLeft, int sensorTop, int sensorRight, int sensorBottom,
                            int cropLeft, int cropTop, int cropRight, int cropBottom,
                            int faceLeft, int faceTop, int faceRight, int faceBottom,
                            int frameWidth, int frameHeight) {
        if (sensorRight <= sensorLeft || sensorBottom <= sensorTop
                || cropRight <= cropLeft || cropBottom <= cropTop
                || faceRight <= faceLeft || faceBottom <= faceTop
                || frameWidth <= 0 || frameHeight <= 0) return null;

        int effectiveLeft = Math.max(sensorLeft, cropLeft);
        int effectiveTop = Math.max(sensorTop, cropTop);
        int effectiveRight = Math.min(sensorRight, cropRight);
        int effectiveBottom = Math.min(sensorBottom, cropBottom);
        if (effectiveRight <= effectiveLeft || effectiveBottom <= effectiveTop) return null;

        double cropAspect = (effectiveRight - effectiveLeft) / (double) (effectiveBottom - effectiveTop);
        double frameAspect = frameWidth / (double) frameHeight;
        if (cropAspect > frameAspect) {
            int wantedWidth = (int) Math.round((effectiveBottom - effectiveTop) * frameAspect);
            int inset = ((effectiveRight - effectiveLeft) - wantedWidth) / 2;
            effectiveLeft += inset;
            effectiveRight = effectiveLeft + wantedWidth;
        } else if (cropAspect < frameAspect) {
            int wantedHeight = (int) Math.round((effectiveRight - effectiveLeft) / frameAspect);
            int inset = ((effectiveBottom - effectiveTop) - wantedHeight) / 2;
            effectiveTop += inset;
            effectiveBottom = effectiveTop + wantedHeight;
        }

        int clippedLeft = Math.max(faceLeft, effectiveLeft);
        int clippedTop = Math.max(faceTop, effectiveTop);
        int clippedRight = Math.min(faceRight, effectiveRight);
        int clippedBottom = Math.min(faceBottom, effectiveBottom);
        if (clippedRight <= clippedLeft || clippedBottom <= clippedTop) return null;

        int left = clamp((int) Math.round((clippedLeft - effectiveLeft)
                * frameWidth / (double) (effectiveRight - effectiveLeft)), 0, frameWidth - 1);
        int top = clamp((int) Math.round((clippedTop - effectiveTop)
                * frameHeight / (double) (effectiveBottom - effectiveTop)), 0, frameHeight - 1);
        int right = clamp((int) Math.round((clippedRight - effectiveLeft)
                * frameWidth / (double) (effectiveRight - effectiveLeft)), left + 1, frameWidth);
        int bottom = clamp((int) Math.round((clippedBottom - effectiveTop)
                * frameHeight / (double) (effectiveBottom - effectiveTop)), top + 1, frameHeight);
        return new int[]{left, top, right, bottom};
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
