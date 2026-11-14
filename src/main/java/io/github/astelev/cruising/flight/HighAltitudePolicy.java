package io.github.astelev.cruising.flight;

/** Pure policy shared by the Sable integration and its boundary tests. */
public final class HighAltitudePolicy {
    // Sable predicts up to 20 blocks vertically and expands collision bounds by one.
    private static final int COLLISION_MARGIN = 21;

    /** Native velocities and the descent limit are blocks/s. Exactly at the limit may still cruise. */
    public static boolean mayCruise(double verticalVelocity, double maxDescentSpeed) {
        return Double.isFinite(verticalVelocity) && Double.isFinite(maxDescentSpeed)
                && maxDescentSpeed >= 0 && verticalVelocity >= -maxDescentSpeed;
    }

    private HighAltitudePolicy() {
    }

    public static boolean maySkipTerrainTicket(boolean enabled, boolean overworld,
                                               boolean plotChunk, double hullMinY,
                                               int maxBuildHeight, int threshold) {
        return enabled
                && overworld
                && !plotChunk
                && Double.isFinite(hullMinY)
                && hullMinY > Math.max((double) threshold, (double) maxBuildHeight + COLLISION_MARGIN);
    }
}
