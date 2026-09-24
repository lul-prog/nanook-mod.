package com.nanookmod.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Fuente única de verdad para la "Noche Escarchada": una fase lunar especial en
 * la que la luna cambia de textura (MixinFrostedMoon), el cielo se tiñe de azul
 * hielo (MixinFrostedSky), caen estrellas fugaces (ShootingStarSkyRenderer),
 * los cultivos crecen más rápido (FrostCrop) y los mobs propios del mod son
 * más fuertes (FrostedNightMobBuffHandler).
 *
 * Recibe un Level (la clase común a ClientLevel y ServerLevel) para poder
 * llamarse tanto desde lógica de cliente (renderizado) como de servidor
 * (crecimiento de cultivos, mobs, drops...).
 *
 * IMPORTANTE: el cálculo se basa en el día in-game MEZCLADO con la semilla
 * del mundo. Antes solo usaba el día, así que TODOS los mundos tenían
 * exactamente el mismo patrón (día 0 siempre escarchada, día 10 siempre
 * escarchada...). En el servidor la semilla se lee directo
 * (ServerLevel#getSeed()); el cliente no tiene esa API disponible de forma
 * confiable, así que la recibe por red al conectarse (ver
 * com.nanookmod.network.SyncFrostedNightSaltPacket) y la guarda aquí en
 * {@link #clientWorldSalt}.
 */
public class FrostedNightHandler {

    // Pon esto en 'true' solo mientras pruebas en tu mundo. Debe quedar en
    // 'false' antes de compilar una versión para jugar/publicar normalmente.
    private static final boolean DEBUG_FORCE_FROSTED_NIGHT = false;

    // Probabilidad de que CUALQUIER noche (sin importar la fase lunar) sea
    // Noche Escarchada (0.0 a 1.0). 0.08 = 1 de cada 12.5 noches en
    // promedio (dentro del rango pedido de 1 cada 10-15).
    private static final double FROSTED_NIGHT_CHANCE = 0.08;

    // Pon esto en 'true' para ver por consola (no chat, la consola de
    // Gradle/logs) la semilla que está usando cada lado. Sirve para
    // confirmar que el cliente SÍ está recibiendo una semilla distinta por
    // mundo (si worldSalt sale igual en 2 mundos distintos, ahí está el bug).
    private static final boolean DEBUG_LOG_SALT = false;
    private static long lastLoggedDayClient = Long.MIN_VALUE;
    private static long lastLoggedDayServer = Long.MIN_VALUE;

    // Solo se usa del lado del CLIENTE puro (renderizado). Se llena cuando
    // llega el paquete de red al conectarse. Hasta que llegue (una fracción
    // de segundo tras conectarse), vale 0, así que por un instante muy breve
    // el cliente podría calcular un resultado distinto al servidor; no
    // importa en la práctica porque nada se ve en ese instante.
    private static long clientWorldSalt = 0L;

    public static void setClientWorldSalt(long salt) {
        clientWorldSalt = salt;
    }

    private FrostedNightHandler() {
    }

    /**
     * true si actualmente está activa la Noche Escarchada.
     */
    public static boolean isFrostedNight(Level level) {
        if (DEBUG_FORCE_FROSTED_NIGHT) {
            return true;
        }

        // Mutex con la Luna Crepuscular: mientras el NecroticKnight esté
        // vivo (TwilightNightHandler.isTwilightNight == true), la Noche
        // Escarchada queda forzada a 'false', sin importar lo que diga el
        // cálculo de día+semilla de más abajo. Esto es lo que evita que
        // ambos eventos se superpongan (cielo, luna, buffs de mobs propios,
        // crecimiento de cultivos, etc. -- todo lo que consulte
        // isFrostedNight() queda cubierto por este único chequeo).
        // La Crepuscular tiene prioridad porque la dispara una acción
        // explícita del jugador (invocar al jefe), mientras que la
        // Escarchada es puramente ambiental/aleatoria.
        if (TwilightNightHandler.isTwilightNight(level)) {
            return false;
        }

        long dayTime = level.getDayTime();
        long timeOfDay = dayTime % 24000;
        if (timeOfDay < 13000 || timeOfDay > 23000) return false;

        long dayIndex = dayTime / 24000;
        long worldSalt = (level instanceof ServerLevel serverLevel) ? serverLevel.getSeed() : clientWorldSalt;

        if (DEBUG_LOG_SALT) {
            boolean clientSide = level.isClientSide();
            long lastLogged = clientSide ? lastLoggedDayClient : lastLoggedDayServer;
            if (dayIndex != lastLogged) {
                if (clientSide) lastLoggedDayClient = dayIndex; else lastLoggedDayServer = dayIndex;
                System.out.println("[Nanook DEBUG] lado=" + (clientSide ? "CLIENTE" : "SERVIDOR")
                        + " dia=" + dayIndex + " worldSalt=" + worldSalt);
            }
        }

        RandomSource dayRandom = RandomSource.create(mixSeed(dayIndex ^ mixSeed(worldSalt)));
        return dayRandom.nextDouble() < FROSTED_NIGHT_CHANCE;
    }

    /**
     * Mezcla bien un número (SplitMix64) para que semillas consecutivas
     * (0, 1, 2, 3...) produzcan resultados de RandomSource totalmente
     * distintos entre sí, en vez de valores pegados/correlacionados (esto
     * fue un bug real que tuvimos: con RandomSource.create(dayIndex) sin
     * mezclar, muchísimos días seguidos daban siempre el mismo resultado).
     */
    private static long mixSeed(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }
}