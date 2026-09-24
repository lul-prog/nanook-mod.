package com.nanookmod.event;

import com.nanookmod.NanookMod;
import com.nanookmod.capability.NecromanticChainCapability;
import com.nanookmod.network.ModNetworking;
import com.nanookmod.network.SyncNecromanticChainPacket;
import com.nanookmod.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NecromanticChainHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player) {
            NecromanticChainCapability.INecromanticChainCapability cap = NecromanticChainCapability.getCap(player);
            if (cap != null && cap.isActive()) {
                cap.deactivate(player);
                // player.playSound(ModSounds.NECROMANTIC_CHAIN_DEACTIVATE.get(), 1.0F, 1.0F); // COMENTADO
                if (!player.level().isClientSide()) {
                    cap.syncToClient((ServerPlayer) player);
                }
            }
        }
    }

    private static void spawnHitParticles(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double cx = target.getX();
        double cy = target.getY() + target.getBbHeight() * 0.5;
        double cz = target.getZ();

        // UNA sola partícula, grande, clavada en el centro del objetivo
        // (estilo "POW" de cómic) -- count=1 y spread/velocidad en 0 para
        // que no la disperse ni la mueva.
        serverLevel.sendParticles(com.nanookmod.registry.ModParticles.NECROMANTIC_IMPACT.get(),
                cx, cy, cz, 1, 0.0, 0.0, 0.0, 0.0);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Entity attacker = event.getEntity();
        if (attacker instanceof Player player) {
            NecromanticChainCapability.INecromanticChainCapability cap = NecromanticChainCapability.getCap(player);
            if (cap != null && cap.isActive() && cap.getAccumulatedDamage() > 0) {
                float bonusDamage = cap.getAccumulatedDamage();
                LivingEntity target = (LivingEntity) event.getTarget();
                DamageSource source = player.damageSources().playerAttack(player);
                target.hurt(source, bonusDamage);
                spawnHitParticles(target);
                cap.resetDamage();
                // El golpe cargado es el "finisher": al soltarlo, la
                // habilidad se apaga ahí mismo (ya no importa que quede
                // tiempo, ni te tienen que golpear a ti para cancelarla).
                cap.deactivate(player);
                if (!player.level().isClientSide()) {
                    cap.syncToClient((ServerPlayer) player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        net.minecraft.world.entity.projectile.Projectile projectile = event.getProjectile();
        Entity owner = projectile.getOwner();
        if (!(owner instanceof Player player)) {
            return;
        }

        NecromanticChainCapability.INecromanticChainCapability cap = NecromanticChainCapability.getCap(player);
        if (cap == null || !cap.isActive() || cap.getAccumulatedDamage() <= 0) {
            return;
        }

        HitResult hit = event.getRayTraceResult();
        if (hit.getType() != HitResult.Type.ENTITY) {
            return;
        }

        EntityHitResult entityHit = (EntityHitResult) hit;
        Entity target = entityHit.getEntity();
        if (!(target instanceof LivingEntity livingTarget)) {
            return;
        }

        float bonusDamage = cap.getAccumulatedDamage();
        DamageSource source;
        if (projectile instanceof AbstractArrow arrow) {
            source = player.damageSources().arrow(arrow, player);
        } else if (projectile instanceof ThrowableItemProjectile thrown) {
            source = player.damageSources().thrown(thrown, player);
        } else {
            source = player.damageSources().mobProjectile(projectile, player);
        }
        livingTarget.hurt(source, bonusDamage);
        spawnHitParticles(livingTarget);
        cap.resetDamage();
        cap.deactivate(player);

        if (!player.level().isClientSide()) {
            cap.syncToClient((ServerPlayer) player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        // Solo nos interesa el "target" de IA de combate del mob (no el de
        // comportamientos como el de aldeanos huyendo, etc.)
        if (event.getTargetType() != LivingChangeTargetEvent.LivingTargetType.MOB_TARGET) {
            return;
        }
        LivingEntity target = event.getNewTarget();
        if (target instanceof Player player) {
            NecromanticChainCapability.INecromanticChainCapability cap = NecromanticChainCapability.getCap(player);
            if (cap != null && cap.isActive()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getEntity().level().isClientSide()) {
            handleClientTick(player);
        } else {
            handleServerTick(player, (ServerLevel) player.level());
        }
    }

    private static void handleServerTick(Player player, ServerLevel level) {
        NecromanticChainCapability.INecromanticChainCapability cap = NecromanticChainCapability.getCap(player);
        if (cap != null) {
            cap.tick(player, level);
        }
    }

    private static void handleClientTick(Player player) {
        NecromanticChainCapability.INecromanticChainCapability cap = NecromanticChainCapability.getCap(player);
        if (cap != null && cap.isActive()) {
            Level level = player.level();
            if (player.tickCount % NecromanticChainCapability.PARTICLE_INTERVAL == 0) {
                level.addParticle(
                        ModParticles.CREPUSCULAR_MOTE.get(),
                        player.getX() + (player.getRandom().nextDouble() - 0.5) * player.getBbWidth(),
                        player.getY() + player.getRandom().nextDouble() * player.getBbHeight(),
                        player.getZ() + (player.getRandom().nextDouble() - 0.5) * player.getBbWidth(),
                        0, 0.05, 0
                );
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player && !event.getEntity().level().isClientSide()) {
            NecromanticChainCapability.INecromanticChainCapability cap = NecromanticChainCapability.getCap(player);
            if (cap != null && cap.isActive()) {
                cap.syncToClient((ServerPlayer) player);
            }
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && !event.getEntity().level().isClientSide()) {
            NecromanticChainCapability.INecromanticChainCapability cap = NecromanticChainCapability.getCap(target);
            if (cap != null && cap.isActive()) {
                ServerPlayer trackingPlayer = (ServerPlayer) event.getEntity();
                cap.syncToClient(trackingPlayer);
            }
        }
    }
}