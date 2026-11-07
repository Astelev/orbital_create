package io.github.astelev.cruising.mixin.dh;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.astelev.cruising.compat.dh.profile.GenerationProfile;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.Coerce;
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.core.generation.DhLightingEngine",remap=false)
public abstract class GenerationLightProfileMixin {
    @WrapMethod(method="lightChunk(Lcom/seibel/distanthorizons/core/wrapperInterfaces/chunk/IChunkWrapper;Ljava/util/ArrayList;I)V")
    private void cruising$light(com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper chunk,java.util.ArrayList<?> adjacent,int sky,Operation<Void> original){try(var s=GenerationProfile.enter("dh.lighting")){original.call(chunk,adjacent,sky);}}
    @WrapMethod(method="bakeChunkBlockLighting")
    private void cruising$block(com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper chunk,java.util.ArrayList<?> adjacent,int sky,Operation<Void> original){try(var s=GenerationProfile.enter("dh.block_lighting")){original.call(chunk,adjacent,sky);}}
}
