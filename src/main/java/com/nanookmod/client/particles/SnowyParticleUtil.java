package com.nanookmod.client.particles;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;

/**
 * Las dos correcciones que comparten TODAS las partículas de nieve de
 * Nanook (snowy_dust, snowy_ring, snowy_wind_ring).
 *
 * ===================================================================
 * PROBLEMA 1 -- "en el rugido, al final la partícula se ve GRIS"
 * ===================================================================
 * Las texturas son blanco puro (RGB 255 en los 9 cuadros, se verificó
 * píxel por píxel), así que el gris NO viene del arte. Viene de la LUZ.
 *
 * Una partícula se pinta multiplicada por el lightmap de la posición donde
 * está. Con hasPhysics = false la nube atraviesa el terreno, y en cuanto
 * su centro queda dentro de un bloque opaco el motor de luz devuelve 0:
 * la partícula se oscurece. El rugido es el caso más claro porque dibuja
 * el anillo casi a ras de piso (y con velocidad hacia abajo), así que
 * justo los ÚLTIMOS ticks de vida -- cuando ya se hundió -- salen grises.
 * De noche (Noche Escarchada) pasa lo mismo aunque no estén hundidas:
 * lightmap bajo = blanco apagado.
 *
 * Solución: {@link #brightLight}. Si el centro quedó dentro de un bloque
 * sólido, sube hasta salir del bloque para muestrear la luz del aire de
 * encima, y además impone un piso de luz de bloque para que la nieve se
 * lea blanca aun de noche o bajo un techo.
 *
 * ===================================================================
 * PROBLEMA 2 -- "cuando unas se solapan con otras se ven transparentes"
 * ===================================================================
 * El render type vanilla PARTICLE_SHEET_TRANSLUCENT deja activa la
 * ESCRITURA DE PROFUNDIDAD. El shader de partículas descarta solo los
 * píxeles con alfa < 0.1, así que el halo suave de una nube (alfa 0.15,
 * 0.3...) SÍ escribe profundidad aunque sea casi invisible. Cualquier
 * nube dibujada después y que esté DETRÁS de esa halo pierde esos píxeles:
 * se ve un "recorte" a través del cual aparece el fondo. Eso es lo que se
 * leía como que se ponen transparentes al cruzarse.
 *
 * Solución: {@link #TRANSLUCENT_NO_DEPTH}, idéntico al vanilla pero con
 * depthMask(false). Sigue haciendo test de profundidad (el terreno y las
 * paredes las siguen tapando bien), solo que ya no se tapan entre sí.
 * Como todas son blancas, el orden de dibujo entre ellas es irrelevante.
 */
public final class SnowyParticleUtil {

    private SnowyParticleUtil() {
    }

    /**
     * Piso de luz de bloque (0..15). 15 = blanco puro siempre, como una
     * partícula emisiva. Si de noche te parece que brillan demasiado, bajalo
     * a 11-12: se ven blancas de día y algo apagadas de noche.
     */
    public static final int MIN_BLOCK_LIGHT = 15;

    /** Como PARTICLE_SHEET_TRANSLUCENT de vanilla, pero SIN escribir profundidad. */
    public static final ParticleRenderType TRANSLUCENT_NO_DEPTH = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
        }

        @Override
        public String toString() {
            return "NANOOK_SNOWY_TRANSLUCENT_NO_DEPTH";
        }
    };

    /**
     * Luz empaquetada para una partícula de nieve en (x, y, z).
     * Ver PROBLEMA 1 arriba.
     */
    public static int brightLight(ClientLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y, z).mutable();
        if (!level.hasChunkAt(pos)) {
            return LightTexture.pack(MIN_BLOCK_LIGHT, 0);
        }

        // Hundida en el terreno: subir hasta el aire (máx. 4 bloques, por si
        // quedó enterrada bajo una pendiente).
        for (int i = 0; i < 4 && level.getBlockState(pos).isSolidRender(level, pos); i++) {
            pos.move(0, 1, 0);
        }

        int packed = LevelRenderer.getLightColor(level, pos);
        int block = Math.max(LightTexture.block(packed), MIN_BLOCK_LIGHT);
        int sky = LightTexture.sky(packed);
        return LightTexture.pack(block, sky);
    }
}
