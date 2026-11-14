package com.clanstats.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula todas las estadísticas a partir del registro de kills y de los clanes actuales.
 * Como se recalcula desde el registro, si agregas a alguien a un clan tarde (o corriges un nombre)
 * sus kills anteriores se cuentan igual.
 *
 * <p>Cada jugador se identifica por su UUID cuando se conoce ("u:..."), así las kills de alguien que se
 * cambió el nombre siguen siendo suyas. Si no hay UUID se usa el nombre ("n:...").
 */
public final class StatsEngine {

    public static final class PlayerStat {
        /** Identidad: "u:&lt;uuid&gt;" o "n:&lt;nombre en minúsculas&gt;". */
        public final String key;
        public String name;
        /** UUID de la cuenta, o null si no se conoce. */
        public final String uuid;
        /** Entrada del clan (solo en las estadísticas de clan; null en la lobby). */
        public ClanConfig.Member member;
        public int kills;
        public int deaths;
        public int streak;
        public int bestStreak;
        /** Kills a compañeros del mismo clan (fuego amigo). */
        public int teamKills;

        PlayerStat(String key, String name, String uuid) {
            this.key = key;
            this.name = name;
            this.uuid = uuid;
        }

        public double kd() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }
    }

    public static final class ClanStat {
        public final ClanConfig.Clan clan;
        public int kills;
        public int deaths;
        public int teamKills;
        /** Estadísticas de los miembros, contando solo kills entre clanes. Ordenadas. */
        public final List<PlayerStat> members = new ArrayList<>();

        ClanStat(ClanConfig.Clan clan) {
            this.clan = clan;
        }

        public double kd() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }
    }

    public static final class Snapshot {
        public final List<ClanStat> clans;
        public final List<PlayerStat> lobby;
        /** Por identidad ({@link PlayerStat#key}). */
        public final Map<String, PlayerStat> lobbyByKey;
        /** Por identidad ({@link PlayerStat#key}). */
        public final Map<String, PlayerStat> clanByKey;
        /** "clanA|clanB" (minúsculas) → kills de A sobre B. */
        public final Map<String, Integer> headToHead;
        public final int events;
        public final boolean byKd;
        private final Map<String, String> nameIndex;
        private final Map<String, ClanConfig.Clan> clanById;
        private final Map<ClanConfig.Member, PlayerStat> memberStats;

        Snapshot(List<ClanStat> clans, List<PlayerStat> lobby, Map<String, PlayerStat> lobbyByKey,
                 Map<String, PlayerStat> clanByKey, Map<String, Integer> headToHead, int events, boolean byKd,
                 Map<String, String> nameIndex, Map<String, ClanConfig.Clan> clanById,
                 Map<ClanConfig.Member, PlayerStat> memberStats) {
            this.clans = clans;
            this.lobby = lobby;
            this.lobbyByKey = lobbyByKey;
            this.clanByKey = clanByKey;
            this.headToHead = headToHead;
            this.events = events;
            this.byKd = byKd;
            this.nameIndex = nameIndex;
            this.clanById = clanById;
            this.memberStats = memberStats;
        }

        public int headToHead(ClanConfig.Clan a, ClanConfig.Clan b) {
            return headToHead.getOrDefault(pairKey(a, b), 0);
        }

        /** Identidad del jugador con ese nombre (actual o uno que usó en esta sesión), o null. */
        public String idOf(String name) {
            return name == null ? null : nameIndex.get(Names.key(name));
        }

        /** Estadísticas de lobby por nombre (también encuentra nombres viejos si se lo cambió). */
        public PlayerStat lobbyByName(String name) {
            String id = idOf(name);
            return id == null ? null : lobbyByKey.get(id);
        }

        /** Estadísticas entre clanes por nombre. */
        public PlayerStat clanStatByName(String name) {
            String id = idOf(name);
            return id == null ? null : clanByKey.get(id);
        }

        public ClanConfig.Clan clanOf(PlayerStat p) {
            return p == null ? null : clanById.get(p.key);
        }

        /** Estadísticas entre clanes de un miembro (null si es una entrada repetida). */
        public PlayerStat statOf(ClanConfig.Member m) {
            return m == null ? null : memberStats.get(m);
        }

        public ClanStat clanStat(ClanConfig.Clan clan) {
            for (ClanStat cs : clans) {
                if (cs.clan == clan) return cs;
            }
            return null;
        }
    }

    public static final Comparator<PlayerStat> BY_KILLS = Comparator
            .comparingInt((PlayerStat p) -> -p.kills)
            .thenComparingDouble(p -> -p.kd())
            .thenComparingInt(p -> p.deaths)
            .thenComparing(p -> p.name.toLowerCase(java.util.Locale.ROOT))
            .thenComparing(p -> p.key);

    public static final Comparator<PlayerStat> BY_KD = Comparator
            .comparingDouble((PlayerStat p) -> -p.kd())
            .thenComparingInt(p -> -p.kills)
            .thenComparingInt(p -> p.deaths)
            .thenComparing(p -> p.name.toLowerCase(java.util.Locale.ROOT))
            .thenComparing(p -> p.key);

    private Snapshot cached;
    private boolean dirty = true;

    public void invalidate() {
        dirty = true;
    }

    public Snapshot snapshot(Session session, ClanConfig config) {
        boolean byKd = "kd".equals(config.hud.orden);
        if (dirty || cached == null || cached.byKd != byKd) {
            cached = compute(session, config, byKd);
            dirty = false;
        }
        return cached;
    }

    static String pairKey(ClanConfig.Clan a, ClanConfig.Clan b) {
        return Names.key(a.nombre) + "|" + Names.key(b.nombre);
    }

    /** Traduce (nombre, UUID) a una identidad estable. */
    private static final class Ids {
        private final Map<String, String> nameToUuid = new HashMap<>();

        Ids(Session session, ClanConfig config) {
            for (Session.KillEvent e : session.eventos) {
                learn(e.victima, e.victimaUuid);
                learn(e.asesino, e.asesinoUuid);
            }
            // los miembros tienen el nombre actual (se actualiza con el Tab), así que mandan
            for (ClanConfig.Clan c : config.clanes) {
                for (ClanConfig.Member m : c.miembros) learn(m.nombre, m.uuid);
            }
        }

        private void learn(String name, String uuid) {
            if (name != null && uuid != null) nameToUuid.put(Names.key(name), uuid);
        }

        String uuidOf(String name, String uuid) {
            if (uuid != null) return uuid;
            return name == null ? null : nameToUuid.get(Names.key(name));
        }

        String id(String name, String uuid) {
            String u = uuidOf(name, uuid);
            return u != null ? "u:" + u : "n:" + Names.key(name);
        }
    }

    public static Snapshot compute(Session session, ClanConfig config, boolean byKd) {
        Ids ids = new Ids(session, config);
        Map<String, PlayerStat> lobby = new LinkedHashMap<>();
        Map<String, PlayerStat> clanPlayers = new HashMap<>();
        Map<String, ClanConfig.Clan> clanById = new HashMap<>();
        Map<ClanConfig.Member, PlayerStat> memberStats = new IdentityHashMap<>();
        Map<String, String> nameIndex = new HashMap<>();
        Map<ClanConfig.Clan, ClanStat> clans = new LinkedHashMap<>();
        Map<String, Integer> h2h = new HashMap<>();
        boolean countFF = config.countFriendlyFire();

        for (ClanConfig.Clan c : config.clanes) {
            ClanStat cs = new ClanStat(c);
            clans.put(c, cs);
            for (ClanConfig.Member m : c.miembros) {
                String id = ids.id(m.nombre, m.uuid);
                if (clanPlayers.containsKey(id)) continue; // misma persona anotada dos veces
                PlayerStat ps = new PlayerStat(id, m.nombre, ids.uuidOf(m.nombre, m.uuid));
                ps.member = m;
                clanPlayers.put(id, ps);
                clanById.put(id, c);
                memberStats.put(m, ps);
                cs.members.add(ps);
            }
        }

        for (Session.KillEvent e : session.eventos) {
            String vid = ids.id(e.victima, e.victimaUuid);
            PlayerStat victim = lobby.computeIfAbsent(vid, k -> new PlayerStat(k, e.victima, ids.uuidOf(e.victima, e.victimaUuid)));
            victim.name = e.victima;
            nameIndex.put(Names.key(e.victima), vid);
            victim.deaths++;
            victim.streak = 0;
            if (e.asesino == null) continue;

            String kid = ids.id(e.asesino, e.asesinoUuid);
            if (kid.equals(vid)) continue; // nadie se mata a sí mismo: solo cuenta la muerte
            PlayerStat killer = lobby.computeIfAbsent(kid, k -> new PlayerStat(k, e.asesino, ids.uuidOf(e.asesino, e.asesinoUuid)));
            killer.name = e.asesino;
            nameIndex.put(Names.key(e.asesino), kid);
            killer.kills++;
            killer.streak++;
            killer.bestStreak = Math.max(killer.bestStreak, killer.streak);

            ClanConfig.Clan cv = clanById.get(vid);
            ClanConfig.Clan ck = clanById.get(kid);
            if (cv == null || ck == null) continue; // solo cuentan las kills ENTRE miembros de clanes

            PlayerStat ckStat = clanPlayers.get(kid);
            PlayerStat cvStat = clanPlayers.get(vid);
            if (cv != ck || countFF) {
                ckStat.kills++;
                ckStat.streak++;
                ckStat.bestStreak = Math.max(ckStat.bestStreak, ckStat.streak);
                cvStat.deaths++;
                cvStat.streak = 0;
                clans.get(ck).kills++;
                clans.get(cv).deaths++;
                h2h.merge(pairKey(ck, cv), 1, Integer::sum);
            } else {
                ckStat.teamKills++;
                killer.teamKills++;
                clans.get(ck).teamKills++;
            }
        }

        // el nombre actual de los miembros (viene del Tab) es el que se muestra en todos lados
        for (PlayerStat ps : clanPlayers.values()) {
            PlayerStat l = lobby.get(ps.key);
            if (l != null) l.name = ps.name;
            nameIndex.put(Names.key(ps.name), ps.key);
        }

        Comparator<PlayerStat> order = byKd ? BY_KD : BY_KILLS;
        List<ClanStat> clanList = new ArrayList<>(clans.values());
        for (ClanStat cs : clanList) cs.members.sort(order);
        clanList.sort(byKd
                ? Comparator.comparingDouble((ClanStat c) -> -c.kd()).thenComparingInt(c -> -c.kills)
                : Comparator.comparingInt((ClanStat c) -> -c.kills).thenComparingDouble(c -> -c.kd()));

        List<PlayerStat> lobbyList = new ArrayList<>(lobby.values());
        lobbyList.sort(order);
        return new Snapshot(clanList, lobbyList, lobby, clanPlayers, h2h, session.eventos.size(), byKd,
                nameIndex, clanById, memberStats);
    }

    public static String fmtKd(double kd) {
        return String.format(java.util.Locale.ROOT, "%.2f", kd);
    }
}
