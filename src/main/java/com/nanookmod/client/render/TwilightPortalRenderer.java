package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nanookmod.NanookMod;
import com.nanookmod.client.model.TwilightPortalModel;
import com.nanookmod.entity.TwilightPortalEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Modelo principal = la base real (star + ring_effects + lightball, con
 * animación de huesos "idle" en loop -- ver TwilightPortalModel).
 *
 * Las 3 piezas "middle_effect*" (small/middle/big) se agregan como render
 * layers aparte porque tienen su propia textura -- GeckoLib no permite
 * más de una textura bindeada por modelo/render. Cada una rota en Z a la
 * velocidad exacta que sacamos de Blockbench (twilight_portal_<capa>
 * .animation.json), con su offset/escala/pivot correspondiente:
 *
 *   small  (middle_effect):  -1080°/3s = -360°/s, sin offset, escala 1
 *   middle (middle_effect2): -720°/3s  = -240°/s, offset Z +7,  escala 2
 *   middle (middle_effect4): -720°/3s  = -240°/s, offset Z +18, escala 2
 *   big    (middle_effect3): -360°/3s  = -120°/s, offset Z +14, escala 3
 *
 * (middle_effect2 y middle_effect4 comparten textura "middle" pero son 2
 * piezas separadas a distinta profundidad -- por eso 2 instancias de la
 * capa en vez de una).
 */
public class TwilightPortalRenderer extends GeoEntityRenderer<TwilightPortalEntity> {

    private static final ResourceLocation SMALL_MODEL =
            new ResourceLocation(NanookMod.MOD_ID, "geo/entity/twilight_portal_small.geo.json");
    private static final ResourceLocation MIDDLE2_MODEL =
            new ResourceLocation(NanookMod.MOD_ID, "geo/entity/twilight_portal_middle2.geo.json");
    private static final ResourceLocation MIDDLE4_MODEL =
            new ResourceLocation(NanookMod.MOD_ID, "geo/entity/twilight_portal_middle4.geo.json");
    private static final ResourceLocation BIG_MODEL =
            new ResourceLocation(NanookMod.MOD_ID, "geo/entity/twilight_portal_big.geo.json");

    private static final ResourceLocation[] SMALL_FRAMES = buildFrames("small");
    private static final ResourceLocation[] MIDDLE_FRAMES = buildFrames("middle");
    private static final ResourceLocation[] BIG_FRAMES = buildFrames("big");

    private static ResourceLocation[] buildFrames(String layer) {
        ResourceLocation[] frames = new ResourceLocation[12];
        for (int i = 0; i < 12; i++) {
            frames[i] = new ResourceLocation(NanookMod.MOD_ID,
                    "textures/entity/twilight_portal/portal_swirl_" + layer + "_frame_" + i + ".png");
        }
        return frames;
    }

    // Pivot común de las 4 piezas middle_effect* (mismo valor en el
    // geo.json para las 4: [0, 2, -4]).
    private static final Vec3 PIVOT = new Vec3(0.0, 2.0, -4.0);

    private static final ResourceLocation STAR_H_MODEL =
            new ResourceLocation(NanookMod.MOD_ID, "geo/entity/twilight_portal_star_h.geo.json");
    private static final ResourceLocation STATIC_TEXTURE = new ResourceLocation(NanookMod.MOD_ID,
            "textures/entity/twilight_portal/portal_static.png");

    // Pivot propio de "star_h" (viene del geo.json base, distinto al de
    // las piezas middle_effect*).
    private static final Vec3 STAR_PIVOT = new Vec3(0.0, 1.5, 0.0);
    // Offset constante que tenía "star" en la animación "idle" de
    // Blockbench (posición fija, no animada en el tiempo) -- originalmente
    // era -7 (mucho más lejos que el resto de las capas, que están entre
    // 0 y 1.5), lo que la hacía ver muy separada del resto del remolino.
    // La acerco al mismo rango.
    private static final Vec3 STAR_OFFSET = new Vec3(0.0, 0.0, 1.0);
    // Pulso de escala real: star_h alterna entre estas 2 poses cada 0.05s
    // en Blockbench (10 Hz = 2 cambios cada 0.1s).
    private static final Vec3 STAR_SCALE_A = new Vec3(3.2, 0.44, 1.0);
    private static final Vec3 STAR_SCALE_B = new Vec3(3.5, 0.4, 1.0);

