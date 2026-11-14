package com.clanstats.core;

/**
 * Un jugador de la lista de Tab: nombre actual y UUID de la cuenta. El UUID queda en null si no es de una
 * cuenta real de Minecraft (versión 4): así no se confunden NPCs ni jugadores "no premium".
 */
public record PlayerRef(String name, String uuid) {
    public PlayerRef {
        uuid = Uuids.isAccount(uuid) ? Uuids.normalize(uuid) : null;
    }
}
