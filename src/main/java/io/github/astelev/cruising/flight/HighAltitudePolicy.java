package io.github.astelev.cruising.flight;

/** Pure policy shared by the Sable integration and its boundary tests. */
public final class HighAltitudePolicy {
    // Sable predicts up to 20 blocks vertically and expands collision bounds by one.
    private static final int COLLISION_MARGIN = 21;

    /** Native velocities are blocks/s. A tiny solver tolerance avoids toggling at numerical rest. */
    public static boolean mayCruise(double verticalVelocity) {
        return Double.isFinite(verticalVelocity) && verticalVelocity >= -1.0e-4;
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
