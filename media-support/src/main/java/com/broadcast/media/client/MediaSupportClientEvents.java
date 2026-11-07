package com.broadcast.media.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge 总线事件:客户端指令与按键 tick。 */
@Mod.EventBusSubscriber(modid = "broadcast_media_support", value = Dist.CLIENT)
public final class MediaSupportClientEvents {
    private MediaSupportClientEvents() {
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
        while (MediaSupportClient.OPEN.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.setScreen(new MediaToolsScreen());
        }
    }
}
