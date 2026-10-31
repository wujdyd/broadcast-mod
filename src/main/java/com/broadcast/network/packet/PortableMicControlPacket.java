package com.broadcast.network.packet;

import com.broadcast.client.PortableMicController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端通知便携话筒的所有者开始/停止采集。 */
public record PortableMicControlPacket(boolean active) {

    public static void encode(PortableMicControlPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.active);
    }

    public static PortableMicControlPacket decode(FriendlyByteBuf buf) {
        return new PortableMicControlPacket(buf.readBoolean());
    }

    public static void handle(PortableMicControlPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> {
            if (p.active) PortableMicController.start();
            else PortableMicController.stop();
        });
        ctxSup.get().setPacketHandled(true);
    }
}
