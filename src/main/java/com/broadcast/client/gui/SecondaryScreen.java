package com.broadcast.client.gui;

import com.broadcast.client.ClientDataHolder;
import com.broadcast.client.ClientDataHolder.StationView;
import com.broadcast.network.NetworkHandler;
import com.broadcast.network.packet.RequestDataPacket;
import com.broadcast.network.packet.SetPlaylistPacket;
import com.broadcast.network.packet.SetPlaylistSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 二级 GUI:播放列表。
 * 左侧音乐库、右侧播放列表;支持循环、顺序/逆序、滚轮调整间隔与单首时长。
 */
public class SecondaryScreen extends Screen {
    private static final int W = 360;
    private static final int H = 250;
    private static final int ROW_H = 16;

    private final BlockPos station;
    private final Screen parent;

    private int selectedLibrary = -1;
    private int selectedPlaylist = -1;
    private int libraryScroll;
    private int playlistPage;
    private int gapValue = 60;
    private int trackValue = 30;
    private long lastValueSend;
    private int mouseX;
    private int mouseY;

    private UiButton loopButton;
    private UiButton orderButton;

    public SecondaryScreen(BlockPos station, Screen parent) {
        super(Component.translatable("gui.broadcast.playlist.title"));
        this.station = station;
        this.parent = parent;
    }

    @Override
    protected void init() {
        StationView view = ClientDataHolder.getView(station);
        if (view != null) {
            gapValue = view.playlistGap();
            trackValue = view.playlistTrackSeconds();
        }
        int x = leftPos();
        int y = topPos();

        addRenderableWidget(new UiButton(x + 168, y + 70, 24, 18,
                Component.literal(">"), b -> addSelected(), false));
        addRenderableWidget(new UiButton(x + 168, y + 94, 24, 18,
                Component.literal("<"), b -> removeSelected(), false));

        loopButton = new UiButton(x + 14, y + 198, 108, 18, loopLabel(), b -> toggleLoop(),
                view != null && view.playlistLoop());
        addRenderableWidget(loopButton);
        orderButton = new UiButton(x + 128, y + 198, 108, 18, orderLabel(), b -> toggleOrder(),
                view != null && view.playlistReverse());
        addRenderableWidget(orderButton);

        addRenderableWidget(new UiButton(x + 258, y + 222, 88, 18,
                Component.translatable("gui.broadcast.device.close"), b -> onClose(), false));

        NetworkHandler.sendToServer(new RequestDataPacket(station));
    }

    private int leftPos() {
        return (this.width - W) / 2;
    }

    private int topPos() {
        return (this.height - H) / 2;
    }

    private StationView view() {
        return ClientDataHolder.getView(station);
    }

    private Component loopLabel() {
        boolean on = view() != null && view().playlistLoop();
        return Component.translatable("gui.broadcast.playlist.loop",
                Component.translatable(on ? "gui.broadcast.on" : "gui.broadcast.off"));
    }

    private Component orderLabel() {
        boolean reverse = view() != null && view().playlistReverse();
        return Component.translatable("gui.broadcast.playlist.order",
                Component.translatable(reverse ? "gui.broadcast.playlist.reverse" : "gui.broadcast.playlist.forward"));
    }

    private void addSelected() {
        StationView view = view();
        if (view == null || selectedLibrary < 0 || selectedLibrary >= view.musicUids().size()) return;
        String uid = view.musicUids().get(selectedLibrary);
        List<String> next = new ArrayList<>(view.playlist());
        if (!next.contains(uid) && next.size() < 64) {
            next.add(uid);
            selectedPlaylist = next.size() - 1;
            NetworkHandler.sendToServer(new SetPlaylistPacket(station, next));
        }
    }

    private void removeSelected() {
        StationView view = view();
        if (view == null || selectedPlaylist < 0 || selectedPlaylist >= view.playlist().size()) return;
        List<String> next = new ArrayList<>(view.playlist());
        next.remove(selectedPlaylist);
        if (selectedPlaylist >= next.size()) selectedPlaylist = next.size() - 1;
        NetworkHandler.sendToServer(new SetPlaylistPacket(station, next));
    }

    private void toggleLoop() {
        StationView v = view();
        if (v == null) return;
        sendSettings(!v.playlistLoop(), v.playlistReverse(), gapValue, trackValue);
    }

