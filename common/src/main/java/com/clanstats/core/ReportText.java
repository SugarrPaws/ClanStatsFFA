package com.clanstats.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Resúmenes en texto: uno completo (historial) y otro con formato de Discord que entra en un mensaje.
 * Los dos salen en el idioma elegido en el mod.
 */
public final class ReportText {
    /** Límite de caracteres de un mensaje normal de Discord. */
    public static final int DISCORD_LIMIT = 2000;
    private static final int DISCORD_DUELS = 6;

    private ReportText() {}

    private static DateTimeFormatter date() {
        return pattern("report.date_format", "dd/MM/yyyy HH:mm");
    }

    private static DateTimeFormatter shortDate() {
        return pattern("report.short_date_format", "dd/MM HH:mm");
    }

    private static DateTimeFormatter pattern(String key, String fallback) {
        try {
            return DateTimeFormatter.ofPattern(Texts.tr(key));
        } catch (IllegalArgumentException e) {
            return DateTimeFormatter.ofPattern(fallback);
        }
    }

    // ------------------------------------------------------------------ resumen completo

    public static String build(StatsEngine.Snapshot s, ClanConfig cfg, Session session) {
        ZoneId zone = ZoneId.systemDefault();
        StringBuilder b = new StringBuilder();
        b.append(Texts.tr("report.title", date().format(Instant.now().atZone(zone)),
                date().format(Instant.ofEpochMilli(session.inicio).atZone(zone)))).append('\n');

        b.append("\n== ").append(Texts.tr("report.clans_header")).append(" ==\n");
        if (s.clans.isEmpty()) b.append(Texts.tr("report.no_clans")).append('\n');
        int i = 1;
        for (StatsEngine.ClanStat cs : s.clans) {
            b.append(i++).append(") ").append(cs.clan.nombre).append(": ")
                    .append(Texts.tr("report.clan_line", cs.kills, cs.deaths, StatsEngine.fmtKd(cs.kd())));
            if (cs.teamKills > 0 && !cfg.countFriendlyFire()) b.append(" · ").append(Texts.tr("report.friendly_fire", cs.teamKills));
            b.append('\n');
            int r = 1;
            for (StatsEngine.PlayerStat p : cs.members) {
                b.append("   ").append(r++).append(". ").append(p.name).append(" — ")
                        .append(p.kills).append("/").append(p.deaths).append(" (KD ").append(StatsEngine.fmtKd(p.kd())).append(")\n");
            }
        }

        List<String> duels = duels(s, false);
        if (!duels.isEmpty()) {
            b.append('\n').append(Texts.tr("report.duels")).append(":\n");
            for (String d : duels) b.append("   ").append(d).append('\n');
        }

        b.append("\n== ").append(Texts.tr("report.lobby_header")).append(" ==\n");
        if (s.lobby.isEmpty()) b.append(Texts.tr("report.no_kills")).append('\n');
        int n = Math.min(25, s.lobby.size());
        for (int k = 0; k < n; k++) {
            StatsEngine.PlayerStat p = s.lobby.get(k);
            ClanConfig.Clan clan = s.clanOf(p);
            b.append(k + 1).append(". ").append(p.name);
            if (clan != null) b.append(" [").append(clan.nombre).append(']');
            b.append(" — ").append(p.kills).append("/").append(p.deaths)
                    .append(" (KD ").append(StatsEngine.fmtKd(p.kd())).append(")\n");
        }
        if (s.lobby.size() > n) b.append("   ").append(Texts.tr("report.more_players", s.lobby.size() - n)).append('\n');
        b.append('\n').append(Texts.tr("report.total", s.events, s.lobby.size())).append('\n');
        return b.toString();
    }

