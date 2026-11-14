package com.clanstats;

import com.clanstats.core.ClanConfig;
import com.clanstats.core.Colors;
import com.clanstats.core.Names;
import com.clanstats.core.PlayerRef;
import com.clanstats.core.ProfileNames;
import com.clanstats.core.StatsEngine;
import com.clanstats.core.Texts;
import com.clanstats.mod.ClanStatsCore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.PlayerSkin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Menú visual (1.21.11): clanes a la izquierda y, a la derecha, los miembros del clan elegido, los jugadores de
 * la sala para añadir, todos los jugadores de la sala con sus kills, o la paleta de colores. Opciones abajo.
 * Todo lo que hace también se puede hacer con /clanes (o /clans).
 */
final class ClanStatsScreen extends Screen {
    // Colores ARGB (desde 1.21.6 el texto sin alfa no se ve)
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int DARK = 0xFF7A7A7A;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int GOLD = 0xFFFFAA00;
    private static final int PANEL = 0xA0000000;
    private static final int STRIPE = 0x14FFFFFF;
    private static final int TOTAL_BG = 0x28FFFFFF;

    private static final int CLAN_ROW = 20;
    private static final int ROW = 16;
    private static final int HEAD = 12;
    private static final int BTN = 18;
    private static final int PALETTE_COLS = 8;

    // Códigos de tecla (GLFW)
    private static final int KEY_ENTER = 257;
    private static final int KEY_TAB = 258;
    private static final int KEY_KP_ENTER = 335;

    /** Qué muestra el panel derecho. */
    enum View { MEMBERS, PICK, ROOM, COLORS }

    /** Últimas skins vistas en el Tab: la cabeza se sigue viendo aunque el jugador se vaya de la sala. */
    private static final Map<String, PlayerSkin> SKINS = new HashMap<>();

    private final Screen parent;
    private final ClanStatsCore core;
    private final KeyMapping hudKey;
    private final KeyMapping menuKey;

    private String selected;
    private View view = View.MEMBERS;
    private int clanScroll;
    private int rowScroll;
    private String clanText = "";
    private String playerText = "";
    private String hexText = "";
    private String confirmKey;
    private String confirmPrompt;
    private long confirmUntil;
    private String status;
    private int statusColor = WHITE;
    private long statusShownAt;
    private long statusUntil;
    private boolean pendingRefresh;
    private int builtVersion = -1;
    private int builtRoster;
    private int ticks;

    // Posiciones (se calculan en init(), también al cambiar el tamaño de la ventana)
    private int leftX;
    private int leftW;
    private int rightX;
    private int rightW;
    private int top;
    private int listTop;
    private int listBottom;
    private int inputY;
    private int actionY;
    private int panelBottom;

    private EditBox clanBox;
    private EditBox playerBox;
    private EditBox hexBox;
    private final List<ClanRow> clanRows = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<Swatch> swatches = new ArrayList<>();
    private int totalRows;
    private int firstRowY;
    private final Map<String, PlayerInfo> infoByUuid = new HashMap<>();
    private final Map<String, PlayerInfo> infoByName = new HashMap<>();

    private record ClanRow(int y, ClanConfig.Clan clan) {}

    /** Una fila del panel derecho: un miembro del clan o un jugador de la sala. */
    private record Row(int y, String name, String uuid, ClanConfig.Member member, boolean online) {}

    /** Un cuadrito de la paleta de colores. */
    private record Swatch(int x, int y, int w, int h, String color) {}

    ClanStatsScreen(Screen parent, ClanStatsCore core, KeyMapping hudKey, KeyMapping menuKey) {
        super(Component.literal("ClanStats FFA"));
        this.parent = parent;
        this.core = core;
        this.hudKey = hudKey;
        this.menuKey = menuKey;
    }

    // ------------------------------------------------------------------ armado

    private void layout() {
        top = 20;
        listTop = top + 14;
        panelBottom = height - 64;
        actionY = panelBottom - BTN - 2;
        inputY = actionY - BTN - 4;
        listBottom = inputY - 4;
        leftX = 10;
        leftW = Math.max(120, Math.min(190, width * 34 / 100));
        rightX = leftX + leftW + 14;
        rightW = width - rightX - 10;
    }

    @Override
    protected void init() {
        layout();
        indexOnline();
        builtVersion = core.version();
        builtRoster = core.roster.current().hashCode();
        clanRows.clear();
        rows.clear();
        swatches.clear();
        playerBox = null;
        hexBox = null;
        ClanConfig.Clan sel = selectedClan();
        if (sel == null && view == View.COLORS) view = View.MEMBERS;

        buildClanList(sel);
        buildClanControls(sel);
        switch (view) {
            case PICK -> buildPickRows(sel);
            case ROOM -> buildRoomRows();
            case COLORS -> buildPalette(sel);
            default -> buildMemberRows(sel);
        }
        if (view == View.COLORS) buildHexControls(sel);
        else buildPlayerControls(sel);
        buildViewButtons();
        buildBottomButtons();
    }

    private void buildClanList(ClanConfig.Clan sel) {
        List<ClanConfig.Clan> clans = core.config.clanes;
        int visible = Math.max(1, (listBottom - listTop) / CLAN_ROW);
        clanScroll = clamp(clanScroll, 0, Math.max(0, clans.size() - visible));
        for (int i = 0; i < visible && clanScroll + i < clans.size(); i++) {
            ClanConfig.Clan c = clans.get(clanScroll + i);
            int y = listTop + i * CLAN_ROW;
            int on = core.onlineCount(c);
            int total = c.miembros.size();
            String counter = "  " + on + "/" + total;
            MutableComponent label = Component.literal(fit(c.nombre, leftW - 18 - font.width(counter))).withColor(Colors.rgb(c.color))
                    .append(Component.literal(counter).withStyle(ChatFormatting.GRAY));
            addRenderableWidget(Button.builder(label, b -> select(c))
                    .bounds(leftX + 6, y, leftW - 6, CLAN_ROW - 2)
                    .tooltip(tip(c.nombre + "\n" + tr("count.connected", on, total - on)))
                    .build());
            clanRows.add(new ClanRow(y, c));
        }
    }

