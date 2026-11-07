package com.broadcast.client;

import com.broadcast.BroadcastMod;
import com.broadcast.client.audio.ClientAudioManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BroadcastMod.MODID, value = Dist.CLIENT)
public class ClientEvents {

    private static String lastSubtitle;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientAudioManager.tick();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                lastSubtitle = null;
                return;
            }
            String music = ClientDataHolder.playingSubtitle();
            String key = music != null ? "music:" + music
                    : ClientDataHolder.anyMicActive() ? "mic" : "";
            if (!key.equals(lastSubtitle)) {
                lastSubtitle = key;
                if (!key.isEmpty()) {
                    Component text = music != null
                            ? Component.translatable("gui.broadcast.subtitle.playing", music)
                            : Component.translatable("gui.broadcast.subtitle.mic");
                    // HUD 之外再显示一次动作栏字幕,保证任何分辨率/界面下都能看到
                    mc.player.displayClientMessage(text, true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAudioManager.stopAll();
        ClientMicController.stopAll();
        PortableMicController.stopAll();
        lastSubtitle = null;
    }
}