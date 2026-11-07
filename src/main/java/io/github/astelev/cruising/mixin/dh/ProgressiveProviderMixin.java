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
    @Override public String cruising$progressiveDiagnostic(){var s=cruising$progressive;return s==null?"off preview="+cruising$coverage.pending():s.pending()+"/"+s.stages()+" preview="+cruising$coverage.pending();}
    @Unique private final java.util.concurrent.atomic.AtomicBoolean cruising$previewScheduled=new java.util.concurrent.atomic.AtomicBoolean();
    @Unique private volatile long cruising$nextPreview;
    @Unique private final PreviewCoverageLane cruising$coverage=new PreviewCoverageLane();
    @Unique private final ThreadLocal<Boolean> cruising$previewPass=ThreadLocal.withInitial(()->false);
    @Unique private final Set<Long> cruising$direct=ConcurrentHashMap.newKeySet();
    @Unique private boolean cruising$needed(long pos){var s=cruising$progressive;return cruising$direct.contains(pos) || s!=null && s.needs(pos);}
    @Unique private CompletableFuture<DataSourceRetrievalResult> cruising$directRequest(Long pos,Operation<CompletableFuture<DataSourceRetrievalResult>> original){
        cruising$direct.add(pos);try{var f=original.call(pos);if(f==null)cruising$direct.remove(pos);else f.whenComplete((r,e)->cruising$direct.remove(pos));return f;}catch(RuntimeException e){cruising$direct.remove(pos);throw e;}
    }
    @Unique private void cruising$cancelOrphan(long pos){var q=worldGenQueueRef.get();if(q!=null)q.removeRetrievalRequestIf(p->p==pos && !cruising$needed(p) && !cruising$coverage.owns(p));}
    @Shadow @Final public AtomicReference<IFullDataSourceRetrievalQueue> worldGenQueueRef;
    @Unique private volatile ProgressiveRequests<DataSourceRetrievalResult> cruising$progressive;
    @Unique private boolean cruising$enabled(){return ProgressiveConfig.enabled()
            && worldGenQueueRef.get() instanceof ProgressiveQueueAccess queue && queue.cruising$previewSupported();}
    @WrapMethod(method="queuePositionForRetrieval")
    private CompletableFuture<DataSourceRetrievalResult> cruising$stages(Long pos,Operation<CompletableFuture<DataSourceRetrievalResult>> original) {
        if(cruising$previewPass.get())return original.call(pos);
        int detail=DhSectionPos.getDetailLevel(pos);
        if(!ProgressiveConfig.enabled() || detail<6
                || detail>=6+ProgressiveConfig.INITIAL_DETAIL.getAsInt()
                || !(worldGenQueueRef.get() instanceof ProgressiveQueueAccess queue)
                || !queue.cruising$previewSupported()
                || !queue.cruising$previewNear(pos,ProgressiveConfig.RADIUS.getAsInt()))return cruising$directRequest(pos,original);
        var path=new ArrayList<Long>();int largest=6+ProgressiveConfig.INITIAL_DETAIL.getAsInt();
        for(int d=largest;d>detail;d--) {
            long parent=DhSectionPos.convertToDetailLevel(pos,(byte)d);
            if(queue.cruising$previewAllowed(parent))path.add(parent);
        }
        if(path.isEmpty())return cruising$directRequest(pos,original);
        var state=cruising$progressive;
        if(state==null)synchronized(this) {
            state=cruising$progressive;
            if(state==null)cruising$progressive=state=io.github.astelev.cruising.compat.dh.progressive.DhProgressiveBackend.create(
                    (GeneratedFullDataSourceProvider)(Object)this,stage->original.call(stage),this::cruising$cancelOrphan,ProgressiveConfig.PENDING.getAsInt());
        }
        ProgressiveDiagnostics.count("server","progressive.logical_request");
        var result=state.request(pos,path);
        // Capacity is observable and admission is bounded; native caller retries rather than bypassing preview.
        return result==null?CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("Progressive backlog capacity")):result;
    }
    @Inject(method="onWorldGenTaskComplete",at=@At("RETURN"))
    private void cruising$published(Long pos,DataSourceRetrievalResult result,Throwable error,CallbackInfo ci) {
        boolean accepted=error==null && result!=null && result.state==ERetrievalResultState.SUCCESS;
        if(accepted)cruising$coverage.published(pos);
        var state=cruising$progressive;if(state==null)return;
        if(error==null && result!=null && result.state==ERetrievalResultState.REQUIRES_SPLITTING)ProgressiveDiagnostics.count("server","progressive.stage_split");
        boolean success=error==null && result!=null && result.state==ERetrievalResultState.SUCCESS;
        state.published(pos,success?null:error!=null?error:new IllegalStateException("Preview was not published"));
        if(success)ProgressiveDiagnostics.count("server","progressive.published."+(DhSectionPos.getDetailLevel(pos)-6));
    }
    @Inject(method={"canQueueRetrievalNow()Z","canQueueRetrievalNow(Z)Z"},at=@At("RETURN"),cancellable=true)
    private void cruising$admission(CallbackInfoReturnable<Boolean> cir){
        if(cruising$previewPass.get() || !cruising$enabled())return;
        var q=(ProgressiveQueueAccess)worldGenQueueRef.get();var s=cruising$progressive;
        boolean saturated=s!=null && !s.canAccept(),cover=saturated || !cir.getReturnValueZ();
        long now=System.nanoTime();
        if((cover || cruising$coverage.pending()>0) && now>=cruising$nextPreview && cruising$previewScheduled.compareAndSet(false,true)) {
            cruising$nextPreview=now+250_000_000L;
            try{q.cruising$previewExecute(()->{
                cruising$previewPass.set(true);
                try{if(worldGenQueueRef.get()==q && cruising$enabled())cruising$coverage.tick((GeneratedFullDataSourceProvider)(Object)this,q,this::cruising$needed,cover);}
                catch(RuntimeException error){ProgressiveDiagnostics.count("server","preview.error");}
                finally{cruising$previewPass.remove();cruising$previewScheduled.set(false);}
            });}catch(java.util.concurrent.RejectedExecutionException ignored){cruising$previewScheduled.set(false);}
        }
        if(saturated)cir.setReturnValue(false);
    }
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="canQueueRetrievalNow(Z)Z",at=@At(value="INVOKE",target="Lcom/seibel/distanthorizons/core/generation/queues/IFullDataSourceRetrievalQueue;getWaitingTaskCount()I"))
    private int cruising$previewSlots(IFullDataSourceRetrievalQueue queue,Operation<Integer> original){
        return cruising$previewPass.get() && queue instanceof ProgressiveQueueAccess q?q.cruising$previewWaiting():original.call(queue);
    }
    @Inject(method="removeRetrievalRequestIf",at=@At("HEAD"))
    private void cruising$cancel(DhSectionPos.IPrimitiveLongConsumer predicate,CallbackInfo ci){var s=cruising$progressive;if(s!=null)s.cancelIf(predicate::accept);}
    @Inject(method={"close","clearRetrievalQueue"},at=@At("HEAD"))
    private void cruising$close(CallbackInfo ci){var s=cruising$progressive;cruising$progressive=null;if(s!=null)s.close();}
}
