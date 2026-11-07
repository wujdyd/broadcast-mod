package com.broadcast.media.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "broadcast_media_support", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MediaSupportClient {
    public static final KeyMapping OPEN = new KeyMapping(
            "key.broadcast_media_support.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9, "key.categories.misc");

    private MediaSupportClient() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }
}
