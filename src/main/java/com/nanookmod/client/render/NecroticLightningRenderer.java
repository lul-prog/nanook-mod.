package com.nanookmod.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
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
import com.nanookmod.entity.NecroticLightningBeamEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Dibuja el rayo del ataque nuevo de Corrupción IV
 * (sword_beam_windup + sword_beam_channel), más las chispas cónicas y el
 * disco en el origen/punta.
 *
 * Estética "eléctrica" (zigzag/jitter) -- la que tenía el mod
 * originalmente -- combinada con los fixes hechos después: ancho
 * CONSTANTE de punta a punta (sin afinado -- ver el comentario junto a
 * las constantes, es la causa real del bug "se achica de cerca"),
 * tamaño calcado del Nameless Guardian, sin partículas del motor de
 * Minecraft. Lee de {@link NecroticLightningBeamEntity}, una entidad de
 * verdad -- la posición y la rotación YA vienen del sistema estándar de
 * Minecraft, este renderer se limita a LEER esos valores y dibujar
 * geometría encima.
 *
 * DOS SHADERS -- nanookmod:necrotic_beam para el prisma del rayo y las
 * chispas (con animación de flujo de energía vía GameTime, ver
 * necrotic_beam.fsh), y el shader vainilla POSITION_TEX_COLOR para los
 * discos con textura. Se dibujan en dos pasadas separadas (cada
 * `begin`/`end` del Tesselator solo puede usar un shader a la vez).
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class NecroticLightningRenderer {

    // Tamaño de las orbes calcado de EntityGuardianLaser.UserType.
    // NAMELESS_GUARDIAN (radius=1.09) -- el ancho del rayo en sí NO, ver
    // el comentario junto a CORE_HALF_WIDTH/GLOW_HALF_WIDTH más abajo.

    private static final int SEGMENTS = 10;
    private static final int JITTER_INTERVAL_TICKS = 2;
    private static final double JITTER_AMOUNT = 0.16D;

    // Cuando el rayo llega a su alcance máximo real (RANGE) SIN chocar
    // contra nada (ni bloque ni jugador) -- ej. el jugador esquivó --
    // esto extiende la línea SOLO PARA DIBUJAR (nunca para el daño, que
    // se sigue calculando aparte y queda limitado a RANGE) en la MISMA
    // dirección recta, hasta que choque contra un bloque de verdad. Así
    // nunca se ve flotando cortado en el aire a la altura del pecho --
    // sigue de largo, con el mismo ángulo real, hasta tocar el piso (o
    // una pared) más allá. Ver #extendVisualToSolidBlock.
    private static final double VISUAL_FLOOR_EXTRA_RANGE = 96.0D;

    // Crecimiento del alcance (0.5s desde que nace la entidad hasta el
    // largo máximo) y fade-in/fade-out visual -- ver
    // NecroticLightningBeamEntity para el mismo crecimiento aplicado al
    // daño real.
    private static final int GROWTH_TICKS = NecroticLightningBeamEntity.GROWTH_TICKS;
    private static final float FADE_TICKS = 4.0f; // 0.2s, puramente visual

    // OJO -- A PROPÓSITO no hay afinado hacia la punta (taper). Esa era
    // la causa real del bug "el rayo se hace chico cuando estoy cerca":
    // con un rayo corto, el afinado (calculado como fracción del largo
    // total O como distancia fija al final) terminaba encogiendo el
    // rayo ENTERO, no solo la puntita. Ancho constante de punta a punta
    // -- soluciona el bug de raíz en vez de parchearlo.

    // Mismos colores EXACTOS que NecroticCorruptionTetherRenderer (los
    // hilos que conectan al jefe con los portales).
    private static final float CORE_R = 0.7373f, CORE_G = 1.0f, CORE_B = 0.7725f;
    private static final float GLOW_R = 0.3843f, GLOW_G = 0.9647f, GLOW_B = 0.5804f;
    // Ancho -- OJO, a propósito bastante más angosto que el beamRadius=0.9
    // del Nameless Guardian. Ese valor se ve bien EN ELLOS porque es un
    // jefe gigante; copiado tal cual acá, el rayo terminaba midiendo 1.8
    // bloques de ancho -- literalmente la altura completa de un jugador
    // parado -- así que por más que la puntería apunte exacto al centro
    // del cuerpo (confirmado por el log de debug), el borde superior del
    // rayo igual roza la cabeza porque el rayo YA es tan alto como el
    // jugador entero. Con esto, el rayo se siente como que pega en el
    // pecho en vez de cubrir todo el cuerpo de pies a cabeza.
    private static final float CORE_HALF_WIDTH = 0.16f;
    private static final float GLOW_HALF_WIDTH = 0.40f;
    private static final float CORE_ALPHA = 0.95f;
    private static final float GLOW_ALPHA = 0.55f;

    // --- Disco (con textura, ver lightning_orb.png) ---
    private static final ResourceLocation ORB_TEXTURE =
            new ResourceLocation(NanookMod.MOD_ID, "textures/particle/lightning_orb.png");
    // Reducido en la misma proporción que el ancho del rayo (ver arriba).
    private static final float DISC_RADIUS = 1.0f;
    private static final float DISC_SIZE_PULSE = 0.12f;
    private static final float DISC_ALPHA = 1.0f;

    // --- Chispas cónicas en el origen -- dibujadas por el mismo shader
    // que el rayo (necrotic_beam), NO son partículas del motor de
    // Minecraft. Esto SÍ hay que mantenerlo -- lo que se sacó fue
    // spawnImpactParticles (soul/spark REALES, motor de partículas
    // vainilla) en NecroticLightningBeamEntity.
    private static final int SPARK_COUNT = 16;
    private static final float SPARK_CONE_ANGLE_DEG = 34.0f;
    private static final double SPARK_MAX_DISTANCE = 2.3D;
    private static final int SPARK_CYCLE_TICKS = 16;
    private static final float SPARK_HALF_WIDTH = 0.07f;
    private static final float SPARK_HALF_LENGTH = 0.16f;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        // --- DEBUG TEMPORAL -- borrar cuando el rayo/las orbes se vean
        // bien de una vez. Si algo dentro de este método tira una
        // excepción, Forge normalmente solo la escupe a la consola/log y
        // SIGUE, sin cortar el juego -- así que no se "crashea" nada mas
        // silenciosamente no se dibuja NINGUNA de las dos pasadas (ni el
        // rayo ni las orbes) desde ese frame. Con este try/catch, si hay
        // una excepción, la vas a ver en consola/latest.log buscando
        // "LIGHTNING-RENDER-ERROR".
        try {
            renderBeams(event, mc, level);
        } catch (Throwable t) {
            NanookMod.LOGGER.error("LIGHTNING-RENDER-ERROR", t);
        }
    }

    private static void renderBeams(RenderLevelStageEvent event, Minecraft mc, ClientLevel level) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        float partialTick = event.getPartialTick();

        PoseStack poseStack = event.getPoseStack();
        Matrix4f matrix = poseStack.last().pose();

        long jitterWindow = level.getGameTime() / JITTER_INTERVAL_TICKS;

        // Junto todas las instancias activas ANTES de dibujar nada -- así
        // recorro entidades una sola vez aunque después dibuje en dos
        // pasadas (una por shader).
        List<BeamInstance> instances = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof NecroticLightningBeamEntity beam)) {
                continue;
            }

            float ticksAlive = beam.tickCount + partialTick;
            // OJO -- acá NO usamos partialTick para la posición/dirección
            // (a propósito, aunque el resto del método sí lo use para el
            // fade/crecimiento). El daño real (NecroticLightningBeamEntity#
            // applyDamage, servidor) usa la rotación EXACTA del tick actual,
            // sin interpolar. Si acá interpolábamos entre el pitch del tick
            // anterior y el de este tick (getPosition/getViewVector con
            // partialTick), y el pitch puede saltar hasta 60°/tick durante
            // el canalizado (para trabarse rápido en el blanco), un frame
            // de render a mitad de esa interpolación dibuja el rayo
            // apuntando a un ángulo que NUNCA existió de verdad ni en el
            // tick viejo ni en el nuevo -- en un rayo fino a 10+ bloques,
            // eso son varios bloques de error VISUAL, aunque el golpe (que
            // no pasa por acá) siga conectando bien. Por eso replicamos acá
            // el mismo criterio "sin interpolar" que usa el daño: el precio
            // es que el rayo se ve a los 20 ticks/s del juego en vez de a
            // los ~60 fps de la pantalla (un pelín menos fluido), pero
            // nunca se desvía de lo que el jugador realmente puede recibir.
            Vec3 origin = beam.position();
            Vec3 dirVec = beam.getViewVector(1.0F);
            double range = NecroticLightningBeamEntity.computeGrowthRange(ticksAlive);
            NecroticLightningBeamEntity.BeamHit hit =
                    NecroticLightningBeamEntity.computeBeamEnd(level, beam, beam.getOwnerKnight(), origin, dirVec, range);
            Vec3 end = hit.endPoint;

            // El rayo llegó a su alcance real SIN chocar contra nada (ni
            // jugador ni bloque) -- antes esto se veía flotando cortado
            // en el aire. Lo seguimos dibujando (SOLO dibujando, el daño
            // ya se calculó arriba y queda limitado a `range`) en línea
            // recta, mismo ángulo, hasta que choque contra el piso o una
            // pared de verdad.
            boolean ranOutWithoutHitting = hit.hitEntity == null && range >= NecroticLightningBeamEntity.RANGE - 0.01D;
            if (ranOutWithoutHitting) {
                end = NecroticLightningBeamEntity.extendVisualToSolidBlock(level, beam, end, dirVec, VISUAL_FLOOR_EXTRA_RANGE);
            }

            Vec3 dir = end.subtract(origin);
            double length = dir.length();
            if (length < 1.0E-4D) {
                continue; // recién nacido, todavía no hay largo -- nada que dibujar este frame
            }
            Vec3 dirNorm = dir.scale(1.0D / length);
            Vec3 upRef = Math.abs(dirNorm.y) > 0.99D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 perpA = dirNorm.cross(upRef).normalize();
            Vec3 perpB = dirNorm.cross(perpA).normalize();

            float fadeIn = Mth.clamp(ticksAlive / FADE_TICKS, 0.0f, 1.0f);
            float ticksRemaining = NecroticKnightEntity.LIGHTNING_ACTIVE_CHANNEL_TICKS - ticksAlive;
            float fadeOut = Mth.clamp(ticksRemaining / FADE_TICKS, 0.0f, 1.0f);
            float alpha = Math.min(fadeIn, fadeOut);

            instances.add(new BeamInstance(beam, origin, end, dirNorm, perpA, perpB, alpha));
        }

        if (instances.isEmpty()) {
            return;
        }

        float gameTimeValue = (level.getGameTime() % 1_000_000L) / 20.0f + partialTick;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        // Estado GL común a las dos pasadas -- aditivo, sin depth write,
        // sin culling (evita cualquier duda de orden de winding).
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        // --- Pasada 1: el prisma del rayo ---
        ShaderInstance beamShader = ModShaders.getNecroticBeamShader();
        if (beamShader != null) {
            RenderSystem.setShader(() -> beamShader);
            setGameTimeUniform(beamShader, gameTimeValue);

            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (BeamInstance inst : instances) {
                drawBeamTube(builder, matrix, inst, camPos, jitterWindow);
                drawSparks(builder, matrix, inst, camPos);
            }
            tesselator.end();
        }

        // --- Pasada 2: los discos (con textura, no shader propio) ---
        // OJO -- getPositionTexColorShader, NO getPositionColorTexShader.
        // El builder se abre con DefaultVertexFormat.POSITION_TEX_COLOR
        // (posición, LUEGO textura, LUEGO color) -- ese es justamente el
        // formato que espera getPositionTexColorShader. El otro
        // (getPositionColorTexShader) espera el orden POSITION_COLOR_TEX
        // (textura y color invertidos). Con el shader equivocado no hay
        // excepción de Java -- la GPU simplemente lee los atributos
        // desalineados y no dibuja nada visible. Esto era el bug real de
        // "el orbe no aparece".
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, ORB_TEXTURE);

        Vector3f camLeftF = camera.getLeftVector();
        Vector3f camUpF = camera.getUpVector();
        Vec3 camRight = new Vec3(camLeftF.x(), camLeftF.y(), camLeftF.z());
        Vec3 camUp = new Vec3(camUpF.x(), camUpF.y(), camUpF.z());

        float discRadius = DISC_RADIUS * (1.0f + DISC_SIZE_PULSE * (float) Math.sin(gameTimeValue * 8.0f));
        // El pulso de BRILLO que antes hacía el shader (necrotic_orb.fsh)
        // ahora se calcula acá mismo, en la CPU, y se mete en el alpha del
        // vértice -- la textura es solo la forma/degradé, el pulso sigue
        // yendo a juego con la misma frecuencia (GameTime * 8) que la
        // onda del rayo.
        float discPulse = 1.0f;

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (BeamInstance inst : instances) {
            float alpha = DISC_ALPHA * inst.alpha * discPulse;
            drawDisc(builder, matrix, inst.origin.subtract(camPos), camRight, camUp, discRadius,
                    CORE_R, CORE_G, CORE_B, alpha);
            drawDisc(builder, matrix, inst.end.subtract(camPos), camRight, camUp, discRadius,
                    CORE_R, CORE_G, CORE_B, alpha);
        }
        tesselator.end();

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void setGameTimeUniform(ShaderInstance shader, float gameTimeValue) {
        Uniform gameTimeUniform = shader.getUniform("GameTime");
        if (gameTimeUniform != null) {
            gameTimeUniform.set(gameTimeValue);
        }
    }

    /** Instancia lista para dibujar -- calculada una sola vez por rayo/frame y reusada en las dos pasadas. */
    private static final class BeamInstance {
        final NecroticLightningBeamEntity beam;
        final Vec3 origin;
        final Vec3 end;
        final Vec3 dirNorm;
        final Vec3 perpA;
        final Vec3 perpB;
        final float alpha;

        BeamInstance(NecroticLightningBeamEntity beam, Vec3 origin, Vec3 end, Vec3 dirNorm,
                     Vec3 perpA, Vec3 perpB, float alpha) {
            this.beam = beam;
            this.origin = origin;
            this.end = end;
            this.dirNorm = dirNorm;
            this.perpA = perpA;
            this.perpB = perpB;
            this.alpha = alpha;
        }
    }

    private static void drawBeamTube(BufferBuilder builder, Matrix4f matrix, BeamInstance inst,
                                     Vec3 camPos, long jitterWindow) {
        Vec3[] points = buildJitteredPoints(inst.beam.getId(), inst.origin, inst.end,
                inst.perpA, inst.perpB, jitterWindow);
        for (int i = 0; i < points.length; i++) {
            points[i] = points[i].subtract(camPos);
        }

        renderTube(builder, matrix, points, inst.perpA, inst.perpB, GLOW_HALF_WIDTH, GLOW_R, GLOW_G, GLOW_B, GLOW_ALPHA * inst.alpha);
        renderTube(builder, matrix, points, inst.perpA, inst.perpB, CORE_HALF_WIDTH, CORE_R, CORE_G, CORE_B, CORE_ALPHA * inst.alpha);
    }

    /**
     * Puntos entre origin y end con jitter perpendicular en los
     * intermedios (los dos extremos NUNCA se mueven, así el rayo sigue
     * naciendo exactamente en la espada y terminando exactamente donde
     * pegó). Semilla determinística (id del rayo + ventana de tiempo +
     * índice) para que el zigzag sea igual en todos los clientes mirando
     * la pelea al mismo tiempo.
     */
    private static Vec3[] buildJitteredPoints(int beamId, Vec3 origin, Vec3 end,
                                              Vec3 perpA, Vec3 perpB, long jitterWindow) {
        Vec3[] points = new Vec3[SEGMENTS + 1];
        Vec3 dir = end.subtract(origin);

        for (int i = 0; i <= SEGMENTS; i++) {
            Vec3 base = origin.add(dir.scale((double) i / SEGMENTS));
            if (i == 0 || i == SEGMENTS) {
                points[i] = base;
                continue;
            }
            long seed = jitterWindow * 7919L + beamId * 131L + i;
            Random rnd = new Random(seed);
            double jitterA = (rnd.nextDouble() * 2.0D - 1.0D) * JITTER_AMOUNT;
            double jitterB = (rnd.nextDouble() * 2.0D - 1.0D) * JITTER_AMOUNT;
            points[i] = base.add(perpA.scale(jitterA)).add(perpB.scale(jitterB));
        }
        return points;
    }

    /**
     * Extruye un prisma de sección cuadrada a lo largo de `points`
     * (relativos a cámara), ancho CONSTANTE (`halfWidth`) en TODOS los
     * anillos -- sin afinado hacia la punta (ver el comentario junto a
     * las constantes de más arriba, es la fix del bug "se achica de
     * cerca"). Cada punto es el centro de un "anillo" de 4 esquinas
     * construido con perpA/perpB; los anillos consecutivos se conectan
     * con 4 quads (una por lado) más una tapa en la punta lejana.
     */
    private static void renderTube(BufferBuilder builder, Matrix4f matrix, Vec3[] points,
                                   Vec3 perpA, Vec3 perpB, float halfWidth,
                                   float r, float g, float b, float alpha) {
        if (alpha <= 0.001f) {
            return;
        }

        int segments = points.length - 1;
        Vec3 a = perpA.scale(halfWidth);
        Vec3 bAxis = perpB.scale(halfWidth);
        Vec3[][] rings = new Vec3[points.length][4];
        for (int i = 0; i < points.length; i++) {
            Vec3 p = points[i];
            rings[i][0] = p.add(a).add(bAxis);
            rings[i][1] = p.add(a).subtract(bAxis);
            rings[i][2] = p.subtract(a).subtract(bAxis);
            rings[i][3] = p.subtract(a).add(bAxis);
        }

        for (int i = 0; i < segments; i++) {
            float vFrom = (float) i / segments;
            float vTo = (float) (i + 1) / segments;
            for (int side = 0; side < 4; side++) {
                int next = (side + 1) % 4;
                Vec3 a0 = rings[i][side];
                Vec3 a1 = rings[i][next];
                Vec3 b1 = rings[i + 1][next];
                Vec3 b0 = rings[i + 1][side];

                vertex(builder, matrix, a0, 0.0f, vFrom, r, g, b, alpha);
                vertex(builder, matrix, a1, 1.0f, vFrom, r, g, b, alpha);
                vertex(builder, matrix, b1, 1.0f, vTo, r, g, b, alpha);
                vertex(builder, matrix, b0, 0.0f, vTo, r, g, b, alpha);
            }
        }

        // Tapa en la punta lejana.
        Vec3[] tip = rings[points.length - 1];
        vertex(builder, matrix, tip[0], 0.0f, 1.0f, r, g, b, alpha);
        vertex(builder, matrix, tip[1], 1.0f, 1.0f, r, g, b, alpha);
        vertex(builder, matrix, tip[2], 1.0f, 1.0f, r, g, b, alpha);
        vertex(builder, matrix, tip[3], 0.0f, 1.0f, r, g, b, alpha);
    }

    /**
     * Chispas en forma de cono desde el origen, apuntando hacia donde
     * viaja el rayo. Cada chispa tiene su PROPIA dirección (desviada del
     * eje principal, dentro de un cono) y su propio ciclo de vida (nace
     * en el origen, viaja, se apaga, vuelve a nacer) -- todo
     * determinístico por semilla. Dibujadas con el MISMO shader que el
     * rayo (necrotic_beam) -- no son partículas del motor de Minecraft.
     */
    private static void drawSparks(BufferBuilder builder, Matrix4f matrix, BeamInstance inst, Vec3 camPos) {
        if (inst.alpha <= 0.001f) {
            return;
        }

        double maxAngleRad = Math.toRadians(SPARK_CONE_ANGLE_DEG);
        long rawTick = inst.beam.level().getGameTime();

        for (int i = 0; i < SPARK_COUNT; i++) {
            long seed = (long) inst.beam.getId() * 92821L + i * 7699L;
            Random rnd = new Random(seed);
            double angleFrac = Math.sqrt(rnd.nextDouble());
            double rotFrac = rnd.nextDouble();
            int lifeOffset = rnd.nextInt(SPARK_CYCLE_TICKS);

            Vec3 sparkDir = sampleConeDirection(inst.dirNorm, inst.perpA, inst.perpB, maxAngleRad, angleFrac, rotFrac);

            double life = ((rawTick + lifeOffset) % SPARK_CYCLE_TICKS) / (double) SPARK_CYCLE_TICKS;
            double dist = life * SPARK_MAX_DISTANCE;
            float lifeFade = life < 0.75D ? 1.0f : (float) (1.0D - (life - 0.75D) / 0.25D);

            Vec3 from = inst.origin.add(sparkDir.scale(dist)).subtract(camPos);
            Vec3 to = inst.origin.add(sparkDir.scale(dist + SPARK_HALF_LENGTH * 2.0D)).subtract(camPos);

            Vec3 sPerpA = sparkDir.cross(Math.abs(sparkDir.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
            Vec3 sPerpB = sparkDir.cross(sPerpA).normalize();

            boolean lighter = (i % 2 == 0);
            float r = lighter ? CORE_R : GLOW_R;
            float g = lighter ? CORE_G : GLOW_G;
            float b = lighter ? CORE_B : GLOW_B;
            float baseAlpha = lighter ? CORE_ALPHA : GLOW_ALPHA * 0.85f;

            renderMiniBox(builder, matrix, from, to, sPerpA, sPerpB, SPARK_HALF_WIDTH,
                    r, g, b, baseAlpha * lifeFade * inst.alpha);
        }
    }

    private static Vec3 sampleConeDirection(Vec3 axis, Vec3 perpA, Vec3 perpB, double maxAngleRad,
                                            double angleFrac, double rotFrac) {
        double angle = maxAngleRad * angleFrac;
        double rot = rotFrac * Math.PI * 2.0D;
        double sinA = Math.sin(angle);
        double cosA = Math.cos(angle);
        Vec3 radial = perpA.scale(Math.cos(rot)).add(perpB.scale(Math.sin(rot)));
        return axis.scale(cosA).add(radial.scale(sinA)).normalize();
    }

    private static void renderMiniBox(BufferBuilder builder, Matrix4f matrix, Vec3 from, Vec3 to,
                                      Vec3 perpA, Vec3 perpB, float halfWidth,
                                      float r, float g, float b, float alpha) {
        if (alpha <= 0.001f) {
            return;
        }
        Vec3 a = perpA.scale(halfWidth);
        Vec3 bAxis = perpB.scale(halfWidth);

        Vec3[] ringFrom = {
                from.add(a).add(bAxis), from.add(a).subtract(bAxis),
                from.subtract(a).subtract(bAxis), from.subtract(a).add(bAxis)
        };
        Vec3[] ringTo = {
                to.add(a).add(bAxis), to.add(a).subtract(bAxis),
                to.subtract(a).subtract(bAxis), to.subtract(a).add(bAxis)
        };

        for (int side = 0; side < 4; side++) {
            int next = (side + 1) % 4;
            vertex(builder, matrix, ringFrom[side], 0.0f, 0.0f, r, g, b, alpha);
            vertex(builder, matrix, ringFrom[next], 1.0f, 0.0f, r, g, b, alpha);
            vertex(builder, matrix, ringTo[next], 1.0f, 1.0f, r, g, b, alpha);
            vertex(builder, matrix, ringTo[side], 0.0f, 1.0f, r, g, b, alpha);
        }
    }

    private static void drawDisc(BufferBuilder builder, Matrix4f matrix, Vec3 centerLocal,
                                 Vec3 camRight, Vec3 camUp, float radius,
                                 float r, float g, float b, float alpha) {
        if (alpha <= 0.001f) {
            return;
        }
        Vec3 right = camRight.scale(radius);
        Vec3 up = camUp.scale(radius);

        Vec3 p0 = centerLocal.subtract(right).subtract(up);
        Vec3 p1 = centerLocal.add(right).subtract(up);
        Vec3 p2 = centerLocal.add(right).add(up);
        Vec3 p3 = centerLocal.subtract(right).add(up);

        vertex(builder, matrix, p0, 0.0f, 0.0f, r, g, b, alpha);
        vertex(builder, matrix, p1, 1.0f, 0.0f, r, g, b, alpha);
        vertex(builder, matrix, p2, 1.0f, 1.0f, r, g, b, alpha);
        vertex(builder, matrix, p3, 0.0f, 1.0f, r, g, b, alpha);
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vec3 pos, float u, float v,
                               float r, float g, float b, float a) {
        builder.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .uv(u, v)
                .color(r, g, b, a)
                .endVertex();
    }
}