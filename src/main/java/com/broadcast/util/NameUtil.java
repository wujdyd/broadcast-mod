package com.broadcast.util;

public final class NameUtil {
    private NameUtil() {
    }

    /** 清洗文件名:去掉路径、控制字符与非法字符,防止路径穿越。 */
    public static String sanitizeFileName(String raw) {
        if (raw == null) return "";
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || (c >= 0x4E00 && c <= 0x9FFF);
            if (ok) sb.append(c);
            else if (c == ' ') sb.append('_');
        }
        String out = sb.toString();
        while (out.startsWith(".")) out = out.substring(1);
        if (out.length() > 80) {
            int dot = out.lastIndexOf('.');
            String ext = dot > 0 ? out.substring(dot) : "";
            out = out.substring(0, Math.max(1, 80 - ext.length())) + ext;
        }
        return out;
    }

    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    public static boolean isPlayableExtension(String name) {
        String ext = extension(name);
        return ext.equals("ogg") || ext.equals("wav") || ext.equals("mp3");
    }
}
