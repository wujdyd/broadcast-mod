package com.broadcast.util;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 广播频率工具:频率由 1~12 位英文 / 数字组成(不区分大小写,统一转大写)。
 * 广播器与话筒通过相同频率建立连接;广播站可同时持有多个频率。
 */
public final class FrequencyUtil {
    public static final String DEFAULT = "1";
    public static final int MAX_LENGTH = 12;
    public static final int MAX_STATION_FREQUENCIES = 8;

    private FrequencyUtil() {
    }

    /** 清洗用户输入:只保留英文与数字,并转大写。 */
    public static String normalize(@Nullable String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length() && sb.length() < MAX_LENGTH; i++) {
            char c = Character.toUpperCase(raw.charAt(i));
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static boolean isValid(@Nullable String frequency) {
        String f = normalize(frequency);
        return !f.isEmpty() && f.equals(frequency);
    }

    /** 清洗并兜底为默认频率。 */
    public static String normalizeOrDefault(@Nullable String raw) {
        String f = normalize(raw);
        return f.isEmpty() ? DEFAULT : f;
    }

    /** 解析广播站的多频率列表:去重、清洗、限制数量,至少保留一个。 */
    public static Set<String> normalizeList(Collection<String> raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw != null) {
            for (String s : raw) {
                String f = normalize(s);
                if (!f.isEmpty()) out.add(f);
                if (out.size() >= MAX_STATION_FREQUENCIES) break;
            }
        }
        if (out.isEmpty()) out.add(DEFAULT);
        return out;
    }
}
