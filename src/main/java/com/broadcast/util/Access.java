package com.broadcast.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** 简单的权限判定:操作广播站需要靠近,管理员(op)不受距离限制。 */
public final class Access {
    private Access() {
    }

    public static final double CONTROL_RADIUS = 64.0;

    public static boolean canControl(ServerPlayer player, BlockPos station) {
        if (player == null) return false;
        if (player.hasPermissions(2)) return true;
        return player.distanceToSqr(Vec3.atCenterOf(station)) <= CONTROL_RADIUS * CONTROL_RADIUS;
    }
}
