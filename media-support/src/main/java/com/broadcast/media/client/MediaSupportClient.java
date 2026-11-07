package com.broadcast.media.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
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

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                com.mojang.brigadier.builder.LiteralArgumentBuilder
                        .<net.minecraft.commands.CommandSourceStack>literal("broadcastmedia")
                        .executes(ctx -> {
                            Minecraft.getInstance().setScreen(new MediaToolsScreen());
                            return 1;
                        }));
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (OPEN.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.setScreen(new MediaToolsScreen());
        }
    }
}
