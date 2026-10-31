package com.broadcast.event;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.blockentity.MicrophoneBlockEntity;
import com.broadcast.registry.ModItems;
import com.broadcast.server.IndicatorLinkManager;
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
        com.broadcast.util.FrequencyBandManager.load(event.getServer());
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
        IndicatorLinkManager.onLogout(player);
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

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && event.getServer() != null) {
            if (event.getServer().getTickCount() % 10 == 0) {
                IndicatorLinkManager.tick(event.getServer());
            }
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
        // 物品名称下方不显示额外说明文字;耐久由原版耐久条显示。
    }
}
