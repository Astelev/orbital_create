package io.github.astelev.cruising.compat.dh.progressive;

import net.neoforged.neoforge.common.ModConfigSpec;
import io.github.astelev.cruising.compat.dh.DhFlightConfig;

/** Common settings also apply to a dedicated DH server, without loading client classes. */
public final class ProgressiveConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue INITIAL_DETAIL,RADIUS,PENDING;
    static {
        var b=new ModConfigSpec.Builder();b.push("dhProgressive");
        ENABLED=b.comment("DH 3.3.3: generate shared rough surface coverage before fine terrain. Does not reduce existing render detail. Restart after changes.").define("enabled",true);
        INITIAL_DETAIL=b.comment("First surface cell exponent: 3 means 8-block cells, then 4/2/1. Independent of flight altitude.").defineInRange("initialDataDetail",3,1,4);
        RADIUS=b.comment("Preview priority radius in blocks, measured to each source footprint.").defineInRange("previewRadiusBlocks",1024,128,4096);
        PENDING=b.comment("Maximum deferred fine requests per provider; shared parents are bounded separately.").defineInRange("maxDeferredRequests",128,16,512);
        b.pop();SPEC=b.build();
    }
    public static boolean enabled(){return SPEC.isLoaded() && ENABLED.getAsBoolean()
            && (!DhFlightConfig.SPEC.isLoaded() || DhFlightConfig.ENABLED.getAsBoolean() && DhFlightConfig.MODIFY_DH.getAsBoolean());}
    private ProgressiveConfig(){}
}
