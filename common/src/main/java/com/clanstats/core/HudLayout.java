package com.clanstats.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Arma el HUD como una lista de operaciones de dibujo (rectángulos y textos) en coordenadas locales.
 * Cada versión de Minecraft solo tiene que ejecutar esas operaciones con su API de dibujo.
 */
public final class HudLayout {

    public interface Metrics {
        int width(String text);

        int lineHeight();
    }

    /** fill=true → rectángulo (x1,y1)-(x2,y2); fill=false → texto en (x1,y1). Colores ARGB. */
    public record Op(boolean fill, int x1, int y1, int x2, int y2, int color, String text, boolean shadow) {
        public static Op rect(int x1, int y1, int x2, int y2, int color) {
            return new Op(true, x1, y1, x2, y2, color, null, false);
        }

        public static Op text(String text, int x, int y, int color) {
            return new Op(false, x, y, 0, 0, color, text, true);
        }
    }

    public static final class Frame {
        public final List<Op> ops;
        public final int width;
        public final int height;
        public final float scale;
        public final int offsetX;
        public final int offsetY;
        public final boolean anchorRight;

        Frame(List<Op> ops, int width, int height, float scale, int offsetX, int offsetY, boolean anchorRight) {
            this.ops = ops;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.anchorRight = anchorRight;
        }

        /** Posición X en pantalla según el ancho escalado de la GUI. */
        public int screenX(int screenWidth) {
            if (!anchorRight || screenWidth <= 0) return offsetX;
            return Math.max(0, Math.round(screenWidth - offsetX - width * scale));
        }
    }

    public record FeedLine(String killer, String victim, boolean heuristic) {}

    public static final int GOLD = 0xFFFFAA00;
    public static final int AQUA = 0xFF55FFFF;
    public static final int GRAY = 0xFFAAAAAA;
    public static final int DARK = 0xFF7A7A7A;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GREEN = 0xFF55FF55;
    public static final int RED = 0xFFFF5555;
    public static final int YELLOW = 0xFFFFFF55;
    private static final int BG = 0x90000000;
    private static final int LINE = 0x40FFFFFF;
    private static final int PAD = 3;
    private static final int GAP = 6;

    private record Seg(String text, int color) {}

    private static final class Row {
        final List<Seg> left = new ArrayList<>();
        Seg c1;
        Seg c2;
        boolean table;
        boolean separator;
    }

    private HudLayout() {}

    public static Frame build(StatsEngine.Snapshot snap, ClanConfig cfg, Metrics m, List<FeedLine> feed, String toast) {
        return build(snap, cfg, m, feed, toast, p -> true);
    }

    /** Solo el aviso (por ejemplo "ClanStats desactivado"), o null si no hay aviso. */
    public static Frame toastOnly(String toast, ClanConfig cfg, Metrics m) {
        if (toast == null) return null;
        List<Row> rows = new ArrayList<>();
        rows.add(title(new Seg(toast, YELLOW)));
        return layout(rows, cfg, m);
    }

    /**
     * Arma el HUD. {@code shown} decide qué miembros salen en la lista de cada clan (por ejemplo, sin los
     * desconectados); los totales del clan siempre cuentan a todos.
     */
    public static Frame build(StatsEngine.Snapshot snap, ClanConfig cfg, Metrics m, List<FeedLine> feed, String toast,
                              Predicate<StatsEngine.PlayerStat> shown) {
        List<Row> rows = new ArrayList<>();
        String mode = cfg.hud.modo;
        boolean showClans = mode.equals("CLANES") || mode.equals("AMBOS");
        boolean showLobby = mode.equals("LOBBY") || mode.equals("AMBOS");

        if (toast != null) rows.add(title(new Seg(toast, YELLOW)));
        if (showClans) addClans(rows, snap, cfg, shown);
        if (showLobby) {
            if (showClans) rows.add(separator());
            addLobby(rows, snap, cfg);
        }
        if (cfg.debug && feed != null && !feed.isEmpty() && (showClans || showLobby)) {
            rows.add(separator());
            addFeed(rows, feed);
        }
        if (rows.isEmpty()) return null;
        return layout(rows, cfg, m);
    }

    private static void addClans(List<Row> rows, StatsEngine.Snapshot snap, ClanConfig cfg, Predicate<StatsEngine.PlayerStat> shown) {
        rows.add(title(new Seg(Texts.tr("hud.clans"), GOLD), new Seg("  " + Texts.tr("hud.clans_note"), DARK)));
        if (snap.clans.isEmpty()) {
            rows.add(title(new Seg(Texts.tr("hud.no_clans"), GRAY)));
            return;
        }
        rows.add(table(List.of(), new Seg(Texts.tr("col.kd_pair"), DARK), new Seg("KD", DARK)));
        boolean ffSeparate = !cfg.countFriendlyFire();
        for (StatsEngine.ClanStat cs : snap.clans) {
            int color = Colors.argb(cs.clan.color);
            List<Seg> left = new ArrayList<>();
            left.add(new Seg("■ ", color));
            left.add(new Seg(cs.clan.nombre, color));
            if (ffSeparate && cs.teamKills > 0) left.add(new Seg(" FF" + cs.teamKills, DARK));
            rows.add(table(left, new Seg(cs.kills + "/" + cs.deaths, WHITE), kd(cs.kills, cs.deaths)));
            int n = 0;
            for (StatsEngine.PlayerStat p : cs.members) {
                if (n >= cfg.hud.topClan) break;
                if (!shown.test(p)) continue;
                n++;
                rows.add(table(List.of(new Seg("  " + n + ". ", DARK), new Seg(p.name, WHITE)),
                        new Seg(p.kills + "/" + p.deaths, GRAY), kd(p.kills, p.deaths)));
            }
        }
    }

