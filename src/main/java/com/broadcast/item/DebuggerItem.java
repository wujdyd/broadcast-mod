package com.broadcast.item;

import com.broadcast.util.Access;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;

/**
 * 调试器:右键广播站 / 广播器 / 话筒打开对应 GUI。
 * 450 点耐久,每次保存配置扣除固定点数;可通过配方合成。
 */
public class DebuggerItem extends Item {
    public static final int MAX_DURABILITY = 450;
    public static final int COST_MIC = 3;
    public static final int COST_SPEAKER = 4;
    public static final int COST_STATION = 5;

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
        if (!openFor(player, context.getClickedPos())) {
            return InteractionResult.PASS;
        }
        return InteractionResult.CONSUME;
    }

    /** 打开对应方块的 GUI;成功返回 true。 */
    public static boolean openFor(ServerPlayer player, BlockPos pos) {
        if (player == null || player.level() == null) return false;
        if (!Access.canControl(player, pos)) {
            player.displayClientMessage(Component.translatable("msg.broadcast.too_far"), true);
            return false;
        }
        BlockEntity be = player.level().getBlockEntity(pos);
        if (!(be instanceof MenuProvider provider)) {
            player.displayClientMessage(Component.translatable("msg.broadcast.debugger_invalid"), true);
            return false;
        }
        NetworkHooks.openScreen(player, provider, buf -> buf.writeBlockPos(pos));
        return true;
    }

    /** 查找玩家身上/背包中的调试器。 */
    public static ItemStack findDebugger(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() instanceof DebuggerItem) return main;
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.getItem() instanceof DebuggerItem) return off;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof DebuggerItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** 配置一次扣除耐久。创造模式不扣;没有调试器则拒绝。 */
    public static boolean consumeDurability(ServerPlayer player, int amount) {
        ItemStack stack = findDebugger(player);
        if (stack.isEmpty()) {
            player.displayClientMessage(Component.translatable("msg.broadcast.need_debugger")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (player.isCreative()) return true;
        int next = stack.getDamageValue() + amount;
        if (next >= stack.getMaxDamage()) {
            stack.shrink(1);
            player.displayClientMessage(Component.translatable("msg.broadcast.debugger_broken")
                    .withStyle(ChatFormatting.RED), true);
            player.broadcastBreakEvent(EquipmentSlot.MAINHAND);
            return true;
        }
        stack.setDamageValue(next);
        return true;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
