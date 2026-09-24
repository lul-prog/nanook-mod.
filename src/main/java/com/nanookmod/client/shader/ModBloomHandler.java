package com.nanookmod.client.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.nanookmod.NanookMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * PILOTO de bloom real (post-procesado) para el cordón de corrupción --
 * ver charla larga sobre las 2 opciones (capas falsas vs blur real) antes
 * de esto. Si funciona bien, el plan es reusar esta misma infraestructura
 * para el rayo del Knight y el portal más adelante.
 *
 * CÓMO FUNCIONA (3 pasos):
 * 1. CAPTURA: en vez de (o además de) dibujar el cordón directo a
 *    pantalla como hacíamos hasta ahora, lo dibujamos TAMBIÉN en un
 *    framebuffer aparte, en blanco, sin nada más del mundo -- ver
 *    getCaptureTarget().
 * 2. BLUR: le corremos un blur real de 2 pasadas (horizontal + vertical)
 *    a ese framebuffer aparte, usando el shader de blur que ya trae
 *    Minecraft de fábrica (el mismo del fondo desenfocado del menú de
 *    pausa) -- ver necrotic_bloom.json, cero GLSL nuevo escrito a mano
 *    para el blur en sí.
 * 3. COMPOSICIÓN: mezclamos ese resultado difuminado DE VUELTA sobre la
 *    pantalla principal, en modo ADITIVO (sumando brillo en vez de tapar
 *    lo que ya está dibujado) -- ver compositeAdditive() más abajo. ESTA
 *    parte es la más delicada de las 3: son llamadas de bajo nivel a
 *    RenderSystem que son sensibles a la versión exacta de Forge/MC. Si
 *    algo no compila o se ve raro en juego, es acá donde hay que mirar
 *    primero.
 */
public final class ModBloomHandler {

    // OJO: a diferencia de otros sistemas de Minecraft (sonidos, texturas,
    // etc.), PostChain NO arma la ruta "shaders/post/<id>.json" solo a
    // partir de un id corto -- hay que pasarle la ResourceLocation con la
    // ruta COMPLETA nosotros mismos (mismo patrón que usa vanilla para
    // sus propios post-efectos, ej. "shaders/post/underwater.json"). Con
    // solo "necrotic_bloom" tira FileNotFoundException: nanookmod:necrotic_bloom
    // (el mensaje de la excepción es justamente ese id corto tal cual, sin
    // "shaders/post/" ni ".json" -- si alguna vez vuelve a pasar un error
    // así, es este mismo problema).
    private static final ResourceLocation CHAIN_LOCATION =
            new ResourceLocation(NanookMod.MOD_ID, "shaders/post/necrotic_bloom.json");
    private static final String CAPTURE_TARGET_NAME = "necrotic_bloom_capture";

    @Nullable
    private static PostChain chain;
    private static boolean loadFailed = false;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    private ModBloomHandler() {
    }

    /**
     * Se fija que el PostChain esté cargado (lo carga la primera vez que
     * hace falta) y que su tamaño coincida con la ventana actual. Llamar
     * SIEMPRE antes de pedir el capture target o procesar -- si falló la
     * carga (json mal formado, etc.) devuelve false y no hay que
     * intentar nada más ese frame; loguea el error UNA sola vez, no cada
     * frame, para no inundar la consola.
     */
    private static boolean ensureReady(Minecraft mc) {
        if (loadFailed) {
            return false;
        }
        if (chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(),
                        mc.getMainRenderTarget(), CHAIN_LOCATION);
            } catch (IOException e) {
                NanookMod.LOGGER.error("nanookmod: no pude cargar el PostChain de bloom ({}) -- "
                        + "el cordón va a seguir viéndose como antes, sin el brillo extra.", CHAIN_LOCATION, e);
                loadFailed = true;
                return false;
            }
        }

        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        if (width != lastWidth || height != lastHeight) {
            chain.resize(width, height);
            lastWidth = width;
            lastHeight = height;
        }
        return true;
    }

    /**
     * Framebuffer aparte donde hay que dibujar SOLO lo que se quiere que
     * brille (nada del resto del mundo) -- bindWrite(true) antes de
     * dibujar ahí, y acordarse de volver a bindear el render target
     * principal (mc.getMainRenderTarget().bindWrite(false)) apenas se
     * termina, para no dejar el juego dibujando en el lugar equivocado.
     * Devuelve null si el PostChain no cargó bien (ver ensureReady).
     */
    @Nullable
    public static RenderTarget getCaptureTarget(Minecraft mc) {
        if (!ensureReady(mc)) {
            return null;
        }
        return chain.getTempTarget(CAPTURE_TARGET_NAME);
    }

    /**
     * Corre el blur de 2 pasadas sobre lo que se haya dibujado en el
     * capture target este frame, y lo mezcla de vuelta sobre la pantalla
     * en modo aditivo. Llamar UNA vez por frame, después de haber
     * dibujado todo lo que tiene que brillar en el capture target (ver
     * getCaptureTarget) y de haber vuelto a bindear el render target
     * principal.
     */
    public static void process(Minecraft mc, float partialTick) {
        if (!ensureReady(mc)) {
            return;
        }
        chain.process(partialTick);
        // Después de process(), el resultado difuminado queda en el
        // mismo capture target (ver necrotic_bloom.json -- la 2da pasada
        // vuelve a escribir ahí). Lo mezclamos manualmente en vez de
        // dejar que el PostChain escriba directo a "minecraft:main"
        // porque necesitamos que sea ADITIVO (sumar brillo), y el
        // esquema de PostChain de Minecraft no da control directo de eso
        // por pasada.
        RenderTarget blurred = chain.getTempTarget(CAPTURE_TARGET_NAME);
        compositeAdditive(mc, blurred);
    }

    /**
     * Dibuja "source" como un quad de pantalla completa sobre el render
     * target principal, en modo aditivo (GL_ONE, GL_ONE -- suma el color
     * en vez de reemplazarlo u opacar). Réplica reducida de lo que hace
     * RenderTarget#blitToScreen internamente, pero con blending aditivo
     * en vez de la copia opaca que usa ese método (por eso no lo
     * reusamos directo).
     */
    private static void compositeAdditive(Minecraft mc, RenderTarget source) {
        RenderTarget destination = mc.getMainRenderTarget();
        destination.bindWrite(false);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, source.getColorTextureId());

        Matrix4f projection = new Matrix4f().setOrtho(
                0.0F, destination.width, destination.height, 0.0F, 1000.0F, 3000.0F);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.translate(0.0, 0.0, -2000.0);
        RenderSystem.applyModelViewMatrix();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(0.0, destination.height, 0.0).uv(0.0F, 0.0F).endVertex();
        builder.vertex(destination.width, destination.height, 0.0).uv(1.0F, 0.0F).endVertex();
        builder.vertex(destination.width, 0.0, 0.0).uv(1.0F, 1.0F).endVertex();
        builder.vertex(0.0, 0.0, 0.0).uv(0.0F, 1.0F).endVertex();
        tesselator.end();

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();

        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}