    /**
     * "Lobos 12 - 5 Tigres" para cada par de clanes que se enfrentó. Para Discord solo los
     * {@value #DISCORD_DUELS} con más kills (con muchos clanes la lista sería enorme).
     */
    private static List<String> duels(StatsEngine.Snapshot s, boolean discord) {
        record Duel(int total, String text) {}
        List<Duel> all = new ArrayList<>();
        for (int a = 0; a < s.clans.size(); a++) {
            for (int c = a + 1; c < s.clans.size(); c++) {
                ClanConfig.Clan x = s.clans.get(a).clan;
                ClanConfig.Clan y = s.clans.get(c).clan;
                int xy = s.headToHead(x, y);
                int yx = s.headToHead(y, x);
                if (xy + yx == 0) continue;
                all.add(new Duel(xy + yx, discord
                        ? markdown(x.nombre) + " **" + xy + "-" + yx + "** " + markdown(y.nombre)
                        : x.nombre + " " + xy + " - " + yx + " " + y.nombre));
            }
        }
        List<String> out = new ArrayList<>();
        if (discord) all.sort((d1, d2) -> Integer.compare(d2.total(), d1.total()));
        int n = discord ? Math.min(DISCORD_DUELS, all.size()) : all.size();
        for (int i = 0; i < n; i++) out.add(all.get(i).text());
        if (all.size() > n) out.add(Texts.tr("report.and_more", all.size() - n));
        return out;
    }

    // ------------------------------------------------------------------ resumen para Discord

    /**
     * Resumen listo para pegar en Discord: tablas en bloques de código (se ven alineadas) y nunca más de
     * {@value #DISCORD_LIMIT} caracteres; si no entra, se acorta la cantidad de jugadores por lista.
     */
    public static String discord(StatsEngine.Snapshot s, ClanConfig cfg, Session session) {
        // {jugadores por clan, top de la lobby, clanes}: se prueba de más completo a más corto
        int[][] limits = {{30, 10, 99}, {30, 8, 99}, {10, 8, 99}, {8, 5, 99}, {5, 5, 99}, {5, 3, 99},
                {3, 5, 99}, {3, 3, 99}, {3, 0, 99}, {1, 0, 99}, {0, 0, 99}, {0, 0, 30}, {0, 0, 15}};
        String text = "";
        for (int[] l : limits) {
            text = discord(s, cfg, session, l[0], l[1], l[2]);
            if (text.length() <= DISCORD_LIMIT) return text;
        }
        return text.substring(0, DISCORD_LIMIT - 4) + "\n```";
    }

