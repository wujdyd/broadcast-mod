package com.broadcast.client;

import com.broadcast.BroadcastMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 播放时的音频字幕(HUD 底部)。 */
@Mod.EventBusSubscriber(modid = BroadcastMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AudioSubtitleOverlay {
    private AudioSubtitleOverlay() {
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("broadcast_audio_subtitle", (gui, graphics, partialTick, width, height) ->
                render(graphics, width, height));
    }

    private static void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) return;
        Component text;
        String music = ClientDataHolder.playingSubtitle();
        if (music != null) {
            text = Component.translatable("gui.broadcast.subtitle.playing", music);
        } else if (ClientDataHolder.anyMicActive()) {
            text = Component.translatable("gui.broadcast.subtitle.mic");
        } else {
            return;
        }
        int w = mc.font.width(text);
        int x = (width - w) / 2;
        int y = height - 58;
        g.fill(x - 6, y - 4, x + w + 6, y + 12, 0x99000000);
        g.drawString(mc.font, text, x, y, 0xFFFFFFFF, true);
    }
}
