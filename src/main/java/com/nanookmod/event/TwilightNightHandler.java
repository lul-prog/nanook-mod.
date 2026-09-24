package com.nanookmod.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import com.nanookmod.network.ModNetworking;
import com.nanookmod.network.SyncTwilightNightPacket;

/**
 * Fuente única de verdad de la "Luna Crepuscular": a diferencia de la Noche
 * Escarchada (que se calcula de forma determinística a partir del día +
 * semilla, ver FrostedNightHandler), esta la prende/apaga directamente el
 * ritual del Altar Necromante -- así que acá no hay fórmula, es un simple
 * flag que el servidor controla y le avisa al cliente por red cada vez que
 * cambia (no hace falta re-sincronizar la semilla en cada login como con la
 * Escarchada, esto es más parecido a "está lloviendo sí/no").
 *
 * ===================================================================
 * v2 -- TRANSICIÓN PROGRESIVA (antes: salto instantáneo a medianoche)
 * ===================================================================
 * PEDIDO: "en lugar de que todo se vuelva noche de la nada... que aparezca
 * de forma progresiva". Antes, {@code setActive(level, true)} saltaba
 * {@code level.setDayTime()} directo a medianoche en UN SOLO tick, y encima
 * se llamaba desde {@code NecroticKnightEntity#tickSpawnSequence} -- es
 * decir, en el MISMO tick en que el Knight ya estaba apareciendo, junto con
 * la explosión de knockback y el sonido de invocación. Todo junto, de
 * golpe.
 *
 * Ahora el flujo es:
 *   1. {@link #beginTransition} se llama al EMPEZAR el ritual del altar
 *      (NecroticAltarBlockEntity#startRitual), no cuando el Knight ya
 *      apareció. Prende {@code serverTwilightActive} de una (otras partes
 *      del mod que solo miran {@link #isTwilightNight} como flag binario
 *      -- reglas de spawn, etc. -- ya lo ven activo desde ese instante), y
 *      guarda el dayTime real actual como punto de partida de la
 *      transición.
 *   2. Cada tick durante el buildup del ritual (~3.2s por defecto),
 *      {@link #tickTransition} avanza {@code level.setDayTime()} un
 *      poquito más, interpolando entre el dayTime de partida y medianoche
 *      con el MISMO progressT (easeIn) que ya usa el temblor de cámara del
 *      altar -- así el atardecer se acelera EN SINCRO con la tensión que
 *      va subiendo, y llega a noche cerrada justo cuando el Knight se
 *      materializa.
 *   3. El cliente NO recibe un paquete por tick. Recibe UNO solo, al
 *      arrancar, con el gameTime de arranque y la duración -- y calcula su
 *      propio progreso interpolando localmente contra
 *      {@code level.getGameTime()} (que ya se replica solo, como siempre).
 *      Esto es a prueba de lag/paquetes perdidos: si un paquete se pierde,
 *      el cálculo se sigue haciendo bien apenas el reloj del mundo avanza,
 *      no depende de haber recibido cada tick.
 *
 * Los mixins de cielo/lightmap/luna (MixinFrostedSky, MixinLightTexture,
 * MixinFrostedMoon) ahora usan {@link #getTransitionProgress} para
 * MEZCLAR gradualmente en vez de pisar el color de un salto -- ver el
 * comentario en cada uno.
 */
public final class TwilightNightHandler {

    // Cache del lado cliente puro (renderizado) - se actualiza cuando llega
    // SyncTwilightNightPacket. El servidor nunca lee este campo.
    private static volatile boolean clientTwilightActive = false;
    private static volatile long clientTransitionStartGameTime = 0L;
    private static volatile int clientTransitionDurationTicks = 0;

    // Estado real, autoritativo, del lado servidor.
    private static volatile boolean serverTwilightActive = false;
    private static volatile long serverTransitionStartDayTime = 0L;
    private static volatile long serverTransitionTargetDayTime = 0L;
    private static volatile int serverTransitionDurationTicks = 0;
    // gameTime (no dayTime -- el gameTime nunca se congela ni se pisa, a
    // diferencia del dayTime de arriba) en el que arrancó la transición.
    // Solo lo usa getTransitionProgress() del lado servidor.
    private static volatile long serverTransitionStartGameTime = 0L;

    // Tick al que saltamos la hora real del mundo SOLO si invocan al jefe
    // de día (ej. probando con /summon). 18000 = medianoche exacta, con la
    // luna justo en el cenit.
    private static final long FALLBACK_NIGHT_TICK = 18000L;

    // Rango vanilla en el que se considera "de noche" (mismo rango en el
    // que se puede dormir / spawnean mobs a la intemperie). Si ya estamos
    // en este rango cuando invocan al jefe, no tocamos la hora para nada,
    // solo la congelamos donde esté.
    private static final long NIGHT_RANGE_START = 12542L;
    private static final long NIGHT_RANGE_END = 23459L;

    // Valor de doDaylightCycle antes de que arrancara la pelea, para
    // devolverlo tal cual estaba al terminar (por si el jugador ya lo tenía
    // en false por su cuenta).
    private static volatile boolean previousDaylightCycle = true;

