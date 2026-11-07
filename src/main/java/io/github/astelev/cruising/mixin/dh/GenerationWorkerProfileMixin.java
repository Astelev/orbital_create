package io.github.astelev.cruising.mixin.dh;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.astelev.cruising.compat.dh.profile.GenerationProfile;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class GenerationWorkerProfileMixin {
    @WrapMethod(method="doFill") private ChunkAccess cruising$fill(Blender blender,StructureManager structures,RandomState state,ChunkAccess chunk,int min,int count,Operation<ChunkAccess> original){try(var s=GenerationProfile.worker("noise_fill",chunk)){return original.call(blender,structures,state,chunk,min,count);}}
    @WrapMethod(method="doCreateBiomes") private void cruising$biomes(Blender blender,RandomState state,StructureManager structures,ChunkAccess chunk,Operation<Void> original){try(var s=GenerationProfile.worker("biomes",chunk)){original.call(blender,state,structures,chunk);}}
    @WrapMethod(method="applyCarvers") private void cruising$carvers(net.minecraft.server.level.WorldGenRegion region,long seed,RandomState state,net.minecraft.world.level.biome.BiomeManager biomes,StructureManager structures,ChunkAccess chunk,GenerationStep.Carving step,Operation<Void> original){try(var s=GenerationProfile.worker("carvers",chunk)){original.call(region,seed,state,biomes,structures,chunk,step);}}
}
