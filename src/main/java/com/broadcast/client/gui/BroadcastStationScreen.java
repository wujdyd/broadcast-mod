package com.broadcast.client.gui;

import com.broadcast.client.ClientDataHolder;
import com.broadcast.client.ClientDataHolder.StationView;
import com.broadcast.menu.BroadcastStationMenu;
import com.broadcast.network.NetworkHandler;
import com.broadcast.network.packet.DeleteMusicPacket;
import com.broadcast.network.packet.PlayRequestPacket;
import com.broadcast.network.packet.RequestDataPacket;
import com.broadcast.network.packet.SetSettingsPacket;
import com.broadcast.network.packet.SetStationFrequenciesPacket;
import com.broadcast.network.packet.StopPlaybackPacket;
import com.broadcast.util.FrequencyUtil;
import com.broadcast.util.NameUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 广播站主 GUI:音乐库(搜索/删除/播放列表入口)、网络音乐、频率、设备管理。
 */
public class BroadcastStationScreen extends AbstractContainerScreen<BroadcastStationMenu> {

    private static final int W = 280;
    private static final int H = 246;
    private static final int TAB_Y = 32;
    private static final int TAB_W = 62;
    private static final int TAB_GAP = 4;
    private static final int CONTENT_Y = 58;

    private enum Tab {
        MUSIC("gui.broadcast.tab.music"),
        URL("gui.broadcast.tab.url"),
        FREQ("gui.broadcast.tab.freq"),
        DEVICES("gui.broadcast.tab.devices");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private Tab tab = Tab.MUSIC;
    private int selectedIndex = -1;
    private int selectedFreq = -1;
    private int scroll;
    private int urlScroll;
    private int freqScroll;
    private long lastClickMs;

    private EditBox urlField;
    private EditBox freqField;
    private EditBox searchField;
    private String musicSearch = "";
    private String confirmUid = "";

    private VolumeSlider volumeSlider;
    private AccentButton redstoneButton;
    private AccentButton btnDelete;
    private Button btnPlay;
    private Button btnStop;
    private Button btnRefresh;
    private AccentButton btnPlaylist;

    private volatile String uploadStatus = "";
    private long uploadStatusAt;

    public BroadcastStationScreen(BroadcastStationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        urlField = null;
        freqField = null;
        searchField = null;
        buildWidgets();
        NetworkHandler.sendToServer(new RequestDataPacket(menu.pos));
    }

    // ---------- 组件 ----------

    private void buildWidgets() {
        clearCustomWidgets();
        int x = leftPos;
        int y = topPos;

        volumeSlider = new VolumeSlider(x + 40, y + 212, 118, 12,
                menu.data.get(BroadcastStationMenu.DATA_VOLUME) / 100D);
        addRenderableWidget(volumeSlider);
        redstoneButton = new AccentButton(x + 188, y + 210, 80, 16,
                redstoneLabel(), b -> toggleRedstone(),
                menu.data.get(BroadcastStationMenu.DATA_REDSTONE) != 0);
        addRenderableWidget(redstoneButton);

        switch (tab) {
            case MUSIC -> {
                searchField = new EditBox(font, x + 12, y + 59, 122, 16,
                        Component.translatable("gui.broadcast.search"));
                searchField.setMaxLength(64);
                searchField.setHint(Component.translatable("gui.broadcast.search"));
                searchField.setValue(musicSearch);
                searchField.setResponder(v -> {
                    musicSearch = v == null ? "" : v;
                    selectedIndex = -1;
                    confirmUid = "";
                    scroll = 0;
                });
                addRenderableWidget(searchField);

                btnDelete = new AccentButton(x + 138, y + 58, 48, 18,
                        deleteLabel(), b -> deleteSelected(), false);
                addRenderableWidget(btnDelete);

                btnPlay = new AccentButton(x + 194, y + 58, 76, 18,
                        Component.translatable("gui.broadcast.play"), b -> playSelected(), true);
                btnStop = new AccentButton(x + 194, y + 80, 76, 18,
                        Component.translatable("gui.broadcast.stop"), b -> stopPlayback(), false);
                btnRefresh = new AccentButton(x + 194, y + 102, 76, 18,
                        Component.translatable("gui.broadcast.refresh"), b -> requestData(), false);
                btnPlaylist = new AccentButton(x + 194, y + 124, 76, 18,
                        Component.translatable("gui.broadcast.playlist.open"), b -> openPlaylist(), false);
                addRenderableWidget(btnPlay);
                addRenderableWidget(btnStop);
                addRenderableWidget(btnRefresh);
                addRenderableWidget(btnPlaylist);
            }
            case URL -> {
                urlField = new EditBox(font, x + 12, y + 70, 256, 18, Component.empty());
                urlField.setMaxLength(512);
                urlField.setHint(Component.translatable("gui.broadcast.url_placeholder"));
                addRenderableWidget(urlField);
                btnPlay = new AccentButton(x + 12, y + 96, 124, 18,
                        Component.translatable("gui.broadcast.play"), b -> playUrl(), true);
                btnStop = new AccentButton(x + 144, y + 96, 124, 18,
                        Component.translatable("gui.broadcast.stop"), b -> stopPlayback(), false);
                addRenderableWidget(btnPlay);
                addRenderableWidget(btnStop);
            }
            case FREQ -> {
                freqField = new EditBox(font, x + 12, y + 142, 132, 18,
                        Component.translatable("gui.broadcast.freq.placeholder"));
                freqField.setMaxLength(FrequencyUtil.MAX_LENGTH);
                freqField.setHint(Component.translatable("gui.broadcast.freq.placeholder"));
                addRenderableWidget(freqField);

                addRenderableWidget(new AccentButton(x + 148, y + 142, 50, 18,
                        Component.translatable("gui.broadcast.freq.add"), b -> addFrequency(), true));
                addRenderableWidget(new AccentButton(x + 202, y + 142, 66, 18,
                        Component.translatable("gui.broadcast.freq.remove"), b -> removeFrequency(), false));
            }
            case DEVICES -> {
                btnRefresh = new AccentButton(x + 202, y + 137, 58, 24,
                        Component.translatable("gui.broadcast.refresh"), b -> requestData(), false);
                addRenderableWidget(btnRefresh);
            }
        }
    }

