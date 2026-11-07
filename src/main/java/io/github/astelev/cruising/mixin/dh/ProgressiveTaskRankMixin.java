package io.github.astelev.cruising.mixin.dh;
import io.github.astelev.cruising.compat.dh.progressive.ProgressiveTaskRank;
import org.spongepowered.asm.mixin.*;
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.core.generation.queues.WorldGenerationQueue$TaskDistancePair",remap=false)
public abstract class ProgressiveTaskRankMixin implements ProgressiveTaskRank {
    @Shadow @Final @Mutable public int dist;
    @Override public int cruising$rank(){return dist;}
    @Override public void cruising$rank(int value){dist=value;}
}
