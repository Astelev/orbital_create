package io.github.astelev.cruising.mixin.dh;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.astelev.cruising.compat.dh.profile.GenerationProfile;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.Coerce;
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.core.dataObjects.transformers.LodDataBuilder",remap=false)
public abstract class GenerationLodProfileMixin {
    @WrapMethod(method="createFromChunk") private static com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2 cruising$lod(
        com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper level,com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper chunk,
        Operation<com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2> original){try(var s=GenerationProfile.enter("dh.chunk_to_lod")){return original.call(level,chunk);}}
}
