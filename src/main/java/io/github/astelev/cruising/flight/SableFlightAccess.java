package io.github.astelev.cruising.flight;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Loaded only when Sable is installed. */
final class SableFlightAccess {
    private SableFlightAccess() {
    }

    static boolean isHighShipEntity(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)
                || entity.getY() <= FlightConfig.MIN_ALTITUDE.getAsInt()) {
            return false;
        }
        SubLevel ship = Sable.HELPER.getTrackingSubLevel(entity);
        var vehicle = entity.getVehicle();
        for (int depth = 0; ship == null && vehicle != null && depth < 16; depth++) {
            ship = Sable.HELPER.getTrackingSubLevel(vehicle);
            vehicle = vehicle.getVehicle();
        }
        var container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return false;
        }
        if (ship == null && entity instanceof EntityMovementExtension movement) {
            var lastId = movement.sable$getLastTrackingSubLevelID();
            if (lastId != null) {
                var previous = container.getSubLevel(lastId);
                if (previous != null && !previous.isRemoved() && ShipGeometry.isPassenger(previous, entity)) {
                    ship = previous;
                }
            }
        }
        // Also cover passengers standing/jumping inside a cabin, as Sable's
        // own player-inhabited test does, without granting free-flight mode.
        if (ship == null) {
            for (var candidate : container.getAllSubLevels()) {
                if (!candidate.isRemoved() && ShipGeometry.isPassenger(candidate, entity)) {
                    ship = candidate;
                    break;
                }
            }
        }
        return ship != null && !ship.isRemoved() && HighAltitudePolicy.maySkipTerrainTicket(
                FlightConfig.ENABLED.getAsBoolean(), level.dimension().equals(Level.OVERWORLD), false,
                ConnectedFlightAssembly.currentMinY(ship), level.getMaxBuildHeight(), FlightConfig.MIN_ALTITUDE.getAsInt());
    }
}
