package io.github.astelev.cruising.flight;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class FlightConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue MIN_ALTITUDE;
    public static final ModConfigSpec.DoubleValue MAX_DESCENT_SPEED;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("highAltitudeFlight");
        ENABLED = builder
                .comment("Skip Sable's forced terrain tickets above the world while its own loading rules keep the ship active.",
                        "Uses temporary client chunks and suspends terrain tickets for high-altitude ship passengers.")
                .translation("cruising.configuration.enabled").define("enabled", true);
        MIN_ALTITUDE = builder
                .comment("The entire ship must be strictly above this Y. Overworld only.",
                        "The world build height plus a collision margin is also respected.")
                .translation("cruising.configuration.minAltitude").defineInRange("minAltitude", 356, -2032, 2032);
        MAX_DESCENT_SPEED = builder
                .comment("Restore normal terrain loading when the connected assembly descends faster than this speed in blocks/second.",
                        "Slower descent remains eligible for high-altitude flight; the hull height safety check still applies.")
                .translation("cruising.configuration.maxDescentSpeed").defineInRange("maxDescentSpeed", 40.0, 0.0, 10000.0);
        builder.pop();
        SPEC = builder.build();
    }

    private FlightConfig() {
    }
}
