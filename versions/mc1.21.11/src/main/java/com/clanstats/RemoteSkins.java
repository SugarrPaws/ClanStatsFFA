package com.clanstats;

import com.clanstats.mod.ClanStatsCore;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.PlayerSkin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Skin de jugadores que no están en la sala, descargada de Mojang con su UUID (como las cabezas de jugador del
 * juego). Usa la caché de skins de Minecraft (PlayerSkinRenderCache, desde 1.21.9): mientras se descarga devuelve
 * la skin por defecto y después la real, sin trabar el juego.
 *
 * <p>Se busca por la forma de los métodos y no por su nombre (en el juego los nombres están ofuscados): un método
 * de Minecraft que devuelve la caché, uno de la caché que recibe un perfil y devuelve la info de render, y en esa
 * info el método que devuelve la skin. Si algo no coincide, se deja de intentar y se usan las skins por defecto.
 */
final class RemoteSkins {
    private static boolean ready;
    private static boolean broken;
    private static Object cache;
    private static Method getOrDefault;
    private static Method playerSkin;
    private static Method profileByUuid;
    private static Method profileByName;
    private static final Map<String, Object> PROFILES = new HashMap<>();

    private RemoteSkins() {}

    /** Skin del jugador (la real cuando ya se descargó), o null si no se puede. */
    static PlayerSkin get(String uuid, String name) {
        if (broken || (uuid == null && name == null)) return null;
        try {
            if (!ready) init();
            String key = uuid != null ? uuid : "n:" + name.toLowerCase(Locale.ROOT);
            Object profile = PROFILES.get(key);
            if (profile == null) {
                if (uuid != null && profileByUuid != null) profile = profileByUuid.invoke(null, UUID.fromString(uuid));
                else if (name != null && profileByName != null) profile = profileByName.invoke(null, name);
                if (profile == null) return null;
                PROFILES.put(key, profile);
            }
            Object info = getOrDefault.invoke(cache, profile);
            Object skin = info == null ? null : playerSkin.invoke(info);
            return skin instanceof PlayerSkin s ? s : null;
        } catch (Throwable t) {
            broken = true;
            ClanStatsCore.LOG.warn("ClanStats: no se pueden cargar las cabezas de jugadores fuera de la sala ({}); se usan las de por defecto", t.toString());
            return null;
        }
    }

    private static void init() throws ReflectiveOperationException {
        Minecraft mc = Minecraft.getInstance();
        for (Method m : Minecraft.class.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 0) continue;
            Class<?> type = m.getReturnType();
            if (type.isPrimitive() || type.isArray() || type.getName().startsWith("java.")) continue;
            try {
                if (tryCache(mc, m, type)) return;
            } catch (LinkageError | RuntimeException ignored) {
                // una clase que no se pudo cargar (de otro mod, por ejemplo): se sigue con la próxima
            }
        }
        throw new NoSuchMethodException("PlayerSkinRenderCache");
    }

    private static boolean tryCache(Minecraft mc, Method getter, Class<?> type) throws ReflectiveOperationException {
        for (Method g : type.getMethods()) {
            if (Modifier.isStatic(g.getModifiers()) || g.getParameterCount() != 1) continue;
            Method skin = noArgReturning(g.getReturnType(), PlayerSkin.class);
            if (skin == null) continue;
            Class<?> profileType = g.getParameterTypes()[0];
            Method byUuid = factory(profileType, UUID.class);
            Method byName = factory(profileType, String.class);
            if (byUuid == null && byName == null) continue;
            Object c = getter.invoke(mc);
            if (c == null) continue;
            cache = c;
            getOrDefault = g;
            playerSkin = skin;
            profileByUuid = byUuid;
            profileByName = byName;
            ready = true;
            return true;
        }
        return false;
    }

    private static Method noArgReturning(Class<?> owner, Class<?> type) {
        if (owner.isPrimitive() || owner.isArray() || owner == type) return null;
        try {
            for (Method m : owner.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 && m.getReturnType() == type) return m;
            }
        } catch (LinkageError | RuntimeException ignored) {
            // clase que no se puede revisar: no es la que buscamos
        }
        return null;
    }

    /** Método estático de {@code type} que crea un perfil a partir de un solo dato (UUID o nombre). */
    private static Method factory(Class<?> type, Class<?> param) {
        if (type.isPrimitive() || type.getName().startsWith("java.")) return null;
        try {
            for (Method m : type.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == param
                        && type.isAssignableFrom(m.getReturnType())) {
                    return m;
                }
            }
        } catch (LinkageError | RuntimeException ignored) {
            // clase que no se puede revisar: no es la que buscamos
        }
        return null;
    }
}