    private void buildClanControls(ClanConfig.Clan sel) {
        clanBox = new EditBox(font, leftX, inputY, leftW - 48, BTN, Component.literal(tr("menu.new_clan")));
        clanBox.setMaxLength(ClanConfig.CLAN_NAME_MAX);
        clanBox.setFilter(s -> s.indexOf('§') < 0 && s.indexOf('"') < 0 && s.indexOf('\\') < 0);
        clanBox.setHint(Component.literal(tr("menu.clan_name")).withStyle(ChatFormatting.DARK_GRAY));
        clanBox.setValue(clanText);
        clanBox.setResponder(s -> clanText = s);
        addRenderableWidget(clanBox);
        addRenderableWidget(Button.builder(Component.literal(tr("menu.create")), b -> createClan())
                .bounds(leftX + leftW - 44, inputY, 44, BTN)
                .tooltip(tip(tr("menu.create_tip", ClanConfig.CLAN_NAME_MAX)))
                .build());

        int half = (leftW - 2) / 2;
        Component colorLabel = view == View.COLORS ? Component.literal("« " + tr("menu.color")).withStyle(ChatFormatting.YELLOW)
                : Component.literal(tr("menu.color"));
        Button color = Button.builder(colorLabel, b -> toggleColors())
                .bounds(leftX, actionY, half, BTN)
                .tooltip(tip(tr(view == View.COLORS ? "menu.color_back_tip" : "menu.color_tip")))
                .build();
        color.active = sel != null;
        addRenderableWidget(color);
        boolean ask = sel != null && confirming("borrar:" + Names.key(sel.nombre));
        Button delete = Button.builder(ask ? Component.literal(tr("menu.delete_ask")).withStyle(ChatFormatting.RED)
                        : Component.literal(tr("menu.delete")), b -> deleteClan())
                .bounds(leftX + half + 2, actionY, leftW - half - 2, BTN)
                .tooltip(tip(tr("menu.delete_tip")))
                .build();
        delete.active = sel != null;
        addRenderableWidget(delete);
    }

    /** Miembros del clan: una fila fija con el total del clan y después cada miembro, en el orden del ranking. */
    private void buildMemberRows(ClanConfig.Clan sel) {
        totalRows = 0;
        firstRowY = listTop;
        if (sel == null) return;
        firstRowY = listTop + ROW; // la primera fila es la del total
        StatsEngine.ClanStat cs = core.snapshot().clanStat(sel);
        Set<ClanConfig.Member> ordered = new LinkedHashSet<>();
        if (cs != null) {
            for (StatsEngine.PlayerStat p : cs.members) {
                if (p.member != null) ordered.add(p.member);
            }
        }
        ordered.addAll(sel.miembros);
        List<ClanConfig.Member> members = new ArrayList<>(ordered);
        members.removeIf(m -> !core.memberShown(m)); // "ocultar desconectados": sus stats siguen contando
        int visible = visibleRows();
        totalRows = members.size();
        rowScroll = clamp(rowScroll, 0, Math.max(0, totalRows - visible));
        for (int i = 0; i < visible && rowScroll + i < members.size(); i++) {
            ClanConfig.Member m = members.get(rowScroll + i);
            int y = firstRowY + i * ROW;
            rows.add(new Row(y, m.nombre, m.uuid, m, core.isOnline(m)));
            addRenderableWidget(Button.builder(Component.literal("x").withStyle(ChatFormatting.RED), b -> removeMember(m))
                    .bounds(rightX + rightW - 14, y + 1, 14, 14)
                    .tooltip(tip(tr("menu.remove_tip", m.nombre, sel.nombre)))
                    .build());
        }
    }

    /** «Añadir desde la lista»: los de la sala que no tienen clan y, al final, los que ya están en este. */
    private void buildPickRows(ClanConfig.Clan sel) {
        firstRowY = listTop;
        List<PlayerRef> players = new ArrayList<>();
        for (PlayerRef p : core.roster.current()) {
            ClanConfig.Clan c = core.config.clanOf(p.name(), p.uuid());
            if (c == null || c == sel) players.add(p);
        }
        players.sort(Comparator.comparingInt((PlayerRef p) -> core.config.clanOf(p.name(), p.uuid()) == null ? 0 : 1)
                .thenComparing(p -> p.name().toLowerCase(Locale.ROOT)));
        int visible = visibleRows();
        totalRows = players.size();
        rowScroll = clamp(rowScroll, 0, Math.max(0, totalRows - visible));
        for (int i = 0; i < visible && rowScroll + i < players.size(); i++) {
            PlayerRef p = players.get(rowScroll + i);
            int y = firstRowY + i * ROW;
            ClanConfig.Member m = core.config.findMember(p.name(), p.uuid());
            rows.add(new Row(y, p.name(), p.uuid(), m, true));
            if (sel == null || core.config.clanOf(m) == sel) continue; // ya está en este clan: se marca con ✔ al dibujar
            addRenderableWidget(Button.builder(Component.literal("+").withStyle(ChatFormatting.GREEN), b -> addFromList(sel, p))
                    .bounds(rightX + rightW - 14, y + 1, 14, 14)
                    .tooltip(tip(tr("menu.add_tip", p.name(), sel.nombre)))
                    .build());
        }
    }

