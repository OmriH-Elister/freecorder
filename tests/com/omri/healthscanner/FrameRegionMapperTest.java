package com.omri.healthscanner;

public final class FrameRegionMapperTest {
    public static void main(String[] args) {
        mapsFaceThroughCenteredAspectCrop();
        mapsFaceThroughDigitalCrop();
        rejectsFaceOutsideEffectiveCrop();
        System.out.println("FrameRegionMapperTest passed");
    }

    private static void mapsFaceThroughCenteredAspectCrop() {
        int[] mapped = FrameRegionMapper.map(
                0, 0, 4000, 3000,
                0, 0, 4000, 3000,
                1000, 750, 3000, 2250,
                1920, 1080);
        assertRect(mapped, 480, 180, 1440, 900, "16:9 stream mapping");
    }

    private static void mapsFaceThroughDigitalCrop() {
        int[] mapped = FrameRegionMapper.map(
                0, 0, 4000, 3000,
                1000, 750, 3000, 2250,
                1500, 1000, 2500, 2000,
                1000, 750);
        assertRect(mapped, 250, 125, 750, 625, "digital crop mapping");
    }

    private static void rejectsFaceOutsideEffectiveCrop() {
        int[] mapped = FrameRegionMapper.map(
                0, 0, 4000, 3000,
                1000, 750, 3000, 2250,
                0, 0, 500, 500,
                1000, 750);
        if (mapped != null) throw new AssertionError("outside face must be rejected");
    }

    private static void assertRect(int[] actual, int left, int top, int right, int bottom,
                                   String label) {
        if (actual == null || actual.length != 4 || actual[0] != left || actual[1] != top
                || actual[2] != right || actual[3] != bottom) {
            throw new AssertionError(label + ": got " + java.util.Arrays.toString(actual));
        }
    }
}
