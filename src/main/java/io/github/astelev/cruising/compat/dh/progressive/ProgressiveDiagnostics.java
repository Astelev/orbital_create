package io.github.astelev.cruising.compat.dh.progressive;

import io.github.astelev.cruising.CruisingSpeed;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Fixed-key bounded telemetry; wall time includes queue wait. No extra workers. */
public final class ProgressiveDiagnostics {
    private static final ConcurrentHashMap<String,LongAdder> COUNTS=new ConcurrentHashMap<>();
    private static volatile long next;
    public static long start(){return System.nanoTime();}
    public static void count(String ignored,String key){COUNTS.computeIfAbsent(key,k->new LongAdder()).increment();report();}
    public static void time(String ignored,String key,long start){COUNTS.computeIfAbsent(key+".count",k->new LongAdder()).increment();COUNTS.computeIfAbsent(key+".wallMs",k->new LongAdder()).add((System.nanoTime()-start)/1_000_000);report();}
    private static synchronized void report(){long now=System.nanoTime();if(now<next)return;next=now+30_000_000_000L;var values=new java.util.TreeMap<String,Long>();COUNTS.forEach((k,v)->{long n=v.sumThenReset();if(n!=0)values.put(k,n);});CruisingSpeed.LOGGER.info("[CRUISING-PROGRESSIVE] {}",values);}
    private ProgressiveDiagnostics(){}
}
