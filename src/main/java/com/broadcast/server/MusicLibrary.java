package com.broadcast.server;

import com.broadcast.BroadcastMod;
import com.broadcast.util.NameUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端音乐库索引:每个音频文件分配一个唯一 UID(8 位,小写字母开头,仅含小写字母与数字)。
 * UID 持久化在音乐目录的 library.json 中,用于 GUI 删除与指令删除。
 */
public final class MusicLibrary {
    public static final int UID_LENGTH = 8;
    private static final String INDEX_FILE = "library.json";
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new Gson();
    private static final MusicLibrary INSTANCE = new MusicLibrary();

    private final Map<String, String> uidToName = new LinkedHashMap<>();
    private final Map<String, String> nameToUid = new LinkedHashMap<>();

    private MusicLibrary() {
    }

    public static MusicLibrary get() {
        return INSTANCE;
    }

    public record Entry(String uid, String name) {
    }

    private Path indexFile() {
        return UploadHttpServer.musicDir().resolve(INDEX_FILE);
    }

    /** 重新扫描音乐目录:读取索引、清理失效项、给新文件分配 UID。 */
    public synchronized void reload() {
        uidToName.clear();
        nameToUid.clear();
        Path dir = UploadHttpServer.musicDir();
        try {
            Files.createDirectories(dir);
            if (Files.isRegularFile(indexFile())) {
                try (Reader reader = Files.newBufferedReader(indexFile(), StandardCharsets.UTF_8)) {
                    Map<String, String> loaded = GSON.fromJson(reader,
                            new TypeToken<LinkedHashMap<String, String>>() {
                            }.getType());
                    if (loaded != null) {
                        for (Map.Entry<String, String> e : loaded.entrySet()) {
                            String uid = sanitizeUid(e.getKey());
                            String name = NameUtil.sanitizeFileName(e.getValue());
                            if (uid != null && !name.isEmpty() && NameUtil.isPlayableExtension(name)) {
                                uidToName.put(uid, name);
                                nameToUid.put(name, uid);
                            }
                        }
                    }
                } catch (Throwable t) {
                    BroadcastMod.LOGGER.warn("读取音乐库索引失败,将重建: {}", t.getMessage());
                }
            }
            List<Path> files = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> NameUtil.isPlayableExtension(p.getFileName().toString()))
                        .forEach(files::add);
            }
            files.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));
            // 清理已不存在的文件
            uidToName.entrySet().removeIf(e -> !Files.isRegularFile(dir.resolve(e.getValue())));
            nameToUid.entrySet().removeIf(e -> !uidToName.containsValue(e.getKey()));
            for (Path p : files) {
                String name = p.getFileName().toString();
                if (!nameToUid.containsKey(name)) {
                    assign(name);
                }
            }
            save();
        } catch (IOException e) {
            BroadcastMod.LOGGER.warn("扫描音乐库失败: {}", e.getMessage());
        }
    }

    private String assign(String name) {
        String uid;
        do {
            uid = randomUid();
        } while (uidToName.containsKey(uid));
        uidToName.put(uid, name);
        nameToUid.put(name, uid);
        return uid;
    }

    private static String randomUid() {
        StringBuilder sb = new StringBuilder(UID_LENGTH);
        sb.append((char) ('a' + RANDOM.nextInt(26)));
        for (int i = 1; i < UID_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public static String sanitizeUid(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.length() < 1 || s.length() > 10) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (!ok) return null;
        }
        if (!s.chars().anyMatch(c -> c >= 'a' && c <= 'z')) return null;
        return s;
    }

    /** 上传/新增文件后调用,返回 UID。 */
    public synchronized String ensure(String fileName) {
        String name = NameUtil.sanitizeFileName(fileName);
        if (name.isEmpty()) return "";
        String existing = nameToUid.get(name);
        if (existing != null) return existing;
        String uid = assign(name);
        save();
        return uid;
    }

    public synchronized String uidFor(String fileName) {
        return nameToUid.getOrDefault(NameUtil.sanitizeFileName(fileName), "");
    }

    public synchronized String nameFor(String uid) {
        return uidToName.get(uid);
    }

    public synchronized List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, String> e : uidToName.entrySet()) {
            out.add(new Entry(e.getKey(), e.getValue()));
        }
        out.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public synchronized String deleteByUid(String uid) {
        String name = uidToName.remove(uid);
        if (name == null) return null;
        nameToUid.remove(name);
        try {
            Files.deleteIfExists(UploadHttpServer.musicDir().resolve(name));
        } catch (IOException e) {
            BroadcastMod.LOGGER.warn("删除音乐文件失败 {}: {}", name, e.getMessage());
        }
        save();
        return name;
    }

    private void save() {
        try {
            Files.createDirectories(UploadHttpServer.musicDir());
            try (Writer writer = Files.newBufferedWriter(indexFile(), StandardCharsets.UTF_8)) {
                GSON.toJson(uidToName, writer);
            }
        } catch (IOException e) {
            BroadcastMod.LOGGER.warn("保存音乐库索引失败: {}", e.getMessage());
        }
    }
}
