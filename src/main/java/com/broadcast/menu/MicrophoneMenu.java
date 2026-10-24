package com.broadcast.menu;

import com.broadcast.blockentity.MicrophoneBlockEntity;
import com.broadcast.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** 话筒菜单:无物品槽,仅用于打开频率 / 红石模式设置界面。 */
public class MicrophoneMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    private final Level level;

    public MicrophoneMenu(int containerId, Inventory inv, FriendlyByteBuf extra) {
        this(containerId, inv, extra.readBlockPos());
    }

    public MicrophoneMenu(int containerId, Inventory inv, BlockPos pos) {
        super(ModMenuTypes.MICROPHONE.get(), containerId);
        this.pos = pos;
        this.level = inv.player.level();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MicrophoneBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
