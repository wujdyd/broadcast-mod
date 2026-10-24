package com.broadcast.client;

import com.broadcast.BroadcastMod;
import com.broadcast.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = BroadcastMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.STATION.get(),
                    com.broadcast.client.gui.BroadcastStationScreen::new);
            MenuScreens.register(ModMenuTypes.SPEAKER.get(),
                    com.broadcast.client.gui.SpeakerScreen::new);
            MenuScreens.register(ModMenuTypes.MICROPHONE.get(),
                    com.broadcast.client.gui.MicrophoneScreen::new);
        });
    }
}