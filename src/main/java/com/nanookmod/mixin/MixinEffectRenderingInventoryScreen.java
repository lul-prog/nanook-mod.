package com.nanookmod.mixin;

import com.nanookmod.capability.NecromanticChainCapability;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * En el .class decompilado de EffectRenderingInventoryScreen (Forge
 * 1.20.1-47.2.0), renderEffects() llama a
 * "this.minecraft.player.getActiveEffects()". El campo Minecraft#player
 * está declarado como LocalPlayer (no Player), así que ese es el owner
 * real en el bytecode del INVOKEVIRTUAL - por eso la versión anterior
 * (con Player como owner) no encontraba coincidencia.
 *
 * Interceptamos esa llamada puntual y le quitamos de la lista los efectos
 * que la Cadena Necromántica aplica en silencio (Invisibilidad y
 * Velocidad) mientras está activa, dejando ver cualquier otro efecto
 * normal que el jugador tenga (pociones, etc.).
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class MixinEffectRenderingInventoryScreen {

    @Redirect(
            method = "renderEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"
            )
    )
    private Collection<MobEffectInstance> nanookmod$hideNecromanticChainEffects(LocalPlayer player) {
        Collection<MobEffectInstance> effects = player.getActiveEffects();

        NecromanticChainCapability.INecromanticChainCapability cap = NecromanticChainCapability.getCap(player);
        if (cap == null || !cap.isActive()) {
            return effects;
        }

        List<MobEffectInstance> filtered = new ArrayList<>();
        for (MobEffectInstance instance : effects) {
            if (instance.getEffect() != MobEffects.INVISIBILITY && instance.getEffect() != MobEffects.MOVEMENT_SPEED) {
                filtered.add(instance);
            }
        }
        return filtered;
    }
}
