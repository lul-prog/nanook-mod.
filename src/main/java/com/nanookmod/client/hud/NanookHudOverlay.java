package com.nanookmod.client.hud;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.NanookEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Bossbar custom de Nanook: nombre + UNA barra de vida. Nada más -- a
 * diferencia del NecroticKnight (que apila vida + Corrupción + Drenaje
 * Vital), Nanook no tiene recursos secundarios que mostrar.
 *
 * No usa la bossbar vanilla para nada (ver NanookEntity:
 * bossEvent.setVisible(false)). Lee getHealth()/getMaxHealth() DIRECTO de la
 * entidad -- eso ya viaja sincronizado por vanilla, no hace falta duplicarlo
 * en un paquete. Lo único que manda el servidor por red es a QUÉ entidad
 * mirar (SyncNanookHudPacket -> NanookHudTracker).
 *
 * TEXTURAS QUE TENÉS QUE CREAR (300x20 px cada una, mismo tamaño que las del
 * Knight para poder reusar el mismo flujo de diseño):
 *   assets/nanookmod/textures/gui/bossbar/nanook_bar_background.png
 *   assets/nanookmod/textures/gui/bossbar/nanook_bar_fill.png
 *   assets/nanookmod/textures/gui/bossbar/nanook_bar_frame.png
 *
 * Mientras no existan, el juego dibuja el cuadrito negro/violeta de textura
 * faltante -- no crashea. Si querés probar YA sin hacer texturas nuevas,
 * apuntá TEX_BACKGROUND/TEX_FILL/TEX_FRAME a las del Knight
 * (bar_background.png / bar_fill_health.png / bar_frame.png).
 */
public class NanookHudOverlay implements IGuiOverlay {

    private static final ResourceLocation TEX_BACKGROUND =
            new ResourceLocation(NanookMod.MOD_ID, "textures/gui/bossbar/nanook_bar_background.png");
    private static final ResourceLocation TEX_FILL =
            new ResourceLocation(NanookMod.MOD_ID, "textures/gui/bossbar/nanook_bar_fill.png");
    private static final ResourceLocation TEX_FRAME =
            new ResourceLocation(NanookMod.MOD_ID, "textures/gui/bossbar/nanook_bar_frame.png");

    // Celeste hielo -- la paleta de Nanook (el YAML original usa
    // '<bold><aqua>Nanook' para el título de su bossbar).
    private static final int NAME_COLOR = 0x8FE3F5;
    private static final int NAME_GAP = 2;

    // --- Tamaño de diseño de las texturas (píxeles reales del PNG) ---
    private static final int TEX_W = 300;
    private static final int TEX_H = 20;

    // --- A qué tamaño se muestran en pantalla ---
    private static final float DISPLAY_SCALE = 0.62F;
    private static final int DISPLAY_W = (int) (TEX_W * DISPLAY_SCALE);
    private static final int DISPLAY_H = (int) (TEX_H * DISPLAY_SCALE);

    private static final int TOP_MARGIN = 14;

    // Marcas de las fases de Nanook (75/50/25% -- ver
    // NanookEntity.getPhaseIndex). A diferencia del Knight, acá SÍ se
    // dibujan: las fases de Nanook cambian su ritmo de ataque de forma
    // notoria, así que vale la pena que el jugador vea dónde están los
    // cortes. Pasá null en el drawBar de abajo si preferís sacarlas.
    private static final float[] PHASE_MARKS = {0.75F, 0.50F, 0.25F};

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics,
                       float partialTick, int screenWidth, int screenHeight) {
        if (!NanookHudTracker.hasActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(NanookHudTracker.getActiveEntityId());
        if (!(entity instanceof NanookEntity nanook) || !entity.isAlive()) {
            return;
        }

        int centerX = screenWidth / 2;
        int y = TOP_MARGIN;

        // --- Nombre, centrado arriba de la barra ---
        Component name = nanook.getDisplayName();
        int nameWidth = mc.font.width(name);
        guiGraphics.drawString(mc.font, name, centerX - nameWidth / 2, y, NAME_COLOR, true);
        y += mc.font.lineHeight + NAME_GAP;

        // --- Barra de vida (la única) ---
        float healthPct = nanook.getMaxHealth() > 0
                ? clamp01(nanook.getHealth() / nanook.getMaxHealth())
                : 0F;
        drawBar(guiGraphics, centerX - DISPLAY_W / 2, y, healthPct, PHASE_MARKS);
    }

    /**
     * Fondo + relleno recortado según pct + marco, todo escalado a
     * DISPLAY_W x DISPLAY_H con una matriz -- así se ve nítido sin importar
     * la resolución/gui scale del jugador.
     */
    private static void drawBar(GuiGraphics guiGraphics, int x, int y, float pct, float[] phaseMarks) {
        pct = clamp01(pct);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(DISPLAY_SCALE, DISPLAY_SCALE, 1F);

        // Fondo (track vacío).
        guiGraphics.blit(TEX_BACKGROUND, 0, 0, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

        // Relleno, recortado en ancho de textura (0..TEX_W) según el %.
        int fillWidth = Math.round(TEX_W * pct);
        if (fillWidth > 0) {
            guiGraphics.blit(TEX_FILL, 0, 0, 0, 0, fillWidth, TEX_H, TEX_W, TEX_H);
        }

        // Marcas de fase -- líneas verticales finas sobre el relleno/fondo.
        if (phaseMarks != null) {
            for (float mark : phaseMarks) {
                int markX = Math.round(TEX_W * mark);
                guiGraphics.fill(markX, 2, markX + 1, TEX_H - 2, 0x80000000);
            }
        }

        // Marco por encima de todo.
        guiGraphics.blit(TEX_FRAME, 0, 0, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

        guiGraphics.pose().popPose();
    }

    private static float clamp01(float v) {
        if (v < 0F) return 0F;
        if (v > 1F) return 1F;
        return v;
    }
}
