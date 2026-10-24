package com.broadcast.client.gui;

import com.broadcast.blockentity.MicrophoneBlockEntity.RedstoneMode;
import com.broadcast.client.ClientDataHolder;
import com.broadcast.client.ClientDataHolder.DeviceView;
import com.broadcast.menu.MicrophoneMenu;
import com.broadcast.network.NetworkHandler;
import com.broadcast.network.packet.RequestDeviceInfoPacket;
import com.broadcast.network.packet.SetMicSettingsPacket;
import com.broadcast.util.FrequencyUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 话筒 GUI:频率、红石控制模式、讲话时是否暂停音乐。
 */
public class MicrophoneScreen extends AbstractContainerScreen<MicrophoneMenu> {
    private static final int W = 240;
    private static final int H = 224;

    private EditBox freqField;
    private UiButton modeButton;
    private UiButton pauseButton;
    private String lastSent = "";
    private int modeIndex = RedstoneMode.SELF.ordinal();
    private boolean pauseMusic = true;
    private boolean dirty;
    private int refreshTimer;

    public MicrophoneScreen(MicrophoneMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        DeviceView view = ClientDataHolder.getDevice(menu.pos);
        if (view != null) {
            lastSent = view.frequency();
            modeIndex = view.redstoneMode();
            pauseMusic = view.pauseMusic();
        }

        freqField = new EditBox(font, leftPos + 16, topPos + 56, W - 32, 18,
                Component.translatable("gui.broadcast.device.frequency"));
        freqField.setMaxLength(FrequencyUtil.MAX_LENGTH);
        freqField.setValue(lastSent);
        addRenderableWidget(freqField);

        modeButton = new UiButton(leftPos + 16, topPos + 92, W - 32, 18, modeLabel(),
                b -> {
                    modeIndex = (modeIndex + 1) % RedstoneMode.values().length;
                    dirty = true;
                    b.setMessage(modeLabel());
                    ((UiButton) b).setAccent(modeIndex == RedstoneMode.ALL.ordinal());
                }, modeIndex == RedstoneMode.ALL.ordinal());
        addRenderableWidget(modeButton);

        pauseButton = new UiButton(leftPos + 16, topPos + 126, W - 32, 18, pauseLabel(),
                b -> {
                    pauseMusic = !pauseMusic;
                    dirty = true;
                    b.setMessage(pauseLabel());
                    ((UiButton) b).setAccent(pauseMusic);
                }, pauseMusic);
        addRenderableWidget(pauseButton);

        addRenderableWidget(new UiButton(leftPos + 16, topPos + 198, 100, 18,
                Component.translatable("gui.broadcast.device.save"), b -> save(), true));
        addRenderableWidget(new UiButton(leftPos + 124, topPos + 198, 100, 18,
                Component.translatable("gui.broadcast.device.close"), b -> onClose(), false));

        NetworkHandler.sendToServer(new RequestDeviceInfoPacket(menu.pos));
    }

    private Component modeLabel() {
        RedstoneMode mode = RedstoneMode.byOrdinal(modeIndex);
        return Component.translatable("gui.broadcast.device.redstone_mode",
                Component.translatable("gui.broadcast.device.mode." + mode.name().toLowerCase()));
    }

    private Component pauseLabel() {
        return Component.translatable("gui.broadcast.device.pause_music",
                Component.translatable(pauseMusic ? "gui.broadcast.on" : "gui.broadcast.off"));
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
        dirty = false;
        NetworkHandler.sendToServer(new SetMicSettingsPacket(menu.pos, f, modeIndex, pauseMusic));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (++refreshTimer >= 20) {
            refreshTimer = 0;
            NetworkHandler.sendToServer(new RequestDeviceInfoPacket(menu.pos));
        }
        DeviceView view = ClientDataHolder.getDevice(menu.pos);
        if (view == null || freqField.isFocused()) return;
        if (!dirty) {
            if (modeIndex != view.redstoneMode()) modeIndex = view.redstoneMode();
            if (pauseMusic != view.pauseMusic()) pauseMusic = view.pauseMusic();
            modeButton.setMessage(modeLabel());
            modeButton.setAccent(modeIndex == RedstoneMode.ALL.ordinal());
            pauseButton.setMessage(pauseLabel());
            pauseButton.setAccent(pauseMusic);
            if (freqField.getValue().isEmpty() || freqField.getValue().equals(lastSent)) {
                freqField.setValue(view.frequency());
                lastSent = view.frequency();
            }
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

        g.drawString(font, Component.translatable("block.broadcast.microphone"),
                x + 12, y + 8, GuiTheme.TEXT, false);
        g.drawString(font, Component.translatable("gui.broadcast.device.mic_subtitle"),
                x + 12, y + 18, GuiTheme.FAINT, false);

        g.drawString(font, Component.translatable("gui.broadcast.device.frequency"),
                x + 16, y + 44, GuiTheme.DIM, false);
        GuiTheme.rr(g, x + 15, y + 55, W - 30, 20, GuiTheme.CARD, GuiTheme.BORDER);
        g.drawString(font, Component.translatable("gui.broadcast.device.redstone_mode_label"),
                x + 16, y + 80, GuiTheme.DIM, false);
        g.drawString(font, Component.translatable("gui.broadcast.device.pause_music_label"),
                x + 16, y + 114, GuiTheme.DIM, false);

        DeviceView view = ClientDataHolder.getDevice(menu.pos);
        GuiTheme.rr(g, x + 16, y + 150, W - 32, 42, GuiTheme.CARD, GuiTheme.BORDER);
        if (view == null) {
            g.drawString(font, Component.translatable("gui.broadcast.device.loading"),
                    x + 24, y + 162, GuiTheme.FAINT, false);
        } else {
            g.drawString(font, Component.translatable(view.active()
                            ? "gui.broadcast.device.talking" : "gui.broadcast.device.idle",
                            Component.translatable("gui.broadcast.device.frequency_short", view.frequency())),
                    x + 24, y + 156, view.active() ? GuiTheme.GREEN : GuiTheme.DIM, false);
            g.drawString(font, Component.translatable("gui.broadcast.device.speakers_status",
                            view.speakers(), view.poweredSpeakers(), view.detectionRadius()),
                    x + 24, y + 170, view.speakers() > 0 ? GuiTheme.TEXT : GuiTheme.ORANGE, false);
            g.drawString(font, Component.translatable("gui.broadcast.device.mic_extra",
                            view.stations(), view.volumePercent()),
                    x + 24, y + 182, GuiTheme.FAINT, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // 自绘
    }
}
