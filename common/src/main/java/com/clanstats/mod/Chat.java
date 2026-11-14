package com.clanstats.mod;

import com.clanstats.core.ClanConfig;
import com.clanstats.core.Colors;
import com.clanstats.core.StatsEngine;
import com.clanstats.core.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Atajos para armar mensajes de chat con colores (los textos salen de {@link Texts}). */
final class Chat {
    private Chat() {}

    static MutableComponent prefix() {
        return Component.literal("[ClanStats] ").withStyle(ChatFormatting.GOLD);
    }

    static MutableComponent t(String text, ChatFormatting... formats) {
        return Component.literal(text).withStyle(formats);
    }

    /** Texto traducido con el formato dado. */
    static MutableComponent tr(ChatFormatting format, String key, Object... args) {
        return t(Texts.tr(key, args), format);
    }

    /**
     * Texto traducido donde los datos pueden ser componentes con su propio color (por ejemplo el nombre de un
     * clan): "{0} movido de {1} a {2}" con {1} y {2} en el color de cada clan.
     */
    static MutableComponent fmt(ChatFormatting base, String key, Object... args) {
        String tpl = Texts.tr(key);
        MutableComponent out = Component.empty();
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < tpl.length()) {
            char c = tpl.charAt(i);
            int end = c == '{' ? tpl.indexOf('}', i) : -1;
            if (end > i + 1 && end - i <= 3) {
                int n = -1;
                try {
                    n = Integer.parseInt(tpl.substring(i + 1, end));
                } catch (NumberFormatException ignored) {
                    // {V}, {K}...: es texto normal
                }
                if (n >= 0 && n < args.length) {
                    if (buf.length() > 0) out.append(t(buf.toString(), base));
                    buf.setLength(0);
                    Object a = args[n];
                    out.append(a instanceof Component comp ? comp : t(String.valueOf(a), base));
                    i = end + 1;
                    continue;
                }
            }
            buf.append(c);
            i++;
        }
        if (buf.length() > 0) out.append(t(buf.toString(), base));
        return out;
    }

    static Component info(String text) {
        return prefix().append(t(text, ChatFormatting.GRAY));
    }

    static Component ok(String text) {
        return prefix().append(t(text, ChatFormatting.GREEN));
    }

    static Component err(String text) {
        return prefix().append(t(text, ChatFormatting.RED));
    }

    /** Texto con el color de un clan (cualquier color, no solo los 16 del chat). */
    static MutableComponent colored(String text, String color) {
        return Component.literal(text).withColor(Colors.rgb(color));
    }

    static MutableComponent clan(ClanConfig.Clan clan) {
        return colored(clan.nombre, clan.color);
    }

    static MutableComponent player(String name, ClanConfig config) {
        ClanConfig.Clan c = config.clanOf(name);
        return c == null ? t(name, ChatFormatting.WHITE) : colored(name, c.color);
    }

    /** "12/4 (KD 3.00)" con el KD en verde o rojo. */
    static MutableComponent kd(int kills, int deaths) {
        double v = deaths == 0 ? kills : (double) kills / deaths;
        ChatFormatting c = (kills == 0 && deaths == 0) ? ChatFormatting.GRAY
                : (v >= 1.0 ? ChatFormatting.GREEN : ChatFormatting.RED);
        return t(kills + "/" + deaths, ChatFormatting.WHITE)
                .append(t(" (KD ", ChatFormatting.DARK_GRAY))
                .append(t(StatsEngine.fmtKd(v), c))
                .append(t(")", ChatFormatting.DARK_GRAY));
    }
}
