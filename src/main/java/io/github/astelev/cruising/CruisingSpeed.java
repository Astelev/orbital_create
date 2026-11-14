package io.github.astelev.cruising;

import com.mojang.logging.LogUtils;
import io.github.astelev.cruising.compat.dh.DhFlightConfig;
import io.github.astelev.cruising.flight.FlightConfig;
import io.github.astelev.cruising.network.TemporaryViewPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/** Rendering and flight support only: no blocks, items, or creative tabs. */
@Mod(CruisingSpeed.MODID)
public final class CruisingSpeed {
    public static final String MODID="cruising";
    public static final Logger LOGGER=LogUtils.getLogger();

    public CruisingSpeed(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(TemporaryViewPayload.Registration::register);
        modContainer.registerConfig(ModConfig.Type.SERVER,FlightConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT,DhFlightConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON,io.github.astelev.cruising.compat.dh.progressive.ProgressiveConfig.SPEC);
        if(ModList.get().isLoaded("sable")) {
            modEventBus.addListener((net.neoforged.neoforge.event.RegisterGameTestsEvent event) ->
                    event.register(io.github.astelev.cruising.gametest.SableFlightGameTests.class));
        }
    }
}
