package com.clanstats.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Colores de los clanes: una paleta con nombre en español e inglés, o cualquier color escrito en hexadecimal
 * ("#FF8800"). En config.json se guarda la clave de la paleta (en español, como siempre) o el código "#RRGGBB".
 */
public final class Colors {
    /** Un color de la paleta. {@code key} es el nombre guardado en config.json. */
    public record Named(String key, String es, String en, int rgb) {
        public String label(String lang) {
            return Texts.EN.equals(lang) ? en : es;
        }

        /** Nombre para escribir en un comando (sin espacios). */
        public String commandName(String lang) {
            return Texts.EN.equals(lang) ? simplify(en).replace(' ', '_') : key;
        }
    }

    private static final Pattern HEX = Pattern.compile("(?:#|0x)?([0-9a-f]{6}|[0-9a-f]{3})");
    private static final List<Named> PALETTE = new ArrayList<>();
    private static final Map<String, Named> BY_KEY = new HashMap<>();
    private static final Map<String, Named> BY_NAME = new HashMap<>();
    private static final String[] AUTO = {"rojo", "azul", "verde", "amarillo", "morado", "aqua", "naranja", "rosa",
            "lima", "celeste", "coral", "violeta", "turquesa", "dorado", "magenta", "blanco"};

    static {
        // 4 filas de 8 en el menú: rojos y amarillos, verdes, azules y violetas, rosas y neutros
        add("rojo", "Rojo", "Red", 0xFF5555);
        add("rojo_oscuro", "Rojo oscuro", "Dark red", 0xAA0000);
        add("carmesi", "Carmesí", "Crimson", 0xDC143C);
        add("coral", "Coral", "Coral", 0xFF7F50);
        add("naranja", "Naranja", "Orange", 0xFF8800);
        add("dorado", "Dorado", "Gold", 0xFFAA00);
        add("amarillo", "Amarillo", "Yellow", 0xFFFF55);
        add("lima", "Lima", "Lime", 0xAAFF00);
        add("verde", "Verde", "Green", 0x55FF55);
        add("verde_oscuro", "Verde oscuro", "Dark green", 0x00AA00);
        add("esmeralda", "Esmeralda", "Emerald", 0x2ECC71);
        add("oliva", "Oliva", "Olive", 0x808000);
        add("menta", "Menta", "Mint", 0x98FFCC);
        add("turquesa", "Turquesa", "Turquoise", 0x40E0D0);
        add("aqua", "Aqua", "Aqua", 0x55FFFF);
        add("cian", "Cian", "Cyan", 0x00AAAA);
        add("celeste", "Celeste", "Sky blue", 0x87CEEB);
        add("azul", "Azul", "Blue", 0x5555FF);
        add("azul_rey", "Azul rey", "Royal blue", 0x4169E1);
        add("azul_oscuro", "Azul oscuro", "Dark blue", 0x0000AA);
        add("lavanda", "Lavanda", "Lavender", 0xB57EDC);
        add("violeta", "Violeta", "Violet", 0x8A2BE2);
        add("morado", "Morado", "Purple", 0xAA00AA);
        add("magenta", "Magenta", "Magenta", 0xFF00FF);
        add("rosa", "Rosa", "Pink", 0xFF55FF);
        add("rosa_claro", "Rosa claro", "Light pink", 0xFFB6C1);
        add("marron", "Marrón", "Brown", 0xA0522D);
        add("beige", "Beige", "Beige", 0xD2B48C);
        add("blanco", "Blanco", "White", 0xFFFFFF);
        add("gris", "Gris", "Gray", 0xAAAAAA);
        add("gris_oscuro", "Gris oscuro", "Dark gray", 0x555555);
        add("negro", "Negro", "Black", 0x000000);
        // otras formas de escribir lo mismo
        alias("grey", "gris");
        alias("dark grey", "gris_oscuro");
        alias("light gray", "gris");
        alias("light grey", "gris");
        alias("cyan", "cian");
        alias("teal", "cian");
        alias("dark aqua", "cian");
        alias("light purple", "rosa");
        alias("dark purple", "morado");
        alias("purpura", "morado");
        alias("lila", "lavanda");
        alias("cafe", "marron");
        alias("fucsia", "magenta");
        alias("fuchsia", "magenta");
        alias("anaranjado", "naranja");
        alias("oro", "dorado");
    }

