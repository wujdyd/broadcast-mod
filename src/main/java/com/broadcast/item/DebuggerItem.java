package com.broadcast.item;

import com.broadcast.util.Access;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;

/**
 * 调试器:右键广播站 / 广播器 / 话筒打开对应 GUI。
 * 不放入创造模式物品栏,也没有合成配方,只能通过 /? give TSQ <玩家> 获取。
 */
public class DebuggerItem extends Item {

    public DebuggerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MenuProvider provider)) {
            player.displayClientMessage(Component.translatable("msg.broadcast.debugger_invalid"), true);
            return InteractionResult.PASS;
        }
        if (!Access.canControl(player, pos)) {
            player.displayClientMessage(Component.translatable("msg.broadcast.too_far"), true);
            return InteractionResult.PASS;
        }
        NetworkHooks.openScreen(player, provider, buf -> buf.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
        return true;
    }
}
