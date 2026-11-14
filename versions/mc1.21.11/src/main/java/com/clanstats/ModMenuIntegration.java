package com.clanstats;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Si tienes Mod Menu, el botón de configuración de ClanStats abre el mismo menú que la tecla J. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ClanStatsClient::createScreen;
    }
}
