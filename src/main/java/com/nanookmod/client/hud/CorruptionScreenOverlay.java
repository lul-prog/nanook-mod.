package com.nanookmod.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Vignette de pantalla completa para la Corrupción -- textura provista por
 * @nanook (tentáculos turquesa/violeta que invaden desde los bordes, centro
 * transparente). Se estira para cubrir toda la pantalla y su opacidad
 * (además de un pulso en niveles altos) depende del amplifier del efecto
 * en el JUGADOR LOCAL -- esto es puramente client-side, no tiene nada que
 * ver con el HUD del jefe (NecroticKnightHudOverlay).
 *
 * Progresión pedida:
 *   Corrupción I   (amplifier 0) -- nada
 *   Corrupción II  (amplifier 1) -- muy sutil, sobre todo en las esquinas
 *   Corrupción III (amplifier 2) -- bastante más visible
 *   Corrupción IV+ (amplifier 3+) -- intenso y pulsante
 */
public class CorruptionScreenOverlay implements IGuiOverlay {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(NanookMod.MOD_ID, "textures/gui/corruption_screen_overlay.png");

    private static final int TEX_SIZE = 512;

    // Cuánto puede cambiar currentAlpha por tick como máximo -- así el
    // pasaje entre niveles (o la desaparición cuando el efecto se cae) es
    // una transición suave, no un salto brusco. Mismo lenguaje visual que
    // el overlay vanilla de nieve en polvo.
    private static final float MAX_ALPHA_DELTA_PER_TICK = 0.03F;

    // Estado propio de esta instancia -- se registra UNA sola vez
    // (ClientEvents#registerGuiOverlays), así que este campo persiste
    // frame a frame sin problema.
    private float currentAlpha = 0.0F;
    private float lastTickTime = -1F;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        float animTime = mc.player != null ? mc.player.tickCount + partialTick : 0F;

        float targetAlpha = 0.0F;
        if (mc.player != null && !mc.options.hideGui) {
            MobEffectInstance corruption = mc.player.getEffect(ModEffects.CORRUPTION.get());
            if (corruption != null) {
                targetAlpha = computeTargetAlpha(corruption.getAmplifier(), animTime);
            }
        }

        // Avanza currentAlpha hacia targetAlpha, acotado por frame para que
        // sea gradual. lastTickTime evita un salto grande en el primer
        // frame después de un rato sin renderizar (pausa, etc.).
        float delta = lastTickTime < 0 ? MAX_ALPHA_DELTA_PER_TICK : Math.abs(animTime - lastTickTime) * MAX_ALPHA_DELTA_PER_TICK;
        lastTickTime = animTime;
        if (currentAlpha < targetAlpha) {
            currentAlpha = Math.min(targetAlpha, currentAlpha + delta);
        } else if (currentAlpha > targetAlpha) {
            currentAlpha = Math.max(targetAlpha, currentAlpha - delta);
        }

        if (currentAlpha <= 0.002F) {
            return;
        }

        // Estira la textura (siempre 512x512) para cubrir toda la pantalla,
        // sin importar resolución/gui scale -- misma técnica de matriz que
        // usamos en NecroticKnightHudOverlay para las bossbars.
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(screenWidth / (float) TEX_SIZE, screenHeight / (float) TEX_SIZE, 1F);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, currentAlpha);
        guiGraphics.blit(TEXTURE, 0, 0, 0, 0, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        guiGraphics.pose().popPose();
    }

    private static float computeTargetAlpha(int amplifier, float animTime) {
        switch (amplifier) {
            case 0:
                return 0.0F;
            case 1:
                return 0.15F;
            case 2:
                return 0.35F;
            default:
                // IV en adelante -- pulso constante entre ~0.45 y ~0.70.
                float pulse = (float) Math.sin(animTime * 0.12D) * 0.125F;
                return 0.575F + pulse;
        }
    }
}
