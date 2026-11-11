package io.github.astelev.cruising.flight;

import io.github.astelev.cruising.CruisingSpeed;
import io.github.astelev.cruising.network.TemporaryViewPayload;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CruisingSpeed.MODID)
public final class FlightHooks {
    private static final Map<Level, TemporaryChunkCache> CACHES = new IdentityHashMap<>();
    private static final java.util.concurrent.atomic.LongAdder SKY_TICKETS=new java.util.concurrent.atomic.LongAdder(),REAL_TICKETS=new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder HOLDING_PROTECTED=new java.util.concurrent.atomic.LongAdder();
    public static void noteHoldingProtection(){HOLDING_PROTECTED.increment();}
    private static final Map<Level,long[]> REMOVAL_WINDOWS=new java.util.WeakHashMap<>();
    public static synchronized void noteNativeRemoval(Level level,UUID id,double y,String reason){
        if(y<=320)return;long tick=level.getGameTime();var w=REMOVAL_WINDOWS.computeIfAbsent(level,k->new long[]{tick,0});
        if(tick<w[0] || tick-w[0]>=100){w[0]=tick;w[1]=0;}
        if(w[1]++<8)CruisingSpeed.LOGGER.info("[CRUISING-NATIVE-REMOVE] side={} ship={} y={} reason={}",level.isClientSide?"client":"server",id,y,reason);
    }
    public static void noteShipTicket(boolean sky){(sky?SKY_TICKETS:REAL_TICKETS).increment();}
    private static final Map<UUID,Long> NEXT_DIAGNOSTIC=new HashMap<>();
    private static final Map<UUID, TemporaryViewPayload> SENT = new HashMap<>();

    private FlightHooks() {
    }

    public static boolean isHighShipEntity(Entity entity) {
        return entity.level() instanceof ServerLevel
                && (!(entity instanceof ServerPlayer player) || player instanceof net.neoforged.neoforge.common.util.FakePlayer
                    || player.connection.hasChannel(TemporaryViewPayload.TYPE))
                && ModList.get().isLoaded("sable")
                && SableFlightAccess.isHighShipEntity(entity);
    }

    public static LevelChunk movementChunk(Entity entity, Level level, int x, int z) {
        if (level == entity.level() && isHighShipEntity(entity)) {
            var real = ((ServerLevel) level).getChunkSource().getChunkNow(x, z);
            return real != null ? real : CACHES.computeIfAbsent(level, TemporaryChunkCache::new).get(x, z);
        }
        return null;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if(player.getY()>320 && player.server.getTickCount()>=NEXT_DIAGNOSTIC.getOrDefault(player.getUUID(),0L)) {
            NEXT_DIAGNOSTIC.put(player.getUUID(),(long)player.server.getTickCount()+100);
            CruisingSpeed.LOGGER.info("[CRUISING-FLIGHT] xyz=({}, {}, {}) enabled={} threshold={} buildMax={} channel={} skyMode={} shipTerrainTickets[skipped/real]={}/{} holdingProtected={} mounted={} vehicle={} {}",
                    player.getX(),player.getY(),player.getZ(),FlightConfig.ENABLED.getAsBoolean(),FlightConfig.MIN_ALTITUDE.getAsInt(),player.serverLevel().getMaxBuildHeight(),player.connection.hasChannel(TemporaryViewPayload.TYPE),isHighShipEntity(player),SKY_TICKETS.sumThenReset(),REAL_TICKETS.sumThenReset(),HOLDING_PROTECTED.sumThenReset(),player.isPassenger(),player.getVehicle()==null?"none":player.getVehicle().getType().toString(),
                    ModList.get().isLoaded("sable")?SableFlightAccess.diagnostic(player):"sable=absent");
        }
        // Client-only rendering installs do not require this server channel.
        // Conversely, never skip a real client's terrain tickets unless it can
        // receive the temporary-view protocol. Fake simulation players have no client.
        if(!player.connection.hasChannel(TemporaryViewPayload.TYPE)) { SENT.remove(player.getUUID()); return; }
        boolean active = isHighShipEntity(player);
        var pos = player.chunkPosition();
        int radius = player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned positioned
                ? positioned.viewDistance() : player.server.getPlayerList().getViewDistance();
        var payload = new TemporaryViewPayload(player.serverLevel().dimension().location(), active,
                pos.x, pos.z, Math.clamp(radius, 0, 32));
        var previous = SENT.get(player.getUUID());
        if (!payload.equals(previous) && (active || previous != null && previous.active())) {
            PacketDistributor.sendToPlayer(player, payload);
            if (!active) {
                // Existing real chunks may already have been sent before flight.
                // Re-queue them; new chunks use vanilla's normal generation queue.
                player.getChunkTrackingView().forEach(chunkPos -> {
                    var chunk = player.serverLevel().getChunkSource().chunkMap.getChunkToSend(chunkPos.toLong());
                    if (chunk != null) {
                        player.connection.chunkSender.markChunkPendingToSend(chunk);
                    }
                });
            }
        }
        if (active) {
            SENT.put(player.getUUID(), payload);
        } else {
            SENT.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());NEXT_DIAGNOSTIC.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if(ModList.get().isLoaded("sable"))ConnectedFlightAssembly.clear(level);
            CACHES.remove(level);
            if(level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)){NEXT_DIAGNOSTIC.clear();SKY_TICKETS.reset();REAL_TICKETS.reset();HOLDING_PROTECTED.reset();}
            SENT.entrySet().removeIf(entry -> entry.getValue().dimension().equals(level.dimension().location()));
        }
    }
}
