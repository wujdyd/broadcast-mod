package com.broadcast.blockentity;

import com.broadcast.menu.BroadcastStationMenu;
import com.broadcast.network.NetworkHandler;
import com.broadcast.network.packet.PlaybackStatePacket;
import com.broadcast.network.packet.StationInfoPacket;
import com.broadcast.registry.ModBlockEntities;
import com.broadcast.server.MusicLibrary;
import com.broadcast.server.UploadHttpServer;
import com.broadcast.util.FrequencyUtil;
import com.broadcast.world.BroadcastRegistryData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BroadcastStationBlockEntity extends BlockEntity implements MenuProvider {

    public static final int DATA_VOLUME = 0;
    public static final int DATA_PLAYING = 1;
    public static final int DATA_REDSTONE = 2;

    private float volume = 0.8F;
    private boolean playing;
    private String target = "";
    private boolean targetIsUrl;
    private boolean redstoneMode;
    private boolean wasPowered;

    /** 该广播站同时广播的频率(可多频率同步广播)。 */
    private final Set<String> frequencies = new LinkedHashSet<>();

    /** 播放列表(存音乐 UID)。 */
    private final List<String> playlist = new ArrayList<>();
    private boolean playlistLoop;
    private boolean playlistReverse;
    private int playlistGapSeconds = 60;
    private int playlistTrackSeconds = 600;
    private int playlistIndex;
    /** 0 = 间隔等待;1 = 正在播放列表曲目 */
    private int playlistPhase;
    private long playlistPhaseStart;
    private boolean playlistActive;

    private int micSuspend;
    private boolean micResumePending;

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_VOLUME -> Math.round(volume * 100);
                case DATA_PLAYING -> playing ? 1 : 0;
                case DATA_REDSTONE -> redstoneMode ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_VOLUME -> setVolume(value / 100F);
                case DATA_REDSTONE -> redstoneMode = value != 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public BroadcastStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STATION.get(), pos, state);
        frequencies.add(FrequencyUtil.DEFAULT);
    }

    // ---------- 状态 ----------

    public boolean isPlaying() {
        return playing;
    }

    public String getTarget() {
        return target;
    }

    public boolean isTargetUrl() {
        return targetIsUrl;
    }

    public float getVolume() {
        return volume;
    }

    public boolean isRedstoneMode() {
        return redstoneMode;
    }

    public void setVolume(float v) {
        this.volume = Math.max(0F, Math.min(1F, v));
        setChanged();
    }

    public void setRedstoneMode(boolean on) {
        this.redstoneMode = on;
        setChanged();
    }

    // ---------- 频率 ----------

    public Set<String> getFrequencies() {
        return frequencies;
    }

    public void setFrequencies(Collection<String> raw) {
        frequencies.clear();
        frequencies.addAll(FrequencyUtil.normalizeList(raw));
        setChanged();
        broadcastInfo();
        broadcastState();
    }

    public boolean addFrequency(String raw) {
        String f = FrequencyUtil.normalize(raw);
        if (f.isEmpty() || frequencies.contains(f) || frequencies.size() >= FrequencyUtil.MAX_STATION_FREQUENCIES) {
            return false;
        }
        frequencies.add(f);
        setChanged();
        broadcastInfo();
        broadcastState();
        return true;
    }

    public boolean removeFrequency(String raw) {
        String f = FrequencyUtil.normalize(raw);
        if (frequencies.size() <= 1 || !frequencies.remove(f)) return false;
        setChanged();
        broadcastInfo();
        broadcastState();
        return true;
    }

    // ---------- 播放列表 ----------

    public List<String> getPlaylist() {
        return playlist;
    }

    public boolean isPlaylistLoop() {
        return playlistLoop;
    }

    public boolean isPlaylistReverse() {
        return playlistReverse;
    }

    public int getPlaylistGapSeconds() {
        return playlistGapSeconds;
    }

    public int getPlaylistTrackSeconds() {
        return playlistTrackSeconds;
    }

    public int getPlaylistIndex() {
        return playlistIndex;
    }

    public boolean isPlaylistPlaying() {
        return playlistLoop && playlistPhase == 1 && playlistActive;
    }

    public void setPlaylist(Collection<String> raw) {
        playlist.clear();
        if (raw != null) {
            for (String s : raw) {
                String uid = MusicLibrary.sanitizeUid(s);
                if (uid != null && !playlist.contains(uid)) playlist.add(uid);
                if (playlist.size() >= 64) break;
            }
        }
        if (playlistIndex >= playlist.size()) playlistIndex = 0;
        if (playlist.isEmpty() && playlistActive) stopPlayback();
        setChanged();
        broadcastInfo();
    }

    public void setPlaylistSettings(boolean loop, boolean reverse, int gapSeconds, int trackSeconds) {
        boolean wasLoop = playlistLoop;
        playlistLoop = loop;
        playlistReverse = reverse;
        playlistGapSeconds = Math.max(0, Math.min(3600, gapSeconds));
        playlistTrackSeconds = Math.max(5, Math.min(600, trackSeconds));
        if (!loop) {
            if (playlistActive || playlistPhase == 1) stopPlayback();
            playlistPhase = 0;
            playlistPhaseStart = level == null ? 0 : level.getGameTime();
        } else if (!wasLoop) {
            // 开启后马上开始第一首
            playlistPhase = 0;
            playlistPhaseStart = level == null ? 0 : level.getGameTime() - playlistGapSeconds * 20L;
        }
        setChanged();
        broadcastInfo();
    }

    private String nextPlaylistName() {
        if (playlist.isEmpty()) return null;
        int size = playlist.size();
        for (int tries = 0; tries < size; tries++) {
            if (playlistIndex < 0 || playlistIndex >= size) playlistIndex = 0;
            String uid = playlist.get(playlistIndex);
            playlistIndex = playlistReverse
                    ? (playlistIndex - 1 + size) % size
                    : (playlistIndex + 1) % size;
            String name = MusicLibrary.get().nameFor(uid);
            if (name != null && UploadHttpServer.hasMusicFile(name)) {
                return name;
            }
        }
        return null;
    }

    // ---------- 设备(按频率) ----------

    public List<BlockPos> getSpeakers() {
        if (level instanceof ServerLevel sl) {
            List<BlockPos> all = BroadcastRegistryData.get(sl).speakersFor(sl, frequencies);
            List<BlockPos> out = new ArrayList<>(all.size());
            for (BlockPos pos : all) {
                if (sl.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker && !speaker.isMuted()) {
                    out.add(pos);
                }
            }
            return out;
        }
        return List.of();
    }

    public List<BlockPos> getMicrophones() {
        if (level instanceof ServerLevel sl) {
            Set<BlockPos> all = new LinkedHashSet<>();
            BroadcastRegistryData reg = BroadcastRegistryData.get(sl);
            for (String f : frequencies) {
                all.addAll(reg.microphonesFor(sl, f));
            }
            return new ArrayList<>(all);
        }
        return List.of();
    }

    // ---------- 播放控制 ----------

    public void startPlayback(String target, boolean isUrl) {
        startPlayback(target, isUrl, false);
    }

    private void startPlayback(String target, boolean isUrl, boolean playlistTrack) {
        this.target = target;
        this.targetIsUrl = isUrl;
        this.playing = true;
        this.playlistActive = playlistTrack;
        setChanged();
        broadcastState();
    }

    private void playPlaylistTrack(String name) {
        startPlayback(name, false, true);
    }

    /** 播放列表当前曲目自然结束:切到间隔,再由 tickPlaylist 播放下一首。 */
    public void onTrackEnded() {
        if (!playlistActive || !playing) return;
        stopPlayback();
        playlistPhase = 0;
        if (level != null) playlistPhaseStart = level.getGameTime();
        broadcastInfo();
    }

    public void stopPlayback() {
        this.playing = false;
        this.playlistActive = false;
        setChanged();
        broadcastState();
    }

    /** 话筒讲话时暂停音乐。 */
    public void suspendForMic() {
        micSuspend++;
        if (playing) {
            micResumePending = true;
            if (playlistLoop && playlistActive) {
                playlistPhase = 0;
                if (level != null) playlistPhaseStart = level.getGameTime();
            }
            stopPlayback();
        }
    }

    /** 话筒停止后恢复音乐。 */
    public void resumeAfterMic() {
        if (micSuspend > 0) micSuspend--;
        if (micSuspend != 0) return;
        if (playlistLoop) {
            // 交给播放列表计时器重新开始(避免和列表计时冲突)
            playlistPhase = 0;
            if (level != null) playlistPhaseStart = level.getGameTime();
            micResumePending = false;
            return;
        }
        if (micResumePending) {
            micResumePending = false;
            if (!target.isEmpty() && !playing) {
                startPlayback(target, targetIsUrl);
            }
        }
    }

    public boolean isMicSuspended() {
        return micSuspend > 0;
    }

    /** 广播站被破坏时调用:通知所有客户端停止该站的播放。 */
    public void onStationRemoved() {
        if (playing) {
            this.playing = false;
            broadcastState();
        }
    }

    public void broadcastState() {
        if (!(level instanceof ServerLevel sl)) return;
        PlaybackStatePacket pkt = statePacket();
        for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
            if (p.level() == sl) NetworkHandler.sendToPlayer(p, pkt);
        }
    }

    /** 当前播放状态(供 GUI 打开时同步)。 */
    public PlaybackStatePacket statePacket() {
        return new PlaybackStatePacket(worldPosition, playing, target, targetIsUrl,
                !playlistActive, Math.round(volume * 100), getSpeakers());
    }

    public StationInfoPacket infoPacket() {
        return new StationInfoPacket(worldPosition, getSpeakers(), getMicrophones(),
                new ArrayList<>(frequencies),
                UploadHttpServer.isRunning() ? com.broadcast.config.BroadcastConfig.uploadPort() : 0,
                new ArrayList<>(playlist), playlistLoop, playlistReverse,
                playlistGapSeconds, playlistTrackSeconds, playlistIndex, isPlaylistPlaying());
    }

    public void sendInfoTo(ServerPlayer player) {
        if (player != null) NetworkHandler.sendToPlayer(player, infoPacket());
    }

    public void broadcastInfo() {
        if (!(level instanceof ServerLevel sl)) return;
        StationInfoPacket pkt = infoPacket();
        for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
            if (p.level() == sl) NetworkHandler.sendToPlayer(p, pkt);
        }
    }

    public void sendStateTo(ServerPlayer player) {
        if (player == null) return;
        NetworkHandler.sendToPlayer(player, statePacket());
    }

    // ---------- 每 tick 逻辑 ----------

    public void serverTick() {
        if (level == null || level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (powered != wasPowered) {
            wasPowered = powered;
            // 播放列表开启时由 tickPlaylist 统一处理红石联动
            if (redstoneMode && !playlistLoop) {
                if (powered && !playing && !target.isEmpty()) {
                    startPlayback(target, targetIsUrl);
                } else if (!powered && playing) {
                    stopPlayback();
                }
            }
        }
        tickPlaylist();
    }

    private void tickPlaylist() {
        if (!playlistLoop) return;
        long now = level.getGameTime();
        if (playlistPhaseStart == 0) playlistPhaseStart = now;

        boolean poweredAllowed = !redstoneMode || level.hasNeighborSignal(worldPosition);
        if (!poweredAllowed || micSuspend > 0 || playlist.isEmpty()) {
            if (playing && (!poweredAllowed || playlist.isEmpty())) stopPlayback();
            playlistPhase = 0;
            playlistPhaseStart = now;
            return;
        }

        long elapsed = now - playlistPhaseStart;
        if (playlistPhase == 0) {
            if (elapsed >= playlistGapSeconds * 20L) {
                String name = nextPlaylistName();
                if (name != null) {
                    playPlaylistTrack(name);
                    playlistPhase = 1;
                    playlistPhaseStart = now;
                    broadcastInfo();
                } else {
                    playlistPhaseStart = now;
                }
            }
        } else if (elapsed >= playlistTrackSeconds * 20L) {
            stopPlayback();
            playlistPhase = 0;
            playlistPhaseStart = now;
            broadcastInfo();
        }
    }

    // ---------- 持久化 ----------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("volume", volume);
        tag.putBoolean("playing", playing);
        tag.putString("target", target);
        tag.putBoolean("targetIsUrl", targetIsUrl);
        tag.putBoolean("redstoneMode", redstoneMode);
        tag.putBoolean("playlistLoop", playlistLoop);
        tag.putBoolean("playlistReverse", playlistReverse);
        tag.putInt("playlistGap", playlistGapSeconds);
        tag.putInt("playlistTrackSeconds", playlistTrackSeconds);
        tag.putInt("playlistIndex", playlistIndex);

        ListTag freqs = new ListTag();
        for (String f : frequencies) freqs.add(StringTag.valueOf(f));
        tag.put("frequencies", freqs);

        ListTag list = new ListTag();
        for (String uid : playlist) list.add(StringTag.valueOf(uid));
        tag.put("playlist", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        volume = tag.contains("volume") ? tag.getFloat("volume") : 0.8F;
        playing = tag.getBoolean("playing");
        target = tag.getString("target");
        targetIsUrl = tag.getBoolean("targetIsUrl");
        redstoneMode = tag.getBoolean("redstoneMode");
        playlistLoop = tag.getBoolean("playlistLoop");
        playlistReverse = tag.getBoolean("playlistReverse");
        playlistGapSeconds = tag.contains("playlistGap") ? tag.getInt("playlistGap") : 60;
        playlistTrackSeconds = tag.contains("playlistTrackSeconds") ? tag.getInt("playlistTrackSeconds") : 600;
        playlistIndex = tag.getInt("playlistIndex");

        frequencies.clear();
        if (tag.contains("frequencies", 9)) {
            ListTag list = tag.getList("frequencies", 8);
            for (int i = 0; i < list.size(); i++) {
                String f = FrequencyUtil.normalize(list.getString(i));
                if (!f.isEmpty()) frequencies.add(f);
            }
        }
        if (frequencies.isEmpty()) frequencies.add(FrequencyUtil.DEFAULT);

        playlist.clear();
        if (tag.contains("playlist", 9)) {
            ListTag list = tag.getList("playlist", 8);
            for (int i = 0; i < list.size(); i++) {
                String uid = MusicLibrary.sanitizeUid(list.getString(i));
                if (uid != null && !playlist.contains(uid)) playlist.add(uid);
            }
        }
        if (playlistIndex < 0 || playlistIndex >= playlist.size()) playlistIndex = 0;
        wasPowered = false;
        playlistPhase = 0;
        playlistPhaseStart = 0;
        playlistActive = false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && level instanceof ServerLevel sl) {
            BroadcastRegistryData.get(sl).addStation(worldPosition);
            if (playing && sl.getServer() != null) {
                sl.getServer().tell(new net.minecraft.server.TickTask(
                        sl.getServer().getTickCount() + 20, this::broadcastState));
            }
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && level instanceof ServerLevel sl) {
            BroadcastRegistryData.get(sl).removeStation(worldPosition);
        }
        super.setRemoved();
    }

    // ---------- 菜单 ----------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.broadcast.station");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new BroadcastStationMenu(containerId, playerInv, worldPosition, dataAccess);
    }
}