    /** «Jugadores en sala»: todos los de la lista de Tab con sus kills y muertes totales, ordenados. */
    private void buildRoomRows() {
        firstRowY = listTop;
        StatsEngine.Snapshot snap = core.snapshot();
        Comparator<StatsEngine.PlayerStat> order = snap.byKd ? StatsEngine.BY_KD : StatsEngine.BY_KILLS;
        List<PlayerRef> players = new ArrayList<>(core.roster.current());
        players.sort((a, b) -> {
            StatsEngine.PlayerStat sa = lobbyStat(snap, a);
            StatsEngine.PlayerStat sb = lobbyStat(snap, b);
            if (sa != null && sb != null) return order.compare(sa, sb);
            if (sa != null) return -1;
            if (sb != null) return 1;
            return a.name().compareToIgnoreCase(b.name());
        });
        int visible = visibleRows();
        totalRows = players.size();
        rowScroll = clamp(rowScroll, 0, Math.max(0, totalRows - visible));
        for (int i = 0; i < visible && rowScroll + i < players.size(); i++) {
            PlayerRef p = players.get(rowScroll + i);
            rows.add(new Row(firstRowY + i * ROW, p.name(), p.uuid(), core.config.findMember(p.name(), p.uuid()), true));
        }
    }

    /** Paleta de colores para el clan elegido. */
    private void buildPalette(ClanConfig.Clan sel) {
        totalRows = 0;
        firstRowY = listTop + 2;
        List<Colors.Named> palette = Colors.palette();
        int rowsCount = (palette.size() + PALETTE_COLS - 1) / PALETTE_COLS;
        int gap = 2;
        int w = Math.max(10, Math.min(26, (rightW - gap * (PALETTE_COLS - 1)) / PALETTE_COLS));
        int h = Math.max(10, Math.min(20, (listBottom - firstRowY - gap * (rowsCount - 1)) / rowsCount));
        for (int i = 0; i < palette.size(); i++) {
            Colors.Named n = palette.get(i);
            int x = rightX + (i % PALETTE_COLS) * (w + gap);
            int y = firstRowY + (i / PALETTE_COLS) * (h + gap);
            swatches.add(new Swatch(x, y, w, h, n.key()));
            addRenderableWidget(Button.builder(Component.literal("■").withColor(n.rgb()), b -> setColor(sel, n.key()))
                    .bounds(x, y, w, h)
                    .tooltip(tip(n.label(Texts.language()) + " · " + Colors.hex(n.rgb())))
                    .build());
        }
    }

    private void buildHexControls(ClanConfig.Clan sel) {
        hexBox = new EditBox(font, rightX, inputY, rightW - 48, BTN, Component.literal("hex"));
        hexBox.setMaxLength(7);
        hexBox.setFilter(s -> s.matches("#?[0-9A-Fa-f]{0,6}"));
        String current = sel == null ? "#FFFFFF" : Colors.hex(Colors.rgb(sel.color));
        hexBox.setHint(Component.literal(fitHint(tr("menu.hex_hint", current), rightW - 56)).withStyle(ChatFormatting.DARK_GRAY));
        hexBox.setValue(hexText);
        hexBox.setResponder(s -> hexText = s);
        addRenderableWidget(hexBox);
        Button apply = Button.builder(Component.literal(tr("menu.apply")), b -> applyHex())
                .bounds(rightX + rightW - 44, inputY, 44, BTN)
                .tooltip(tip(tr("menu.apply_tip")))
                .build();
        apply.active = sel != null;
        addRenderableWidget(apply);
    }

    private void buildPlayerControls(ClanConfig.Clan sel) {
        playerBox = new EditBox(font, rightX, inputY, rightW - 48, BTN, Component.literal(tr("menu.player")));
        playerBox.setMaxLength(16);
        playerBox.setFilter(s -> s.matches("[A-Za-z0-9_]*"));
        String hint = sel == null ? tr("menu.first_create") : fitHint(tr("menu.add_by_name"), rightW - 56, tr("menu.add_by_name_short"));
        playerBox.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
        playerBox.setValue(playerText);
        playerBox.setResponder(this::onPlayerTyped);
        addRenderableWidget(playerBox);
        Button add = Button.builder(Component.literal(tr("menu.add")), b -> addTyped())
                .bounds(rightX + rightW - 44, inputY, 44, BTN)
                .tooltip(tip(sel == null ? tr("menu.first_choose") : tr("menu.add_typed_tip", sel.nombre)))
                .build();
        add.active = sel != null;
        addRenderableWidget(add);
    }

    /** Debajo del panel derecho: «Añadir desde la lista» y «Jugadores en sala» (o volver a los miembros). */
    private void buildViewButtons() {
        int half = (rightW - 2) / 2;
        boolean pick = view == View.PICK;
        boolean room = view == View.ROOM;
        addRenderableWidget(Button.builder(Component.literal(pick ? "« " + tr("menu.members") : tr("menu.pick")), b -> show(pick ? View.MEMBERS : View.PICK))
                .bounds(rightX, actionY, half, BTN)
                .tooltip(tip(tr(pick ? "menu.members_tip" : "menu.pick_tip")))
                .build());
        addRenderableWidget(Button.builder(Component.literal(room ? "« " + tr("menu.members") : tr("menu.room", core.roster.size())), b -> show(room ? View.MEMBERS : View.ROOM))
                .bounds(rightX + half + 2, actionY, rightW - half - 2, BTN)
                .tooltip(tip(tr(room ? "menu.members_tip" : "menu.room_tip")))
                .build());
    }

