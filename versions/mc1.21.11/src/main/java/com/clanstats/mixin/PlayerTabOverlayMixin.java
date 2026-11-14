package com.clanstats.mixin;

import com.clanstats.TabColors;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lista de Tab: el nombre de los miembros de un clan sale con el color del clan (ver {@link TabColors}).
 * Con @ModifyReturnValue los cambios de otros mods sobre el mismo nombre (tiers de TierTagger o QTiers,
 * por ejemplo) se aplican igual: cada mod recibe el nombre ya modificado por el otro.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    private Component clanstats$clanColor(Component original, PlayerInfo info) {
        Component colored = TabColors.recolor(info, original);
        return colored != null ? colored : original;
    }
}
