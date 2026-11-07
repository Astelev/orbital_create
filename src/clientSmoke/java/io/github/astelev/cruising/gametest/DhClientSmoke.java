package io.github.astelev.cruising.gametest;

import io.github.astelev.cruising.CruisingSpeed;
import io.github.astelev.cruising.compat.dh.DhFlightState;
import io.github.astelev.cruising.compat.dh.DhFlightConfig;
import io.github.astelev.cruising.client.ClientFlightView;
import io.github.astelev.cruising.client.FallbackPlaneRenderer;
import io.github.astelev.cruising.network.TemporaryViewPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.concurrent.atomic.AtomicReference;

/** Opt-in development source set: verifies the actual transformed DH client classes. */
@EventBusSubscriber(modid = CruisingSpeed.MODID, value = Dist.CLIENT)
public final class DhClientSmoke {
    private static int ticks;
    private static boolean finished;
    private static boolean verified;
    private static int savedHeight;
    private static int probeTick;
    private static long descentFrames, descentEpoch;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (finished || Boolean.getBoolean("cruising.progressiveSmoke")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (ticks > 2400) throw new AssertionError("CRUISING_SPEED_DH_SMOKE: timeout waiting for the test world");
        if (mc.level == null || mc.player == null || ticks < 100) return;
        if (verified) {
            if (FallbackPlaneRenderer.drawnFrames() > descentFrames && io.github.astelev.cruising.client.FallbackBiomeSampler.samples() > 0
                    && io.github.astelev.cruising.compat.dh.DhLodCoverage.trackedCount() > 0) {
                require(!ClientFlightView.isActive(mc.level), "Temporary sky view unexpectedly active after descent");
                require(io.github.astelev.cruising.compat.dh.FallbackBiomeColors.generation() == descentEpoch, "Descent cleared the biome palette");
                DhFlightConfig.FALLBACK_HEIGHT.set(savedHeight);
                ClientFlightView.accept(new TemporaryViewPayload(mc.level.dimension().location(), false, 0, 0, 0));
                finished = true;
                CruisingSpeed.LOGGER.info("CRUISING_SPEED_DH_SMOKE PASS: client quadtree, motion pause, surface depth, night lightmap, biome sampling, nearby generation priority, 64-block grid and continued surface after descent");
                mc.stop();
            } else if (ticks - probeTick > 200) {
                throw new AssertionError("Fallback native DH render hook did not draw: " + FallbackPlaneRenderer.lastSkip());
            }
            return;
        }
        try {
            var wrapperType = Class.forName("com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper_neoforge");
            var wrapper = wrapperType.getMethod("getWrapper", ClientLevel.class).invoke(null, mc.level);
            var level = wrapper.getClass().getMethod("getDhLevel").invoke(wrapper);
            if (level == null) return;
            var module = level.getClass().getField("clientside").get(level);
            var ref = (AtomicReference<?>) module.getClass().getField("ClientRenderStateRef").get(module);
            var renderState = ref.get();
            if (renderState == null) return;
            var tree = renderState.getClass().getField("quadtree").get(renderState);
            var detail = tree.getClass().getDeclaredMethod("calcDetailLevelFromDistance", double.class, byte.class);
            detail.setAccessible(true);
            DhFlightState.publish(false, 0);
            byte original = (Byte) detail.invoke(tree, 1.0, (byte) 0);
            if (original >= 4) return; // Wait for the first quadtree tick.
            DhFlightState.publish(true, 4);
            require((Byte) detail.invoke(tree, 1.0, (byte) 0) == original, "Flight downgraded ready DH terrain");
            DhFlightState.publish(false, 0);
            require((Byte) detail.invoke(tree, 1.0, (byte) 0) == original, "Original detail did not return on exit");

            var apiType = Class.forName("com.seibel.distanthorizons.core.api.internal.ClientApi");
            var api = apiType.getField("INSTANCE").get(null);
            var averageField = apiType.getDeclaredField("cameraSpeedRollingAverage");
            averageField.setAccessible(true);
            var average = averageField.get(api);
            var add = average.getClass().getMethod("add", double.class);
            for (int i = 0; i < 40; i++) add.invoke(average, 100.0);
            var configType = Class.forName("com.seibel.distanthorizons.core.config.Config$Common$WorldGenerator");
            var pause = configType.getField("pauseIfMovingQuickly").get(null);
            var get = pause.getClass().getMethod("get");
            var set = pause.getClass().getMethod("setWithoutSaving", Object.class);
            var saved = get.invoke(pause);
            var pools = Class.forName("com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil");
            var moving = pools.getDeclaredMethod("cameraMovingFast");
            moving.setAccessible(true);
            try {
                set.invoke(pause, true);
                DhFlightState.publish(false, 0);
                require((Boolean) moving.invoke(null), "Native DH motion pause was not exercised");
                DhFlightState.publish(true, 4);
                require(!(Boolean) moving.invoke(null), "Flight motion-pause bypass did not apply");
                // This must still run the native server/queue checks, not force true.
                pools.getMethod("worldGenThreadsCanRun").invoke(null);
                require(DhFlightState.recentGate() != DhFlightState.Reason.UNKNOWN, "DH gate diagnostics did not run");
                DhFlightState.publish(false, 0);
                require((Boolean) moving.invoke(null), "Native DH motion pause did not return on exit");
            } finally {
                set.invoke(pause, saved);
                DhFlightState.publish(false, 0);
            }
            Class.forName("com.seibel.distanthorizons.common.wrappers.worldGeneration.DhRoughSurfaceGenerator_neoforge");
            verifyGenerationPriority(tree);
            verifyLodCoverage();
            verifyStandaloneToggles(mc);
            FallbackPlaneSmoke.verify();
            savedHeight = DhFlightConfig.FALLBACK_HEIGHT.getAsInt();
            DhFlightConfig.FALLBACK_HEIGHT.set(-128);
            var pos = mc.player.chunkPosition();
            ClientFlightView.accept(new TemporaryViewPayload(mc.level.dimension().location(), true, pos.x, pos.z, 8));
            descentFrames = FallbackPlaneRenderer.drawnFrames();
            descentEpoch = io.github.astelev.cruising.compat.dh.FallbackBiomeColors.generation();
            ClientFlightView.accept(new TemporaryViewPayload(mc.level.dimension().location(), false, 0, 0, 0));
            verified = true;
            probeTick = ticks;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("CRUISING_SPEED_DH_SMOKE failed", e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("CRUISING_SPEED_DH_SMOKE: " + message);
    }
    private static void verifyGenerationPriority(Object tree) throws ReflectiveOperationException {
        var pos = Class.forName("com.seibel.distanthorizons.core.pos.DhSectionPos");
        var block = Class.forName("com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D");
        var task = Class.forName("com.seibel.distanthorizons.core.generation.tasks.DataSourceRetrievalTask");
        var queue = Class.forName("com.seibel.distanthorizons.core.generation.queues.WorldGenerationQueue");
        var rank = queue.getDeclaredMethod("lambda$tryStartNextWorldGenTask$0", block, java.util.Map.Entry.class);
        rank.setAccessible(true);
        long section = (Long) pos.getMethod("encode", byte.class, int.class, int.class).invoke(null, (byte) 8, 0, 0);
        var request = task.getConstructor(long.class, byte.class).newInstance(section, (byte) 2);
        var target = block.getConstructor(int.class, int.class).newInstance(0, 0);
        var entry = java.util.Map.entry(section, request);
        var distance = rank.getReturnType().getDeclaredField("dist");
        distance.setAccessible(true);
        DhFlightState.publish(false, 0);
        int normal = distance.getInt(rank.invoke(null, target, entry));
        DhFlightState.publish(true, 4);
        int flight = distance.getInt(rank.invoke(null, target, entry));
        var encode = pos.getMethod("encode", byte.class, int.class, int.class);
        long ahead = (Long) encode.invoke(null, (byte) 8, 5, 0);
        long behind = (Long) encode.invoke(null, (byte) 8, -6, 0);
        var aheadTask = task.getConstructor(long.class, byte.class).newInstance(ahead, (byte) 2);
        var behindTask = task.getConstructor(long.class, byte.class).newInstance(behind, (byte) 2);
        DhFlightState.travelVelocity(100, 0);
        int frontRank = distance.getInt(rank.invoke(null, target, java.util.Map.entry(ahead, aheadTask)));
        int backRank = distance.getInt(rank.invoke(null, target, java.util.Map.entry(behind, behindTask)));
        require(frontRank < backRank, "Generation did not add forward weight");
        var retrieval = tree.getClass().getDeclaredMethod("lambda$startQueuedRetrievalTasks$0", block, Long.class, Long.class);
        retrieval.setAccessible(true);
        require((Integer) retrieval.invoke(null,target,ahead,behind) < 0, "Retrieval did not prefer forward travel");
        var levelField = tree.getClass().getDeclaredField("level");
        levelField.setAccessible(true);
        var sectionType = Class.forName("com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection");
        var constructor = sectionType.getConstructors()[0];
        var frontSection = constructor.newInstance(ahead, tree, levelField.get(tree), null);
        var backSection = constructor.newInstance(behind, tree, levelField.get(tree), null);
        try {
            var meshes = tree.getClass().getDeclaredMethod("lambda$loadQueuedSections$0",block,sectionType,sectionType);
            meshes.setAccessible(true);
            require((Integer) meshes.invoke(null,target,frontSection,backSection) < 0, "Mesh queue did not prefer forward travel");
            io.github.astelev.cruising.compat.dh.DhGenerationPriority.beginSort();
            try {
                DhFlightState.travelVelocity(-100,0);
                require((Integer) retrieval.invoke(null,target,ahead,behind) < 0, "Sort heading changed mid-sort");
            } finally { io.github.astelev.cruising.compat.dh.DhGenerationPriority.endSort(); }
            require((Integer) retrieval.invoke(null,target,ahead,behind) > 0, "Turn did not update forward weight");
        } finally {
            sectionType.getMethod("close").invoke(frontSection);
            sectionType.getMethod("close").invoke(backSection);
        }
        DhFlightState.publish(false, 0);
        if (flight != 8 || normal == flight) throw new AssertionError("Native generation queue priority was not changed/scoped: " + normal + "/" + flight);
    }

    private static void verifyLodCoverage() throws ReflectiveOperationException {
        var type=Class.forName("com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource");
        var pos=Class.forName("com.seibel.distanthorizons.core.pos.DhSectionPos");
        long section=(Long)pos.getMethod("encode",byte.class,int.class,int.class).invoke(null,(byte)6,-1,-1);
        var source=type.getMethod("createEmpty",long.class,int.class,int.class).invoke(null,section,1,-64);
        try {
            type.getMethod("markNotEmpty").invoke(source);
            var data=(it.unimi.dsi.fastutil.longs.LongArrayList)type.getField("renderDataContainer").get(source);
            for(int i=0;i<4096;i++) data.set(i,1L);
            var known=io.github.astelev.cruising.compat.dh.DhLodCoverageBridge.capture(source);
            require(known!=null,"Native render-source coverage was unavailable");
            var mask=new boolean[1]; known.mark(mask,-1,-1,1);
            require(mask[0],"Ready native columns did not mask the world-space tile");
            data.set(0,0L);
            mask=new boolean[1];
            io.github.astelev.cruising.compat.dh.DhLodCoverageBridge.capture(source).mark(mask,-1,-1,1);
            require(!mask[0],"Partial native columns hid unfinished terrain");
        } finally { type.getMethod("close").invoke(source); }
    }

    private static void verifyStandaloneToggles(Minecraft mc) {
        require(mc.getConnection().hasChannel(TemporaryViewPayload.TYPE),"Optional high-altitude protocol was not negotiated");
        boolean modify=DhFlightConfig.MODIFY_DH.getAsBoolean();
        int altitude=DhFlightConfig.ADAPT_ABOVE_Y.getAsInt();
        try {
            ClientFlightView.accept(new TemporaryViewPayload(mc.level.dimension().location(),false,0,0,0));
            DhFlightConfig.MODIFY_DH.set(true);
            DhFlightConfig.ADAPT_ABOVE_Y.set(-128);
            io.github.astelev.cruising.client.DhFlightController.tick(null);
            require(DhFlightState.bypassMotionPause(),"Standalone camera altitude did not activate DH support without a sky passenger");
            DhFlightConfig.MODIFY_DH.set(false);
            io.github.astelev.cruising.client.DhFlightController.tick(null);
            require(!DhFlightState.bypassMotionPause(),"Disabling DH behavior left flight adaptations active");
            require(io.github.astelev.cruising.client.FallbackBiomeSampler.surfaceEnabled(mc.level),"Disabling DH behavior also disabled the independent biome surface");
        } finally {
            DhFlightConfig.MODIFY_DH.set(modify);
            DhFlightConfig.ADAPT_ABOVE_Y.set(altitude);
            DhFlightState.publish(false,0);
        }
    }

}
