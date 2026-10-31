package com.broadcast.blockentity;

import com.broadcast.config.BroadcastConfig;
import com.broadcast.menu.MicrophoneMenu;
import com.broadcast.network.NetworkHandler;
import com.broadcast.network.packet.MicCaptureControlPacket;
import com.broadcast.network.packet.MicStatePacket;
import com.broadcast.registry.ModBlockEntities;
import com.broadcast.server.MicCapture;
import com.broadcast.util.FrequencyUtil;
import com.broadcast.world.BroadcastRegistryData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 话筒方块实体。
 * 频率决定它向哪些广播器讲话;红石控制模式可在话筒 GUI 里切换:
 *   SELF   = 话筒自身通电即可讲话(默认)
 *   ALL    = 话筒与同频率的所有广播器都通电才能讲话
 *   ALWAYS = 不需要红石,放置后常开(调试/特殊场景)
 * 还可设置「讲话时是否暂停音乐」。
 */
public class MicrophoneBlockEntity extends BlockEntity implements MenuProvider {

    public enum RedstoneMode {
        SELF,
        ALL,
        ALWAYS;

        public RedstoneMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static RedstoneMode byOrdinal(int ordinal) {
            RedstoneMode[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SELF;
        }
    }

    private volatile boolean active;
    private boolean lastShouldBroadcast;
    private boolean clientCapture;
    private boolean pausedMusic;

    private String frequency = FrequencyUtil.DEFAULT;
    private RedstoneMode redstoneMode = RedstoneMode.SELF;
    private boolean pauseMusicWhileTalking = true;
    private boolean startSoundEnabled = true;

    @Nullable
    private UUID owner;

