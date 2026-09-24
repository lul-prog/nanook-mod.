package com.nanookmod.entity;

import com.nanookmod.registry.ModEntities;
import com.nanookmod.registry.ModParticles;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Meteoro de hielo del ataque nanook_channelling (el "type=METEOR" del
 * proyectil original). Cae en vertical desde bien arriba y explota al tocar
 * el suelo, haciendo daño en área -- no hace falta que te pegue directo.
 *
 * El área al explotar es la parte importante del diseño: el meteoro solo es
 * esquivable si el jugador SE MUEVE (por eso el canalizado deja a Nanook
 * quieto un buen rato -- es la ventana para pegarle, pero tenés que estar
 * moviéndote mientras lo hacés).
 *
 * VFX:
 *   - Estela: BLOCK de ICE y GLOW (azuladas, se dejaron como estaban) +
 *     SNOWY_DUST propio reemplazando la nube blanca vanilla.
 *   - IMPACTO: ModParticles.FROST_IMPACT, el estallido azul de 7 cuadros
 *     (arte directional_impact_001_small_blue). Es el "golpe" visual del
 *     meteoro, y se spawnea una sola vez, bien grande, en el punto exacto.
 */
public class NanookIceMeteorProjectile extends AbstractArrow {

    /** Velocidad de caída, en bloques/tick. */
    public static final double FALL_SPEED = 1.1D;

    private static final float DIRECT_HIT_DAMAGE = 6.0F;
    private static final float EXPLOSION_DAMAGE = 4.0F;
    private static final double EXPLOSION_RADIUS = 2.2D;
    private static final int FREEZE_TICKS = 40;
    private static final int MAX_LIFETIME_TICKS = 120;

    public NanookIceMeteorProjectile(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        this.setNoGravity(true); // la caída la manejamos nosotros, a velocidad constante
    }

    public NanookIceMeteorProjectile(Level level, LivingEntity shooter) {
        super(ModEntities.NANOOK_ICE_METEOR.get(), shooter, level);
        this.setNoGravity(true);
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    public ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (result.getEntity() instanceof LivingEntity victim && victim != this.getOwner()) {
            LivingEntity owner = this.getOwner() instanceof LivingEntity living ? living : null;
            victim.hurt(this.damageSources().mobAttack(owner), DIRECT_HIT_DAMAGE);
        }
        explode();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        explode();
    }

    /**
     * Explosión de hielo al impactar. No rompe bloques (no es una explosión
     * de verdad) -- solo daño en área + congelamiento + partículas.
     */
    private void explode() {
        if (this.level().isClientSide) {
            return;
        }

        LivingEntity owner = this.getOwner() instanceof LivingEntity living ? living : null;
        DamageSource source = this.damageSources().mobAttack(owner);

        AABB area = this.getBoundingBox().inflate(EXPLOSION_RADIUS);
        List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != owner && !(e instanceof NanookEntity)
                        && this.distanceTo(e) <= EXPLOSION_RADIUS + 1.0D);

        for (LivingEntity victim : nearby) {
            victim.hurt(source, EXPLOSION_DAMAGE);
            victim.setTicksFrozen(FREEZE_TICKS);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0));

            // Empujoncito hacia afuera para que el jugador SIENTA el impacto
            // aunque no lo haya matado.
            Vec3 away = victim.position().subtract(this.position());
            if (away.lengthSqr() > 1.0E-4) {
                away = away.normalize().scale(0.4D);
                victim.setDeltaMovement(victim.getDeltaMovement().add(away.x, 0.25D, away.z));
                if (victim instanceof ServerPlayer sp) {
                    sp.hurtMarked = true;
                }
            }
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            // --- El estallido azul pedido: una sola partícula grande,
            // centrada, sin dispersión (count=1, offsets 0). ---
            serverLevel.sendParticles(ModParticles.FROST_IMPACT.get(),
                    this.getX(), this.getY() + 0.4D, this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            // Un par más, apenas corridas, para que el impacto tenga volumen.
            serverLevel.sendParticles(ModParticles.FROST_IMPACT.get(),
                    this.getX(), this.getY() + 1.1D, this.getZ(), 2, 0.5D, 0.3D, 0.5D, 0.0D);

            // Escombros de hielo: ESTAS SE QUEDAN (son las azuladas).
            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()),
                    this.getX(), this.getY(), this.getZ(), 25, 0.8D, 0.4D, 0.8D, 0.1D);

            // Antes: CLOUD + SNOWFLAKE (blancas vanilla) -> polvo propio.
            serverLevel.sendParticles(ModParticles.SNOWY_DUST.get(),
                    this.getX(), this.getY() + 0.3D, this.getZ(), 26, 1.0D, 0.4D, 1.0D, 0.06D);

            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GLASS_BREAK, this.getSoundSource(), 1.2F, 0.8F);
        }

        this.discard();
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            return;
        }

        // Caída a velocidad constante (sin acelerar) -- así el tiempo de
        // reacción del jugador es siempre el mismo sin importar desde qué
        // altura salió el meteoro.
        this.setDeltaMovement(0.0D, -FALL_SPEED, 0.0D);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()),
                    this.getX(), this.getY(), this.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.01D);
            serverLevel.sendParticles(ParticleTypes.GLOW,
                    this.getX(), this.getY(), this.getZ(), 1, 0.15D, 0.15D, 0.15D, 0.0D);
            // Estela de polvo blanco propio (antes no había; ayuda a leer la
            // trayectoria desde lejos).
            serverLevel.sendParticles(ModParticles.SNOWY_DUST.get(),
                    this.getX(), this.getY() + 0.5D, this.getZ(), 1, 0.15D, 0.1D, 0.15D, 0.01D);
        }

        if (this.tickCount > MAX_LIFETIME_TICKS) {
            this.discard();
        }
    }

    @Override
    protected void tickDespawn() {
        this.discard();
    }
}
