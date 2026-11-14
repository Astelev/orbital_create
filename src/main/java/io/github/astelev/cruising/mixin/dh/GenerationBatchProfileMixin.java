package io.github.astelev.cruising.mixin.dh;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.astelev.cruising.compat.dh.profile.GenerationProfile;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.Coerce;
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.common.wrappers.worldGeneration.DhChunkGenerator_neoforge",remap=false)
public abstract class GenerationBatchProfileMixin {
    @WrapMethod(method="generateChunks") private void cruising$profileBatch(@Coerce Object event,Operation<Void> original){try(var s=GenerationProfile.enter("dh.batch")){original.call(event);}}
    @WrapMethod(method="generateDirect") private void cruising$profileDirect(@Coerce Object event,@Coerce Object chunks,@Coerce Object region,Operation<Void> original){try(var s=GenerationProfile.enter("dh.generate_direct")){original.call(event,chunks,region);}}
}
