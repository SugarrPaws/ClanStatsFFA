package com.clanstats.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Textos del mod en español e inglés (assets/clanstats/texts/es.json y en.json). El idioma lo elige el jugador
 * en el menú o con /clanes idioma; en "auto" sigue el idioma del juego (español si es cualquier variante de
 * español, si no inglés). Los textos usan {0}, {1}... para los datos.
 */
public final class Texts {
    public static final String ES = "es";
    public static final String EN = "en";
    /** Valores de la opción "idioma" de config.json. */
    public static final String AUTO = "auto";

    private static final Map<String, Map<String, String>> TABLES = new LinkedHashMap<>();
    private static volatile String language = ES;

    static {
        TABLES.put(ES, load(ES));
        TABLES.put(EN, load(EN));
    }

    private Texts() {}

    private static Map<String, String> load(String lang) {
        Map<String, String> out = new LinkedHashMap<>();
        String path = "/assets/clanstats/texts/" + lang + ".json";
        try (InputStream in = Texts.class.getResourceAsStream(path)) {
            if (in == null) return out;
            JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (root.isJsonObject()) flatten("", root.getAsJsonObject(), out);
        } catch (Exception e) {
            // sin el archivo se muestran las claves: feo, pero el mod sigue funcionando
        }
        return out;
    }

    /** Los objetos anidados se leen con punto: {"menu": {"add": ...}} es la clave menu.add. */
    private static void flatten(String prefix, JsonObject o, Map<String, String> out) {
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            String k = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            JsonElement v = e.getValue();
            if (v.isJsonObject()) flatten(k, v.getAsJsonObject(), out);
            else if (v.isJsonPrimitive()) out.put(k, v.getAsString());
        }
    }

    /** Idioma en uso: {@link #ES} o {@link #EN}. */
    public static String language() {
        return language;
    }

    public static boolean english() {
        return EN.equals(language);
    }

    /** Cambia el idioma en uso. Devuelve true si cambió. */
    public static boolean setLanguage(String lang) {
        String l = EN.equals(lang) ? EN : ES;
        if (l.equals(language)) return false;
        language = l;
        return true;
    }

    /** Idioma a usar según la opción guardada ("auto", "es" o "en") y el idioma del juego ("es_mx", "en_us"...). */
    public static String resolve(String setting, String gameLanguage) {
        if (ES.equals(setting) || EN.equals(setting)) return setting;
        String g = gameLanguage == null ? "" : gameLanguage.toLowerCase(Locale.ROOT);
        return g.startsWith("es") ? ES : EN;
    }

    /** Arregla el valor de la opción "idioma" leído de config.json. */
    public static String normalizeSetting(String setting) {
        if (setting == null) return AUTO;
        String s = setting.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case ES, "español", "espanol", "spanish" -> ES;
            case EN, "english", "inglés", "ingles" -> EN;
            default -> AUTO;
        };
    }

    /** Texto en el idioma en uso. */
    public static String tr(String key, Object... args) {
        return trIn(language, key, args);
    }

    /** Texto en un idioma concreto. Si falta, se usa el otro idioma y, como último recurso, la clave. */
    public static String trIn(String lang, String key, Object... args) {
        Map<String, String> table = TABLES.getOrDefault(lang, TABLES.get(ES));
        String s = table.get(key);
        if (s == null) s = TABLES.get(EN.equals(lang) ? ES : EN).get(key);
        if (s == null) s = key;
        return format(s, args);
    }

    static String format(String s, Object... args) {
        if (args == null || args.length == 0 || s.indexOf('{') < 0) return s;
        StringBuilder b = new StringBuilder(s.length() + 16);
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '{') {
                int end = s.indexOf('}', i);
                if (end > i + 1 && end - i <= 3) {
                    try {
                        int n = Integer.parseInt(s.substring(i + 1, end));
                        if (n >= 0 && n < args.length) {
                            b.append(args[n]);
                            i = end + 1;
                            continue;
                        }
                    } catch (NumberFormatException ignored) {
                        // no era un {n}: se copia tal cual
                    }
                }
            }
            b.append(c);
            i++;
        }
        return b.toString();
    }

    /** Claves de un idioma (para las pruebas). */
    public static Set<String> keys(String lang) {
        Map<String, String> t = TABLES.get(lang);
        return t == null ? Set.of() : Collections.unmodifiableSet(t.keySet());
    }

    public static boolean has(String key) {
        return TABLES.get(ES).containsKey(key) || TABLES.get(EN).containsKey(key);
    }
}