    private Colors() {}

    private static void add(String key, String es, String en, int rgb) {
        Named n = new Named(key, es, en, rgb);
        PALETTE.add(n);
        BY_KEY.put(key, n);
        BY_NAME.put(simplify(key), n);
        BY_NAME.put(simplify(es), n);
        BY_NAME.put(simplify(en), n);
    }

    private static void alias(String name, String key) {
        BY_NAME.put(simplify(name), BY_KEY.get(key));
    }

    /** Minúsculas, sin tildes y con espacios en lugar de "_" o "-". */
    static String simplify(String s) {
        String n = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ");
    }

    public static List<Named> palette() {
        return List.copyOf(PALETTE);
    }

    /**
     * Interpreta lo que escribió el jugador: un nombre de la paleta (en español o inglés, con o sin tildes) o un
     * código hexadecimal ("#FF8800", "ff8800", "#f80"). Devuelve el valor a guardar, o null si no es un color.
     */
    public static String parse(String input) {
        if (input == null || input.isBlank()) return null;
        String s = simplify(input);
        Named n = BY_NAME.get(s);
        if (n != null) return n.key();
        var m = HEX.matcher(s.replace(" ", ""));
        if (!m.matches()) return null;
        String h = m.group(1);
        if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        return "#" + h.toUpperCase(Locale.ROOT);
    }

    /** true si es un valor guardado válido (clave de la paleta o "#RRGGBB"). */
    public static boolean isValid(String stored) {
        if (stored == null) return false;
        if (BY_KEY.containsKey(stored)) return true;
        return stored.length() == 7 && stored.charAt(0) == '#' && parse(stored) != null;
    }

    /** Arregla lo leído de config.json: claves viejas, mayúsculas o códigos sin "#". Si no se entiende, blanco. */
    public static String normalize(String stored) {
        if (stored != null && BY_KEY.containsKey(stored)) return stored;
        String p = parse(stored);
        return p != null ? p : "blanco";
    }

    /** Color RGB (sin alfa) de un valor guardado. Si no se entiende, blanco. */
    public static int rgb(String stored) {
        if (stored == null) return 0xFFFFFF;
        Named n = BY_KEY.get(stored);
        if (n != null) return n.rgb();
        String p = parse(stored);
        if (p != null && p.startsWith("#")) return Integer.parseInt(p.substring(1), 16);
        n = p == null ? null : BY_KEY.get(p);
        return n != null ? n.rgb() : 0xFFFFFF;
    }

    /** Igual que {@link #rgb} pero con alfa opaco, para dibujar (desde 1.21.6 el color sin alfa no se ve). */
    public static int argb(String stored) {
        return 0xFF000000 | rgb(stored);
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** Color de la paleta guardado con esa clave, o null si es un código hexadecimal. */
    public static Named named(String stored) {
        return stored == null ? null : BY_KEY.get(stored);
    }

    /** Nombre para mostrar en el idioma del mod: "Rojo oscuro", "Dark red" o "#FF8800". */
    public static String label(String stored) {
        Named n = named(stored);
        return n != null ? n.label(Texts.language()) : hex(rgb(stored));
    }

    /** Nombres para autocompletar en los comandos (en el idioma del mod). */
    public static List<String> commandNames() {
        List<String> out = new ArrayList<>();
        for (Named n : PALETTE) out.add(n.commandName(Texts.language()));
        return out;
    }

    /** Color automático para el clan número {@code index} (0, 1, 2...). */
    public static String auto(int index) {
        return AUTO[Math.floorMod(index, AUTO.length)];
    }
}
