package com.clanstats.mixin;

import com.clanstats.TabColors;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Nombre encima de cada jugador: si es de un clan, el nombre sale con el color del clan. Como en el Tab, solo
 * cambia el color del nombre y deja igual lo que agregan el servidor u otros mods (tiers, rangos).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @ModifyReturnValue(method = "getNameTag", at = @At("RETURN"))
    private Component clanstats$clanColor(Component original, Entity entity) {
        if (original == null || !(entity instanceof Player player)) return original;
        Component colored = TabColors.recolor(player.getScoreboardName(), player.getUUID().toString(), original);
        return colored != null ? colored : original;
    }
}
