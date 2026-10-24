package com.broadcast.blockentity;

import com.broadcast.menu.SpeakerMenu;
import com.broadcast.registry.ModBlockEntities;
import com.broadcast.util.FrequencyUtil;
import com.broadcast.world.BroadcastRegistryData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 广播器方块实体(三种外观共用)。
 * 只保存自己的频率;广播站/话筒按频率找到它。
 */
public class SpeakerBlockEntity extends BlockEntity implements MenuProvider {

    private String frequency = FrequencyUtil.DEFAULT;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER.get(), pos, state);
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String raw) {
        String f = FrequencyUtil.normalizeOrDefault(raw);
        if (!f.equals(frequency)) {
            frequency = f;
            setChanged();
        }
    }

    /** 持有该频率的广播站坐标。 */
    public List<BlockPos> getStations() {
        if (level instanceof ServerLevel sl) {
            return BroadcastRegistryData.get(sl).stationsFor(sl, frequency);
        }
        return List.of();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("frequency", frequency);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        frequency = FrequencyUtil.normalizeOrDefault(tag.getString("frequency"));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && level instanceof ServerLevel sl) {
            BroadcastRegistryData.get(sl).addSpeaker(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && level instanceof ServerLevel sl) {
            BroadcastRegistryData.get(sl).removeSpeaker(worldPosition);
        }
        super.setRemoved();
    }

    // ---------- 菜单 ----------

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new SpeakerMenu(containerId, inv, worldPosition);
    }
}
