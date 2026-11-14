package io.github.astelev.cruising.compat.dh.progressive;

import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import com.seibel.distanthorizons.core.generation.tasks.DataSourceRetrievalResult;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongPredicate;

/** Independent bounded coarse coverage. Uses the same native queue, workers and publication. */
public final class PreviewCoverageLane {
    private static final int LIMIT=8,CHECKS=4;
    private final Map<Long,CompletableFuture<DataSourceRetrievalResult>> pending=new HashMap<>();
    private final LinkedHashSet<Long> published=new LinkedHashSet<>();
    public synchronized void published(long pos){if(DhSectionPos.getDetailLevel(pos)<=6)return;published.add(pos);while(published.size()>512)published.remove(published.iterator().next());}
    private List<Long> candidates=List.of();private int cursor;private long next;
    private ProgressiveQueueAccess.Focus focus;private int detail,radius;
    public synchronized boolean owns(long pos){return pending.containsKey(pos);}
    public synchronized int pending(){return pending.size();}
    public void tick(GeneratedFullDataSourceProvider provider,ProgressiveQueueAccess queue,LongPredicate needed,boolean admit) {
        long now=System.nanoTime();synchronized(this){if(now<next)return;next=now+250_000_000L;}
        var current=queue.cruising$previewFocus();if(current==null)return;
        int d=6+ProgressivePriority.initialDetail(ProgressiveConfig.INITIAL_DETAIL.getAsInt(),current.period()),r=Math.min(ProgressiveConfig.RADIUS.getAsInt(),current.viewRadius()),width=1<<d;
        List<Long> obsolete;
        synchronized(this){obsolete=pending.keySet().stream().filter(p->distance(p,current)>(current.viewRadius()==Integer.MAX_VALUE?r:current.viewRadius())+(long)DhSectionPos.getBlockWidth(p) && !needed.test(p)).toList();}
        var nativeQueue=provider.worldGenQueueRef.get();
        if(nativeQueue!=null && !obsolete.isEmpty())nativeQueue.removeRetrievalRequestIf(p->obsolete.contains(p) && !needed.test(p));
        if(!admit)return;
        synchronized(this){
            if(focus==null || Math.floorDiv(focus.x(),width)!=Math.floorDiv(current.x(),width) || Math.floorDiv(focus.z(),width)!=Math.floorDiv(current.z(),width) || focus.period()!=current.period() || focus.viewRadius()!=current.viewRadius() || detail!=d || radius!=r) {
                focus=current;detail=d;radius=r;cursor=0;var positions=new HashSet<Long>();
                int outer=current.viewRadius()==Integer.MAX_VALUE?r:current.viewRadius(),ring=r,previous=-1,ringDetail=d;
                while(true) {
                    int sourceWidth=1<<ringDetail,range=(ring+sourceWidth-1)/sourceWidth+1;
                    int centerX=Math.floorDiv(current.x(),sourceWidth),centerZ=Math.floorDiv(current.z(),sourceWidth);
                    for(int x=centerX-(current.period()>0?Math.min(range,current.period()/sourceWidth):range);x<=centerX+(current.period()>0?Math.min(range,current.period()/sourceWidth):range);x++)for(int z=centerZ-range;z<=centerZ+range;z++) {
                        long blockX=(long)x*sourceWidth;
                        if(current.period()>0)blockX=Math.floorMod(blockX+current.period()/2L,current.period())-current.period()/2L;
                        long p=DhSectionPos.encode((byte)ringDetail,(int)Math.floorDiv(blockX,sourceWidth),z);
                        long far=ProgressivePriority.distance(DhSectionPos.getCenterBlockPosX(p),DhSectionPos.getCenterBlockPosZ(p),current.x(),current.z(),current.period())+sourceWidth/2L;
                        if(distance(p,current)<=ring && far>previous && queue.cruising$previewAllowed(p))positions.add(p);
                    }
                    if(ring>=outer || ring==0)break;
                    previous=ring;ring=Math.min(outer,ring*2);
                    ringDetail=6+ProgressivePriority.initialDetail(Math.min(8,ringDetail-5),current.period());
                }
                candidates=new ArrayList<>(positions);candidates.sort(Comparator.comparingLong(p->distance(p,current)));
            }
        }
        for(int checked=0;checked<CHECKS;checked++) {
            long pos;synchronized(this){if(pending.size()>=LIMIT || candidates.isEmpty())break;pos=candidates.get(cursor++%candidates.size());if(pending.containsKey(pos) || published.contains(pos))continue;}
            if(!inRange(pos))continue;
            if(!provider.canQueueRetrievalNow())break; // caller supplies native admission, excluding only parked refinement slots
            var missing=provider.getPositionsToRetrieve(pos,DhSectionPos.getDetailLevel(pos));if(missing==null || missing.isEmpty())continue;
            queue.cruising$markPreview(pos,true);
            CompletableFuture<DataSourceRetrievalResult> result;
            try{result=provider.queuePositionForRetrieval(pos);}catch(RuntimeException error){queue.cruising$markPreview(pos,false);throw error;}
            if(result==null){queue.cruising$markPreview(pos,false);break;}
            synchronized(this){pending.put(pos,result);}
            ProgressiveDiagnostics.count("server","preview.submitted");
            result.whenComplete((value,error)->{queue.cruising$markPreview(pos,false);synchronized(this){pending.remove(pos,result);}ProgressiveDiagnostics.count("server",error==null?"preview.completed":"preview.cancelled_or_failed");});
        }
    }
    private static boolean inRange(long pos){return com.seibel.distanthorizons.core.util.WorldGenUtil.isPosInWorldGenRange(pos,
            com.seibel.distanthorizons.core.config.Config.Common.WorldGenerator.generationCenterChunkX.get(),
            com.seibel.distanthorizons.core.config.Config.Common.WorldGenerator.generationCenterChunkZ.get(),
            com.seibel.distanthorizons.core.config.Config.Common.WorldGenerator.generationMaxChunkRadius.get());}
    private static long distance(long pos,ProgressiveQueueAccess.Focus focus){return ProgressivePriority.sectionDistance(DhSectionPos.getCenterBlockPosX(pos),DhSectionPos.getCenterBlockPosZ(pos),focus.x(),focus.z(),focus.period(),DhSectionPos.getBlockWidth(pos));}
}
