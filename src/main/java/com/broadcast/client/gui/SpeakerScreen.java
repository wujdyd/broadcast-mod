package com.broadcast.client.gui;

import com.broadcast.client.ClientDataHolder;
import com.broadcast.client.ClientDataHolder.DeviceView;
import com.broadcast.menu.SpeakerMenu;
import com.broadcast.network.NetworkHandler;
import com.broadcast.network.packet.RequestDeviceInfoPacket;
import com.broadcast.network.packet.SetSpeakerFrequencyPacket;
import com.broadcast.util.FrequencyUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 广播器 GUI:设置频率。
 * 频率相同的广播器会接收来自同一广播站(多频率同步)和同一话筒的声音。
 */
public class SpeakerScreen extends AbstractContainerScreen<SpeakerMenu> {
    private static final int W = 240;
    private static final int H = 176;

    private EditBox freqField;
    private UiButton saveButton;
    private String lastSent = "";
    private int refreshTimer;

    public SpeakerScreen(SpeakerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        DeviceView view = ClientDataHolder.getDevice(menu.pos);
        lastSent = view == null ? "" : view.frequency();

        freqField = new EditBox(font, leftPos + 16, topPos + 58, W - 32, 18,
                Component.translatable("gui.broadcast.device.frequency"));
        freqField.setMaxLength(FrequencyUtil.MAX_LENGTH);
        freqField.setValue(lastSent);
        addRenderableWidget(freqField);

        saveButton = new UiButton(leftPos + 16, topPos + 84, 100, 18,
                Component.translatable("gui.broadcast.device.save"), b -> save(), true);
        addRenderableWidget(saveButton);
        addRenderableWidget(new UiButton(leftPos + 124, topPos + 84, 100, 18,
                Component.translatable("gui.broadcast.device.close"), b -> onClose(), false));

        NetworkHandler.sendToServer(new RequestDeviceInfoPacket(menu.pos));
    }

    private void save() {
        String f = FrequencyUtil.normalize(freqField.getValue());
        if (f.isEmpty()) {
            freqField.setTextColor(GuiTheme.RED);
            return;
        }
        freqField.setTextColor(GuiTheme.TEXT);
        freqField.setValue(f);
        lastSent = f;
        NetworkHandler.sendToServer(new SetSpeakerFrequencyPacket(menu.pos, f));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (++refreshTimer >= 20) {
            refreshTimer = 0;
            NetworkHandler.sendToServer(new RequestDeviceInfoPacket(menu.pos));
        }
        DeviceView view = ClientDataHolder.getDevice(menu.pos);
        if (view != null && !freqField.isFocused()
                && (freqField.getValue().isEmpty() || freqField.getValue().equals(lastSent))) {
            if (!view.frequency().equals(freqField.getValue())) {
                freqField.setValue(view.frequency());
            }
            lastSent = view.frequency();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        GuiTheme.background(g, width, height);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos;
        int y = topPos;
        g.fill(x + 3, y + 3, x + W + 3, y + H + 3, 0x50000000);
        GuiTheme.rr(g, x, y, W, H, GuiTheme.PANEL, GuiTheme.BORDER_LIGHT);
        g.fillGradient(x + 1, y + 1, x + W - 1, y + 27, GuiTheme.HEADER_TOP, GuiTheme.HEADER_BOTTOM);
        g.fill(x + 1, y + 1, x + 4, y + 27, GuiTheme.ACCENT);

        Component title = blockTitle();
        g.drawString(font, title, x + 12, y + 8, GuiTheme.TEXT, false);
        g.drawString(font, Component.translatable("gui.broadcast.device.speaker_subtitle"),
                x + 12, y + 18, GuiTheme.FAINT, false);

        g.drawString(font, Component.translatable("gui.broadcast.device.frequency"),
                x + 16, y + 46, GuiTheme.DIM, false);
        // 输入框底衬
        GuiTheme.rr(g, x + 15, y + 57, W - 30, 20, GuiTheme.CARD, GuiTheme.BORDER);

        DeviceView view = ClientDataHolder.getDevice(menu.pos);
        GuiTheme.rr(g, x + 16, y + 110, W - 32, 42, GuiTheme.CARD, GuiTheme.BORDER);
        if (view == null) {
            g.drawString(font, Component.translatable("gui.broadcast.device.loading"),
                    x + 24, y + 126, GuiTheme.FAINT, false);
        } else {
            g.drawString(font, Component.translatable("gui.broadcast.device.current_frequency",
                            view.frequency()), x + 24, y + 116, GuiTheme.TEXT, false);
            if (view.stations() > 0) {
                g.drawString(font, Component.translatable("gui.broadcast.device.linked_stations",
                        view.stations()), x + 24, y + 129, GuiTheme.GREEN, false);
            } else {
                g.drawString(font, Component.translatable("gui.broadcast.device.no_station"),
                        x + 24, y + 129, GuiTheme.ORANGE, false);
            }
            g.drawString(font, Component.translatable(view.powered()
                            ? "gui.broadcast.device.redstone_on" : "gui.broadcast.device.redstone_off"),
                    x + 24, y + 141, view.powered() ? GuiTheme.GREEN : GuiTheme.DIM, false);
        }

        g.drawString(font, Component.translatable("gui.broadcast.device.speaker_hint"),
                x + 16, y + 160, GuiTheme.FAINT, false);
    }

    private Component blockTitle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            var block = mc.level.getBlockState(menu.pos).getBlock();
            return Component.translatable(block.getDescriptionId());
        }
        return Component.translatable("gui.broadcast.device.speaker");
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // 自绘
    }
}
