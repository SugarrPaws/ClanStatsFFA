package com.clanstats.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Convierte un mensaje del chat en una kill (asesino → víctima) o una muerte sin asesino.
 *
 * Orden de detección:
 *  1. Mensajes de muerte "vanilla" (componente traducible death.*), independientes del idioma.
 *  2. Plantillas personalizadas del usuario (/clanes patron agregar ...).
 *  3. Las 87 kill messages conocidas de CubeCraft + las de Minecraft vanilla.
 *  4. Patrones generales en inglés y español ("X was ... by Y", "X fue ... por Y", "Y mató a X").
 *  5. Muertes sin asesino ("X died", "X fell into the void", "X murió").
 *  6. Heurística: dos jugadores conectados mencionados en un mensaje que no es chat.
 *
 * Los mensajes de chat de jugadores se ignoran para que nadie pueda "inventar" kills escribiendo.
 */
public final class KillParser {

    public enum Source {
        PLANTILLA("plantilla"), PERSONALIZADA("personalizada"), GENERAL("general"), SOLO("sin asesino"),
        VANILLA("vanilla"), HEURISTICA("heurística"), MANUAL("manual");

        public final String label;

        Source(String label) {
            this.label = label;
        }
    }

    public static final class Result {
        public final String victim;
        /** null = murió sin asesino (vacío, caída, etc.). */
        public final String killer;
        public final Source source;
        public final String templateId;

        public Result(String victim, String killer, Source source, String templateId) {
            this.victim = victim;
            this.killer = killer;
            this.source = source;
            this.templateId = templateId;
        }

        public boolean hasKiller() {
            return killer != null;
        }

        @Override
        public String toString() {
            return (killer == null ? "(nadie)" : killer) + " -> " + victim + " [" + source.label + ":" + templateId + "]";
        }
    }

    /** Resultado detallado para el comando /clanes probar. */
    public static final class Diagnosis {
        public final String cleaned;
        public final String normalized;
        public final boolean chatLike;
        public final Result result;

        Diagnosis(String cleaned, String normalized, boolean chatLike, Result result) {
            this.cleaned = cleaned;
            this.normalized = normalized;
            this.chatLike = chatLike;
            this.result = result;
        }
    }

    private enum Kind { CUSTOM, SPECIFIC, GENERIC, SOLO }

    private static final class Template {
        final String id;
        final Kind kind;
        final Pattern pattern;
        final boolean hasKiller;
        final String original;
        /** true = se compara con el texto con símbolos (plantillas sin palabras, p. ej. "{K} ☠ {V}"). */
        final boolean onCleaned;

        Template(String id, Kind kind, Pattern pattern, boolean hasKiller, String original, boolean onCleaned) {
            this.id = id;
            this.kind = kind;
            this.pattern = pattern;
            this.hasKiller = hasKiller;
            this.original = original;
            this.onCleaned = onCleaned;
        }
    }

    private static final String NB = "(?<![A-Za-z0-9_])";
    private static final String NA = "(?![A-Za-z0-9_])";
    private static final String V = NB + "(?<v>" + Names.NAME_RX + ")" + NA;
    private static final String K = NB + "(?<k>" + Names.NAME_RX + ")" + NA;
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final int UFLAGS = FLAGS | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern FORMAT_CODES = Pattern.compile("§[0-9a-fk-orxA-FK-ORX]");
    private static final Pattern TOKEN = Pattern.compile(NB + Names.NAME_RX + NA);
    private static final Pattern CHAT_ANGLE = Pattern.compile("^\\s*<[A-Za-z0-9_]{3,16}>\\s");
    private static final Pattern CHAT_SEP = Pattern.compile(NB + "([A-Za-z0-9_]{3,16})\\s*(:|»|>>|->|<-)(?:\\s|$)");

