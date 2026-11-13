package com.broadcast.client.gui;

import com.broadcast.network.NetworkHandler;
import com.broadcast.network.packet.RenameMusicPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** 音乐库重命名页。 */
public class RenameMusicScreen extends Screen {
    private static final int W = 280, H = 150;
    private final BlockPos station;
    private final String uid;
    private final Screen parent;
    private EditBox field;

    public RenameMusicScreen(BlockPos station, String uid, String oldName, Screen parent) {
        super(Component.translatable("gui.broadcast.rename.title"));
        this.station = station;
        this.uid = uid;
        this.parent = parent;
        this.oldName = oldName;
    }

    private final String oldName;

    @Override
    protected void init() {
        int x = leftPos(), y = topPos();
        field = new EditBox(font, x + 14, y + 52, W - 28, 20,
                Component.translatable("gui.broadcast.rename.name"));
        field.setMaxLength(80);
        field.setTextColor(0xFFFFFFFF);
        field.setValue(oldName);
        addRenderableWidget(field);
        addRenderableWidget(Button.builder(Component.translatable("gui.broadcast.device.save"), b -> save())
                .bounds(x + 14, y + H - 34, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.broadcast.device.close"), b -> onClose())
                .bounds(x + W - 134, y + H - 34, 120, 20).build());
    }

    private int leftPos() {
        return (width - W) / 2;
    }

    private int topPos() {
        return (height - H) / 2;
    }

    private void save() {
        String name = field.getValue().trim();
        if (name.isEmpty()) return;
        NetworkHandler.sendToServer(new RenameMusicPacket(station, uid, name));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        GuiTheme.background(g, width, height);
        renderPanel(g);
    }

    private void renderPanel(GuiGraphics g) {
        int x = leftPos(), y = topPos();
        GuiTheme.rr(g, x, y, W, H, GuiTheme.PANEL, GuiTheme.BORDER_LIGHT);
        g.fill(x + 1, y + 1, x + W - 1, y + 27, GuiTheme.HEADER_TOP);
        g.drawString(font, Component.translatable("gui.broadcast.rename.title"), x + 12, y + 9, GuiTheme.TEXT, false);
        g.drawString(font, Component.translatable("gui.broadcast.rename.name"), x + 14, y + 40, GuiTheme.DIM, false);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
