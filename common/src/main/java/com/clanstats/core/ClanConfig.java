package com.clanstats.core;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Configuración guardada en config/clanstats/config.json (clanes, HUD y opciones). */
public final class ClanConfig {
    /** Largo máximo del nombre de un clan. */
    public static final int CLAN_NAME_MAX = 24;

    /** Un jugador de un clan. Se reconoce por el UUID de su cuenta; el nombre se actualiza solo si se lo cambia. */
    public static final class Member {
        public String nombre;
        /** UUID de la cuenta (minúsculas, con guiones). null mientras todavía no se conoce. */
        public String uuid;

        public Member() {}

        public Member(String nombre, String uuid) {
            this.nombre = nombre;
            this.uuid = Uuids.normalize(uuid);
        }

        public String key() {
            return Names.key(nombre);
        }

        @Override
        public String toString() {
            return nombre;
        }
    }

    /** Lee "miembros" en el formato nuevo ({nombre, uuid}) y en el viejo (lista de nombres), para no perder clanes. */
    public static final class MemberAdapter implements JsonDeserializer<Member> {
        @Override
        public Member deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
            if (json == null || json.isJsonNull()) return null;
            if (json.isJsonPrimitive()) return new Member(json.getAsString(), null);
            if (!json.isJsonObject()) return null;
            JsonObject o = json.getAsJsonObject();
            return new Member(text(o, "nombre"), text(o, "uuid"));
        }