    private void clearCustomWidgets() {
        List<AbstractWidget> toRemove = new ArrayList<>();
        for (Renderable w : this.renderables) {
            if (w instanceof AccentButton || w instanceof VolumeSlider || w instanceof EditBox) {
                toRemove.add((AbstractWidget) w);
            }
        }
        for (AbstractWidget w : toRemove) {
            removeWidget(w);
        }
    }

    private void requestData() {
        NetworkHandler.sendToServer(new RequestDataPacket(menu.pos));
    }

    private void switchTab(Tab t) {
        if (tab == t) return;
        tab = t;
        selectedIndex = -1;
        selectedFreq = -1;
        confirmUid = "";
        scroll = 0;
        urlScroll = 0;
        freqScroll = 0;
        buildWidgets();
    }

    private void openPlaylist() {
        if (minecraft != null) {
            minecraft.setScreen(new SecondaryScreen(menu.pos, this));
        }
    }

    // ---------- 操作 ----------

    private List<String> filteredMusic(StationView view) {
        if (view == null) return List.of();
        String q = musicSearch == null ? "" : musicSearch.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        List<String> files = view.music();
        List<String> uids = view.musicUids();
        for (int i = 0; i < files.size(); i++) {
            String name = files.get(i).toLowerCase(Locale.ROOT);
            String uid = i < uids.size() ? uids.get(i) : "";
            if (q.isEmpty() || name.contains(q) || uid.contains(q)) out.add(files.get(i));
        }
        return out;
    }

    /** 当前过滤列表中某首歌对应的 UID。 */
    private String uidAt(StationView view, int filteredIndex) {
        if (view == null) return "";
        String name = filteredMusic(view).get(filteredIndex);
        int idx = view.music().indexOf(name);
        return idx >= 0 && idx < view.musicUids().size() ? view.musicUids().get(idx) : "";
    }

    private void playSelected() {
        StationView view = ClientDataHolder.getView(menu.pos);
        List<String> files = filteredMusic(view);
        if (selectedIndex < 0 || selectedIndex >= files.size()) return;
        NetworkHandler.sendToServer(new PlayRequestPacket(menu.pos, files.get(selectedIndex), false, volumePercent()));
    }

    private Component deleteLabel() {
        return Component.translatable(confirmUid.isEmpty()
                ? "gui.broadcast.delete" : "gui.broadcast.delete_confirm");
    }

    private void deleteSelected() {
        StationView view = ClientDataHolder.getView(menu.pos);
        List<String> files = filteredMusic(view);
        if (view == null || selectedIndex < 0 || selectedIndex >= files.size()) return;
        String uid = uidAt(view, selectedIndex);
        if (uid.isEmpty()) return;
        if (!uid.equals(confirmUid)) {
            confirmUid = uid;
            if (btnDelete != null) {
                btnDelete.setMessage(deleteLabel());
                btnDelete.setDanger(true);
            }
            return;
        }
        NetworkHandler.sendToServer(new DeleteMusicPacket(menu.pos, uid));
        confirmUid = "";
        selectedIndex = -1;
        if (btnDelete != null) {
            btnDelete.setMessage(deleteLabel());
            btnDelete.setDanger(false);
        }
        requestData();
    }

