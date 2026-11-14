package com.clanstats.mod;

import com.clanstats.core.ClanConfig;
import com.clanstats.core.Colors;
import com.clanstats.core.HudLayout;
import com.clanstats.core.KillParser;
import com.clanstats.core.MojangLookup;
import com.clanstats.core.Names;
import com.clanstats.core.OnlineRoster;
import com.clanstats.core.PlayerRef;
import com.clanstats.core.ReportText;
import com.clanstats.core.Session;
import com.clanstats.core.StatsEngine;
import com.clanstats.core.Storage;
import com.clanstats.core.Texts;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** Estado del mod y eventos comunes a todas las versiones. */
public final class ClanStatsCore {
    public static final String MOD_ID = "clanstats";
    public static final Logger LOG = LoggerFactory.getLogger("ClanStats");
    static final String[] HUD_MODES = {"OFF", "CLANES", "LOBBY", "AMBOS"};

    /** Estado de la búsqueda del UUID de alguien que agregaste sin que estuviera en la sala. */
    public enum LookupState { PENDING, NOT_FOUND, ERROR }

    /**
     * Resultado de agregar un jugador por nombre. ADDED/MOVED/ALREADY: quedó guardado con su UUID.
     * NOT_FOUND: no existe ninguna cuenta con ese nombre (no se guardó). ERROR: no se pudo consultar a Mojang
     * (no se guardó). CLAN_GONE: el clan se borró mientras se buscaba. PENDING: ya se estaba buscando ese nombre.
     */
    public enum AddKind { ADDED, MOVED, ALREADY, NOT_FOUND, ERROR, CLAN_GONE, PENDING }

    public record AddReport(AddKind kind, String typed, ClanConfig.Member member, ClanConfig.Clan clan,
                            ClanConfig.Clan previous) {
        /** Nombre a mostrar: el de la cuenta si se encontró, si no lo escrito. */
        public String name() {
            return member != null ? member.nombre : typed;
        }
    }

    private static ClanStatsCore instance;

    public final Platform platform;
    public final Storage storage;
    public final KillParser parser = new KillParser();
    public final OnlineRoster roster = new OnlineRoster();
    private final StatsEngine engine = new StatsEngine();
    private final MojangLookup lookup;
    private final Map<String, LookupState> lookups = new ConcurrentHashMap<>();
    private final Set<String> pendingAdds = new HashSet<>();
    private final Queue<Runnable> mainThread = new ConcurrentLinkedQueue<>();
    public ClanConfig config;
    public Session session;

    private boolean configDirty;
    private boolean sessionDirty;
    private long lastSave = System.currentTimeMillis();
    private int ticks;
    private String lastMessage = "";
    private long lastMessageTime;
    private final Deque<FeedEntry> feed = new ArrayDeque<>();
    private String toast;
    private long toastUntil;
    private int version;
    private boolean menuRequested;
    private long resolveMissingAt;
    private boolean missingResolved;
    private List<PlayerRef> onlineList;
    private final Set<String> onlineUuids = new HashSet<>();
    private final Set<String> onlineNames = new HashSet<>();

    private record FeedEntry(long time, String killer, String victim, boolean heuristic) {}

