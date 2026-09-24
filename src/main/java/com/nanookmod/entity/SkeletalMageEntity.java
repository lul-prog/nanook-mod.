package com.nanookmod.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import com.nanookmod.registry.ModItems;
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

import javax.annotation.Nullable;

/**
 * Skeletal Mage: portado desde MINION_SKELETAL_MAGE del pack "Awakened
 * Necromancer" (skill Skeletal_Mage-SHOOT). SIEMPRE hostil, igual que
 * warrior/knight.
 *
 * Es puramente a distancia -- no tiene ningún golpe cuerpo a cuerpo en el
 * YAML, así que en vez de perseguir de cerca, mantiene una banda de
 * distancia preferida (retrocede si lo acorralan) como ya hace
 * SnowyBlizzAttackGoal.
 *
 * Timings del YAML (Skeletal_Mage-SHOOT):
 *   delay 2 (mira al objetivo) + gcd 24 (windup, setAI false, reproduce
 *   mage_shoot1/2 al azar) + delay 8 (después del cast) = dispara el
 *   proyectil en tick 10 desde que arranca el golpe. Animación
 *   mage_shoot1/mage_shoot2 dura 1.25s = 25 ticks, así que esa es la
 *   duración total del ataque.
 *
 * Rango: el YAML usa targetwithin d=37 (dispara desde MUY lejos, pensado
 * para un minion que apoya a un jugador). Lo bajé a 18 bloques para que
 * tenga sentido como mob independiente en el mundo -- avisame si lo
 * preferís más fiel al original.
 *
 * Daño + quemadura: el YAML aplica daño directo + un aura de burn con tics
 * de daño propios (Skeletal_Mage_FlameDebuff_BURN). Lo simplifiqué a fuego
 * vanilla real (setSecondsOnFire) en vez de replicar todo el sistema de
 * aura -- el efecto se siente igual (quema unos segundos) con muchísimo
 * menos código.
 */
public class SkeletalMageEntity extends Monster implements GeoEntity {

    private static final EntityDataAccessor<Integer> DATA_ATTACK_STATE =
            SynchedEntityData.defineId(SkeletalMageEntity.class, EntityDataSerializers.INT);

    public static final int ATTACK_NONE = 0;
    public static final int ATTACK_SHOOT = 1;

    private static final int SHOOT_DURATION_TICKS = 25;
    private static final int SHOOT_FIRE_TICK = 10;

    private static final float SHOOT_DAMAGE = 7.0F;
    private static final int BURN_SECONDS = 3;

    public static final double MIN_PREFERRED_RANGE = 6.0D;
    public static final double SHOOT_RANGE = 18.0D;
    public static final double RETREAT_DISTANCE = 6.0D;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SHOOT1_ANIM = RawAnimation.begin().thenPlay("mage_shoot1");
    private static final RawAnimation SHOOT2_ANIM = RawAnimation.begin().thenPlay("mage_shoot2");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("despawn");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int attackTicksElapsed = 0;
    private boolean shotFiredThisAttack = false;
    // Cuál de las dos animaciones (mage_shoot1/2) toca esta vez -- se
    // decide una sola vez al arrancar el ataque, igual que el
    // "randomskill" del YAML, no en cada tick.
    private boolean useShoot1Animation = true;
    private int lastAttackStateAnimated = ATTACK_NONE;

    public SkeletalMageEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noCulling = true; // evita que se congele la animacion al salir del frustum de camara
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, SHOOT_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 26.0D)
                .add(Attributes.ARMOR, 1.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SkeletalMageAttackGoal(this));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ATTACK_STATE, ATTACK_NONE);
    }

    public int getAttackState() {
        return this.entityData.get(DATA_ATTACK_STATE);
    }

    public boolean isAttacking() {
        return getAttackState() != ATTACK_NONE;
    }

    public void triggerShoot() {
        if (!isAttacking()) {
            this.entityData.set(DATA_ATTACK_STATE, ATTACK_SHOOT);
            this.attackTicksElapsed = 0;
            this.shotFiredThisAttack = false;
            this.useShoot1Animation = this.random.nextBoolean();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            tickAttackWindow();
        }
    }

    private void tickAttackWindow() {
        if (getAttackState() == ATTACK_NONE) {
            return;
        }

        attackTicksElapsed++;

        if (!shotFiredThisAttack && attackTicksElapsed >= SHOOT_FIRE_TICK) {
            fireNecroball();
            shotFiredThisAttack = true;
        }

        if (attackTicksElapsed >= SHOOT_DURATION_TICKS) {
            this.entityData.set(DATA_ATTACK_STATE, ATTACK_NONE);
        }
    }

    private void fireNecroball() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            return;
        }

        SkeletalMageNecroball projectile = new SkeletalMageNecroball(this.level(), this);
        projectile.setPos(this.getX(), this.getEyeY() - 0.1D, this.getZ());

        double targetY = target.getY() + target.getBbHeight() * 0.5D;
        Vec3AimHelper.aim(projectile, target.getX(), targetY, target.getZ(), 1.6D);

        this.level().addFreshEntity(projectile);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, this.getSoundSource(), 0.8F, 1.2F);
    }

    public static void applyHitEffects(LivingEntity target, LivingEntity attacker) {
        DamageSource source = attacker != null
                ? attacker.damageSources().mobAttack(attacker)
                : target.damageSources().magic();
        target.hurt(source, SHOOT_DAMAGE);
        target.setSecondsOnFire(BURN_SECONDS);
    }

    // ---- Cabeza al morir por un creeper cargado ----
    // OJO: esto NO se puede hacer con una condición de loot table tipo
    // "type_specific":"creeper" - ese sub-predicate no existe en 1.20.1
    // (por eso la loot table entera fallaba al parsear y no dropeaba nada,
    // ni siquiera el hueso ni la tela). Vanilla implementa este mecanismo
    // así, en Java, no vía datapack.
    @Override
    protected void dropCustomDeathLoot(DamageSource damageSource, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(damageSource, looting, recentlyHit);
        if (damageSource.getEntity() instanceof Creeper creeper && creeper.isPowered()) {
            this.spawnAtLocation(ModItems.SKELETAL_HEAD.get());
        }
    }

    // ---- Sonidos ----

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementPredicate));
        controllers.add(new AnimationController<>(this, "attack", 0, this::attackPredicate));
        controllers.add(new AnimationController<>(this, "death", 0, this::deathPredicate));
    }

    private PlayState movementPredicate(AnimationState<SkeletalMageEntity> state) {
        if (isAttacking() || this.isDeadOrDying()) {
            return PlayState.STOP;
        }
        state.getController().setAnimation(state.isMoving() ? WALK : IDLE);
        return PlayState.CONTINUE;
    }

    private PlayState attackPredicate(AnimationState<SkeletalMageEntity> state) {
        int attackState = getAttackState();

        if (attackState == ATTACK_NONE) {
            lastAttackStateAnimated = ATTACK_NONE;
            return PlayState.STOP;
        }

        if (lastAttackStateAnimated != attackState) {
            state.getController().forceAnimationReset();
            state.getController().setAnimation(useShoot1Animation ? SHOOT1_ANIM : SHOOT2_ANIM);
            lastAttackStateAnimated = attackState;
        }
        return PlayState.CONTINUE;
    }

    private PlayState deathPredicate(AnimationState<SkeletalMageEntity> state) {
        if (this.isDeadOrDying()) {
            state.getController().setAnimation(DEATH);
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}