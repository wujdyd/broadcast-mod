package com.broadcast.util;

import com.broadcast.BroadcastMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务器/联机权限组频率段。
 * 默认:
 *   su  0-40     纯数字
 *   cr  41-100   纯数字
 *   max 0-...    任意(含英文)
 * 可通过 /? band 指令修改并持久化。
 */
public final class FrequencyBandManager {
    public enum Group {
        MAX, CR, SU;

        public static Group byName(String name) {
            if (name == null) return null;
            return switch (name.toLowerCase()) {
                case "max" -> MAX;
                case "cr" -> CR;
                case "su" -> SU;
                default -> null;
            };
        }

        public int permissionLevel() {
            return switch (this) {
                case MAX -> 3;
                case CR -> 2;
                case SU -> 0;
            };
        }
    }

    public record Band(int start, int end, boolean english) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Group, Band> BANDS = new LinkedHashMap<>();
    private static Path file;

    static {
        resetDefaults();
    }

    private FrequencyBandManager() {
    }

    private static void resetDefaults() {
        BANDS.clear();
        BANDS.put(Group.SU, new Band(0, 40, false));
        BANDS.put(Group.CR, new Band(41, 100, false));
        BANDS.put(Group.MAX, new Band(0, 999_999_999, true));
    }

    public static synchronized void load(MinecraftServer server) {
        resetDefaults();
        file = server.getWorldPath(LevelResource.ROOT).resolve("broadcast_bands.json");
        if (!Files.isRegularFile(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Band> loaded = GSON.fromJson(reader,
                    new TypeToken<LinkedHashMap<String, Band>>() {
                    }.getType());
            if (loaded == null) return;
            for (Map.Entry<String, Band> e : loaded.entrySet()) {
                Group g = Group.byName(e.getKey());
                Band b = e.getValue();
                if (g != null && b != null && b.start() <= b.end()) BANDS.put(g, b);
            }
        } catch (Throwable t) {
            BroadcastMod.LOGGER.warn("读取频率权限段失败,使用默认值: {}", t.getMessage());
        }
    }

    public static synchronized void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Map<String, Band> out = new LinkedHashMap<>();
            for (Map.Entry<Group, Band> e : BANDS.entrySet()) out.put(e.getKey().name(), e.getValue());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(out, writer);
            }
        } catch (Throwable t) {
            BroadcastMod.LOGGER.warn("保存频率权限段失败: {}", t.getMessage());
        }
    }

    public static Group groupFor(ServerPlayer player) {
        if (player.hasPermissions(3)) return Group.MAX;
        if (player.hasPermissions(2)) return Group.CR;
        return Group.SU;
    }

    public static Band band(Group group) {
        return BANDS.getOrDefault(group, new Band(0, 0, false));
    }

    public static boolean canUse(ServerPlayer player, String frequency) {
        String f = FrequencyUtil.normalize(frequency);
        if (f.isEmpty()) return false;
        Band band = band(groupFor(player));
        boolean digits = f.chars().allMatch(Character::isDigit);
        if (digits) {
            try {
                long n = Long.parseLong(f);
                return n >= band.start() && n <= band.end();
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return band.english();
    }

    /** @return null 表示成功,否则为语言键。 */
    public static synchronized String setBand(Group group, int start, int end, boolean english) {
        if (group == null || start < 0 || end < start) return "command.broadcast.band_invalid";
        if (group != Group.MAX) {
            for (Map.Entry<Group, Band> e : BANDS.entrySet()) {
                if (e.getKey() == Group.MAX || e.getKey() == group) continue;
                Band other = e.getValue();
                if (start <= other.end() && end >= other.start()) {
                    return "command.broadcast.band_overlap";
                }
            }
        }
        BANDS.put(group, new Band(start, end, english));
        save();
        return null;
    }

    public static String describe(Group group) {
        Band b = band(group);
        return group.name().toLowerCase() + ": " + b.start() + "-" + b.end()
                + " english=" + (b.english() ? "yes" : "no");
    }
}
