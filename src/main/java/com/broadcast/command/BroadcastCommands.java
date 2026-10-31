package com.broadcast.command;

import com.broadcast.server.MusicLibrary;
import com.broadcast.server.UploadHttpServer;
import com.broadcast.util.FrequencyBandManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

/** 调试指令:/? ... ;危险操作需要 OP。 */
public final class BroadcastCommands {
    private static final String[] HELP_KEYS = new String[]{
            "command.broadcast.help.list",
            "command.broadcast.help.music",
            "command.broadcast.help.recipe",
            "command.broadcast.help.band",
            "command.broadcast.help.delete",
            "command.broadcast.help.restart",
            "command.broadcast.help.stopweb",
            "command.broadcast.help.startweb"
    };

    private BroadcastCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("?")
                .then(Commands.literal("list")
                        .then(Commands.literal("command").executes(BroadcastCommands::listCommands))
                        .then(Commands.literal("music").executes(BroadcastCommands::listMusic))
                        .then(Commands.literal("recipe").executes(BroadcastCommands::listRecipes)))
                .then(Commands.literal("delete")
                        .then(Commands.literal("musichub")
                                .then(Commands.literal("sure")
                                        .then(Commands.argument("uid", StringArgumentType.word())
                                                .executes(BroadcastCommands::deleteMusic)))))
                .then(Commands.literal("restart").executes(BroadcastCommands::restart))
                .then(Commands.literal("stopweb").executes(BroadcastCommands::stopWeb))
                .then(Commands.literal("startweb").executes(BroadcastCommands::startWeb))
                .then(Commands.literal("band")
                        .executes(BroadcastCommands::listBands)
                        .then(Commands.argument("group", StringArgumentType.word())
                                .then(Commands.argument("args", StringArgumentType.greedyString())
                                        .executes(BroadcastCommands::setBand))))
                .executes(BroadcastCommands::listCommands));
    }

    private static boolean requireOp(CommandSourceStack source) {
        if (source.hasPermission(2)) return true;
        source.sendFailure(Component.translatable("command.broadcast.no_permission"));
        return false;
    }

    private static int listCommands(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        for (String key : HELP_KEYS) {
            source.sendSuccess(() -> Component.translatable(key), false);
        }
        return 1;
    }

    private static int listMusic(CommandContext<CommandSourceStack> ctx) {
        var entries = MusicLibrary.get().entries();
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.broadcast.music_empty"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.broadcast.music_header",
                entries.size()), false);
        for (MusicLibrary.Entry entry : entries) {
            ctx.getSource().sendSuccess(() -> Component.literal(entry.uid() + "  " + entry.name()), false);
        }
        return entries.size();
    }

    private static int listRecipes(CommandContext<CommandSourceStack> ctx) {
        var recipes = ctx.getSource().getServer().getRecipeManager().getRecipes().stream()
                .filter(r -> r.getId().getNamespace().equals("broadcast"))
                .map(r -> r.getId().toString())
                .sorted()
                .toList();
        ctx.getSource().sendSuccess(() -> Component.literal("broadcast recipes (" + recipes.size() + "): "
                + String.join(", ", recipes)), false);
        return recipes.size();
    }

    private static boolean requireOp3(CommandSourceStack source) {
        if (source.hasPermission(3)) return true;
        source.sendFailure(Component.translatable("command.broadcast.no_permission"));
        return false;
    }

    private static int listBands(CommandContext<CommandSourceStack> ctx) {
        for (FrequencyBandManager.Group g : FrequencyBandManager.Group.values()) {
            ctx.getSource().sendSuccess(() -> Component.literal(FrequencyBandManager.describe(g)), false);
        }
        return FrequencyBandManager.Group.values().length;
    }

    private static int setBand(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOp3(source)) return 0;
        FrequencyBandManager.Group group =
                FrequencyBandManager.Group.byName(StringArgumentType.getString(ctx, "group"));
        if (group == null) {
            source.sendFailure(Component.translatable("command.broadcast.band_group_invalid"));
            return 0;
        }
        String args = StringArgumentType.getString(ctx, "args").trim();
        int split = args.lastIndexOf(' ');
        if (split <= 0) {
            source.sendFailure(Component.translatable("command.broadcast.band_invalid"));
            return 0;
        }
        String raw = args.substring(0, split).trim();
        String englishRaw = args.substring(split + 1).trim();
        int start;
        int end;
        try {
            String[] parts = raw.split(":", -1);
            if (parts.length != 2) throw new NumberFormatException();
            start = Integer.parseInt(parts[0].trim());
            end = Integer.parseInt(parts[1].trim());
        } catch (Throwable t) {
            source.sendFailure(Component.translatable("command.broadcast.band_invalid"));
            return 0;
        }
        boolean english;
        if ("yes".equalsIgnoreCase(englishRaw)) english = true;
        else if ("no".equalsIgnoreCase(englishRaw)) english = false;
        else {
            source.sendFailure(Component.translatable("command.broadcast.band_english_invalid"));
            return 0;
        }
        String error = FrequencyBandManager.setBand(group, start, end, english);
        if (error != null) {
            source.sendFailure(Component.translatable(error));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(FrequencyBandManager.describe(group)), true);
        return 1;
    }

    private static int deleteMusic(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOp(source)) return 0;
        String uid = MusicLibrary.sanitizeUid(StringArgumentType.getString(ctx, "uid"));
        if (uid == null) {
            source.sendFailure(Component.translatable("command.broadcast.uid_invalid"));
            return 0;
        }
        String removed = MusicLibrary.get().deleteByUid(uid);
        if (removed == null) {
            source.sendFailure(Component.translatable("command.broadcast.uid_missing", uid));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.broadcast.deleted", removed, uid), true);
        return 1;
    }

    private static int restart(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOp(source)) return 0;
        UploadHttpServer.stop();
        MusicLibrary.get().reload();
        UploadHttpServer.start(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.broadcast.restarted"), true);
        return 1;
    }

    private static int stopWeb(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOp(source)) return 0;
        UploadHttpServer.stop();
        source.sendSuccess(() -> Component.translatable("command.broadcast.web_stopped"), true);
        return 1;
    }

    private static int startWeb(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOp(source)) return 0;
        UploadHttpServer.start(source.getServer());
        boolean running = UploadHttpServer.isRunning();
        source.sendSuccess(() -> Component.translatable(running
                ? "command.broadcast.web_started" : "command.broadcast.web_start_failed"), true);
        return running ? 1 : 0;
    }
}
