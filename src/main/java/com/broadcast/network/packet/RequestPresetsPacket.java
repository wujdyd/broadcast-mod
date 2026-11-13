package com.broadcast.network.packet;

import com.broadcast.server.PresetManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 请求预设名称列表。 */
public record RequestPresetsPacket() {
    public static void encode(RequestPresetsPacket p, FriendlyByteBuf buf) {
    }

    public static RequestPresetsPacket decode(FriendlyByteBuf buf) {
        return new RequestPresetsPacket();
    }

    public static void handle(RequestPresetsPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> {
            ServerPlayer player = ctxSup.get().getSender();
            if (player == null) return;
            List<String> names = new ArrayList<>();
            for (PresetManager.Preset preset : PresetManager.all()) names.add(preset.name());
            com.broadcast.network.NetworkHandler.sendToPlayer(player, new PresetListPacket(names));
        });
        ctxSup.get().setPacketHandled(true);
    }
}
