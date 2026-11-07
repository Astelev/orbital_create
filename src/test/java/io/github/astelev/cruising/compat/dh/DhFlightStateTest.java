package io.github.astelev.cruising.compat.dh;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DhFlightStateTest {
    @AfterEach
    void clear() {
        DhFlightState.publish(false, 0);
    }

    @Test
    void flightNeverDowngradesAvailableTerrainDetail() {
        DhFlightState.publish(true, 4);
        assertEquals(4, DhFlightState.minimumDetail());
        assertEquals(0, DhFlightState.applyDetailFloor((byte) 0));
        assertEquals(6, DhFlightState.applyDetailFloor((byte) 6));
        assertTrue(DhFlightState.bypassMotionPause());
        DhFlightState.publish(false, 0);
        assertEquals(0, DhFlightState.applyDetailFloor((byte) 0));
        assertFalse(DhFlightState.bypassMotionPause());
        assertEquals(0,DhFlightState.minimumDetail());
    }

    @Test
    void distinguishesGenerationPermissionFromBackpressureAndClearsOnExit() {
        DhFlightState.publish(true, 4);
        DhFlightState.recordGate(true, false);
        assertEquals(DhFlightState.Reason.ALLOWED, DhFlightState.recentGate());
        DhFlightState.recordGate(false, true);
        assertEquals(DhFlightState.Reason.CHUNK_BACKLOG, DhFlightState.recentGate());
        DhFlightState.recordGate(false, false);
        assertEquals(DhFlightState.Reason.SERVER_LOAD, DhFlightState.recentGate());
        DhFlightState.publish(false, 0);
        assertEquals(DhFlightState.Reason.UNKNOWN, DhFlightState.recentGate());
    }
}
