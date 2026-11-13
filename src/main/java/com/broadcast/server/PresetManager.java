package com.broadcast.server;

import com.broadcast.BroadcastMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 广播预设:名称 + 曲目 + 循环 + 顺序。 */
public final class PresetManager {
    public record Preset(String name, List<String> tracks, boolean loop, String order) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();
    private static Path file;

    private PresetManager() {
    }

    public static synchronized void load(MinecraftServer server) {
        PRESETS.clear();
        file = server.getWorldPath(LevelResource.ROOT).resolve("broadcast_presets.json");
        if (!Files.isRegularFile(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Preset[] arr = GSON.fromJson(reader, Preset[].class);
            if (arr != null) {
                for (Preset p : arr) {
                    if (p != null && p.name() != null && !p.name().isBlank()) PRESETS.put(p.name(), p);
                }
            }
        } catch (Throwable t) {
            BroadcastMod.LOGGER.warn("读取广播预设失败: {}", t.getMessage());
        }
    }

    public static synchronized void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(new ArrayList<>(PRESETS.values()), writer);
            }
        } catch (Throwable t) {
            BroadcastMod.LOGGER.warn("保存广播预设失败: {}", t.getMessage());
        }
    }

    public static synchronized boolean put(String name, List<String> tracks, boolean loop, String order) {
        if (name == null || name.isBlank()) return false;
        PRESETS.put(name, new Preset(name, new ArrayList<>(tracks), loop, order));
        save();
        return true;
    }

    public static synchronized boolean delete(String name) {
        boolean removed = PRESETS.remove(name) != null;
        if (removed) save();
        return removed;
    }

    public static synchronized Preset get(String name) {
        return PRESETS.get(name);
    }

    public static synchronized List<Preset> all() {
        List<Preset> out = new ArrayList<>(PRESETS.values());
        out.sort(Comparator.comparing(Preset::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }
}
