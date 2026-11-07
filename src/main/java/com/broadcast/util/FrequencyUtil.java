package com.broadcast.util;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 广播频率工具。
 * 支持三种形式:
 * - 整数:1、40、100
 * - 小数:1.5、40.125(最多 3 位小数)
 * - 英文/英文+数字:CH1、NEWS(不区分大小写,统一转大写)
 * 小数和英文不能混用。
 */
public final class FrequencyUtil {
    public static final String DEFAULT = "1";
    public static final int MAX_LENGTH = 12;
    public static final int MAX_STATION_FREQUENCIES = 8;
    public static final int MAX_DECIMALS = 3;

    private FrequencyUtil() {
    }

    /** 清洗用户输入;非法输入返回空字符串。 */
    public static String normalize(@Nullable String raw) {
        if (raw == null) return "";
        String s = raw.trim().toUpperCase(Locale.ROOT);
        int dot = s.indexOf('.');
        if (dot >= 0) {
            if (s.indexOf('.', dot + 1) >= 0) return "";
            String intPart = s.substring(0, dot).trim();
            String fracPart = s.substring(dot + 1).trim();
            if (intPart.isEmpty() && fracPart.isEmpty()) return "";
            if (intPart.isEmpty()) intPart = "0";
            if (fracPart.isEmpty()) return intPart;
            if (!digits(intPart) || !digits(fracPart)) return "";
            if (intPart.length() > MAX_LENGTH - 1) return "";
            if (fracPart.length() > MAX_DECIMALS) fracPart = fracPart.substring(0, MAX_DECIMALS);
            while (fracPart.endsWith("0")) fracPart = fracPart.substring(0, fracPart.length() - 1);
            String out = fracPart.isEmpty() ? intPart : intPart + "." + fracPart;
            return out.length() > MAX_LENGTH ? "" : out;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < MAX_LENGTH; i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) sb.append(c);
        }
        return sb.toString();
    }

    private static boolean digits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        }
        return true;
    }

    public static boolean isDecimal(String frequency) {
        return frequency != null && frequency.indexOf('.') >= 0;
    }

    public static boolean isNumeric(String frequency) {
        if (frequency == null || frequency.isEmpty()) return false;
        for (int i = 0; i < frequency.length(); i++) {
            char c = frequency.charAt(i);
            if ((c < '0' || c > '9') && c != '.') return false;
        }
        return true;
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
