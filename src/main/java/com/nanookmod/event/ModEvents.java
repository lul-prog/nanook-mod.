package com.nanookmod.event;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.NecroticKnightEntity;
import com.nanookmod.entity.TwilightPortalEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Eventos de Forge que no cuelgan de ninguna entidad en particular (a
 * diferencia de ClientEvents, que es solo registro de renderers/overlays
 * del lado cliente) -- este va en el bus FORGE, no el MOD bus, porque
 * ProjectileImpactEvent (y el resto de los eventos de gameplay, como los
 * de LivingEntity o Entity en general) se disparan ahí.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEvents {

    /**
     * "Esquive fantasma": el NecroKnight y los portales necróticos son inmunes a
     * PROYECTILES, pero en vez de simplemente absorber el golpe (que se vería como si
     * el jugador estuviera pegándole a una pared), CANCELAMOS el impacto antes de que
     * llegue a procesarse -- el proyectil sigue de largo con la trayectoria que traía,
     * atravesándolo literalmente. En el Knight además dispara un parpadeo a casi
     * invisible (ver NecroticKnightEntity#triggerArrowPhase); en el portal, unas chispas
     * y un tintineo.
     *
     * CAMBIOS de esta revisión:
     *  - Antes solo AbstractArrow (flechas). Ahora CUALQUIER Projectile: tridentes, bolas
     *    de nieve, huevos, perlas, proyectiles de otros mods.
     *  - Antes solo el Knight. Ahora también TwilightPortalEntity.
     *  - Prioridad HIGHEST. Importante: NecromanticChainHandler también escucha este
     *    evento (aplica el daño acumulado de la cadena cuando el proyectil de un jugador
     *    pega). Como este handler cancela primero y ese no recibe eventos cancelados, la
     *    cadena NO gasta su carga contra un objetivo inmune. Con prioridad normal el orden
     *    dependía de cuál se registrara antes, y la carga podía perderse contra la
     *    inmunidad sin hacer daño.
     *
     * Complemento: NecroticKnightEntity#hurt y TwilightPortalEntity#hurt rechazan el daño
     * de proyectil que llegue por otro camino (mods que llaman a hurt() directo).
     */
    @SuppressWarnings("deprecation") // setCanceled sigue siendo la forma correcta en Forge 1.20.1 -- el warning es prep para versiones futuras (NeoForge), no aplica todavía acá
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) {
            return;
        }
        Entity hit = entityHit.getEntity();
        if (hit.level().isClientSide) {
            return;
        }

        if (hit instanceof NecroticKnightEntity knight) {
            event.setCanceled(true);
            knight.triggerArrowPhase();
        } else if (hit instanceof TwilightPortalEntity portal) {
            event.setCanceled(true);
            if (portal.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        projectile.getX(), projectile.getY(), projectile.getZ(), 4, 0.15D, 0.15D, 0.15D, 0.02D);
                serverLevel.playSound(null, portal.blockPosition(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 0.6F, 0.7F);
            }
        }
    }

    /**
     * Robo de vida del Knight (NUEVO): se cura una fracción del daño REAL que le hace a un
     * jugador. LivingDamageEvent trae el daño ya mitigado por armadura y efectos, así que
     * un jugador bien equipado le da menos vida al jefe que uno sin armadura. Hacerlo acá,
     * en vez de dentro de cada ataque, cubre thrust, guard slap, charge, onda y rayo a la
     * vez. Ver NecroticKnightEntity#applyLifesteal.
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (event.getSource().getEntity() instanceof NecroticKnightEntity knight) {
            knight.applyLifesteal(event.getAmount());
        }
    }
}
