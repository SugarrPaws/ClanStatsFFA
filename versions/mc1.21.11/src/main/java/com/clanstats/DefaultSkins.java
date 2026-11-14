package com.clanstats;

import com.clanstats.core.Uuids;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.world.entity.player.PlayerSkin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Skin por defecto (Steve, Alex, etc.) según el UUID, para la cabeza de quien no está en la sala.
 * Se busca por reflexión con el nombre intermediario de DefaultPlayerSkin.get(UUID): si en alguna versión
 * no existe, simplemente se dibuja un cuadrado con la inicial en lugar de la cabeza.
 */
final class DefaultSkins {
    private static final String OWNER = "net.minecraft.class_1068";
    private static Method getter;
    private static boolean searched;

    private DefaultSkins() {}

    static PlayerSkin forUuid(String uuid) {
        UUID id = Uuids.parse(uuid);
        if (id == null) return null;
        try {
            if (!searched) {
                searched = true;
                MappingResolver r = FabricLoader.getInstance().getMappingResolver();
                Class<?> c = Class.forName(r.mapClassName("intermediary", OWNER));
                getter = c.getMethod(r.mapMethodName("intermediary", OWNER, "method_4648",
                        "(Ljava/util/UUID;)Lnet/minecraft/class_8685;"), UUID.class);
            }
            return getter == null ? null : (PlayerSkin) getter.invoke(null, id);
        } catch (Throwable t) {
            getter = null;
            return null;
        }
    }
}
