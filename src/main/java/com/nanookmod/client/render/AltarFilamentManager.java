package com.nanookmod.client.render;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Filamentos de energía del Altar Necromante -- el "estilo Scylla" que
 * pediste (Cataclysm: al 50% de vida le salen filamentos alrededor). Acá
 * el disparador no es la vida de un jefe sino el PROGRESO DEL RITUAL: a
 * medida que se acerca el final del buildup, nacen hilos de energía en
 * puntos al azar alrededor del altar que VIAJAN HACIA ADENTRO y se
 * disuelven consumiéndose desde afuera hacia el centro -- literalmente
 * "los filamentos van adentro del bloque", tal como lo describiste.
 *
 * Es un calco deliberado de CorruptionFilamentManager +
 * CorruptionFilamentRenderer (mismo mod, otro efecto ya probado) con dos
 * diferencias de diseño:
 *   1. Acá el origen Y el destino son puntos FIJOS en el mundo (no hay un
 *      "owner" que se mueve) -- un filamento del altar no tiene que
 *      perseguir a nadie, así que directamente guarda los dos Vec3.
 *   2. La dirección de "crecimiento" apunta HACIA EL ALTAR en vez de
 *      hacia arriba desde un jugador -- por eso el wobble usa una base
 *      ortonormal calculada a partir de la dirección real de cada
 *      filamento (pueden venir de cualquier lado, no solo "hacia arriba"),
 *      en vez del giro alrededor de un eje vertical fijo que usa la
 *      Corrupción.
 *
 * Puramente cliente: no hace falta que el servidor sepa nada de esto, se
 * dispara leyendo directo NecroticAltarBlockEntity#getRitualProgress() /
 * #getOverallOfferingFraction() desde NecroticAltarRenderer (ver ahí el
 * método maybeSpawn más abajo).
 */
public final class AltarFilamentManager {

    private static final List<Filament> ACTIVE = new ArrayList<>();

    // Recuerda el último progreso visto POR ALTAR (clave = posición
    // empaquetada), para poder disparar la ráfaga final una sola vez al
    // cruzar el umbral, en vez de todos los frames mientras esté por
    // encima de él.
    private static final Map<Long, Float> LAST_PROGRESS_BY_ALTAR = new HashMap<>();

    private static final float BURST_THRESHOLD = 0.85F;
    private static final int BURST_COUNT = 7;

    // Generador propio: esto es puramente estético (jitter visual), no
    // necesita ser determinístico ni compartir semilla con nada del juego,
    // así que alcanza con uno fijo creado una sola vez acá.
    private static final RandomSource RANDOM = RandomSource.create(ThreadLocalRandom.current().nextLong());

    private AltarFilamentManager() {
    }

    public static final class Filament {
        public final Vec3 origin;
        public final Vec3 target;
        public final float spawnTick;
        public final float growthTicks;
        public final float duration;
        public final float wobbleAmplitude;
        public final float wobbleFrequency;
        public final float wobblePhase;
        /** Base ortonormal del wobble, perpendicular a (target - origin). */
        public final Vec3 wobbleAxisA;
        public final Vec3 wobbleAxisB;
        public final float wobbleAxisAngle;
        /** Intensidad capturada al nacer (0..1) -- más blanco/brillante cerca del final del ritual. */
        public final float intensity;

        private Filament(Vec3 origin, Vec3 target, float spawnTick, float growthTicks, float duration,
                         float wobbleAmplitude, float wobbleFrequency, float wobblePhase,
                         Vec3 wobbleAxisA, Vec3 wobbleAxisB, float wobbleAxisAngle, float intensity) {
            this.origin = origin;
            this.target = target;
            this.spawnTick = spawnTick;
            this.growthTicks = growthTicks;
            this.duration = duration;
            this.wobbleAmplitude = wobbleAmplitude;
            this.wobbleFrequency = wobbleFrequency;
            this.wobblePhase = wobblePhase;
            this.wobbleAxisA = wobbleAxisA;
            this.wobbleAxisB = wobbleAxisB;
            this.wobbleAxisAngle = wobbleAxisAngle;
            this.intensity = intensity;
        }
    }

    public static List<Filament> active() {
        return ACTIVE;
    }

