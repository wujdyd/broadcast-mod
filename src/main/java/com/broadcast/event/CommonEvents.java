package com.broadcast.event;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.blockentity.MicrophoneBlockEntity;
import com.broadcast.registry.ModItems;
import com.broadcast.server.MicCapture;
import com.broadcast.server.MusicLibrary;
import com.broadcast.server.PortableMicManager;
import com.broadcast.server.UploadHttpServer;
import com.broadcast.world.BroadcastRegistryData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;

public class CommonEvents {
    private static final ResourceLocation[] RECIPE_IDS = new ResourceLocation[]{
            new ResourceLocation("broadcast", "station"),
            new ResourceLocation("broadcast", "wall_speaker"),
            new ResourceLocation("broadcast", "ceiling_speaker"),
            new ResourceLocation("broadcast", "floor_speaker"),
            new ResourceLocation("broadcast", "column_speaker"),
            new ResourceLocation("broadcast", "microphone"),
            new ResourceLocation("broadcast", "indicator_light"),
            new ResourceLocation("broadcast", "debugger"),
            new ResourceLocation("broadcast", "portable_microphone")
    };

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MusicLibrary.get().reload();
        UploadHttpServer.start(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MicCapture.stopAll();
        PortableMicManager.stopAll();
        UploadHttpServer.stop();
    }

    /** 采集话筒的玩家下线时,立刻停掉他客户端的话筒广播,避免状态卡在「广播中」。 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PortableMicManager.onLogout(player);
        if (!(player.level() instanceof ServerLevel level)) return;
        for (BlockPos pos : new ArrayList<>(BroadcastRegistryData.get(level).getMicrophones())) {
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof MicrophoneBlockEntity mic) {
                if (player.getUUID().equals(mic.getOwner()) && mic.isActive()) {
                    mic.forceStop();
                }
            }
        }
    }

    /** 玩家进入服务器/切换维度时,补发状态并解锁配方书。 */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.awardRecipesByKey(RECIPE_IDS);
            syncStates(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PortableMicManager.onDimensionChange(player);
            syncStates(player);
        }
    }

    private static void syncStates(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        BroadcastRegistryData reg = BroadcastRegistryData.get(level);
        for (BlockPos pos : new ArrayList<>(reg.getStations())) {
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof BroadcastStationBlockEntity be && be.isPlaying()) {
                be.sendStateTo(player);
            }
        }
        for (BlockPos pos : new ArrayList<>(reg.getMicrophones())) {
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof MicrophoneBlockEntity mic && mic.isActive()) {
                mic.sendStateTo(player);
            }
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        var item = event.getItemStack().getItem();
        if (item == ModItems.STATION.get()) {
            event.getToolTip().add(Component.translatable("tooltip.broadcast.station").withStyle(ChatFormatting.GRAY));
        } else if (item == ModItems.WALL_SPEAKER.get()) {
            event.getToolTip().add(Component.translatable("tooltip.broadcast.wall_speaker").withStyle(ChatFormatting.GRAY));
        } else if (item == ModItems.CEILING_SPEAKER.get()) {
            event.getToolTip().add(Component.translatable("tooltip.broadcast.ceiling_speaker").withStyle(ChatFormatting.GRAY));
        } else if (item == ModItems.FLOOR_SPEAKER.get()) {
            event.getToolTip().add(Component.translatable("tooltip.broadcast.floor_speaker").withStyle(ChatFormatting.GRAY));
        } else if (item == ModItems.COLUMN_SPEAKER.get()) {
            event.getToolTip().add(Component.translatable("tooltip.broadcast.column_speaker").withStyle(ChatFormatting.GRAY));
        } else if (item == ModItems.INDICATOR_LIGHT.get()) {
            event.getToolTip().add(Component.translatable("tooltip.broadcast.indicator_light").withStyle(ChatFormatting.GRAY));
        } else if (item == ModItems.MICROPHONE.get()) {
            event.getToolTip().add(Component.translatable("tooltip.broadcast.microphone").withStyle(ChatFormatting.GRAY));
        } else if (item == ModItems.DEBUGGER.get()) {
            event.getToolTip().add(Component.translatable("tooltip.broadcast.debugger").withStyle(ChatFormatting.GRAY));
            var stack = event.getItemStack();
            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            event.getToolTip().add(Component.translatable("tooltip.broadcast.debugger_durability",
                    remaining, stack.getMaxDamage()).withStyle(ChatFormatting.DARK_AQUA));
        } else if (item == ModItems.PORTABLE_MICROPHONE.get()) {
            event.getToolTip().add(Component.translatable("tooltip.broadcast.portable_microphone")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