    private void playUrl() {
        if (urlField == null) return;
        String url = urlField.getValue() == null ? "" : urlField.getValue().trim();
        if (url.isEmpty()) return;
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            urlField.setTextColor(GuiTheme.RED);
            return;
        }
        urlField.setTextColor(GuiTheme.TEXT);
        ClientDataHolder.pushUrlHistory(url);
        NetworkHandler.sendToServer(new PlayRequestPacket(menu.pos, url, true, volumePercent()));
    }

    private void stopPlayback() {
        NetworkHandler.sendToServer(new StopPlaybackPacket(menu.pos));
    }

    private void toggleRedstone() {
        boolean next = menu.data.get(BroadcastStationMenu.DATA_REDSTONE) == 0;
        NetworkHandler.sendToServer(new SetSettingsPacket(menu.pos, volumePercent(), next));
    }

    private int volumePercent() {
        return Math.max(0, Math.min(100, (int) Math.round(volumeSlider.currentValue() * 100)));
    }

    // ---------- 频率 ----------

    private void addFrequency() {
        if (freqField == null) return;
        StationView view = ClientDataHolder.getView(menu.pos);
        String f = FrequencyUtil.normalize(freqField.getValue());
        if (f.isEmpty() || view == null) return;
        if (view.frequencies().contains(f) || view.frequencies().size() >= FrequencyUtil.MAX_STATION_FREQUENCIES) {
            return;
        }
        List<String> next = new ArrayList<>(view.frequencies());
        next.add(f);
        freqField.setValue("");
        NetworkHandler.sendToServer(new SetStationFrequenciesPacket(menu.pos, next));
    }

    private void removeFrequency() {
        StationView view = ClientDataHolder.getView(menu.pos);
        if (view == null || selectedFreq < 0 || selectedFreq >= view.frequencies().size()) return;
        if (view.frequencies().size() <= 1) return;
        List<String> next = new ArrayList<>(view.frequencies());
        next.remove(selectedFreq);
        selectedFreq = -1;
        NetworkHandler.sendToServer(new SetStationFrequenciesPacket(menu.pos, next));
    }

    // ---------- 渲染 ----------

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

        drawHeader(g, x, y);
        drawTabs(g, x, y);

        g.fill(x + 10, y + 204, x + W - 10, y + 205, GuiTheme.BORDER);
        drawFooter(g, x, y);

