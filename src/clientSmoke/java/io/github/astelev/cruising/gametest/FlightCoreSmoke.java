package io.github.astelev.cruising.gametest;
import io.github.astelev.cruising.CruisingSpeed;
import io.github.astelev.cruising.flight.FlightHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
public final class FlightCoreSmoke {
    private static boolean registered;
    public static void bootstrap(){if(!registered){registered=true;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(FlightCoreSmoke.class);}}
    private static int ticks,phase,start;private static volatile Throwable failure;private static volatile boolean ready;
    private static double targetX=2400008;
    private static dev.ryanhcode.sable.sublevel.ServerSubLevel ship;
    @net.neoforged.bus.api.SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        if(!Boolean.getBoolean("cruising.flightCoreSmoke"))return;
        var mc=Minecraft.getInstance();ticks++;if(failure!=null)throw new AssertionError("FLIGHT_CORE_SMOKE",failure);
        if(ticks>1600)throw new AssertionError("Flight core timeout phase="+phase);
        if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null || ticks<100)return;
        if(phase==0){phase=1;start=ticks;mc.getSingleplayerServer().execute(()->{try {
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var level=p.serverLevel();
            require(p.connection.hasChannel(io.github.astelev.cruising.network.TemporaryViewPayload.TYPE),"Real player does not negotiate temporary-view channel");
            var anchor=new BlockPos(0,80,0);level.setBlockAndUpdate(anchor,net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState());
            ship=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level,anchor,java.util.List.of(anchor),new dev.ryanhcode.sable.companion.math.BoundingBox3i(0,80,0,0,80,0));
            var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            container.addForceLoadTicket(ship,dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,net.minecraft.util.Unit.INSTANCE);
            container.physicsSystem().getPipeline().teleport(ship,new Vector3d(2400008,1000,2400008),ship.logicalPose().orientation());ship.updateBoundingBox();ship.updateLastPose();
            ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)p).sable$setTrackingSubLevel(ship);
            p.setNoGravity(true);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();
            p.connection.teleport(2400008,1001,2400008,0,0);
            require(FlightHooks.isHighShipEntity(p),"Real high-altitude crew not recognized");
            require(level.getChunkSource().getChunkNow(2400008>>4,2400008>>4)==null,"Crew teleport generated real terrain");
            ready=true;
        }catch(Throwable t){failure=t;}});}
        else if(phase==1 && ready && ticks-start>60){
            require(io.github.astelev.cruising.client.ClientFlightView.isActive(mc.level),"Real client temporary sky view is inactive");
            phase=2;mc.getSingleplayerServer().execute(()->{try {
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var level=p.serverLevel();
                require(FlightHooks.isHighShipEntity(p),"Crew lost sky eligibility");
                require(level.getChunkSource().getChunkNow(2400008>>4,2400008>>4)==null,"Sky mode loaded real terrain after ticks");
                var skip=net.minecraft.server.level.ChunkMap.class.getDeclaredMethod("skipPlayer",net.minecraft.server.level.ServerPlayer.class);skip.setAccessible(true);
                require((Boolean)skip.invoke(level.getChunkSource().chunkMap,p),"Native player terrain tickets were not suspended");
                for(int i=1;i<=3;i++) {
                    targetX=2400008+512*i;
                    var pipeline=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).physicsSystem().getPipeline();
                    pipeline.teleport(ship,new Vector3d(targetX,1000,2400008),ship.logicalPose().orientation());ship.updateBoundingBox();ship.updateLastPose();
                    ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)p).sable$setTrackingSubLevel(ship);
                    p.connection.teleport(targetX,1001,2400008,0,0);
                    require(FlightHooks.isHighShipEntity(p),"Moving crew lost sky eligibility");
                    require(level.getChunkSource().getChunkNow(((int)targetX)>>4,2400008>>4)==null,"Moving high ship loaded terrain");
                }
                CruisingSpeed.LOGGER.info("CRUISING_REAL_FLIGHT PASS: real network player, active temporary client view and unloaded terrain below ship after native ticking");
                mc.execute(mc::stop);
            }catch(Throwable t){failure=t;}});
        }
    }
    @net.neoforged.bus.api.SubscribeEvent public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        if(Boolean.getBoolean("cruising.flightCoreSmoke") && ship!=null && !ship.isRemoved()) {
            var pipeline=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(ship.getLevel()).physicsSystem().getPipeline();
            pipeline.teleport(ship,new Vector3d(targetX,1000,2400008),ship.logicalPose().orientation());pipeline.resetVelocity(ship);ship.updateBoundingBox();
        }
    }
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
}
