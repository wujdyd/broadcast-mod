package com.broadcast.media.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 客户端下载请求页。 */
public class MediaToolsScreen extends Screen {
    private static final int W = 360;
    private static final int H = 220;

    public MediaToolsScreen() {
        super(Component.literal("Broadcast Media Support"));
    }

    @Override
    protected void init() {
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        addRenderableWidget(Button.builder(Component.literal("请求服务端下载工具"),
                        b -> requestDownload())
                .bounds(x + 20, y + 150, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
                .bounds(x + 190, y + 150, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("yt-dlp 下载页"),
                        b -> Util.getPlatform().openUri("https://github.com/yt-dlp/yt-dlp/releases"))
                .bounds(x + 20, y + 178, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ffmpeg 下载页"),
                        b -> Util.getPlatform().openUri("https://www.gyan.dev/ffmpeg/builds/"))
                .bounds(x + 130, y + 178, 100, 20).build());
    }

    private void requestDownload() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand("broadcastmediasupport download");
            mc.player.displayClientMessage(Component.literal("已向服务器发送下载请求"), true);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fillGradient(0, 0, width, height, 0xC0101018, 0xE0101018);
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFF5A6A7A);
        g.fill(x, y, x + W, y + H, 0xFF202833);
        g.fill(x, y, x + W, y + 30, 0xFF2B3A4A);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        g.drawString(font, "Broadcast Media Support", x + 14, y + 10, 0xFFE8F0FF, false);
        g.drawString(font, "下载媒体工具需要你的明确同意", x + 14, y + 40, 0xFFFFD37A, false);
        g.drawString(font, "点击“请求服务端下载工具”后才会开始下载：", x + 14, y + 58, 0xFFD0D8E0, false);
        g.drawString(font, "· yt-dlp（约 17MB）", x + 22, y + 74, 0xFFD0D8E0, false);
        g.drawString(font, "· ffmpeg（约 80MB）", x + 22, y + 88, 0xFFD0D8E0, false);
        g.drawString(font, "下载目录：config/broadcast_media_support/tools/", x + 14, y + 108, 0xFF9FB0C0, false);
        g.drawString(font, "播放视频时不会静默下载任何文件。", x + 14, y + 124, 0xFF9FB0C0, false);
        g.drawString(font, "快捷键 F9 可再次打开此页。", x + 14, y + 136, 0xFF7F90A0, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
