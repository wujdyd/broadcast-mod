package com.broadcast.client.gui;

import com.broadcast.client.ClientDataHolder;
import com.broadcast.item.PortableMicrophoneItem;
import com.broadcast.menu.PortableMicMenu;
import com.broadcast.network.NetworkHandler;
import com.broadcast.network.packet.SetPortableMicSettingsPacket;
import com.broadcast.util.FrequencyUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** 便携式麦克风 GUI:频率、讲话时是否暂停音乐。 */
public class PortableMicScreen extends AbstractContainerScreen<PortableMicMenu> {
    private static final int W = 240;
    private static final int H = 190;

    private EditBox freqField;
    private UiButton pauseButton;
    private boolean pauseMusic = true;
    private String lastSent = "";

    public PortableMicScreen(PortableMicMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    private ItemStack stack() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        return mc.player.getItemInHand(menu.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
    }

    @Override
    protected void init() {
        super.init();
        ItemStack stack = stack();
        lastSent = PortableMicrophoneItem.getFrequency(stack);
        pauseMusic = PortableMicrophoneItem.isPauseMusic(stack);

        freqField = new EditBox(font, leftPos + 16, topPos + 60, W - 32, 18,
                Component.translatable("gui.broadcast.device.frequency"));
        freqField.setMaxLength(FrequencyUtil.MAX_LENGTH);
        freqField.setValue(lastSent);
        addRenderableWidget(freqField);

        pauseButton = new UiButton(leftPos + 16, topPos + 106, W - 32, 18, pauseLabel(),
                b -> {
                    pauseMusic = !pauseMusic;
                    b.setMessage(pauseLabel());
                    ((UiButton) b).setAccent(pauseMusic);
                }, pauseMusic);
        addRenderableWidget(pauseButton);

        addRenderableWidget(new UiButton(leftPos + 16, topPos + 148, 100, 18,
                Component.translatable("gui.broadcast.device.save"), b -> save(), true));
        addRenderableWidget(new UiButton(leftPos + 124, topPos + 148, 100, 18,
                Component.translatable("gui.broadcast.device.close"), b -> onClose(), false));
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
        NetworkHandler.sendToServer(new SetPortableMicSettingsPacket(menu.offhand, f, pauseMusic));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (freqField.isFocused()) return;
        ItemStack stack = stack();
        if (stack.isEmpty() || !(stack.getItem() instanceof PortableMicrophoneItem)) return;
        String serverFreq = PortableMicrophoneItem.getFrequency(stack);
        boolean serverPause = PortableMicrophoneItem.isPauseMusic(stack);
        if (pauseMusic != serverPause) {
            pauseMusic = serverPause;
            pauseButton.setMessage(pauseLabel());
            pauseButton.setAccent(pauseMusic);
        }
        if (freqField.getValue().isEmpty() || freqField.getValue().equals(lastSent)) {
            if (!serverFreq.equals(freqField.getValue())) freqField.setValue(serverFreq);
            lastSent = serverFreq;
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

        g.drawString(font, Component.translatable("item.broadcast.portable_microphone"),
                x + 12, y + 8, GuiTheme.TEXT, false);
        g.drawString(font, Component.translatable("gui.broadcast.device.portable_subtitle"),
                x + 12, y + 18, GuiTheme.FAINT, false);

        g.drawString(font, Component.translatable("gui.broadcast.device.frequency"),
                x + 16, y + 48, GuiTheme.DIM, false);
        GuiTheme.rr(g, x + 15, y + 59, W - 30, 20, GuiTheme.CARD, GuiTheme.BORDER);
        g.drawString(font, Component.translatable("gui.broadcast.device.pause_music_label"),
                x + 16, y + 94, GuiTheme.DIM, false);

        g.drawString(font, Component.translatable("gui.broadcast.device.portable_hint1"),
                x + 16, y + 130, GuiTheme.FAINT, false);
        g.drawString(font, Component.translatable("gui.broadcast.device.portable_hint2"),
                x + 16, y + 140, GuiTheme.FAINT, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // 自绘
    }
}
