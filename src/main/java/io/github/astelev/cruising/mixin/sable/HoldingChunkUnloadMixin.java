package io.github.astelev.cruising.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import io.github.astelev.cruising.flight.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Collection;

/** Chunk retirement is separate from the physics ticket manager's player safeguard. */
@Pseudo
@Mixin(targets="dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap",remap=false)
public abstract class HoldingChunkUnloadMixin {
    @WrapOperation(method="processUnload",at=@At(value="INVOKE",target="Ljava/util/Collection;contains(Ljava/lang/Object;)Z"))
    private boolean cruising$keepOccupiedAssembly(Collection<?> forced,Object candidate,Operation<Boolean> original) {
        if(original.call(forced,candidate))return true;
        boolean occupied=candidate instanceof ServerSubLevel ship && ConnectedFlightAssembly.protectOccupied(ship);
        if(occupied)FlightHooks.noteHoldingProtection();
        return occupied;
    }
}