    private static void addLobby(List<Row> rows, StatsEngine.Snapshot snap, ClanConfig cfg) {
        int kills = 0;
        for (StatsEngine.PlayerStat p : snap.lobby) kills += p.kills;
        rows.add(title(new Seg(Texts.tr("hud.lobby"), AQUA),
                new Seg("  " + Texts.tr("hud.lobby_note", snap.lobby.size(), kills), DARK)));
        if (snap.lobby.isEmpty()) {
            rows.add(title(new Seg(Texts.tr("hud.no_kills"), GRAY)));
            return;
        }
        rows.add(table(List.of(), new Seg(Texts.tr("col.kd_pair"), DARK), new Seg("KD", DARK)));
        int n = Math.min(cfg.hud.topLobby, snap.lobby.size());
        for (int i = 0; i < n; i++) {
            StatsEngine.PlayerStat p = snap.lobby.get(i);
            ClanConfig.Clan clan = snap.clanOf(p);
            int nameColor = clan == null ? WHITE : Colors.argb(clan.color);
            rows.add(table(List.of(new Seg((i + 1) + ". ", DARK), new Seg(p.name, nameColor)),
                    new Seg(p.kills + "/" + p.deaths, GRAY), kd(p.kills, p.deaths)));
        }
    }

    private static void addFeed(List<Row> rows, List<FeedLine> feed) {
        rows.add(title(new Seg(Texts.tr("hud.feed"), DARK)));
        for (FeedLine f : feed) {
            if (f.killer() != null) {
                Row r = title(new Seg(f.killer(), WHITE), new Seg(" » ", DARK), new Seg(f.victim(), WHITE));
                if (f.heuristic()) r.left.add(new Seg(" (?)", YELLOW));
                rows.add(r);
            } else {
                rows.add(title(new Seg(f.victim(), WHITE), new Seg(" " + Texts.tr("hud.died"), DARK)));
            }
        }
    }

    private static Seg kd(int kills, int deaths) {
        double v = deaths == 0 ? kills : (double) kills / deaths;
        int color = (kills == 0 && deaths == 0) ? GRAY : (v >= 1.0 ? GREEN : RED);
        return new Seg(StatsEngine.fmtKd(v), color);
    }

    private static Row title(Seg... segs) {
        Row r = new Row();
        r.left.addAll(List.of(segs));
        return r;
    }

    private static Row table(List<Seg> left, Seg c1, Seg c2) {
        Row r = new Row();
        r.left.addAll(left);
        r.c1 = c1;
        r.c2 = c2;
        r.table = true;
        return r;
    }

    private static Row separator() {
        Row r = new Row();
        r.separator = true;
        return r;
    }

    private static int width(Metrics m, List<Seg> segs) {
        int w = 0;
        for (Seg s : segs) w += m.width(s.text());
        return w;
    }

    private static Frame layout(List<Row> rows, ClanConfig cfg, Metrics m) {
        int lh = m.lineHeight() + 1;
        int col0 = 0;
        int col1 = 0;
        int col2 = 0;
        int titleW = 0;
        for (Row r : rows) {
            if (r.separator) continue;
            int w = width(m, r.left);
            if (r.table) {
                col0 = Math.max(col0, w);
                col1 = Math.max(col1, m.width(r.c1.text()));
                col2 = Math.max(col2, m.width(r.c2.text()));
            } else {
                titleW = Math.max(titleW, w);
            }
        }
        int tableW = col0 + GAP + col1 + GAP + col2;
        int width = Math.max(tableW, titleW) + PAD * 2;

        List<Op> ops = new ArrayList<>();
        int y = PAD;
        for (Row r : rows) {
            if (r.separator) {
                ops.add(Op.rect(PAD, y + 1, width - PAD, y + 2, LINE));
                y += 4;
                continue;
            }
            int x = PAD;
            for (Seg s : r.left) {
                if (!s.text().isEmpty()) ops.add(Op.text(s.text(), x, y, s.color()));
                x += m.width(s.text());
            }
            if (r.table) {
                int edge1 = PAD + col0 + GAP + col1;
                int edge2 = edge1 + GAP + col2;
                ops.add(Op.text(r.c1.text(), edge1 - m.width(r.c1.text()), y, r.c1.color()));
                ops.add(Op.text(r.c2.text(), edge2 - m.width(r.c2.text()), y, r.c2.color()));
            }
            y += lh;
        }
        int height = y + PAD - 1;
        if (cfg.hud.fondo) ops.add(0, Op.rect(0, 0, width, height, BG));
        return new Frame(ops, width, height, cfg.hud.escala, cfg.hud.x, cfg.hud.y, cfg.hud.derecha);
    }
}
