package com.clanstats.core;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Utilidades para nombres de jugador de Minecraft Java (3-16 caracteres, letras/números/_). */
public final class Names {
    public static final String NAME_RX = "[A-Za-z0-9_]{3,16}";
    private static final Pattern VALID = Pattern.compile(NAME_RX);

    /** Palabras que pueden caer en el hueco de un nombre pero nunca son un jugador real del mensaje. */
    private static final Set<String> BLACKLIST = Set.of(
            "you", "your", "yourself", "the", "and", "was", "were", "has", "have", "had", "his", "her",
            "hers", "their", "they", "them", "with", "from", "into", "for", "who", "him", "she", "its",
            "someone", "something", "player", "players", "everyone", "everybody", "nobody", "all",
            "por", "fue", "con", "los", "las", "una", "uno", "que", "del", "jugador", "alguien",
            "todos", "nadie", "usted", "ustedes");

    private Names() {}

    public static boolean isValid(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    public static boolean isBlacklisted(String name) {
        return name == null || BLACKLIST.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Nombre con formato válido y que no es una palabra común. */
    public static boolean isPlausible(String name) {
        return isValid(name) && !isBlacklisted(name);
    }

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