    public static void pruneExpired(float currentTick) {
        ACTIVE.removeIf(f -> currentTick - f.spawnTick > f.duration);
    }

    /**
     * Llamado una vez por frame por cada altar visible desde
     * NecroticAltarRenderer. Decide si toca hacer nacer un filamento nuevo
     * (o una ráfaga, si se acaba de cruzar BURST_THRESHOLD) y lo agrega a
     * la lista si corresponde. `progress` es getRitualProgress() (0 si el
     * ritual todavía no arrancó).
     */
    public static void maybeSpawn(long altarKey, Vec3 altarCenter, float progress, float currentTick) {
        if (progress <= 0.0F) {
            LAST_PROGRESS_BY_ALTAR.remove(altarKey);
            return;
        }

        float last = LAST_PROGRESS_BY_ALTAR.getOrDefault(altarKey, 0.0F);
        LAST_PROGRESS_BY_ALTAR.put(altarKey, progress);

        // Ráfaga única al cruzar el umbral -- el "momento Scylla".
        if (last < BURST_THRESHOLD && progress >= BURST_THRESHOLD) {
            for (int i = 0; i < BURST_COUNT; i++) {
                spawnOne(altarCenter, progress, currentTick);
            }
        }

        // Ritmo normal: el intervalo baja de ~16 ticks a ~4 ticks a medida
        // que progress avanza -- funnel de tensión creciente, mismo
        // criterio de "easeIn" que ya usa el temblor de cámara del altar.
        float spawnChancePerTick = Mth.lerp(progress * progress, 1.0F / 16.0F, 1.0F / 4.0F);
        if (RANDOM.nextFloat() < spawnChancePerTick) {
            spawnOne(altarCenter, progress, currentTick);
        }
    }

    private static void spawnOne(Vec3 altarCenter, float progress, float currentTick) {
        // El radio del que "vienen" los filamentos se va achicando a
        // medida que avanza el ritual -- como si el altar fuera tirando
        // cada vez con más fuerza y alcanzara cada vez más cerca.
        double radius = Mth.lerp(progress, 6.0D, 2.2D) * (0.7D + RANDOM.nextDouble() * 0.6D);
        double yaw = RANDOM.nextDouble() * Math.PI * 2.0D;
        double pitch = Math.toRadians(RANDOM.nextDouble() * 50.0D - 10.0D); // sobre todo desde arriba/costado

        double ox = Math.cos(yaw) * Math.cos(pitch) * radius;
        double oy = Math.sin(pitch) * radius + 0.6D;
        double oz = Math.sin(yaw) * Math.cos(pitch) * radius;
        Vec3 origin = altarCenter.add(ox, oy, oz);

        Vec3 dir = altarCenter.subtract(origin);
        double len = dir.length();
        if (len < 1.0e-4) {
            return;
        }
        dir = dir.scale(1.0D / len);

        // Base ortonormal perpendicular a 'dir', para poder hacer serpentear
        // el filamento en cualquier plano (no solo "alrededor de un eje
        // vertical", porque acá los filamentos vienen de cualquier lado).
        Vec3 helper = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 axisA = dir.cross(helper).normalize();
        Vec3 axisB = dir.cross(axisA).normalize();

        float growthTicks = (float) Mth.lerp(progress, 22.0, 9.0); // más rápido cerca del final
        float duration = growthTicks + (float) Mth.lerp(progress, 14.0, 6.0);

        ACTIVE.add(new Filament(
                origin, altarCenter, currentTick, growthTicks, duration,
                (float) (0.18 + RANDOM.nextDouble() * 0.30), // amplitud del wobble
                (float) (1.5 + RANDOM.nextDouble() * 2.0),   // frecuencia
                (float) (RANDOM.nextDouble() * Math.PI * 2.0), // fase
                axisA, axisB, (float) (RANDOM.nextDouble() * Math.PI * 2.0), // ángulo de mezcla entre axisA/axisB
                progress
        ));

        if (ACTIVE.size() > 220) {
            // Válvula de seguridad: si por lo que sea se acumulan (varios
            // altares en ritual al mismo tiempo, lag del cliente que no
            // llega a podar a tiempo), no dejar que crezca sin límite.
            ACTIVE.remove(0);
        }
    }
}