    private static String discord(StatsEngine.Snapshot s, ClanConfig cfg, Session session,
                                  int perClan, int lobbyTop, int maxClans) {
        ZoneId zone = ZoneId.systemDefault();
        StringBuilder b = new StringBuilder();
        b.append("**⚔️ ClanStats · FFA** — ").append(date().format(Instant.now().atZone(zone))).append('\n');
        b.append("-# ").append(Texts.tr("discord.subtitle", shortDate().format(Instant.ofEpochMilli(session.inicio).atZone(zone)),
                s.events, s.lobby.size())).append('\n');

        if (s.clans.isEmpty() && s.lobby.isEmpty()) {
            b.append('\n').append(Texts.tr("discord.empty"));
            return b.toString();
        }

        if (!s.clans.isEmpty()) {
            int shown = Math.min(maxClans, s.clans.size());
            boolean ff = !cfg.countFriendlyFire();
            boolean anyFf = false;
            int nameW = 4;
            for (int i = 0; i < shown; i++) {
                nameW = Math.max(nameW, block(s.clans.get(i).clan.nombre).length());
                anyFf |= ff && s.clans.get(i).teamKills > 0;
            }
            b.append("\n**🏆 ").append(Texts.tr("discord.clans")).append("** (").append(Texts.tr("discord.clans_note")).append(")\n```\n");
            b.append(padR("#", 3)).append(padR("Clan", nameW)).append(padL("K", 6)).append(padL(Texts.tr("col.deaths_short"), 6))
                    .append(padL("KD", 7)).append(anyFf ? padL("FF", 5) : "").append('\n');
            for (int i = 0; i < shown; i++) {
                StatsEngine.ClanStat cs = s.clans.get(i);
                b.append(padR(String.valueOf(i + 1), 3)).append(padR(block(cs.clan.nombre), nameW))
                        .append(padL(String.valueOf(cs.kills), 6)).append(padL(String.valueOf(cs.deaths), 6))
                        .append(padL(StatsEngine.fmtKd(cs.kd()), 7))
                        .append(anyFf ? padL(String.valueOf(cs.teamKills), 5) : "").append('\n');
            }
            if (s.clans.size() > shown) b.append("… ").append(Texts.tr("discord.more_clans", s.clans.size() - shown)).append('\n');
            b.append("```\n");

            List<String> duels = duels(s, true);
            if (!duels.isEmpty()) b.append("**").append(Texts.tr("report.duels")).append(":** ").append(String.join(" · ", duels)).append('\n');

            if (perClan > 0) {
                b.append("\n**👥 ").append(Texts.tr("discord.members")).append("** (").append(Texts.tr("discord.members_note")).append(")\n```\n");
                for (int i = 0; i < shown; i++) {
                    StatsEngine.ClanStat cs = s.clans.get(i);
                    b.append(block(cs.clan.nombre)).append(cs.members.isEmpty() ? " (" + Texts.tr("discord.no_members") + ")" : "").append('\n');
                    int n = Math.min(perClan, cs.members.size());
                    for (int k = 0; k < n; k++) {
                        StatsEngine.PlayerStat p = cs.members.get(k);
                        b.append(padL(String.valueOf(k + 1), 3)).append(' ').append(padR(p.name, 16))
                                .append(padL(p.kills + "/" + p.deaths, 8)).append(padL(StatsEngine.fmtKd(p.kd()), 7)).append('\n');
                    }
                    if (cs.members.size() > n) b.append("    … ").append(Texts.tr("discord.more", cs.members.size() - n)).append('\n');
                }
                b.append("```\n");
            }
        }

        if (lobbyTop > 0 && !s.lobby.isEmpty()) {
            int n = Math.min(lobbyTop, s.lobby.size());
            int tagW = 0;
            for (int k = 0; k < n; k++) {
                ClanConfig.Clan c = s.clanOf(s.lobby.get(k));
                if (c != null) tagW = Math.max(tagW, block(c.nombre).length() + 2);
            }
            b.append("\n**🌍 Lobby** (").append(Texts.tr("discord.lobby_note", n, s.lobby.size())).append(")\n```\n");
            for (int k = 0; k < n; k++) {
                StatsEngine.PlayerStat p = s.lobby.get(k);
                ClanConfig.Clan c = s.clanOf(p);
                b.append(padL(String.valueOf(k + 1), 3)).append(' ').append(padR(p.name, 16));
                if (tagW > 0) b.append(' ').append(padR(c == null ? "" : "[" + block(c.nombre) + "]", tagW));
                b.append(padL(p.kills + "/" + p.deaths, 8)).append(padL(StatsEngine.fmtKd(p.kd()), 7)).append('\n');
            }
            b.append("```");
        }
        return b.toString().stripTrailing();
    }

    private static String padR(String s, int w) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < w) b.append(' ');
        return b.append(' ').toString();
    }

    private static String padL(String s, int w) {
        StringBuilder b = new StringBuilder();
        for (int i = s.length(); i < w; i++) b.append(' ');
        return b.append(s).toString();
    }

    /** Dentro de un bloque de código: una comilla invertida en el nombre cerraría el bloque. */
    static String block(String s) {
        return s.replace('`', '\'');
    }

    /**
     * Fuera de los bloques de código: los símbolos de formato de Discord (*, _, ~, |, `) se escapan para que el
     * nombre se vea tal cual, y "@" lleva un espacio invisible para que "@everyone" en un nombre no avise a nadie.
     */
    static String markdown(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '*' || c == '_' || c == '~' || c == '|' || c == '`' || c == '<' || c == '>' || c == '#') {
                b.append('\\');
            }
            b.append(c);
            if (c == '@') b.append('​');
        }
        return b.toString();
    }
}
