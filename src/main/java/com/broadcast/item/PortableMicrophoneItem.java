package com.broadcast.item;

import com.broadcast.menu.PortableMicMenu;
import com.broadcast.server.PortableMicManager;
import com.broadcast.util.FrequencyUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * 便携式麦克风:右键开始/停止讲话,Shift+右键打开独立 GUI。无耐久。
 */
public class PortableMicrophoneItem extends Item {
    public static final String TAG_FREQUENCY = "frequency";
    public static final String TAG_PAUSE_MUSIC = "pauseMusic";

    public PortableMicrophoneItem(Properties properties) {
        super(properties);
    }

    public static String getFrequency(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(TAG_FREQUENCY)) {
            return FrequencyUtil.normalizeOrDefault(stack.getTag().getString(TAG_FREQUENCY));
        }
        return FrequencyUtil.DEFAULT;
    }

    public static void setFrequency(ItemStack stack, String frequency) {
        stack.getOrCreateTag().putString(TAG_FREQUENCY, FrequencyUtil.normalizeOrDefault(frequency));
    }

    public static boolean isPauseMusic(ItemStack stack) {
        return !stack.hasTag() || !stack.getTag().contains(TAG_PAUSE_MUSIC)
                || stack.getTag().getBoolean(TAG_PAUSE_MUSIC);
    }

    public static void setPauseMusic(ItemStack stack, boolean pause) {
        stack.getOrCreateTag().putBoolean(TAG_PAUSE_MUSIC, pause);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return new net.minecraft.world.InteractionResultHolder<>(handleUse(level, player, hand), stack);
    }

    private InteractionResult handleUse(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            boolean offhand = hand == InteractionHand.OFF_HAND;
            NetworkHooks.openScreen(sp, new SimpleMenuProvider(
                    (id, inv, p) -> new PortableMicMenu(id, inv, offhand),
                    Component.translatable("item.broadcast.portable_microphone")),
                    buf -> buf.writeBoolean(offhand));
            return InteractionResult.CONSUME;
        }
        PortableMicManager.toggle(sp, hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return handleUse(context.getLevel(), context.getPlayer(), context.getHand());
    }
}
