package io.github.astelev.cruising.compat.dh.progressive;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import java.lang.reflect.Method;

/** Optional around_planet topology, without a compile or runtime dependency on it. */
public final class ProgressiveWorldBounds {
    private record Bridge(Method get,Method width,Method west,Method east){}
    private static final Bridge BRIDGE=discover();
    private static Bridge discover(){try {
        var borders=Class.forName("io.github.astelev.around_planet.border.PlanetBorders");
        var topology=Class.forName("io.github.astelev.around_planet.topology.EastWestTopology");
        return new Bridge(borders.getMethod("get",WorldBorder.class),topology.getMethod("width"),topology.getMethod("west"),topology.getMethod("east"));
    }catch(ReflectiveOperationException e){return null;}}
    public static int width(Level level){if(BRIDGE!=null)try {var t=BRIDGE.get.invoke(null,level.getWorldBorder());if(t!=null)return ((Number)BRIDGE.width.invoke(t)).intValue();}catch(ReflectiveOperationException ignored){}return 0;}
    public static boolean inside(Level level,long pos){if(BRIDGE!=null)try {
        var t=BRIDGE.get.invoke(null,level.getWorldBorder());if(t!=null){long min=DhSectionPos.getMinCornerBlockX(pos);return min>=((Number)BRIDGE.west.invoke(t)).longValue() && min+DhSectionPos.getBlockWidth(pos)<=((Number)BRIDGE.east.invoke(t)).longValue();}
    }catch(ReflectiveOperationException ignored){}return true;}
    private ProgressiveWorldBounds(){}
}
