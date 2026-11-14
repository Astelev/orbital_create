package io.github.astelev.cruising.mixin.dh;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.astelev.cruising.compat.dh.profile.GenerationProfile;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.Coerce;
@Pseudo
@Mixin(targets={"com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepStructureStart_neoforge","com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepStructureReference_neoforge","com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepBiomes_neoforge","com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepNoise_neoforge","com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepSurface_neoforge","com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepFeatures_neoforge"},remap=false)
public abstract class GenerationStepsProfileMixin {
    @WrapMethod(method="generateGroup") private void cruising$profileStep(@Coerce Object params,@Coerce Object region,@Coerce Object chunks,Operation<Void> original){
        if(!GenerationProfile.enabled()){original.call(params,region,chunks);return;}
        java.util.ArrayList<net.minecraft.world.level.chunk.ChunkAccess> marked=new java.util.ArrayList<>();
        if(chunks instanceof Iterable<?> list)for(Object entry:list)if(entry instanceof com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper_neoforge wrapper){var chunk=wrapper.getChunk();marked.add(chunk);GenerationProfile.markChunk(chunk,true);}
        try(var scope=GenerationProfile.enter("dh.step."+getClass().getSimpleName())){original.call(params,region,chunks);}finally {marked.forEach(chunk->GenerationProfile.markChunk(chunk,false));}
    }
}