    public TwilightPortalRenderer(EntityRendererProvider.Context context) {
        super(context, new TwilightPortalModel());
        this.shadowRadius = 0.0F;

        // Orden de dibujado = orden de "más al fondo" a "más al frente".
        // Ojo: la transparencia (entityTranslucentEmissive) no escribe al
        // depth buffer, así que Minecraft NO ordena esto por profundidad
        // real entre capas de distinta textura -- el orden de acá abajo
        // ES el orden final de dibujado, ajustalo si alguna capa se ve
        // tapando a otra que debería estar encima.
        //
        // Copia opaca de BIG (misma textura, mismo geo, misma
        // posición/escala/velocidad que su gemela translúcida de abajo)
        // -- sandwich: opaca justo antes, translúcida justo después,
        // mismo lugar exacto -- para tapar bastante la vista a través del
        // portal sin cambiar el look de siempre. Esto SÍ se mantiene
        // (probado y andando bien).
        //
        // Se intentó agregar el mismo tratamiento a MIDDLE2 para tapar
        // los huecos que BIG sola no cubre, pero se revirtió: costaba
        // demasiado (MIDDLE dejaba de verse de frente en varios ángulos)
        // para un caso -- ver el portal desde atrás -- que en la práctica
        // nadie va a explotar.
        this.addRenderLayer(new TwilightPortalSpinLayer<>(this, BIG_MODEL, BIG_FRAMES, 1,
                new Vec3(0.0, 0.0, 1.5), PIVOT, 1.8, -120.0, true));

        this.addRenderLayer(new TwilightPortalSpinLayer<>(this, BIG_MODEL, BIG_FRAMES, 1,
                new Vec3(0.0, 0.0, 1.5), PIVOT, 1.8, -120.0));

        this.addRenderLayer(new TwilightPortalSpinLayer<>(this, MIDDLE2_MODEL, MIDDLE_FRAMES, 1,
                new Vec3(0.0, 0.0, 0.5), PIVOT, 1.4, -240.0));
        this.addRenderLayer(new TwilightPortalSpinLayer<>(this, MIDDLE4_MODEL, MIDDLE_FRAMES, 1,
                new Vec3(0.0, 0.0, 0.75), PIVOT, 1.4, -240.0));

        this.addRenderLayer(new TwilightPortalSpinLayer<>(this, SMALL_MODEL, SMALL_FRAMES, 1,
                Vec3.ZERO, PIVOT, 1.0, -360.0));

        // "star_h" al final = dibujada por ENCIMA de small (antes era
        // parte del modelo base, que se dibuja siempre primero, así que
        // nunca podía quedar delante de nada -- ver charla). Se dibuja
        // DOS VECES con la misma geometría: una tal cual (rama
        // horizontal) y otra con 90° extra ANTES de escalar (rama
        // vertical) -- así el mismo estiramiento queda orientado bien en
        // cada rama, en vez de que ambas compartan una sola escala mal
        // orientada (era el bug de la rama vertical comprimida en vez de
        // estirada).
        this.addRenderLayer(new TwilightPortalSpinLayer<>(this, STAR_H_MODEL, STATIC_TEXTURE,
                STAR_OFFSET, STAR_PIVOT, STAR_SCALE_A, STAR_SCALE_B, 10.0, 0.0, 0.0));
        this.addRenderLayer(new TwilightPortalSpinLayer<>(this, STAR_H_MODEL, STATIC_TEXTURE,
                STAR_OFFSET, STAR_PIVOT, STAR_SCALE_A, STAR_SCALE_B, 10.0, 90.0, 0.0));
    }

