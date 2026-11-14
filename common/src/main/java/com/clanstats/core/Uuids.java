package com.clanstats.core;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** UUIDs de cuentas de Minecraft, siempre guardados como texto en minúsculas y con guiones. */
public final class Uuids {
    private static final Pattern DASHED = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern PLAIN = Pattern.compile("[0-9a-fA-F]{32}");

    private Uuids() {}

    /** Acepta un {@link UUID} o texto con o sin guiones. Devuelve null si no es un UUID válido. */
    public static String normalize(Object value) {
        if (value == null) return null;
        if (value instanceof UUID u) return u.toString();
        String s = value.toString().trim();
        if (DASHED.matcher(s).matches()) return s.toLowerCase(Locale.ROOT);
        if (PLAIN.matcher(s).matches()) {
            s = s.toLowerCase(Locale.ROOT);
            return s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-"
                    + s.substring(16, 20) + "-" + s.substring(20);
        }
        return null;
    }

    /** true si es el UUID de una cuenta real (las de Mojang/Microsoft son versión 4). */
    public static boolean isAccount(Object value) {
        String n = normalize(value);
        return n != null && n.charAt(14) == '4';
    }

    public static UUID parse(String value) {
        String n = normalize(value);
        return n == null ? null : UUID.fromString(n);
    }
}
