package io.github.astelev.cruising.mixin.dh;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.astelev.cruising.compat.dh.profile.GenerationProfile;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.Coerce;
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.common.wrappers.worldGeneration.chunkFileHandling.ChunkFileReader_neoforge",remap=false)
public abstract class GenerationDiskProfileMixin {
    @WrapMethod(method="getChunkNbtDataAsync") private java.util.concurrent.CompletableFuture<?> cruising$disk(@Coerce Object pos,Operation<java.util.concurrent.CompletableFuture<?>> original){
        long start=GenerationProfile.asyncStart();var future=original.call(pos);if(start!=0 && future!=null)future.whenComplete((r,e)->GenerationProfile.asyncEnd("async.disk_request",start));return future;
    }
}
