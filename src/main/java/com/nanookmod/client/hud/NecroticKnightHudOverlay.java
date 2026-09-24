package com.nanookmod.client.hud;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.NecroticKnightEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Dibuja el HUD custom del NecroticKnight: bossbar de vida, debajo la
 * bossbar de Corrupción (con número romano), y debajo de esa el widget de
 * Drenaje Vital (ícono + cargas) -- solo si hay cargas activas.
 *
 * No usa la bossbar vanilla para nada de esto (ver NecroticKnightEntity:
 * bossEvent.setVisible(false)). Lee los valores DIRECTO de la entidad
 * (getHealth/getCorruptionLevel/getDrenajeCharges), que ya viajan
 * sincronizados como SynchedEntityData -- no hace falta duplicar esos
 * datos en un paquete propio, solo necesitamos saber A QUÉ entidad mirar
 * (eso sí lo manda SyncNecroticKnightHudPacket -> NecroticKnightHudTracker).
 *
 * Texturas más grandes que el límite viejo de bossbar vanilla (182x5) a
 * propósito -- 300x20 -- para tener margen de detalle. Se dibujan
 * escaladas con una matriz (DISPLAY_SCALE) así se ven nítidas sin importar
 * la resolución/gui scale del jugador. Si el día de mañana el diseño final
 * necesita otro tamaño de textura, ajustar TEX_W/TEX_H y listo.
 */
public class NecroticKnightHudOverlay implements IGuiOverlay {

    private static final ResourceLocation TEX_FRAME =
            new ResourceLocation(NanookMod.MOD_ID, "textures/gui/bossbar/bar_frame.png");
    private static final ResourceLocation TEX_BACKGROUND_HEALTH =
            new ResourceLocation(NanookMod.MOD_ID, "textures/gui/bossbar/bar_background.png");
    private static final ResourceLocation TEX_FILL_HEALTH =
            new ResourceLocation(NanookMod.MOD_ID, "textures/gui/bossbar/bar_fill_health.png");

    // Independiente de la de vida a propósito (antes reusaban la misma
    // TEX_BACKGROUND/TEX_FRAME) -- así se puede cambiar el look de esta
    // barra sin afectar la de vida. Por ahora apunta al mismo PNG de fondo
    // que la de vida así no cambia nada visualmente todavía; si querés que
    // se vea distinta, apuntá esto a otro archivo (ya tenés
    // bar_background1.png sin usar en los assets, por si es para esto).
    private static final ResourceLocation TEX_BACKGROUND_CORRUPTION =
            new ResourceLocation(NanookMod.MOD_ID, "textures/gui/bossbar/bar_background3.png");
    private static final ResourceLocation TEX_FILL_CORRUPTION =
            new ResourceLocation(NanookMod.MOD_ID, "textures/gui/bossbar/bar_fill_corruption3.png");
    // Sin marco en la barra de Corrupción a pedido. Para volver a
    // mostrarlo, descomentar esta constante y pasarla como frameTexture en
    // el drawBar() de la barra de Corrupción (más abajo).
    // private static final ResourceLocation TEX_FRAME_CORRUPTION =
    //         new ResourceLocation(NanookMod.MOD_ID, "textures/gui/bossbar/bar_frame.png");

    private static final ResourceLocation TEX_DRENAJE_ICON =
            new ResourceLocation(NanookMod.MOD_ID, "textures/gui/bossbar/drenaje_icon.png");

    // Mismo color que el anillo del ataque Wave (NecroticWaveRenderer:
    // COLOR_R/G/B = 0.6118/0.9647/0.8706 -- #9CF6DE), a pedido, para que el
    // nombre del jefe quede visualmente asociado a su paleta.
    private static final int NAME_COLOR = 0x9CF6DE;
    private static final int NAME_GAP = 2;

    // --- Tamaño de diseño de las texturas (píxeles reales del PNG) ---
    private static final int TEX_W = 300;
    private static final int TEX_H = 20;

    // --- A qué tamaño se muestran en pantalla (independiente de TEX_W/H) ---
    private static final float DISPLAY_SCALE = 0.62F;
    private static final int DISPLAY_W = (int) (TEX_W * DISPLAY_SCALE);
    private static final int DISPLAY_H = (int) (TEX_H * DISPLAY_SCALE);

    // Separación vertical entre barras / entre la última barra y el widget.
    private static final int GAP = 3;

    // Dónde arranca la primera barra (vida), medido desde arriba de la pantalla.
    private static final int TOP_MARGIN = 14;

    // Marcas de fase (75/50/25% de vida) sobre la barra de vida.
    // Desactivado a pedido -- las líneas de 75/50/25% ya no se dibujan en
    // la barra de vida (ver la llamada a drawBar más abajo, ahora pasa
    // null en vez de esta constante). Para volver a mostrarlas, pasar
    // PHASE_MARKS de nuevo en esa llamada.
    private static final float[] PHASE_MARKS = {0.75F, 0.50F, 0.25F};

