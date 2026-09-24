package com.nanookmod.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
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
 * Skeletal Warrior: portado desde el pack MythicMobs "Awakened Necromancer"
 * (MINION_SKELETAL_WARRIOR / skills Skeletal_Warrior-SLASH1 y SLASH2).
 *
 * A diferencia del minion original (que era neutral y solo peleaba para su
 * invocador), este mob es SIEMPRE hostil - ataca a cualquier jugador que
 * entre en su rango de detección, como cualquier monstruo normal del mod.
 *
 * Timings sacados directo del YAML (delay + gcd del skill, convertido a
 * ticks de animación real del .bbmodel exportado):
 *   SLASH1: delay 2 + delay 5 = golpea en tick 7. Animación "attack1" (1.2s = 24 ticks).
 *   SLASH2: delay 2 + delay 7 = golpea en tick 9. Animación "attack2" (1.15s = 23 ticks).
 * El YAML alterna ambos con randomskill, así que este mob hace lo mismo.
 *
 * Mismo patrón que GnutEntity/NanookEntity: DATA_ATTACK_STATE sincronizado,
 * startAttack(), tick de telegraph para aplicar el daño real.
 */
public class SkeletalWarriorEntity extends Monster implements GeoEntity {

    private static final EntityDataAccessor<Integer> DATA_ATTACK_STATE =
            SynchedEntityData.defineId(SkeletalWarriorEntity.class, EntityDataSerializers.INT);

    public static final int ATTACK_NONE = 0;
    public static final int ATTACK_SLASH1 = 1;
    public static final int ATTACK_SLASH2 = 2;

    // --- Slash 1 ---
    private static final int SLASH1_DURATION_TICKS = 24;
    private static final int SLASH1_DAMAGE_TICK = 7;

    // --- Slash 2 ---
    private static final int SLASH2_DURATION_TICKS = 23;
    private static final int SLASH2_DAMAGE_TICK = 9;

    // Comunes a ambos golpes (el YAML usa el mismo daño/rango para los dos)
    private static final float SLASH_DAMAGE = 6.0F;
    public static final double MELEE_RANGE = 4.0D;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SLASH1_ANIM = RawAnimation.begin().thenPlay("attack1");
    private static final RawAnimation SLASH2_ANIM = RawAnimation.begin().thenPlay("attack2");
    // El modelo no trae animación de "death" dedicada, solo "despawn".
    // La reutilizamos para la muerte hasta que exista una animación propia.
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("despawn");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int attackTicksElapsed = 0;
    private int currentAttackDuration = 0;
    private boolean attackEffectsApplied = false;
    private int lastAttackStateAnimated = ATTACK_NONE;

    public SkeletalWarriorEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noCulling = true; // evita que se congele la animacion al salir del frustum de camara
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, SLASH_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SkeletalWarriorAttackGoal(this));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        // Siempre hostil: no espera a que lo golpeen primero (a diferencia de Gnut).
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

    public void triggerSlash1() {
        if (!isAttacking()) {
            startAttack(ATTACK_SLASH1, SLASH1_DURATION_TICKS);
        }
    }

    public void triggerSlash2() {
        if (!isAttacking()) {
            startAttack(ATTACK_SLASH2, SLASH2_DURATION_TICKS);
        }
    }

    private void startAttack(int attackState, int durationTicks) {
        this.entityData.set(DATA_ATTACK_STATE, attackState);
        this.attackTicksElapsed = 0;
        this.currentAttackDuration = durationTicks;
        this.attackEffectsApplied = false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            tickAttackWindow();
        }
    }

    private void tickAttackWindow() {
        int state = getAttackState();
        if (state == ATTACK_NONE) {
            return;
        }

        attackTicksElapsed++;

        int damageTick = (state == ATTACK_SLASH1) ? SLASH1_DAMAGE_TICK : SLASH2_DAMAGE_TICK;
        if (!attackEffectsApplied && attackTicksElapsed >= damageTick) {
            applyAttackEffects();
            attackEffectsApplied = true;
        }

        if (attackTicksElapsed >= currentAttackDuration) {
            this.entityData.set(DATA_ATTACK_STATE, ATTACK_NONE);
        }
    }

    private void applyAttackEffects() {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && this.distanceTo(target) <= MELEE_RANGE) {
            DamageSource source = this.damageSources().mobAttack(this);
            target.hurt(source, SLASH_DAMAGE);
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.6F, 1.1F);
        }
    }

    // ---- Knockback: igual que Gnut, no lo saca de su golpe a mitad de camino ----
    @Override
    public void knockback(double strength, double x, double z) {
        if (isAttacking()) {
            return;
        }
        super.knockback(strength, x, z);
    }

    // ---- Sonidos (paleta esqueleto vanilla) ----

    // ---- Cabeza al morir por un creeper cargado ----
    // OJO: esto NO se puede hacer con una condición de loot table tipo
    // "type_specific":"creeper" - ese sub-predicate no existe en 1.20.1
    // (por eso la loot table entera fallaba al parsear y no dropeaba nada,
    // ni siquiera el hueso). Vanilla implementa este mecanismo así, en
    // Java, no vía datapack.
    @Override
    protected void dropCustomDeathLoot(DamageSource damageSource, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(damageSource, looting, recentlyHit);
        if (damageSource.getEntity() instanceof Creeper creeper && creeper.isPowered()) {
            this.spawnAtLocation(ModItems.SKELETAL_HEAD.get());
        }
    }

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

    private PlayState movementPredicate(AnimationState<SkeletalWarriorEntity> state) {
        if (isAttacking() || this.isDeadOrDying()) {
            return PlayState.STOP;
        }
        state.getController().setAnimation(state.isMoving() ? WALK : IDLE);
        return PlayState.CONTINUE;
    }

    private PlayState attackPredicate(AnimationState<SkeletalWarriorEntity> state) {
        int attackState = getAttackState();

        if (attackState == ATTACK_NONE) {
            lastAttackStateAnimated = ATTACK_NONE;
            return PlayState.STOP;
        }

        RawAnimation animation = (attackState == ATTACK_SLASH1) ? SLASH1_ANIM : SLASH2_ANIM;

        if (lastAttackStateAnimated != attackState) {
            state.getController().forceAnimationReset();
            state.getController().setAnimation(animation);
            lastAttackStateAnimated = attackState;
        }
        return PlayState.CONTINUE;
    }

    private PlayState deathPredicate(AnimationState<SkeletalWarriorEntity> state) {
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