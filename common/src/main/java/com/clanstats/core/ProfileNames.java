package com.clanstats.core;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lee el nombre y el UUID de un GameProfile (authlib) sin depender de su versión:
 * en 1.21.9+ es un record con name() e id(); en versiones viejas eran getName() y getId().
 * authlib no está ofuscado, así que la reflexión funciona en cualquier versión.
 */
public final class ProfileNames {
    private static final Map<Class<?>, Method> NAMES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> IDS = new ConcurrentHashMap<>();

    private ProfileNames() {}

    public static String nameOf(Object profile) {
        Object v = invoke(profile, NAMES, String.class, "name", "getName");
        return v instanceof String s ? s : null;
    }

    /** UUID del perfil como texto normalizado (id() en 1.21.9+, getId() antes), o null. */
    public static String idOf(Object profile) {
        return Uuids.normalize(invoke(profile, IDS, UUID.class, "id", "getId"));
    }

    private static Object invoke(Object profile, Map<Class<?>, Method> cache, Class<?> type, String... names) {
        if (profile == null) return null;
        try {
            Method m = cache.computeIfAbsent(profile.getClass(), c -> find(c, type, names));
            return m == null ? null : m.invoke(profile);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Method find(Class<?> c, Class<?> type, String... names) {
        for (String n : names) {
            try {
                Method m = c.getMethod(n);
                if (m.getReturnType() == type) {
                    m.trySetAccessible();
                    return m;
                }
            } catch (NoSuchMethodException | SecurityException ignored) {
                // probar el siguiente
            }
        }
        return null;
    }
}
