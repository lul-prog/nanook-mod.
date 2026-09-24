package com.nanookmod.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nanookmod.NanookMod;
import com.nanookmod.event.FrostedNightHandler;
import com.nanookmod.event.TwilightNightHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * === REEMPLAZA POR COMPLETO al MixinFrostedMoon.java actual ===
 *
 * Por qué se fusionó: Mixin NO permite que dos @Redirect distintos apunten
 * a la MISMA instrucción (acá, la llamada a
 * RenderSystem.setShaderTexture dentro de renderSky) - si dejábamos un
 * MixinFrostedMoon Y un MixinTwilightMoon por separado, los dos compitiendo
 * por el mismo punto de inyección, el juego directamente no arranca
 * (conflicto de Mixin al aplicar). Por eso ahora es UN solo mixin que
 * decide entre 3 casos: Crepuscular > Escarchada > vanilla.
 *
 * Orden de prioridad: si en algún momento las dos lunas especiales
 * pudieran coincidir (no debería pasar con el diseño actual: una es
 * aleatoria y la otra la prende el jefe), la Crepuscular gana. Si eso no
 * es lo que querés, decime y cambio el orden.
 *
 * Sigue pendiente crear textures/environment/moon_phases_twilight.png
 * (misma cuadrícula 4x2 que moon_phases.png vanilla / moon_phases_frosted.png).
 */
@Mixin(LevelRenderer.class)
public class MixinFrostedMoon {

    @Shadow
    @Final
    private static ResourceLocation MOON_LOCATION;

    private static final ResourceLocation FROSTED_MOON_LOCATION =
            new ResourceLocation(NanookMod.MOD_ID, "textures/environment/moon_phases_frosted.png");

    private static final ResourceLocation TWILIGHT_MOON_LOCATION =
            new ResourceLocation(NanookMod.MOD_ID, "textures/environment/moon_phases_twilight.png");

    /**
     * v2: el swap de textura de la luna sigue siendo un salto (no se puede
     * hacer un crossfade real entre dos texturas con un solo bind, eso sí
     * necesitaría un shader de verdad con dos samplers). Lo que sí se hizo
     * progresivo es CUÁNDO salta: antes cambiaba apenas isTwilightNight()
     * se ponía en true (o sea, en el mismo tick en que arranca el ritual,
     * con el cielo todavía de día) - un pop rarísimo, la luna crepuscular
     * apareciendo a plena luz del día. Ahora espera a que la transición
     * esté CASI terminada (progress >= 0.92) antes de cambiar la textura,
     * que es cuando el cielo ya está lo bastante oscuro como para que la
     * luna se vea y el cambio de textura no se note como un "pop" (la luna
     * vainilla original tampoco se ve nítida contra un cielo todavía
     * claro, así que el salto queda escondido en el propio atardecer).
     */
    @Redirect(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"
            )
    )
    private void nanookmod$swapMoonTexture(int textureUnit, ResourceLocation location) {
        if (location.equals(MOON_LOCATION)) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                if (TwilightNightHandler.isTwilightNight(level)
                        && TwilightNightHandler.getTransitionProgress(level) >= 0.92F) {
                    RenderSystem.setShaderTexture(textureUnit, TWILIGHT_MOON_LOCATION);
                    return;
                }
                if (FrostedNightHandler.isFrostedNight(level)) {
                    RenderSystem.setShaderTexture(textureUnit, FROSTED_MOON_LOCATION);
                    return;
                }
            }
        }
        RenderSystem.setShaderTexture(textureUnit, location);
    }
}