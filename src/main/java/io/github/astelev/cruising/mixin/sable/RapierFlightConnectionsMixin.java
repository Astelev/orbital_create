package io.github.astelev.cruising.mixin.sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import io.github.astelev.cruising.flight.ConnectedFlightAssembly;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo
@Mixin(targets="dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline",remap=false)
public abstract class RapierFlightConnectionsMixin {
    @Shadow @Final private ServerLevel level;
    @Inject(method="addConstraint",at=@At("RETURN"))
    private void cruising$connected(PhysicsPipelineBody first,PhysicsPipelineBody second,PhysicsConstraintConfiguration<?> config,
                                    CallbackInfoReturnable<PhysicsConstraintHandle> cir) {
        ConnectedFlightAssembly.added(level,first,second,cir.getReturnValue());
    }
    @Inject(method="remove(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V",at=@At("HEAD"))
    private void cruising$removeShip(ServerSubLevel ship,CallbackInfo ci){ConnectedFlightAssembly.bodyRemoved(level,ship);}
    @Inject(method="remove(Ldev/ryanhcode/sable/api/sublevel/KinematicContraption;)V",at=@At("TAIL"))
    private void cruising$removeContraption(KinematicContraption body,CallbackInfo ci){ConnectedFlightAssembly.refreshConnections(level);}
    @Inject(method="dispose",at=@At("HEAD"))
    private void cruising$dispose(CallbackInfo ci){ConnectedFlightAssembly.clear(level);}
}
