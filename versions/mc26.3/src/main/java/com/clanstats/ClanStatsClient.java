package com.clanstats;

import com.clanstats.core.HudLayout;
import com.clanstats.core.PlayerRef;
import com.clanstats.core.ProfileNames;
import com.clanstats.mod.ClanStatsCore;
import com.clanstats.mod.Platform;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Punto de entrada para Minecraft 26.3 (juego sin ofuscar, Fabric API con nombres de Mojang). */
public final class ClanStatsClient implements ClientModInitializer, Platform {
    private KeyMapping hudKey;

    @Override
    public void onInitializeClient() {
        ClanStatsCore core = ClanStatsCore.init(this);

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(ClanStatsCore.MOD_ID, "main"));
        // 26.3 cambió GLFW por SDL (InputConstants.Type.KEYSYM ahora es KEYBOARD): se usa el constructor
        // sin tipo, que asume teclado, y la constante de InputConstants (nunca un código fijo).
        hudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.clanstats.cycle_hud", InputConstants.KEY_K, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (hudKey.consumeClick()) core.cycleHudMode();
        });

        // Se dibuja junto al chat, así se oculta con F1 igual que el resto del HUD.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(ClanStatsCore.MOD_ID, "hud"),
                (graphics, deltaTracker) -> {
                    if (Reflect.hudHidden()) return;
                    var font = Minecraft.getInstance().font;
                    HudLayout.Frame frame = core.hudFrame(new HudLayout.Metrics() {
                        @Override
                        public int width(String text) {
                            return font.width(text);
                        }

                        @Override
                        public int lineHeight() {
                            return font.lineHeight;
                        }
                    });
                    if (frame == null) return;
                    int x = frame.screenX(Reflect.guiScaledWidth(graphics));
                    graphics.pose().pushMatrix();
                    graphics.pose().translate((float) x, (float) frame.offsetY);
                    graphics.pose().scale(frame.scale, frame.scale);
                    for (HudLayout.Op op : frame.ops) {
                        if (op.fill()) {
                            graphics.fill(op.x1(), op.y1(), op.x2(), op.y2(), op.color());
                        } else {
                            graphics.text(font, op.text(), op.x1(), op.y1(), op.color(), op.shadow());
                        }
                    }
                    graphics.pose().popMatrix();
                });
    }

    // ------------------------------------------------------------------ Platform

    @Override
    public Collection<PlayerRef> onlinePlayers() {
        Object connection = Reflect.call(Minecraft.getInstance(), "getConnection");
        if (!(Reflect.call(connection, "getOnlinePlayers") instanceof Collection<?> players)) return List.of();
        List<PlayerRef> out = new ArrayList<>();
        for (Object info : players) {
            Object profile = Reflect.call(info, "getProfile");
            String name = ProfileNames.nameOf(profile);
            if (name != null) out.add(new PlayerRef(name, ProfileNames.idOf(profile)));
        }
        return out;
    }

    @Override
    public boolean copyToClipboard(String text) {
        return Reflect.setClipboard(text);
    }

    @Override
    public DeathInfo deathInfo(Component message) {
        Object contents = findDeath(message, 0);
        if (contents == null) return null;
        List<String> args = new ArrayList<>();
        if (Reflect.call(contents, "getArgs") instanceof Object[] raw) {
            for (Object a : raw) args.add(a instanceof Component c ? c.getString() : String.valueOf(a));
        }
        return new DeathInfo((String) Reflect.call(contents, "getKey"), args);
    }

    private static Object findDeath(Component c, int depth) {
        Object contents = c.getContents();
        if (contents != null && contents.getClass().getSimpleName().equals("TranslatableContents")
                && Reflect.call(contents, "getKey") instanceof String key && key.startsWith("death.")) {
            return contents;
        }
        if (depth >= 3) return null;
        for (Component sibling : c.getSiblings()) {
            Object r = findDeath(sibling, depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    @Override
    public Component copyButton(String label, String textToCopy) {
        // En 26.x el resumen se copia directo al portapapeles y queda en resumen.txt; no hace falta botón.
        return Component.literal("").withStyle(ChatFormatting.AQUA);
    }

    @Override
    public void chat(Component message) {
        if (!Reflect.chat(message)) ClanStatsCore.LOG.info(message.getString());
    }

    @Override
    public boolean hasMenu() {
        // El menú visual por ahora solo está en la versión 1.21.11; aquí se usa /clanes.
        return false;
    }

    @Override
    public boolean openMenu() {
        return false;
    }

    @Override
    public String minecraftVersion() {
        return "26.3";
    }

    @Override
    public String gameLanguage() {
        Object code = Reflect.field(Reflect.field(Minecraft.getInstance(), "options"), "languageCode");
        return code instanceof String c ? c : "";
    }
}