    // GeckoLib le aplica esta rotación a los mobs cuando mueren (el "cae
    // de lado" de vanilla) -- el portal no es un mob normal, no tiene
    // sentido que se incline, así que la apagamos.
    @Override
    public float getDeathMaxRotation(TwilightPortalEntity animatable) {
        return 0.0F;
    }

    // Aplicamos el "hacia dónde mira" nosotros mismos, UNA sola vez acá,
    // en vez de dejar que vanilla/GeckoLib lo saque de getYRot() -- ver
    // TwilightPortalEntity.setFacingYaw() para el motivo. Envuelve TODO
    // el render (base + las 5 capas), así que las capas no necesitan
    // saber nada de esto, siguen rotando/escalando en su propio espacio
    // relativo de siempre.
    @Override
    public void render(TwilightPortalEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        // Segundo intento: "yaw + 180" (el patrón que usan los 3
        // renderers de proyectiles de este mod) NO funcionó -- el portal
        // seguía mirando al revés. El modelo del portal probablemente se
        // armó en Blockbench mirando para el lado opuesto de como se
        // armaron esos proyectiles -- necesita el patrón espejado, igual
        // que NecroticSlashVfxRenderer (180 - yaw en vez de yaw + 180).
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getFacingYaw()));
        // Inclinación (solo la usan los portales flotantes de invocación
        // de wisps -- los de fase se quedan en pitch 0 y esto no hace
        // nada). Eje X, no invertido: con pitch positivo (definido en
        // NecroticKnightEntity#spawnWispPortals como "mirando hacia
        // abajo") el portal se inclina hacia el jugador que está debajo.
        if (entity.getFacingPitch() != 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(entity.getFacingPitch()));
        }

        float scale = computeScale(entity, partialTick);
        if (scale <= 0.001F) {
            poseStack.popPose();
            return; // terminó de achicarse del todo -- nada que dibujar este frame
        }
        poseStack.scale(scale, scale, scale);

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    /**
     * 0 -> 1 al aparecer (usa tickCount, no necesita nada sincronizado -- es
     * "cuántos ticks lleva este cliente viéndola"), y 1 -> 0 al morir (esta
     * sí sincronizada, ver TwilightPortalEntity#isDying/getDyingTicksRemaining
     * -- el discard() real de la entidad se demora DEATH_ANIM_TICKS
     * mientras esto corre, así el achicamiento llega a verse). Mismo
     * smoothstep de siempre para que no sea un crecimiento/achicamiento a
     * velocidad constante (se siente más "vivo" con ease).
     */
    private static float computeScale(TwilightPortalEntity entity, float partialTick) {
        float t;

        if (entity.isDying()) {
            // 1 (recién empieza a morir) -> 0 (a punto de discard()).
            float ticksLeft = entity.getDyingTicksRemaining() + (1.0F - partialTick);
            t = Mth.clamp(ticksLeft / TwilightPortalEntity.DEATH_ANIM_TICKS, 0.0F, 1.0F);
        } else {
            // 0 (recién apareció) -> 1 (terminó de crecer).
            float ticksIn = entity.tickCount + partialTick;
            t = Mth.clamp(ticksIn / TwilightPortalEntity.SPAWN_ANIM_TICKS, 0.0F, 1.0F);
        }

        return t * t * (3.0F - 2.0F * t); // smoothstep -- no crece/achica a velocidad constante
    }

    // Gancho para mover TODO el portal (base + las 3 capas) relativo al
    // hitbox, sin tocar el .geo.json. Ahora mismo es (0,0,0) -- la
    // geometría no tiene offset lateral de fábrica (los pivots de star/
    // ring_effects/lightball están en X=0,Z=0), así que si el modelo se
    // ve corrido del hitbox, este es el lugar más directo para
    // compensarlo a mano. Unidades: bloques (no /16 como en las capas).
    @Override
    public Vec3 getRenderOffset(TwilightPortalEntity entity, float partialTicks) {
        return new Vec3(0.0, 1.5, 0.0); // subido ~la mitad de la hitbox (3.5 de alto)
    }
}