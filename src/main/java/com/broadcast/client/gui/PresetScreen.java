package com.broadcast.client.gui;

import com.broadcast.client.ClientDataHolder;
import com.broadcast.network.NetworkHandler;
import com.broadcast.network.packet.ApplyPresetPacket;
import com.broadcast.network.packet.RequestPresetsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 预设选择页(替代旧播放列表)。 */
public class PresetScreen extends Screen {
    private static final int W = 300, H = 220, ROW = 18;

    private final BlockPos station;
    private final Screen parent;
    private EditBox search;
    private String query = "";
    private int selected = -1;
    private int scroll;

    public PresetScreen(BlockPos station, Screen parent) {
        super(Component.translatable("gui.broadcast.preset.title"));
        this.station = station;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = leftPos(), y = topPos();
        search = new EditBox(font, x + 14, y + 36, W - 28, 18,
                Component.translatable("gui.broadcast.preset.search"));
        search.setHint(Component.empty());
        search.setTextColor(0xFFFFFFFF);
        search.setResponder(v -> {
            query = v == null ? "" : v;
            selected = -1;
            scroll = 0;
        });
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.translatable("gui.broadcast.preset.apply"), b -> apply())
                .bounds(x + 14, y + H - 30, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.broadcast.device.close"), b -> onClose())
                .bounds(x + W - 134, y + H - 30, 120, 20).build());
        NetworkHandler.sendToServer(new RequestPresetsPacket());
    }

    private int leftPos() {
        return (width - W) / 2;
    }

    private int topPos() {
        return (height - H) / 2;
    }

    private List<String> filtered() {
        List<String> out = new ArrayList<>();
        for (String name : ClientDataHolder.presets()) {
            if (query.isBlank() || name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                out.add(name);
            }
        }
        return out;
    }

    private void apply() {
        List<String> list = filtered();
        if (selected >= 0 && selected < list.size()) {
            NetworkHandler.sendToServer(new ApplyPresetPacket(station, list.get(selected)));
        }
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        GuiTheme.background(g, width, height);
        renderPanel(g, 0, 0);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
    }

    private void renderPanel(GuiGraphics g, int mx, int my) {
        int x = leftPos(), y = topPos();
        GuiTheme.rr(g, x, y, W, H, GuiTheme.PANEL, GuiTheme.BORDER_LIGHT);
        g.fill(x + 1, y + 1, x + W - 1, y + 27, GuiTheme.HEADER_TOP);
        g.drawString(font, Component.translatable("gui.broadcast.preset.title"), x + 12, y + 9, GuiTheme.TEXT, false);
        if (search != null && search.getValue().isEmpty() && !search.isFocused()) {
            g.drawString(font, Component.translatable("gui.broadcast.preset.search"),
                    x + 18, y + 41, 0xFFFFFFFF, false);
        }
        List<String> list = filtered();
        int visible = 7;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - visible)));
        for (int i = 0; i < visible && scroll + i < list.size(); i++) {
            int idx = scroll + i;
            int ey = y + 62 + i * ROW;
            boolean sel = idx == selected;
            boolean hover = mx >= x + 12 && mx < x + W - 12 && my >= ey && my < ey + ROW - 2;
            if (sel) GuiTheme.rr(g, x + 12, ey, W - 24, ROW - 2, GuiTheme.CARD_ACTIVE, GuiTheme.ACCENT);
            else if (hover) g.fill(x + 12, ey, x + W - 12, ey + ROW - 2, GuiTheme.CARD_HOVER);
            g.drawString(font, list.get(idx), x + 18, ey + 4, sel ? GuiTheme.TEXT : GuiTheme.DIM, false);
        }
        if (list.isEmpty()) {
            g.drawString(font, Component.translatable("gui.broadcast.preset.empty"),
                    x + 18, y + 80, GuiTheme.FAINT, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos(), y = topPos();
        List<String> list = filtered();
        if (mx >= x + 12 && mx < x + W - 12 && my >= y + 62 && my < y + 62 + 7 * ROW) {
            int idx = (int) ((my - (y + 62)) / ROW) + scroll;
            if (idx >= 0 && idx < list.size()) selected = idx;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, filtered().size() - 7);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
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
