package com.nanookmod.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nanookmod.NanookMod;
import com.nanookmod.event.PermafrostDamageHandler;
import com.nanookmod.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Escarcha que invade la pantalla según cuánto Permafrost acumulaste.
 *
 * El arte es la secuencia "clima_congelacion" (60 cuadros, 320x180), que
 * por dentro tiene tres tramos bien distintos y los usamos los tres:
 *
 *   cuadros 0..30   -- la escarcha se forma desde los bordes. Este tramo NO
 *                      se reproduce como animación en el tiempo: se elige el
 *                      cuadro según la CARGA. O sea, el cuadro es literalmente
 *                      un medidor de cuán congelado estás.
 *   cuadros 30..48  -- escarcha "llena". Acá sí loopea despacio, para que a
 *                      carga alta la pantalla tenga vida propia en vez de
 *                      quedar como una imagen pegada.
 *   cuadros 49..59  -- la escarcha se deshace. Se reproduce UNA vez cuando la
 *                      carga cae a cero (saliste del bioma, tomaste la poción
 *                      mejorada, moriste y respawneaste).
 *
 * Todo esto es 100% cliente: lee PERMAFROST_CHARGE, que ya viaja solo por
 * SynchedEntityData (ver PermafrostDamageHandler), así que no hizo falta
 * ningún paquete nuevo.
 */
public class PermafrostFrostOverlay implements IGuiOverlay {

    private static final int FRAME_COUNT = 60;
    private static final int BUILD_LAST_FRAME = 30;   // fin del tramo "se forma"
    private static final int HOLD_LAST_FRAME = 48;    // fin del tramo "lleno"
    private static final int THAW_FIRST_FRAME = 49;   // arranque del "se deshace"

    private static final int TEX_WIDTH = 320;
    private static final int TEX_HEIGHT = 180;

    /** Misma constante que PermafrostDamageHandler -- 100 s de carga. */
    private static final int MAX_CHARGE = 2000;

    /** Debajo de esto no se dibuja nada (evita el velo permanente sutil). */
    private static final float MIN_VISIBLE_PROGRESS = 0.04F;

    /** Cuántos ticks tarda cada cuadro del loop de "escarcha llena". */
    private static final int HOLD_TICKS_PER_FRAME = 3;

    /** Cuántos ticks tarda cada cuadro del deshielo. */
    private static final int THAW_TICKS_PER_FRAME = 2;

    private static final ResourceLocation[] FRAMES = buildFrameLocations();

    // Estado propio de la instancia (se registra una sola vez en ClientEvents).
    private float smoothedProgress = 0.0F;
    private int holdAnimTicks = 0;
    private int thawFrame = -1;          // -1 = no estamos deshelando
    private int thawTicks = 0;
    private float lastProgress = 0.0F;
    private long lastGameTime = -1L;

    private static ResourceLocation[] buildFrameLocations() {
        ResourceLocation[] frames = new ResourceLocation[FRAME_COUNT];
        for (int i = 0; i < FRAME_COUNT; i++) {
            frames[i] = new ResourceLocation(NanookMod.MOD_ID,
                    String.format("textures/gui/permafrost/frost_%02d.png", i));
        }
        return frames;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }

        // Avanzar contadores una vez por tick de juego (render corre a FPS,
        // no a 20 tps -- sin esto, a 144 fps el loop iría 7 veces más rápido).
        long gameTime = mc.level.getGameTime();
        boolean newTick = gameTime != lastGameTime;
        lastGameTime = gameTime;

        float targetProgress = computeTargetProgress(mc);

        // Transición suave de la carga: la carga real sube de a 1 por tick,
        // pero cuando salta de golpe (respawn, poción) no queremos un corte.
        if (newTick) {
            smoothedProgress = Mth.lerp(0.25F, smoothedProgress, targetProgress);

            // ¿Arrancó el deshielo? Se dispara cuando veníamos con escarcha
            // visible y la carga real cayó a cero.
            if (targetProgress <= 0.001F && lastProgress > 0.25F && thawFrame < 0) {
                thawFrame = THAW_FIRST_FRAME;
                thawTicks = 0;
                smoothedProgress = 0.0F;
            }
            lastProgress = targetProgress;

            if (thawFrame >= 0) {
                if (++thawTicks >= THAW_TICKS_PER_FRAME) {
                    thawTicks = 0;
                    thawFrame++;
                    if (thawFrame >= FRAME_COUNT) {
                        thawFrame = -1; // terminó el deshielo
                    }
                }
            } else if (targetProgress > 0.0F) {
                holdAnimTicks++;
            }
        }

        int frame;
        float alpha;

        if (thawFrame >= 0) {
            // Tramo 3: deshielo. Se dibuja completo, la propia textura se
            // encarga de desvanecerse.
            frame = thawFrame;
            alpha = 1.0F;
        } else {
            if (smoothedProgress < MIN_VISIBLE_PROGRESS) {
                return;
            }
            if (smoothedProgress >= 0.97F) {
                // Tramo 2: escarcha llena, loop lento entre 30 y 48.
                int span = HOLD_LAST_FRAME - BUILD_LAST_FRAME + 1;
                frame = BUILD_LAST_FRAME + ((holdAnimTicks / HOLD_TICKS_PER_FRAME) % span);
            } else {
                // Tramo 1: el cuadro ES el medidor de congelación.
                frame = Math.round(smoothedProgress * BUILD_LAST_FRAME);
            }
            frame = Mth.clamp(frame, 0, FRAME_COUNT - 1);
            alpha = 1.0F;
        }

        // Estirar la textura (320x180) a toda la pantalla, sin importar
        // resolución ni gui scale -- misma técnica que CorruptionScreenOverlay.
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(screenWidth / (float) TEX_WIDTH, screenHeight / (float) TEX_HEIGHT, 1.0F);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        guiGraphics.blit(FRAMES[frame], 0, 0, 0, 0, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        guiGraphics.pose().popPose();
    }

    /**
     * 0 = sin nada de escarcha, 1 = al borde de morir congelado.
     *
     * Con inmunidad TOTAL (poción mejorada, amplifier >= 1) devolvemos 0
     * aunque el efecto siga puesto: PermafrostDamageHandler ya baja la carga
     * a cero en ese caso, pero adelantarlo acá hace que el deshielo arranque
     * en el mismo instante en que tomás la poción, que es lo que se espera.
     */
    private float computeTargetProgress(Minecraft mc) {
        if (!mc.player.hasEffect(ModEffects.PERMAFROST.get())) {
            return 0.0F;
        }
        var immunity = mc.player.getEffect(ModEffects.PERMAFROST_IMMUNITY.get());
        if (immunity != null && immunity.getAmplifier() >= 1) {
            return 0.0F;
        }
        int charge = PermafrostDamageHandler.getPermafrostCharge(mc.player);
        return Mth.clamp(charge / (float) MAX_CHARGE, 0.0F, 1.0F);
    }
}
