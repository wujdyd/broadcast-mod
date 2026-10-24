package com.broadcast.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 与广播站 GUI 风格一致的按钮。 */
public class UiButton extends Button {
    private boolean accent;

    public UiButton(int x, int y, int w, int h, Component message, OnPress onPress, boolean accent) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        this.accent = accent;
    }

    public void setAccent(boolean accent) {
        this.accent = accent;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hover = isHoveredOrFocused();
        int bg = accent ? (hover ? GuiTheme.ACCENT_HOVER : GuiTheme.ACCENT)
                : (hover ? GuiTheme.CARD_HOVER : GuiTheme.CARD);
        GuiTheme.rr(g, getX(), getY(), getWidth(), getHeight(), bg,
                accent ? GuiTheme.ACCENT_HOVER : GuiTheme.BORDER);
        Minecraft mc = Minecraft.getInstance();
        g.drawCenteredString(mc.font, getMessage(), getX() + getWidth() / 2,
                getY() + (getHeight() - 8) / 2, accent ? 0xFFFFFFFF : GuiTheme.TEXT);
    }
}
