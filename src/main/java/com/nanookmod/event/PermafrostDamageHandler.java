package com.nanookmod.event;

import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PermafrostDamageHandler {

    // DataAccessor para sincronizar la carga entre servidor y cliente
    public static final EntityDataAccessor<Integer> PERMAFROST_CHARGE =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);

    private static final int MAX_CHARGE = 2000; // 100 segundos
    private static final int PARTICLE_THRESHOLD = 1400; // 70%
    private static final int SHAKE_THRESHOLD = 1700; // 85%
    private static final int FREEZE_THRESHOLD = 1000; // 50% - empieza el efecto de congelamiento

    // Registrar el DataAccessor cuando el jugador spawnea
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            // Definir el DataAccessor si no existe
            try {
                player.getEntityData().define(PERMAFROST_CHARGE, 0);
            } catch (Exception e) {
                // Ya está definido, ignorar
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;

        if (player.hasEffect(ModEffects.PERMAFROST.get())) {

            // === INMUNIDAD ===
            // Amplificador 0 (poción básica) = evita la muerte, pero NO detiene la carga ni el temblor/ralentización
            // Amplificador 1+ (poción mejorada) = inmunidad total, la carga no sube y se descongela
            MobEffectInstance immunity = player.getEffect(ModEffects.PERMAFROST_IMMUNITY.get());
            boolean fullImmunity = immunity != null && immunity.getAmplifier() >= 1;
            boolean basicImmunity = immunity != null && immunity.getAmplifier() == 0;

            int currentCharge = player.getEntityData().get(PERMAFROST_CHARGE);

            if (fullImmunity) {
                // Inmunidad total: la carga baja hasta 0 y no se aplica ningún efecto de congelamiento
                if (currentCharge > 0) {
                    currentCharge = Math.max(0, currentCharge - 4);
                    player.getEntityData().set(PERMAFROST_CHARGE, currentCharge);
                }
                if (player.getTicksFrozen() > 0) {
                    player.setTicksFrozen(0);
                }
                return; // No hay más nada que procesar este tick para este jugador
            }

            currentCharge++;

            // Con inmunidad básica, la carga jamás llega al tope que mata (se queda justo debajo)
            if (basicImmunity && currentCharge >= MAX_CHARGE) {
                currentCharge = MAX_CHARGE - 1;
            }

            // Actualizar carga (se sincroniza automáticamente)
            player.getEntityData().set(PERMAFROST_CHARGE, currentCharge);

            // === EFECTO DE CONGELAMIENTO (Freeze) vanilla ===
            // Cuando la carga es > 50%, aplicar efecto de congelamiento progresivo
            if (currentCharge > FREEZE_THRESHOLD) {
                // Calcular intensidad del freeze (0 a 1)
                float freezeIntensity = (float)(currentCharge - FREEZE_THRESHOLD) / (MAX_CHARGE - FREEZE_THRESHOLD);

                // Aplicar ticks de congelamiento (vanilla usa 0-140, donde 140 es máximo)
                int freezeTicks = (int)(freezeIntensity * 140);
                player.setTicksFrozen(freezeTicks);

                // Si el freeze es muy alto, aplicar lentitud
                if (freezeIntensity > 0.7f) {
                    // El freeze vanilla ya ralentiza automáticamente
                }
            }

// === PARTÍCULAS (solo cliente) ===
            if (player.level().isClientSide()) {
                if (currentCharge > PARTICLE_THRESHOLD) {
                    // Partículas de nieve (REDUCIDAS: solo 1 cada 10 ticks)
                    if (currentCharge % 10 == 0) {
                        double offsetX = (Math.random() - 0.5) * 1.5;
                        double offsetY = Math.random() * 1.5;
                        double offsetZ = (Math.random() - 0.5) * 1.5;

                        player.level().addParticle(
                                ParticleTypes.SNOWFLAKE,
                                player.getX() + offsetX,
                                player.getY() + offsetY,
                                player.getZ() + offsetZ,
                                0.0, 0.0, 0.0
                        );
                    }
                }

                if (currentCharge > SHAKE_THRESHOLD) {
                    // Partículas más intensas (REDUCIDAS: solo 2 cada 10 ticks)
                    if (currentCharge % 10 == 0) {
                        for (int i = 0; i < 2; i++) {
                            double offsetX = (Math.random() - 0.5) * 2.0;
                            double offsetY = Math.random() * 2.0;
                            double offsetZ = (Math.random() - 0.5) * 2.0;

                            player.level().addParticle(
                                    ParticleTypes.SNOWFLAKE,
                                    player.getX() + offsetX,
                                    player.getY() + offsetY,
                                    player.getZ() + offsetZ,
                                    0.0, -0.1, 0.0
                            );
                        }
                    }
                }
            }

            // === SONIDO (servidor) ===
            if (currentCharge > SHAKE_THRESHOLD && currentCharge % 20 == 0) {
                float volume = 0.5F + ((currentCharge - SHAKE_THRESHOLD) / 300.0F) * 0.5F;
                player.level().playSound(
                        null,
                        player.blockPosition(),
                        SoundEvents.GLASS_BREAK,
                        SoundSource.PLAYERS,
                        volume,
                        0.3F
                );
            }

            // === MUERTE AL 100% ===
            // (basicImmunity ya deja la carga en MAX_CHARGE - 1, esto es solo una red de seguridad)
            if (currentCharge >= MAX_CHARGE && !basicImmunity) {
                // Sonido final
                player.level().playSound(
                        null,
                        player.blockPosition(),
                        SoundEvents.GLASS_BREAK,
                        SoundSource.PLAYERS,
                        1.0F,
                        0.5F
                );

                // Matar al jugador
                player.hurt(player.damageSources().freeze(), Float.MAX_VALUE);

                // Resetear carga
                player.getEntityData().set(PERMAFROST_CHARGE, 0);
                player.setTicksFrozen(0);
            }
        } else {
            // Si no tiene el efecto, resetear carga
            int currentCharge = player.getEntityData().get(PERMAFROST_CHARGE);
            if (currentCharge > 0) {
                player.getEntityData().set(PERMAFROST_CHARGE, 0);
                player.setTicksFrozen(0);
            }
        }
    }

    // Método público para obtener la carga (usado por el cliente)
    public static int getPermafrostCharge(Player player) {
        return player.getEntityData().get(PERMAFROST_CHARGE);
    }
}