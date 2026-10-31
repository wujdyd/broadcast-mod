package com.broadcast.blockentity;

import com.broadcast.block.IndicatorLightBlock;
import com.broadcast.registry.ModBlockEntities;
import com.broadcast.world.BroadcastRegistryData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Set;

/** 连接式指示灯:只响应被调试器连接的那一台广播站/广播器/话筒。 */
public class IndicatorLightBlockEntity extends BlockEntity {
    private int tick;
    @Nullable
    private BlockPos linkedPos;

    public IndicatorLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INDICATOR.get(), pos, state);
    }

    @Nullable
    public BlockPos getLinkedPos() {
        return linkedPos;
    }

    public void setLinkedPos(@Nullable BlockPos pos) {
        linkedPos = pos == null ? null : pos.immutable();
        setChanged();
        refreshLit();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel)) return;
        if (++tick < 10) return;
        tick = 0;
        refreshLit();
    }

    private void refreshLit() {
        boolean lit = isLinkedActive();
        BlockState state = getBlockState();
        if (state.hasProperty(IndicatorLightBlock.LIT)
                && state.getValue(IndicatorLightBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(IndicatorLightBlock.LIT, lit), 3);
        }
    }

    private boolean isLinkedActive() {
        if (!(level instanceof ServerLevel sl) || linkedPos == null) return false;
        if (!sl.isLoaded(linkedPos)) return false;
        BlockEntity be = sl.getBlockEntity(linkedPos);
        if (be == null) {
            linkedPos = null;
            setChanged();
            return false;
        }
        if (be instanceof MicrophoneBlockEntity mic) {
            return mic.isActive();
        }
        if (be instanceof BroadcastStationBlockEntity station) {
            return station.isPlaying() || hasActiveMic(sl, station.getFrequencies());
        }
        if (be instanceof SpeakerBlockEntity speaker) {
            if (speaker.isMuted()) return false;
            String freq = speaker.getFrequency();
            if (hasActiveMic(sl, Set.of(freq))) return true;
            for (BlockPos stationPos : BroadcastRegistryData.get(sl).stationsFor(sl, freq)) {
                if (sl.getBlockEntity(stationPos) instanceof BroadcastStationBlockEntity station
                        && station.isPlaying()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasActiveMic(ServerLevel sl, Set<String> frequencies) {
        for (BlockPos pos : BroadcastRegistryData.get(sl).getMicrophones()) {
            if (!sl.isLoaded(pos)) continue;
            if (sl.getBlockEntity(pos) instanceof MicrophoneBlockEntity mic
                    && mic.isActive() && frequencies.contains(mic.getFrequency())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (linkedPos != null) tag.putLong("linked", linkedPos.asLong());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        linkedPos = tag.contains("linked") ? BlockPos.of(tag.getLong("linked")) : null;
    }
}