    private ClanStatsCore(Platform platform, MojangLookup lookup) {
        this.platform = platform;
        this.lookup = lookup;
        this.storage = new Storage(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID), LOG::warn);
        this.config = storage.loadConfig();
        this.session = storage.loadSession();
        applyParserSettings();
        applyLanguage();
    }

    public static ClanStatsCore init(Platform platform) {
        return init(platform, new MojangLookup());
    }

    public static ClanStatsCore init(Platform platform, MojangLookup lookup) {
        if (instance == null) {
            instance = new ClanStatsCore(platform, lookup);
            instance.registerEvents();
            LOG.info("ClanStats listo para Minecraft {}: {} clanes, {} muertes en la sesión guardada",
                    platform.minecraftVersion(), instance.config.clanes.size(), instance.session.eventos.size());
        }
        return instance;
    }

    public static ClanStatsCore get() {
        return instance;
    }

    private void registerEvents() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onGameMessage(message);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> Commands.register(dispatcher, this));
        ClientTickEvents.END_CLIENT_TICK.register(client -> onTick());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveNow());
    }

    // ------------------------------------------------------------------ eventos

    private void onGameMessage(Component message) {
        if (!config.activo) return; // mod desactivado: no se cuenta nada
        try {
            String raw = message.getString();
            long now = System.currentTimeMillis();
            // algunos servidores mandan el mismo mensaje dos veces seguidas
            if (raw.equals(lastMessage) && now - lastMessageTime < 400) return;
            lastMessage = raw;
            lastMessageTime = now;

            Platform.DeathInfo info = platform.deathInfo(message);
            KillParser.Result r = parser.parse(raw, info == null ? null : info.key(), info == null ? null : info.args(), roster);
            if (config.debug) storage.appendDebug((r == null ? "----  " : "KILL  " + r + "  |  ") + raw);
            if (r != null) {
                record(r.victim, r.killer, r.source.label + ":" + r.templateId, raw, r.source == KillParser.Source.HEURISTICA);
            }
        } catch (Throwable t) {
            LOG.warn("Error procesando un mensaje del chat", t);
        }
    }

    private void onTick() {
        try {
            Runnable task;
            while ((task = mainThread.poll()) != null) task.run();
            if (++ticks % 20 == 0) {
                syncOnline(platform.onlinePlayers());
                applyLanguage();
            }
            if (menuRequested) {
                menuRequested = false;
                platform.openMenu();
            }
            if (!missingResolved && resolveMissingAt > 0 && System.currentTimeMillis() >= resolveMissingAt) {
                missingResolved = true;
                for (ClanConfig.Member m : config.membersWithoutUuid()) requestLookup(m.nombre);
            }
            if ((configDirty || sessionDirty) && System.currentTimeMillis() - lastSave > 10_000) saveNow();
        } catch (Throwable t) {
            LOG.warn("Error en el tick de ClanStats", t);
        }
    }

    /** Lee la lista de Tab: guarda UUIDs que faltaban y detecta a los miembros que se cambiaron el nombre. */
    private void syncOnline(Collection<PlayerRef> online) {
        roster.update(online);
        if (online.isEmpty()) return;
        // ya en una sala: primero se aprovecha el Tab y unos segundos después se busca en Mojang lo que falte
        if (resolveMissingAt == 0) resolveMissingAt = System.currentTimeMillis() + 8_000;
        ClanConfig.SyncResult r = config.syncOnline(online);
        if (r.changed()) configChanged();
        for (ClanConfig.Rename rn : r.renames()) {
            MutableComponent msg = Chat.prefix().append(Chat.tr(ChatFormatting.GRAY, "chat.renamed", rn.oldName(), rn.member().nombre));
            if (rn.clan() != null) msg.append(Chat.t(" — " + Texts.tr("chat.still_in") + " ", ChatFormatting.GRAY)).append(Chat.clan(rn.clan()));
            platform.chat(msg.append(Chat.t(" (" + Texts.tr("chat.same_uuid") + ")", ChatFormatting.DARK_GRAY)));
        }
    }

    // ------------------------------------------------------------------ datos

    public void record(String victim, String killer, String source, String raw, boolean heuristic) {
        long now = System.currentTimeMillis();
        String msg = config.debug && raw != null ? (raw.length() > 160 ? raw.substring(0, 160) : raw) : null;
        session.eventos.add(new Session.KillEvent(now, victim, killer, source, msg,
                uuidFor(victim), killer == null ? null : uuidFor(killer)));
        sessionDirty = true;
        engine.invalidate();
        feed.addFirst(new FeedEntry(now, killer, victim, heuristic));
        while (feed.size() > 5) feed.removeLast();
    }

    /** UUID de un jugador por su nombre: primero la lista de Tab y después los miembros de clanes. */
    public String uuidFor(String name) {
        if (name == null) return null;
        String u = roster.uuidOf(name);
        if (u != null) return u;
        ClanConfig.Member m = config.findMember(name);
        return m == null ? null : m.uuid;
    }

    public Session.KillEvent undoLast() {
        if (session.eventos.isEmpty()) return null;
        Session.KillEvent e = session.eventos.remove(session.eventos.size() - 1);
        sessionDirty = true;
        engine.invalidate();
        if (!feed.isEmpty()) feed.removeFirst();
        return e;
    }

    /** Guarda la sesión actual en historial/ y empieza una nueva. Devuelve el archivo del historial (o null). */
    public Path resetSession() {
        Path archived = null;
        if (!session.eventos.isEmpty()) {
            archived = storage.archive(session, summaryText());
        }
        session = new Session();
        sessionDirty = true;
        engine.invalidate();
        feed.clear();
        version++;
        saveNow();
        return archived;
    }

    /** Llamar después de cambiar clanes u opciones. */
    public void configChanged() {
        config.reindex();
        configDirty = true;
        engine.invalidate();
        applyParserSettings();
        version++;
    }

    /** Cambia cada vez que cambian los clanes o se reinicia la sesión (el menú lo usa para redibujarse). */
    public int version() {
        return version;
    }

    // ------------------------------------------------------------------ idioma

    /** Aplica el idioma elegido (o el del juego si está en "auto"). Si cambió, el menú se redibuja. */
    public void applyLanguage() {
        String game;
        try {
            game = platform.gameLanguage();
        } catch (RuntimeException e) {
            game = "";
        }
        if (Texts.setLanguage(Texts.resolve(config.idioma, game))) version++;
    }

    /** Cambia la opción de idioma: "auto", "es" o "en". */
    public void setLanguageSetting(String setting) {
        config.idioma = Texts.normalizeSetting(setting);
        configChanged();
        applyLanguage();
    }

    /** Siguiente opción para el botón de idioma: auto → español → inglés → auto. */
    public String nextLanguageSetting() {
        return switch (config.idioma) {
            case Texts.AUTO -> Texts.ES;
            case Texts.ES -> Texts.EN;
            default -> Texts.AUTO;
        };
    }

    /** "Auto (Español)", "Español" o "English", en el idioma en uso. */
    public String languageLabel() {
        String current = Texts.tr(Texts.english() ? "lang.english" : "lang.spanish");
        return Texts.AUTO.equals(config.idioma) ? Texts.tr("lang.auto", current) : current;
    }

    // ------------------------------------------------------------------ conectados

    private void indexOnline() {
        List<PlayerRef> now = roster.current();
        if (now == onlineList) return;
        onlineList = now;
        onlineUuids.clear();
        onlineNames.clear();
        for (PlayerRef p : now) {
            if (p.uuid() != null) onlineUuids.add(p.uuid());
            onlineNames.add(Names.key(p.name()));
        }
    }

    /** true si el miembro está ahora en la lista de Tab (por UUID; por nombre si todavía no tiene UUID). */
    public boolean isOnline(ClanConfig.Member m) {
        if (m == null) return false;
        indexOnline();
        return m.uuid != null ? onlineUuids.contains(m.uuid) : onlineNames.contains(m.key());
    }

    /** Color RGB del clan del jugador (para pintar su nombre en el Tab), o null si no es de ningún clan. */
    public Integer tabColor(String name, String uuid) {
        if (!config.activo || !config.colorNombres || (name == null && uuid == null)) return null;
        ClanConfig.Clan c = config.clanOf(name, uuid);
        return c == null ? null : Colors.rgb(c.color);
    }

    /** Si un miembro sale en las listas (menú y HUD): con "ocultar desconectados", solo los que están en la sala. */
    public boolean memberShown(StatsEngine.PlayerStat p) {
        return config.mostrarDesconectados || p.member == null || isOnline(p.member);
    }

    public boolean memberShown(ClanConfig.Member m) {
        return config.mostrarDesconectados || isOnline(m);
    }

    // ------------------------------------------------------------------ interruptores

    /** Activa o pausa el mod. Pausado no cuenta kills ni muestra el HUD ni los colores; los datos se guardan. */
    public void setActive(boolean on) {
        config.activo = on;
        configChanged();
        showToast(Texts.tr(on ? "toast.enabled" : "toast.disabled"));
    }

    /** Color de clan en el Tab y encima de los jugadores. */
    public void setNameColors(boolean on) {
        config.colorNombres = on;
        configChanged();
    }

    /** Mostrar u ocultar a los miembros desconectados en las listas (sus stats se guardan igual). */
    public void setShowOffline(boolean on) {
        config.mostrarDesconectados = on;
        configChanged();
    }

    /** Cuántos miembros del clan están conectados. */
    public int onlineCount(ClanConfig.Clan clan) {
        int n = 0;
        for (ClanConfig.Member m : clan.miembros) {
            if (isOnline(m)) n++;
        }
        return n;
    }

    void applyParserSettings() {
        parser.setHeuristicEnabled(config.heuristica);
        for (String e : parser.setCustomTemplates(config.patrones)) LOG.warn("Plantilla inválida {}", e);
    }

    public void saveNow() {
        if (configDirty && storage.saveConfig(config)) configDirty = false;
        if (sessionDirty && storage.saveSession(session)) sessionDirty = false;
        lastSave = System.currentTimeMillis();
    }

    public StatsEngine.Snapshot snapshot() {
        return engine.snapshot(session, config);
    }

    /** Resumen completo (sin límite de largo), el que va al historial. */
    public String summaryText() {
        return ReportText.build(snapshot(), config, session);
    }

    /** Resumen con formato de Discord que entra en un solo mensaje. */
    public String discordText() {
        return ReportText.discord(snapshot(), config, session);
    }

    // ------------------------------------------------------------------ jugadores

    /**
     * Agrega un jugador a un clan por su nombre (lo mueve si estaba en otro). Siempre se guarda con el UUID de su
     * cuenta: si está en la sala (o ya se conoce) se agrega al instante y se devuelve el resultado; si no, se
     * busca en la API de Mojang, se devuelve null y {@code later} recibe el resultado en el hilo del juego.
     * Un nombre que no es de ninguna cuenta de Minecraft no se guarda.
     */
    public AddReport addByName(ClanConfig.Clan clan, String typed, Consumer<AddReport> later) {
        PlayerRef online = roster.find(typed);
        if (online != null && online.uuid() != null) return apply(clan, online.name(), online.uuid());
        ClanConfig.Member known = config.findMember(typed);
        if (known != null && known.uuid != null) return apply(clan, known.nombre, known.uuid);
        StatsEngine.PlayerStat seen = snapshot().lobbyByName(typed);
        if (seen != null && seen.uuid != null && seen.name.equalsIgnoreCase(typed)) return apply(clan, seen.name, seen.uuid);

        String k = Names.key(typed);
        if (!pendingAdds.add(k)) return new AddReport(AddKind.PENDING, typed, null, clan, null);
        lookup.lookup(typed).whenComplete((res, err) -> mainThread.add(() -> {
            pendingAdds.remove(k);
            later.accept(finishAdd(clan, typed, res, err));
        }));
        return null;
    }

    private AddReport finishAdd(ClanConfig.Clan clan, String typed, Optional<MojangLookup.Profile> res, Throwable err) {
        if (err != null) {
            LOG.warn("No se pudo consultar el UUID de {}: {}", typed, err.getMessage());
            return new AddReport(AddKind.ERROR, typed, null, clan, null);
        }
        if (res == null || res.isEmpty()) return new AddReport(AddKind.NOT_FOUND, typed, null, clan, null);
        if (!config.clanes.contains(clan)) return new AddReport(AddKind.CLAN_GONE, typed, null, clan, null);
        MojangLookup.Profile p = res.get();
        lookups.remove(Names.key(typed));
        return apply(clan, p.name(), p.uuid());
    }

    private AddReport apply(ClanConfig.Clan clan, String name, String uuid) {
        ClanConfig.AddResult r = config.addMember(clan, name, uuid);
        configChanged();
        AddKind kind = switch (r.status()) {
            case MOVED -> AddKind.MOVED;
            case ALREADY -> AddKind.ALREADY;
            default -> AddKind.ADDED;
        };
        return new AddReport(kind, name, r.member(), clan, r.previous());
    }

    /** true si se está buscando en Mojang a alguien que se quiere agregar. */
    public boolean isAdding(String name) {
        return name != null && pendingAdds.contains(Names.key(name));
    }

    private static String reportKey(AddKind kind) {
        return switch (kind) {
            case ADDED -> "add.added";
            case MOVED -> "add.moved";
            case ALREADY -> "add.already";
            case NOT_FOUND -> "add.not_found";
            case ERROR -> "add.error";
            case CLAN_GONE -> "add.clan_gone";
            case PENDING -> "add.pending";
        };
    }

    /** true si el jugador quedó guardado en el clan (también si ya estaba). */
    public static boolean saved(AddReport r) {
        return r.kind() == AddKind.ADDED || r.kind() == AddKind.MOVED || r.kind() == AddKind.ALREADY;
    }

    /** Resultado de agregar, como texto para el menú. */
    public String addReportText(AddReport r) {
        return Texts.tr(reportKey(r.kind()), r.name(), r.kind() == AddKind.MOVED && r.previous() != null ? r.previous().nombre : r.clan().nombre,
                r.clan().nombre);
    }

    /** Resultado de agregar, como mensaje de chat (con los clanes en su color). */
    public Component addReportMessage(AddReport r) {
        ChatFormatting base = switch (r.kind()) {
            case ADDED, MOVED -> ChatFormatting.GREEN;
            case ALREADY, PENDING -> ChatFormatting.YELLOW;
            default -> ChatFormatting.RED;
        };
        Object from = r.kind() == AddKind.MOVED && r.previous() != null ? Chat.clan(r.previous()) : Chat.clan(r.clan());
        return Chat.prefix().append(Chat.fmt(base, reportKey(r.kind()), Chat.t(r.name(), ChatFormatting.WHITE), from, Chat.clan(r.clan())));
    }

    /** Pide a Mojang el UUID de un miembro viejo que todavía no lo tiene (en otro hilo). */
    public boolean requestLookup(String name) {
        if (!config.buscarUuid || !Names.isValid(name)) return false;
        String k = Names.key(name);
        if (lookups.get(k) == LookupState.PENDING) return true;
        lookups.put(k, LookupState.PENDING);
        lookup.lookup(name).whenComplete((res, err) -> mainThread.add(() -> onLookup(name, res, err)));
        return true;
    }

    public LookupState lookupState(String name) {
        return name == null ? null : lookups.get(Names.key(name));
    }

    private void onLookup(String name, Optional<MojangLookup.Profile> res, Throwable err) {
        String k = Names.key(name);
        if (err != null) {
            lookups.put(k, LookupState.ERROR);
            LOG.warn("No se pudo consultar el UUID de {}: {}", name, err.getMessage());
            return;
        }
        if (res == null || res.isEmpty()) {
            lookups.put(k, LookupState.NOT_FOUND);
            ClanConfig.Member m = config.findMember(name);
            if (m != null && m.uuid == null) {
                MutableComponent msg = Chat.prefix().append(Chat.tr(ChatFormatting.RED, "chat.legacy_not_found", name));
                ClanConfig.Clan c = config.clanOf(m);
                if (c != null) msg.append(Chat.t(" (" + Texts.tr("chat.is_in") + " ", ChatFormatting.GRAY)).append(Chat.clan(c)).append(Chat.t(")", ChatFormatting.GRAY));
                platform.chat(msg.append(Chat.tr(ChatFormatting.GRAY, "chat.legacy_not_found_hint", name)));
                version++;
            }
            return;
        }
        lookups.remove(k);
        MojangLookup.Profile p = res.get();
        ClanConfig.LookupResult r = config.applyLookup(name, p.uuid(), p.name());
        if (r.kind() == ClanConfig.LookupKind.RESOLVED) {
            configChanged();
        } else if (r.kind() == ClanConfig.LookupKind.MERGED) {
            configChanged();
            platform.chat(Chat.info(Texts.tr("chat.merged", name,
                    r.member().nombre + (r.clan() != null ? " (" + r.clan().nombre + ")" : ""))));
        }
    }

    /** Jugadores conocidos (Tab + los que salieron en kills + miembros de clanes), para autocompletar. */
    public List<String> knownNames() {
        Map<String, String> names = new TreeMap<>();
        for (String n : roster.names()) names.put(Names.key(n), n);
        for (StatsEngine.PlayerStat p : snapshot().lobby) names.putIfAbsent(Names.key(p.name), p.name);
        for (ClanConfig.Clan c : config.clanes) {
            for (ClanConfig.Member m : c.miembros) names.putIfAbsent(m.key(), m.nombre);
        }
        return new ArrayList<>(names.values());
    }

    /** Devuelve el nombre con las mayúsculas correctas (y el actual si se lo cambió) cuando lo conocemos. */
    public String canonicalName(String typed) {
        PlayerRef p = roster.find(typed);
        if (p != null) return p.name();
        ClanConfig.Member m = config.findMember(typed);
        if (m != null) return m.nombre;
        StatsEngine.PlayerStat s = snapshot().lobbyByName(typed);
        return s != null ? s.name : typed;
    }

    /** Abre el menú visual en el próximo tick (desde un comando el chat todavía se está cerrando). */
    public void requestMenu() {
        menuRequested = true;
    }

    // ------------------------------------------------------------------ HUD

    public void cycleHudMode() {
        if (!config.activo) {
            showToast(Texts.tr("toast.disabled"));
            return;
        }
        int idx = 0;
        for (int i = 0; i < HUD_MODES.length; i++) {
            if (HUD_MODES[i].equals(config.hud.modo)) idx = i;
        }
        setHudMode(HUD_MODES[(idx + 1) % HUD_MODES.length]);
    }

    public void setHudMode(String mode) {
        config.hud.modo = mode;
        configDirty = true;
        showToast("ClanStats HUD: " + modeLabel(mode));
    }

    /** "solo clanes", "clans only"... */
    public static String modeLabel(String mode) {
        return Texts.tr("hud.mode." + modeKey(mode));
    }

    /** Versión corta para el botón del menú: "clanes", "clans"... */
    public static String shortModeLabel(String mode) {
        return Texts.tr("hud.short." + modeKey(mode));
    }

    private static String modeKey(String mode) {
        return switch (mode) {
            case "OFF" -> "off";
            case "CLANES" -> "clans";
            case "LOBBY" -> "lobby";
            default -> "both";
        };
    }

    public void showToast(String text) {
        toast = text;
        toastUntil = System.currentTimeMillis() + 2500;
    }

    /** Lo que hay que dibujar este frame, o null si el HUD está apagado. */
    public HudLayout.Frame hudFrame(HudLayout.Metrics metrics) {
        long now = System.currentTimeMillis();
        String activeToast = toast != null && now < toastUntil ? toast : null;
        if (!config.activo) return HudLayout.toastOnly(activeToast, config, metrics);
        if ("OFF".equals(config.hud.modo) && activeToast == null) return null;
        List<HudLayout.FeedLine> lines = new ArrayList<>();
        if (config.debug) {
            for (FeedEntry f : feed) {
                if (now - f.time() < 60_000) lines.add(new HudLayout.FeedLine(f.killer(), f.victim(), f.heuristic()));
            }
        }
        return HudLayout.build(snapshot(), config, metrics, lines, activeToast, this::memberShown);
    }

    /** Mensaje para el chat con el prefijo [ClanStats]. */
    public static Component message(String text, boolean error) {
        return error ? Chat.err(text) : Chat.ok(text);
    }
}
