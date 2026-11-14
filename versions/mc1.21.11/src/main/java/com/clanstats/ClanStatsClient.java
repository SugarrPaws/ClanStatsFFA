package com.clanstats;

import com.clanstats.core.HudLayout;
import com.clanstats.core.PlayerRef;
import com.clanstats.core.ProfileNames;
import com.clanstats.core.Texts;
import com.clanstats.mod.ClanStatsCore;
import com.clanstats.mod.Platform;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Punto de entrada para Minecraft 1.21.11 (mappings oficiales de Mojang). */
public final class ClanStatsClient implements ClientModInitializer, Platform {
    private static ClanStatsClient instance;

    private ClanStatsCore core;
    private KeyMapping hudKey;
    private KeyMapping menuKey;

    @Override
    public void onInitializeClient() {
        instance = this;
        core = ClanStatsCore.init(this);

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(ClanStatsCore.MOD_ID, "main"));
        hudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.clanstats.cycle_hud", InputConstants.Type.KEYSYM, InputConstants.KEY_K, category));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.clanstats.open_menu", InputConstants.Type.KEYSYM, InputConstants.KEY_J, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (hudKey.consumeClick()) core.cycleHudMode();
            while (menuKey.consumeClick()) openMenu();
        });

        // Se dibuja junto al chat, así se oculta con F1 igual que el resto del HUD.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(ClanStatsCore.MOD_ID, "hud"),
                (graphics, deltaTracker) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.options.hideGui) return;
                    var font = mc.font;
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
                    int x = frame.screenX(mc.getWindow().getGuiScaledWidth());
                    graphics.pose().pushMatrix();
                    graphics.pose().translate((float) x, (float) frame.offsetY);
                    graphics.pose().scale(frame.scale, frame.scale);
                    for (HudLayout.Op op : frame.ops) {
                        if (op.fill()) {
                            graphics.fill(op.x1(), op.y1(), op.x2(), op.y2(), op.color());
                        } else {
                            graphics.drawString(font, op.text(), op.x1(), op.y1(), op.color(), op.shadow());
                        }
                    }
                    graphics.pose().popMatrix();
                });
    }

    /** El menú visual. También lo abre Mod Menu con su botón de configuración. */
    static Screen createScreen(Screen parent) {
        return new ClanStatsScreen(parent, instance.core, instance.hudKey, instance.menuKey);
    }

    // ------------------------------------------------------------------ Platform

    @Override
    public Collection<PlayerRef> onlinePlayers() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return List.of();
        List<PlayerRef> out = new ArrayList<>();
        for (var info : connection.getOnlinePlayers()) {
            Object profile = info.getProfile();
            String name = ProfileNames.nameOf(profile);
            if (name != null) out.add(new PlayerRef(name, ProfileNames.idOf(profile)));
        }
        return out;
    }

    @Override
    public boolean copyToClipboard(String text) {
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public DeathInfo deathInfo(Component message) {
        TranslatableContents tc = findDeath(message, 0);
        if (tc == null) return null;
        List<String> args = new ArrayList<>();
        for (Object a : tc.getArgs()) args.add(a instanceof Component c ? c.getString() : String.valueOf(a));
        return new DeathInfo(tc.getKey(), args);
    }

    private static TranslatableContents findDeath(Component c, int depth) {
        if (c.getContents() instanceof TranslatableContents tc && tc.getKey().startsWith("death.")) return tc;
        if (depth >= 3) return null;
        for (Component sibling : c.getSiblings()) {
            TranslatableContents r = findDeath(sibling, depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    @Override
    public Component copyButton(String label, String textToCopy) {
        return Component.literal(label).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(textToCopy))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(Texts.tr("cmd.copy.hover")))));
    }

    @Override
    public void chat(Component message) {
        try {
            Minecraft.getInstance().gui.getChat().addMessage(message);
        } catch (RuntimeException e) {
            ClanStatsCore.LOG.info(message.getString());
        }
    }

    @Override
    public boolean hasMenu() {
        return true;
    }

    @Override
    public boolean openMenu() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(createScreen(mc.screen));
        return true;
    }

    @Override
    public String minecraftVersion() {
        return "1.21.11";
    }

    @Override
    public String gameLanguage() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.options == null ? "" : mc.options.languageCode;
    }
}
