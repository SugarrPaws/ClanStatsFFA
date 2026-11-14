package com.clanstats.mod;

import com.clanstats.core.PlayerRef;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;

/** Lo que cambia entre versiones de Minecraft; cada versión lo implementa en su ClanStatsClient. */
public interface Platform {

    /** Mensaje de muerte vanilla (componente traducible "death.*") con sus argumentos ya como texto. */
    record DeathInfo(String key, List<String> args) {}

    /** Jugadores de la lista de Tab (nombre + UUID). Vacío si no hay conexión. */
    Collection<PlayerRef> onlinePlayers();

    /** Copia texto al portapapeles. false si no se pudo. */
    boolean copyToClipboard(String text);

    /** Datos del mensaje de muerte vanilla, o null si el mensaje no es de ese tipo. */
    DeathInfo deathInfo(Component message);

    /** Texto clicable que copia {@code textToCopy} (o texto normal si la versión no lo permite). */
    Component copyButton(String label, String textToCopy);

    /** Muestra un mensaje solo para ti en el chat (no se envía al servidor). */
    void chat(Component message);

    /** true si esta versión tiene el menú visual. */
    boolean hasMenu();

    /** Abre el menú visual. false si esta versión no lo tiene. */
    boolean openMenu();

    String minecraftVersion();

    /** Idioma del juego ("es_mx", "en_us"...). Se usa cuando el idioma del mod está en "auto". */
    default String gameLanguage() {
        return "";
    }
}