    private TwilightNightHandler() {
    }

    /**
     * true si ahora mismo está activa la Luna Crepuscular. Del lado
     * servidor lee el flag real; del lado cliente, el último valor que le
     * llegó por red. Sigue siendo un simple booleano -- lo que cambió con
     * la v2 es que además hay un PROGRESO (0..1) disponible mientras
     * transiciona, ver {@link #getTransitionProgress}.
     */
    public static boolean isTwilightNight(Level level) {
        if (level.isClientSide()) {
            return clientTwilightActive;
        }
        return serverTwilightActive;
    }

    /**
     * Progreso 0..1 de la transición día→noche. 0 = recién arrancó (cielo
     * todavía normal), 1 = noche cerrada. Si {@link #isTwilightNight} es
     * false, da igual lo que devuelva esto (nadie debería consultarlo).
     *
     * Del lado SERVIDOR se calcula con el mismo progressT que ya maneja
     * NecroticAltarBlockEntity (no hace falta duplicar la cuenta acá, pero
     * se deja disponible por las dudas de que algún día otra fuente además
     * del altar quiera arrancar una transición).
     *
     * Del lado CLIENTE se calcula solo, contra el gameTime replicado del
     * nivel -- ver la nota de "a prueba de lag" en el comentario de clase.
     */
    public static float getTransitionProgress(Level level) {
        if (level.isClientSide()) {
            if (clientTransitionDurationTicks <= 0) {
                return 1.0F; // transición "instantánea" (fallback /summon) o sin datos: ya está
            }
            long elapsed = level.getGameTime() - clientTransitionStartGameTime;
            return Mth.clamp(elapsed / (float) clientTransitionDurationTicks, 0.0F, 1.0F);
        }
        if (serverTransitionDurationTicks <= 0) {
            return 1.0F;
        }
        long elapsed = level.getGameTime() - serverTransitionStartGameTime;
        return Mth.clamp(elapsed / (float) serverTransitionDurationTicks, 0.0F, 1.0F);
    }

    /**
     * gameTime en el que arrancó la transición actual, del lado servidor.
     * Solo tiene sentido si {@link #isTwilightNight} es true. Pensado para
     * que TwilightNightSyncHandler pueda replicar el mismo estado que ya
     * tienen los demás jugadores a alguien que se conecta a mitad de
     * transición (o de una Luna Crepuscular ya asentada).
     */
    public static long getServerTransitionStartGameTime() {
        return serverTransitionStartGameTime;
    }

    /**
     * Duración total (en ticks) de la transición actual, del lado servidor.
     * Ver {@link #getServerTransitionStartGameTime()}.
     */
    public static int getServerTransitionDurationTicks() {
        return serverTransitionDurationTicks;
    }

    /**
     * gameTime (replicado, del lado cliente) en el que arrancó la transición actual. Solo tiene
     * sentido si {@link #isTwilightNight} es true del lado cliente. Expuesto para que cualquier
     * efecto puramente visual (ver MixinFasterClouds) pueda arrancar su propia animación desde
     * CERO en el mismo instante en que arranca la Luna Crepuscular, en vez de tener que inventarse
     * su propio timestamp de "cuándo empezó" por separado.
     */
    public static long getClientTransitionStartGameTime() {
        return clientTransitionStartGameTime;
    }

