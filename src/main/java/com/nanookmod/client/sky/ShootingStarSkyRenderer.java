package com.nanookmod.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nanookmod.NanookMod;
import com.nanookmod.event.FrostedNightHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Reemplaza al sistema de partículas para las estrellas fugaces de la Noche
 * Escarchada. En vez de spawnear algo que se mueve por el mundo (y que por
 * lo tanto puede acabar "cayendo" hacia el jugador o el terreno), esto
 * dibuja quads FIJOS directamente como parte del cielo, igual que hace el
 * mod Confluence con su lluvia de meteoros: cada estrella nace en una
 * orientación aleatoria y ya no se mueve; es la textura (spritesheet
 * animado, ver ShootingStarSkySprite) la que da la sensación de movimiento.
 *
 * Reutiliza assets/nanookmod/textures/particle/shooting_star.png, que ya
 * tiene el formato correcto (7 cuadros de 32x32 apilados).
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ShootingStarSkyRenderer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(NanookMod.MOD_ID, "textures/particle/shooting_star.png");

    // Máximo de estrellas visibles a la vez.
    private static final int MAX_SPRITES = 55;
    // Cada cuántos ticks nace una estrella nueva mientras está activa la
    // Noche Escarchada (8 ticks = poco menos de 1 cada medio segundo).
    // Súbelo/bájalo a tu gusto una vez que confirmes que se ven bien.
    private static final int SPAWN_INTERVAL_TICKS = 4;

    private static final Deque<ShootingStarSkySprite> SPRITES = new ArrayDeque<>();

    // Pon esto en 'true' para que te avise por chat, cada vez que cambia el
    // día, si esa noche tocó o no ser Noche Escarchada. Vuélvelo a 'false'
    // cuando termines de probar.
    private static final boolean DEBUG_LOG_DAILY_ROLL = false;
    private static long lastLoggedDayIndex = Long.MIN_VALUE;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            SPRITES.clear();
            return;
        }

        boolean frostedNight = FrostedNightHandler.isFrostedNight(level);

        if (DEBUG_LOG_DAILY_ROLL) {
            long dayIndex = level.getDayTime() / 24000;
            if (dayIndex != lastLoggedDayIndex) {
                lastLoggedDayIndex = dayIndex;
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                    "[Nanook DEBUG] dia " + dayIndex + " -> Noche Escarchada = " + frostedNight
                            ),
                            false
                    );
                }
            }
        }

        if (frostedNight && level.getGameTime() % SPAWN_INTERVAL_TICKS == 0) {
            if (SPRITES.size() >= MAX_SPRITES) {
                SPRITES.poll(); // se descarta la más vieja para dejar entrar la nueva
            }
            SPRITES.add(new ShootingStarSkySprite(level.random));
        }

        Iterator<ShootingStarSkySprite> it = SPRITES.iterator();
        while (it.hasNext()) {
            ShootingStarSkySprite sprite = it.next();
            sprite.tick();
            if (sprite.isFinished()) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderSky(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (SPRITES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !FrostedNightHandler.isFrostedNight(level)) return;

        PoseStack poseStack = event.getPoseStack();

        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        poseStack.pushPose();

        // Se ven un poco menos si está lloviendo, igual que el sol/la luna.
        float rainFactor = Math.max(1.0F - level.getRainLevel(event.getPartialTick()), 0.2F);

        for (ShootingStarSkySprite sprite : SPRITES) {
            sprite.addToBuffer(builder, poseStack, rainFactor);
        }

        poseStack.popPose();
        tesselator.end();

        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }
}