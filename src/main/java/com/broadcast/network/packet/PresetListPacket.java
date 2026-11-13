package com.broadcast.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 服务端 → 客户端:预设名称列表。 */
public record PresetListPacket(List<String> names) {
    public static void encode(PresetListPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.names.size());
        for (String name : p.names) buf.writeUtf(name, 64);
    }

    public static PresetListPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readInt(), 256);
        List<String> names = new ArrayList<>(n);
        for (int i = 0; i < n; i++) names.add(buf.readUtf(64));
        return new PresetListPacket(names);
    }

    public static void handle(PresetListPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> com.broadcast.client.ClientDataHolder.setPresets(p.names));
        ctxSup.get().setPacketHandled(true);
    }
}