        private static String text(JsonObject o, String field) {
            JsonElement e = o.get(field);
            return e == null || !e.isJsonPrimitive() ? null : e.getAsString();
        }
    }

    public static final class Clan {
        public String nombre;
        public String color = "blanco";
        public List<Member> miembros = new ArrayList<>();

        public Clan() {}

        public Clan(String nombre, String color) {
            this.nombre = nombre;
            this.color = color;
        }

        public List<String> memberNames() {
            List<String> out = new ArrayList<>();
            for (Member m : miembros) out.add(m.nombre);
            return out;
        }
    }

    public static final class Hud {
        /** OFF, CLANES, LOBBY o AMBOS. */
        public String modo = "AMBOS";
        /** Cuántos jugadores mostrar por clan. */
        public int topClan = 3;
        /** Cuántos jugadores mostrar en el top de la lobby. */
        public int topLobby = 5;
        public float escala = 1.0f;
        public int x = 4;
        public int y = 4;
        /** true = pegado al lado derecho de la pantalla. */
        public boolean derecha = false;
        /** "kills" o "kd". */
        public String orden = "kills";
        public boolean fondo = true;
    }

    public enum AddStatus { ADDED, MOVED, ALREADY }

    /** Resultado de agregar un jugador: {@code previous} es el clan donde estaba antes (o null). */
    public record AddResult(AddStatus status, Member member, Clan previous) {}

    /** Un miembro que apareció en el Tab con otro nombre pero el mismo UUID. */
    public record Rename(Member member, Clan clan, String oldName) {}

    public record SyncResult(boolean changed, List<Rename> renames) {}

    public enum LookupKind { NONE, RESOLVED, MERGED }

    /** RESOLVED: se guardó el UUID. MERGED: era la misma cuenta que otro miembro y se quitó la entrada repetida. */
    public record LookupResult(LookupKind kind, Member member, Clan clan) {}

    /** 1 = miembros como lista de nombres; 2 = miembros con UUID. */
    public int version = 2;
    public List<Clan> clanes = new ArrayList<>();
    public Hud hud = new Hud();
    /** Muestra en el HUD las últimas kills detectadas y guarda debug.log. */
    public boolean debug = false;
    /** Detección por heurística cuando ningún patrón coincide. */
    public boolean heuristica = true;
    /** "separar": las kills entre miembros del mismo clan no cuentan para el clan. "contar": sí cuentan. */
    public String fuegoAmigo = "separar";
    /** Buscar en la API de Mojang el UUID de los jugadores que agregas cuando no están en la sala. */
    public boolean buscarUuid = true;
    /** Plantillas extra del usuario, con {V}, {K} y *. */
    public List<String> patrones = new ArrayList<>();
    /** Idioma del mod: "auto" (el del juego), "es" o "en". */
    public String idioma = Texts.AUTO;
    /** Mod activado. Apagado no cuenta kills ni muestra el HUD ni los colores (los datos se guardan igual). */
    public boolean activo = true;
    /** Color de clan en el nombre de los miembros: en la lista de Tab y encima de cada jugador. */
    public boolean colorNombres = true;
    /** Mostrar a los miembros desconectados en las listas del menú y del HUD (sus stats cuentan igual). */
    public boolean mostrarDesconectados = true;

    private transient Map<String, Member> byName;
    private transient Map<String, Member> byUuid;
    private transient Map<Member, Clan> owner;

    /**
     * Nombre de clan válido: de 1 a {@value #CLAN_NAME_MAX} caracteres, con espacios y símbolos permitidos,
     * salvo § (códigos de color de Minecraft), comillas dobles, barra invertida y caracteres de control.
     */
    public static boolean isValidClanName(String name) {
        if (name == null || name.isEmpty() || name.length() > CLAN_NAME_MAX) return false;
        if (!name.equals(name.trim()) || name.contains("  ")) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\u00a7' || c == '"' || c == '\\' || Character.isISOControl(c)) return false;
        }
        return true;
    }

    /** Limpia lo escrito como nombre de clan: quita comillas de los extremos y espacios sobrantes. */
    public static String cleanClanName(String raw) {
        if (raw == null) return "";
        String s = raw.strip();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1).strip();
        return s.replaceAll("\\s+", " ");
    }

    // ------------------------------------------------------------------ reparación tras cargar

    /** Corrige valores nulos, repetidos o fuera de rango (por si alguien editó el JSON a mano). */
    public void sanitize() {
        if (clanes == null) clanes = new ArrayList<>();
        if (hud == null) hud = new Hud();
        if (patrones == null) patrones = new ArrayList<>();
        if (fuegoAmigo == null || !(fuegoAmigo.equals("separar") || fuegoAmigo.equals("contar"))) fuegoAmigo = "separar";
        idioma = Texts.normalizeSetting(idioma);
        clanes.removeIf(c -> c == null || c.nombre == null);
        for (Clan c : clanes) {
            // "§c" y parecidos son códigos de color de Minecraft: se quitan enteros
            c.nombre = cleanClanName(c.nombre.replaceAll("\u00a7.?", "").replace("\"", "").replace("\\", ""));
            if (c.nombre.length() > CLAN_NAME_MAX) c.nombre = c.nombre.substring(0, CLAN_NAME_MAX).strip();
            if (c.miembros == null) c.miembros = new ArrayList<>();
        }
        clanes.removeIf(c -> c.nombre.isEmpty());
        // dos clanes con el mismo nombre: se juntan en el primero (no se pierde ning\u00fan miembro)
        Map<String, Clan> firstByName = new HashMap<>();
        clanes.removeIf(c -> {
            Clan first = firstByName.putIfAbsent(Names.key(c.nombre), c);
            if (first == null) return false;
            first.miembros.addAll(c.miembros);
            return true;
        });
        for (Clan c : clanes) {
            c.miembros.removeIf(m -> m == null || !Names.isValid(m.nombre));
            for (Member m : c.miembros) m.uuid = Uuids.normalize(m.uuid);
            c.color = Colors.normalize(c.color);
        }
        removeDuplicates();
        version = 2;
        String m = hud.modo == null ? "" : hud.modo.toUpperCase(Locale.ROOT);
        hud.modo = switch (m) {
            case "OFF", "CLANES", "LOBBY", "AMBOS" -> m;
            default -> "AMBOS";
        };
        hud.topClan = clamp(hud.topClan, 0, 30);
        hud.topLobby = clamp(hud.topLobby, 0, 30);
        if (!(hud.escala >= 0.3f && hud.escala <= 3.0f)) hud.escala = 1.0f;
        hud.x = clamp(hud.x, 0, 4000);
        hud.y = clamp(hud.y, 0, 4000);
        if (!"kd".equals(hud.orden)) hud.orden = "kills";
        reindex();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Un jugador solo puede estar una vez y en un solo clan. Primero manda el UUID (dos entradas con el mismo
     * UUID son la misma persona aunque tengan distinto nombre) y después el nombre. Devuelve cuántas se quitaron.
     */
    public int removeDuplicates() {
        Set<String> uuids = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<Member> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Clan c : clanes) {
            for (Member m : c.miembros) {
                if (m.uuid != null && uuids.add(m.uuid)) {
                    keep.add(m);
                    names.add(m.key());
                }
            }
        }
        for (Clan c : clanes) {
            for (Member m : c.miembros) {
                if (m.uuid == null && names.add(m.key())) keep.add(m);
            }
        }
        int removed = 0;
        for (Clan c : clanes) {
            int before = c.miembros.size();
            c.miembros.removeIf(m -> !keep.contains(m));
            removed += before - c.miembros.size();
        }
        reindex();
        return removed;
    }

    public void reindex() {
        Map<String, Member> n = new HashMap<>();
        Map<String, Member> u = new HashMap<>();
        Map<Member, Clan> o = new IdentityHashMap<>();
        for (Clan c : clanes) {
            for (Member m : c.miembros) {
                o.put(m, c);
                n.putIfAbsent(m.key(), m);
                if (m.uuid != null) u.putIfAbsent(m.uuid, m);
            }
        }
        byName = n;
        byUuid = u;
        owner = o;
    }

    private void ensureIndex() {
        if (owner == null) reindex();
    }

    // ------------------------------------------------------------------ clanes y miembros

    public Clan findClan(String name) {
        if (name == null) return null;
        for (Clan c : clanes) {
            if (c.nombre.equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    /**
     * Busca un miembro: primero por UUID y después por nombre. Si el nombre coincide pero el UUID es de otra
     * cuenta (alguien se cambió el nombre y otro jugador tomó el viejo), no es el mismo jugador.
     */
    public Member findMember(String name, String uuid) {
        ensureIndex();
        String u = Uuids.normalize(uuid);
        if (u != null) {
            Member m = byUuid.get(u);
            if (m != null) return m;
        }
        if (name == null) return null;
        Member m = byName.get(Names.key(name));
        if (m == null) return null;
        if (u != null && m.uuid != null && !m.uuid.equals(u)) return null;
        return m;
    }

    public Member findMember(String name) {
        return findMember(name, null);
    }

    public Member memberByUuid(String uuid) {
        ensureIndex();
        String u = Uuids.normalize(uuid);
        return u == null ? null : byUuid.get(u);
    }

    public Clan clanOf(Member member) {
        ensureIndex();
        return member == null ? null : owner.get(member);
    }

    /** Clan del jugador (por nombre) o null si no pertenece a ninguno. */
    public Clan clanOf(String player) {
        return clanOf(findMember(player, null));
    }

    public Clan clanOf(String player, String uuid) {
        return clanOf(findMember(player, uuid));
    }

    /** Miembros a los que todavía les falta el UUID. */
    public List<Member> membersWithoutUuid() {
        List<Member> out = new ArrayList<>();
        for (Clan c : clanes) {
            for (Member m : c.miembros) {
                if (m.uuid == null) out.add(m);
            }
        }
        return out;
    }

    /**
     * Agrega al jugador al clan. Si ya estaba en otro clan lo mueve (nunca queda repetido).
     * {@code uuid} puede ser null si no se conoce todavía.
     */
    public AddResult addMember(Clan clan, String name, String uuid) {
        String u = Uuids.normalize(uuid);
        Member existing = findMember(name, u);
        if (existing != null) {
            Clan prev = clanOf(existing);
            // con UUID el nombre viene del Tab (es el actual); sin UUID se respeta el que ya estaba guardado
            if (u != null) {
                existing.uuid = u;
                if (Names.isValid(name)) existing.nombre = name;
            }
            if (prev != clan) {
                if (prev != null) prev.miembros.remove(existing);
                clan.miembros.add(existing);
            }
            removeDuplicates();
            return new AddResult(prev == clan ? AddStatus.ALREADY : AddStatus.MOVED, existing, prev);
        }
        Member m = new Member(name, u);
        clan.miembros.add(m);
        removeDuplicates();
        return new AddResult(AddStatus.ADDED, m, null);
    }

    /** Saca al miembro de su clan. Devuelve el clan del que salió o null. */
    public Clan removeMember(Member member) {
        Clan c = clanOf(member);
        if (c != null) {
            c.miembros.remove(member);
            reindex();
        }
        return c;
    }

    public Clan removeMember(String player) {
        return removeMember(findMember(player, null));
    }

    /**
     * Compara los miembros con la lista de Tab: guarda el UUID de quien todavía no lo tenía y actualiza el
     * nombre de quien se lo cambió (se reconoce por el UUID).
     */
    public SyncResult syncOnline(Collection<PlayerRef> online) {
        ensureIndex();
        boolean changed = false;
        List<Rename> renames = new ArrayList<>();
        for (PlayerRef p : online) {
            if (p == null || p.uuid() == null || !Names.isValid(p.name())) continue;
            Member m = byUuid.get(p.uuid());
            if (m != null) {
                if (!m.nombre.equals(p.name())) {
                    String old = m.nombre;
                    m.nombre = p.name();
                    changed = true;
                    if (!old.equalsIgnoreCase(p.name())) renames.add(new Rename(m, owner.get(m), old));
                }
                continue;
            }
            m = byName.get(Names.key(p.name()));
            if (m != null && m.uuid == null) {
                m.uuid = p.uuid();
                m.nombre = p.name();
                changed = true;
            }
        }
        if (changed) removeDuplicates();
        return new SyncResult(changed, renames);
    }

    /** Aplica el UUID encontrado en la API de Mojang para el miembro llamado {@code name}. */
    public LookupResult applyLookup(String name, String uuid, String canonicalName) {
        ensureIndex();
        String u = Uuids.normalize(uuid);
        Member m = name == null ? null : byName.get(Names.key(name));
        if (m == null || m.uuid != null || u == null) return new LookupResult(LookupKind.NONE, m, m == null ? null : owner.get(m));
        Member other = byUuid.get(u);
        if (other != null && other != m) {
            Clan c = owner.get(m);
            if (c != null) c.miembros.remove(m);
            reindex();
            return new LookupResult(LookupKind.MERGED, other, owner.get(other));
        }
        m.uuid = u;
        if (Names.isValid(canonicalName)) m.nombre = canonicalName;
        removeDuplicates();
        return new LookupResult(LookupKind.RESOLVED, m, owner.get(m));
    }

    public boolean countFriendlyFire() {
        return "contar".equals(fuegoAmigo);
    }
}
