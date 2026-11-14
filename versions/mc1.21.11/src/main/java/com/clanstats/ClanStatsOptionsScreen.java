package com.clanstats;

import com.clanstats.core.Texts;
import com.clanstats.mod.ClanStatsCore;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Opciones del mod (se abre desde el botón «Opciones» del menú): teclas del menú y del HUD, idioma y el color
 * de clan en el Tab y encima de los jugadores.
 */
final class ClanStatsOptionsScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int GREEN = 0xFF55FF55;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int GOLD = 0xFFFFAA00;
    private static final int PANEL = 0xA0000000;

    private static final int KEY_ESCAPE = 256;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;

    private final Screen parent;
    private final ClanStatsCore core;
    private final KeyMapping hudKey;
    private final KeyMapping menuKey;

    private KeyMapping capturing;
    private String status;
    private int statusColor = WHITE;
    private long statusUntil;
    private boolean pendingRefresh;
    private int builtVersion = -1;
    private int panelX;
    private int panelW;
    private int top;
    private int bottom;

    ClanStatsOptionsScreen(Screen parent, ClanStatsCore core, KeyMapping hudKey, KeyMapping menuKey) {
        super(Component.literal("ClanStats"));
        this.parent = parent;
        this.core = core;
        this.hudKey = hudKey;
        this.menuKey = menuKey;
    }

    @Override
    protected void init() {
        builtVersion = core.version();
        panelW = Math.min(260, width - 40);
        panelX = (width - panelW) / 2;
        int rows = 5;
        int step = 24;
        top = Math.max(26, (height - rows * step - 40) / 2);
        int y = top + 18;
        int x = panelX + 10;
        int w = panelW - 20;

        addRenderableWidget(keyButton(tr("opt.menu_key"), menuKey, x, y, w));
        y += step;
        addRenderableWidget(keyButton(tr("opt.hud_key"), hudKey, x, y, w));
        y += step;
        addRenderableWidget(Button.builder(Component.literal(tr("opt.language", core.languageLabel())), b -> {
                    core.setLanguageSetting(core.nextLanguageSetting());
                    setStatus(tr("lang.changed", core.languageLabel()), GREEN);
                    refresh();
                })
                .bounds(x, y, w, 20)
                .tooltip(tip(tr("menu.language_tip", core.languageLabel())))
                .build());
        y += step;
        boolean colors = core.config.colorNombres;
        Component state = Component.literal(tr(colors ? "state.on" : "state.off"))
                .withStyle(colors ? ChatFormatting.GREEN : ChatFormatting.RED);
        addRenderableWidget(Button.builder(Component.literal(tr("opt.colors") + ": ").append(state), b -> {
                    core.setNameColors(!core.config.colorNombres);
                    refresh();
                })
                .bounds(x, y, w, 20)
                .tooltip(tip(tr("opt.colors_tip")))
                .build());
        y += step + 6;
        addRenderableWidget(Button.builder(Component.literal(tr("menu.done")), b -> onClose())
                .bounds(x, y, w, 20)
                .build());
        bottom = y + 20 + 8;
    }

    private Button keyButton(String label, KeyMapping mapping, int x, int y, int w) {
        Component text = capturing == mapping
                ? Component.literal("> " + label + ": ? <").withStyle(ChatFormatting.YELLOW)
                : Component.literal(label + ": " + mapping.getTranslatedKeyMessage().getString());
        return Button.builder(text, b -> {
                    capturing = mapping;
                    refresh();
                })
                .bounds(x, y, w, 20)
                .tooltip(tip(tr("menu.key_tip")))
                .build();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderBackground(g, mouseX, mouseY, delta);
        g.fill(panelX, top - 4, panelX + panelW, bottom, PANEL);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        String title = tr("opt.title");
        g.drawString(font, title, width / 2 - font.width(title) / 2, top + 2, GOLD, true);
        String line = null;
        int color = GRAY;
        if (capturing != null) {
            line = tr("menu.press_key");
            color = YELLOW;
        } else if (status != null && System.currentTimeMillis() < statusUntil) {
            line = status;
            color = statusColor;
        }
        if (line != null) {
            String shown = fit(line, width - 20);
            g.drawString(font, shown, width / 2 - font.width(shown) / 2, bottom + 6, color, true);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.input();
        if (capturing != null) {
            KeyMapping mapping = capturing;
            capturing = null;
            if (key == KEY_ESCAPE) {
                setStatus(tr("menu.key_cancelled"), GRAY);
            } else {
                mapping.setKey(key == KEY_BACKSPACE || key == KEY_DELETE ? InputConstants.UNKNOWN : InputConstants.getKey(event));
                KeyMapping.resetMapping();
                minecraft.options.save();
                String conflict = conflictOf(mapping);
                if (mapping.isUnbound()) {
                    setStatus(tr("menu.key_unbound"), YELLOW);
                } else {
                    setStatus(tr("menu.key_set", mapping.getTranslatedKeyMessage().getString())
                            + (conflict != null ? "  " + tr("menu.key_conflict", conflict) : ""), conflict != null ? YELLOW : GREEN);
                }
            }
            refresh();
            return true;
        }
        return super.keyPressed(event);
    }

    private String conflictOf(KeyMapping mapping) {
        if (mapping.isUnbound()) return null;
        for (KeyMapping other : minecraft.options.keyMappings) {
            if (other != mapping && other.same(mapping)) return Component.translatable(other.getName()).getString();
        }
        return null;
    }

    @Override
    public void tick() {
        if (core.version() != builtVersion) pendingRefresh = true;
        if (pendingRefresh) {
            pendingRefresh = false;
            rebuildWidgets();
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void refresh() {
        pendingRefresh = true;
    }

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 6000;
    }

    private static String tr(String key, Object... args) {
        return Texts.tr(key, args);
    }

    private static Tooltip tip(String text) {
        return Tooltip.create(Component.literal(text));
    }

    private String fit(String s, int max) {
        if (font.width(s) <= max) return s;
        String out = s;
        while (!out.isEmpty() && font.width(out + "…") > max) out = out.substring(0, out.length() - 1);
        return out + "…";
    }
}
