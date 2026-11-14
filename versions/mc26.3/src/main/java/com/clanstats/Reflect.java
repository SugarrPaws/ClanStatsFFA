package com.clanstats;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Desde 26.1 Minecraft ya no está ofuscado, así que los nombres oficiales existen en tiempo de ejecución.
 * Para las partes que Mojang reorganiza seguido (HUD, ventana, portapapeles, lista de jugadores) usamos
 * reflexión: si algo cambia en otra versión 26.x, el mod sigue funcionando y solo pierde ese detalle.
 */
final class Reflect {
    private static final Map<String, Optional<Field>> FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    private Reflect() {}

    static Object field(Object target, String name) {
        if (target == null) return null;
        Optional<Field> f = FIELDS.computeIfAbsent(target.getClass().getName() + "#" + name, k -> findField(target.getClass(), name));
        if (f.isEmpty()) return null;
        try {
            return f.get().get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static Object call(Object target, String name, Object... args) {
        if (target == null) return null;
        Optional<Method> m = METHODS.computeIfAbsent(target.getClass().getName() + "#" + name + "/" + args.length,
                k -> findMethod(target.getClass(), name, args.length));
        if (m.isEmpty()) return null;
        try {
            return m.get().invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Optional<Field> findField(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.trySetAccessible();
                return Optional.of(f);
            } catch (NoSuchFieldException | SecurityException ignored) {
                // seguir con la superclase
            }
        }
        return Optional.empty();
    }

    private static Optional<Method> findMethod(Class<?> c, String name, int argCount) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == argCount) {
                m.trySetAccessible();
                return Optional.of(m);
            }
        }
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == argCount) {
                    m.trySetAccessible();
                    return Optional.of(m);
                }
            }
        }
        return Optional.empty();
    }

    /** F1: 26.2+ usa Gui.hud.isHidden(); antes era options.hideGui. */
    static boolean hudHidden() {
        Minecraft mc = Minecraft.getInstance();
        Object hud = field(field(mc, "gui"), "hud");
        if (call(hud, "isHidden") instanceof Boolean b) return b;
        return field(field(mc, "options"), "hideGui") instanceof Boolean b && b;
    }

    static int guiScaledWidth(Object graphics) {
        if (call(graphics, "guiWidth") instanceof Integer w) return w;
        if (call(call(Minecraft.getInstance(), "getWindow"), "getGuiScaledWidth") instanceof Integer w) return w;
        return 0;
    }

    /** Mensaje local en el chat: en 26.1+ es addClientSystemMessage; antes era addMessage. */
    static boolean chat(Object component) {
        Object chat = call(field(Minecraft.getInstance(), "gui"), "getChat");
        if (chat == null) return false;
        for (String name : new String[]{"addClientSystemMessage", "addMessage"}) {
            Optional<Method> m = METHODS.computeIfAbsent(chat.getClass().getName() + "#" + name + "/1",
                    k -> findMethod(chat.getClass(), name, 1));
            if (m.isEmpty()) continue;
            try {
                m.get().invoke(chat, component);
                return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                // probar el siguiente
            }
        }
        return false;
    }

    /** El portapapeles se movió a ClipboardManager en 26.3; se prueban los lugares posibles. */
    static boolean setClipboard(String text) {
        Minecraft mc = Minecraft.getInstance();
        Object keyboard = field(mc, "keyboardHandler");
        Object[] holders = {
                keyboard,
                field(mc, "clipboardManager"),
                call(mc, "getClipboardManager"),
                field(keyboard, "clipboardManager"),
                call(keyboard, "getClipboardManager"),
        };
        for (Object holder : holders) {
            if (invokeStringSetter(holder, "setClipboard", text)) return true;
        }
        return false;
    }

    private static boolean invokeStringSetter(Object target, String name, String value) {
        if (target == null) return false;
        try {
            Method m = target.getClass().getMethod(name, String.class);
            m.trySetAccessible();
            m.invoke(target, value);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
