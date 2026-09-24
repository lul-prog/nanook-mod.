package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nanookmod.NanookMod;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;

/**
 * RenderType a medida: copia EXACTA de RenderType.entityTranslucentEmissive
 * (mismo shader -- sin sombreado por dirección de cara, brilla parejo de
 * día/noche --, mismo blending translúcido que respeta el degradé de alfa
 * real de la textura, sin culling, mismo lightmap/overlay) salvo por UNA
 * cosa: el writeMaskState. Vanilla usa COLOR_WRITE (no graba profundidad)
 * a propósito, para que varias capas translúcidas se puedan superponer sin
 * z-fighting entre sí. Acá lo pisamos por COLOR_DEPTH_WRITE -- así esta
 * capa SÍ graba en el depth buffer y se ocluye correctamente contra
 * cualquier otra cosa según la distancia real a la cámara, en vez de
 * depender del orden de dibujado.
 *
 * Extraído de NecroticAuraRenderLayer (que lo usaba solo para el aura) para
 * que NecroticKnightGlowLayer lo reuse tal cual -- ver el historial de esa
 * clase para el detalle completo de por qué se llegó a esta versión
 * (v2 aditivo, v3 sin depth write, v4 con sombreado por cara -- todas
 * probadas y descartadas antes de esta).
 *
 * Los "ingredientes" son campos protected/private adentro de RenderType --
 * vanilla nunca pensó que un mod quisiera un writeMaskState distinto para
 * un RenderType ya existente, así que no hay un método público para
 * "clonar entityTranslucentEmissive pero con otro writeMask". Los leemos
 * por reflexión UNA sola vez (quedan cacheados en los campos de abajo, no
 * hay reflexión por frame). Si algún día una versión de Forge renombra
 * alguno de estos campos, readRenderTypeField devuelve null en vez de
 * explotar, y get() cae de nuevo al entityTranslucentEmissive de siempre
 * (vuelve el bug de "atraviesa cosas transparentes", pero el juego no
 * crashea).
 */
final class GlowingDepthWriteRenderType {

    private static final RenderStateShard.ShaderStateShard SHADER =
            readRenderTypeField("RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER");
    private static final RenderStateShard.TransparencyStateShard TRANSPARENCY =
            readRenderTypeField("TRANSLUCENT_TRANSPARENCY");
    private static final RenderStateShard.CullStateShard NO_CULL =
            readRenderTypeField("NO_CULL");
    private static final RenderStateShard.LightmapStateShard LIGHTMAP =
            readRenderTypeField("LIGHTMAP");
    private static final RenderStateShard.OverlayStateShard OVERLAY =
            readRenderTypeField("OVERLAY");
    private static final RenderStateShard.WriteMaskStateShard COLOR_DEPTH_WRITE =
            readRenderTypeField("COLOR_DEPTH_WRITE");

    private static final boolean AVAILABLE = SHADER != null && TRANSPARENCY != null
            && NO_CULL != null && LIGHTMAP != null && OVERLAY != null && COLOR_DEPTH_WRITE != null;

    private GlowingDepthWriteRenderType() {
    }

    @SuppressWarnings("unchecked")
    private static <T> T readRenderTypeField(String fieldName) {
        try {
            Field field = RenderType.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(null);
        } catch (ReflectiveOperationException e) {
            NanookMod.LOGGER.warn("nanookmod: no pude leer RenderType.{} por reflexión -- "
                            + "esta capa emissive vuelve a entityTranslucentEmissive normal. {}",
                    fieldName, e.toString());
            return null;
        }
    }

    static RenderType get(ResourceLocation texture) {
        if (!AVAILABLE) {
            return RenderType.entityTranslucentEmissive(texture);
        }
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setWriteMaskState(COLOR_DEPTH_WRITE)
                .createCompositeState(true);
        return RenderType.create("nanookmod_glow_translucent_depth_write",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true, state);
    }
}