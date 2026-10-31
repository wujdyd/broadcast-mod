package com.broadcast.server;

import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.item.PortableMicrophoneItem;
import com.broadcast.network.NetworkHandler;
import com.broadcast.network.packet.MicAudioPacket;
import com.broadcast.network.packet.MicStatePacket;
import com.broadcast.network.packet.PortableMicControlPacket;
import com.broadcast.util.FrequencyUtil;
import com.broadcast.world.BroadcastRegistryData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 便携式麦克风的服务端会话:管理谁能采集、出声广播器、暂停音乐。 */
public final class PortableMicManager {
    private static final Map<UUID, Session> ACTIVE = new ConcurrentHashMap<>();

    private PortableMicManager() {
    }

    private static final class Session {
        final ServerLevel level;
        final InteractionHand hand;
        final BlockPos key;
        final String frequency;
        final List<BlockPos> speakers;
        final List<BlockPos> pausedStations;
        final int volume;

        Session(ServerLevel level, InteractionHand hand, BlockPos key, String frequency,
                List<BlockPos> speakers, List<BlockPos> pausedStations, int volume) {
            this.level = level;
            this.hand = hand;
            this.key = key;
            this.frequency = frequency;
            this.speakers = speakers;
            this.pausedStations = pausedStations;
            this.volume = volume;
        }
    }

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void toggle(ServerPlayer player, InteractionHand hand) {
        if (ACTIVE.containsKey(player.getUUID())) {
            stop(player);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "msg.broadcast.portable_stop"), true);
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof PortableMicrophoneItem)) return;
        if (!(player.level() instanceof ServerLevel sl)) return;

        String freq = PortableMicrophoneItem.getFrequency(stack);
        List<BlockPos> speakers = new ArrayList<>();
        // 便携式话筒:广播器附近 300 格内均可使用
        double radius = 300.0D;
        double r2 = radius * radius;
        for (BlockPos pos : BroadcastRegistryData.get(sl).speakersFor(sl, java.util.Set.of(freq))) {
            if (sl.getBlockEntity(pos) instanceof com.broadcast.blockentity.SpeakerBlockEntity speaker
                    && speaker.isMuted()) {
                continue;
            }
            if (player.position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= r2) {
                speakers.add(pos);
            }
        }
        if (speakers.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "msg.broadcast.no_speaker_mic"), true);
            return;
        }

        List<BlockPos> paused = new ArrayList<>();
        int volume = 80;
        for (BlockPos stationPos : BroadcastRegistryData.get(sl).stationsFor(sl, freq)) {
            if (sl.getBlockEntity(stationPos) instanceof BroadcastStationBlockEntity station) {
                volume = Math.round(station.getVolume() * 100);
                if (PortableMicrophoneItem.isPauseMusic(stack)) {
                    station.suspendForMic();
                    paused.add(stationPos);
                }
                break;
            }
        }

        // 使用不会与世界方块坐标冲突的合成 key(客户端只用它作为音源组标识)
        BlockPos key = new BlockPos(0, 1_000_000 + player.getId(), 0);
        Session session = new Session(sl, hand, key, freq, speakers, paused, volume);
        ACTIVE.put(player.getUUID(), session);
        NetworkHandler.sendToPlayer(player, new PortableMicControlPacket(true));
        NetworkHandler.sendToLevel(sl, new MicStatePacket(key, true, null, volume, speakers));
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "msg.broadcast.portable_start", freq), true);
    }

    public static void stop(ServerPlayer player) {
        Session session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        NetworkHandler.sendToPlayer(player, new PortableMicControlPacket(false));
        NetworkHandler.sendToLevel(session.level, new MicStatePacket(session.key, false, null,
                session.volume, session.speakers));
        for (BlockPos pos : session.pausedStations) {
            if (session.level.getBlockEntity(pos) instanceof BroadcastStationBlockEntity station) {
                station.resumeAfterMic();
            }
        }
    }

    public static void onAudio(ServerPlayer player, byte[] pcm) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        // 物品被丢掉/换手时立即停止
        if (!(player.getItemInHand(session.hand).getItem() instanceof PortableMicrophoneItem)) {
            stop(player);
            return;
        }
        if (pcm == null || pcm.length == 0) {
            stop(player);
            return;
        }
        NetworkHandler.sendToLevel(session.level, new MicAudioPacket(session.key, pcm));
    }

    public static void onLogout(ServerPlayer player) {
        stop(player);
    }

    public static void onDimensionChange(ServerPlayer player) {
        stop(player);
    }

    public static void stopAll() {
        ACTIVE.clear();
    }
}