    private void toggleOrder() {
        StationView v = view();
        if (v == null) return;
        sendSettings(v.playlistLoop(), !v.playlistReverse(), gapValue, trackValue);
    }

    private void sendSettings(boolean loop, boolean reverse, int gap, int track) {
        gapValue = Mth.clamp(gap, 0, 3600);
        trackValue = Mth.clamp(track, 5, 600);
        lastValueSend = System.currentTimeMillis();
        NetworkHandler.sendToServer(new SetPlaylistSettingsPacket(station, loop, reverse, gapValue, trackValue));
    }

    private String nameForUid(StationView view, String uid) {
        if (view == null) return uid;
        int idx = view.musicUids().indexOf(uid);
        return idx >= 0 && idx < view.music().size() ? view.music().get(idx) : uid;
    }

    /** 播放列表当前曲目:服务端 playlistIndex 指向“下一首”,播放中时当前曲目在反方向。 */
    private int currentIndex(StationView view) {
        if (view == null || view.playlist().isEmpty() || !view.playlistPlaying()) return -1;
        int size = view.playlist().size();
        return view.playlistReverse()
                ? (view.playlistIndex() + 1) % size
                : (view.playlistIndex() - 1 + size) % size;
    }

    private int pageCount(StationView view) {
        if (view == null || view.playlist().isEmpty()) return 1;
        return Math.max(1, (view.playlist().size() + 4) / 5);
    }

    private int upcomingIndex(StationView view, int slot) {
        int size = view.playlist().size();
        int step = view.playlistReverse() ? -1 : 1;
        int offset = playlistPage * 5 + slot;
        return Math.floorMod(view.playlistIndex() + step * offset, size);
    }

    // ---------- 渲染 ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        int x = leftPos();
        int y = topPos();
        GuiTheme.background(g, width, height);
        g.fill(x + 4, y + 4, x + W + 4, y + H + 4, 0x50000000);
        GuiTheme.rr(g, x, y, W, H, GuiTheme.PANEL, GuiTheme.BORDER_LIGHT);
        g.fillGradient(x + 1, y + 1, x + W - 1, y + 29, GuiTheme.HEADER_TOP, GuiTheme.HEADER_BOTTOM);
        g.fill(x + 1, y + 1, x + 4, y + 29, GuiTheme.ACCENT);

        g.drawString(font, Component.translatable("gui.broadcast.playlist.title"),
                x + 12, y + 8, GuiTheme.TEXT, false);
        g.drawString(font, Component.translatable("gui.broadcast.playlist.subtitle"),
                x + 12, y + 19, GuiTheme.FAINT, false);

        StationView view = view();
        if (view == null) {
            g.drawString(font, Component.translatable("gui.broadcast.device.loading"),
                    x + 16, y + 110, GuiTheme.FAINT, false);
            return;
        }

        // 音乐库
        int lx = x + 14, ly = y + 50, lw = 152, lh = 142;
        GuiTheme.rr(g, lx, ly, lw, lh, GuiTheme.CARD, GuiTheme.BORDER);
        g.drawString(font, Component.translatable("gui.broadcast.playlist.library",
                view.music().size()), lx + 8, ly + 6, GuiTheme.TEXT, false);
        g.fill(lx + 8, ly + 17, lx + lw - 8, ly + 18, GuiTheme.BORDER);
        int visible = 7;
        libraryScroll = Mth.clamp(libraryScroll, 0, Math.max(0, view.music().size() - visible));
        for (int i = 0; i < visible && libraryScroll + i < view.music().size(); i++) {
            int idx = libraryScroll + i;
            int ey = ly + 22 + i * ROW_H;
            boolean sel = idx == selectedLibrary;
            boolean hover = hovered(lx + 2, ey, lw - 8, ROW_H - 2);
            if (sel) GuiTheme.rr(g, lx + 2, ey, lw - 6, ROW_H - 2, GuiTheme.CARD_ACTIVE, GuiTheme.ACCENT_DARK);
            else if (hover) g.fill(lx + 2, ey, lx + lw - 4, ey + ROW_H - 2, GuiTheme.CARD_HOVER);
            String uid = view.musicUids().get(idx);
            g.drawString(font, uid, lx + 7, ey + 3, GuiTheme.ACCENT_HOVER, false);
            g.drawString(font, GuiTheme.trim(view.music().get(idx), 11), lx + 60, ey + 3,
                    sel ? GuiTheme.TEXT : GuiTheme.DIM, false);
        }
        drawScrollbar(g, lx + lw - 5, ly + 20, lh - 24, view.music().size(), visible, libraryScroll);