    private static final Pattern HEUR_EXCLUDE = Pattern.compile(
            "streak|racha|dominat|revenge|vengan|bounty|recompensa|party|friend|amig|invit|joined|se uni"
                    + "|left the|abandon|report|duel|queue|teleport|whisper|susurr|mensaje|message|gave you|te dio"
                    + "|te ha|muted|silenciad|banned|baneado|expulsad|kicked from|is now|ahora es|level|nivel"
                    + "|achievement|logro|coins|monedas|reward|premio",
            UFLAGS);
    private static final Pattern VICTIM_FIRST_HINT = Pattern.compile(
            "\\b(?:by|from|escaping|escape|fighting|against|dodging|while|whilst|watched|died|lost|fell|was|were|been"
                    + "|void|abyss|death|thanks"
                    + "|por|de|contra|escapando|huyendo|mientras|frente|murió|murio|cayó|cayo|perdió|perdio|fue|sido"
                    + "|gracias|ante|vacío|vacio|abismo|muerte|manos)\\b",
            UFLAGS);

    /** Plantillas incluidas. {V} = víctima, {K} = asesino; cada espacio admite uno o más espacios. */
    private static final String[][] BUILT_IN = {
            // ---- CubeCraft: cuerpo a cuerpo ----
            {"cc_slain", "S", "{V} was slain by {K}"},
            {"cc_owo", "S", "{K} OwO'?d {V}"},
            {"cc_uwu", "S", "{K} UwU'?d {V}"},
            {"cc_punchbag", "S", "{K} used {V} as a punching bag"},
            {"cc_murdered", "S", "{V} was murdered by {K}"},
            {"cc_flabbergasted", "S", "{K} flabbergasted {V}"},
            {"cc_bombarded", "S", "{V} was bombarded by {K}"},
            {"cc_facelift", "S", "{V} got a face\\s*lift by {K}"},
            {"cc_settled", "S", "{K} and {V} settled their differences"},
            {"cc_killed", "S", "{K} killed {V}"},
            {"cc_betrayed", "S", "{K} betrayed {V}"},
            {"cc_pummelled", "S", "{V} was pummell?ed by {K}"},
            {"cc_nextweek", "S", "{V} was (?:smacked|smashed) into (?:the )?next week by {K}"},
            {"cc_afterlife", "S", "{V} has been sent into the after\\s*life by {K}"},
            {"cc_gift", "S", "{K} bought {V} a gift"},
            {"cc_the_l", "S", "{K} gave {V} the L"},
            {"cc_deepsleep", "S", "{K} sent {V} into a deep sleep"},
            {"cc_disappear", "S", "{K} made {V} disappear"},
            {"cc_annihilated", "S", "{V} was annihilated by {K}"},
            {"cc_underworld", "S", "{K} sent {V} to the underworld"},
            {"cc_poked", "S", "{V} was poked to death by {K}"},
            {"cc_toohard", "S", "{K} went too hard on {V}"},
            {"cc_dust", "S", "{V} got turned into dust by {K}"},
            {"cc_rko", "S", "{V} was RKO'?d out of nowhere by {K}"},
            {"cc_dirtnap", "S", "{K} tucked {V} in for a dirt nap"},
            {"cc_deadlyhands", "S", "{K} had to register their hands as deadly weapons after what they did to {V}"},
            {"cc_medieval", "S", "{K} went medieval on {V}"},
            {"cc_snapped", "S", "{V} got snapped away by {K}"},
            {"cc_oblivion", "S", "{K} destroyed {V}"},
            {"cc_shadowrealm", "S", "{K} sent {V} to the shadow realm"},
            {"cc_slapdeath", "S", "{K} slapped {V} to death"},
            {"cc_showcool", "S", "{K} wanted to show {V} something cool"},
            {"cc_deepfreeze", "S", "{K} sent {V} into a deep freeze"},
            {"cc_wipedfloor", "S", "{K} wiped the floor with {V}"},
            {"cc_forced", "S", "{K} was forced to kill {V}"},
            {"cc_ghost", "S", "{K} turned {V} into a spooky ghost"},
            {"cc_showcased", "S", "{K} showcased their weapon to {V}"},
            {"cc_finished", "S", "{V} was finished by {K}"},
            {"cc_yeetweapon", "S", "{K} yeeted their weapon into {V}"},
            {"cc_brawled", "S", "{K} brawled {V} to death"},
            {"cc_ticked", "S", "{V} was ticked to death by {K}"},
            // ---- CubeCraft: a distancia ----
            {"cc_sniped", "S", "{V} was sniped by {K}"},
            {"cc_shot", "S", "{V} was shot by {K}"},
            {"cc_allprojectiles", "S", "{K} gave {V} all of their projectiles"},
            {"cc_katniss", "S", "{K} showed {V} their Katniss Everdeen impression"},
            {"cc_stuckwith", "S", "{V} was stuck with a projectile by {K}"},
            {"cc_stuckinto", "S", "{K} stuck their projectile into {V}"},
            {"cc_pewpew", "S", "{K} pew\\s*pew'?d {V}"},
            {"cc_impaled", "S", "{V} was impaled by {K}"},
            {"cc_heart", "S", "{V} was shot through the heart and {K} is to blame"},
            {"cc_dodging", "S", "{V} isn'?t the best at dodging {K}'?s projectile"},
            {"cc_targetpractice", "S", "{K} used {V} for target practice"},
            {"cc_snipedv", "S", "{K} sniped {V}"},
            {"cc_katniss2", "S", "{K} went all Katniss Everdeen on {V}"},
            {"cc_swiss", "S", "{K} turned {V} into Swiss cheese"},
            {"cc_yeetprojectile", "S", "{K} yeeted a projectile at {V}"},
            {"cc_bullseye", "S", "{V} just got BULLSEYED by {K}"},
            {"cc_slapprojectile", "S", "{V} was slapped by a projectile from {K}"},
            {"cc_epicshot", "S", "{K} got an epic shot on {V}"},
            // ---- CubeCraft: vacío ----
            {"cc_survivevoid", "S", "{V} thought they could survive (?:in|into) the void while escaping\\s*{K}"},
            {"cc_diedvoid", "S", "{V} died in the void while escaping\\s*{K}"},
            {"cc_blackhole", "S", "{V} was sucked into a black hole while {K} watched"},
            {"cc_voidtrauma", "S", "{K} assisted {V} with their void"},
            {"cc_kickvoid", "S", "{K} kicked {V} into the (?:void|abyss)"},
            {"cc_kraken", "S", "{V} was pulled into the void by {K}"},
            {"cc_trippedvoid", "S", "{K} tripped {V} into the void"},
            {"cc_upsidedown", "S", "{K} sent {V} into the Upside Down"},
            {"cc_ninja", "S", "{V} tried being a ninja against {K}"},
            {"cc_jumpvoid", "S", "{K} made {V} jump into the void"},
            {"cc_sparta", "S", "{K} screamed .{0,40}? before kicking {V}"},
            {"cc_sentvoid", "S", "{V} got sent into the void by {K}"},
            {"cc_blewvoid", "S", "{K} blew {V} into the void"},
            {"cc_slapvoid", "S", "{V} was slapped into the void by {K}"},
            {"cc_scaredvoid", "S", "{K} scared {V} into the void"},
            {"cc_scaredfight", "S", "{V} was too scared to fight {K}"},
            // ---- CubeCraft: explosiones ----
            {"cc_blownup", "S", "{V} was blown up by {K}"},
            {"cc_redmist", "S", "{V} was turned into a fire red mist by {K}"},
            {"cc_smithereens", "S", "{V} was blown into smithereens by {K}"},
            {"cc_grenade", "S", "{V} didn'?t notice the live grenade {K}"},
            {"cc_explosionout", "S", "{K} made an explosion out of {V}"},
            {"cc_bomberman", "S", "{K} showed {V} their bomberman impression"},
            {"cc_tookl", "S", "{V} took the L from {K}'?s explosion"},
            // ---- CubeCraft: caída ----
            {"cc_falldeath", "S", "{V} fell to their death while escaping\\s*{K}"},
            {"cc_cantfly", "S", "{V} couldn'?t fly while escaping\\s*{K}"},
            // ---- Minecraft vanilla (inglés) ----
            {"v_killedby", "S", "{V} was killed by {K}"},
            {"v_knockvoid", "S", "{V} was knocked (?:into|off) .{0,30}? by {K}"},
            {"v_doomed", "S", "{V} was doomed to fall by {K}"},
            {"v_fireballed", "S", "{V} was fireballed by {K}"},
            {"v_sameworld", "S", "{V} didn'?t want to live in the same world as {K}"},
            {"v_whilst", "S", "{V} .{1,60}? whilst (?:fighting|trying to escape) {K}"},
            {"v_tryingescape", "S", "{V} .{1,60}? trying to escape {K}"},
            // ---- Generales (exigen que ambos estén en la lista de jugadores, si hay lista) ----
            {"g_passive_en", "G", "{V} (?:was|got|has been|have been) (?:\\S+ ){0,7}?by {K}"},
            {"g_escape_en", "G", "{V} (?:\\S+ ){0,9}?(?:while|whilst) (?:fighting|escaping|trying to escape|running from) {K}"},
            {"g_escape2_en", "G", "{V} (?:\\S+ ){0,9}?(?:to escape|escaping) {K}"},
            {"g_active_en", "G", "{K} (?:killed|slew|eliminated|destroyed|murdered|annihilated|obliterated|defeated|wrecked|rekt|demolished|deleted|erased|ended) {V}"},
            {"g_passive_es", "G", "{V} (?:fue|ha sido|resultó|resulto) (?:\\S+ ){0,7}?(?:por|a manos de) {K}"},
            {"g_escape_es", "G", "{V} (?:\\S+ ){0,9}?(?:mientras (?:luchaba|peleaba|combatía|combatia) contra|(?:intentando|mientras intentaba|al intentar|tratando de) (?:escapar|huir) de|escapando de|huyendo de) {K}"},
            {"g_hands_es", "G", "{V} (?:\\S+ ){0,6}?a manos de {K}"},
            {"g_active_es", "G", "{K} (?:mató|mato|asesinó|asesino|eliminó|elimino|destruyó|destruyo|aniquiló|aniquilo|liquidó|liquido|venció|vencio|derrotó|derroto|reventó|revento|despachó|despacho) a {V}"},
            // ---- Muertes sin asesino (el nombre debe ir al inicio del mensaje) ----
            {"s_en", "O", "^(?:\\S+ ){0,2}?{V} (?:died|fell (?:into|out of|from|off)|fell to their death|hit the ground too hard"
                    + "|drowned|burned to death|burnt to a crisp|went up in flames|tried to swim in lava|blew up|was killed"
                    + "|suffocated|starved to death|was struck by lightning|withered away|froze to death|was squished"
                    + "|discovered the floor was lava|was too scared to fight|jumped into the void|left the confines of this world"
                    + "|was obliterated|experienced kinetic energy|was pricked to death|walked into (?:fire|a cactus))"},
            {"s_es", "O", "^(?:\\S+ ){0,2}?{V} (?:murió|murio|ha muerto|se cayó|se cayo|cayó|cayo|se ahogó|se ahogo|se quemó|se quemo"
                    + "|ardió|ardio|explotó|exploto|se suicidó|se suicido|fue asesinado|fue asesinada|fue eliminado|fue eliminada"
                    + "|se fue al vacío|se fue al vacio|saltó al vacío|salto al vacio|se murió|se murio)"},
    };

