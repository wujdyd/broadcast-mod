package com.broadcast.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端打开广播器/话筒 GUI 时请求设备信息。 */
public record RequestDeviceInfoPacket(BlockPos pos) {

    public static void encode(RequestDeviceInfoPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
    }

    public static RequestDeviceInfoPacket decode(FriendlyByteBuf buf) {
        return new RequestDeviceInfoPacket(buf.readBlockPos());
    }

    public static void handle(RequestDeviceInfoPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DeviceInfoPacket.sendFor(ctx.getSender(), p.pos));
        ctx.setPacketHandled(true);
    }
}
