package io.github.astelev.cruising.compat.dh.profile;
@net.neoforged.fml.common.EventBusSubscriber(modid="cruising")
public final class ProfileEvents {
    @net.neoforged.bus.api.SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event){GenerationProfile.report(true);}
    private ProfileEvents(){}
}
