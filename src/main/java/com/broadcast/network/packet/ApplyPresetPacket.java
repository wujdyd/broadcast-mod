package com.broadcast.network.packet;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.server.PresetManager;
import com.broadcast.util.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 将预设应用到指定广播站。 */
public record ApplyPresetPacket(BlockPos station, String name) {
    public static void encode(ApplyPresetPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.station);
        buf.writeUtf(p.name, 64);
    }

    public static ApplyPresetPacket decode(FriendlyByteBuf buf) {
        return new ApplyPresetPacket(buf.readBlockPos(), buf.readUtf(64));
    }

    public static void handle(ApplyPresetPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> {
            ServerPlayer player = ctxSup.get().getSender();
            if (player == null || player.level() == null) return;
            if (!Access.canControl(player, p.station)) return;
            if (!(player.level().getBlockEntity(p.station) instanceof BroadcastStationBlockEntity station)) return;
            if (!station.canAccess(player)) return;
            PresetManager.Preset preset = PresetManager.get(p.name);
            if (preset == null) return;
            station.startPreset(preset.tracks(), preset.loop(), preset.order());
        });
        ctxSup.get().setPacketHandled(true);
    }
}
