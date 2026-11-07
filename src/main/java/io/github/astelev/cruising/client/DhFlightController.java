package io.github.astelev.cruising.client;

import io.github.astelev.cruising.CruisingSpeed;
import io.github.astelev.cruising.compat.dh.DhFlightConfig;
import io.github.astelev.cruising.compat.dh.DhFlightState;
import io.github.astelev.cruising.compat.dh.FallbackBiomeColors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
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
    private static Vec3 lastPosition;
    private static long lastSampleMillis;
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
            lastPosition = null;
            return;
        }
        long now = System.nanoTime() / 1_000_000L;
        var position = mc.gameRenderer.getMainCamera().getPosition();
        speed = lastPosition == null || now <= lastSampleMillis ? 0 : position.distanceTo(lastPosition) * 1000.0 / (now - lastSampleMillis);
        var travel = position;
        if (lastPosition != null && now > lastSampleMillis && DhFlightConfig.FORWARD_PRIORITY.getAsBoolean()) {
            double scale = 1000.0 / (now - lastSampleMillis);
            DhFlightState.travelVelocity((travel.x - lastPosition.x) * scale, (travel.z - lastPosition.z) * scale);
        } else DhFlightState.travelVelocity(0, 0);
        lastPosition = position;
        lastSampleMillis = now;
        DhFlightState.publish(true, 0);
        if (DhFlightConfig.DIAGNOSTICS.getAsBoolean() && now - lastLogMillis >= 30_000) {
            lastLogMillis = now;
            CruisingSpeed.LOGGER.info("Flight LOD: native detail ({} block floor), cameraSpeed={} blocks/s, DH generation gate={}, {}",
                    1 << DhFlightState.minimumDetail(), Math.round(speed), DhFlightState.recentGate(), serverTiming());
        }
    }

    @SubscribeEvent
    public static void debugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (active && DhFlightConfig.DIAGNOSTICS.getAsBoolean()) {
            event.getLeft().add("CruisingSpeed LOD: native detail; floor " + (1 << DhFlightState.minimumDetail())
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
