package com.broadcast.world;

import com.broadcast.blockentity.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 广播器干扰规则:
 * - OP3(权限>=3)放置的广播器,100 格内其他权限的广播器被压制;
 * - OP2 放置的广播器,100 格内普通玩家的广播器被压制;
 * - OP3 广播器不会被 OP2 压制。
 */
public final class InterferenceUtil {
    public static final double RADIUS = 100.0D;

    private InterferenceUtil() {
    }

    public static boolean isMuted(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SpeakerBlockEntity self)) return false;
        int myLevel = self.getOwnerLevel();
        if (myLevel >= 3) return false;
        double r2 = RADIUS * RADIUS;
        for (BlockPos otherPos : BroadcastRegistryData.get(level).getSpeakers()) {
            if (otherPos.equals(pos) || !level.isLoaded(otherPos)) continue;
            if (otherPos.distSqr(pos) > r2) continue;
            if (!(level.getBlockEntity(otherPos) instanceof SpeakerBlockEntity other)) continue;
            int otherLevel = other.getOwnerLevel();
            if (otherLevel >= 3 && myLevel < 3) return true;
            if (otherLevel == 2 && myLevel < 2) return true;
        }
        return false;
    }
}
