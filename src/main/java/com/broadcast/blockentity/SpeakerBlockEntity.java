package com.broadcast.blockentity;

import com.broadcast.menu.SpeakerMenu;
import com.broadcast.registry.ModBlockEntities;
import com.broadcast.util.FrequencyUtil;
import com.broadcast.world.BroadcastRegistryData;
import com.broadcast.world.InterferenceUtil;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 广播器方块实体(三种外观共用)。
 * 只保存自己的频率;广播站/话筒按频率找到它。
 */
public class SpeakerBlockEntity extends BlockEntity implements MenuProvider {

    private String frequency = FrequencyUtil.DEFAULT;
    @Nullable
    private UUID owner;
    private int ownerLevel;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER.get(), pos, state);
    }

    public void setOwner(@Nullable Player player, int permissionLevel) {
        owner = player == null ? null : player.getUUID();
        ownerLevel = Math.max(0, permissionLevel);
        setChanged();
        notifyStations();
    }

    public int getOwnerLevel() {
        return ownerLevel;
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public boolean isMuted() {
        return level instanceof ServerLevel sl && InterferenceUtil.isMuted(sl, worldPosition);
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String raw) {
        String f = FrequencyUtil.normalizeOrDefault(raw);
        if (!f.equals(frequency)) {
            frequency = f;
            setChanged();
            notifyStations();
        }
    }

    /** 持有该频率的广播站坐标。 */
    public List<BlockPos> getStations() {
        if (level instanceof ServerLevel sl) {
            return BroadcastRegistryData.get(sl).stationsFor(sl, frequency);
        }
        return List.of();
    }

    /** 广播器增删/改频率后,让同频广播站/话筒立即刷新输出列表。 */
    public void notifyStations() {
        if (!(level instanceof ServerLevel sl)) return;
        BroadcastRegistryData reg = BroadcastRegistryData.get(sl);
        for (BlockPos stationPos : new ArrayList<>(reg.stationsFor(sl, frequency))) {
            if (sl.getBlockEntity(stationPos) instanceof BroadcastStationBlockEntity station) {
                station.broadcastState();
                station.broadcastInfo();
            }
        }
        for (BlockPos micPos : new ArrayList<>(reg.microphonesFor(sl, frequency))) {
            if (sl.getBlockEntity(micPos) instanceof MicrophoneBlockEntity mic && mic.isActive()) {
                mic.broadcastState();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("frequency", frequency);
        tag.putInt("ownerLevel", ownerLevel);
        if (owner != null) tag.putUUID("owner", owner);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        frequency = FrequencyUtil.normalizeOrDefault(tag.getString("frequency"));
        ownerLevel = tag.getInt("ownerLevel");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && level instanceof ServerLevel sl) {
            BroadcastRegistryData.get(sl).addSpeaker(worldPosition);
            notifyStations();
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && level instanceof ServerLevel sl) {
            BroadcastRegistryData.get(sl).removeSpeaker(worldPosition);
            notifyStations();
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
