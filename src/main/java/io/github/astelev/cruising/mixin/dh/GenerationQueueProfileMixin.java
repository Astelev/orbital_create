package io.github.astelev.cruising.mixin.dh;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.astelev.cruising.compat.dh.profile.GenerationProfile;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.Coerce;
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.core.generation.queues.WorldGenerationQueue",remap=false)
public abstract class GenerationQueueProfileMixin {
    @WrapMethod(method="submitRetrievalTask") private java.util.concurrent.CompletableFuture<?> cruising$wait(long pos,byte detail,Operation<java.util.concurrent.CompletableFuture<?>> original){
        long start=GenerationProfile.asyncStart();var future=original.call(pos,detail);
        if(start!=0 && future!=null)future.whenComplete((r,e)->GenerationProfile.asyncEnd(detail==0?"async.full_request":"async.rough_request",start));return future;
    }
}