    private final List<Template> builtIn = new ArrayList<>();
    private final List<Template> custom = new ArrayList<>();
    private boolean heuristicEnabled = true;

    public KillParser() {
        for (String[] def : BUILT_IN) {
            Kind kind = switch (def[1]) {
                case "S" -> Kind.SPECIFIC;
                case "G" -> Kind.GENERIC;
                default -> Kind.SOLO;
            };
            String dsl = def[2];
            String rx = dsl.replace(" ", "\\s+").replace("{V}", V).replace("{K}", K);
            builtIn.add(new Template(def[0], kind, Pattern.compile(rx, FLAGS), dsl.contains("{K}"), dsl, false));
        }
    }

    public int builtInCount() {
        return builtIn.size();
    }

    public void setHeuristicEnabled(boolean enabled) {
        this.heuristicEnabled = enabled;
    }

    /**
     * Carga las plantillas del usuario. Formato: texto con {V} (víctima), {K} (asesino) y * (cualquier cosa).
     * Ejemplo: "{K} convirtió a {V} en polvo". Devuelve los errores (vacío si todo bien).
     */
    public List<String> setCustomTemplates(List<String> templates) {
        custom.clear();
        List<String> errors = new ArrayList<>();
        int i = 1;
        for (String t : templates) {
            try {
                custom.add(compileUserTemplate("p" + i, t));
            } catch (IllegalArgumentException e) {
                errors.add("#" + i + " (" + t + "): " + e.getMessage());
            }
            i++;
        }
        return errors;
    }

