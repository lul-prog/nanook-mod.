package com.nanookmod.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Snowy Blizz: mob de progresión temprana del Mar Primigenio. Portado del
 * pack MythicMobs "Blizz" (variante snowy_blizz). Alterna entre un golpe
 * cuerpo a cuerpo (stab) y una ráfaga de 3 proyectiles de hielo (shoot),
 * según la distancia al objetivo. No usa daño de contacto vanilla: todo
 * el daño sale de los timings de ataque, igual que Nanook.
 *
 * Paleta de sonido: Evoker (illager hechicero), no Vindicator, porque encaja
 * mejor con un caster de hielo que con un illager de hacha.
 */
public class SnowyBlizzEntity extends Monster implements GeoEntity, FrostedBuffed {

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Integer> DATA_ATTACK_STATE =
            SynchedEntityData.defineId(SnowyBlizzEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Boolean> DATA_FROSTED_BUFFED =
            SynchedEntityData.defineId(SnowyBlizzEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int ATTACK_NONE = 0;
    public static final int ATTACK_STAB = 1;
    public static final int ATTACK_SHOOT = 2;

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation STAB_ANIM = RawAnimation.begin().thenPlay("stab");
    private static final RawAnimation SHOOT_ANIM = RawAnimation.begin().thenPlay("shoot_far");

    // --- Stab (cuerpo a cuerpo) ---
    private static final int STAB_DURATION_TICKS = 40;
    private static final int STAB_DAMAGE_TICK = 15;
    private static final float STAB_DAMAGE = 5.0F;
    private static final double STAB_RANGE = 3.0D;

    // --- Shoot (3 proyectiles en ráfaga, mismo espaciado que el YAML original) ---
    private static final int SHOOT_DURATION_TICKS = 26;
    private static final int[] SHOOT_FIRE_TICKS = {5, 13, 21};

    private int attackTicksRemaining = -1;
    private int lastAttackStateAnimated = ATTACK_NONE;
    private boolean stabDamageAppliedThisAttack = false;
    private final Set<Integer> shotsFiredThisAttack = new HashSet<>();

    public SnowyBlizzEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noCulling = true; // evita que se congele la animacion al salir del frustum de camara
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.16D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.ARMOR, 0.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.getEntityData().define(DATA_ATTACK_STATE, ATTACK_NONE);
        this.getEntityData().define(DATA_FROSTED_BUFFED, false);
    }

    public boolean isFrostedBuffed() {
        return this.getEntityData().get(DATA_FROSTED_BUFFED);
    }

    public void setFrostedBuffed(boolean buffed) {
        this.getEntityData().set(DATA_FROSTED_BUFFED, buffed);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new SnowyBlizzAttackGoal(this));
        this.goalSelector.addGoal(4, new AttackFrostCropGoal(this, 1.0D, 16));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---- Soporte GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementPredicate));
    }

    private PlayState movementPredicate(AnimationState<SnowyBlizzEntity> state) {
        int attackState = this.getAttackState();

        if (attackState != ATTACK_NONE) {
            if (this.lastAttackStateAnimated != attackState) {
                this.lastAttackStateAnimated = attackState;
                return state.setAndContinue(pickAnimationFor(attackState));
            }
            return PlayState.CONTINUE;
        }

        this.lastAttackStateAnimated = ATTACK_NONE;

        if (state.isMoving()) {
            return state.setAndContinue(WALK_ANIM);
        }
        return state.setAndContinue(IDLE_ANIM);
    }

    private RawAnimation pickAnimationFor(int attackState) {
        return attackState == ATTACK_STAB ? STAB_ANIM : SHOOT_ANIM;
    }

    // ---- Lógica de ataques ----

    public int getAttackState() {
        return this.getEntityData().get(DATA_ATTACK_STATE);
    }

    public boolean isAttacking() {
        return this.getAttackState() != ATTACK_NONE;
    }

    public void triggerStabAttack() {
        if (!this.isAttacking()) {
            this.startAttack(ATTACK_STAB, STAB_DURATION_TICKS);
            playCastSound(SoundEvents.SNOW_HIT, 1.0F, 0.8F);
        }
    }

    public void triggerShootAttack() {
        if (!this.isAttacking()) {
            this.startAttack(ATTACK_SHOOT, SHOOT_DURATION_TICKS);
            playCastSound(SoundEvents.VEX_HURT, 1.0F, 1.0F);
        }
    }

    private void startAttack(int attackType, int durationTicks) {
        this.attackTicksRemaining = durationTicks;
        this.stabDamageAppliedThisAttack = false;
        this.shotsFiredThisAttack.clear();
        this.getEntityData().set(DATA_ATTACK_STATE, attackType);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            if (this.attackTicksRemaining > 0) {
                int attackState = this.getAttackState();
                int totalDuration = totalDurationFor(attackState);
                int elapsed = totalDuration - this.attackTicksRemaining;

                if (attackState == ATTACK_STAB) {
                    if (elapsed >= STAB_DAMAGE_TICK && !this.stabDamageAppliedThisAttack) {
                        applyStabDamage();
                        this.stabDamageAppliedThisAttack = true;
                    }
                } else if (attackState == ATTACK_SHOOT) {
                    for (int fireTick : SHOOT_FIRE_TICKS) {
                        if (elapsed >= fireTick && !this.shotsFiredThisAttack.contains(fireTick)) {
                            fireIceball();
                            this.shotsFiredThisAttack.add(fireTick);
                        }
                    }
                }

                this.attackTicksRemaining--;
            } else if (this.attackTicksRemaining == 0) {
                this.attackTicksRemaining = -1;
                this.stabDamageAppliedThisAttack = false;
                this.shotsFiredThisAttack.clear();
                this.getEntityData().set(DATA_ATTACK_STATE, ATTACK_NONE);
            }
        } else {
            com.nanookmod.client.particles.FrostedBuffParticleHelper.spawnIfBuffed(this, isFrostedBuffed());
        }
    }

    private int totalDurationFor(int attackState) {
        return attackState == ATTACK_SHOOT ? SHOOT_DURATION_TICKS : STAB_DURATION_TICKS;
    }

    private void applyStabDamage() {
        forEachTargetInCone(STAB_RANGE, 120.0, player ->
                player.hurt(this.damageSources().mobAttack(this), STAB_DAMAGE));
    }

    private void fireIceball() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            return;
        }

        SnowyBlizzIceball projectile = new SnowyBlizzIceball(this.level(), this);
        projectile.setPos(this.getX(), this.getEyeY() - 0.1, this.getZ());

        double targetY = target.getY() + target.getBbHeight() * 0.5D;
        Vec3AimHelper.aim(projectile, target.getX(), targetY, target.getZ(), 1.6D);

        this.level().addFreshEntity(projectile);
        playCastSound(SoundEvents.GLASS_HIT, 0.8F, 1.4F);
    }

    private void playCastSound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                sound, this.getSoundSource(), volume, pitch);
    }

    private void forEachTargetInCone(double radius, double coneAngleDegrees, java.util.function.Consumer<Player> action) {
        for (Player player : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(radius))) {
            if (this.distanceTo(player) <= radius && isInFrontCone(player, coneAngleDegrees)) {
                action.accept(player);
            }
        }
    }

    private boolean isInFrontCone(Player player, double coneAngleDegrees) {
        double dx = player.getX() - this.getX();
        double dz = player.getZ() - this.getZ();
        double angleToPlayer = Math.toDegrees(Math.atan2(dz, dx));
        double yaw = Mth.wrapDegrees(this.getYRot() + 90.0);
        double diff = Mth.wrapDegrees(angleToPlayer - yaw);
        return Math.abs(diff) <= coneAngleDegrees / 2.0;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT ;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ELDER_GUARDIAN_DEATH;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}