        // 播放列表:正在播放固定显示 + 将要播放每页 5 个,页数不设上限
        int px = x + 194, py = y + 50, pw = 152, ph = 142;
        GuiTheme.rr(g, px, py, pw, ph, GuiTheme.CARD, GuiTheme.BORDER);
        g.drawString(font, Component.translatable("gui.broadcast.playlist.now_playing"),
                px + 8, py + 6, GuiTheme.TEXT, false);
        int current = currentIndex(view);
        String currentName = current >= 0
                ? GuiTheme.trim(nameForUid(view, view.playlist().get(current)), 15)
                : Component.translatable("gui.broadcast.playlist.no_current").getString();
        g.drawString(font, currentName, px + 8, py + 18,
                current >= 0 ? GuiTheme.GREEN : GuiTheme.FAINT, false);
        g.fill(px + 8, py + 31, px + pw - 8, py + 32, GuiTheme.BORDER);

        g.drawString(font, Component.translatable("gui.broadcast.playlist.up_next"),
                px + 8, py + 36, GuiTheme.TEXT, false);
        int pages = pageCount(view);
        playlistPage = Mth.clamp(playlistPage, 0, pages - 1);
        g.drawString(font, (playlistPage + 1) + "/" + pages, px + pw - 34, py + 36,
                GuiTheme.FAINT, false);

        if (view.playlist().isEmpty()) {
            g.drawString(font, Component.translatable("gui.broadcast.playlist.empty"),
                    px + 8, py + 70, GuiTheme.FAINT, false);
        } else {
            for (int i = 0; i < 5; i++) {
                int idx = upcomingIndex(view, i);
                int ey = py + 48 + i * ROW_H;
                boolean sel = idx == selectedPlaylist;
                boolean hover = hovered(px + 2, ey, pw - 8, ROW_H - 2);
                if (sel) GuiTheme.rr(g, px + 2, ey, pw - 6, ROW_H - 2, GuiTheme.CARD_ACTIVE, GuiTheme.ACCENT_DARK);
                else if (hover) g.fill(px + 2, ey, px + pw - 4, ey + ROW_H - 2, GuiTheme.CARD_HOVER);
                String uid = view.playlist().get(idx);
                g.drawString(font, (playlistPage * 5 + i + 1) + ".", px + 7, ey + 3, GuiTheme.FAINT, false);
                g.drawString(font, GuiTheme.trim(nameForUid(view, uid), 14), px + 24, ey + 3,
                        sel ? GuiTheme.TEXT : GuiTheme.DIM, false);
            }
        }
        // 页码切换(无限页)
        int arrowY = py + 130;
        boolean leftHover = hovered(px + 8, arrowY, 15, 11);
        boolean rightHover = hovered(px + pw - 23, arrowY, 15, 11);
        g.drawString(font, "<", px + 10, arrowY, leftHover ? GuiTheme.ACCENT_HOVER : GuiTheme.FAINT, false);
        g.drawString(font, ">", px + pw - 21, arrowY, rightHover ? GuiTheme.ACCENT_HOVER : GuiTheme.FAINT, false);

        // 设置
        drawValueField(g, x + 242, y + 198, 104, 18,
                Component.translatable("gui.broadcast.playlist.gap", gapValue), hovered(x + 242, y + 198, 104, 18));
        drawValueField(g, x + 14, y + 222, 108, 18,
                Component.translatable("gui.broadcast.playlist.track", trackValue), hovered(x + 14, y + 222, 108, 18));
        g.drawString(font, Component.translatable("gui.broadcast.playlist.scroll_hint"),
                x + 128, y + 226, GuiTheme.FAINT, false);