    /** Valida una plantilla del usuario sin guardarla. Lanza IllegalArgumentException con el motivo. */
    public static void validateUserTemplate(String template) {
        compileUserTemplate("test", template);
    }

    private static Template compileUserTemplate(String id, String dsl) {
        if (dsl == null || dsl.isBlank()) throw new IllegalArgumentException(Texts.tr("pattern.err.empty"));
        // Si la plantilla no tiene palabras (solo nombres y símbolos), se compara con el texto con símbolos.
        String literalOnly = dsl.replaceAll("(?i)\\{[VK]\\}", " ").replace("*", " ");
        boolean raw = mapChars(literalOnly).trim().isEmpty();
        StringBuilder rx = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        boolean hasV = false;
        boolean hasK = false;
        int i = 0;
        while (i < dsl.length()) {
            String rest = dsl.substring(i);
            String up = rest.length() >= 3 ? rest.substring(0, 3).toUpperCase(Locale.ROOT) : "";
            if (up.equals("{V}") || up.equals("{K}") || dsl.charAt(i) == '*') {
                appendLiteral(rx, literal.toString(), raw);
                literal.setLength(0);
                if (dsl.charAt(i) == '*') {
                    rx.append(".*?");
                    i++;
                } else if (up.equals("{V}")) {
                    rx.append(hasV ? "\\k<v>" : V);
                    hasV = true;
                    i += 3;
                } else {
                    rx.append(hasK ? "\\k<k>" : K);
                    hasK = true;
                    i += 3;
                }
            } else {
                literal.append(dsl.charAt(i));
                i++;
            }
        }
        appendLiteral(rx, literal.toString(), raw);
        if (!hasV) throw new IllegalArgumentException(Texts.tr("pattern.err.no_victim"));
        if (raw && !hasK) throw new IllegalArgumentException(Texts.tr("pattern.err.no_words"));
        try {
            return new Template(id, Kind.CUSTOM, Pattern.compile(rx.toString(), FLAGS), hasK, dsl, raw);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(Texts.tr("pattern.err.parse", e.getDescription()));
        }
    }

