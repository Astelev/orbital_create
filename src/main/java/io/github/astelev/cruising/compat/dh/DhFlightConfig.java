package io.github.astelev.cruising.compat.dh;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DhFlightConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED, MODIFY_DH, SMOOTH_TERRAIN;
    public static final ModConfigSpec.IntValue ADAPT_ABOVE_Y;
    public static final ModConfigSpec.IntValue COARSE_DETAIL;
    public static final ModConfigSpec.DoubleValue FAST_SPEED;
    public static final ModConfigSpec.IntValue QUIET_SECONDS;
    public static final ModConfigSpec.IntValue REFINE_SECONDS;
    public static final ModConfigSpec.BooleanValue DIAGNOSTICS;
    public static final ModConfigSpec.BooleanValue FALLBACK_PLANE;
    public static final ModConfigSpec.IntValue FALLBACK_HEIGHT;
    public static final ModConfigSpec.IntValue FALLBACK_COLOR;
    public static final ModConfigSpec.DoubleValue FALLBACK_BRIGHTNESS;
    public static final ModConfigSpec.BooleanValue FALLBACK_BIOMES;
    public static final ModConfigSpec.BooleanValue NEARBY_PRIORITY, FORWARD_PRIORITY, FALLBACK_HEIGHTS;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("distantHorizonsFlight");
        ENABLED = builder.comment("Enable DH 3.3.3 integration. The biome surface also works near the ground; camera-height flight adaptations are separately configurable.",
                "Allows generation during fast flight without disabling DH's server-load or queue safeguards.")
                .translation("cruising.configuration.enabled").define("enabled", true);
        MODIFY_DH = builder.comment("Allow progressive generation, fast-motion pause bypass, and generation/retrieval/mesh priority changes. Existing render detail is preserved.",
                "Disable to keep native DH behavior while retaining the independent biome surface.")
                .translation("cruising.configuration.modifyDistantHorizonsBehavior").define("modifyDistantHorizonsBehavior",true);
        ADAPT_ABOVE_Y = builder.comment("Camera Y above which DH flight adaptations activate without Sable, including creative/elytra flight.")
                .translation("cruising.configuration.adaptAboveY").defineInRange("adaptAboveY",356,-2032,2032);
        SMOOTH_TERRAIN = builder.comment("Smooth neighboring biome height samples. Disable for flat stepped tiles with closed sides.")
                .translation("cruising.configuration.smoothFallbackTerrain").define("smoothFallbackTerrain",true);
        COARSE_DETAIL = builder.comment("Legacy setting, retained for configuration compatibility; no longer reduces render detail.",
                "Configure generation preview in cruising-common.toml, dhProgressive instead.").translation("cruising.configuration.coarseDetail").defineInRange("coarseDetail", 4, 1, 6);
        FAST_SPEED = builder.comment("Legacy coarse-mode threshold, no longer used to reduce render detail.")
                .translation("cruising.configuration.fastSpeed").defineInRange("fastSpeed", 20.0, 1.0, 1000000.0);
        QUIET_SECONDS = builder.comment("Legacy render-refinement delay; no longer used.")
                .translation("cruising.configuration.quietSeconds").defineInRange("quietSeconds", 5, 0, 300);
        REFINE_SECONDS = builder.comment("Legacy render-refinement interval; no longer used.")
                .translation("cruising.configuration.refineSeconds").defineInRange("refineSeconds", 10, 1, 300);
        DIAGNOSTICS = builder.comment("Show the flight LOD mode and DH's generation gate in F3.",
                "Also log a short status at most once every 30 seconds during sky flight.")
                .translation("cruising.configuration.diagnostics").define("diagnostics", true);
        FALLBACK_PLANE = builder.comment("Draw a temporary biome surface in the DH terrain target, before native fog/compositing.")
                .translation("cruising.configuration.fallbackPlane").define("fallbackPlane", true);
        FALLBACK_HEIGHT = builder.translation("cruising.configuration.fallbackHeight").defineInRange("fallbackHeight", 60, -2032, 2032);
        FALLBACK_COLOR = builder.comment("RGB color used until the budgeted biome sampler has calculated a local surface color.")
                .translation("cruising.configuration.fallbackColor").defineInRange("fallbackColor", 0x718a54, 0, 0xffffff);
        FALLBACK_BRIGHTNESS = builder.comment("Surface tint brightness before lighting. Lower values better match textured terrain.")
                .translation("cruising.configuration.fallbackBrightness").defineInRange("fallbackBrightness", 0.55, 0.0, 1.0);
        FALLBACK_BIOMES = builder.comment("Sample surface biomes independently of DH terrain work in a bounded 64-block grid. No chunks are generated.")
                .translation("cruising.configuration.fallbackBiomeColors").define("fallbackBiomeColors", true);
        NEARBY_PRIORITY = builder.comment("During sky flight, rank waiting DH generation tasks by distance to their bounds, nearest first.")
                .translation("cruising.configuration.nearbyGenerationPriority").define("nearbyGenerationPriority", true);
        FORWARD_PRIORITY = builder.comment("Prefer LOD generation, retrieval and mesh requests along camera travel; keep the nearest 512 blocks first.")
                .translation("cruising.configuration.forwardGenerationPriority").define("forwardGenerationPriority", true);
        FALLBACK_HEIGHTS = builder.comment("Use approximate biome height offsets from a static table. No terrain generation or height queries.")
                .translation("cruising.configuration.fallbackBiomeHeights").define("fallbackBiomeHeights", true);
        builder.pop();
        SPEC = builder.build();
    }

    private DhFlightConfig() {}
}
