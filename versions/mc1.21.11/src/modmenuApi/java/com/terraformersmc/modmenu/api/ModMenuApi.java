package com.terraformersmc.modmenu.api;

/**
 * Copia mínima de la interfaz pública de Mod Menu (licencia MIT, TerraformersMC), solo para compilar.
 * No va dentro del .jar (build.gradle la excluye): en el juego se usa la del Mod Menu real si está instalado,
 * y si no está, Fabric nunca carga ModMenuIntegration.
 */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> null;
    }
}
