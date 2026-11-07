package io.github.astelev.cruising.mixin.dh;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.generation.DhWorldGenerator;
import com.seibel.distanthorizons.core.generation.tasks.DataSourceRetrievalTask;
import com.seibel.distanthorizons.core.level.IDhServerLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import io.github.astelev.cruising.compat.dh.progressive.ProgressiveConfig;
import io.github.astelev.cruising.compat.dh.progressive.ProgressiveWorldBounds;
import io.github.astelev.cruising.compat.dh.progressive.ProgressiveDiagnostics;
import io.github.astelev.cruising.compat.dh.progressive.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;

@Pseudo
@Mixin(targets="com.seibel.distanthorizons.core.generation.queues.WorldGenerationQueue",remap=false)
public abstract class ProgressiveQueueMixin implements ProgressiveQueueAccess {
    @Shadow @Final private IDhApiWorldGenerator generator;
    @Shadow @Final private IDhServerLevel level;
    @Shadow private DhBlockPos2D generationTargetPos;
    @Override public boolean cruising$previewSupported(){return generator instanceof DhWorldGenerator builtin && builtin.roughGenerator!=null && Config.Common.WorldGenerator.generatorPlan.get().surfaceGenEnabled;}
    @Unique private int cruising$width(){var mc=level.getServerLevelWrapper().getWrappedMcObject();if(mc instanceof Level world){return ProgressiveWorldBounds.width(world);}return 0;}
    @Override public boolean cruising$previewAllowed(long pos) {
        var mc=level.getServerLevelWrapper().getWrappedMcObject();
        if(mc instanceof Level world){return ProgressiveWorldBounds.inside(world,pos);}
        return false;
    }
    @Override public boolean cruising$previewNear(long pos,int radius) {
        var p=generationTargetPos;return p==null || ProgressivePriority.sectionDistance(DhSectionPos.getCenterBlockPosX(pos),DhSectionPos.getCenterBlockPosZ(pos),p.x,p.z,cruising$width(),DhSectionPos.getBlockWidth(pos))<=radius;
    }
    @WrapOperation(method="tryStartNextWorldGenTask",at=@At(value="INVOKE",target="Ljava/util/concurrent/ConcurrentHashMap;reduceEntries(JLjava/util/function/Function;Ljava/util/function/BiFunction;)Ljava/lang/Object;"))
    private Object cruising$coarseFirst(ConcurrentHashMap<Long,DataSourceRetrievalTask> map,long threshold,Function<Map.Entry<Long,DataSourceRetrievalTask>,Object> transform,
            BiFunction<Object,Object,Object> reduce,Operation<Object> original,@Local(argsOnly=true) DhBlockPos2D target) {
        if(!ProgressiveConfig.enabled() || !cruising$previewSupported())return original.call(map,threshold,transform,reduce);
        int initial=ProgressiveConfig.INITIAL_DETAIL.getAsInt(),radius=ProgressiveConfig.RADIUS.getAsInt(),width=cruising$width();
        int generationFloor=Math.min(initial,io.github.astelev.cruising.compat.dh.DhFlightState.minimumDetail());
        Function<Map.Entry<Long,DataSourceRetrievalTask>,Object> ranked=entry->{
            int queuedDetail=DhSectionPos.getDetailLevel(entry.getKey())-6;
            if(queuedDetail<generationFloor)return null;
            Object result=transform.apply(entry);if(result instanceof ProgressiveTaskRank pair) {
                long pos=entry.getKey();int detail=DhSectionPos.getDetailLevel(pos)-6;
                boolean near=ProgressivePriority.sectionDistance(DhSectionPos.getCenterBlockPosX(pos),DhSectionPos.getCenterBlockPosZ(pos),target.x,target.z,width,DhSectionPos.getBlockWidth(pos))<=radius;
                pair.cruising$rank(ProgressivePriority.score(pair.cruising$rank(),detail,initial,near));
            }return result;
        };
        ProgressiveDiagnostics.count("server","progressive.queue_rank");return original.call(map,threshold,ranked,reduce);
    }
}
