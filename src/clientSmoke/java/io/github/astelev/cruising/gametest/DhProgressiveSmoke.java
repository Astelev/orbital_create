package io.github.astelev.cruising.gametest;

import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper_neoforge;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiGeneratorPlan;
import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import com.seibel.distanthorizons.core.generation.DhWorldGenerator;
import com.seibel.distanthorizons.core.generation.queues.*;
import com.seibel.distanthorizons.core.generation.tasks.*;
import com.seibel.distanthorizons.core.level.IDhServerLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import io.github.astelev.cruising.CruisingSpeed;
import io.github.astelev.cruising.compat.dh.progressive.ProgressiveConfig;
import io.github.astelev.cruising.compat.dh.progressive.ProgressiveQueueAccess;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.Proxy;

/** Real DH provider, callback, pooled sources and reduction; deterministic generation substitutes only terrain work. */
@net.neoforged.fml.common.EventBusSubscriber(modid=CruisingSpeed.MODID,value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class DhProgressiveSmoke {
    private static int ticks;
    @net.neoforged.bus.api.SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("cruising.progressiveSmoke"))return;
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null || ++ticks<100)return;
        run(mc);System.setProperty("cruising.progressiveSmoke","false");mc.stop();
    }
    static void run(Minecraft mc) {
        var plan=Config.Common.WorldGenerator.generatorPlan.get();
        try {
            Config.Common.WorldGenerator.generatorPlan.set(EDhApiGeneratorPlan.SURFACE_THEN_CHUNKS);
            ProgressiveConfig.ENABLED.set(true);ProgressiveConfig.INITIAL_DETAIL.set(3);
            var level=SharedApi.getAbstractDhWorld().getLevel(ServerLevelWrapper_neoforge.getWrapper(mc.getSingleplayerServer().overworld()));
            if(Boolean.getBoolean("cruising.progressiveTerrainSmoke"))verifyRealSurface((IDhServerLevel)level);
            verifyNativeDetail(level);
            var submitted=new ArrayList<Long>();var futures=new HashMap<Long,CompletableFuture<DataSourceRetrievalResult>>();
            Object queue=Proxy.newProxyInstance(DhProgressiveSmoke.class.getClassLoader(),new Class[]{IFullDataSourceRetrievalQueue.class,ProgressiveQueueAccess.class},(p,m,a)->switch(m.getName()) {
                case "cruising$previewSupported","cruising$previewNear","cruising$previewAllowed" -> true;
                case "submitRetrievalTask" -> {long pos=(Long)a[0];submitted.add(pos);yield futures.computeIfAbsent(pos,k->new CompletableFuture<>());}
                case "getRetrievalQueueLength","getMaxConcurrentTasks","getEstimatedRemainingChunkCount","getRetrievalEstimatedRemainingChunkCount" -> 0;
                case "lowestDataDetail" -> (byte)0;
                case "toString" -> "progressive smoke queue";
                default -> null;
            });
            var directory=java.nio.file.Files.createTempDirectory("around-progressive-dh-").toFile();
            try(var provider=new GeneratedFullDataSourceProvider(level,level.getSaveStructure(),directory)) {
                provider.worldGenQueueRef.set((IFullDataSourceRetrievalQueue)queue);
                long fine=DhSectionPos.encode((byte)6,0,3072);
                var request=provider.queuePositionForRetrieval(fine);
                for(int detail=9;detail>=6;detail--) {
                    long pos=DhSectionPos.convertToDetailLevel(fine,(byte)detail);
                    require(submitted.size()==10-detail && submitted.getLast()==pos,"Native stages out of order: "+submitted);
                    require(!request.isDone(),"Fine result completed before publication");
                    var source=FullDataSourceV2.createEmpty(pos);
                    futures.get(pos).complete(DataSourceRetrievalResult.CreateSuccess(pos,source));
                }
                require(request.isDone() && !request.isCompletedExceptionally(),"Native progressive request failed");
                ProgressiveConfig.ENABLED.set(false);
                long direct=DhSectionPos.encode((byte)6,1,3072);int before=submitted.size();
                var bypass=provider.queuePositionForRetrieval(direct);
                require(submitted.size()==before+1 && submitted.getLast()==direct,"Disabled progressive adapter still queued ancestors");
                futures.get(direct).complete(DataSourceRetrievalResult.CreateSuccess(direct,FullDataSourceV2.createEmpty(direct)));
                require(bypass.isDone(),"Native bypass did not complete");ProgressiveConfig.ENABLED.set(true);
                provider.worldGenQueueRef.set(null);
            }
            var generator=new DhWorldGenerator((IDhServerLevel)level) {
                @Override public byte getSmallestDataDetailLevel(){return 4;}
                @Override public byte getLargestDataDetailLevel(){return 4;}
            };
            var ranked=new WorldGenerationQueue(generator,(IDhServerLevel)level);
            try {
                if(net.neoforged.fml.ModList.get().isLoaded("around_planet")) {
                    require(!(Boolean)Class.forName("io.github.astelev.around_planet.progressive.ProgressiveQueueAccess")
                            .getMethod("around_planet$previewSupported").invoke(ranked),"Legacy scheduler remains active");
                }
                var field=WorldGenerationQueue.class.getDeclaredField("waitingTaskByPos");field.setAccessible(true);
                @SuppressWarnings("unchecked") var waiting=(ConcurrentHashMap<Long,DataSourceRetrievalTask>)field.get(ranked);
                var fine=new DataSourceRetrievalTask(DhSectionPos.encode((byte)6,0,0),(byte)0);
                var rough=new DataSourceRetrievalTask(DhSectionPos.encode((byte)9,0,0),(byte)3);
                waiting.put(fine.pos,fine);waiting.put(rough.pos,rough);
                // Exceed the native parallel-reduction threshold: worker threads must share the captured rank context.
                for(int i=1;i<=2048;i++){var extra=new DataSourceRetrievalTask(DhSectionPos.encode((byte)6,i,0),(byte)0);waiting.put(extra.pos,extra);}
                var choose=WorldGenerationQueue.class.getDeclaredMethod("tryStartNextWorldGenTask",DhBlockPos2D.class);choose.setAccessible(true);
                choose.invoke(ranked,new DhBlockPos2D(0,0));
                require(rough.future.isDone() && !fine.future.isDone(),"Native reduction did not prefer coarse preview");
                CruisingSpeed.LOGGER.info("DH_PROGRESSIVE_NATIVE PASS: native provider publishes 8/4/2/1 in order, pooled source ownership retained, native reduction prefers preview with Cruising present");
            } finally {ranked.startClosingAsync(true,true).join();}
        } catch(Exception e){throw new AssertionError("Native DH progressive regression",e);}
        finally {Config.Common.WorldGenerator.generatorPlan.set(plan);}
    }
    private static void verifyRealSurface(IDhServerLevel level) throws Exception {
        var generator=new DhWorldGenerator(level);
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            long fine=DhSectionPos.encode((byte)6,4,4096);
            for(int detail=3;detail>=1;detail--) {
                long pos=DhSectionPos.convertToDetailLevel(fine,(byte)(6+detail));
                try(var source=FullDataSourceV2.createEmpty(pos)) {
                    source.setRunApiSetterValidation(generator.runApiValidation());
                    long start=System.nanoTime();
                    generator.generateLod(DhSectionPos.getMinCornerBlockX(pos)/16,DhSectionPos.getMinCornerBlockZ(pos)/16,
                            DhSectionPos.getX(pos),DhSectionPos.getZ(pos),(byte)detail,source,
                            com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode.FEATURES,executor,r->{})
                            .get(90,TimeUnit.SECONDS);
                    int columns=0;for(int x=0;x<64;x++)for(int z=0;z<64;z++){var c=source.tryGetColumnAtRelPos(x,z);if(c!=null && !c.isEmpty())columns++;}
                    require(columns==4096,"Native surface left empty columns at detail "+detail+": "+columns);
                    CruisingSpeed.LOGGER.info("DH_PROGRESSIVE_TERRAIN detail={} width={} columns={} wallMs={}",detail,64<<detail,columns,(System.nanoTime()-start)/1e6);
                }
            }
            CruisingSpeed.LOGGER.info("DH_PROGRESSIVE_TERRAIN PASS: real native surface generator fills every column at 8/4/2-block detail");
        } finally {executor.shutdownNow();}
    }
    private static void verifyNativeDetail(com.seibel.distanthorizons.core.level.IDhLevel level) throws Exception {
        var combined=(com.seibel.distanthorizons.core.level.DhClientServerLevel)level;
        var tree=combined.clientside.ClientRenderStateRef.get().quadtree;
        var detail=tree.getClass().getDeclaredMethod("calcDetailLevelFromDistance",double.class,byte.class);detail.setAccessible(true);
        try {for(double distance:new double[]{1,100,1000,10000}) {
            io.github.astelev.cruising.compat.dh.DhFlightState.publish(false,0);
            byte nativeDetail=(Byte)detail.invoke(tree,distance,(byte)0);
            io.github.astelev.cruising.compat.dh.DhFlightState.publish(true,6);
            require((Byte)detail.invoke(tree,distance,(byte)0)==nativeDetail,"Flight reduced native detail at "+distance);
        }}finally {io.github.astelev.cruising.compat.dh.DhFlightState.publish(false,0);}
        if(net.neoforged.fml.ModList.get().isLoaded("around_planet")) {
            var world=Minecraft.getInstance().getSingleplayerServer().overworld();
            int width=io.github.astelev.cruising.compat.dh.progressive.ProgressiveWorldBounds.width(world);
            require(width>0,"Optional topology was not detected in paired fixture");
            long wide=DhSectionPos.encode((byte)9,0,0);
            require(!io.github.astelev.cruising.compat.dh.progressive.ProgressiveWorldBounds.inside(world,wide),"Cross-boundary preview ancestor allowed");
            var ownership=Class.forName("io.github.astelev.around_planet.progressive.ProgressiveOwnership");
            require((Boolean)ownership.getMethod("cruisingOwnsGeneration").invoke(null),"around_planet did not delegate scheduling");
        }
        CruisingSpeed.LOGGER.info("CRUISING_NATIVE_DETAIL PASS: flight preserves native quadtree detail at all sampled distances");
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private DhProgressiveSmoke(){}
}
