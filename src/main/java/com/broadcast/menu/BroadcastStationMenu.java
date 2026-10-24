package com.broadcast.menu;

import com.broadcast.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.broadcast.blockentity.BroadcastStationBlockEntity;

/**
 * 广播站菜单:不承载物品,仅同步方块实体的数据(音量/播放/红石模式)。
 */
public class BroadcastStationMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 3;
    public static final int DATA_VOLUME = 0;
    public static final int DATA_PLAYING = 1;
    public static final int DATA_REDSTONE = 2;

    public final BlockPos pos;
    public final ContainerData data;
    private final Level level;

    /** 客户端构造(从网络附加数据读取坐标)。 */
    public BroadcastStationMenu(int containerId, Inventory inv, FriendlyByteBuf extra) {
        this(containerId, inv, extra.readBlockPos(), new SimpleContainerData(DATA_COUNT));
    }

    /** 服务端构造。 */
    public BroadcastStationMenu(int containerId, Inventory inv, BlockPos pos, ContainerData data) {
        super(ModMenuTypes.STATION.get(), containerId);
        this.pos = pos;
        this.level = inv.player.level();
        this.data = data;
        addDataSlots(data);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof BroadcastStationBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