    private static void appendLiteral(StringBuilder rx, String literal, boolean raw) {
        if (literal.isEmpty()) return;
        if (raw) {
            // símbolos exactos, espacios flexibles
            StringBuilder chunk = new StringBuilder();
            for (int i = 0; i < literal.length(); i++) {
                char ch = literal.charAt(i);
                if (Character.isWhitespace(ch)) {
                    if (chunk.length() > 0) {
                        rx.append(Pattern.quote(chunk.toString()));
                        chunk.setLength(0);
                    }
                    rx.append("\\s*");
                } else {
                    chunk.append(ch);
                }
            }
            if (chunk.length() > 0) rx.append(Pattern.quote(chunk.toString()));
            return;
        }
        String mapped = mapChars(literal);
        String trimmed = mapped.trim();
        if (trimmed.isEmpty()) {
            rx.append("\\s*");
            return;
        }
        if (mapped.startsWith(" ")) rx.append("\\s*");
        String[] words = trimmed.split(" +");
        for (int w = 0; w < words.length; w++) {
            if (w > 0) rx.append("\\s+");
            rx.append(Pattern.quote(words[w]));
        }
        if (mapped.endsWith(" ")) rx.append("\\s*");
    }

    // ------------------------------------------------------------------ análisis

    public Result parse(String raw, String translationKey, List<String> translationArgs, NameOracle oracle) {
        return diagnose(raw, translationKey, translationArgs, oracle).result;
    }