    private static final int ICON_TEX_SIZE = 32;
    private static final int ICON_DISPLAY_SIZE = 18;
    private static final float ICON_SCALE = (float) ICON_DISPLAY_SIZE / ICON_TEX_SIZE;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics,
                       float partialTick, int screenWidth, int screenHeight) {
        if (!NecroticKnightHudTracker.hasActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(NecroticKnightHudTracker.getActiveEntityId());
        if (!(entity instanceof NecroticKnightEntity knight) || !entity.isAlive()) {
            return;
        }
        // No mostrar el HUD custom mientras el jugador está en un menú de
        // debug/pausa raro donde no debería (F1 oculta todo el HUD, Forge
        // ya no llama a este render en ese caso, así que no hace falta
        // chequearlo acá).

        int centerX = screenWidth / 2;
        int y = TOP_MARGIN;

        // --- Nombre del jefe, centrado arriba de la barra de vida ---
        Component name = knight.getDisplayName();
        int nameWidth = mc.font.width(name);
        guiGraphics.drawString(mc.font, name, centerX - nameWidth / 2, y, NAME_COLOR, true);
        y += mc.font.lineHeight + NAME_GAP;

        // --- Barra de vida ---
        float healthPct = knight.getMaxHealth() > 0
                ? Mth_clamp01(knight.getHealth() / knight.getMaxHealth())
                : 0F;
        drawBar(guiGraphics, centerX - DISPLAY_W / 2, y, TEX_BACKGROUND_HEALTH, TEX_FILL_HEALTH, TEX_FRAME, healthPct, null);
        y += DISPLAY_H + GAP;

        // --- Barra de Corrupción (solo si el jefe llegó a subir alguna vez en esta pelea, o si está activa ahora) ---
        int corruptionLevel = knight.getCorruptionLevel();
        int maxCorruption = knight.getMaxCorruptionLevel();
        boolean showCorruption = corruptionLevel > 0;
        if (showCorruption) {
            float corruptionPct = maxCorruption > 0 ? (float) corruptionLevel / (float) maxCorruption : 0F;
            // Sin marco (frameTexture = null) a pedido -- ver el comentario
            // de TEX_FRAME_CORRUPTION más arriba para reactivarlo.
            drawBar(guiGraphics, centerX - DISPLAY_W / 2, y, TEX_BACKGROUND_CORRUPTION, TEX_FILL_CORRUPTION, null, corruptionPct, null);

            // Número romano a la derecha de la barra.
            String roman = corruptionLevel > 0 && corruptionLevel < NecroticKnightEntity.CORRUPTION_ROMAN.length
                    ? NecroticKnightEntity.CORRUPTION_ROMAN[corruptionLevel]
                    : "";
            if (!roman.isEmpty()) {
                int textX = centerX + DISPLAY_W / 2 + 6;
                int textY = y + (DISPLAY_H / 2) - 4;
                guiGraphics.drawString(mc.font, roman, textX, textY, 0xB98CE0, true);
            }
            y += DISPLAY_H + GAP;
        }

        // --- Widget de Drenaje Vital: ícono + número, solo si hay cargas ---
        int drenajeCharges = knight.getDrenajeCharges();
        if (drenajeCharges > 0) {
            String chargesText = "x" + drenajeCharges;
            int textWidth = mc.font.width(chargesText);
            int widgetWidth = ICON_DISPLAY_SIZE + 3 + textWidth;
            int widgetX = centerX - widgetWidth / 2;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(widgetX, y, 0);
            guiGraphics.pose().scale(ICON_SCALE, ICON_SCALE, 1F);
            guiGraphics.blit(TEX_DRENAJE_ICON, 0, 0, 0, 0, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE);
            guiGraphics.pose().popPose();

            int textX = widgetX + ICON_DISPLAY_SIZE + 3;
            int textY = y + (ICON_DISPLAY_SIZE / 2) - 4;
            guiGraphics.drawString(mc.font, chargesText, textX, textY, 0xFF5555, true);
        }
    }

    /**
     * Dibuja una barra completa (fondo + relleno recortado según pct + marco opcional) escalada a DISPLAY_W x DISPLAY_H.
     * phaseMarks puede ser null si esa barra no tiene marcas de fase (ej. la de Corrupción).
     * frameTexture puede ser null para no dibujar marco (ej. la de Corrupción, a pedido).
     */
    private static void drawBar(GuiGraphics guiGraphics, int x, int y, ResourceLocation backgroundTexture,
                                ResourceLocation fillTexture, ResourceLocation frameTexture,
                                float pct, float[] phaseMarks) {
        pct = Mth_clamp01(pct);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(DISPLAY_SCALE, DISPLAY_SCALE, 1F);

        // Fondo (track vacío) -- textura completa, 1:1 en el espacio ya escalado.
        guiGraphics.blit(backgroundTexture, 0, 0, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

        // Relleno, recortado en ancho de textura (0..TEX_W) según el %.
        int fillWidth = Math.round(TEX_W * pct);
        if (fillWidth > 0) {
            guiGraphics.blit(fillTexture, 0, 0, 0, 0, fillWidth, TEX_H, TEX_W, TEX_H);
        }

        // Marcas de fase (75/50/25%) -- líneas verticales finas sobre el relleno/fondo.
        if (phaseMarks != null) {
            for (float mark : phaseMarks) {
                int markX = Math.round(TEX_W * mark);
                guiGraphics.fill(markX, 2, markX + 1, TEX_H - 2, 0x80000000);
            }
        }

        // Marco por encima de todo -- opcional.
        if (frameTexture != null) {
            guiGraphics.blit(frameTexture, 0, 0, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
        }

        guiGraphics.pose().popPose();
    }

    private static float Mth_clamp01(float v) {
        if (v < 0F) return 0F;
        if (v > 1F) return 1F;
        return v;
    }
}