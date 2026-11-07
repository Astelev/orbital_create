package io.github.astelev.cruising.mixin.dh;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.astelev.cruising.compat.dh.profile.GenerationProfile;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.Coerce;
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.common.wrappers.worldGeneration.DhRoughSurfaceGenerator_neoforge",remap=false)
public abstract class GenerationRoughProfileMixin {
    @WrapMethod(method="generateSurface") private void cruising$rough(int cx,int cz,int x,int z,byte detail,
        com.seibel.distanthorizons.api.objects.data.IDhApiFullDataSource source,
        com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode mode,
        java.util.function.Consumer<?> consumer,Operation<Void> original){try(var s=GenerationProfile.enter("dh.rough_surface.detail_"+detail)){original.call(cx,cz,x,z,detail,source,mode,consumer);}}
}