    public Diagnosis diagnose(String raw, String translationKey, List<String> translationArgs, NameOracle oracle) {
        NameOracle o = oracle == null ? NameOracle.NONE : oracle;
        if (translationKey != null) {
            Result r = parseTranslatable(translationKey, translationArgs, o);
            if (r != null) return new Diagnosis(raw, raw, false, r);
        }
        if (raw == null) return new Diagnosis("", "", false, null);
        String cleaned = stripFormatting(raw).trim();
        if (cleaned.isEmpty() || cleaned.length() > 300) return new Diagnosis(cleaned, "", false, null);
        if (looksLikeChat(cleaned, o)) return new Diagnosis(cleaned, normalize(cleaned), true, null);
        String norm = normalize(cleaned);
        for (Template t : custom) {
            Result r = tryTemplate(t, t.onCleaned ? cleaned : norm, o);
            if (r != null) return new Diagnosis(cleaned, norm, false, r);
        }
        for (Template t : builtIn) {
            Result r = tryTemplate(t, norm, o);
            if (r != null) return new Diagnosis(cleaned, norm, false, r);
        }
        Result h = heuristicEnabled ? heuristic(norm, o) : null;
        return new Diagnosis(cleaned, norm, false, h);
    }

    Result parseTranslatable(String key, List<String> args, NameOracle o) {
        if (!key.startsWith("death.") || args == null || args.isEmpty()) return null;
        String victim = extractName(args.get(0), o);
        if (victim == null) return null;
        if (o.available() && !o.isKnown(victim)) return null;
        String killer = null;
        if (args.size() >= 2) {
            String cand = extractName(args.get(1), o);
            if (cand != null && !cand.equalsIgnoreCase(victim) && (!o.available() || o.isKnown(cand))) {
                killer = cand;
            }
        }
        return new Result(victim, killer, Source.VANILLA, key);
    }

    /** Saca un nombre de jugador de un argumento que puede traer prefijo de equipo, rango, etc. */
    private static String extractName(String arg, NameOracle o) {
        if (arg == null) return null;
        String s = stripFormatting(arg).trim();
        if (Names.isPlausible(s)) return s;
        Matcher m = TOKEN.matcher(s);
        String last = null;
        while (m.find()) {
            String tok = m.group();
            if (!Names.isPlausible(tok)) continue;
            if (o.available() && o.isKnown(tok)) return tok;
            last = tok;
        }
        return o.available() ? null : last;
    }

    private Result tryTemplate(Template t, String norm, NameOracle o) {
        Matcher m = t.pattern.matcher(norm);
        while (m.find()) {
            String v = m.group("v");
            String k = t.hasKiller ? m.group("k") : null;
            if (accept(t.kind, v, k, o)) {
                return new Result(v, k, sourceOf(t.kind), t.id);
            }
        }
        return null;
    }

