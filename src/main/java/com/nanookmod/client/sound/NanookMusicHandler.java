package com.nanookmod.client.sound;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.NanookEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Lado cliente: arranca y para el tema de batalla de Nanook (con fade), y
 * mientras suena calla la música ambiente de Minecraft para que no se pisen.
 *
 * ===================================================================
 * POR QUÉ NO ES IGUAL QUE EL DEL NECROTIC KNIGHT
 * ===================================================================
 * El Knight es un jefe INVOCADO: la pelea empieza cuando aparece y termina
 * cuando muere, así que el servidor manda un paquete a todos al principio y al
 * final. Nanook es un jefe que recorre el mundo: si la música se atara a que
 * exista, sonaría para todos los jugadores desde que spawnea, estén donde
 * estén. Y tampoco sirve "está siendo visto": el rango de tracking de la
 * entidad es de cientos de bloques.
 *
 * Acá la decisión la toma el CLIENTE, cada 5 ticks, con dos datos que ya tiene:
 *   - NanookEntity#isEngaged (dato sincronizado: el oso tiene un objetivo), y
 *   - la distancia al jugador.
 * No hace falta ningún paquete nuevo.
 *
 * REGLAS
 *   - Empieza cuando un Nanook VIVO y en combate está a <= START_RANGE.
 *   - Sigue mientras haya uno a <= STOP_RANGE (mayor que la de inicio: si
 *     fueran iguales, pararte justo en el borde haría que la música empezara y
 *     parara sin parar).
 *   - Si deja de haber pelea, espera LOSE_GRACE_TICKS antes de apagar (un cambio
 *     de objetivo o unos segundos sin línea de visión no deben cortarla).
 *   - Si el jefe muere, desaparece o el jugador muere: fade out YA.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, value = Dist.CLIENT)
public final class NanookMusicHandler {

    /** Distancia a la que EMPIEZA a sonar (bloques). */
    private static final double START_RANGE = 48.0D;
    /** Distancia a la que se CORTA (bloques). */
    private static final double STOP_RANGE = 72.0D;
    private static final int CHECK_INTERVAL_TICKS = 5;
    /** Ticks sin pelea antes de empezar el fade out. */
    private static final int LOSE_GRACE_TICKS = 100;

    private static NanookMusicSound currentSound;
    private static int trackedBossId = -1;
    private static int ticksSincePeace = 0;
    private static int tickCounter = 0;

    private NanookMusicHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || mc.player == null) {
            // Se desconectó o cambió de mundo con la música activa: no la
            // dejamos sonando esperando algo que ya no va a pasar.
            currentSound = null;
            trackedBossId = -1;
            ticksSincePeace = 0;
            return;
        }

        if (++tickCounter >= CHECK_INTERVAL_TICKS) {
            tickCounter = 0;
            evaluate(mc);
        }

        // Mientras nuestra música está activa no dejamos que la ambiente de
        // Minecraft se cuele encima (stopPlaying() es barato si no hay nada
        // sonando). Fuera de la pelea no se toca nada del MusicManager.
        if (currentSound != null) {
            mc.getMusicManager().stopPlaying();
        }
    }

    private static void evaluate(Minecraft mc) {
        if (mc.player.isDeadOrDying()) {
            stop("el jugador murió");
            return;
        }

        NanookEntity boss = findFightingBoss(mc);
        if (boss != null) {
            ticksSincePeace = 0;
            trackedBossId = boss.getId();
            if (currentSound == null) {
                NanookMod.LOGGER.info("Nanook Mod (cliente): arranca el tema de Nanook (entity id {})", boss.getId());
                currentSound = new NanookMusicSound();
                mc.getSoundManager().play(currentSound);
                // Corta lo que estuviera sonando de la música ambiente.
                mc.getMusicManager().stopPlaying();
            }
            return;
        }

        if (currentSound == null) {
            return;
        }

        // No hay pelea. Si el jefe murió o ya no está, fade out inmediato.
        Entity tracked = trackedBossId >= 0 ? mc.level.getEntity(trackedBossId) : null;
        if (tracked == null || !tracked.isAlive()) {
            stop("el jefe murió o desapareció");
            return;
        }

        ticksSincePeace += CHECK_INTERVAL_TICKS;
        if (ticksSincePeace >= LOSE_GRACE_TICKS) {
            stop("terminó la pelea");
        }
    }

    /** El Nanook vivo, en combate y más cercano dentro del rango (con histéresis). */
    private static NanookEntity findFightingBoss(Minecraft mc) {
        double range = currentSound == null ? START_RANGE : STOP_RANGE;
        double rangeSqr = range * range;
        AABB box = mc.player.getBoundingBox().inflate(range);

        List<NanookEntity> candidates = mc.level.getEntitiesOfClass(NanookEntity.class, box,
                e -> e.isAlive() && e.isEngaged() && e.distanceToSqr(mc.player) <= rangeSqr);

        NanookEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (NanookEntity candidate : candidates) {
            double d = candidate.distanceToSqr(mc.player);
            if (d < best) {
                best = d;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static void stop(String reason) {
        if (currentSound != null) {
            NanookMod.LOGGER.info("Nanook Mod (cliente): fade out del tema de Nanook ({})", reason);
            // Solo pedimos el fade out: ella se marca "stopped" sola al terminar.
            currentSound.requestStop();
            currentSound = null;
        }
        trackedBossId = -1;
        ticksSincePeace = 0;
    }
}
