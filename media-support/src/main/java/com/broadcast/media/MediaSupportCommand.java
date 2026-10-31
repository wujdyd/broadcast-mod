package com.broadcast.media;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;

/** 主模组调用的隐藏指令;玩家也可以直接用。 */
public final class MediaSupportCommand {
    private MediaSupportCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("broadcastmediasupport")
                .then(Commands.literal("resolve")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("dir", StringArgumentType.word())
                                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                                .executes(MediaSupportCommand::resolve))))));
    }

    private static int resolve(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }
        BlockPos pos;
        try {
            pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        } catch (Exception e) {
            return 0;
        }
        String dirHex = StringArgumentType.getString(ctx, "dir");
        String url = StringArgumentType.getString(ctx, "url");
        Path dir;
        try {
            dir = Path.of(new String(HexFormat.of().parseHex(dirHex), StandardCharsets.UTF_8));
        } catch (Throwable t) {
            player.displayClientMessage(Component.literal("媒体目录参数无效"), false);
            return 0;
        }

        Thread thread = new Thread(() -> {
            try {
                String fileName = MediaSupport.resolveBlocking(player, url, dir.toString());
                if (player.getServer() == null) return;
                player.getServer().execute(() -> {
                    try {
                        BlockEntity be = player.level().getBlockEntity(pos);
                        if (be == null) throw new IllegalStateException("广播站不存在");
                        Method start = be.getClass().getMethod("startPlayback", String.class, boolean.class);
                        start.invoke(be, fileName, false);
                        player.displayClientMessage(Component.literal("视频音频已就绪: " + fileName), true);
                    } catch (Throwable t) {
                        player.displayClientMessage(Component.literal(
                                "视频音频播放失败: " + rootMessage(t)), false);
                    }
                });
            } catch (Throwable t) {
                if (player.getServer() != null) {
                    player.getServer().execute(() -> player.displayClientMessage(
                            Component.literal("视频音频解析失败: " + rootMessage(t)), false));
                }
            }
        }, "BroadcastMediaSupport-Resolve");
        thread.setDaemon(true);
        thread.start();
        return 1;
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) c = c.getCause();
        return c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
    }
}
