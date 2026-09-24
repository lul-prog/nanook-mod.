package com.nanookmod.client.render;

import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Guarda los filamentos de energía activos (Corrupción) y decide cuándo
 * nace uno nuevo. Puramente datos -- quien los dibuja es
 * CorruptionFilamentRenderer.
 *
 * v3: nacen ALREDEDOR del jugador (a un radio del cuerpo, no pegados al
 * centro), a distintas alturas de arranque, y su timing depende de su
 * propio largo -- una línea más larga tarda más en subir del todo Y tarda
 * más en desvanecerse; una corta sube más rápido y se apaga más rápido.
 * Eso se logra con una VELOCIDAD DE SUBIDA constante en vez de una
 * duración de crecimiento fija (ver spawnOne): a más distancia por
 * recorrer, más ticks de crecimiento necesita solo por aritmética simple.
 */
public final class CorruptionFilamentManager {

    public static final class Filament {
        public final Entity owner;
        public final float spawnTick;

        /** Ticks que tarda en terminar de subir (la punta llega arriba del todo). */
        public final float growthTicks;
        /** Ticks totales de vida (growth + desvanecido). Después de esto se elimina. */
        public final float duration;

        // Geometría fija generada al nacer.
        public final double originOffsetX; // alrededor del cuerpo (no adentro)
        public final double originOffsetZ;
        public final double baseHeightFraction;  // dónde nace (fracción de bbHeight) -- varía bastante
        public final double riseHeightFraction;  // cuánto sube en total (fracción de bbHeight)
        public final double wobbleAmplitude;
        public final double wobbleFrequency;
        public final double wobblePhase;
        public final double wobbleAxisAngle;

        Filament(Entity owner, float spawnTick, float growthTicks, float duration,
                 double originOffsetX, double originOffsetZ,
                 double baseHeightFraction, double riseHeightFraction,
                 double wobbleAmplitude, double wobbleFrequency, double wobblePhase, double wobbleAxisAngle) {
            this.owner = owner;
            this.spawnTick = spawnTick;
            this.growthTicks = growthTicks;
            this.duration = duration;
            this.originOffsetX = originOffsetX;
            this.originOffsetZ = originOffsetZ;
            this.baseHeightFraction = baseHeightFraction;
            this.riseHeightFraction = riseHeightFraction;
            this.wobbleAmplitude = wobbleAmplitude;
            this.wobbleFrequency = wobbleFrequency;
            this.wobblePhase = wobblePhase;
            this.wobbleAxisAngle = wobbleAxisAngle;
        }
    }

    private static final List<Filament> ACTIVE = new ArrayList<>();
    private static final int MAX_ACTIVE = 64;

    // --- Timing basado en el largo real del filamento (en bloques) ---
    // Más blocks/tick = suben más rápido en general. Ajustable.
    private static final double RISE_SPEED_BLOCKS_PER_TICK = 0.16D;
    // Más ticks/block = las líneas largas se desvanecen más lento
    // (proporcional a su propio largo, así que las cortas siempre son más
    // rápidas en desvanecerse que las largas, sin importar el valor exacto).
    private static final double DISSOLVE_TICKS_PER_BLOCK = 16D;

    private CorruptionFilamentManager() {
    }

    public static void maybeSpawn(Entity owner, int amplifier) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        // Arranca MUY sutil en Corrupción I (amplifier 0) y va subiendo con
        // el nivel. Este maybeSpawn se llama cada 4 ticks (ver
        // CorruptionEffect#isDurationEffectTick), así que con 0.04 de
        // chance sale, en promedio, más o menos 1 filamento por segundo en
        // el nivel más bajo -- ajustá estos dos números si lo querés más
        // o menos notorio.
        float chance = 0.04F + amplifier * 0.045F;
        if (rnd.nextFloat() < chance) {
            spawnOne(owner, rnd, amplifier);
        }
    }

    public static void spawnBurst(Entity owner, int amplifier) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int count = 2 + Math.min(amplifier - 3, 3);
        for (int i = 0; i < count; i++) {
            spawnOne(owner, rnd, amplifier);
        }
    }

    private static void spawnOne(Entity owner, ThreadLocalRandom rnd, int amplifier) {
        if (ACTIVE.size() >= MAX_ACTIVE) {
            return;
        }
        float spawnTick = owner.tickCount + rnd.nextFloat();

        // Alrededor del cuerpo -- radio entre "pegado a la piel" y "un
        // poco separado", ángulo random para que rodeen al jugador en vez
        // de amontonarse de un solo lado. En los niveles más altos (V-VI)
        // el radio se achica un poco -- pedido explícito, para que no se
        // sientan tan "esparcidas" cuando hay más filamentos en pantalla
        // a la vez (por la mayor frecuencia + las ráfagas).
        double bbWidth = owner.getBbWidth();
        double radiusScale = amplifier >= 4 ? (amplifier >= 5 ? 0.70D : 0.85D) : 1.0D;
        double radius = bbWidth * (0.42D + rnd.nextDouble() * 0.32D) * radiusScale;
        double angleDeg = rnd.nextDouble() * 360.0D;
        double angleRad = Math.toRadians(angleDeg);
        double originOffsetX = Math.cos(angleRad) * radius;
        double originOffsetZ = Math.sin(angleRad) * radius;

        // Alturas de arranque -- ahora bien pegadas al piso (los pies),
        // con una variación chica para que no todas nazcan exactamente a
        // la misma altura.
        double baseHeightFraction = rnd.nextDouble() * 0.08D;
        double riseHeightFraction = 0.45D + rnd.nextDouble() * 0.90D; // algunas cortas, algunas bien largas

        double bbHeight = owner.getBbHeight();
        double lengthBlocks = riseHeightFraction * bbHeight;
        float growthTicks = (float) Math.max(4.0D, lengthBlocks / RISE_SPEED_BLOCKS_PER_TICK);
        float dissolveTicks = (float) Math.max(6.0D, lengthBlocks * DISSOLVE_TICKS_PER_BLOCK);
        float duration = growthTicks + dissolveTicks;

        double wobbleAmplitude = 0.04D + rnd.nextDouble() * 0.05D;
        double wobbleFrequency = 0.8D + rnd.nextDouble() * 1.0D;
        double wobblePhase = rnd.nextDouble() * Math.PI * 2.0D;
        double wobbleAxisAngle = rnd.nextDouble() * 360.0D;

        ACTIVE.add(new Filament(owner, spawnTick, growthTicks, duration,
                originOffsetX, originOffsetZ, baseHeightFraction, riseHeightFraction,
                wobbleAmplitude, wobbleFrequency, wobblePhase, wobbleAxisAngle));
    }

    static void pruneExpired(float currentTick) {
        Iterator<Filament> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Filament f = it.next();
            if (!f.owner.isAlive() || currentTick - f.spawnTick > f.duration) {
                it.remove();
            }
        }
    }

    static List<Filament> active() {
        return ACTIVE;
    }
}