    /**
     * Arranca la transición. SOLO llamar del lado servidor, UNA vez, al
     * empezar el evento que la dispara (hoy: el ritual del altar, ver
     * NecroticAltarBlockEntity#startRitual). Si ya estaba activa, no hace
     * nada -- evita reiniciar la cuenta si por lo que sea se llamara dos
     * veces (dos altares completando ofrendas casi a la vez, por ejemplo).
     *
     * @param durationTicks cuánto debería tardar el cielo en llegar a
     *                       noche cerrada. Pensado para pasarle el mismo
     *                       RITUAL_BUILDUP_TICKS del altar, así la
     *                       transición termina justo cuando aparece el
     *                       Knight.
     */
    public static void beginTransition(ServerLevel level, int durationTicks) {
        if (serverTwilightActive) {
            return; // ya en curso (u otro altar la disparó primero) -- no reiniciar
        }
        serverTwilightActive = true;

        GameRules.BooleanValue daylightRule = level.getGameRules().getRule(GameRules.RULE_DAYLIGHT);
        previousDaylightCycle = daylightRule.get();
        // Congelamos el ciclo YA: a partir de acá, el único que mueve el
        // dayTime somos nosotros (tickTransition), no el server tick
        // normal de Minecraft. Si no lo apagáramos, los dos empujarían el
        // reloj a la vez y la velocidad de "anochecer" dependería del TPS.
        daylightRule.set(false, level.getServer());

        long dayTime = level.getDayTime();
        long timeOfDay = dayTime % 24000L;
        boolean alreadyNight = timeOfDay >= NIGHT_RANGE_START && timeOfDay <= NIGHT_RANGE_END;

        serverTransitionStartDayTime = dayTime;
        if (alreadyNight) {
            // Ya era de noche: no hay atardecer que animar, la transición
            // "visual" (mixins de cielo/lightmap) igual corre por las
            // dudas de que el jugador la vea empezar (invocado de noche
            // temprano, por ejemplo), pero el dayTime no se mueve.
            serverTransitionTargetDayTime = dayTime;
        } else {
            long currentDay = dayTime / 24000L;
            serverTransitionTargetDayTime = currentDay * 24000L + FALLBACK_NIGHT_TICK;
        }
        serverTransitionDurationTicks = Math.max(1, durationTicks);
        serverTransitionStartGameTime = level.getGameTime();

        ModNetworking.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new SyncTwilightNightPacket(true, level.getGameTime(), serverTransitionDurationTicks)
        );
    }

    /**
     * Avanza la transición un tick. Llamar UNA vez por tick de servidor
     * mientras dure el buildup (ver NecroticAltarBlockEntity#tickRitualBuildup),
     * pasándole el MISMO progressT (0..1, ya con su propio easeIn) que se
     * usa para el temblor de cámara -- así el atardecer y la tensión suben
     * exactamente juntos, sin que este método tenga que llevar su propia
     * cuenta de ticks restantes por separado.
     *
     * No hace nada si la transición ya no está activa o si empezó siendo
     * "ya de noche" (serverTransitionStartDayTime == serverTransitionTargetDayTime).
     */
    public static void tickTransition(ServerLevel level, float easedProgress) {
        if (!serverTwilightActive || serverTransitionStartDayTime == serverTransitionTargetDayTime) {
            return;
        }
        long newDayTime = Math.round(Mth.lerp(easedProgress,
                (double) serverTransitionStartDayTime, (double) serverTransitionTargetDayTime));
        level.setDayTime(newDayTime);
    }

    /**
     * Apaga la Luna Crepuscular. SOLO llamar del lado servidor (cuando el
     * Knight muere/despawnea). A diferencia de prenderla, apagarla sigue
     * siendo instantáneo -- no hubo pedido de hacer el AMANECER progresivo,
     * y tiene mucho menos sentido: el jugador acaba de ganarle a un jefe,
     * un fundido lento no aporta nada ahí.
     */
    public static void setActive(ServerLevel level, boolean active) {
        if (!active) {
            deactivate(level);
            return;
        }
        // Compatibilidad hacia atrás: si algo llama a setActive(true)
        // directo (el fallback de NecroticKnightEntity#tickSpawnSequence
        // para spawns sin altar, por /summon) en vez de pasar por
        // beginTransition, lo tratamos como una transición de duración 0
        // -- instantánea, igual que se comportaba la v1 de este archivo.
        beginTransition(level, 0);
    }

    private static void deactivate(ServerLevel level) {
        if (!serverTwilightActive) {
            return;
        }
        serverTwilightActive = false;
        serverTransitionDurationTicks = 0;

        GameRules.BooleanValue daylightRule = level.getGameRules().getRule(GameRules.RULE_DAYLIGHT);
        // Devuelve el ciclo al estado en el que estaba antes de invocar al
        // jefe (no lo forzamos a 'true' a lo bruto, por si el jugador ya lo
        // tenía en false por su cuenta).
        daylightRule.set(previousDaylightCycle, level.getServer());

        ModNetworking.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new SyncTwilightNightPacket(false, 0L, 0)
        );
    }

    /**
     * Llamado desde el handler de red al recibir el paquete. Solo el
     * cliente debería llamar esto (ver SyncTwilightNightPacket#handle).
     */
    public static void setClientActive(boolean active, long transitionStartGameTime, int transitionDurationTicks) {
        clientTwilightActive = active;
        clientTransitionStartGameTime = transitionStartGameTime;
        clientTransitionDurationTicks = transitionDurationTicks;
    }

    /**
     * Fuerza el flag del CLIENTE a 'false' sin pasar por ningún paquete de
     * red. Hace falta llamarlo al salir del mundo actual (ver
     * com.nanookmod.client.ClientWorldResetHandler): si el jugador invoca
     * al jefe y sale del mundo ANTES de que muera, nunca llega el
     * SyncTwilightNightPacket(false) que normalmente apaga esto, y como
     * clientTwilightActive es static, el valor 'true' sobrevive al cambio
     * de mundo dentro del mismo proceso del juego -> el mundo nuevo arranca
     * con el cielo ya teñido. Este reset rompe ese "arrastre".
     */
    public static void resetClient() {
        clientTwilightActive = false;
        clientTransitionDurationTicks = 0;
    }

    /**
     * Igual que {@link #resetClient()} pero para el flag AUTORITATIVO del
     * servidor. Hace falta llamarlo cuando el servidor (integrado o
     * dedicado) se apaga (ver com.nanookmod.event.WorldResetHandler), por
     * la misma razón: si el jefe queda vivo al salir del mundo, este flag
     * también queda "pegado" en true para el próximo mundo que se cargue
     * en el mismo proceso.
     */
    public static void resetServer() {
        serverTwilightActive = false;
        serverTransitionDurationTicks = 0;
        previousDaylightCycle = true;
    }
}