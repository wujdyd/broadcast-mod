package com.broadcast.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/** 设备 GUI 共用的配色与绘制工具,保持与广播站 GUI 一致的深色控制台风格。 */
public final class GuiTheme {
    public static final int PANEL = 0xFFC6C6C6;
    public static final int HEADER_TOP = 0xFFB8B8B8;
    public static final int HEADER_BOTTOM = 0xFF9A9A9A;
    public static final int CARD = 0xFFA8A8A8;
    public static final int CARD_HOVER = 0xFFB8B8B8;
    public static final int CARD_ACTIVE = 0xFF8F8F8F;
    public static final int BORDER = 0xFF373737;
    public static final int BORDER_LIGHT = 0xFFFFFFFF;
    public static final int ACCENT = 0xFF3B8526;
    public static final int ACCENT_HOVER = 0xFF4AAE32;
    public static final int ACCENT_DARK = 0xFF2D6A1E;
    public static final int TEXT = 0xFF1A1A1A;
    public static final int DIM = 0xFF3A3A3A;
    public static final int FAINT = 0xFF555555;
    public static final int GREEN = 0xFF1E7A1E;
    public static final int RED = 0xFFAA2222;
    public static final int ORANGE = 0xFF8A5A00;

    private GuiTheme() {
    }

    public static void rr(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    public static void background(GuiGraphics g, int width, int height) {
        g.fillGradient(0, 0, width, height, 0x70000000, 0xA0000000);
    }

    public static String trim(String s, int maxChars) {
        if (s == null) return "";
        return s.length() <= maxChars ? s : s.substring(0, Math.max(0, maxChars - 1)) + "…";
    }
}