    /**
     * Abajo, dos filas: HUD, activar/desactivar el mod, mostrar u ocultar desconectados y Opciones (teclas, idioma
     * y colores); después Copiar para Discord, Reiniciar, Ordenar y Listo.
     */
    private void buildBottomButtons() {
        int bw = (width - 20 - 12) / 4;
        int x1 = 10;
        int x2 = x1 + bw + 4;
        int x3 = x2 + bw + 4;
        int x4 = x3 + bw + 4;
        int w4 = width - 10 - x4;
        int r1 = height - 46;
        int r2 = height - 24;
        Button hud = Button.builder(Component.literal("HUD: " + ClanStatsCore.shortModeLabel(core.config.hud.modo)), b -> {
                    core.cycleHudMode();
                    refresh();
                })
                .bounds(x1, r1, bw, 20)
                .tooltip(tip(tr("menu.hud_tip")))
                .build();
        hud.active = core.config.activo;
        addRenderableWidget(hud);
        boolean on = core.config.activo;
        addRenderableWidget(Button.builder(Component.literal("Mod: ").append(Component.literal(tr(on ? "state.on" : "state.off"))
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)), b -> toggleMod())
                .bounds(x2, r1, bw, 20)
                .tooltip(tip(tr("menu.mod_tip")))
                .build());
        boolean shown = core.config.mostrarDesconectados;
        addRenderableWidget(Button.builder(Component.literal(tr(shown ? "menu.offline_shown" : "menu.offline_hidden")), b -> toggleOffline())
                .bounds(x3, r1, bw, 20)
                .tooltip(tip(tr("menu.offline_tip")))
                .build());
        addRenderableWidget(Button.builder(Component.literal(tr("menu.options")), b -> openOptions())
                .bounds(x4, r1, w4, 20)
                .tooltip(tip(tr("menu.options_tip")))
                .build());

