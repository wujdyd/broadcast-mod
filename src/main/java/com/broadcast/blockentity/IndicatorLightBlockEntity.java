package com.broadcast.blockentity;

import com.broadcast.block.IndicatorLightBlock;
import com.broadcast.registry.ModBlockEntities;
import com.broadcast.world.BroadcastRegistryData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 指示灯:定时检查同维度是否有正在广播的话筒或正在播放的广播站。 */
public class IndicatorLightBlockEntity extends BlockEntity {
    private int tick;

    public IndicatorLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INDICATOR.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (++tick < 10) return;
        tick = 0;
        boolean lit = isBroadcasting(sl);
        BlockState state = getBlockState();
        if (state.getValue(IndicatorLightBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(IndicatorLightBlock.LIT, lit), 3);
        }
    }

    private boolean isBroadcasting(ServerLevel sl) {
        BroadcastRegistryData reg = BroadcastRegistryData.get(sl);
        for (BlockPos pos : reg.getMicrophones()) {
            if (sl.isLoaded(pos) && sl.getBlockEntity(pos) instanceof MicrophoneBlockEntity mic
                    && mic.isActive()) {
                return true;
            }
        }
        for (BlockPos pos : reg.getStations()) {
            if (sl.isLoaded(pos) && sl.getBlockEntity(pos) instanceof BroadcastStationBlockEntity station
                    && station.isPlaying()) {
                return true;
            }
        }
        return false;
    }
}
