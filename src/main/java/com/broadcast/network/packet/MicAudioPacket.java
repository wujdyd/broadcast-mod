package com.broadcast.network.packet;

import com.broadcast.client.audio.ClientAudioManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 话筒 PCM 音频数据块(16kHz 单声道 16bit)。 */
public record MicAudioPacket(BlockPos mic, byte[] pcm) {

    public static void encode(MicAudioPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.mic);
        buf.writeByteArray(p.pcm);
    }

    public static MicAudioPacket decode(FriendlyByteBuf buf) {
        return new MicAudioPacket(buf.readBlockPos(), buf.readByteArray(65536));
    }

    public static void handle(MicAudioPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> ClientAudioManager.feedMic(p.mic, p.pcm));
        ctxSup.get().setPacketHandled(true);
    }
}
