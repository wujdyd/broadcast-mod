package com.broadcast.network.packet;

import com.broadcast.item.PortableMicrophoneItem;
import com.broadcast.util.FrequencyUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 保存便携话筒 GUI 设置(频率、讲话暂停音乐)。 */
public record SetPortableMicSettingsPacket(boolean offhand, String frequency, boolean pauseMusic) {

    public static void encode(SetPortableMicSettingsPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.offhand);
        buf.writeUtf(p.frequency, 32);
        buf.writeBoolean(p.pauseMusic);
    }

    public static SetPortableMicSettingsPacket decode(FriendlyByteBuf buf) {
        return new SetPortableMicSettingsPacket(buf.readBoolean(), buf.readUtf(32), buf.readBoolean());
    }

    public static void handle(SetPortableMicSettingsPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            InteractionHand hand = p.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof PortableMicrophoneItem)) return;
            String f = FrequencyUtil.normalize(p.frequency);
            if (f.isEmpty()) return;
            PortableMicrophoneItem.setFrequency(stack, f);
            PortableMicrophoneItem.setPauseMusic(stack, p.pauseMusic);
            player.getInventory().setChanged();
        });
        ctx.setPacketHandled(true);
    }
}
