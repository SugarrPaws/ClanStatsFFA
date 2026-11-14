package com.clanstats;

import com.clanstats.core.ProfileNames;
import com.clanstats.mod.ClanStatsCore;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Pinta en la lista de Tab el nombre de cada miembro de un clan con el color del clan. Solo cambia el color del
 * nombre: el resto de lo que muestra el servidor (rango, prefijos) queda igual y no se agrega ninguna etiqueta.
 */
public final class TabColors {
    private TabColors() {}

    /** Nombre del Tab ya pintado, o null si no hay que cambiar nada (se deja el original). */
    public static Component recolor(PlayerInfo info, Component original) {
        if (info == null || original == null) return null;
        try {
            Object profile = info.getProfile();
            return recolor(ProfileNames.nameOf(profile), ProfileNames.idOf(profile), original);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Texto con el nombre del jugador pintado del color de su clan, o null si no hay que cambiar nada: no es de
     * ningún clan, la opción de colores o el mod están apagados, o el texto no contiene su nombre.
     */
    public static Component recolor(String name, String uuid, Component original) {
        ClanStatsCore core = ClanStatsCore.get();
        if (core == null || original == null || name == null) return null;
        try {
            Integer rgb = core.tabColor(name, uuid);
            return rgb == null ? null : paint(original, name, rgb);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Copia el texto con el mismo estilo, pero con el nombre del jugador en el color dado. Todo lo demás (rango,
     * tiers, íconos de otros mods) queda exactamente igual. Si el nombre no aparece, devuelve null.
     */
    static Component paint(Component original, String name, int rgb) {
        List<String> parts = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        FormattedText.StyledContentConsumer<Object> collect = (style, text) -> {
            if (!text.isEmpty()) {
                parts.add(text);
                styles.add(style);
            }
            return Optional.empty();
        };
        original.visit(collect, Style.EMPTY);
        String full = String.join("", parts);
        int start = full.indexOf(name);
        if (start < 0) start = full.toLowerCase(Locale.ROOT).indexOf(name.toLowerCase(Locale.ROOT));
        if (start < 0) return null; // el servidor muestra otro texto (un apodo): no se toca nada
        int end = start + name.length();

        TextColor color = TextColor.fromRgb(rgb);
        MutableComponent out = Component.empty();
        int pos = 0;
        for (int i = 0; i < parts.size(); i++) {
            String t = parts.get(i);
            Style st = styles.get(i);
            int a = pos;
            int b = pos + t.length();
            int s1 = Math.max(a, Math.min(b, start));
            int s2 = Math.max(a, Math.min(b, end));
            if (s1 > a) out.append(Component.literal(t.substring(0, s1 - a)).setStyle(st));
            if (s2 > s1) out.append(Component.literal(t.substring(s1 - a, s2 - a)).setStyle(st.withColor(color)));
            if (b > s2) out.append(Component.literal(t.substring(s2 - a)).setStyle(st));
            pos = b;
        }
        return out;
    }
}
