package com.broadcast.network.packet;

import com.broadcast.client.ClientDataHolder;
import com.broadcast.client.PortableMicController;
import com.broadcast.client.audio.ClientAudioManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** /stopsound 适配:停止本客户端由本模组创建的 OpenAL 音源。 */
public record StopAllAudioPacket() {

    public static void encode(StopAllAudioPacket p, FriendlyByteBuf buf) {
    }

    public static StopAllAudioPacket decode(FriendlyByteBuf buf) {
        return new StopAllAudioPacket();
    }

    public static void handle(StopAllAudioPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> {
            ClientAudioManager.stopAll();
            ClientDataHolder.clearAudioState();
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("msg.broadcast.sound_stopped"), true);
            }
        });
        ctxSup.get().setPacketHandled(true);
    }
}
