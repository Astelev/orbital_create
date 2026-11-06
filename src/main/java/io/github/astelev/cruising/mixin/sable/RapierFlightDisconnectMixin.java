package io.github.astelev.cruising.mixin.sable;
import io.github.astelev.cruising.flight.ConnectedFlightAssembly;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle",remap=false)
public abstract class RapierFlightDisconnectMixin {
    @Inject(method="remove",at=@At("RETURN"))
    private void cruising$disconnected(CallbackInfo ci){ConnectedFlightAssembly.removed(this);}
}