    public MicrophoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MICROPHONE.get(), pos, state);
    }

    // ---------- 基础状态 ----------

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String raw) {
        String f = FrequencyUtil.normalizeOrDefault(raw);
        if (f.equals(frequency)) return;
        if (active) stopBroadcast();
        frequency = f;
        lastShouldBroadcast = false;
        setChanged();
    }

    public RedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    public void setRedstoneMode(RedstoneMode mode) {
        if (mode == null || mode == redstoneMode) return;
        if (active) stopBroadcast();
        redstoneMode = mode;
        lastShouldBroadcast = false;
        setChanged();
    }

    public boolean isPauseMusicWhileTalking() {
        return pauseMusicWhileTalking;
    }

    public void setPauseMusicWhileTalking(boolean pause) {
        if (pause == pauseMusicWhileTalking) return;
        pauseMusicWhileTalking = pause;
        if (active) {
            if (pause) pauseStations();
            else resumeStations();
        }
        setChanged();
    }

    public boolean isStartSoundEnabled() {
        return startSoundEnabled;
    }

    public void setStartSoundEnabled(boolean enabled) {
        if (enabled == startSoundEnabled) return;
        startSoundEnabled = enabled;
        setChanged();
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public void setOwnerFrom(@Nullable LivingEntity placer) {
        if (placer instanceof Player p) {
            setOwner(p.getUUID());
        }
    }

    // ---------- 频率连接 ----------

    /** 持有该频率的广播站(可能没有,话筒仍可直接向同频率广播器讲话)。 */
    @Nullable
    public BlockPos getLinkedStation() {
        if (level instanceof ServerLevel sl) {
            List<BlockPos> stations = BroadcastRegistryData.get(sl).stationsFor(sl, frequency);
            return stations.isEmpty() ? null : stations.get(0);
        }
        return null;
    }

    /** 该频率下、且位于话筒检测半径内的广播器。 */
    public List<BlockPos> getOutputSpeakers() {
        if (level instanceof ServerLevel sl) {
            List<BlockPos> all = BroadcastRegistryData.get(sl).speakersFor(sl, Set.of(frequency));
            double radius = Math.max(1, BroadcastConfig.micDetectionRadius());
            double r2 = radius * radius;
            net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atCenterOf(worldPosition);
            List<BlockPos> out = new ArrayList<>();
            for (BlockPos pos : all) {
                if (net.minecraft.world.phys.Vec3.atCenterOf(pos).distanceToSqr(center) <= r2) out.add(pos);
            }
            return out;
        }
        return Collections.emptyList();
    }

    public int countPoweredSpeakers() {
        if (!(level instanceof ServerLevel sl)) return 0;
        int powered = 0;
        for (BlockPos pos : getOutputSpeakers()) {
            if (level.isLoaded(pos) && level.hasNeighborSignal(pos)) powered++;
        }
        return powered;
    }

    public float getStationVolume() {
        BlockPos station = getLinkedStation();
        if (station != null && level instanceof ServerLevel sl
                && sl.getBlockEntity(station) instanceof BroadcastStationBlockEntity be) {
            return be.getVolume();
        }
        return 0.8F;
    }

    // ---------- 红石 / 广播开关 ----------

    public boolean shouldBroadcastNow() {
        if (level == null) return false;
        if (redstoneMode == RedstoneMode.ALWAYS) return true;
        if (!level.hasNeighborSignal(worldPosition)) return false;
        if (redstoneMode == RedstoneMode.SELF) return true;
        // ALL:同频率的每一个已加载广播器都必须通电
        List<BlockPos> speakers = getOutputSpeakers();
        if (speakers.isEmpty()) return false;
        for (BlockPos pos : speakers) {
            if (level.isLoaded(pos) && !level.hasNeighborSignal(pos)) return false;
        }
        return true;
    }

    public void checkRedstone() {
        serverTick();
    }

    public void serverTick() {
        if (level == null || level.isClientSide) return;
        boolean should = shouldBroadcastNow();
        if (should == lastShouldBroadcast) return;
        lastShouldBroadcast = should;
        if (should) startBroadcast();
        else stopBroadcast();
    }

    private void startBroadcast() {
        if (getOutputSpeakers().isEmpty()) {
            setActive(false);
            lastShouldBroadcast = false;
            broadcastState();
            return;
        }
        if (useClientCapture()) {
            ServerPlayer ownerPlayer = ownerPlayer();
            if (ownerPlayer != null) {
                clientCapture = true;
                setActive(true);
                if (pauseMusicWhileTalking) pauseStations();
                NetworkHandler.sendToPlayer(ownerPlayer, new MicCaptureControlPacket(worldPosition, true,
                        Math.round(getStationVolume() * 100), getOutputSpeakers()));
                broadcastState();
                playStartSound();
                return;
            }
        }
        MicCapture.tryStart(this);
        if (isActive() && pauseMusicWhileTalking) pauseStations();
        if (isActive()) playStartSound();
    }

    private void playStartSound() {
        if (!startSoundEnabled || !(level instanceof ServerLevel sl)) return;
        sl.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.BELL_BLOCK,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.6F);
    }

    private void stopBroadcast() {
        if (clientCapture) {
            clientCapture = false;
            ServerPlayer ownerPlayer = ownerPlayer();
            if (ownerPlayer != null) {
                NetworkHandler.sendToPlayer(ownerPlayer, new MicCaptureControlPacket(worldPosition, false,
                        0, Collections.emptyList()));
            }
            setActive(false);
        } else {
            MicCapture.stop(this);
        }
        if (pausedMusic) resumeStations();
        setActive(false);
        broadcastState();
        lastShouldBroadcast = false;
    }

    /** 方块被移除 / 客户端采集失败时强制停止。 */
    public void forceStop() {
        stopBroadcast();
    }

    private void pauseStations() {
        if (pausedMusic) return;
        pausedMusic = true;
        if (!(level instanceof ServerLevel sl)) return;
        for (BlockPos pos : BroadcastRegistryData.get(sl).stationsFor(sl, frequency)) {
            if (sl.getBlockEntity(pos) instanceof BroadcastStationBlockEntity be) {
                be.suspendForMic();
            }
        }
    }

    private void resumeStations() {
        if (!pausedMusic) return;
        pausedMusic = false;
        if (!(level instanceof ServerLevel sl)) return;
        for (BlockPos pos : BroadcastRegistryData.get(sl).stationsFor(sl, frequency)) {
            if (sl.getBlockEntity(pos) instanceof BroadcastStationBlockEntity be) {
                be.resumeAfterMic();
            }
        }
    }

    private boolean useClientCapture() {
        if (level == null || level.getServer() == null) return false;
        return BroadcastConfig.useClientMicCapture(level.getServer().isDedicatedServer());
    }

    @Nullable
    private ServerPlayer ownerPlayer() {
        if (owner == null || !(level instanceof ServerLevel sl) || sl.getServer() == null) return null;
        return sl.getServer().getPlayerList().getPlayer(owner);
    }

    /** 向同维度所有玩家同步话筒的广播状态。 */
    public void broadcastState() {
        if (!(level instanceof ServerLevel sl)) return;
        BlockPos station = getLinkedStation();
        MicStatePacket pkt = new MicStatePacket(worldPosition, active, station,
                Math.round(getStationVolume() * 100), getOutputSpeakers());
        for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
            if (p.level() == sl) NetworkHandler.sendToPlayer(p, pkt);
        }
    }

    /** 玩家进入世界/维度时补发当前状态。 */
    public void sendStateTo(ServerPlayer player) {
        if (player == null) return;
        BlockPos station = getLinkedStation();
        NetworkHandler.sendToPlayer(player, new MicStatePacket(worldPosition, active, station,
                Math.round(getStationVolume() * 100), getOutputSpeakers()));
    }

    // ---------- 持久化 ----------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("frequency", frequency);
        tag.putInt("redstoneMode", redstoneMode.ordinal());
        tag.putBoolean("pauseMusic", pauseMusicWhileTalking);
        tag.putBoolean("startSound", startSoundEnabled);
        if (owner != null) tag.putUUID("owner", owner);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        frequency = FrequencyUtil.normalizeOrDefault(tag.getString("frequency"));
        redstoneMode = RedstoneMode.byOrdinal(tag.getInt("redstoneMode"));
        pauseMusicWhileTalking = !tag.contains("pauseMusic") || tag.getBoolean("pauseMusic");
        startSoundEnabled = !tag.contains("startSound") || tag.getBoolean("startSound");
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
        // 红石状态不持久化:世界重新加载后由 serverTick 重新检测
        lastShouldBroadcast = false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && level instanceof ServerLevel sl) {
            BroadcastRegistryData.get(sl).addMicrophone(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && level instanceof ServerLevel sl) {
            BroadcastRegistryData.get(sl).removeMicrophone(worldPosition);
        }
        super.setRemoved();
    }

    // ---------- 菜单 ----------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.broadcast.microphone");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new MicrophoneMenu(containerId, inv, worldPosition);
    }
}
