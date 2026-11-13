package com.broadcast.command;

import com.broadcast.server.MusicLibrary;
import com.broadcast.server.UploadHttpServer;
import com.broadcast.util.FrequencyBandManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/** 调试指令:/? ... ;危险操作需要 OP。 */
public final class BroadcastCommands {
    private static final String[] HELP_KEYS = new String[]{
            "command.broadcast.help.list",
            "command.broadcast.help.music",
            "command.broadcast.help.recipe",
            "command.broadcast.help.band",
            "command.broadcast.help.find",
            "command.broadcast.help.sre",
            "command.broadcast.help.listed",
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
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                .executes(BroadcastCommands::setBand)))
                .then(Commands.literal("find")
                        .then(Commands.argument("frequency", StringArgumentType.greedyString())
                                .executes(BroadcastCommands::findFrequency)))
                .then(Commands.literal("sre").executes(BroadcastCommands::forceRestart))
                .then(Commands.literal("listed")
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                .executes(BroadcastCommands::listed)))
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
        String[] parts = StringArgumentType.getString(ctx, "args").trim().split("\\s+");
        FrequencyBandManager.Group group;
        String raw;
        String englishRaw;
        if (parts.length == 2) {
            raw = parts[0];
            englishRaw = parts[1];
            group = inferGroup(raw);
            if (group == null) {
                source.sendFailure(Component.translatable("command.broadcast.band_invalid"));
                return 0;
            }
        } else if (parts.length == 3) {
            group = FrequencyBandManager.Group.byName(parts[0]);
            raw = parts[1];
            englishRaw = parts[2];
            if (group == null) {
                source.sendFailure(Component.translatable("command.broadcast.band_group_invalid"));
                return 0;
            }
        } else {
            source.sendFailure(Component.translatable("command.broadcast.band_invalid"));
            return 0;
        }
        int start;
        int end;
        try {
            String[] range = raw.split(":", -1);
            if (range.length != 2) throw new NumberFormatException();
            start = Integer.parseInt(range[0].trim());
            end = Integer.parseInt(range[1].trim());
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

    /** 未写权限组时,根据范围终值推断:0-40=su,41-100=cr,更大=max。 */
    private static FrequencyBandManager.Group inferGroup(String range) {
        try {
            String[] parts = range.split(":", -1);
            if (parts.length != 2) return null;
            int end = Integer.parseInt(parts[1].trim());
            if (end <= 40) return FrequencyBandManager.Group.SU;
            if (end <= 100) return FrequencyBandManager.Group.CR;
            return FrequencyBandManager.Group.MAX;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String ownerSuffix(CommandSourceStack source, java.util.UUID owner) {
        if (owner == null) return "";
        var player = source.getServer() == null ? null : source.getServer().getPlayerList().getPlayer(owner);
        if (player != null) return "(" + player.getGameProfile().getName() + ")";
        return "(" + owner.toString().substring(0, 8) + ")";
    }

    private static int findFrequency(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String f = com.broadcast.util.FrequencyUtil.normalize(
                StringArgumentType.getString(ctx, "frequency"));
        if (f.isEmpty()) {
            source.sendFailure(Component.translatable("command.broadcast.find_invalid"));
            return 0;
        }
        var level = source.getLevel();
        var reg = com.broadcast.world.BroadcastRegistryData.get(level);
        java.util.List<String> owners = new java.util.ArrayList<>();
        for (BlockPos pos : reg.getStations()) {
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof com.broadcast.blockentity.BroadcastStationBlockEntity station
                    && station.getFrequencies().contains(f)) {
                owners.add("广播站@" + pos.toShortString() + ownerSuffix(source, station.getOwner()));
            }
        }
        for (BlockPos pos : reg.getSpeakers()) {
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof com.broadcast.blockentity.SpeakerBlockEntity speaker
                    && speaker.getFrequency().equals(f)) {
                owners.add("广播器@" + pos.toShortString() + ownerSuffix(source, speaker.getOwner()));
            }
        }
        for (BlockPos pos : reg.getMicrophones()) {
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof com.broadcast.blockentity.MicrophoneBlockEntity mic
                    && mic.getFrequency().equals(f)) {
                owners.add("话筒@" + pos.toShortString() + ownerSuffix(source, mic.getOwner()));
            }
        }
        if (owners.isEmpty()) {
            source.sendSuccess(() -> Component.literal(f + " 未被占用"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(f + " 已被占用: " + String.join(", ", owners)), false);
        return owners.size();
    }

    private static int forceRestart(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOp3(source)) return 0;
        UploadHttpServer.stop();
        MusicLibrary.get().reload();
        com.broadcast.server.PresetManager.load(source.getServer());
        FrequencyBandManager.load(source.getServer());
        UploadHttpServer.start(source.getServer());
        for (net.minecraft.server.level.ServerLevel level : source.getServer().getAllLevels()) {
            var reg = com.broadcast.world.BroadcastRegistryData.get(level);
            for (BlockPos pos : new java.util.ArrayList<>(reg.getStations())) {
                if (level.isLoaded(pos)
                        && level.getBlockEntity(pos) instanceof com.broadcast.blockentity.BroadcastStationBlockEntity station) {
                    station.stopPreset();
                    station.broadcastInfo();
                }
            }
            for (BlockPos pos : new java.util.ArrayList<>(reg.getMicrophones())) {
                if (level.isLoaded(pos)
                        && level.getBlockEntity(pos) instanceof com.broadcast.blockentity.MicrophoneBlockEntity mic) {
                    mic.forceStop();
                }
            }
        }
        source.sendSuccess(() -> Component.translatable("command.broadcast.sre_done"), true);
        return 1;
    }

    private static int listed(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String[] parts = StringArgumentType.getString(ctx, "args").trim().split("\\s+");
        if (parts.length == 2 && ("delete".equalsIgnoreCase(parts[1]) || "save".equalsIgnoreCase(parts[1]))) {
            String name = parts[0];
            if ("delete".equalsIgnoreCase(parts[1])) {
                if (com.broadcast.server.PresetManager.delete(name)) {
                    source.sendSuccess(() -> Component.literal("预设已删除: " + name), true);
                    return 1;
                }
                source.sendFailure(Component.literal("预设不存在: " + name));
                return 0;
            }
            com.broadcast.server.PresetManager.Preset preset = com.broadcast.server.PresetManager.get(name);
            if (preset == null) {
                source.sendFailure(Component.literal("预设不存在: " + name));
                return 0;
            }
            applyPresetNear(source, preset);
            return 1;
        }
        if (parts.length < 4) {
            source.sendFailure(Component.translatable("command.broadcast.listed_invalid"));
            return 0;
        }
        int n = parts.length;
        String name = parts[n - 1];
        String order = parts[n - 2].toLowerCase(java.util.Locale.ROOT);
        String loopRaw = parts[n - 3];
        if (!order.equals("z") && !order.equals("d") && !order.equals("s")) {
            source.sendFailure(Component.translatable("command.broadcast.listed_invalid"));
            return 0;
        }
        boolean loop;
        if ("yes".equalsIgnoreCase(loopRaw)) loop = true;
        else if ("no".equalsIgnoreCase(loopRaw)) loop = false;
        else {
            source.sendFailure(Component.translatable("command.broadcast.listed_invalid"));
            return 0;
        }
        StringBuilder trackPart = new StringBuilder();
        for (int i = 0; i < n - 3; i++) {
            if (i > 0) trackPart.append(' ');
            trackPart.append(parts[i]);
        }
        java.util.List<String> tracks = new java.util.ArrayList<>();
        for (String s : trackPart.toString().split("\\*")) {
            if (!s.isBlank()) tracks.add(s.trim());
        }
        if (tracks.isEmpty()) {
            source.sendFailure(Component.translatable("command.broadcast.listed_invalid"));
            return 0;
        }
        com.broadcast.server.PresetManager.put(name, tracks, loop, order);
        com.broadcast.server.PresetManager.Preset preset = com.broadcast.server.PresetManager.get(name);
        source.sendSuccess(() -> Component.literal("预设已保存: " + name), true);
        if (preset != null) applyPresetNear(source, preset);
        return 1;
    }

    private static void applyPresetNear(CommandSourceStack source,
                                        com.broadcast.server.PresetManager.Preset preset) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return;
        ServerLevel level = source.getLevel();
        var reg = com.broadcast.world.BroadcastRegistryData.get(level);
        BlockPos best = null;
        double bestDist = 32 * 32;
        for (BlockPos pos : reg.getStations()) {
            if (!level.isLoaded(pos)) continue;
            double d = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (d <= bestDist) {
                bestDist = d;
                best = pos;
            }
        }
        if (best != null
                && level.getBlockEntity(best) instanceof com.broadcast.blockentity.BroadcastStationBlockEntity station
                && station.canAccess(player)) {
            station.startPreset(preset.tracks(), preset.loop(), preset.order());
        }
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
