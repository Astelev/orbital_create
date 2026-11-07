package io.github.astelev.cruising.compat.dh.profile;
@net.neoforged.fml.common.EventBusSubscriber(modid="cruising",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class ProfileClientEvents {
    @net.neoforged.bus.api.SubscribeEvent public static void disconnected(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){GenerationProfile.report(true);}
    private ProfileClientEvents(){}
}
