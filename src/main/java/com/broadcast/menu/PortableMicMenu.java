package com.broadcast.menu;

import com.broadcast.item.PortableMicrophoneItem;
import com.broadcast.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** 便携式麦克风的独立 GUI 菜单。 */
public class PortableMicMenu extends AbstractContainerMenu {
    public final boolean offhand;

    public PortableMicMenu(int id, Inventory inv, FriendlyByteBuf extra) {
        this(id, inv, extra.readBoolean());
    }

    public PortableMicMenu(int id, Inventory inv, boolean offhand) {
        super(ModMenuTypes.PORTABLE_MIC.get(), id);
        this.offhand = offhand;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        ItemStack stack = player.getItemInHand(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        return stack.getItem() instanceof PortableMicrophoneItem;
    }
}
