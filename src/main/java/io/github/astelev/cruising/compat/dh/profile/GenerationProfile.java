package io.github.astelev.cruising.compat.dh.profile;

import io.github.astelev.cruising.CruisingSpeed;
import io.github.astelev.cruising.compat.dh.progressive.ProgressiveConfig;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;

/** Aggregate timings only. Nested work is subtracted on the same thread; async time is not CPU time. */
public final class GenerationProfile {
    private static final ThreadMXBean CPU=ManagementFactory.getThreadMXBean();
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    private static final Map<String,Stats> STATS=new ConcurrentHashMap<>();
    private static long next,lastReport=System.nanoTime();
    private static final Map<net.minecraft.world.level.chunk.ChunkAccess,Integer> DH_CHUNKS=Collections.synchronizedMap(new WeakHashMap<>());
    public static void markChunk(net.minecraft.world.level.chunk.ChunkAccess chunk,boolean enter){synchronized(DH_CHUNKS){if(enter)DH_CHUNKS.merge(chunk,1,Integer::sum);else {var n=DH_CHUNKS.get(chunk);if(n!=null){if(n<=1)DH_CHUNKS.remove(chunk);else DH_CHUNKS.put(chunk,n-1);}}}}
    public static Scope worker(String phase,net.minecraft.world.level.chunk.ChunkAccess chunk){if(!enabled())return null;return enter((DH_CHUNKS.containsKey(chunk)?"dh_worker.":"minecraft_worker.")+phase);}

    private static final class Stats {
        final LongAdder count=new LongAdder(),wall=new LongAdder(),selfWall=new LongAdder(),cpu=new LongAdder(),selfCpu=new LongAdder(),cpuSamples=new LongAdder();
        final AtomicLong max=new AtomicLong();
    }
    public static boolean observed(String key){return STATS.containsKey(key);}
    public static boolean enabled(){return ProgressiveConfig.SPEC.isLoaded() && ProgressiveConfig.PROFILE.getAsBoolean();}
    private static long cpu(){return CPU.isCurrentThreadCpuTimeSupported() && CPU.isThreadCpuTimeEnabled()?CPU.getCurrentThreadCpuTime():-1;}
    public static Scope enter(String stage){if(!enabled())return null;var s=new Scope(stage,CURRENT.get());CURRENT.set(s);return s;}
    public static long asyncStart(){return enabled()?System.nanoTime():0;}
    public static void asyncEnd(String stage,long start){if(start!=0)record(stage,System.nanoTime()-start,0,-1,0);}
    public static final class Scope implements AutoCloseable {
        final String stage;final Scope parent;final long wall=System.nanoTime(),cpu=cpu();long childWall,childCpu;
        private Scope(String stage,Scope parent){this.stage=stage;this.parent=parent;}
        @Override public void close(){long elapsed=System.nanoTime()-wall,endCpu=cpu();long used=cpu>=0 && endCpu>=0?endCpu-cpu:-1;
            CURRENT.set(parent);if(parent==null)CURRENT.remove();else {parent.childWall+=elapsed;if(used>=0)parent.childCpu+=used;}
            record(stage,elapsed,Math.max(0,elapsed-childWall),used,used<0?0:Math.max(0,used-childCpu));
        }
    }
    private static void record(String name,long wall,long selfWall,long cpu,long selfCpu){var s=STATS.computeIfAbsent(name,k->new Stats());s.count.increment();s.wall.add(wall);s.selfWall.add(selfWall);s.max.accumulateAndGet(wall,Math::max);if(cpu>=0){s.cpuSamples.increment();s.cpu.add(cpu);s.selfCpu.add(selfCpu);}report(false);}
    public static synchronized void report(boolean force){long now=System.nanoTime();if(!force && now<next)return;next=now+20_000_000_000L;long windowMs=(now-lastReport)/1_000_000;lastReport=now;
        var rows=new ArrayList<String>();new TreeMap<>(STATS).forEach((k,s)->{long n=s.count.sumThenReset();if(n==0)return;long wall=s.wall.sumThenReset(),self=s.selfWall.sumThenReset(),cpu=s.cpu.sumThenReset(),selfCpu=s.selfCpu.sumThenReset(),samples=s.cpuSamples.sumThenReset(),max=s.max.getAndSet(0);
            rows.add(String.format(Locale.ROOT,"%s n=%d totalWallMs=%.2f avgWallMs=%.3f maxWallMs=%.3f selfWallMs=%.2f threadCpuMs=%.2f selfThreadCpuMs=%.2f cpuSamples=%d",k,n,wall/1e6,wall/1e6/n,max/1e6,self/1e6,cpu/1e6,selfCpu/1e6,samples));});
        if(!rows.isEmpty()){CruisingSpeed.LOGGER.info("[CRUISING-GEN-PROFILE] windowEndEpochMs={} windowMs={} threadCpuSupported={} nested rows overlap; async rows include waiting",System.currentTimeMillis(),windowMs,CPU.isCurrentThreadCpuTimeSupported());rows.forEach(r->CruisingSpeed.LOGGER.info("[CRUISING-GEN-PROFILE] {}",r));}}
    private GenerationProfile(){}
}
