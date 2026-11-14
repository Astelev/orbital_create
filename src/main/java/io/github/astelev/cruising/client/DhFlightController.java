package io.github.astelev.cruising.client;

import io.github.astelev.cruising.CruisingSpeed;
import io.github.astelev.cruising.compat.dh.DhFlightConfig;
import io.github.astelev.cruising.compat.dh.DhFlightState;
import io.github.astelev.cruising.compat.dh.FallbackBiomeColors;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Arrays;
import java.util.Locale;

@EventBusSubscriber(modid = CruisingSpeed.MODID, value = Dist.CLIENT)
public final class DhFlightController {
    private static io.github.astelev.cruising.compat.dh.FlightLodPolicy generationPolicy;
    private static String generationSettings;
    private static final io.github.astelev.cruising.compat.dh.FlightMotionSampler motionSampler=new io.github.astelev.cruising.compat.dh.FlightMotionSampler();
    private static long lastLogMillis;
    private static volatile boolean active;
    private static double speed;

    private DhFlightController() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        active = mc.level != null && mc.player != null && ModList.get().isLoaded("distanthorizons")
                && DhFlightConfig.ENABLED.getAsBoolean() && DhFlightConfig.MODIFY_DH.getAsBoolean()
                && mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                && (ClientFlightView.isActive(mc.level) || mc.gameRenderer.getMainCamera().getPosition().y > DhFlightConfig.ADAPT_ABOVE_Y.getAsInt());
        if (!active) {
            DhFlightState.publish(false, 0);
            motionSampler.reset();generationPolicy=null;
            return;
        }
        long now = System.nanoTime() / 1_000_000L;
        var position = mc.gameRenderer.getMainCamera().getPosition();
        var motion=motionSampler.sample(position.x,position.y,position.z,now);speed=motion.speed();
        DhFlightState.travelVelocity(DhFlightConfig.FORWARD_PRIORITY.getAsBoolean()?motion.xVelocity():0,
                DhFlightConfig.FORWARD_PRIORITY.getAsBoolean()?motion.zVelocity():0);
        String config=DhFlightConfig.COARSE_DETAIL.getAsInt()+":"+DhFlightConfig.FAST_SPEED.get()+":"+DhFlightConfig.QUIET_SECONDS.getAsInt()+":"+DhFlightConfig.REFINE_SECONDS.getAsInt();
        if(generationPolicy==null || !config.equals(generationSettings)) {
            generationSettings=config;generationPolicy=new io.github.astelev.cruising.compat.dh.FlightLodPolicy(
                    DhFlightConfig.COARSE_DETAIL.getAsInt(),DhFlightConfig.FAST_SPEED.get(),DhFlightConfig.QUIET_SECONDS.getAsInt()*1000L,DhFlightConfig.REFINE_SECONDS.getAsInt()*1000L);
        }
        int generationFloor=generationPolicy.sample(true,speed,now);
        generationFloor=io.github.astelev.cruising.compat.dh.progressive.ProgressiveConfig.enabled()
                ?Math.min(generationFloor,io.github.astelev.cruising.compat.dh.progressive.ProgressiveConfig.INITIAL_DETAIL.getAsInt()):0;
        DhFlightState.publish(true,generationFloor);
        if (DhFlightConfig.DIAGNOSTICS.getAsBoolean() && now - lastLogMillis >= 30_000) {
            lastLogMillis = now;
            CruisingSpeed.LOGGER.info("Flight LOD: native render detail, new generation cell={} blocks, cameraSpeed={} blocks/s, DH generation gate={}, {}",
                    1 << DhFlightState.minimumDetail(), Math.round(speed), DhFlightState.recentGate(), serverTiming());
        }
    }

    @SubscribeEvent
    public static void debugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (active && DhFlightConfig.DIAGNOSTICS.getAsBoolean()) {
            event.getLeft().add("CruisingSpeed LOD: native render; generation floor " + (1 << DhFlightState.minimumDetail())
                    + " blocks; DH gate " + DhFlightState.recentGate());
            event.getLeft().add("CruisingSpeed " + serverTiming());
            if (DhFlightConfig.FALLBACK_PLANE.getAsBoolean() && DhFlightConfig.FALLBACK_BIOMES.getAsBoolean()) {
                event.getLeft().add("CruisingSpeed surface biomes: " + FallbackBiomeColors.knownCount() + "/"
                        + FallbackBiomeColors.WIDTH * FallbackBiomeColors.WIDTH + " cells");
            }
        }
    }

    private static String serverTiming() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return "server timing unavailable (multiplayer)";
        }
        long[] ticks = server.getTickTimesNanos().clone();
        Arrays.sort(ticks);
        double average = Arrays.stream(ticks).average().orElse(0) / 1_000_000.0;
        double p99 = ticks[Math.max(0, (int) Math.ceil(ticks.length * 0.99) - 1)] / 1_000_000.0;
        return String.format(Locale.ROOT, "server avg %.1f ms / p99 %.1f ms (DH limits: 25 / 50)", average, p99);
    }

}
