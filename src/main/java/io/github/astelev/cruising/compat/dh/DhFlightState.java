package io.github.astelev.cruising.compat.dh;

/** Immutable snapshots shared between the client and DH's background threads. */
public final class DhFlightState {
    private static volatile FlightPathPriority.Motion motion = FlightPathPriority.Motion.STILL;
    public static void travelVelocity(double x, double z) { motion = FlightPathPriority.velocity(x, z); }
    public static FlightPathPriority.Motion travelMotion() { return motion; }

    private static volatile Mode mode = new Mode(false, 0);
    private static volatile Gate gate = new Gate(Reason.UNKNOWN, 0);

    private DhFlightState() {
    }

    public static void publish(boolean active, int minimumDetail) {
        mode = new Mode(active, minimumDetail);
        if (!active) {
            motion = FlightPathPriority.Motion.STILL;
            gate = new Gate(Reason.UNKNOWN, 0);
        }
    }

    public static boolean bypassMotionPause() {
        return mode.active;
    }

    public static byte applyDetailFloor(byte original) {
        return original; // Generation preview must never reduce available render detail.
    }

    public static int minimumDetail() {
        return mode.minimumDetail;
    }

    public static void recordGate(boolean allowed, boolean chunkBuilderOverloaded) {
        if (mode.active) {
            gate = new Gate(allowed ? Reason.ALLOWED : chunkBuilderOverloaded ? Reason.CHUNK_BACKLOG : Reason.SERVER_LOAD,
                    System.nanoTime());
        }
    }

    public static Reason recentGate() {
        var snapshot = gate;
        return System.nanoTime() - snapshot.checkedAtNanos < 2_000_000_000L ? snapshot.reason : Reason.UNKNOWN;
    }

    public enum Reason { UNKNOWN, ALLOWED, CHUNK_BACKLOG, SERVER_LOAD }
    private record Mode(boolean active, int minimumDetail) {}
    private record Gate(Reason reason, long checkedAtNanos) {}
}
