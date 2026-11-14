package com.clanstats.mod;

import com.clanstats.core.ClanConfig;
import com.clanstats.core.Colors;
import com.clanstats.core.KillParser;
import com.clanstats.core.Names;
import com.clanstats.core.Session;
import com.clanstats.core.StatsEngine;
import com.clanstats.core.Texts;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Comandos del mod (solo del lado del cliente, nunca se envían al servidor). Hay dos versiones del mismo árbol:
 * /clanes con palabras en español y /clans con palabras en inglés. Los mensajes salen en el idioma del mod.
 */
public final class Commands {
    private static ClanStatsCore core;

    private Commands() {}

    private static LiteralArgumentBuilder<FabricClientCommandSource> lit(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> arg(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private static String str(CommandContext<FabricClientCommandSource> ctx, String name) {
        return StringArgumentType.getString(ctx, name);
    }

    /** Clan como argumento seguido de más argumentos: con espacios o símbolos va entre comillas. */
    private static final SuggestionProvider<FabricClientCommandSource> CLANS = (c, b) -> suggestClans(b, true);
    /** Clan como último argumento: se puede escribir con espacios sin comillas. */
    private static final SuggestionProvider<FabricClientCommandSource> CLANS_REST = (c, b) -> suggestClans(b, false);
    private static final SuggestionProvider<FabricClientCommandSource> COLORS = (c, b) -> suggest(Colors.commandNames(), b);
    private static final SuggestionProvider<FabricClientCommandSource> PLAYERS = (c, b) -> suggest(core.knownNames(), b);
    private static final SuggestionProvider<FabricClientCommandSource> MEMBERS = (c, b) -> suggest(allMembers(), b);
    private static final SuggestionProvider<FabricClientCommandSource> PLAYER_LIST = (c, b) -> suggestLastToken(core.knownNames(), b);

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, ClanStatsCore c) {
        core = c;
        dispatcher.register(tree(false));
        dispatcher.register(tree(true));
    }

    /** Árbol de comandos: {@code en} = /clans con palabras en inglés; si no, /clanes en español. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> tree(boolean en) {
        String aClan = "clan";
        String aColor = "color";
        String aNew = en ? "new_name" : "nuevo";
        String aPlayers = en ? "players" : "jugadores";
        String aPlayer = en ? "player" : "jugador";
        String aKiller = en ? "killer" : "asesino";
        String aVictim = en ? "victim" : "victima";
        String aTemplate = en ? "template" : "plantilla";
        String aNumber = en ? "number" : "numero";
        String aMessage = en ? "message" : "mensaje";
        Command<FabricClientCommandSource> help = ctx -> help(ctx, en);

        return lit(en ? "clans" : "clanes")
                .executes(help)
                .then(lit(en ? "help" : "ayuda").executes(help))
                .then(lit("menu").executes(Commands::menu))
                .then(lit(en ? "create" : "crear")
                        .then(arg(aClan, StringArgumentType.string())
                                .executes(ctx -> crear(ctx, str(ctx, aClan), null, en))
                                .then(arg(aColor, StringArgumentType.greedyString()).suggests(COLORS)
                                        .executes(ctx -> crear(ctx, str(ctx, aClan), str(ctx, aColor), en)))))
                .then(lit(en ? "delete" : "borrar")
                        .then(arg(aClan, StringArgumentType.greedyString()).suggests(CLANS_REST)
                                .executes(ctx -> borrar(ctx, str(ctx, aClan)))))
                .then(lit("color")
                        .then(arg(aClan, StringArgumentType.string()).suggests(CLANS)
                                .then(arg(aColor, StringArgumentType.greedyString()).suggests(COLORS)
                                        .executes(ctx -> color(ctx, str(ctx, aClan), str(ctx, aColor))))))
                .then(lit(en ? "rename" : "renombrar")
                        .then(arg(aClan, StringArgumentType.string()).suggests(CLANS)
                                .then(arg(aNew, StringArgumentType.greedyString())
                                        .executes(ctx -> renombrar(ctx, str(ctx, aClan), str(ctx, aNew))))))
                .then(lit(en ? "add" : "agregar")
                        .then(arg(aClan, StringArgumentType.string()).suggests(CLANS)
                                .then(arg(aPlayers, StringArgumentType.greedyString()).suggests(PLAYER_LIST)
                                        .executes(ctx -> agregar(ctx, str(ctx, aClan), str(ctx, aPlayers))))))
                .then(lit(en ? "remove" : "quitar")
                        .then(arg(aPlayer, StringArgumentType.word()).suggests(MEMBERS)
                                .executes(ctx -> quitar(ctx, str(ctx, aPlayer)))))
                .then(lit(en ? "list" : "lista").executes(Commands::lista))
                .then(lit("top")
                        .executes(ctx -> top(ctx, null))
                        .then(lit("kills").executes(ctx -> top(ctx, false)))
                        .then(lit("kd").executes(ctx -> top(ctx, true))))
                .then(lit("lobby")
                        .executes(ctx -> lobby(ctx, null))
                        .then(lit("kills").executes(ctx -> lobby(ctx, false)))
                        .then(lit("kd").executes(ctx -> lobby(ctx, true))))
                .then(lit("stats")
                        .then(arg(aPlayer, StringArgumentType.word()).suggests(PLAYERS)
                                .executes(ctx -> stats(ctx, str(ctx, aPlayer)))))
                .then(lit(en ? "copy" : "copiar")
                        .executes(ctx -> copiar(ctx, false))
                        .then(lit(en ? "full" : "completo").executes(ctx -> copiar(ctx, true))))
                .then(lit("uuid")
                        .then(arg(aPlayer, StringArgumentType.word()).suggests(PLAYERS)
                                .executes(ctx -> uuid(ctx, str(ctx, aPlayer)))))
                .then(lit("reset")
                        .executes(Commands::resetAsk)
                        .then(lit(en ? "confirm" : "confirmar").executes(Commands::resetDo)))
                .then(lit(en ? "undo" : "deshacer").executes(Commands::deshacer))
                .then(lit("kill")
                        .then(arg(aKiller, StringArgumentType.word()).suggests(PLAYERS)
                                .then(arg(aVictim, StringArgumentType.word()).suggests(PLAYERS)
                                        .executes(ctx -> killManual(ctx, str(ctx, aKiller), str(ctx, aVictim))))))
                .then(lit(en ? "death" : "muerte")
                        .then(arg(aVictim, StringArgumentType.word()).suggests(PLAYERS)
                                .executes(ctx -> muerteManual(ctx, str(ctx, aVictim)))))
                .then(hudTree(en))
                .then(lit(en ? "enable" : "activar").executes(ctx -> setActive(ctx, true, en)))
                .then(lit(en ? "disable" : "desactivar").executes(ctx -> setActive(ctx, false, en)))
                .then(lit(en ? "colors" : "colores")
                        .executes(ctx -> toggleInfo(ctx, "opt.colors", core.config.colorNombres))
                        .then(lit("on").executes(ctx -> setColors(ctx, true)))
                        .then(lit("off").executes(ctx -> setColors(ctx, false))))
                .then(lit(en ? "offline" : "desconectados")
                        .executes(ctx -> toggleInfo(ctx, "opt.offline", core.config.mostrarDesconectados))
                        .then(lit(en ? "show" : "ver").executes(ctx -> setOffline(ctx, true)))
                        .then(lit(en ? "hide" : "ocultar").executes(ctx -> setOffline(ctx, false))))
                .then(lit(en ? "language" : "idioma")
                        .executes(Commands::idiomaInfo)
                        .then(lit("auto").executes(ctx -> idioma(ctx, Texts.AUTO)))
                        .then(lit("es").executes(ctx -> idioma(ctx, Texts.ES)))
                        .then(lit("en").executes(ctx -> idioma(ctx, Texts.EN))))
                .then(lit("debug")
                        .executes(ctx -> toggleInfo(ctx, "opt.debug", core.config.debug))
                        .then(lit("on").executes(ctx -> setDebug(ctx, true)))
                        .then(lit("off").executes(ctx -> setDebug(ctx, false))))
                .then(lit(en ? "heuristic" : "heuristica")
                        .executes(ctx -> toggleInfo(ctx, "opt.heuristic", core.config.heuristica))
                        .then(lit("on").executes(ctx -> setHeuristic(ctx, true)))
                        .then(lit("off").executes(ctx -> setHeuristic(ctx, false))))
                .then(lit(en ? "friendlyfire" : "fuegoamigo")
                        .executes(ctx -> fuegoAmigoInfo(ctx, en))
                        .then(lit(en ? "separate" : "separar").executes(ctx -> setFuegoAmigo(ctx, "separar")))
                        .then(lit(en ? "count" : "contar").executes(ctx -> setFuegoAmigo(ctx, "contar"))))
                .then(lit(en ? "pattern" : "patron")
                        .executes(ctx -> patronLista(ctx, en))
                        .then(lit(en ? "list" : "lista").executes(ctx -> patronLista(ctx, en)))
                        .then(lit(en ? "add" : "agregar")
                                .then(arg(aTemplate, StringArgumentType.greedyString())
                                        .executes(ctx -> patronAgregar(ctx, str(ctx, aTemplate)))))
                        .then(lit(en ? "delete" : "borrar")
                                .then(arg(aNumber, IntegerArgumentType.integer(1))
                                        .executes(ctx -> patronBorrar(ctx, IntegerArgumentType.getInteger(ctx, aNumber))))))
                .then(lit(en ? "test" : "probar")
                        .then(arg(aMessage, StringArgumentType.greedyString())
                                .executes(ctx -> probar(ctx, str(ctx, aMessage)))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> hudTree(boolean en) {
        LiteralArgumentBuilder<FabricClientCommandSource> hud = lit("hud").executes(ctx -> hudInfo(ctx, en));
        String[][] modes = {{"off", "off", "OFF"}, {"clanes", "clans", "CLANES"}, {"lobby", "lobby", "LOBBY"}, {"ambos", "both", "AMBOS"}};
        for (String[] m : modes) {
            hud.then(lit(en ? m[1] : m[0]).executes(ctx -> hudMode(ctx, m[2])));
        }
        String aValue = en ? "value" : "valor";
        hud.then(lit("top").then(arg("n", IntegerArgumentType.integer(0, 30))
                .executes(ctx -> hudTop(ctx, false, IntegerArgumentType.getInteger(ctx, "n")))));
        hud.then(lit("toplobby").then(arg("n", IntegerArgumentType.integer(0, 30))
                .executes(ctx -> hudTop(ctx, true, IntegerArgumentType.getInteger(ctx, "n")))));
        hud.then(lit(en ? "scale" : "escala").then(arg(aValue, FloatArgumentType.floatArg(0.3f, 3.0f))
                .executes(ctx -> hudScale(ctx, FloatArgumentType.getFloat(ctx, aValue)))));
        hud.then(lit(en ? "position" : "posicion")
                .then(arg("x", IntegerArgumentType.integer(0, 4000))
                        .then(arg("y", IntegerArgumentType.integer(0, 4000))
                                .executes(ctx -> hudPos(ctx, IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"))))));
        hud.then(lit(en ? "side" : "lado")
                .then(lit(en ? "left" : "izquierda").executes(ctx -> hudSide(ctx, false)))
                .then(lit(en ? "right" : "derecha").executes(ctx -> hudSide(ctx, true))));
        hud.then(lit(en ? "order" : "orden")
                .then(lit("kills").executes(ctx -> hudOrder(ctx, "kills")))
                .then(lit("kd").executes(ctx -> hudOrder(ctx, "kd"))));
        hud.then(lit(en ? "background" : "fondo")
                .then(lit("on").executes(ctx -> hudBg(ctx, true)))
                .then(lit("off").executes(ctx -> hudBg(ctx, false))));
        return hud;
    }

    // ------------------------------------------------------------------ utilidades

    private static void send(CommandContext<FabricClientCommandSource> ctx, Component message) {
        ctx.getSource().sendFeedback(message);
    }

    private static String tr(String key, Object... args) {
        return Texts.tr(key, args);
    }

    private static CompletableFuture<Suggestions> suggest(Iterable<String> options, SuggestionsBuilder b) {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(o);
        }
        return b.buildFuture();
    }

    /** Nombres de clan; {@code quoted} = entre comillas si hace falta (cuando después vienen más argumentos). */
    private static CompletableFuture<Suggestions> suggestClans(SuggestionsBuilder b, boolean quoted) {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        String bare = rem.startsWith("\"") ? rem.substring(1) : rem;
        for (String name : clanNames()) {
            String shown = quoted ? quote(name) : name;
            if (name.toLowerCase(Locale.ROOT).startsWith(bare) || shown.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(shown);
        }
        return b.buildFuture();
    }

    /** Pone el nombre entre comillas si tiene algo que Brigadier no acepta sin comillas (espacios, tildes, símbolos). */
    static String quote(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean plain = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || c == '_' || c == '-' || c == '.' || c == '+';
            if (!plain) return "\"" + name + "\"";
        }
        return name;
    }

    /** Autocompleta el último nombre de una lista "Pepe Juan Ana...". */
    private static CompletableFuture<Suggestions> suggestLastToken(Iterable<String> options, SuggestionsBuilder b) {
        String rem = b.getRemaining();
        int cut = Math.max(rem.lastIndexOf(' '), rem.lastIndexOf(',')) + 1;
        Set<String> already = new HashSet<>();
        for (String tok : rem.substring(0, cut).split("[\\s,;]+")) already.add(tok.toLowerCase(Locale.ROOT));
        String part = rem.substring(cut).toLowerCase(Locale.ROOT);
        SuggestionsBuilder sub = b.createOffset(b.getStart() + cut);
        for (String o : options) {
            String low = o.toLowerCase(Locale.ROOT);
            if (low.startsWith(part) && !already.contains(low)) sub.suggest(o);
        }
        return sub.buildFuture();
    }

    private static List<String> clanNames() {
        List<String> out = new ArrayList<>();
        for (ClanConfig.Clan c : core.config.clanes) out.add(c.nombre);
        return out;
    }

    private static List<String> allMembers() {
        List<String> out = new ArrayList<>();
        for (ClanConfig.Clan c : core.config.clanes) out.addAll(c.memberNames());
        return out;
    }

    private static ClanConfig.Clan requireClan(CommandContext<FabricClientCommandSource> ctx, String typed) {
        String name = ClanConfig.cleanClanName(typed);
        ClanConfig.Clan clan = core.config.findClan(name);
        if (clan != null) return clan;
        // escribió solo la primera palabra de un clan con espacios (sin comillas)
        for (ClanConfig.Clan c : core.config.clanes) {
            if (c.nombre.toLowerCase(Locale.ROOT).startsWith(name.toLowerCase(Locale.ROOT) + " ")) {
                send(ctx, Chat.err(tr("cmd.no_clan_quotes", name, quote(c.nombre))));
                return null;
            }
        }
        send(ctx, Chat.err(tr("cmd.no_clan", name, quote(name))));
        return null;
    }

    // ------------------------------------------------------------------ ayuda

    private static int help(CommandContext<FabricClientCommandSource> ctx, boolean en) {
        send(ctx, Chat.prefix().append(Chat.tr(ChatFormatting.YELLOW, "help.title"))
                .append(Chat.t(" (" + tr(core.platform.hasMenu() ? "help.keys_menu" : "help.keys") + ")", ChatFormatting.GRAY)));
        String r = en ? "/clans " : "/clanes ";
        String[][] lines = {
                {r + "menu", "help.menu"},
                {r + (en ? "create <clan> [color]" : "crear <clan> [color]"), "help.create"},
                {r + (en ? "add <clan> <players...>" : "agregar <clan> <jugadores...>"), "help.add"},
                {r + (en ? "remove <player>" : "quitar <jugador>"), "help.remove"},
                {r + (en ? "delete | color | rename" : "borrar | color | renombrar"), "help.manage"},
                {r + (en ? "list" : "lista"), "help.list"},
                {r + "top [kills|kd]", "help.top"},
                {r + "lobby [kills|kd]", "help.lobby"},
                {r + (en ? "stats <player>" : "stats <jugador>"), "help.stats"},
                {r + (en ? "copy [full]" : "copiar [completo]"), "help.copy"},
                {r + (en ? "uuid <player>" : "uuid <jugador>"), "help.uuid"},
                {r + "reset", "help.reset"},
                {r + (en ? "undo | kill | death" : "deshacer | kill | muerte"), "help.fix"},
                {r + "hud ...", "help.hud"},
                {r + (en ? "language auto|es|en" : "idioma auto|es|en"), "help.language"},
                {r + (en ? "enable | disable" : "activar | desactivar"), "help.toggle"},
                {r + (en ? "colors on|off" : "colores on|off"), "help.colors"},
                {r + (en ? "offline show|hide" : "desconectados ver|ocultar"), "help.offline"},
                {r + (en ? "friendlyfire separate|count" : "fuegoamigo separar|contar"), "help.ff"},
                {r + "debug on|off", "help.debug"},
                {r + (en ? "test <message>" : "probar <mensaje>"), "help.test"},
                {r + (en ? "pattern add <template>" : "patron agregar <plantilla>"), "help.pattern"},
        };
        for (String[] l : lines) {
            send(ctx, Chat.t(" " + l[0], ChatFormatting.AQUA).append(Chat.t(" — " + tr(l[1]), ChatFormatting.GRAY)));
        }
        send(ctx, Chat.t(" " + tr("help.quotes", r + (en ? "create" : "crear")), ChatFormatting.DARK_GRAY));
        send(ctx, Chat.t(" " + tr(en ? "help.other_spanish" : "help.other_english"), ChatFormatting.DARK_GRAY));
        return 1;
    }

    // ------------------------------------------------------------------ clanes

    private static int crear(CommandContext<FabricClientCommandSource> ctx, String typed, String color, boolean en) {
        String name = ClanConfig.cleanClanName(typed);
        if (!ClanConfig.isValidClanName(name)) {
            send(ctx, Chat.err(tr("clan.invalid_name", ClanConfig.CLAN_NAME_MAX)));
            return 0;
        }
        if (core.config.findClan(name) != null) {
            send(ctx, Chat.err(tr("clan.exists", name)));
            return 0;
        }
        String col = color == null ? Colors.auto(core.config.clanes.size()) : Colors.parse(color);
        if (col == null) {
            send(ctx, Chat.err(tr("color.unknown", color.trim())));
            send(ctx, Chat.info(tr("cmd.create_quotes", (en ? "/clans create \"" : "/clanes crear \"") + name + " " + color.trim() + "\"")));
            return 0;
        }
        ClanConfig.Clan clan = new ClanConfig.Clan(name, col);
        core.config.clanes.add(clan);
        core.configChanged();
        send(ctx, Chat.prefix().append(Chat.fmt(ChatFormatting.GREEN, "cmd.created", Chat.clan(clan)))
                .append(Chat.t(" " + tr("cmd.created_next", quote(name)), ChatFormatting.GRAY)));
        return 1;
    }

    private static int borrar(CommandContext<FabricClientCommandSource> ctx, String typed) {
        ClanConfig.Clan clan = requireClan(ctx, typed);
        if (clan == null) return 0;
        core.config.clanes.remove(clan);
        core.configChanged();
        send(ctx, Chat.ok(tr("cmd.deleted", clan.nombre, clan.miembros.size())));
        return 1;
    }

    private static int color(CommandContext<FabricClientCommandSource> ctx, String typedClan, String typedColor) {
        ClanConfig.Clan clan = requireClan(ctx, typedClan);
        if (clan == null) return 0;
        String col = Colors.parse(typedColor);
        if (col == null) {
            send(ctx, Chat.err(tr("color.unknown", typedColor.trim())));
            return 0;
        }
        clan.color = col;
        core.configChanged();
        send(ctx, Chat.prefix().append(Chat.fmt(ChatFormatting.GREEN, "color.changed", Chat.clan(clan),
                Chat.colored(Colors.label(col), col))));
        return 1;
    }

    private static int renombrar(CommandContext<FabricClientCommandSource> ctx, String typedClan, String typedNew) {
        ClanConfig.Clan clan = requireClan(ctx, typedClan);
        if (clan == null) return 0;
        String nuevo = ClanConfig.cleanClanName(typedNew);
        if (!ClanConfig.isValidClanName(nuevo)) {
            send(ctx, Chat.err(tr("clan.invalid_name", ClanConfig.CLAN_NAME_MAX)));
            return 0;
        }
        ClanConfig.Clan other = core.config.findClan(nuevo);
        if (other != null && other != clan) {
            send(ctx, Chat.err(tr("clan.exists", nuevo)));
            return 0;
        }
        String old = clan.nombre;
        clan.nombre = nuevo;
        core.configChanged();
        send(ctx, Chat.ok(tr("cmd.renamed", old, nuevo)));
        return 1;
    }

    private static int agregar(CommandContext<FabricClientCommandSource> ctx, String typedClan, String players) {
        ClanConfig.Clan clan = requireClan(ctx, typedClan);
        if (clan == null) return 0;
        List<String> added = new ArrayList<>();
        List<String> moved = new ArrayList<>();
        List<String> already = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        List<String> searching = new ArrayList<>();
        for (String raw : players.split("[\\s,;]+")) {
            if (raw.isBlank()) continue;
            if (!Names.isValid(raw)) {
                invalid.add(raw);
                continue;
            }
            ClanStatsCore.AddReport r = core.addByName(clan, raw, Commands::reportLater);
            if (r == null) {
                searching.add(raw);
                continue;
            }
            switch (r.kind()) {
                case ADDED -> added.add(r.name());
                case MOVED -> moved.add(tr("cmd.add.was_in", r.name(), r.previous() == null ? "?" : r.previous().nombre));
                case ALREADY -> already.add(r.name());
                default -> searching.add(r.name());
            }
        }
        if (!added.isEmpty()) {
            send(ctx, Chat.prefix().append(Chat.fmt(ChatFormatting.GREEN, "cmd.add.added", Chat.clan(clan)))
                    .append(Chat.t(" " + String.join(", ", added), ChatFormatting.WHITE)));
        }
        if (!moved.isEmpty()) {
            send(ctx, Chat.prefix().append(Chat.fmt(ChatFormatting.YELLOW, "cmd.add.moved", Chat.clan(clan)))
                    .append(Chat.t(" " + String.join(", ", moved), ChatFormatting.WHITE)));
        }
        if (!already.isEmpty()) send(ctx, Chat.info(tr("cmd.add.already", String.join(", ", already))));
        if (!searching.isEmpty()) send(ctx, Chat.info(tr("cmd.add.searching", String.join(", ", searching))));
        if (!invalid.isEmpty()) send(ctx, Chat.err(tr("cmd.add.invalid", String.join(", ", invalid))));
        return added.size() + moved.size();
    }

    /** Llega cuando termina la búsqueda en Mojang de alguien agregado con /clanes agregar. */
    private static void reportLater(ClanStatsCore.AddReport r) {
        core.platform.chat(core.addReportMessage(r));
    }

    private static int quitar(CommandContext<FabricClientCommandSource> ctx, String name) {
        ClanConfig.Member member = core.config.findMember(name);
        ClanConfig.Clan clan = core.config.removeMember(member);
        if (clan == null) {
            send(ctx, Chat.err(tr("cmd.not_in_clan", name)));
            return 0;
        }
        core.configChanged();
        send(ctx, Chat.ok(tr("member.left", member.nombre, clan.nombre)));
        return 1;
    }

    private static int lista(CommandContext<FabricClientCommandSource> ctx) {
        if (core.config.clanes.isEmpty()) {
            send(ctx, Chat.info(tr("cmd.no_clans")));
            return 0;
        }
        send(ctx, Chat.prefix().append(Chat.tr(ChatFormatting.YELLOW, "cmd.list.title", core.config.clanes.size())));
        for (ClanConfig.Clan c : core.config.clanes) {
            int on = core.onlineCount(c);
            MutableComponent line = Chat.colored(" ■ ", c.color).append(Chat.clan(c))
                    .append(Chat.t(" (" + tr("count.connected", on, c.miembros.size() - on) + "): ", ChatFormatting.DARK_GRAY));
            if (c.miembros.isEmpty()) line.append(Chat.tr(ChatFormatting.DARK_GRAY, "cmd.list.empty"));
            for (int i = 0; i < c.miembros.size(); i++) {
                ClanConfig.Member m = c.miembros.get(i);
                if (i > 0) line.append(Chat.t(", ", ChatFormatting.DARK_GRAY));
                line.append(Chat.t(m.nombre, core.isOnline(m) ? ChatFormatting.WHITE : ChatFormatting.GRAY));
                if (m.uuid == null) line.append(Chat.t("?", ChatFormatting.YELLOW));
            }
            send(ctx, line);
        }
        send(ctx, Chat.tr(ChatFormatting.DARK_GRAY, "cmd.list.legend"));
        if (!core.config.membersWithoutUuid().isEmpty()) send(ctx, Chat.tr(ChatFormatting.DARK_GRAY, "cmd.list.no_uuid"));
        return 1;
    }

    // ------------------------------------------------------------------ estadísticas

    private static StatsEngine.Snapshot snapshotFor(Boolean byKd) {
        return byKd == null ? core.snapshot() : StatsEngine.compute(core.session, core.config, byKd);
    }

    private static String orderLabel(boolean byKd) {
        return byKd ? "KD" : "kills";
    }

    private static int top(CommandContext<FabricClientCommandSource> ctx, Boolean byKd) {
        StatsEngine.Snapshot s = snapshotFor(byKd);
        if (s.clans.isEmpty()) {
            send(ctx, Chat.info(tr("cmd.no_clans")));
            return 0;
        }
        send(ctx, Chat.prefix().append(Chat.tr(ChatFormatting.YELLOW, "cmd.top.title"))
                .append(Chat.t(" — " + tr("cmd.top.note", orderLabel(s.byKd)), ChatFormatting.GRAY)));
        boolean ffSeparate = !core.config.countFriendlyFire();
        for (StatsEngine.ClanStat cs : s.clans) {
            MutableComponent line = Chat.colored("■ ", cs.clan.color).append(Chat.clan(cs.clan))
                    .append(Chat.t("  ", ChatFormatting.GRAY)).append(Chat.kd(cs.kills, cs.deaths));
            if (ffSeparate && cs.teamKills > 0) line.append(Chat.t("  FF " + cs.teamKills, ChatFormatting.DARK_GRAY));
            send(ctx, line);
            int i = 1;
            for (StatsEngine.PlayerStat p : cs.members) {
                send(ctx, Chat.t("   " + i++ + ". ", ChatFormatting.DARK_GRAY).append(Chat.t(p.name + "  ", ChatFormatting.WHITE))
                        .append(Chat.kd(p.kills, p.deaths)));
            }
        }
        if (s.clans.size() >= 2) {
            MutableComponent h = Chat.t(tr("report.duels") + ": ", ChatFormatting.GRAY);
            boolean any = false;
            for (int a = 0; a < s.clans.size(); a++) {
                for (int b = a + 1; b < s.clans.size(); b++) {
                    ClanConfig.Clan x = s.clans.get(a).clan;
                    ClanConfig.Clan y = s.clans.get(b).clan;
                    int xy = s.headToHead(x, y);
                    int yx = s.headToHead(y, x);
                    if (xy + yx == 0) continue;
                    if (any) h.append(Chat.t(" · ", ChatFormatting.DARK_GRAY));
                    h.append(Chat.clan(x)).append(Chat.t(" " + xy + "-" + yx + " ", ChatFormatting.WHITE)).append(Chat.clan(y));
                    any = true;
                }
            }
            if (any) send(ctx, h);
        }
        return 1;
    }

    private static int lobby(CommandContext<FabricClientCommandSource> ctx, Boolean byKd) {
        StatsEngine.Snapshot s = snapshotFor(byKd);
        int kills = 0;
        for (StatsEngine.PlayerStat p : s.lobby) kills += p.kills;
        send(ctx, Chat.prefix().append(Chat.t("Lobby", ChatFormatting.AQUA))
                .append(Chat.t(" — " + tr("cmd.lobby.note", s.lobby.size(), kills, orderLabel(s.byKd)), ChatFormatting.GRAY)));
        if (s.lobby.isEmpty()) {
            send(ctx, Chat.info(tr("cmd.lobby.empty")));
            return 0;
        }
        int n = Math.min(15, s.lobby.size());
        for (int i = 0; i < n; i++) {
            StatsEngine.PlayerStat p = s.lobby.get(i);
            send(ctx, Chat.t(" " + (i + 1) + ". ", ChatFormatting.DARK_GRAY).append(Chat.player(p.name, core.config))
                    .append(Chat.t("  ", ChatFormatting.GRAY)).append(Chat.kd(p.kills, p.deaths)));
        }
        if (s.lobby.size() > n) send(ctx, Chat.info(tr("cmd.lobby.more", s.lobby.size() - n)));
        return 1;
    }

    private static int stats(CommandContext<FabricClientCommandSource> ctx, String name) {
        StatsEngine.Snapshot s = core.snapshot();
        StatsEngine.PlayerStat lob = s.lobbyByName(name);
        StatsEngine.PlayerStat cl = s.clanStatByName(name);
        ClanConfig.Clan clan = cl != null ? s.clanOf(cl) : core.config.clanOf(name);
        if (lob == null && clan == null) {
            send(ctx, Chat.info(tr("cmd.stats.none", name)));
            return 0;
        }
        MutableComponent head = Chat.prefix().append(Chat.player(lob != null ? lob.name : name, core.config));
        if (clan != null) head.append(Chat.t(" [", ChatFormatting.DARK_GRAY)).append(Chat.clan(clan)).append(Chat.t("]", ChatFormatting.DARK_GRAY));
        send(ctx, head);
        if (lob != null) {
            send(ctx, Chat.t(" Lobby: ", ChatFormatting.GRAY).append(Chat.kd(lob.kills, lob.deaths))
                    .append(Chat.t("  " + tr("cmd.stats.streak", lob.bestStreak), ChatFormatting.GRAY)));
        }
        if (cl != null) {
            MutableComponent line = Chat.t(" " + tr("cmd.stats.between") + ": ", ChatFormatting.GRAY).append(Chat.kd(cl.kills, cl.deaths));
            if (cl.teamKills > 0) line.append(Chat.t("  " + tr("report.friendly_fire", cl.teamKills), ChatFormatting.DARK_GRAY));
            send(ctx, line);
        }
        return 1;
    }

    private static int copiar(CommandContext<FabricClientCommandSource> ctx, boolean full) {
        String report = full ? core.summaryText() : core.discordText();
        boolean copied = core.platform.copyToClipboard(report);
        Path file = full ? core.storage.writeSummary(report) : core.storage.writeDiscordSummary(report);
        if (!copied) {
            send(ctx, Chat.info(tr("cmd.copy.failed")));
        } else if (full) {
            send(ctx, Chat.ok(tr("cmd.copy.full", report.length())
                    + (report.length() > 2000 ? " " + tr("cmd.copy.too_long") : "")));
        } else {
            send(ctx, Chat.ok(tr("cmd.copy.discord", report.length())));
        }
        send(ctx, Chat.prefix().append(core.platform.copyButton("[" + tr("cmd.copy.button") + "]", report))
                .append(Chat.t("  " + tr("cmd.copy.file", file.getFileName()), ChatFormatting.DARK_GRAY)));
        return 1;
    }

    private static int uuid(CommandContext<FabricClientCommandSource> ctx, String name) {
        ClanConfig.Member m = core.config.findMember(name);
        String uuid = m != null && m.uuid != null ? m.uuid : core.uuidFor(name);
        String shown = m != null ? m.nombre : core.canonicalName(name);
        if (uuid == null) {
            ClanStatsCore.LookupState st = core.lookupState(name);
            String why = st == ClanStatsCore.LookupState.PENDING ? "cmd.uuid.searching"
                    : st == ClanStatsCore.LookupState.NOT_FOUND ? "cmd.uuid.not_found" : "cmd.uuid.unknown_hint";
            send(ctx, Chat.info(tr("cmd.uuid.unknown", shown) + " (" + tr(why) + ")"));
            return 0;
        }
        ClanConfig.Clan clan = core.config.clanOf(m);
        MutableComponent line = Chat.prefix().append(Chat.player(shown, core.config))
                .append(Chat.t(" → " + uuid, ChatFormatting.WHITE));
        if (clan != null) line.append(Chat.t("  [", ChatFormatting.DARK_GRAY)).append(Chat.clan(clan)).append(Chat.t("]", ChatFormatting.DARK_GRAY));
        send(ctx, line);
        send(ctx, Chat.tr(ChatFormatting.GRAY, "cmd.uuid.note"));
        return 1;
    }

    private static int menu(CommandContext<FabricClientCommandSource> ctx) {
        if (!core.platform.hasMenu()) {
            send(ctx, Chat.info(tr("cmd.menu.unavailable")));
            return 0;
        }
        core.requestMenu();
        return 1;
    }

    private static int resetAsk(CommandContext<FabricClientCommandSource> ctx) {
        send(ctx, Chat.info(tr("cmd.reset.ask", core.session.eventos.size())));
        send(ctx, Chat.prefix().append(Chat.tr(ChatFormatting.YELLOW, "cmd.reset.confirm")));
        return 1;
    }

    private static int resetDo(CommandContext<FabricClientCommandSource> ctx) {
        Path archived = core.resetSession();
        send(ctx, Chat.ok(tr("cmd.reset.done")));
        if (archived != null) send(ctx, Chat.info(tr("cmd.reset.archived", archived.getFileName())));
        return 1;
    }

    private static int deshacer(CommandContext<FabricClientCommandSource> ctx) {
        Session.KillEvent e = core.undoLast();
        if (e == null) {
            send(ctx, Chat.info(tr("cmd.undo.nothing")));
            return 0;
        }
        send(ctx, Chat.ok(tr("cmd.undo.done", e.asesino == null ? tr("cmd.undo.death", e.victima) : e.asesino + " → " + e.victima)));
        return 1;
    }

    private static int killManual(CommandContext<FabricClientCommandSource> ctx, String killer, String victim) {
        if (!Names.isValid(killer) || !Names.isValid(victim) || killer.equalsIgnoreCase(victim)) {
            send(ctx, Chat.err(tr("cmd.invalid_names")));
            return 0;
        }
        core.record(core.canonicalName(victim), core.canonicalName(killer), KillParser.Source.MANUAL.label, null, false);
        send(ctx, Chat.ok(tr("cmd.kill.added", killer, victim)));
        return 1;
    }

    private static int muerteManual(CommandContext<FabricClientCommandSource> ctx, String victim) {
        if (!Names.isValid(victim)) {
            send(ctx, Chat.err(tr("cmd.invalid_name")));
            return 0;
        }
        core.record(core.canonicalName(victim), null, KillParser.Source.MANUAL.label, null, false);
        send(ctx, Chat.ok(tr("cmd.death.added", victim)));
        return 1;
    }

    // ------------------------------------------------------------------ HUD

    private static int hudInfo(CommandContext<FabricClientCommandSource> ctx, boolean en) {
        var h = core.config.hud;
        send(ctx, Chat.prefix().append(Chat.t("HUD: " + ClanStatsCore.modeLabel(h.modo), ChatFormatting.YELLOW))
                .append(Chat.t(" · " + tr("cmd.hud.info", h.topClan, h.topLobby, h.escala, h.x + "," + h.y,
                        tr(h.derecha ? "cmd.hud.right" : "cmd.hud.left"), orderLabel("kd".equals(h.orden)),
                        h.fondo ? "on" : "off"), ChatFormatting.GRAY)));
        send(ctx, Chat.t(en
                ? " /clans hud off|clans|lobby|both · top <n> · toplobby <n> · scale <0.3-3> · position <x> <y> · side left|right · order kills|kd · background on|off"
                : " /clanes hud off|clanes|lobby|ambos · top <n> · toplobby <n> · escala <0.3-3> · posicion <x> <y> · lado izquierda|derecha · orden kills|kd · fondo on|off",
                ChatFormatting.AQUA));
        return 1;
    }

    private static int hudMode(CommandContext<FabricClientCommandSource> ctx, String mode) {
        core.setHudMode(mode);
        send(ctx, Chat.ok("HUD: " + ClanStatsCore.modeLabel(mode)));
        return 1;
    }

    private static int hudTop(CommandContext<FabricClientCommandSource> ctx, boolean lobby, int n) {
        if (lobby) core.config.hud.topLobby = n;
        else core.config.hud.topClan = n;
        core.configChanged();
        send(ctx, Chat.ok(tr(lobby ? "cmd.hud.top_lobby" : "cmd.hud.top_clan", n)));
        return 1;
    }

    private static int hudScale(CommandContext<FabricClientCommandSource> ctx, float value) {
        core.config.hud.escala = value;
        core.configChanged();
        send(ctx, Chat.ok(tr("cmd.hud.scale", core.config.hud.escala)));
        return 1;
    }

    private static int hudPos(CommandContext<FabricClientCommandSource> ctx, int x, int y) {
        core.config.hud.x = x;
        core.config.hud.y = y;
        core.configChanged();
        send(ctx, Chat.ok(tr("cmd.hud.position", x, y)));
        return 1;
    }

    private static int hudSide(CommandContext<FabricClientCommandSource> ctx, boolean right) {
        core.config.hud.derecha = right;
        core.configChanged();
        send(ctx, Chat.ok(tr(right ? "cmd.hud.side_right" : "cmd.hud.side_left")));
        return 1;
    }

    private static int hudOrder(CommandContext<FabricClientCommandSource> ctx, String order) {
        core.config.hud.orden = order;
        core.configChanged();
        send(ctx, Chat.ok(tr("cmd.hud.order", orderLabel(order.equals("kd")))));
        return 1;
    }

    private static int hudBg(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        core.config.hud.fondo = on;
        core.configChanged();
        send(ctx, Chat.ok(tr("cmd.hud.background", on ? "on" : "off")));
        return 1;
    }

    // ------------------------------------------------------------------ opciones

    private static int idiomaInfo(CommandContext<FabricClientCommandSource> ctx) {
        send(ctx, Chat.info(tr("cmd.language.info", core.languageLabel())));
        return 1;
    }

    private static int idioma(CommandContext<FabricClientCommandSource> ctx, String setting) {
        core.setLanguageSetting(setting);
        send(ctx, Chat.ok(tr("cmd.language.set", core.languageLabel())));
        return 1;
    }

    private static int setActive(CommandContext<FabricClientCommandSource> ctx, boolean on, boolean en) {
        core.setActive(on);
        send(ctx, on ? Chat.ok(tr("cmd.enable.on")) : Chat.info(tr("cmd.enable.off", en ? "/clans enable" : "/clanes activar")));
        return 1;
    }

    private static int setColors(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        core.setNameColors(on);
        send(ctx, Chat.ok(tr(on ? "cmd.colors.on" : "cmd.colors.off")));
        return 1;
    }

    private static int setOffline(CommandContext<FabricClientCommandSource> ctx, boolean show) {
        core.setShowOffline(show);
        send(ctx, Chat.ok(tr(show ? "cmd.offline.show" : "cmd.offline.hide")));
        return 1;
    }

    private static int toggleInfo(CommandContext<FabricClientCommandSource> ctx, String labelKey, boolean value) {
        send(ctx, Chat.info(tr(labelKey) + ": " + (value ? "on" : "off")));
        return 1;
    }

    private static int setDebug(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        core.config.debug = on;
        core.configChanged();
        send(ctx, Chat.ok(tr(on ? "cmd.debug.on" : "cmd.debug.off")));
        return 1;
    }

    private static int setHeuristic(CommandContext<FabricClientCommandSource> ctx, boolean on) {
        core.config.heuristica = on;
        core.configChanged();
        send(ctx, Chat.ok(tr(on ? "cmd.heuristic.on" : "cmd.heuristic.off")));
        return 1;
    }

    private static int fuegoAmigoInfo(CommandContext<FabricClientCommandSource> ctx, boolean en) {
        boolean count = core.config.countFriendlyFire();
        String current = en ? (count ? "count" : "separate") : (count ? "contar" : "separar");
        send(ctx, Chat.info(tr(en ? "cmd.ff.info_en" : "cmd.ff.info_es", current)));
        return 1;
    }

    private static int setFuegoAmigo(CommandContext<FabricClientCommandSource> ctx, String mode) {
        core.config.fuegoAmigo = mode;
        core.configChanged();
        send(ctx, Chat.ok(tr(mode.equals("contar") ? "cmd.ff.count" : "cmd.ff.separate")));
        return 1;
    }

    private static int patronLista(CommandContext<FabricClientCommandSource> ctx, boolean en) {
        List<String> list = core.config.patrones;
        send(ctx, Chat.prefix().append(Chat.tr(ChatFormatting.YELLOW, "cmd.pattern.title", list.size()))
                .append(Chat.t(" " + tr("cmd.pattern.builtin", core.parser.builtInCount()), ChatFormatting.GRAY)));
        for (int i = 0; i < list.size(); i++) {
            send(ctx, Chat.t(" " + (i + 1) + ". ", ChatFormatting.DARK_GRAY).append(Chat.t(list.get(i), ChatFormatting.WHITE)));
        }
        send(ctx, Chat.t(" " + tr("cmd.pattern.example", en ? "/clans pattern add" : "/clanes patron agregar"), ChatFormatting.GRAY));
        return 1;
    }

    private static int patronAgregar(CommandContext<FabricClientCommandSource> ctx, String template) {
        String t = template.trim();
        try {
            KillParser.validateUserTemplate(t);
        } catch (IllegalArgumentException e) {
            send(ctx, Chat.err(tr("cmd.pattern.invalid", e.getMessage())));
            return 0;
        }
        core.config.patrones.add(t);
        core.configChanged();
        send(ctx, Chat.ok(tr("cmd.pattern.added", core.config.patrones.size(), t)));
        return 1;
    }

    private static int patronBorrar(CommandContext<FabricClientCommandSource> ctx, int n) {
        if (n > core.config.patrones.size()) {
            send(ctx, Chat.err(tr("cmd.pattern.missing", n)));
            return 0;
        }
        String removed = core.config.patrones.remove(n - 1);
        core.configChanged();
        send(ctx, Chat.ok(tr("cmd.pattern.deleted", removed)));
        return 1;
    }

    private static int probar(CommandContext<FabricClientCommandSource> ctx, String msg) {
        KillParser.Diagnosis d = core.parser.diagnose(msg, null, null, core.roster);
        if (d.chatLike) {
            send(ctx, Chat.info(tr("cmd.test.chat")));
        } else if (d.result == null) {
            send(ctx, Chat.err(tr("cmd.test.none", d.normalized)));
            send(ctx, Chat.info(tr("cmd.test.teach")));
        } else {
            KillParser.Result r = d.result;
            MutableComponent line = Chat.prefix().append(Chat.tr(ChatFormatting.GREEN, "cmd.test.detected")).append(Chat.t(" ", ChatFormatting.GRAY));
            if (r.killer != null) {
                line.append(Chat.fmt(ChatFormatting.GRAY, "cmd.test.kill", Chat.player(r.killer, core.config), Chat.player(r.victim, core.config)));
            } else {
                line.append(Chat.fmt(ChatFormatting.GRAY, "cmd.test.death", Chat.player(r.victim, core.config)));
            }
            line.append(Chat.t("  [" + r.source.label + ": " + r.templateId + "]", ChatFormatting.DARK_GRAY));
            send(ctx, line);
        }
        send(ctx, Chat.info(core.roster.available() ? tr("cmd.test.roster", core.roster.size()) : tr("cmd.test.no_roster")));
        return 1;
    }
}
