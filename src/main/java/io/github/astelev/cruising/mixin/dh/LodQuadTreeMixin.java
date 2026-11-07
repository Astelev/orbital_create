package io.github.astelev.cruising.mixin.dh;

import io.github.astelev.cruising.compat.dh.DhGenerationPriority;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.QuadTree.LodQuadTree", remap = false)
public abstract class LodQuadTreeMixin {
    @Inject(method = {"loadQueuedSections", "startQueuedRetrievalTasks"}, at = @At("HEAD"))
    private void cruising$snapshotTravel(CallbackInfo ci) {
        DhGenerationPriority.beginSort();
    }

    @Inject(method = {"loadQueuedSections", "startQueuedRetrievalTasks"}, at = @At("RETURN"))
    private void cruising$finishTravelSort(CallbackInfo ci) {
        DhGenerationPriority.endSort();
    }

    @Inject(method = "lambda$startQueuedRetrievalTasks$0", at = @At("RETURN"), cancellable = true)
    private static void cruising$forwardData(@Coerce Object target, Long a, Long b,
            CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(DhGenerationPriority.compare(cir.getReturnValue(), target, a, b));
    }

    @Inject(method = "lambda$loadQueuedSections$0", at = @At("RETURN"), cancellable = true)
    private static void cruising$forwardMeshes(@Coerce Object target,
            @Coerce Object a, @Coerce Object b,
            CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(DhGenerationPriority.compareSections(cir.getReturnValue(), target, a, b));
    }
}
