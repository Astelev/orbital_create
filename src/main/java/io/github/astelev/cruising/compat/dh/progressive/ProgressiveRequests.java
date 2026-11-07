package io.github.astelev.cruising.compat.dh.progressive;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Portable scheduling only: the backend owns data, generation, publication and rendering. */
public final class ProgressiveRequests<R> implements AutoCloseable {
    public interface Backend<R> {
        boolean ready(long pos);
        CompletableFuture<R> enqueue(long pos);
        default void cancelQueued(long pos){}
    }
    private final Backend<R> backend;
    private final int limit;
    private final Map<Long,CompletableFuture<R>> requests=new HashMap<>();
    private final LinkedHashMap<Long,CompletableFuture<Void>> stages=new LinkedHashMap<>();
    private final LinkedHashSet<Long> published=new LinkedHashSet<>();
    private final Map<Long,List<Long>> ownership=new HashMap<>();
    private final Map<Long,Integer> users=new HashMap<>();
    private boolean closed;
    public synchronized boolean needs(long pos){return users.containsKey(pos);}
    private void release(long pos,CompletableFuture<R> result) {
        var orphaned=new ArrayList<Long>();var cancelledStages=new ArrayList<CompletableFuture<Void>>();
        synchronized(this){
            if(!requests.remove(pos,result))return;
            var owned=ownership.remove(pos);if(owned!=null)for(long stage:owned){
                int remaining=users.getOrDefault(stage,1)-1;
                if(remaining>0)users.put(stage,remaining);else {
                    users.remove(stage);orphaned.add(stage);
                    var barrier=stages.get(stage);
                    if(barrier!=null && !barrier.isDone()){stages.remove(stage);cancelledStages.add(barrier);}
                }
            }
        }
        cancelledStages.forEach(f->f.cancel(false));
        orphaned.forEach(stage->{if(!needs(stage))backend.cancelQueued(stage);});
    }

    public ProgressiveRequests(Backend<R> backend,int limit){this.backend=backend;this.limit=limit;}
    public synchronized boolean canAccept(){return !closed && requests.size()<limit;}
    public synchronized int pending(){return requests.size();}
    public synchronized int stages(){return stages.size();}
    public CompletableFuture<R> request(long pos,List<Long> path) {
        CompletableFuture<R> result;
        synchronized(this) {
            if(closed)return CompletableFuture.failedFuture(new CancellationException("Provider closed"));
            var old=requests.get(pos);if(old!=null)return old;
            if(requests.size()>=limit)return null;
            result=new CompletableFuture<>();requests.put(pos,result);
            var owned=new LinkedHashSet<Long>(path);owned.add(pos);ownership.put(pos,List.copyOf(owned));owned.forEach(p->users.merge(p,1,Integer::sum));
        }
        result.whenComplete((r,e)->release(pos,result));
        advance(result,pos,path,0);return result;
    }
    private void advance(CompletableFuture<R> result,long pos,List<Long> path,int index) {
        if(result.isDone())return;
        if(index==path.size()) {
            try {var future=backend.enqueue(pos);if(future==null)result.completeExceptionally(new CancellationException("Queue unavailable"));else future.whenComplete((r,e)->{if(e==null)result.complete(r);else result.completeExceptionally(e);});}
            catch(Throwable e){result.completeExceptionally(e);}return;
        }
        stage(path.get(index)).whenComplete((v,e)->{if(e==null)advance(result,pos,path,index+1);else result.completeExceptionally(e);});
    }
    private CompletableFuture<Void> stage(long pos) {
        CompletableFuture<Void> future;
        synchronized(this) {
            if(closed)return CompletableFuture.failedFuture(new CancellationException("Provider closed"));
            if(published.contains(pos))return CompletableFuture.completedFuture(null);
            var old=stages.get(pos);if(old!=null)return old;
            while(stages.size()>=limit*4) {
                var victim=stages.entrySet().stream().filter(e->e.getValue().isDone()).findFirst();
                if(victim.isEmpty())return CompletableFuture.failedFuture(new CancellationException("Preview capacity"));
                stages.remove(victim.get().getKey());
            }
            future=new CompletableFuture<>();stages.put(pos,future);
        }
        try {
            if(backend.ready(pos))published(pos,null);
            else {
                var submitted=backend.enqueue(pos);
                if(submitted==null)published(pos,new CancellationException("Queue unavailable"));
                else submitted.whenComplete((r,e)->{if(e!=null)published(pos,e);});
                // Successful completion is deliberately signalled by the native publication callback.
            }
        }catch(Throwable e){published(pos,e);}
        return future;
    }
    public void published(long pos,Throwable error) {
        CompletableFuture<Void> future;
        synchronized(this) {
            if(closed)return;
            if(error==null){published.add(pos);while(published.size()>limit*8)published.remove(published.iterator().next());}
            future=stages.get(pos);if(error!=null)stages.remove(pos);
        }
        if(future!=null){if(error==null)future.complete(null);else future.completeExceptionally(error);}
    }
    public void cancelIf(LongPredicate predicate) {
        List<CompletableFuture<R>> cancelled=new ArrayList<>();
        synchronized(this){requests.forEach((p,f)->{if(predicate.test(p))cancelled.add(f);});}
        cancelled.forEach(f->f.cancel(false)); // Shared preview stages remain useful to sibling requests.
    }
    @Override public void close() {
        List<CompletableFuture<?>> all=new ArrayList<>();
        synchronized(this){closed=true;all.addAll(requests.values());all.addAll(stages.values());requests.clear();ownership.clear();users.clear();stages.clear();published.clear();}
        all.forEach(f->f.cancel(false));
    }
}
