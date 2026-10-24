package com.broadcast.command;

import com.broadcast.registry.ModItems;
import com.broadcast.server.MusicLibrary;
import com.broadcast.server.UploadHttpServer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * 调试指令:/? ...
 * 只放不进 GUI 的功能;所有危险操作需要 OP(权限等级 2)。
 */
public final class BroadcastCommands {
    private BroadcastCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("?")
                .then(Commands.literal("list")
                        .then(Commands.literal("command").executes(BroadcastCommands::listCommands))
                        .then(Commands.literal("music").executes(BroadcastCommands::listMusic)))
                .then(Commands.literal("delete")
                        .then(Commands.literal("musichub")
                                .then(Commands.literal("sure")
                                        .then(Commands.argument("uid", StringArgumentType.word())
                                                .executes(BroadcastCommands::deleteMusic)))))
                .then(Commands.literal("restart").executes(BroadcastCommands::restart))
                .then(Commands.literal("stopweb").executes(BroadcastCommands::stopWeb))
                .then(Commands.literal("startweb").executes(BroadcastCommands::startWeb))
                .then(Commands.literal("give")
                        .then(Commands.literal("TSQ")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(BroadcastCommands::giveDebugger))))
                .executes(BroadcastCommands::listCommands));
    }

    private static boolean requireOp(CommandSourceStack source) {
        if (source.hasPermission(2)) return true;
        source.sendFailure(Component.literal("需要管理员权限(OP)"));
        return false;
    }

    private static int listCommands(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String[] lines = new String[]{
                "/? list command  - 显示本模组指令",
                "/? list music    - 列出音乐库 UID 与文件名",
                "/? delete musichub sure <UID> - 删除指定 UID 的音乐(需要 OP)",
                "/? restart       - 重启上传服务并重扫音乐库(需要 OP)",
                "/? stopweb       - 停止音乐上传服务(需要 OP)",
                "/? startweb      - 启动音乐上传服务(需要 OP)",
                "/? give TSQ <玩家> - 给玩家一个调试器(需要 OP)"
        };
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int listMusic(CommandContext<CommandSourceStack> ctx) {
        var entries = MusicLibrary.get().entries();
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("音乐库为空"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("音乐库(" + entries.size() + "):"), false);
        for (MusicLibrary.Entry entry : entries) {
            ctx.getSource().sendSuccess(() -> Component.literal(entry.uid() + "  " + entry.name()), false);
        }
        return entries.size();
    }

    private static int deleteMusic(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOp(source)) return 0;
        String uid = MusicLibrary.sanitizeUid(StringArgumentType.getString(ctx, "uid"));
        if (uid == null) {
            source.sendFailure(Component.literal("UID 格式不合法(1~10 位小写字母/数字)"));
            return 0;
        }
        String removed = MusicLibrary.get().deleteByUid(uid);
        if (removed == null) {
            source.sendFailure(Component.literal("未找到 UID: " + uid));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已删除: " + removed + " (" + uid + ")"), true);
        return 1;
    }

    private static int restart(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOp(source)) return 0;
        UploadHttpServer.stop();
        MusicLibrary.get().reload();
        UploadHttpServer.start(source.getServer());
        source.sendSuccess(() -> Component.literal("广播模组服务已重启(上传服务 + 音乐库索引)"), true);
        return 1;
    }

    private static int stopWeb(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOp(source)) return 0;
        UploadHttpServer.stop();
        source.sendSuccess(() -> Component.literal("音乐上传服务已停止"), true);
        return 1;
    }

    private static int startWeb(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOp(source)) return 0;
        UploadHttpServer.start(source.getServer());
        boolean running = UploadHttpServer.isRunning();
        source.sendSuccess(() -> Component.literal(running
                ? "音乐上传服务已启动" : "启动失败:检查端口/配置(0 表示关闭)"), true);
        return running ? 1 : 0;
    }

    private static int giveDebugger(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!requireOp(source)) return 0;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ItemStack stack = new ItemStack(ModItems.DEBUGGER.get());
        if (!target.getInventory().add(stack)) {
            target.drop(stack, false);
        }
        target.displayClientMessage(Component.literal("已获得广播调试器"), false);
        source.sendSuccess(() -> Component.literal("已给予 " + target.getGameProfile().getName() + " 调试器"), true);
        return 1;
    }
}