    private static Source sourceOf(Kind kind) {
        return switch (kind) {
            case CUSTOM -> Source.PERSONALIZADA;
            case SPECIFIC -> Source.PLANTILLA;
            case GENERIC -> Source.GENERAL;
            case SOLO -> Source.SOLO;
        };
    }

    private static boolean accept(Kind kind, String v, String k, NameOracle o) {
        if (!Names.isPlausible(v)) return false;
        if (k != null && (!Names.isPlausible(k) || k.equalsIgnoreCase(v))) return false;
        if (!o.available()) return true;
        return switch (kind) {
            // Plantillas exactas de CubeCraft: basta con reconocer a uno (por si el servidor oculta gente del Tab).
            case SPECIFIC -> o.isKnown(v) || (k != null && o.isKnown(k));
            case CUSTOM, GENERIC -> o.isKnown(v) && (k == null || o.isKnown(k));
            case SOLO -> o.isKnown(v);
        };
    }

    private Result heuristic(String norm, NameOracle o) {
        if (!o.available() || norm.length() > 160) return null;
        if (HEUR_EXCLUDE.matcher(norm).find()) return null;
        Map<String, int[]> found = new LinkedHashMap<>();
        Map<String, String> display = new LinkedHashMap<>();
        Matcher m = TOKEN.matcher(norm);
        while (m.find()) {
            String tok = m.group();
            if (!Names.isPlausible(tok) || !o.isKnown(tok)) continue;
            String key = Names.key(tok);
            if (!found.containsKey(key)) {
                found.put(key, new int[]{m.start(), m.end()});
                display.put(key, tok);
            }
        }
        if (found.size() != 2) return null;
        List<String> keys = new ArrayList<>(found.keySet());
        int[] a = found.get(keys.get(0));
        int[] b = found.get(keys.get(1));
        String first = display.get(keys.get(0));
        String second = display.get(keys.get(1));
        String between = norm.substring(a[1], b[0]);
        boolean victimFirst = VICTIM_FIRST_HINT.matcher(between).find();
        return victimFirst
                ? new Result(first, second, Source.HEURISTICA, "victima_primero")
                : new Result(second, first, Source.HEURISTICA, "asesino_primero");
    }

    // ------------------------------------------------------------------ utilidades de texto

    public static String stripFormatting(String s) {
        return s == null ? "" : FORMAT_CODES.matcher(s).replaceAll("");
    }

    /** Quita emojis/símbolos (✺ ☠ » ♥ ...) dejando letras, números, _ y puntuación básica. */
    public static String normalize(String s) {
        String mapped = mapChars(s);
        return mapped.trim().replaceAll(" {2,}", " ");
    }

    private static String mapChars(String s) {
        StringBuilder b = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '’' || cp == '‘' || cp == '`' || cp == '´') cp = '\'';
            if (Character.isLetterOrDigit(cp) || cp == '_' || cp == '\'' || ".,!?:\"-".indexOf(cp) >= 0) {
                b.appendCodePoint(cp);
            } else {
                b.append(' ');
            }
        }
        return b.toString();
    }

    /** Detecta mensajes de chat de jugadores ("<Pepe> hola", "[VIP] Pepe: hola", "Pepe » hola"). */
    static boolean looksLikeChat(String cleaned, NameOracle o) {
        if (CHAT_ANGLE.matcher(cleaned).find()) return true;
        Matcher m = CHAT_SEP.matcher(cleaned);
        while (m.find() && m.start() <= 48) {
            String name = m.group(1);
            String sep = m.group(2);
            if (o.available()) {
                if (o.isKnown(name)) return true;
            } else if (sep.equals(":")) {
                return true;
            }
        }
        return false;
    }

    public List<String> customTemplateList() {
        List<String> out = new ArrayList<>();
        for (Template t : custom) out.add(t.original);
        return Collections.unmodifiableList(out);
    }
}
