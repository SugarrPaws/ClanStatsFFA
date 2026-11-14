package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

/** Copia mínima de la interfaz de Mod Menu, solo para compilar (no va dentro del .jar). */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {
    S create(Screen parent);
}
