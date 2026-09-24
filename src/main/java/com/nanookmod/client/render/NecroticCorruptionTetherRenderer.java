package com.nanookmod.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nanookmod.NanookMod;
import com.nanookmod.client.shader.ModBloomHandler;
import com.nanookmod.client.shader.ModShaders;
import com.nanookmod.entity.NecroticKnightEntity;
import com.nanookmod.entity.TwilightPortalEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dibuja el cordón de energía espectral entre cada TwilightPortalEntity y
 * el NecroticKnightEntity que lo abrió (ver
 * NecroticKnightEntity#openPortalsNearAllPlayers /
 * TwilightPortalEntity#getOwnerKnightId) -- parte del sistema de
 * Corrupción ("Pacto Necromántico").
 *
 * MISMA TÉCNICA que NecroticWaveRenderer/TwilightWispTrailRenderer, a
 * propósito: nada de calcular posiciones/distancias adentro del shader
 * (fragment shader), TODA la forma real (curva del cordón, ancho,
 * billboard hacia cámara) es geometría de verdad armada acá en Java con
 * vectores comunes -- reusamos directamente necrotic_wave.vsh/.fsh (ver
 * ModShaders) porque ya hace exactamente lo que necesitamos: pintar la
 * geometría que le pasamos con un shimmer parejo, sin tener que escribir
 * ni un shader nuevo.
 *
 * Cada segmento del cordón es un billboard orientado a cámara (igual que
 * TwilightWispTrailRenderer -- ver el comentario largo en esa clase sobre
 * por qué hace falta RenderSystem.disableCull() con esta técnica).
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class NecroticCorruptionTetherRenderer {

    private static final int SEGMENTS = 24;

    // Núcleo brillante del cordón. #bcffc5
    private static final float CORE_R = 0.7373F;
    private static final float CORE_G = 1.0F;
    private static final float CORE_B = 0.7725F;
    private static final float CORE_WIDTH = 0.05F; // más fino a pedido (antes 0.07F)
    private static final float CORE_ALPHA = 0.9F;

    // Halo exterior. #62f694
    private static final float GLOW_R = 0.3843F;
    private static final float GLOW_G = 0.9647F;
    private static final float GLOW_B = 0.5804F;
    private static final float GLOW_WIDTH = 0.16F; // más fino a pedido (antes 0.22F)
    private static final float GLOW_ALPHA = 0.5F;

    // --- Chispas cerca del anclaje del portal ---
    // Líneas rectas bien finitas que nacen pegadas al portal, avanzan un
    // trecho corto SIGUIENDO la curva real del cordón en ese momento (no
    // una línea recta "de mentira" -- reusan computeCenterPoint, la misma
    // función que arma el cordón principal, así que si el cordón está
    // ondulando/respirando, las chispas ondulan exactamente igual) y se
    // desvanecen. Puramente cosmético/cliente, igual que el resto de los
    // efectos de este archivo -- no hace falta sincronizar nada porque
    // cada chispa nace con una semilla determinística (portal id + "slot"
    // de tiempo), así que todos los clientes generan la misma.
    private static final double SPARK_SPAWN_INTERVAL_TICKS = 5.0D;  // una chispa nueva cada ~0.25s por portal
    private static final double SPARK_LIFETIME_TICKS = 9.0D;        // vida total de la mayoría (~0.45s)
    private static final double SPARK_BASE_T_MAX = 0.035D;          // nacen bien pegadas al portal (t chico, 0 = portal)
    private static final double SPARK_TRAVEL_T_MIN = 0.08D;         // cuánto del cordón recorren como mínimo (chispas normales)
    private static final double SPARK_TRAVEL_T_MAX = 0.16D;         // ...y como máximo (chispas normales)
    private static final double SPARK_HALF_LEN_T = 0.018D;          // qué tan cortas son (medio largo, en fracción t)

    // Algunas (no todas) son "largas" y llegan bastante más lejos, casi a
    // mitad de camino del cordón -- necesitan más vida para que dé tiempo a
    // ver el recorrido largo (si no, se sentirían como un teletransporte).
    private static final double SPARK_FAR_CHANCE = 0.15D;            // ~1 de cada 6-7 chispas es "larga"
    private static final double SPARK_FAR_TRAVEL_T_MIN = 0.28D;
    private static final double SPARK_FAR_TRAVEL_T_MAX = 0.50D;
    private static final double SPARK_FAR_LIFETIME_TICKS = 18.0D;    // el doble, aprox (~0.9s)

    // Antes era un offset simétrico (podía salir casi 0, o sea "adentro" del
    // cordón) -- ahora es un radio polar con mínimo garantizado, así que
    // SIEMPRE nacen por fuera del halo (GLOW_WIDTH/2 = 0.08) con margen,
    // nunca superpuestas al cordón mismo.
    private static final double SPARK_JITTER_MIN_RADIUS = 0.14D;
    private static final double SPARK_JITTER_MAX_RADIUS = 0.26D;

    private static final float SPARK_WIDTH = 0.045F; // bien finitas, más que el núcleo del cordón (CORE_WIDTH)
    private static final float SPARK_R = 0.85F; // más blanquecino que el núcleo, para que resalten como "energía nueva"
    private static final float SPARK_G = 1.0F;
    private static final float SPARK_B = 0.92F;
    private static final float SPARK_ALPHA = 1.0F;

    // --- Nodo de anclaje en el portal ---
    // Un pequeño "asterisco" de rayitos cortos, centrado justo donde el
    // cordón toca el portal, girando lento y pulsando en sincronía con el
    // mismo pulso de brillo que viaja por el cordón (pulseBoostAt(0, ...))
    // -- da la sensación de que ahí es donde "nace" la energía. Reusa
    // exactamente la misma técnica de ribbon que el resto del archivo, sin
    // texturas ni shaders nuevos.
    private static final int ANCHOR_SPOKE_COUNT = 6;
    private static final double ANCHOR_SPOKE_LENGTH = 0.16D; // bloques
    private static final double ANCHOR_SPIN_SPEED = 0.6D; // rad/seg, rotación lenta
    private static final float ANCHOR_WIDTH = 0.05F;
    private static final float ANCHOR_R = 0.7373F; // mismo verde-blanquecino que el núcleo del cordón
    private static final float ANCHOR_G = 1.0F;
    private static final float ANCHOR_B = 0.7725F;
    private static final float ANCHOR_ALPHA = 0.85F;

    private record Spark(double spawnTick, double baseT, double travelT, double axisJitter, double upJitter,
                         double lifetimeTicks) {
    }

    private static final Map<Integer, List<Spark>> SPARKS_BY_PORTAL = new HashMap<>();
    private static final Map<Integer, Long> LAST_SPARK_SLOT_BY_PORTAL = new HashMap<>();

    // Ondulación del cordón (para que no sea una línea recta rígida). Se
    // apaga cerca de las puntas (ver taper en drawTether) para que quede
    // bien pegado al portal y al jefe, no flotando al costado de ellos.
    // AMPLITUDE ahora es un RANGO -- cada portal saca su propio valor
    // dentro de ese rango (ver ampScale en pickPortalProfile), no todos
    // ondulan igual de fuerte.
    private static final double WAVE_AMPLITUDE_MIN = 0.22D;
    private static final double WAVE_AMPLITUDE_MAX = 0.46D;
    private static final double WAVE_FREQUENCY = 1.8D; // ondas a lo largo de todo el cordón
    private static final double WAVE_SPEED = 3.2D;

    // Perfil ARC: un solo arco vertical (sube como si pasara por encima de
    // una montaña, después baja al jefe) en vez de la ondulación horizontal
    // de varios ciclos de WAVE. Altura en RANGO, no fija -- cada portal
    // saca la suya (ver ampScale). Además lleva un "respiro" lento (ver
    // BREATHE_*) para que la altura del arco no quede congelada en el
    // tiempo, y un poquito de ondulación horizontal de acompañamiento.
    private static final double ARC_HEIGHT_MIN = 0.5D;
    private static final double ARC_HEIGHT_MAX = 2.8D;
    private static final double ARC_SIDE_WAVE_SCALE = 0.4D; // fracción de la amplitud de WAVE

    // Perfil ZIGZAG: quiebres angulares (onda triangular) en el plano
    // VERTICAL -- antes iba en el plano horizontal (waveAxis), y por eso
    // solo se notaba mirando el cordón desde arriba; parado en el piso,
    // el vaivén de lado a lado prácticamente no se ve. En vertical, el
    // quiebre se nota clarísimo desde cualquier ángulo normal de juego.
    // Frecuencia y velocidad bajadas respecto al primer intento -- se
    // veía demasiado rápido/marcado.
    private static final double ZIGZAG_FREQUENCY = 1.6D; // quiebres a lo largo de todo el cordón
    private static final double ZIGZAG_SPEED = 0.8D;

    // "Respiración": variación lenta y suave de la amplitud/altura de
    // CUALQUIER forma a lo largo del tiempo, para que ninguna (ni
    // siquiera ARC, que si no queda como una forma fija sin vida) se vea
    // estática -- el pulso de brillo viajero (pulseBoostAt) ya daba algo
    // de esto, pero era solo brillo, no movimiento real de la curva.
    private static final double BREATHE_SPEED = 0.6D; // ciclos por segundo, aprox
    private static final double BREATHE_DEPTH = 0.18D; // ±18% sobre la amplitud/altura base

    // Cada portal tiene su propia amplitud/altura (dentro de los rangos de
    // arriba) y su propia fase de tiempo (para que dos portales con la
    // MISMA forma no ondulen/respiren exactamente sincronizados) -- ver
    // pickPortalProfile.
    private static final double PORTAL_PROFILE_PHASE_RANGE = Math.PI * 2.0D;

    // Pulso de brillo viajando del portal hacia el jefe -- vende la idea
    // de "energía siendo absorbida" en vez de un cordón estático.
    private static final double PULSE_SPEED = 0.5D; // vueltas por segundo a lo largo del cordón
    private static final double PULSE_WIDTH = 0.16D; // ancho del bulto brillante, fracción 0..1 del largo
    private static final float PULSE_BOOST = 2.0F;

    // Cuánto tarda el cordón en "crecer" desde el portal hasta el jefe
    // apenas se abre el portal, en vez de aparecer de golpe a full largo.
    // Usa portal.tickCount (ticks desde que el portal se creó) -- mismo
    // truco que ya usamos para la ventana de gracia de Corrupción, no
    // hace falta trackear nada aparte.
    private static final float GROWTH_DURATION_TICKS = 30.0F; // 1.5s

    // Antes TODOS los cordones enganchaban al jefe exactamente a la misma
    // altura (knight.getBbHeight() * 0.65, fijo) -- con varios portales
    // alrededor se veía repetitivo/artificial, todas las líneas
    // convergiendo en el mismo punto. Ahora cada portal engancha un poco
    // más arriba o más abajo de ese punto base, según su propio ID (ver
    // perPortalOffset) -- 0.5 acá son ±0.5 bloques de variación posible.
    private static final double KNIGHT_ATTACH_HEIGHT_VARIATION = 0.5D;

    /**
     * Distintas "formas" que puede tomar el cordón, elegida por portal
     * (ver pickShape) -- así no todos los cordones tienen la misma pinta
     * cuando hay varios portales abiertos a la vez.
     */
    private enum TetherShape {
        WAVE,   // ondulación suave horizontal, el comportamiento original
        ARC,    // sube como un arco/montaña y después baja al jefe
        ZIGZAG  // quiebres angulares en vez de curva suave
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
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

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        float partialTick = event.getPartialTick();
        float gameTimeTicks = (level.getGameTime() % 1_000_000L) + partialTick;

        PoseStack poseStack = event.getPoseStack();
        Matrix4f matrix = poseStack.last().pose();

        // Pasada 1: dibujo normal, a la pantalla de siempre -- esto es
        // EXACTAMENTE lo que había antes de agregar el bloom, nada
        // cambia acá si el PostChain de más abajo llegara a fallar.
        boolean drewAnything = renderAllTethers(shader, mc.getMainRenderTarget(), matrix, cameraPos,
                level, partialTick, gameTimeTicks);

        if (!drewAnything) {
            return;
        }

        // Pasada 2 (BLOOM) -- desactivada a pedido, no convenció el look.
        // El código queda comentado (no borrado) por si más adelante se
        // quiere retomar con otros parámetros de blur/composición. Ver
        // ModBloomHandler para el detalle completo de cómo funciona.
        /*
        RenderTarget captureTarget = ModBloomHandler.getCaptureTarget(mc);
        if (captureTarget != null) {
            captureTarget.clear(Minecraft.ON_OSX);
            captureTarget.bindWrite(false);

            renderAllTethers(shader, captureTarget, matrix, cameraPos, level, partialTick, gameTimeTicks);

            mc.getMainRenderTarget().bindWrite(false);
            ModBloomHandler.process(mc, partialTick);
        }
        */
    }

    /**
     * Dibuja el cordón de TODOS los pares portal-jefe activos sobre el
     * render target indicado (bindéalo VOS antes de llamar esto -- este
     * método no bindea nada, solo dibuja con lo que esté bindeado en ese
     * momento). Devuelve true si dibujó al menos un cordón.
     */
    private static boolean renderAllTethers(ShaderInstance shader, RenderTarget target, Matrix4f matrix,
                                            Vec3 cameraPos, ClientLevel level, float partialTick,
                                            float gameTimeTicks) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        boolean began = false;

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof TwilightPortalEntity portal)) {
                continue;
            }
            int ownerId = portal.getOwnerKnightId();
            if (ownerId < 0) {
                continue;
            }
            if (!(level.getEntity(ownerId) instanceof NecroticKnightEntity knight) || !knight.isAlive()) {
                continue;
            }

            if (!began) {
                RenderSystem.depthMask(false);
                RenderSystem.enableBlend();
                RenderSystem.disableCull();
                RenderSystem.setShader(() -> shader);

                Uniform gameTimeUniform = shader.getUniform("GameTime");
                if (gameTimeUniform != null) {
                    gameTimeUniform.set(gameTimeTicks / 20.0f);
                }

                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                began = true;
            }

            PortalProfile profile = pickPortalProfile(portal.getId());

            // "Atrás" = dirección opuesta a hacia dónde mira el portal
            // (vector de mirada vanilla: forward = (-sin(yaw), cos(yaw)),
            // así que atrás es el opuesto de eso).
            float yawRad = (float) Math.toRadians(portal.getFacingYaw());
            double backX = Math.sin(yawRad) * PORTAL_ATTACH_BACK_OFFSET;
            double backZ = -Math.cos(yawRad) * PORTAL_ATTACH_BACK_OFFSET;

            Vec3 portalPos = portal.getPosition(partialTick)
                    .add(0, portal.getBbHeight() * PORTAL_ATTACH_HEIGHT_FRACTION, 0)
                    .add(backX, 0, backZ);
            Vec3 knightPos = knight.getPosition(partialTick)
                    .add(0, knight.getBbHeight() * 0.65D + profile.attachHeightOffset(), 0);

            float growthFraction = Math.min(1.0F, (portal.tickCount + partialTick) / GROWTH_DURATION_TICKS);

            // Portal muriendo (isDying(), ver TwilightPortalEntity -- el
            // discard() real se demora DEATH_ANIM_TICKS a propósito para
            // que esto se vea): en vez de cortar el cordón de golpe apenas
            // el portal desaparece, lo "retiramos" desde el lado del
            // portal hacia el jefe, con el MISMO smoothstep y la MISMA
            // ventana de tiempo que usa TwilightPortalRenderer#computeScale
            // para achicar el modelo del portal -- así los dos efectos
            // (portal achicándose + cordón retirándose) terminan exactos
            // al mismo tiempo.
            float startFraction = 0.0F;
            if (portal.isDying()) {
                float ticksLeft = portal.getDyingTicksRemaining() + (1.0F - partialTick);
                float dyingT = Mth.clamp(ticksLeft / TwilightPortalEntity.DEATH_ANIM_TICKS, 0.0F, 1.0F);
                float ease = dyingT * dyingT * (3.0F - 2.0F * dyingT); // 1 (recién empieza a morir) -> 0 (a punto de desaparecer)
                startFraction = 1.0F - ease; // 0 -> 1: "come" el cordón desde el portal hacia el jefe
            }
            if (startFraction >= growthFraction) {
                continue; // no queda nada de cordón que dibujar este frame
            }

            drawTether(builder, matrix, cameraPos, portalPos, knightPos, gameTimeTicks, startFraction, growthFraction, profile, portal.getId());
        }

        if (began) {
            tesselator.end();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
        }
        return began;
    }

    private static void drawTether(BufferBuilder builder, Matrix4f matrix, Vec3 cameraPos,
                                   Vec3 worldA, Vec3 worldB, float gameTimeTicks,
                                   float startFraction, float growthFraction,
                                   PortalProfile profile, int portalId) {
        // Espacio relativo a cámara -- mismo motivo que en NecroticWaveRenderer
        // / TwilightWispTrailRenderer: el PoseStack de este evento ya está
        // centrado ahí, sin poseStack.translate() de por medio.
        Vec3 a = worldA.subtract(cameraPos);
        Vec3 b = worldB.subtract(cameraPos);

        Vec3 straightDir = b.subtract(a);
        double totalLength = straightDir.length();
        if (totalLength < 1.0e-4) {
            return;
        }
        Vec3 dirNorm = straightDir.scale(1.0 / totalLength);

        // Eje de la ondulación: perpendicular al cordón, FIJO en espacio de
        // mundo (no depende de cámara) -- así la curva en sí es siempre la
        // misma la mires desde donde la mires; el billboard de cada
        // segmento (ver addRibbonSegment) es lo único que sí sigue a cámara.
        Vec3 waveAxis = dirNorm.cross(new Vec3(0, 1, 0));
        if (waveAxis.lengthSqr() < 1.0e-6) {
            waveAxis = dirNorm.cross(new Vec3(1, 0, 0));
        }
        waveAxis = waveAxis.normalize();

        double timeSeconds = gameTimeTicks / 20.0;
        double pulsePhase = (timeSeconds * PULSE_SPEED) % 1.0;

        Vec3[] centerPoints = new Vec3[SEGMENTS + 1];
        Vec3 worldUp = new Vec3(0, 1, 0);
        double breathe = 1.0 + BREATHE_DEPTH * Math.sin(timeSeconds * BREATHE_SPEED + profile.timePhase());
        double waveAmplitude = (WAVE_AMPLITUDE_MIN
                + (WAVE_AMPLITUDE_MAX - WAVE_AMPLITUDE_MIN) * (profile.ampScale() - 0.7) / 0.6)
                * breathe;
        double arcHeight = (ARC_HEIGHT_MIN
                + (ARC_HEIGHT_MAX - ARC_HEIGHT_MIN) * (profile.ampScale() - 0.7) / 0.6)
                * breathe;

        for (int i = 0; i <= SEGMENTS; i++) {
            double t = (double) i / SEGMENTS;
            centerPoints[i] = computeCenterPoint(a, straightDir, waveAxis, worldUp, t,
                    timeSeconds, waveAmplitude, arcHeight, profile);
        }

        for (int i = 0; i < SEGMENTS; i++) {
            double t0 = (double) i / SEGMENTS;
            double t1 = (double) (i + 1) / SEGMENTS;

            if (t0 >= growthFraction || t1 <= startFraction) {
                continue; // todavía no "creció" hasta acá, o ya "se retiró" de acá (portal muriendo)
            }

            Vec3 segFrom = centerPoints[i];
            Vec3 segTo = centerPoints[i + 1];
            float growTipFade = 1.0F;
            float dyingTipFade = 1.0F;

            if (t1 > growthFraction) {
                // Segmento de la punta que está creciendo ahora mismo -- lo
                // recortamos a mitad de camino (en vez de esperar al
                // próximo segmento entero) y lo desvanecemos en la punta,
                // para que se vea como si de verdad estuviera avanzando en
                // vez de aparecer de golpe cada 1/24 del recorrido.
                double localT = (growthFraction - t0) / (t1 - t0);
                segTo = segFrom.add(segTo.subtract(segFrom).scale(localT));
                t1 = growthFraction;
                growTipFade = 0.0F;
            }
            if (t0 < startFraction) {
                // Mismo recorte que arriba pero del lado del portal --
                // esta es la punta que se está RETIRANDO hacia el jefe
                // mientras el portal se achica y muere.
                double localT = (startFraction - t0) / (t1 - t0);
                segFrom = segFrom.add(segTo.subtract(segFrom).scale(localT));
                t0 = startFraction;
                dyingTipFade = 0.0F;
            }

            float boost0 = pulseBoostAt(t0, pulsePhase) * dyingTipFade;
            float boost1 = pulseBoostAt(t1, pulsePhase) * growTipFade;

            addRibbonSegment(builder, matrix, segFrom, segTo,
                    GLOW_WIDTH, GLOW_R, GLOW_G, GLOW_B, GLOW_ALPHA, boost0, boost1);
            addRibbonSegment(builder, matrix, segFrom, segTo,
                    CORE_WIDTH, CORE_R, CORE_G, CORE_B, CORE_ALPHA, boost0, boost1);
        }

        // Chispas cerca del anclaje del portal -- dibujadas AL FINAL para
        // que queden por encima del cordón (mismo buffer/pasada, así que
        // el orden de inserción es el orden en el que se ven).
        drawTetherSparks(builder, matrix, portalId, a, straightDir, waveAxis, worldUp,
                timeSeconds, waveAmplitude, arcHeight, profile, gameTimeTicks, startFraction, growthFraction);

        // Nodo de anclaje -- también al final, por encima de todo.
        drawAnchorNode(builder, matrix, a, waveAxis, worldUp, timeSeconds, pulsePhase, startFraction);
    }

    /** Cuánto brillar en el punto t (0..1 a lo largo del cordón) según qué tan cerca está del pulso viajero. */
    private static float pulseBoostAt(double t, double pulsePhase) {
        double dist = Math.abs(t - pulsePhase);
        dist = Math.min(dist, 1.0 - dist); // distancia circular -- el pulso "da la vuelta" sin salto brusco
        if (dist > PULSE_WIDTH) {
            return 1.0F;
        }
        double closeness = 1.0 - (dist / PULSE_WIDTH);
        return (float) (1.0 + (PULSE_BOOST - 1.0) * closeness);
    }

    /**
     * Posición del cordón en un t continuo cualquiera (0 = portal, 1 = jefe),
     * no atado a la grilla fija de SEGMENTS -- extraído del loop de arriba
     * para poder reusarlo tal cual en drawTetherSparks (así las chispas seguí
     * exactamente la misma curva/ondulación/respiración que el cordón real
     * en ese instante, en vez de una línea recta aparte que se desincroniza).
     */
    private static Vec3 computeCenterPoint(Vec3 a, Vec3 straightDir, Vec3 waveAxis, Vec3 worldUp,
                                           double t, double timeSeconds, double waveAmplitude,
                                           double arcHeight, PortalProfile profile) {
        Vec3 base = a.add(straightDir.scale(t));
        double taper = Math.sin(Math.PI * t); // 0 en las puntas, 1 en el medio

        double verticalOffset;
        double horizontalOffset;
        switch (profile.shape()) {
            case ARC -> {
                // Un solo arco (sube y despues baja), no varios ciclos --
                // por eso NO multiplica por WAVE_FREQUENCY acá, el taper
                // solo ya dibuja la forma de montaña completa. "breathe"
                // ya está adentro de arcHeight, así que la altura del arco
                // respira solita en vez de quedar congelada en el tiempo.
                verticalOffset = taper * arcHeight;
                horizontalOffset = Math.sin(t * WAVE_FREQUENCY * Math.PI * 2.0
                        + timeSeconds * WAVE_SPEED + profile.timePhase())
                        * (waveAmplitude * ARC_SIDE_WAVE_SCALE) * taper;
            }
            case ZIGZAG -> {
                // En VERTICAL (antes iba en waveAxis/horizontal, ver
                // comentario en las constantes de arriba sobre por qué no
                // se veía parado en el piso).
                verticalOffset = triangleWave(t * ZIGZAG_FREQUENCY
                        + timeSeconds * ZIGZAG_SPEED + profile.timePhase())
                        * waveAmplitude * taper;
                horizontalOffset = 0.0;
            }
            default -> { // WAVE
                verticalOffset = 0.0;
                horizontalOffset = Math.sin(t * WAVE_FREQUENCY * Math.PI * 2.0
                        + timeSeconds * WAVE_SPEED + profile.timePhase())
                        * waveAmplitude * taper;
            }
        }

        return base.add(worldUp.scale(verticalOffset)).add(waveAxis.scale(horizontalOffset));
    }

    /**
     * Devuelve las chispas activas de este portal, haciendo nacer una nueva
     * si tocó (y podando las que ya cumplieron su vida). Determinístico por
     * portal id + "slot" de tiempo (gameTimeTicks / SPARK_SPAWN_INTERVAL_TICKS)
     * -- mismo criterio que CorruptionFilamentManager/NecroticOverflowTracker
     * para no tener que sincronizar nada por red: cualquier cliente que
     * calcule el mismo slot genera exactamente la misma chispa.
     */
    private static List<Spark> getActiveSparks(int portalId, double gameTimeTicks) {
        List<Spark> list = SPARKS_BY_PORTAL.computeIfAbsent(portalId, k -> new ArrayList<>());

        long slot = (long) Math.floor(gameTimeTicks / SPARK_SPAWN_INTERVAL_TICKS);
        Long lastSlot = LAST_SPARK_SLOT_BY_PORTAL.put(portalId, slot);
        if (lastSlot == null || slot != lastSlot) {
            RandomSource random = RandomSource.create(portalId * 1_000_003L + slot);
            double baseT = random.nextDouble() * SPARK_BASE_T_MAX;

            boolean isFarSpark = random.nextDouble() < SPARK_FAR_CHANCE;
            double travelT;
            double lifetimeTicks;
            if (isFarSpark) {
                travelT = SPARK_FAR_TRAVEL_T_MIN
                        + random.nextDouble() * (SPARK_FAR_TRAVEL_T_MAX - SPARK_FAR_TRAVEL_T_MIN);
                lifetimeTicks = SPARK_FAR_LIFETIME_TICKS;
            } else {
                travelT = SPARK_TRAVEL_T_MIN + random.nextDouble() * (SPARK_TRAVEL_T_MAX - SPARK_TRAVEL_T_MIN);
                lifetimeTicks = SPARK_LIFETIME_TICKS;
            }

            // Ángulo random alrededor del eje del cordón + radio con mínimo
            // garantizado (ver comentario en SPARK_JITTER_MIN_RADIUS) --
            // así la chispa nace apuntando "hacia afuera" en una dirección
            // cualquiera del cilindro imaginario alrededor del cordón, en
            // vez de en el eje central.
            double jitterAngle = random.nextDouble() * Math.PI * 2.0D;
            double jitterRadius = SPARK_JITTER_MIN_RADIUS
                    + random.nextDouble() * (SPARK_JITTER_MAX_RADIUS - SPARK_JITTER_MIN_RADIUS);
            double axisJitter = Math.cos(jitterAngle) * jitterRadius;
            double upJitter = Math.sin(jitterAngle) * jitterRadius;
            list.add(new Spark(slot * SPARK_SPAWN_INTERVAL_TICKS, baseT, travelT, axisJitter, upJitter, lifetimeTicks));
        }

        list.removeIf(s -> gameTimeTicks - s.spawnTick() > s.lifetimeTicks());
        return list;
    }

    /**
     * Dibuja las chispas activas de este portal -- líneas rectas finitas que
     * avanzan un trecho corto pegadas al cordón real (mismo t que usa el
     * cordón, ver computeCenterPoint) y se desvanecen con una envolvente
     * "sube y baja" (Math.sin) en vez de aparecer/desaparecer de golpe.
     */
    private static void drawTetherSparks(BufferBuilder builder, Matrix4f matrix, int portalId,
                                         Vec3 a, Vec3 straightDir, Vec3 waveAxis, Vec3 worldUp,
                                         double timeSeconds, double waveAmplitude, double arcHeight,
                                         PortalProfile profile, double gameTimeTicks,
                                         float startFraction, float growthFraction) {
        List<Spark> sparks = getActiveSparks(portalId, gameTimeTicks);
        for (Spark spark : sparks) {
            double age = gameTimeTicks - spark.spawnTick();
            double progress = Mth.clamp(age / spark.lifetimeTicks(), 0.0, 1.0);

            double centerT = spark.baseT() + spark.travelT() * progress;
            double t0 = centerT - SPARK_HALF_LEN_T;
            double t1 = centerT + SPARK_HALF_LEN_T;

            // No dibujar la parte que todavía no "creció" o que ya se
            // "retiró" (portal muriendo) -- mismo criterio que el cordón
            // principal (ver renderAllTethers/drawTether).
            if (t1 <= startFraction || t0 >= growthFraction) {
                continue;
            }
            t0 = Math.max(t0, startFraction);
            t1 = Math.min(t1, growthFraction);
            if (t1 - t0 < 1.0e-4) {
                continue;
            }

            Vec3 jitter = waveAxis.scale(spark.axisJitter()).add(worldUp.scale(spark.upJitter()));
            Vec3 from = computeCenterPoint(a, straightDir, waveAxis, worldUp, t0,
                    timeSeconds, waveAmplitude, arcHeight, profile).add(jitter);
            Vec3 to = computeCenterPoint(a, straightDir, waveAxis, worldUp, t1,
                    timeSeconds, waveAmplitude, arcHeight, profile).add(jitter);

            // Envolvente de brillo propia de cada chispa: aparece, sube, y
            // se apaga, todo dentro de su propia vida (en vez de un fade
            // lineal soso, o de aparecer/desaparecer de golpe).
            float envelope = (float) Math.sin(Math.PI * progress);

            addRibbonSegment(builder, matrix, from, to, SPARK_WIDTH, SPARK_R, SPARK_G, SPARK_B, SPARK_ALPHA,
                    envelope, envelope);
        }
    }

    /**
     * Dibuja el "nodo" de anclaje: un pequeño asterisco de rayitos girando
     * despacio justo donde el cordón toca el portal, pulsando en sincronía
     * con pulseBoostAt(0, ...) -- el mismo pulso de brillo que ya viaja por
     * el cordón, así que cuando el pulso "nace" acá el nodo destella un
     * poco más fuerte.
     */
    private static void drawAnchorNode(BufferBuilder builder, Matrix4f matrix, Vec3 a,
                                       Vec3 waveAxis, Vec3 worldUp, double timeSeconds,
                                       double pulsePhase, float startFraction) {
        if (startFraction > 0.0F) {
            return; // el portal ya está muriendo y el cordón se retiró de acá -- no mostrar el nodo
        }

        float boost = pulseBoostAt(0.0, pulsePhase);
        double spin = timeSeconds * ANCHOR_SPIN_SPEED;
        for (int i = 0; i < ANCHOR_SPOKE_COUNT; i++) {
            double angle = spin + (Math.PI * 2.0D * i) / ANCHOR_SPOKE_COUNT;
            Vec3 dir = waveAxis.scale(Math.cos(angle)).add(worldUp.scale(Math.sin(angle)));
            Vec3 tip = a.add(dir.scale(ANCHOR_SPOKE_LENGTH));
            // boost0 brillante en el centro, boost1 = 0 en la punta (se desvanece hacia afuera).
            addRibbonSegment(builder, matrix, a, tip, ANCHOR_WIDTH, ANCHOR_R, ANCHOR_G, ANCHOR_B, ANCHOR_ALPHA,
                    boost, 0.0F);
        }
    }

    private static void addRibbonSegment(BufferBuilder builder, Matrix4f matrix,
                                         Vec3 fromLocal, Vec3 toLocal,
                                         float width, float r, float g, float b, float baseAlpha,
                                         float alphaBoost0, float alphaBoost1) {
        Vec3 segmentDir = toLocal.subtract(fromLocal);
        if (segmentDir.lengthSqr() < 1.0e-8) {
            return;
        }

        // La cámara está en el origen de este espacio (ver drawTether), así
        // que "hacia cámara" desde el punto medio del segmento es
        // simplemente ese punto invertido.
        Vec3 midLocal = fromLocal.add(toLocal).scale(0.5);
        Vec3 towardCamera = midLocal.scale(-1.0);
        Vec3 perp = segmentDir.cross(towardCamera);
        if (perp.lengthSqr() < 1.0e-6) {
            perp = segmentDir.cross(new Vec3(0, 1, 0));
            if (perp.lengthSqr() < 1.0e-6) {
                perp = segmentDir.cross(new Vec3(1, 0, 0));
            }
        }
        perp = perp.normalize();

        Vec3 fromA = fromLocal.add(perp.scale(width / 2.0));
        Vec3 fromB = fromLocal.subtract(perp.scale(width / 2.0));
        Vec3 toA = toLocal.add(perp.scale(width / 2.0));
        Vec3 toB = toLocal.subtract(perp.scale(width / 2.0));

        float alpha0 = Math.min(1.0F, baseAlpha * alphaBoost0);
        float alpha1 = Math.min(1.0F, baseAlpha * alphaBoost1);

        vertex(builder, matrix, fromA, r, g, b, alpha0);
        vertex(builder, matrix, fromB, r, g, b, alpha0);
        vertex(builder, matrix, toB, r, g, b, alpha1);
        vertex(builder, matrix, toA, r, g, b, alpha1);
    }

    /**
     * Todo lo que varía "por portal" (no por frame) calculado de una sola
     * vez: forma de la curva, altura de enganche en el jefe, escala de
     * amplitud/altura, y fase de tiempo (para desincronizar la
     * respiración/ondulación entre portales con la misma forma).
     */
    private record PortalProfile(TetherShape shape, double attachHeightOffset, double ampScale, double timePhase) {
    }

    // Vuelta a la fórmula original (fracción de portal.getBbHeight(), no
    // un número fijo) pero con la fracción bajada de 0.5 a 0.3 -- 0.5 (la
    // mitad exacta del hitbox de colisión, 1.75 bloques) quedaba muy alto
    // respecto al remolino visual real; 0.3 lo baja sin ir tan abajo como
    // el intento anterior con PORTAL_ATTACH_HEIGHT fijo en 0.5.
    private static final double PORTAL_ATTACH_HEIGHT_FRACTION = 0.45D;
    // Empuje hacia ATRÁS del portal (en la dirección opuesta a hacia
    // dónde mira, ver TwilightPortalEntity#getFacingYaw) -- para que el
    // cordón nazca un poco "adentro"/detrás del remolino en vez de
    // pegado exactamente a su centro. 0 = desactivado.
    private static final double PORTAL_ATTACH_BACK_OFFSET = 0.09D;

    private static PortalProfile pickPortalProfile(int entityId) {
        TetherShape[] shapes = TetherShape.values();
        TetherShape shape = shapes[Math.floorMod(hashUnit(entityId, 1), shapes.length)];
        double attachHeightOffset = (unitFloat(entityId, 2) - 0.5) * 2.0 * KNIGHT_ATTACH_HEIGHT_VARIATION;
        double ampScale = 0.7 + unitFloat(entityId, 3) * 0.6; // 0.7..1.3
        double timePhase = unitFloat(entityId, 4) * PORTAL_PROFILE_PHASE_RANGE;
        return new PortalProfile(shape, attachHeightOffset, ampScale, timePhase);
    }

    /** Hash entero determinístico por (entityId, salt) -- mismo par siempre da el mismo resultado. */
    private static int hashUnit(int entityId, int salt) {
        int h = (entityId * 0x9E3779B1) ^ (salt * 0x27D4EB2D);
        h ^= (h >>> 15);
        h *= 0x85EBCA6B;
        h ^= (h >>> 13);
        return h;
    }

    /** Igual que hashUnit pero normalizado a 0..1. */
    private static float unitFloat(int entityId, int salt) {
        return (hashUnit(entityId, salt) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    /** Onda triangular (quiebres angulares) en vez de la curva suave de Math.sin -- rango -1..1. */
    private static double triangleWave(double phase) {
        double frac = phase - Math.floor(phase); // 0..1
        return 4.0 * Math.abs(frac - 0.5) - 1.0;
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vec3 pos, float r, float g, float b, float alpha) {
        builder.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(r, g, b, alpha)
                .endVertex();
    }
}