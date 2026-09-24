package com.nanookmod.client.render;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nanookmod.NanookMod;
import com.nanookmod.client.shader.ModShaders;
import com.nanookmod.entity.NecroticKnightEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Dibuja el ataque Wave del NecroticKnight como un anillo de energía real
 * en el suelo -- reemplaza el anillo de partículas que dibujaba el
 * servidor en tickWave() (ver NecroticKnightEntity).
 *
 * CAMBIO DE ENFOQUE (ver charla): las primeras 2 vueltas armaban un solo
 * plano gigante y le pedían al FRAGMENT SHADER que calculara "distancia
 * al jefe" para decidir qué píxeles pintar como anillo -- eso terminó
 * siguiendo a la cámara de maneras que no pude reproducir sin poder
 * compilar/correr el juego yo mismo. Ahora la forma del anillo (radio
 * actual, grosor, degradado suave en los bordes) es geometría de verdad,
 * construida acá con trigonometría común y corriente y la MISMA técnica
 * de posicionamiento ya probada en TwilightWispTrailRenderer (sin
 * poseStack.translate(), sumando a mano el offset relativo a cámara). El
 * shader (necrotic_wave.vsh/.fsh) ya no tiene que "encontrar" nada en el
 * mundo -- solo le agrega un pulso de brillo parejo (shimmer) a la
 * geometría que ya está bien puesta en su lugar.
 *
 * Cuando la onda entra en su fase de desvanecido (ver
 * NecroticWaveTracker), el anillo baja de alpha 1 a 0 en vez de cortar
 * de golpe -- eso es TODO lo que pasa en esa fase, no hay ningún efecto
 * extra de "evaporación" (se probó con unas motas de geometría alrededor
 * del anillo y se sacó por pedido explícito).
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class NecroticWaveRenderer {

    private static final int SEGMENTS = 64;

    // Mismo cian-fuego que usan las auras del warrior/mage/knight (ver
    // NecroticAuraRenderLayer -> aura_frame_N.png, color sampleado de la
    // textura real; coincide exactamente con TwilightWispAuraRenderLayer).
    private static final float COLOR_R = 0.6118F;
    private static final float COLOR_G = 0.9647F;
    private static final float COLOR_B = 0.8706F;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        ShaderInstance shader = ModShaders.getNecroticWaveShader();
        if (shader == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof NecroticKnightEntity knight)) {
                continue;
            }
            // Ahora puede haber VARIAS ondas activas a la vez para el mismo
            // jefe (una por cada sacudida del forcejeo) -- dibujamos un
            // anillo por cada una.
            for (NecroticWaveTracker.PulseVisual pulse : NecroticWaveTracker.getActivePulseVisuals(knight, event.getPartialTick())) {
                drawWaveRing(knight, pulse.radius(), pulse.alpha(), shader, event);
            }
        }
    }

    private static void drawWaveRing(NecroticKnightEntity knight, double radius, float fadeAlpha,
                                     ShaderInstance shader, RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        double halfBand = NecroticKnightEntity.WAVE_BAND_WIDTH / 2.0D;
        double innerR = Math.max(0.0D, radius - halfBand);
        double midR = radius;
        double outerR = radius + halfBand;

        double cx = knight.getX() - camPos.x;
        // +0.05 para no pelearse por z-fighting con el bloque del suelo.
        double cy = knight.getY() - camPos.y + 0.05D;
        double cz = knight.getZ() - camPos.z;

        // Igual que TwilightWispTrailRenderer (probado y funcionando): SIN
        // poseStack.translate(), la matriz del evento tal cual viene, y el
        // offset relativo a cámara sumado a mano en cada vértice.
        PoseStack poseStack = event.getPoseStack();
        Matrix4f mat = poseStack.last().pose();

        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        // Ver AuroraSkyRenderer/TwilightWispTrailRenderer: mismo motivo,
        // geometría fina vista desde cualquier ángulo necesita esto o
        // media onda (o toda) se descarta por culling.
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        Uniform gameTimeUniform = shader.getUniform("GameTime");
        if (gameTimeUniform != null) {
            gameTimeUniform.set((mc.level.getGameTime() % 1_000_000L) / 20.0f + event.getPartialTick());
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = (Math.PI * 2.0D * i) / SEGMENTS;
            double a1 = (Math.PI * 2.0D * (i + 1)) / SEGMENTS;

            // Banda interior: alpha 0 (borde) -> 1 (radio real, en el medio).
            // Banda exterior: alpha 1 (radio real) -> 0 (borde) -- juntas
            // dan un degradado suave completo, sin canto filoso, sin
            // necesitar ninguna cuenta de "distancia" en el shader.
            // fadeAlpha multiplica todo el anillo: 1.0 mientras expande,
            // y baja a 0 durante la fase de desvanecido (ver
            // NecroticWaveTracker) en vez de que el anillo corte de golpe.
            addBandQuad(builder, mat, cx, cy, cz, a0, a1, innerR, midR, 0.0F, fadeAlpha);
            addBandQuad(builder, mat, cx, cy, cz, a0, a1, midR, outerR, fadeAlpha, 0.0F);
        }

        tesselator.end();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private static void addBandQuad(BufferBuilder builder, Matrix4f mat,
                                    double cx, double cy, double cz,
                                    double a0, double a1, double r0, double r1,
                                    float alphaAtR0, float alphaAtR1) {
        float x00 = (float) (cx + Math.cos(a0) * r0);
        float z00 = (float) (cz + Math.sin(a0) * r0);
        float x01 = (float) (cx + Math.cos(a0) * r1);
        float z01 = (float) (cz + Math.sin(a0) * r1);
        float x11 = (float) (cx + Math.cos(a1) * r1);
        float z11 = (float) (cz + Math.sin(a1) * r1);
        float x10 = (float) (cx + Math.cos(a1) * r0);
        float z10 = (float) (cz + Math.sin(a1) * r0);

        float y = (float) cy;

        builder.vertex(mat, x00, y, z00).color(COLOR_R, COLOR_G, COLOR_B, alphaAtR0).endVertex();
        builder.vertex(mat, x01, y, z01).color(COLOR_R, COLOR_G, COLOR_B, alphaAtR1).endVertex();
        builder.vertex(mat, x11, y, z11).color(COLOR_R, COLOR_G, COLOR_B, alphaAtR1).endVertex();
        builder.vertex(mat, x10, y, z10).color(COLOR_R, COLOR_G, COLOR_B, alphaAtR0).endVertex();
    }
}