        switch (tab) {
            case MUSIC -> drawMusicTab(g, x, y, mx, my);
            case URL -> drawUrlTab(g, x, y, mx, my);
            case FREQ -> drawFreqTab(g, x, y, mx, my);
            case DEVICES -> drawDevicesTab(g, x, y, mx, my);
        }
    }

    private void drawHeader(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 1, x + 4, y + 27, GuiTheme.ACCENT);
        g.drawString(font, Component.translatable("container.broadcast.station"), x + 12, y + 8, GuiTheme.TEXT, false);
        g.drawString(font, Component.translatable("gui.broadcast.subtitle"), x + 12, y + 18, GuiTheme.FAINT, false);

        var play = ClientDataHolder.getPlayState(menu.pos);
        boolean playing = play != null && play.playing();
        String status = playing
                ? Component.translatable("gui.broadcast.now_playing").getString() + ": " + trim(play.target(), 18)
                : Component.translatable("gui.broadcast.stopped").getString();
        int color = playing ? GuiTheme.GREEN : GuiTheme.FAINT;
        int tw = font.width(status);
        g.drawString(font, status, x + W - 12 - tw, y + 9, color, false);
        g.fill(x + W - 16 - tw, y + 10, x + W - 12 - tw, y + 14, color);
    }

    private void drawTabs(GuiGraphics g, int x, int y) {
        int i = 0;
        for (Tab t : Tab.values()) {
            int tx = x + 10 + i * (TAB_W + TAB_GAP);
            boolean active = tab == t;
            GuiTheme.rr(g, tx, y + TAB_Y, TAB_W, 18,
                    active ? GuiTheme.CARD_ACTIVE : GuiTheme.CARD,
                    active ? GuiTheme.ACCENT : GuiTheme.BORDER);
            if (active) g.fill(tx + 1, y + TAB_Y + 16, tx + TAB_W - 1, y + TAB_Y + 17, GuiTheme.ACCENT);
            g.drawCenteredString(font, Component.translatable(t.key), tx + TAB_W / 2, y + TAB_Y + 5,
                    active ? GuiTheme.TEXT : GuiTheme.DIM);
            i++;
        }
    }

    private void drawFooter(GuiGraphics g, int x, int y) {
        g.drawString(font, Component.translatable("gui.broadcast.volume"), x + 12, y + 214, GuiTheme.DIM, false);
        g.drawString(font, volumePercent() + "%", x + 160, y + 214, GuiTheme.TEXT, false);
        boolean rm = menu.data.get(BroadcastStationMenu.DATA_REDSTONE) != 0;
        redstoneButton.setMessage(redstoneLabel());
        redstoneButton.setAccent(rm);
        g.drawString(font, Component.translatable(rm
                        ? "gui.broadcast.redstone_hint_on" : "gui.broadcast.redstone_hint_off"),
                x + 12, y + 230, rm ? GuiTheme.GREEN : GuiTheme.FAINT, false);
    }

    private Component redstoneLabel() {
        boolean on = menu.data.get(BroadcastStationMenu.DATA_REDSTONE) != 0;
        return Component.translatable("gui.broadcast.redstone_mode").copy()
                .append(" ").append(Component.translatable(on ? "gui.broadcast.on" : "gui.broadcast.off"));
    }

    // ---- 音乐库 ----

    private void drawMusicTab(GuiGraphics g, int x, int y, int mx, int my) {
        GuiTheme.rr(g, x + 11, y + 58, 124, 18, GuiTheme.CARD, GuiTheme.BORDER);
        GuiTheme.rr(g, x + 137, y + 58, 50, 18, confirmUid.isEmpty() ? GuiTheme.CARD : 0x55F87171,
                confirmUid.isEmpty() ? GuiTheme.BORDER : GuiTheme.RED);
        if (btnDelete != null) {
            btnDelete.setMessage(deleteLabel());
            btnDelete.setDanger(!confirmUid.isEmpty());
        }

        int lx = x + 12, ly = y + 80, lw = 174, lh = 118;
        GuiTheme.rr(g, lx, ly, lw, lh, GuiTheme.CARD, GuiTheme.BORDER);

        StationView view = ClientDataHolder.getView(menu.pos);
        List<String> files = filteredMusic(view);
        List<String> uids = view == null ? List.of() : view.musicUids();
        int rowH = 16;
        int visible = (lh - 4) / rowH;

        if (files.isEmpty()) {
            String key = (view != null && !view.music().isEmpty())
                    ? "gui.broadcast.no_match" : "gui.broadcast.no_music";
            g.drawString(font, Component.translatable(key), lx + 10, ly + 52, GuiTheme.DIM, false);
        } else {
            scroll = clamp(scroll, 0, Math.max(0, files.size() - visible));
            g.enableScissor(lx + 1, ly + 1, lx + lw - 1, ly + lh - 1);
            for (int i = 0; i < visible && scroll + i < files.size(); i++) {
                int idx = scroll + i;
                int ey = ly + 2 + i * rowH;
                boolean sel = idx == selectedIndex;
                boolean hover = mx >= lx + 2 && mx < lx + lw - 10 && my >= ey && my < ey + rowH - 1;
                if (sel) {
                    GuiTheme.rr(g, lx + 2, ey, lw - 4, rowH - 2, GuiTheme.CARD_ACTIVE, GuiTheme.ACCENT_DARK);
                } else if (hover) {
                    g.fill(lx + 2, ey, lx + lw - 2, ey + rowH - 2, GuiTheme.CARD_HOVER);
                }
                String name = files.get(idx);
                String uid = uidForName(view, name);
                g.fill(lx + 6, ey + 5, lx + 9, ey + 12, formatColor(name));
                g.drawString(font, uid, lx + 13, ey + 4, GuiTheme.ACCENT_HOVER, false);
                boolean local = ClientDataHolder.hasLocalFile(name);
                g.drawString(font, trim(name, 15), lx + 64, ey + 4,
                        sel ? GuiTheme.TEXT : (local ? GuiTheme.TEXT : GuiTheme.DIM), false);
                if (sel) g.drawString(font, ">", lx + lw - 16, ey + 4, GuiTheme.ACCENT_HOVER, false);
            }
            g.disableScissor();
            drawScrollbar(g, lx + lw - 6, ly + 3, lh - 6, files.size(), visible, scroll);
        }

        int ix = x + 194, iy = y + 146, iw = 76, ih = 52;
        GuiTheme.rr(g, ix, iy, iw, ih, GuiTheme.CARD, GuiTheme.BORDER);
        if (selectedIndex >= 0 && selectedIndex < files.size()) {
            String name = files.get(selectedIndex);
            String ext = NameUtil.extension(name);
            g.drawString(font, trim(name, 11), ix + 6, iy + 6, GuiTheme.TEXT, false);
            g.drawString(font, uidForName(view, name), ix + 6, iy + 20, GuiTheme.ACCENT_HOVER, false);
            g.drawString(font, ext.isEmpty() ? "?" : ext.toUpperCase(), ix + 6, iy + 34,
                    formatColor(name), false);
            boolean local = ClientDataHolder.hasLocalFile(name);
            g.drawString(font, Component.translatable(local ? "gui.broadcast.local" : "gui.broadcast.remote"),
                    ix + 30, iy + 34, local ? GuiTheme.GREEN : GuiTheme.ORANGE, false);
        } else {
            g.drawString(font, Component.translatable("gui.broadcast.select_hint"), ix + 6, iy + 22,
                    GuiTheme.FAINT, false);
        }
    }

    private String uidForName(StationView view, String name) {
        if (view == null) return "";
        int idx = view.music().indexOf(name);
        return idx >= 0 && idx < view.musicUids().size() ? view.musicUids().get(idx) : "";
    }

    // ---- 网络音乐 ----

    private void drawUrlTab(GuiGraphics g, int x, int y, int mx, int my) {
        g.drawString(font, Component.translatable("gui.broadcast.url_hint"), x + 12, y + 58, GuiTheme.DIM, false);
        GuiTheme.rr(g, x + 11, y + 69, 258, 20, GuiTheme.CARD, GuiTheme.BORDER);
        g.drawString(font, Component.translatable("gui.broadcast.url_history"), x + 12, y + 122, GuiTheme.ORANGE, false);

        int hx = x + 12, hy = y + 134, hw = 256, hh = 64;
        GuiTheme.rr(g, hx, hy, hw, hh, GuiTheme.CARD, GuiTheme.BORDER);
        List<String> hist = ClientDataHolder.urlHistory();
        if (hist.isEmpty()) {
            g.drawString(font, Component.translatable("gui.broadcast.no_history"), hx + 8, hy + 28, GuiTheme.FAINT, false);
        } else {
            int rowH = 13;
            int visible = (hh - 4) / rowH;
            urlScroll = clamp(urlScroll, 0, Math.max(0, hist.size() - visible));
            g.enableScissor(hx + 1, hy + 1, hx + hw - 6, hy + hh - 1);
            for (int i = 0; i < visible && urlScroll + i < hist.size(); i++) {
                int idx = urlScroll + i;
                int ey = hy + 2 + i * rowH;
                boolean hover = mx >= hx + 2 && mx < hx + hw - 6 && my >= ey && my < ey + rowH - 1;
                if (hover) g.fill(hx + 2, ey, hx + hw - 6, ey + rowH - 1, GuiTheme.CARD_HOVER);
                g.drawString(font, trim(hist.get(idx), 36), hx + 7, ey + 1, hover ? GuiTheme.TEXT : GuiTheme.DIM, false);
            }
            g.disableScissor();
            drawScrollbar(g, hx + hw - 5, hy + 2, hh - 4, hist.size(), visible, urlScroll);
        }
    }

    // ---- 频率 ----

    private void drawFreqTab(GuiGraphics g, int x, int y, int mx, int my) {
        StationView view = ClientDataHolder.getView(menu.pos);
        List<String> freqs = view == null ? List.of(FrequencyUtil.DEFAULT) : view.frequencies();

        int lx = x + 12, ly = y + CONTENT_Y, lw = 170, lh = 76;
        GuiTheme.rr(g, lx, ly, lw, lh, GuiTheme.CARD, GuiTheme.BORDER);
        g.drawString(font, Component.translatable("gui.broadcast.freq.list", freqs.size()),
                lx + 8, ly + 4, GuiTheme.TEXT, false);
        g.fill(lx + 8, ly + 15, lx + lw - 8, ly + 16, GuiTheme.BORDER);
        int rowH = 14;
        int visible = 4;
        freqScroll = clamp(freqScroll, 0, Math.max(0, freqs.size() - visible));
        for (int i = 0; i < visible && freqScroll + i < freqs.size(); i++) {
            int idx = freqScroll + i;
            int ey = ly + 18 + i * rowH;
            boolean sel = idx == selectedFreq;
            boolean hover = mx >= lx + 2 && mx < lx + lw - 6 && my >= ey && my < ey + rowH - 1;
            if (sel) {
                GuiTheme.rr(g, lx + 2, ey, lw - 4, rowH - 2, GuiTheme.CARD_ACTIVE, GuiTheme.ACCENT_DARK);
            } else if (hover) {
                g.fill(lx + 2, ey, lx + lw - 2, ey + rowH - 2, GuiTheme.CARD_HOVER);
            }
            g.fill(lx + 7, ey + 4, lx + 9, ey + 10, GuiTheme.ACCENT);
            g.drawString(font, freqs.get(idx), lx + 13, ey + 3,
                    sel ? GuiTheme.TEXT : GuiTheme.DIM, false);
        }
        drawScrollbar(g, lx + lw - 5, ly + 17, lh - 22, freqs.size(), visible, freqScroll);

        g.drawString(font, Component.translatable("gui.broadcast.freq.hint1"), x + 12, y + 166, GuiTheme.FAINT, false);
        g.drawString(font, Component.translatable("gui.broadcast.freq.hint2"), x + 12, y + 178, GuiTheme.FAINT, false);
        g.drawString(font, Component.translatable("gui.broadcast.freq.hint3"), x + 12, y + 190, GuiTheme.FAINT, false);
    }

    // ---- 设备 ----

    private void drawDevicesTab(GuiGraphics g, int x, int y, int mx, int my) {
        StationView view = ClientDataHolder.getView(menu.pos);
        if (view == null) {
            g.drawString(font, Component.translatable("gui.broadcast.loading"), x + 12, y + 70, GuiTheme.FAINT, false);
            return;
        }

        int sx = x + 12, sy = y + CONTENT_Y, sw = 122, sh = 70;
        GuiTheme.rr(g, sx, sy, sw, sh, GuiTheme.CARD, GuiTheme.BORDER);
        g.drawString(font, Component.translatable("gui.broadcast.speakers", view.speakers().size()),
                sx + 8, sy + 7, GuiTheme.TEXT, false);
        g.fill(sx + 8, sy + 18, sx + sw - 8, sy + 19, GuiTheme.BORDER);
        if (view.speakers().isEmpty()) {
            g.drawString(font, Component.translatable("gui.broadcast.no_speaker"), sx + 8, sy + 34, GuiTheme.FAINT, false);
        } else {
            for (int i = 0; i < Math.min(4, view.speakers().size()); i++) {
                var p = view.speakers().get(i);
                g.fill(sx + 8, sy + 27 + i * 11, sx + 10, sy + 33 + i * 11, GuiTheme.ACCENT);
                g.drawString(font, p.getX() + ", " + p.getY() + ", " + p.getZ(),
                        sx + 14, sy + 25 + i * 11, GuiTheme.DIM, false);
            }
            if (view.speakers().size() > 4) {
                g.drawString(font, Component.translatable("gui.broadcast.more", view.speakers().size() - 4),
                        sx + 14, sy + 25 + 4 * 11, GuiTheme.FAINT, false);
            }
        }

        int mx0 = x + 146, my0 = y + CONTENT_Y, mw = 122, mh = 70;
        GuiTheme.rr(g, mx0, my0, mw, mh, GuiTheme.CARD, GuiTheme.BORDER);
        g.drawString(font, Component.translatable("gui.broadcast.mics", view.mics().size()),
                mx0 + 8, my0 + 7, GuiTheme.TEXT, false);
        g.fill(mx0 + 8, my0 + 18, mx0 + mw - 8, my0 + 19, GuiTheme.BORDER);
        boolean anyActive = view.mics().stream().anyMatch(ClientDataHolder::isMicActive);
        if (view.mics().isEmpty()) {
            g.drawString(font, Component.translatable("gui.broadcast.no_mic"), mx0 + 8, my0 + 34, GuiTheme.FAINT, false);
        } else {
            for (int i = 0; i < Math.min(3, view.mics().size()); i++) {
                var p = view.mics().get(i);
                boolean on = ClientDataHolder.isMicActive(p);
                g.fill(mx0 + 8, my0 + 27 + i * 11, mx0 + 10, my0 + 33 + i * 11, on ? GuiTheme.GREEN : GuiTheme.FAINT);
                g.drawString(font, p.getX() + ", " + p.getY() + ", " + p.getZ(),
                        mx0 + 14, my0 + 25 + i * 11, on ? GuiTheme.GREEN : GuiTheme.DIM, false);
            }
            g.drawString(font, Component.translatable(anyActive
                            ? "gui.broadcast.mic_active" : "gui.broadcast.mic_idle"),
                    mx0 + 8, my0 + 58, anyActive ? GuiTheme.GREEN : GuiTheme.FAINT, false);
        }

        int ux = x + 12, uy = y + 134, uw = 186, uh = 30;
        boolean hover = mx >= ux && mx < ux + uw && my >= uy && my < uy + uh;
        GuiTheme.rr(g, ux, uy, uw, uh, hover ? GuiTheme.CARD_HOVER : GuiTheme.CARD,
                hover ? GuiTheme.ACCENT : GuiTheme.BORDER);
        int port = view.uploadPort();
        String addr = port > 0
                ? Component.translatable("gui.broadcast.upload_addr").getString() + " "
                  + ClientDataHolder.currentServerHost() + ":" + port
                : Component.translatable("gui.broadcast.upload_disabled").getString();
        g.drawString(font, trim(addr, 25), ux + 8, uy + 6, port > 0 ? GuiTheme.GREEN : GuiTheme.RED, false);
        String status = uploadStatus == null || uploadStatus.isEmpty()
                || System.currentTimeMillis() - uploadStatusAt > 15000
                ? Component.translatable("gui.broadcast.upload_hint").getString()
                : uploadStatus;
        g.drawString(font, trim(status, 30), ux + 8, uy + 18, GuiTheme.DIM, false);

        g.drawString(font, Component.translatable("gui.broadcast.freq.current", String.join(", ", view.frequencies())),
                x + 12, y + 174, GuiTheme.FAINT, false);
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
        int x = leftPos;
        int y = topPos;

        if (button == 0 && my >= y + TAB_Y && my < y + TAB_Y + 18) {
            for (int i = 0; i < Tab.values().length; i++) {
                int tx = x + 10 + i * (TAB_W + TAB_GAP);
                if (mx >= tx && mx < tx + TAB_W) {
                    switchTab(Tab.values()[i]);
                    return true;
                }
            }
        }

        if (tab == Tab.MUSIC && button == 0
                && mx >= x + 12 && mx < x + 186 && my >= y + 80 && my < y + 198) {
            StationView view = ClientDataHolder.getView(menu.pos);
            List<String> files = filteredMusic(view);
            if (!files.isEmpty()) {
                int idx = (int) ((my - (y + 80 + 2)) / 16) + scroll;
                if (idx >= 0 && idx < files.size()) {
                    selectedIndex = idx;
                    confirmUid = "";
                    if (btnDelete != null) {
                        btnDelete.setMessage(deleteLabel());
                        btnDelete.setDanger(false);
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastClickMs < 400) playSelected();
                    lastClickMs = now;
                }
            }
            return true;
        }

        if (tab == Tab.URL && button == 0 && urlField != null) {
            int hx = x + 12, hy = y + 134, hw = 256, hh = 64;
            if (mx >= hx && mx < hx + hw && my >= hy && my < hy + hh) {
                List<String> hist = ClientDataHolder.urlHistory();
                int idx = (int) ((my - hy - 2) / 13) + urlScroll;
                if (idx >= 0 && idx < hist.size()) {
                    urlField.setValue(hist.get(idx));
                    urlField.setTextColor(GuiTheme.TEXT);
                }
                return true;
            }
        }

        if (tab == Tab.FREQ && button == 0) {
            int lx = x + 12, ly = y + CONTENT_Y, lw = 170;
            if (mx >= lx && mx < lx + lw && my >= ly + 17 && my < ly + 17 + 4 * 14) {
                int idx = (int) ((my - (ly + 18)) / 14) + freqScroll;
                StationView view = ClientDataHolder.getView(menu.pos);
                if (view != null && idx >= 0 && idx < view.frequencies().size()) {
                    selectedFreq = idx;
                }
                return true;
            }
        }

        if (tab == Tab.DEVICES && button == 0) {
            StationView view = ClientDataHolder.getView(menu.pos);
            if (view != null && mx >= x + 12 && mx < x + 198 && my >= y + 134 && my < y + 164) {
                if (view.uploadPort() > 0) {
                    setUploadStatus(Component.translatable("gui.broadcast.upload_picking").getString());
                    ClientDataHolder.pickAndUploadAsync(view.uploadPort(), r -> {
                        setUploadStatus(r);
                        if (minecraft != null) minecraft.execute(this::requestData);
                    });
                } else {
                    setUploadStatus(Component.translatable("gui.broadcast.upload_disabled").getString());
                }
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    private void setUploadStatus(String s) {
        this.uploadStatus = s;
        this.uploadStatusAt = System.currentTimeMillis();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int x = leftPos;
        int y = topPos;
        if (tab == Tab.MUSIC && mx >= x + 12 && mx < x + 186
                && my >= y + 80 && my < y + 198) {
            StationView view = ClientDataHolder.getView(menu.pos);
            if (view != null) {
                int visible = 114 / 16;
                List<String> files = filteredMusic(view);
                scroll = clamp(scroll - (int) Math.signum(delta) * 2, 0, Math.max(0, files.size() - visible));
            }
            return true;
        }
        if (tab == Tab.URL && mx >= x + 12 && mx < x + 268 && my >= y + 134 && my < y + 198) {
            List<String> hist = ClientDataHolder.urlHistory();
            int visible = 60 / 13;
            urlScroll = clamp(urlScroll - (int) Math.signum(delta) * 2, 0, Math.max(0, hist.size() - visible));
            return true;
        }
        if (tab == Tab.FREQ && mx >= x + 12 && mx < x + 182 && my >= y + CONTENT_Y && my < y + CONTENT_Y + 76) {
            StationView view = ClientDataHolder.getView(menu.pos);
            int size = view == null ? 1 : view.frequencies().size();
            freqScroll = clamp(freqScroll - (int) Math.signum(delta) * 1, 0, Math.max(0, size - 4));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // 全部自绘
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (volumeSlider != null) {
            double target = menu.data.get(BroadcastStationMenu.DATA_VOLUME) / 100D;
            if (Math.abs(target - volumeSlider.currentValue()) > 0.005) {
                volumeSlider.syncValue(target);
            }
        }
    }

    // ---------- 工具 ----------

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int formatColor(String name) {
        return switch (NameUtil.extension(name)) {
            case "ogg" -> GuiTheme.GREEN;
            case "wav" -> GuiTheme.ACCENT;
            case "mp3" -> GuiTheme.ORANGE;
            default -> GuiTheme.FAINT;
        };
    }

    private static String trim(String s, int maxWidth) {
        if (s == null) return "";
        return s.length() <= maxWidth ? s : s.substring(0, Math.max(0, maxWidth - 1)) + "…";
    }

    // ---------- 自定义组件 ----------

    private class VolumeSlider extends AbstractSliderButton {
        VolumeSlider(int x, int y, int w, int h, double value) {
            super(x, y, w, h, Component.empty(), value);
            updateMessage();
        }

        double currentValue() {
            return this.value;
        }

        void syncValue(double v) {
            this.value = Math.max(0D, Math.min(1D, v));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            NetworkHandler.sendToServer(new SetSettingsPacket(menu.pos, volumePercent(),
                    menu.data.get(BroadcastStationMenu.DATA_REDSTONE) != 0));
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int hover = isHoveredOrFocused() ? 1 : 0;
            GuiTheme.rr(g, getX(), getY(), getWidth(), getHeight(), GuiTheme.CARD,
                    hover == 1 ? GuiTheme.BORDER_LIGHT : GuiTheme.BORDER);
            int fillW = (int) (value * (getWidth() - 2));
            if (fillW > 0) {
                g.fill(getX() + 1, getY() + 1, getX() + 1 + fillW, getY() + getHeight() - 1, GuiTheme.ACCENT);
                g.fill(getX() + 1, getY() + 1, getX() + 1 + fillW, getY() + 2, GuiTheme.ACCENT_HOVER);
            }
        }
    }

    private class AccentButton extends Button {
        private boolean accent;
        private boolean danger;

        AccentButton(int x, int y, int w, int h, Component msg, OnPress onPress, boolean accent) {
            super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
            this.accent = accent;
        }

        void setAccent(boolean accent) {
            this.accent = accent;
        }

        void setDanger(boolean danger) {
            this.danger = danger;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hover = isHoveredOrFocused();
            int bg;
            int border;
            int text;
            if (danger) {
                bg = hover ? 0xFFB91C1C : GuiTheme.RED;
                border = 0xFFFCA5A5;
                text = 0xFFFFFFFF;
            } else if (accent) {
                bg = hover ? GuiTheme.ACCENT_HOVER : GuiTheme.ACCENT;
                border = GuiTheme.ACCENT_HOVER;
                text = 0xFFFFFFFF;
            } else {
                bg = hover ? GuiTheme.CARD_HOVER : GuiTheme.CARD;
                border = GuiTheme.BORDER;
                text = GuiTheme.TEXT;
            }
            GuiTheme.rr(g, getX(), getY(), getWidth(), getHeight(), bg, border);
            g.drawCenteredString(font, getMessage(), getX() + getWidth() / 2,
                    getY() + (getHeight() - 8) / 2, text);
        }
    }
}