        if (loopButton != null) {
            loopButton.setMessage(loopLabel());
            loopButton.setAccent(view.playlistLoop());
        }
        if (orderButton != null) {
            orderButton.setMessage(orderLabel());
            orderButton.setAccent(view.playlistReverse());
        }
    }

    private void drawValueField(GuiGraphics g, int x, int y, int w, int h, Component text, boolean hover) {
        GuiTheme.rr(g, x, y, w, h, hover ? GuiTheme.CARD_HOVER : GuiTheme.CARD,
                hover ? GuiTheme.ACCENT : GuiTheme.BORDER);
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, GuiTheme.TEXT);
    }

    private boolean hovered(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void drawScrollbar(GuiGraphics g, int x, int y, int h, int total, int visible, int offset) {
        if (total <= visible) return;
        int barH = Math.max(8, h * visible / total);
        int max = total - visible;
        int barY = y + (h - barH) * offset / Math.max(1, max);
        g.fill(x, y, x + 2, y + h, GuiTheme.BORDER);
        g.fill(x, barY, x + 2, barY + barH, GuiTheme.ACCENT);
    }

    // ---------- 输入 ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        StationView view = view();
        int x = leftPos();
        int y = topPos();
        if (view != null) {
            int lx = x + 14, ly = y + 50;
            if (mx >= lx && mx < lx + 152 && my >= ly + 22 && my < ly + 138) {
                int idx = (int) ((my - (ly + 22)) / ROW_H) + libraryScroll;
                if (idx >= 0 && idx < view.music().size()) selectedLibrary = idx;
                return true;
            }
            int px = x + 194, py = y + 50;
            if (mx >= px && mx < px + 152 && my >= py + 48 && my < py + 128) {
                int slot = (int) ((my - (py + 48)) / ROW_H);
                if (slot >= 0 && slot < 5) {
                    selectedPlaylist = upcomingIndex(view, slot);
                }
                return true;
            }
            if (my >= py + 130 && my < py + 141) {
                int pages = pageCount(view);
                if (mx >= px + 8 && mx < px + 23) {
                    playlistPage = Math.floorMod(playlistPage - 1, pages);
                    return true;
                }
                if (mx >= px + 129 && mx < px + 144) {
                    playlistPage = Math.floorMod(playlistPage + 1, pages);
                    return true;
                }
            }
            // 滚轮数值框:左键 +，右键 -
            if (mx >= x + 242 && mx < x + 346 && my >= y + 198 && my < y + 216) {
                sendSettings(view.playlistLoop(), view.playlistReverse(),
                        gapValue + (button == 0 ? 15 : -15), trackValue);
                return true;
            }
            if (mx >= x + 14 && mx < x + 122 && my >= y + 222 && my < y + 240) {
                sendSettings(view.playlistLoop(), view.playlistReverse(),
                        gapValue, trackValue + (button == 0 ? 5 : -5));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        StationView view = view();
        int x = leftPos();
        int y = topPos();
        if (view != null) {
            if (mx >= x + 242 && mx < x + 346 && my >= y + 198 && my < y + 216) {
                sendSettings(view.playlistLoop(), view.playlistReverse(),
                        gapValue + (int) Math.signum(delta) * 15, trackValue);
                return true;
            }
            if (mx >= x + 14 && mx < x + 122 && my >= y + 222 && my < y + 240) {
                sendSettings(view.playlistLoop(), view.playlistReverse(),
                        gapValue, trackValue + (int) Math.signum(delta) * 5);
                return true;
            }
            if (mx >= x + 14 && mx < x + 166 && my >= y + 50 && my < y + 192) {
                libraryScroll = Mth.clamp(libraryScroll - (int) Math.signum(delta), 0,
                        Math.max(0, view.music().size() - 7));
                return true;
            }
            if (mx >= x + 194 && mx < x + 346 && my >= y + 50 && my < y + 192) {
                int pages = pageCount(view);
                playlistPage = Math.floorMod(playlistPage - (int) Math.signum(delta), pages);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void tick() {
        super.tick();
        StationView view = view();
        if (view != null && System.currentTimeMillis() - lastValueSend > 800) {
            if (gapValue != view.playlistGap()) gapValue = view.playlistGap();
            if (trackValue != view.playlistTrackSeconds()) trackValue = view.playlistTrackSeconds();
            if (selectedPlaylist >= view.playlist().size()) selectedPlaylist = view.playlist().size() - 1;
            playlistPage = Mth.clamp(playlistPage, 0, pageCount(view) - 1);
        }
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