        addRenderableWidget(Button.builder(Component.literal(tr("menu.copy")), b -> copyDiscord())
                .bounds(x1, r2, bw, 20)
                .tooltip(tip(tr("menu.copy_tip")))
                .build());
        boolean ask = confirming("reset");
        addRenderableWidget(Button.builder(ask ? Component.literal(tr("menu.reset_ask")).withStyle(ChatFormatting.RED)
                        : Component.literal(tr("menu.reset")), b -> resetData())
                .bounds(x2, r2, bw, 20)
                .tooltip(tip(tr("menu.reset_tip")))
                .build());
        boolean kd = "kd".equals(core.config.hud.orden);
        addRenderableWidget(Button.builder(Component.literal(tr("menu.sort", kd ? "KD" : "kills")), b -> toggleOrder())
                .bounds(x3, r2, bw, 20)
                .tooltip(tip(tr("menu.sort_tip")))
                .build());
        addRenderableWidget(Button.builder(Component.literal(tr("menu.done")), b -> onClose()).bounds(x4, r2, w4, 20).build());
    }

    // ------------------------------------------------------------------ dibujo

    /** Los paneles van con el fondo: así quedan arriba del desenfoque y debajo de los botones. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        g.fill(leftX - 4, top - 2, leftX + leftW + 4, panelBottom + 2, PANEL);
        g.fill(rightX - 4, top - 2, rightX + rightW + 4, panelBottom + 2, PANEL);
        if (view == View.MEMBERS && selectedClan() != null) g.fill(rightX - 2, listTop, rightX + rightW, listTop + ROW - 1, TOTAL_BG);
        for (int i = 0; i < rows.size(); i++) {
            if (((rowScroll + i) & 1) == 1) g.fill(rightX - 2, rows.get(i).y(), rightX + rightW - 16, rows.get(i).y() + ROW, STRIPE);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        StatsEngine.Snapshot snap = core.snapshot();
        String title = core.config.activo ? "ClanStats FFA" : tr("menu.disabled_title");
        centered(g, title, width / 2, 6, core.config.activo ? GOLD : RED);
        String deaths = tr("menu.deaths", snap.events);
        String online = tr("menu.online", core.roster.size());
        int titleHalf = font.width(title) / 2 + 8;
        text(g, fit(deaths, width / 2 - titleHalf - 10), 10, 6, DARK);
        String on = fit(online, width / 2 - titleHalf - 10);
        text(g, on, width - 10 - font.width(on), 6, DARK);

        ClanConfig.Clan sel = selectedClan();
        drawClanPanel(g, sel);
        switch (view) {
            case PICK -> drawPickPanel(g, sel, snap);
            case ROOM -> drawRoomPanel(g, snap);
            case COLORS -> drawPalette(g, sel);
            default -> drawMemberPanel(g, sel, snap);
        }
        drawStatus(g, mouseX, mouseY);
    }

    private void drawClanPanel(GuiGraphics g, ClanConfig.Clan sel) {
        text(g, tr("menu.clans", core.config.clanes.size()), leftX, top + 2, WHITE);
        for (ClanRow cr : clanRows) {
            g.fill(leftX, cr.y(), leftX + 4, cr.y() + CLAN_ROW - 2, Colors.argb(cr.clan().color));
            if (cr.clan() == sel) outline(g, leftX + 5, cr.y() - 1, leftW - 4, CLAN_ROW, WHITE);
        }
        if (core.config.clanes.isEmpty()) {
            lines(g, leftX + 2, listTop + 4, leftW - 4, GRAY, tr("menu.no_clans"));
        }
        int visible = Math.max(1, (listBottom - listTop) / CLAN_ROW);
        scrollbar(g, leftX + leftW + 1, listTop, listTop + visible * CLAN_ROW - 2, clanScroll, visible, core.config.clanes.size());
    }

    private int colKd() {
        return rightX + rightW - 18;
    }

    private int colM() {
        return colKd() - 34;
    }

    private int colK() {
        return colM() - 24;
    }

    private void columnHeaders(GuiGraphics g) {
        rightText(g, "K", colK(), top + 2, DARK);
        rightText(g, tr("col.deaths_short"), colM(), top + 2, DARK);
        rightText(g, "KD", colKd(), top + 2, DARK);
    }

    private void drawMemberPanel(GuiGraphics g, ClanConfig.Clan sel, StatsEngine.Snapshot snap) {
        if (sel == null) {
            text(g, tr("menu.members"), rightX, top + 2, WHITE);
            lines(g, rightX + 2, listTop + 4, rightW - 6, GRAY, tr("menu.start"), tr("menu.start2"));
            return;
        }
        text(g, "■ " + fit(sel.nombre, colK() - 22 - rightX - font.width("■ ")), rightX, top + 2, Colors.argb(sel.color));
        columnHeaders(g);

        // fila fija: total del clan y cuántos están en la sala
        int on = core.onlineCount(sel);
        String total = tr("menu.clan_total") + " · ";
        String onText = tr("count.online", on);
        int off = sel.miembros.size() - on;
        String offText = " · " + tr("count.offline", off) + (off > 0 && !core.config.mostrarDesconectados ? " " + tr("menu.hidden_note") : "");
        StatsEngine.ClanStat cs = snap.clanStat(sel);
        int room = colK() - font.width(String.valueOf(cs == null ? 0 : cs.kills)) - 8 - rightX - 2;
        int ty = listTop + (ROW - 8) / 2;
        int x = rightX + 2;
        if (font.width(total + onText + offText) <= room) {
            text(g, total, x, ty, GOLD);
            x += font.width(total);
        }
        if (font.width(onText + offText) <= room) {
            text(g, onText, x, ty, GREEN);
            text(g, offText, x + font.width(onText), ty, GRAY);
        } else {
            text(g, on + "/" + sel.miembros.size(), x, ty, GREEN);
        }
        drawNumbers(g, ty, cs == null ? 0 : cs.kills, cs == null ? 0 : cs.deaths, YELLOW);

        if (sel.miembros.isEmpty()) {
            lines(g, rightX + 2, firstRowY + 4, rightW - 6, GRAY, tr("menu.no_members"), tr("menu.no_members2"));
        } else if (rows.isEmpty()) {
            lines(g, rightX + 2, firstRowY + 4, rightW - 6, GRAY, tr("menu.all_hidden"));
        }
        for (Row r : rows) {
            StatsEngine.PlayerStat st = snap.statOf(r.member());
            drawRow(g, r, st, false);
        }
        scrollbar(g, rightX + rightW + 1, firstRowY, firstRowY + visibleRows() * ROW, rowScroll, visibleRows(), totalRows);
    }

    private void drawPickPanel(GuiGraphics g, ClanConfig.Clan sel, StatsEngine.Snapshot snap) {
        String title = sel == null ? tr("menu.pick") : tr("menu.pick_title", sel.nombre);
        text(g, fit(title, colK() - 22 - rightX), rightX, top + 2, sel == null ? WHITE : Colors.argb(sel.color));
        columnHeaders(g);
        if (rows.isEmpty()) {
            lines(g, rightX + 2, listTop + 4, rightW - 6, GRAY, core.roster.size() == 0
                    ? tr("menu.room_empty") : tr("menu.pick_empty"), core.roster.size() == 0 ? tr("menu.room_empty2") : "");
        }
        for (Row r : rows) drawRow(g, r, lobbyStat(snap, r), true);
        scrollbar(g, rightX + rightW + 1, listTop, listTop + visibleRows() * ROW, rowScroll, visibleRows(), totalRows);
    }

    private void drawRoomPanel(GuiGraphics g, StatsEngine.Snapshot snap) {
        String title = tr("menu.room_title", core.roster.size());
        text(g, title, rightX, top + 2, WHITE);
        String hint = tr("menu.room_order", snap.byKd ? "KD" : "kills");
        if (rightX + font.width(title) + 8 + font.width(hint) < colK() - 22) {
            text(g, hint, rightX + font.width(title) + 8, top + 2, DARK);
        }
        columnHeaders(g);
        if (rows.isEmpty()) {
            lines(g, rightX + 2, listTop + 4, rightW - 6, GRAY, tr("menu.room_empty"), tr("menu.room_empty2"));
        }
        for (Row r : rows) drawRow(g, r, lobbyStat(snap, r), false);
        scrollbar(g, rightX + rightW + 1, listTop, listTop + visibleRows() * ROW, rowScroll, visibleRows(), totalRows);
    }

    private void drawPalette(GuiGraphics g, ClanConfig.Clan sel) {
        if (sel == null) return;
        String label = tr("menu.color_of", sel.nombre) + ": ";
        String current = Colors.label(sel.color);
        String shown = fit(label, rightW - font.width(current) - 4);
        text(g, shown, rightX, top + 2, WHITE);
        text(g, current, rightX + font.width(shown), top + 2, Colors.argb(sel.color));
        for (Swatch s : swatches) {
            g.fill(s.x() + 2, s.y() + 2, s.x() + s.w() - 2, s.y() + s.h() - 2, 0xFF000000 | Colors.rgb(s.color()));
            if (Colors.rgb(s.color()) == Colors.rgb(sel.color)) outline(g, s.x() - 1, s.y() - 1, s.w() + 2, s.h() + 2, WHITE);
        }
    }

    private StatsEngine.PlayerStat lobbyStat(StatsEngine.Snapshot snap, Row r) {
        StatsEngine.PlayerStat st = r.uuid() != null ? snap.lobbyByKey.get("u:" + r.uuid()) : null;
        return st != null ? st : snap.lobbyByName(r.name());
    }

    private StatsEngine.PlayerStat lobbyStat(StatsEngine.Snapshot snap, PlayerRef p) {
        StatsEngine.PlayerStat st = p.uuid() != null ? snap.lobbyByKey.get("u:" + p.uuid()) : null;
        return st != null ? st : snap.lobbyByName(p.name());
    }

    private void drawRow(GuiGraphics g, Row r, StatsEngine.PlayerStat st, boolean pick) {
        int textY = r.y() + (ROW - 8) / 2;
        ClanConfig.Clan clan = core.config.clanOf(r.member());
        drawHead(g, r, clan, rightX, r.y() + 2);
        int nameX = rightX + HEAD + 4;
        String name = fit(r.name(), colK() - 34 - nameX);
        int nameColor = view == View.ROOM && clan != null ? Colors.argb(clan.color) : (r.online() ? WHITE : GRAY);
        text(g, name, nameX, textY, nameColor);
        int after = nameX + font.width(name) + 3;
        int space = Math.max(0, colK() - 30 - after);
        if (view == View.MEMBERS) {
            ClanStatsCore.LookupState ls = r.member() != null && r.member().uuid == null ? core.lookupState(r.name()) : null;
            if (ls == ClanStatsCore.LookupState.NOT_FOUND) {
                text(g, fit(tr("menu.not_exists"), space), after, textY, RED);
            } else {
                // punto verde = en la sala; gris = desconectado
                g.fill(after, textY + 2, after + 4, textY + 6, r.online() ? GREEN : 0xFF555555);
                if (r.member() != null && r.member().uuid == null) text(g, "?", after + 7, textY, YELLOW);
            }
        } else if (view == View.ROOM && clan != null) {
            text(g, fit("[" + clan.nombre + "]", space), after, textY, Colors.argb(clan.color));
        } else if (pick && clan != null) {
            text(g, "✔", rightX + rightW - 11, textY, GREEN);
        }
        drawNumbers(g, textY, st == null ? 0 : st.kills, st == null ? 0 : st.deaths, WHITE);
    }

    private void drawNumbers(GuiGraphics g, int y, int k, int d, int color) {
        double kd = d == 0 ? k : (double) k / d;
        rightText(g, String.valueOf(k), colK(), y, color);
        rightText(g, String.valueOf(d), colM(), y, color);
        rightText(g, StatsEngine.fmtKd(kd), colKd(), y, (k == 0 && d == 0) ? GRAY : (kd >= 1.0 ? GREEN : RED));
    }

    private void drawHead(GuiGraphics g, Row r, ClanConfig.Clan clan, int x, int y) {
        PlayerSkin skin = skinFor(r.uuid() != null ? r.uuid() : (r.member() == null ? null : r.member().uuid), r.name());
        if (skin != null) {
            PlayerFaceRenderer.draw(g, skin, x, y, HEAD);
            return;
        }
        // sin skin conocida: cuadrado con la inicial y el color del clan
        int c = clan != null ? Colors.argb(clan.color) : 0xFF555555;
        g.fill(x, y, x + HEAD, y + HEAD, (c & 0x00FFFFFF) | 0x90000000);
        centered(g, r.name().substring(0, 1).toUpperCase(Locale.ROOT), x + HEAD / 2 + 1, y + 2, WHITE);
    }

    private void drawStatus(GuiGraphics g, int mouseX, int mouseY) {
        int y = height - 58;
        long now = System.currentTimeMillis();
        Row hovered = null;
        for (Row r : rows) {
            if (mouseX >= rightX && mouseX < rightX + rightW - 16 && mouseY >= r.y() && mouseY < r.y() + ROW) hovered = r;
        }
        // un mensaje recién mostrado tiene prioridad; después, pasar el mouse por un jugador muestra su UUID
        if (status != null && now < statusUntil && (hovered == null || now < statusShownAt + 1500)) {
            centered(g, fit(status, width - 20), width / 2, y, statusColor);
            return;
        }
        if (hovered != null) {
            String uuid = hovered.member() != null && hovered.member().uuid != null ? hovered.member().uuid : hovered.uuid();
            String where = view == View.MEMBERS ? " · " + tr(hovered.online() ? "menu.status_online" : "menu.status_offline") : "";
            centered(g, fit(hovered.name() + where + " · UUID: " + (uuid != null ? uuid : tr("menu.uuid_unknown")), width - 20),
                    width / 2, y, DARK);
        }
    }

    // ------------------------------------------------------------------ cabezas

    private void indexOnline() {
        infoByUuid.clear();
        infoByName.clear();
        ClientPacketListener connection = minecraft == null ? null : minecraft.getConnection();
        if (connection == null) return;
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            Object profile = info.getProfile();
            String uuid = ProfileNames.idOf(profile);
            String name = ProfileNames.nameOf(profile);
            if (uuid != null) infoByUuid.put(uuid, info);
            if (name != null) infoByName.put(Names.key(name), info);
        }
    }

    /**
     * Skin para la cabeza: la del Tab si el jugador está en la sala; si no, la última que se vio en el Tab;
     * si nunca se vio, la de su cuenta (se descarga de Mojang con el UUID); y mientras tanto la de por defecto.
     */
    private PlayerSkin skinFor(String uuid, String name) {
        PlayerInfo info = uuid != null ? infoByUuid.get(uuid) : null;
        if (info == null && uuid == null && name != null) info = infoByName.get(Names.key(name));
        if (info != null) {
            try {
                PlayerSkin s = info.getSkin();
                if (s != null) {
                    if (uuid != null) SKINS.put(uuid, s);
                    return s;
                }
            } catch (RuntimeException ignored) {
                // si falla se usa la de reserva
            }
        }
        if (uuid != null) {
            PlayerSkin cached = SKINS.get(uuid);
            if (cached != null) return cached;
        }
        PlayerSkin remote = RemoteSkins.get(uuid, name);
        if (remote != null) return remote;
        return uuid != null ? DefaultSkins.forUuid(uuid) : null;
    }

    // ------------------------------------------------------------------ acciones

    private ClanConfig.Clan selectedClan() {
        ClanConfig.Clan c = selected == null ? null : core.config.findClan(selected);
        if (c == null && !core.config.clanes.isEmpty()) {
            c = core.config.clanes.get(0);
            selected = Names.key(c.nombre);
        }
        if (c == null) selected = null;
        return c;
    }

    private void select(ClanConfig.Clan c) {
        selected = Names.key(c.nombre);
        rowScroll = 0;
        confirmKey = null;
        hexText = "";
        refresh();
    }

    private void show(View v) {
        view = v;
        rowScroll = 0;
        refresh();
    }

    private void toggleColors() {
        show(view == View.COLORS ? View.MEMBERS : View.COLORS);
    }

    private void createClan() {
        String name = ClanConfig.cleanClanName(clanText);
        if (!ClanConfig.isValidClanName(name)) {
            setStatus(tr("clan.invalid_name", ClanConfig.CLAN_NAME_MAX), RED);
            return;
        }
        if (core.config.findClan(name) != null) {
            setStatus(tr("clan.exists", name), RED);
            return;
        }
        ClanConfig.Clan c = new ClanConfig.Clan(name, Colors.auto(core.config.clanes.size()));
        core.config.clanes.add(c);
        core.configChanged();
        selected = Names.key(name);
        clanText = "";
        clanScroll = Integer.MAX_VALUE; // se ajusta al final de la lista
        rowScroll = 0;
        if (view == View.COLORS) view = View.MEMBERS;
        setStatus(tr("menu.created", name), GREEN);
        refresh();
    }

    private void setColor(ClanConfig.Clan sel, String color) {
        if (sel == null) return;
        sel.color = color;
        core.configChanged();
        setStatus(tr("menu.color_set", sel.nombre, Colors.label(color)), Colors.argb(color));
        refresh();
    }

    private void applyHex() {
        ClanConfig.Clan sel = selectedClan();
        String c = Colors.parse(hexText);
        if (sel == null) return;
        if (c == null || !hexText.replace("#", "").matches("[0-9A-Fa-f]{6}|[0-9A-Fa-f]{3}")) {
            setStatus(tr("menu.hex_invalid"), RED);
            return;
        }
        hexText = "";
        setColor(sel, c);
    }

    private void deleteClan() {
        ClanConfig.Clan sel = selectedClan();
        if (sel == null) return;
        if (!confirm("borrar:" + Names.key(sel.nombre), tr("menu.delete_confirm", sel.nombre, sel.miembros.size()))) {
            return;
        }
        core.config.clanes.remove(sel);
        core.configChanged();
        selected = null;
        rowScroll = 0;
        if (view == View.COLORS) view = View.MEMBERS;
        setStatus(tr("menu.deleted", sel.nombre), GREEN);
        refresh();
    }

    private void addTyped() {
        ClanConfig.Clan sel = selectedClan();
        String name = playerText.trim();
        if (sel == null) {
            setStatus(tr("menu.first_choose"), RED);
            return;
        }
        if (!Names.isValid(name)) {
            setStatus(tr("menu.invalid_player"), RED);
            return;
        }
        add(sel, name, core.config.findMember(name), true);
    }

    private void addFromList(ClanConfig.Clan sel, PlayerRef p) {
        add(sel, p.name(), core.config.findMember(p.name(), p.uuid()), false);
    }

    private void add(ClanConfig.Clan sel, String name, ClanConfig.Member existing, boolean typed) {
        ClanConfig.Clan prev = core.config.clanOf(existing);
        if (existing != null && prev == sel) {
            setStatus(tr("add.already", existing.nombre, sel.nombre), YELLOW);
            return;
        }
        if (prev != null && !confirm("mover:" + existing.key(), tr("menu.move_confirm", existing.nombre, prev.nombre, sel.nombre))) {
            return;
        }
        confirmKey = null;
        ClanStatsCore.AddReport r = core.addByName(sel, name, this::onAddLater);
        if (r == null) {
            setStatus(tr("add.searching", name), YELLOW);
            statusUntil = System.currentTimeMillis() + 15_000;
        } else {
            showReport(r);
            if (typed && ClanStatsCore.saved(r)) playerText = "";
        }
        refresh();
    }

    /** Resultado de una búsqueda en Mojang: si el menú se cerró mientras tanto, va al chat. */
    private void onAddLater(ClanStatsCore.AddReport r) {
        if (minecraft != null && minecraft.screen != this) {
            core.platform.chat(core.addReportMessage(r));
            return;
        }
        showReport(r);
        if (ClanStatsCore.saved(r) && playerText.equalsIgnoreCase(r.typed())) playerText = "";
        refresh();
    }

    private void showReport(ClanStatsCore.AddReport r) {
        int color = switch (r.kind()) {
            case ADDED, MOVED -> GREEN;
            case ALREADY, PENDING -> YELLOW;
            default -> RED;
        };
        setStatus(core.addReportText(r), color);
    }

    private void removeMember(ClanConfig.Member m) {
        ClanConfig.Clan c = core.config.removeMember(m);
        if (c != null) {
            core.configChanged();
            setStatus(tr("member.left", m.nombre, c.nombre), GREEN);
        }
        refresh();
    }

    private void toggleMod() {
        core.setActive(!core.config.activo);
        setStatus(tr(core.config.activo ? "toast.enabled" : "menu.disabled_status"), core.config.activo ? GREEN : YELLOW);
        refresh();
    }

    private void toggleOffline() {
        core.setShowOffline(!core.config.mostrarDesconectados);
        rowScroll = 0;
        refresh();
    }

    private void openOptions() {
        minecraft.setScreen(new ClanStatsOptionsScreen(this, core, hudKey, menuKey));
    }

    private void toggleOrder() {
        core.config.hud.orden = "kd".equals(core.config.hud.orden) ? "kills" : "kd";
        core.configChanged();
        refresh();
    }

    private void copyDiscord() {
        String text = core.discordText();
        boolean ok = core.platform.copyToClipboard(text);
        core.storage.writeDiscordSummary(text);
        setStatus(ok ? tr("menu.copied", text.length()) : tr("menu.copy_failed"), ok ? GREEN : RED);
    }

    private void resetData() {
        int n = core.session.eventos.size();
        if (!confirm("reset", tr("menu.reset_confirm", n))) {
            return;
        }
        Path archived = core.resetSession();
        setStatus(tr("menu.reset_done") + (archived != null ? " " + tr("menu.reset_archived", archived.getFileName()) : ""), GREEN);
        refresh();
    }

    private void onPlayerTyped(String s) {
        playerText = s;
        String c = completion(s);
        playerBox.setSuggestion(c == null ? null : c.substring(s.length()));
    }

    /** Nombre que empieza con lo escrito: primero los de la sala, después los conocidos. */
    private String completion(String typed) {
        if (typed == null || typed.isEmpty()) return null;
        String low = typed.toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        for (PlayerRef p : core.roster.current()) candidates.add(p.name());
        candidates.sort(String.CASE_INSENSITIVE_ORDER);
        candidates.addAll(core.knownNames());
        for (String n : candidates) {
            if (n.length() > typed.length() && n.toLowerCase(Locale.ROOT).startsWith(low)) return n;
        }
        return null;
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.input();
        boolean typingPlayer = playerBox != null && playerBox.isFocused() && view != View.COLORS;
        boolean typingClan = clanBox != null && clanBox.isFocused();
        boolean typingHex = hexBox != null && hexBox.isFocused() && view == View.COLORS;
        if (key == KEY_ENTER || key == KEY_KP_ENTER) {
            if (typingPlayer) {
                addTyped();
                return true;
            }
            if (typingClan) {
                createClan();
                return true;
            }
            if (typingHex) {
                applyHex();
                return true;
            }
        }
        if (key == KEY_TAB && typingPlayer) {
            String c = completion(playerText);
            if (c != null) {
                playerBox.setValue(c);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
        if (step != 0 && mouseX >= leftX && mouseX < leftX + leftW + 4 && mouseY >= listTop && mouseY < listBottom) {
            clanScroll = Math.max(0, clanScroll + step);
            rebuildNow();
            return true;
        }
        if (step != 0 && view != View.COLORS && mouseX >= rightX && mouseX < rightX + rightW + 4 && mouseY >= listTop && mouseY < listBottom) {
            rowScroll = Math.max(0, rowScroll + step);
            rebuildNow();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks % 10 == 0) indexOnline();
        if (confirmKey != null && System.currentTimeMillis() >= confirmUntil) {
            confirmKey = null;
            if (confirmPrompt != null && confirmPrompt.equals(status)) status = null; // la pregunta ya no vale
            pendingRefresh = true;
        }
        if (core.version() != builtVersion) pendingRefresh = true;
        if (ticks % 20 == 0 && core.roster.current().hashCode() != builtRoster) pendingRefresh = true;
        if (pendingRefresh) {
            pendingRefresh = false;
            rebuildNow();
        }
    }

    @Override
    public void removed() {
        core.saveNow();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ utilidades

    private static String tr(String key, Object... args) {
        return Texts.tr(key, args);
    }

    /** Rearma los botones en el próximo tick (no se puede mientras se procesa el clic de un botón). */
    private void refresh() {
        pendingRefresh = true;
    }

    /** Rearma ya mismo, sin perder el cuadro de texto donde se estaba escribiendo. */
    private void rebuildNow() {
        int focus = playerBox != null && playerBox.isFocused() ? 1
                : clanBox != null && clanBox.isFocused() ? 2
                : hexBox != null && hexBox.isFocused() ? 3 : 0;
        rebuildWidgets();
        if (focus == 1 && playerBox != null) setFocused(playerBox);
        else if (focus == 2) setFocused(clanBox);
        else if (focus == 3 && hexBox != null) setFocused(hexBox);
    }

    private boolean confirming(String key) {
        return key.equals(confirmKey) && System.currentTimeMillis() < confirmUntil;
    }

    /** Primer clic: pide confirmación y devuelve false. Segundo clic (antes de 4 s): devuelve true. */
    private boolean confirm(String key, String prompt) {
        if (confirming(key)) {
            confirmKey = null;
            return true;
        }
        confirmKey = key;
        confirmPrompt = prompt;
        confirmUntil = System.currentTimeMillis() + 4000;
        setStatus(prompt, YELLOW);
        refresh();
        return false;
    }

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
        statusShownAt = System.currentTimeMillis();
        statusUntil = statusShownAt + 6000;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - firstRowY) / ROW);
    }

    private static Tooltip tip(String text) {
        return Tooltip.create(Component.literal(text));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, true);
    }

    private void centered(GuiGraphics g, String s, int centerX, int y, int color) {
        text(g, s, centerX - font.width(s) / 2, y, color);
    }

    private void rightText(GuiGraphics g, String s, int right, int y, int color) {
        text(g, s, right - font.width(s), y, color);
    }

    /** Párrafos cortos que se parten en varias líneas para no salirse de {@code maxWidth}. */
    private void lines(GuiGraphics g, int x, int y, int maxWidth, int color, String... paragraphs) {
        for (String p : paragraphs) {
            if (p.isEmpty()) continue;
            StringBuilder line = new StringBuilder();
            for (String word : p.split(" ")) {
                String next = line.length() == 0 ? word : line + " " + word;
                if (font.width(next) > maxWidth && line.length() > 0) {
                    text(g, line.toString(), x, y, color);
                    y += font.lineHeight + 2;
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            if (line.length() > 0) {
                text(g, fit(line.toString(), maxWidth), x, y, color);
                y += font.lineHeight + 2;
            }
        }
    }

    /** Recorta el texto con "…" si no entra en {@code max} píxeles. */
    private String fit(String s, int max) {
        if (max <= 0) return "";
        if (font.width(s) <= max) return s;
        String out = s;
        while (!out.isEmpty() && font.width(out + "…") > max) out = out.substring(0, out.length() - 1);
        return out + "…";
    }

    /** Texto guía de un cuadro de texto: si no entra, la versión corta (o recortado). */
    private String fitHint(String hint, int max, String... shorter) {
        if (font.width(hint) <= max) return hint;
        for (String s : shorter) {
            if (font.width(s) <= max) return s;
        }
        return fit(shorter.length > 0 ? shorter[shorter.length - 1] : hint, max);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void scrollbar(GuiGraphics g, int x, int y1, int y2, int first, int visible, int total) {
        if (total <= visible || y2 <= y1) return;
        g.fill(x, y1, x + 2, y2, 0x40FFFFFF);
        int h = y2 - y1;
        int thumb = Math.max(8, h * visible / total);
        int ty = y1 + (h - thumb) * first / Math.max(1, total - visible);
        g.fill(x, ty, x + 2, ty + thumb, 0xC0FFFFFF);
    }
}
