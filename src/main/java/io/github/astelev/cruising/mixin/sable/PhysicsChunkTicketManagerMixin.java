package io.github.astelev.cruising.mixin.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import io.github.astelev.cruising.flight.FlightConfig;
import io.github.astelev.cruising.flight.HighAltitudePolicy;
import io.github.astelev.cruising.flight.ShipGeometry;
import io.github.astelev.cruising.flight.ConnectedFlightAssembly;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.List;
import java.util.function.Predicate;

/**
 * Sable has already decided that a player or a sub-level loading ticket keeps
 * this ship active before invoking inhabitChunk. At sky altitude it needs no
 * terrain collision sections, so its synchronous terrain request is unnecessary.
 * No ChunkHolder, block data, save flags, or player tickets are changed here.
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager", remap = false)
public abstract class PhysicsChunkTicketManagerMixin {
    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;boundingBox()Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;"))
    private BoundingBox3dc cruising$liveSkyTicketBounds(ServerSubLevel ship, Operation<BoundingBox3dc> original) {
        var live = ShipGeometry.currentBounds(ship);
        if (live != null && ship.getLevel() instanceof ServerLevel level
                && HighAltitudePolicy.maySkipTerrainTicket(FlightConfig.ENABLED.getAsBoolean(),
                level.dimension().equals(Level.OVERWORLD), false, ConnectedFlightAssembly.currentMinY(ship),
                level.getMaxBuildHeight(), FlightConfig.MIN_ALTITUDE.getAsInt())) {
            // Cached low-altitude bounds after a rapid ascent must not request
            // terrain collision sections for a ship already above the world.
            return live;
        }
        return original.call(ship);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getPlayers(Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<ServerPlayer> cruising$recognizeLivePassengers(ServerLevel level, Predicate<? super ServerPlayer> predicate,
                                                                Operation<List<ServerPlayer>> original,
                                                                @Local ServerSubLevel ship) {
        if (FlightConfig.ENABLED.getAsBoolean() && level.dimension().equals(Level.OVERWORLD)
                && (ConnectedFlightAssembly.connected(ship) || HighAltitudePolicy.maySkipTerrainTicket(true,true,false,
                    ConnectedFlightAssembly.currentMinY(ship),level.getMaxBuildHeight(),FlightConfig.MIN_ALTITUDE.getAsInt()))) {
            // Keep Sable's cannot-unload-with-players decision intact when its
            // cached AABB lags behind the current ship/passenger pose. Native
            // attachment must also count while the position is between poses. This call
            // is only reached when Sable itself enables that player safeguard.
            return original.call(level, (Predicate<ServerPlayer>) player -> predicate.test(player)
                    || ConnectedFlightAssembly.hasPassenger(ship, player));
        }
        return original.call(level, predicate);
    }

    @WrapMethod(method="update")
    private void cruising$refreshConnections(ServerLevel level,dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container,
            dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem system,dev.ryanhcode.sable.api.physics.PhysicsPipeline pipeline,
            double delta,Operation<Void> original) {
        ConnectedFlightAssembly.beginUpdate(level);
        try {original.call(level,container,system,pipeline,delta);}finally{ConnectedFlightAssembly.endUpdate(level);}
    }

    @WrapOperation(method="update",at=@At(value="INVOKE",target="Ljava/util/Collection;contains(Ljava/lang/Object;)Z"))
    private boolean cruising$inheritNativeForceLoad(java.util.Collection<?> forced,Object candidate,Operation<Boolean> original) {
        if(original.call(forced,candidate))return true;
        return candidate instanceof ServerSubLevel ship && FlightConfig.ENABLED.getAsBoolean()
                && ship.getLevel().dimension().equals(Level.OVERWORLD) && ConnectedFlightAssembly.forceLoaded(ship,forced);
    }

    @Inject(method = "inhabitChunk", at = @At("HEAD"), cancellable = true)
    private void cruising$skipSkyTerrainTicket(ServerLevel level, DistanceManager distanceManager,
                                                UUID subLevelId, long gameTime, long chunkLong,
                                                int x, int z, CallbackInfo ci) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        var ship = container.getSubLevel(subLevelId);
        if (ship == null) {
            return;
        }

        if (HighAltitudePolicy.maySkipTerrainTicket(
                FlightConfig.ENABLED.getAsBoolean(),
                level.dimension().equals(Level.OVERWORLD),
                container.inBounds(x, z),
                ConnectedFlightAssembly.currentMinY(ship),
                level.getMaxBuildHeight(),
                FlightConfig.MIN_ALTITUDE.getAsInt())) {
            io.github.astelev.cruising.flight.FlightHooks.noteShipTicket(true);
            ci.cancel();
        } else {
            if(!container.inBounds(x,z))io.github.astelev.cruising.flight.FlightHooks.noteShipTicket(false);
        }
    }
}
