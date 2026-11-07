package io.github.astelev.cruising.mixin.dh;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import com.seibel.distanthorizons.core.generation.queues.IFullDataSourceRetrievalQueue;
import com.seibel.distanthorizons.core.generation.tasks.*;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import io.github.astelev.cruising.compat.dh.progressive.ProgressiveConfig;
import io.github.astelev.cruising.compat.dh.progressive.ProgressiveDiagnostics;
import io.github.astelev.cruising.compat.dh.progressive.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Pseudo
@Mixin(targets="com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider",remap=false)
public abstract class ProgressiveProviderMixin implements ProgressiveProviderAccess {
    @Override public String cruising$progressiveDiagnostic(){var s=cruising$progressive;return s==null?"off":s.pending()+"/"+s.stages();}
    @Shadow @Final public AtomicReference<IFullDataSourceRetrievalQueue> worldGenQueueRef;
    @Unique private volatile ProgressiveRequests<DataSourceRetrievalResult> cruising$progressive;
    @Unique private boolean cruising$enabled(){return ProgressiveConfig.enabled()
            && worldGenQueueRef.get() instanceof ProgressiveQueueAccess queue && queue.cruising$previewSupported();}
    @WrapMethod(method="queuePositionForRetrieval")
    private CompletableFuture<DataSourceRetrievalResult> cruising$stages(Long pos,Operation<CompletableFuture<DataSourceRetrievalResult>> original) {
        int detail=DhSectionPos.getDetailLevel(pos);
        if(!ProgressiveConfig.enabled() || detail<6
                || detail>=6+ProgressiveConfig.INITIAL_DETAIL.getAsInt()
                || !(worldGenQueueRef.get() instanceof ProgressiveQueueAccess queue)
                || !queue.cruising$previewSupported()
                || !queue.cruising$previewNear(pos,ProgressiveConfig.RADIUS.getAsInt()))return original.call(pos);
        var path=new ArrayList<Long>();int largest=6+ProgressiveConfig.INITIAL_DETAIL.getAsInt();
        for(int d=largest;d>detail;d--) {
            long parent=DhSectionPos.convertToDetailLevel(pos,(byte)d);
            if(queue.cruising$previewAllowed(parent))path.add(parent);
        }
        if(path.isEmpty())return original.call(pos);
        var state=cruising$progressive;
        if(state==null)synchronized(this) {
            state=cruising$progressive;
            if(state==null)cruising$progressive=state=io.github.astelev.cruising.compat.dh.progressive.DhProgressiveBackend.create(
                    (GeneratedFullDataSourceProvider)(Object)this,stage->original.call(stage),ProgressiveConfig.PENDING.getAsInt());
        }
        ProgressiveDiagnostics.count("server","progressive.logical_request");
        var result=state.request(pos,path);
        // Capacity is observable and admission is bounded; native caller retries rather than bypassing preview.
        return result==null?CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("Progressive backlog capacity")):result;
    }
    @Inject(method="onWorldGenTaskComplete",at=@At("RETURN"))
    private void cruising$published(Long pos,DataSourceRetrievalResult result,Throwable error,CallbackInfo ci) {
        var state=cruising$progressive;if(state==null)return;
        if(error==null && result!=null && result.state==ERetrievalResultState.REQUIRES_SPLITTING)ProgressiveDiagnostics.count("server","progressive.stage_split");
        boolean success=error==null && result!=null && result.state==ERetrievalResultState.SUCCESS;
        state.published(pos,success?null:error!=null?error:new IllegalStateException("Preview was not published"));
        if(success)ProgressiveDiagnostics.count("server","progressive.published."+(DhSectionPos.getDetailLevel(pos)-6));
    }
    @Inject(method={"canQueueRetrievalNow()Z","canQueueRetrievalNow(Z)Z"},at=@At("RETURN"),cancellable=true)
    private void cruising$admission(CallbackInfoReturnable<Boolean> cir){var s=cruising$progressive;if(s!=null && cruising$enabled() && !s.canAccept())cir.setReturnValue(false);}
    @Inject(method="removeRetrievalRequestIf",at=@At("HEAD"))
    private void cruising$cancel(DhSectionPos.IPrimitiveLongConsumer predicate,CallbackInfo ci){var s=cruising$progressive;if(s!=null)s.cancelIf(predicate::accept);}
    @Inject(method={"close","clearRetrievalQueue"},at=@At("HEAD"))
    private void cruising$close(CallbackInfo ci){var s=cruising$progressive;cruising$progressive=null;if(s!=null)s.close();}
}
