package io.github.astelev.cruising.flight;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import java.util.*;

/** Live constraint connectivity, independent of proximity or a swept bounding box. Optional Sable code only. */
public final class ConnectedFlightAssembly {
    private static final Map<ServerLevel,Graph> GRAPHS=new IdentityHashMap<>();
    private record Link(PhysicsPipelineBody first,PhysicsPipelineBody second,PhysicsConstraintHandle handle) {}
    private record Group(List<ServerSubLevel> ships,boolean external,Set<PhysicsPipelineBody> bodies) {}
    private static final class Graph {
        final Map<PhysicsConstraintHandle,Link> links=new IdentityHashMap<>();
        final Map<PhysicsPipelineBody,Group> groups=new IdentityHashMap<>();
        final Map<Group,Double> minimum=new IdentityHashMap<>();
        final Map<Group,Boolean> forced=new IdentityHashMap<>();
        boolean evaluating;
        void invalidate(){groups.clear();minimum.clear();forced.clear();}
        Group group(ServerSubLevel ship) {
            var cached=groups.get(ship);if(cached!=null)return cached;
            var adjacent=new IdentityHashMap<PhysicsPipelineBody,List<PhysicsPipelineBody>>();
            for(var link:links.values()) {
                var a=link.first;var b=link.second;
                // A null endpoint is a world-space joint frame, not a physical body.
                // Tool/servo motors use it too. It has no hull that could intersect terrain.
                if(a==null || b==null)continue;
                adjacent.computeIfAbsent(a,ignored->new ArrayList<>()).add(b);
                adjacent.computeIfAbsent(b,ignored->new ArrayList<>()).add(a);
            }
            var visited=Collections.newSetFromMap(new IdentityHashMap<PhysicsPipelineBody,Boolean>());
            var queue=new ArrayDeque<PhysicsPipelineBody>();queue.add(ship);visited.add(ship);
            var members=new ArrayList<ServerSubLevel>();boolean external=false;
            while(!queue.isEmpty()) {
                var body=queue.removeFirst();
                if(body instanceof ServerSubLevel member)members.add(member);else external=true;
                for(var next:adjacent.getOrDefault(body,List.of()))if(visited.add(next))queue.addLast(next);
            }
            var group=new Group(List.copyOf(members),external,visited);
            for(var body:visited)groups.put(body,group);
            return group;
        }
    }
    public static synchronized void added(ServerLevel level,PhysicsPipelineBody first,PhysicsPipelineBody second,PhysicsConstraintHandle handle) {
        if(handle==null)return;
        var graph=GRAPHS.computeIfAbsent(level,ignored->new Graph());
        graph.links.put(handle,new Link(first,second,handle));graph.invalidate();
    }
    public static synchronized void removed(Object handle) {
        for(var graph:GRAPHS.values())if(graph.links.remove(handle)!=null)graph.invalidate();
    }
    public static synchronized void bodyRemoved(ServerLevel level,PhysicsPipelineBody body) {
        var graph=GRAPHS.get(level);
        if(graph!=null){graph.links.values().removeIf(l->l.first==body || l.second==body);graph.invalidate();}
    }
    /** Native breakage may invalidate a joint without calling its Java remove method. Check once per ticket update. */
    public static synchronized void refreshConnections(ServerLevel level) {
        var graph=GRAPHS.get(level);if(graph==null)return;
        if(graph.links.values().removeIf(l->l.first!=null && l.first.isRemoved()
                || l.second!=null && l.second.isRemoved() || !l.handle.isValid()))graph.invalidate();
    }
    public static synchronized void beginUpdate(ServerLevel level) {
        refreshConnections(level);
        var graph=GRAPHS.get(level);if(graph==null)return;
        graph.minimum.clear();graph.forced.clear();graph.evaluating=true;
    }
    public static synchronized void endUpdate(ServerLevel level) {
        var graph=GRAPHS.get(level);if(graph!=null){graph.evaluating=false;graph.minimum.clear();graph.forced.clear();}
    }
    public static synchronized void clear(ServerLevel level){GRAPHS.remove(level);}
    private static synchronized Group group(SubLevel ship) {
        if(!(ship instanceof ServerSubLevel server))return new Group(List.of(),true,Set.of());
        var graph=GRAPHS.get(server.getLevel());return graph==null?new Group(List.of(server),false,Set.of(server)):graph.group(server);
    }
    public static synchronized String diagnostic(SubLevel ship){
        var g=group(ship);var graph=GRAPHS.get(ship.getLevel());int frames=0;
        if(graph!=null)for(var l:graph.links.values())if(l.first==null && g.bodies.contains(l.second) || l.second==null && g.bodies.contains(l.first))frames++;
        return "assemblyShips="+g.ships.size()+" externalAnchor="+g.external+" worldFrameJoints="+frames;
    }
    /** Use the whole connected assembly, not player velocity or a rotor's individual velocity. */
    /** Slow descent is allowed; preserve the method name for optional integrations. */
    public static boolean notDescending(SubLevel ship) {
        var g=group(ship);if(g.external || g.ships.isEmpty())return false;
        double mass=0,momentum=0;
        for(var s:g.ships){double m=s.getSelfMassTracker().getMass();
            if(s.isRemoved() || !(m>0) || !Double.isFinite(m))return false;
            var system=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(s.getLevel()).physicsSystem();
            if(!system.getPhysicsHandle(s).isValid())return false;
            momentum+=m*system.getPipeline().getLinearVelocity(s,new org.joml.Vector3d()).y;mass+=m;
        }
        return HighAltitudePolicy.mayCruise(momentum/mass,FlightConfig.MAX_DESCENT_SPEED.get());
    }
    /** Keep occupied sky assemblies native when their ordinary terrain holding column retires.
     * This is a transient unload decision, not a terrain or persistent force-load ticket. */
    public static boolean protectOccupied(ServerSubLevel ship) {
        var level=ship.getLevel();
        if(!FlightConfig.ENABLED.getAsBoolean() || ship.isRemoved() || !level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                || !HighAltitudePolicy.maySkipTerrainTicket(true,true,false,currentMinY(ship),level.getMaxBuildHeight(),FlightConfig.MIN_ALTITUDE.getAsInt()))return false;
        return level.players().stream().anyMatch(player->hasPassenger(ship,player));
    }
    public static boolean connected(SubLevel ship){return group(ship).ships.size()>1;}
    public static synchronized double currentMinY(SubLevel ship) {
        var group=group(ship);
        var graph=ship.getLevel() instanceof ServerLevel level?GRAPHS.get(level):null;
        // Poses do not step inside the ticket manager. Movement checks outside it always use live geometry.
        if(graph!=null && graph.evaluating)return graph.minimum.computeIfAbsent(group,ConnectedFlightAssembly::minimum);
        return minimum(group);
    }
    private static double minimum(Group group) {
        if(group.external || group.ships.isEmpty())return Double.NEGATIVE_INFINITY;
        double min=Double.POSITIVE_INFINITY;
        for(var member:group.ships) {
            if(member.isRemoved())return Double.NEGATIVE_INFINITY;
            min=Math.min(min,ShipGeometry.currentMinY(member));
        }
        return min;
    }
    public static boolean hasPassenger(SubLevel ship,Entity entity) {
        if(ship.isRemoved() || entity.level()!=ship.getLevel())return false;
        var group=group(ship);
        if(hasBody(group,Sable.HELPER.getTrackingSubLevel(entity)))return true;
        var vehicle=entity.getVehicle();
        for(int depth=0;vehicle!=null && depth<16;depth++,vehicle=vehicle.getVehicle())
            if(hasBody(group,Sable.HELPER.getTrackingSubLevel(vehicle)) || hasBody(group,Sable.HELPER.getContaining(vehicle)))return true;
        for(var member:group.ships)if(!member.isRemoved() && ShipGeometry.containsPassenger(member,entity))return true;
        return false;
    }
    private static boolean hasBody(Group group,SubLevel body){return body!=null && !body.isRemoved() && group.bodies.contains(body);}
    public static synchronized boolean forceLoaded(SubLevel ship,Collection<?> forced) {
        var group=group(ship);var graph=ship.getLevel() instanceof ServerLevel level?GRAPHS.get(level):null;
        if(graph!=null && graph.evaluating)return graph.forced.computeIfAbsent(group,g->g.ships.stream().anyMatch(forced::contains));
        return group.ships.stream().anyMatch(forced::contains);
    }
    private ConnectedFlightAssembly() {}
}
