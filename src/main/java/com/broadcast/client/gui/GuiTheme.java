package com.broadcast.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/** 设备 GUI 共用的配色与绘制工具,保持与广播站 GUI 一致的深色控制台风格。 */
public final class GuiTheme {
    public static final int PANEL = 0xFF0E141D;
    public static final int HEADER_TOP = 0xFF16243A;
    public static final int HEADER_BOTTOM = 0xFF101A28;
    public static final int CARD = 0xFF16202E;
    public static final int CARD_HOVER = 0xFF1F2D40;
    public static final int CARD_ACTIVE = 0xFF1B2C45;
    public static final int BORDER = 0xFF2A3A50;
    public static final int BORDER_LIGHT = 0xFF3A506D;
    public static final int ACCENT = 0xFF3B82F6;
    public static final int ACCENT_HOVER = 0xFF60A5FA;
    public static final int ACCENT_DARK = 0xFF1D4ED8;
    public static final int TEXT = 0xFFE6EDF6;
    public static final int DIM = 0xFF90A2BA;
    public static final int FAINT = 0xFF63758C;
    public static final int GREEN = 0xFF34D399;
    public static final int RED = 0xFFF87171;
    public static final int ORANGE = 0xFFFBBF24;

    private GuiTheme() {
    }

    public static void rr(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    public static void background(GuiGraphics g, int width, int height) {
        g.fillGradient(0, 0, width, height, 0xB0060A10, 0xE0020508);
    }

    public static String trim(String s, int maxChars) {
        if (s == null) return "";
        return s.length() <= maxChars ? s : s.substring(0, Math.max(0, maxChars - 1)) + "…";
    }
}
