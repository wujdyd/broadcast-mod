package com.broadcast.world;

import com.broadcast.block.BroadcastStationBlock;
import com.broadcast.block.MicrophoneBlock;
import com.broadcast.block.SpeakerBlock;
import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.blockentity.MicrophoneBlockEntity;
import com.broadcast.blockentity.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 每个维度一份的设备注册表:广播站、广播器、话筒的位置。
 * 连接关系由「频率」决定:广播站持有若干频率,广播器/话筒各自设置一个频率。
 */
public class BroadcastRegistryData extends SavedData {
    public static final String NAME = "broadcast_registry";

    private final Set<BlockPos> stations = new HashSet<>();
    private final Set<BlockPos> speakers = new HashSet<>();
    private final Set<BlockPos> microphones = new HashSet<>();

    public static BroadcastRegistryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                BroadcastRegistryData::load, BroadcastRegistryData::new, NAME);
    }

    private static BroadcastRegistryData load(CompoundTag tag) {
        BroadcastRegistryData data = new BroadcastRegistryData();
        data.stations.addAll(readPositions(tag, "stations"));
        data.speakers.addAll(readPositions(tag, "speakers"));
        data.microphones.addAll(readPositions(tag, "microphones"));
        return data;
    }

    private static List<BlockPos> readPositions(CompoundTag tag, String key) {
        List<BlockPos> out = new ArrayList<>();
        if (tag.contains(key, Tag.TAG_LONG_ARRAY)) {
            for (long l : tag.getLongArray(key)) {
                out.add(BlockPos.of(l));
            }
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("stations", toArray(stations));
        tag.put("speakers", toArray(speakers));
        tag.put("microphones", toArray(microphones));
        return tag;
    }

    private static LongArrayTag toArray(Set<BlockPos> set) {
        long[] arr = new long[set.size()];
        int i = 0;
        for (BlockPos p : set) {
            arr[i++] = p.asLong();
        }
        return new LongArrayTag(arr);
    }

    public void addStation(BlockPos pos) {
        if (stations.add(pos.immutable())) setDirty();
    }

    public void removeStation(BlockPos pos) {
        if (stations.remove(pos)) setDirty();
    }

    public void addSpeaker(BlockPos pos) {
        if (speakers.add(pos.immutable())) setDirty();
    }

    public void removeSpeaker(BlockPos pos) {
        if (speakers.remove(pos)) setDirty();
    }

    public void addMicrophone(BlockPos pos) {
        if (microphones.add(pos.immutable())) setDirty();
    }

    public void removeMicrophone(BlockPos pos) {
        if (microphones.remove(pos)) setDirty();
    }

    public Set<BlockPos> getStations() {
        return stations;
    }

    public Set<BlockPos> getMicrophones() {
        return microphones;
    }

    public Set<BlockPos> getSpeakers() {
        return speakers;
    }

    // ---------- 按频率检索 ----------

    /** 所有频率命中给定集合的广播器(会顺手清理失效的注册项)。 */
    public List<BlockPos> speakersFor(ServerLevel level, Collection<String> frequencies) {
        List<BlockPos> out = new ArrayList<>();
        if (frequencies == null || frequencies.isEmpty()) return out;
        List<BlockPos> stale = null;
        for (BlockPos p : speakers) {
            if (!level.isLoaded(p)) continue;
            if (!(level.getBlockState(p).getBlock() instanceof SpeakerBlock)) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(p);
                continue;
            }
            if (level.getBlockEntity(p) instanceof SpeakerBlockEntity be
                    && frequencies.contains(be.getFrequency())) {
                out.add(p);
            }
        }
        removeStale(speakers, stale);
        return out;
    }

    public List<BlockPos> microphonesFor(ServerLevel level, String frequency) {
        List<BlockPos> out = new ArrayList<>();
        if (frequency == null || frequency.isEmpty()) return out;
        List<BlockPos> stale = null;
        for (BlockPos p : microphones) {
            if (!level.isLoaded(p)) continue;
            if (!(level.getBlockState(p).getBlock() instanceof MicrophoneBlock)) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(p);
                continue;
            }
            if (level.getBlockEntity(p) instanceof MicrophoneBlockEntity be
                    && frequency.equals(be.getFrequency())) {
                out.add(p);
            }
        }
        removeStale(microphones, stale);
        return out;
    }

    /** 持有该频率的广播站。 */
    public List<BlockPos> stationsFor(ServerLevel level, String frequency) {
        List<BlockPos> out = new ArrayList<>();
        if (frequency == null || frequency.isEmpty()) return out;
        List<BlockPos> stale = null;
        for (BlockPos p : stations) {
            if (!level.isLoaded(p)) continue;
            if (!(level.getBlockState(p).getBlock() instanceof BroadcastStationBlock)) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(p);
                continue;
            }
            if (level.getBlockEntity(p) instanceof BroadcastStationBlockEntity be
                    && be.getFrequencies().contains(frequency)) {
                out.add(p);
            }
        }
        removeStale(stations, stale);
        return out;
    }

    private void removeStale(Set<BlockPos> set, List<BlockPos> stale) {
        if (stale != null && !stale.isEmpty()) {
            set.removeAll(stale);
            setDirty();
        }
    }
}
