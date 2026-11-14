package io.github.astelev.cruising.mixin.sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import io.github.astelev.cruising.flight.FlightHooks;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Observe actual native removal on both sides; never cancel removal or alter the save path. */
@Pseudo
@Mixin(targets="dev.ryanhcode.sable.api.sublevel.SubLevelContainer",remap=false)
public abstract class SubLevelRemovalDiagnosticMixin {
    @Inject(method="removeSubLevel(Ldev/ryanhcode/sable/sublevel/SubLevel;Ldev/ryanhcode/sable/sublevel/storage/SubLevelRemovalReason;)V",at=@At("HEAD"))
    private void cruising$removed(SubLevel ship,SubLevelRemovalReason reason,CallbackInfo ci){
        FlightHooks.noteNativeRemoval(ship.getLevel(),ship.getUniqueId(),ship.logicalPose().position().y,reason.toString());
    }
}
