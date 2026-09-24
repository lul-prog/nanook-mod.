package com.nanookmod.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Gnut: mob hostil/neutral de mar_primigenio, portado desde el pack de
 * MythicMobs (Type: vindicator, skill "gnut_attack" original) y expandido:
 *   - Combo cerca->área (ver GnutAttackGoal, cooldowns independientes para
 *     que el golpe de contacto rellene la espera del stomp).
 *   - Huida corta por daño acumulado (esquiva/reposiciona).
 *   - Huida larga por vida baja (<50%), con Regeneración tipo ajolote,
 *     limitada a MAX_REGEN_USES veces - después de eso sigue huyendo a baja
 *     vida pero sin el efecto.
 *
 * Mismo patrón que NanookEntity: DATA_ATTACK_STATE sincronizado, startAttack,
 * pickAnimationFor, applyAttackEffects en el tick de telegraph.
 */
public class GnutEntity extends Monster implements GeoEntity, FrostedBuffed {

    private static final EntityDataAccessor<Integer> DATA_ATTACK_STATE =
            SynchedEntityData.defineId(GnutEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Boolean> DATA_FROSTED_BUFFED =
            SynchedEntityData.defineId(GnutEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int ATTACK_NONE = 0;
    public static final int ATTACK_MELEE = 1;
    public static final int ATTACK_STOMP = 2;

    // --- Golpe de contacto ---
    private static final int MELEE_DURATION_TICKS = 12;
    private static final int MELEE_TELEGRAPH_TICK = 6;
    private static final float MELEE_DAMAGE = 4.5F;
    public static final double MELEE_RANGE = 1.5D;

    // --- Stomp de área ---
    private static final int STOMP_DURATION_TICKS = 20;
    private static final int STOMP_TELEGRAPH_TICK = 10;
    private static final float STOMP_DAMAGE = 7.0F;
    public static final double STOMP_RADIUS = 2.0D;

    // --- Huida corta por daño acumulado O por racha de golpes sin responder (sin curación) ---
    private static final float DODGE_FLEE_DAMAGE_THRESHOLD = 8.0F;
    private static final int DODGE_FLEE_HIT_STREAK_THRESHOLD = 4; // golpes recibidos seguidos sin conectar ninguno propio
    private static final int DODGE_FLEE_DURATION_TICKS = 60;
    private static final int DODGE_FLEE_COOLDOWN_TICKS = 200;

    // --- Huida larga por vida baja, con regeneración tipo ajolote ---
    private static final float HEAL_FLEE_HEALTH_FRACTION = 0.5F; // <50% de vida
    private static final int HEAL_FLEE_DURATION_TICKS = 100;     // 5s, para que la regen rinda
    private static final int HEAL_FLEE_COOLDOWN_TICKS = 300;     // 15s entre intentos de huida-cura
    private static final int MAX_REGEN_USES = 3;
    // Fórmula vanilla: cura 1 HP cada max(50 >> amplifier, 1) ticks. Con amplifier=5 ya
    // se satura en 1 tick (cura cada tick = lo más rápido que existe); usamos un poco
    // más alto para dejar margen. "Regeneración X" no se ve en el HUD porque es un mob,
    // no un jugador - solo importa la velocidad de curación real.
    private static final int REGEN_AMPLIFIER = 9;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("death");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int attackTicksElapsed = 0;
    private int currentAttackDuration = 0;
    private boolean attackEffectsApplied = false;
    private int lastAttackStateAnimated = ATTACK_NONE;

    // Huida corta (esquiva)
    private float damageSinceLastDodge = 0.0F;
    private int hitsTakenWithoutLanding = 0;
    private boolean dodgeFleeRequested = false;
    private int dodgeFleeCooldown = 0;

    // Huida larga (cura)
    private boolean healFleeRequested = false;
    private int healFleeCooldown = 0;
    private int regenUsesRemaining = MAX_REGEN_USES;

    // Cuál de las dos pidió la huida activa en este momento (para que GnutFleeGoal sepa
    // qué duración usar y si debe aplicar regeneración).
    private boolean currentFleeIsHealFlee = false;

    public GnutEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noCulling = true; // evita que se congele la animacion al salir del frustum de camara
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 70.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, STOMP_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 20.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Prioridad 1: ambas huidas comparten este goal, así que interrumpen el ataque.
        this.goalSelector.addGoal(1, new GnutFleeGoal(this, 1.3D, DODGE_FLEE_DURATION_TICKS));
        this.goalSelector.addGoal(2, new GnutAttackGoal(this));
        this.goalSelector.addGoal(3, new AttackFrostCropGoal(this, 1.0D, 16));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        // NEUTRAL por diseño: solo se pone hostil si algo lo golpea primero.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ATTACK_STATE, ATTACK_NONE);
        this.entityData.define(DATA_FROSTED_BUFFED, false);
    }

    public boolean isFrostedBuffed() {
        return this.entityData.get(DATA_FROSTED_BUFFED);
    }

    public void setFrostedBuffed(boolean buffed) {
        this.entityData.set(DATA_FROSTED_BUFFED, buffed);
    }

    public int getAttackState() {
        return this.entityData.get(DATA_ATTACK_STATE);
    }

    public void triggerMeleeAttack() {
        if (getAttackState() == ATTACK_NONE) {
            startAttack(ATTACK_MELEE, MELEE_DURATION_TICKS);
        }
    }

    public void triggerStompAttack() {
        if (getAttackState() == ATTACK_NONE) {
            startAttack(ATTACK_STOMP, STOMP_DURATION_TICKS);
        }
    }

    private void startAttack(int attackState, int durationTicks) {
        this.entityData.set(DATA_ATTACK_STATE, attackState);
        this.attackTicksElapsed = 0;
        this.currentAttackDuration = durationTicks;
        this.attackEffectsApplied = false;

        if (attackState == ATTACK_STOMP) {
            this.setDeltaMovement(this.getDeltaMovement().x, 0.3D, this.getDeltaMovement().z);
            this.playSound(SoundEvents.GOAT_HORN_BREAK, 1.0F, 1.0F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            tickAttackWindow();
            tickFleeCooldownsAndTriggers();
        } else {
            com.nanookmod.client.particles.FrostedBuffParticleHelper.spawnIfBuffed(this, isFrostedBuffed());
        }
    }

    private void tickAttackWindow() {
        int state = getAttackState();
        if (state == ATTACK_NONE) {
            return;
        }

        attackTicksElapsed++;

        int telegraphTick = (state == ATTACK_MELEE) ? MELEE_TELEGRAPH_TICK : STOMP_TELEGRAPH_TICK;
        if (!attackEffectsApplied && attackTicksElapsed >= telegraphTick) {
            applyAttackEffects(state);
            attackEffectsApplied = true;
        }

        if (attackTicksElapsed >= currentAttackDuration) {
            this.entityData.set(DATA_ATTACK_STATE, ATTACK_NONE);
        }
    }

    private void applyAttackEffects(int attackState) {
        DamageSource source = this.damageSources().mobAttack(this);

        if (attackState == ATTACK_MELEE) {
            LivingEntity target = this.getTarget();
            if (target != null && target.isAlive() && this.distanceTo(target) <= MELEE_RANGE + 0.5D) {
                target.hurt(source, MELEE_DAMAGE);
                hitsTakenWithoutLanding = 0; // conectó - se corta la racha de "estar recibiendo sin responder"
            }
            return;
        }

        if (attackState == ATTACK_STOMP) {
            AABB area = this.getBoundingBox().inflate(STOMP_RADIUS);
            List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != this && e.isAlive() && this.distanceTo(e) <= STOMP_RADIUS);
            for (LivingEntity target : nearby) {
                target.hurt(source, STOMP_DAMAGE);
            }
            if (!nearby.isEmpty()) {
                hitsTakenWithoutLanding = 0;
            }

            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        this.getX(), this.getY() + 0.1D, this.getZ(),
                        14, 0.4D, 0.05D, 0.4D, 0.01D);
            }
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    // ---- Huida corta (esquiva por daño acumulado) ----

    // --- Alerta de manada ---
    private static final double PACK_ALERT_RADIUS = 16.0D;

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean wasHurt = super.hurt(source, amount);
        if (wasHurt && !this.level().isClientSide && this.isAlive()) {
            damageSinceLastDodge += amount;
            hitsTakenWithoutLanding++;

            boolean damageThresholdReached = damageSinceLastDodge >= DODGE_FLEE_DAMAGE_THRESHOLD;
            boolean hitStreakReached = hitsTakenWithoutLanding >= DODGE_FLEE_HIT_STREAK_THRESHOLD;

            if (dodgeFleeCooldown <= 0 && !dodgeFleeRequested && (damageThresholdReached || hitStreakReached)) {
                dodgeFleeRequested = true;
                damageSinceLastDodge = 0.0F;
                hitsTakenWithoutLanding = 0;
            }

            alertNearbyPack(source);
        }
        return wasHurt;
    }

    /** Si algo lastima a este Gnut, los Gnut cercanos sin objetivo también se enojan. */
    private void alertNearbyPack(DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            return;
        }

        AABB alertArea = this.getBoundingBox().inflate(PACK_ALERT_RADIUS);
        List<GnutEntity> nearbyPack = this.level().getEntitiesOfClass(GnutEntity.class, alertArea,
                e -> e != this && e.isAlive());

        for (GnutEntity packmate : nearbyPack) {
            if (packmate.getTarget() == null) {
                packmate.setTarget(attacker);
            }
        }
    }

    // ---- Huida larga (vida baja -> regeneración tipo ajolote) ----

    private void tickFleeCooldownsAndTriggers() {
        if (dodgeFleeCooldown > 0) {
            dodgeFleeCooldown--;
        }
        if (healFleeCooldown > 0) {
            healFleeCooldown--;
        }

        if (healFleeCooldown <= 0 && !healFleeRequested
                && this.getHealth() < this.getMaxHealth() * HEAL_FLEE_HEALTH_FRACTION) {
            healFleeRequested = true;
            healFleeCooldown = HEAL_FLEE_COOLDOWN_TICKS;
        }
    }

    /**
     * Consumido por GnutFleeGoal. La huida-cura tiene prioridad sobre la de esquiva si
     * ambas están pendientes a la vez (vida baja importa más que un simple retroceso).
     */
    public boolean consumeFleeRequest() {
        if (healFleeRequested) {
            healFleeRequested = false;
            currentFleeIsHealFlee = true;
            return true;
        }
        if (dodgeFleeRequested) {
            dodgeFleeRequested = false;
            dodgeFleeCooldown = DODGE_FLEE_COOLDOWN_TICKS;
            currentFleeIsHealFlee = false;
            return true;
        }
        return false;
    }

    public boolean isCurrentFleeHealFlee() {
        return currentFleeIsHealFlee;
    }

    public int getHealFleeDurationTicks() {
        return HEAL_FLEE_DURATION_TICKS;
    }

    /** Llamado por GnutFleeGoal al arrancar una huida-cura. Aplica Regeneración si quedan usos. */
    public void onHealFleeStarted() {
        if (regenUsesRemaining > 0) {
            regenUsesRemaining--;
            // Parámetros: duración, amplificador, ambient=false, showParticles=false,
            // showIcon=false - efecto real pero sin las partículas verdes visibles.
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, HEAL_FLEE_DURATION_TICKS,
                    REGEN_AMPLIFIER, false, false, false));
        }
        // Si ya no quedan usos, sigue huyendo igual (vida baja sigue siendo vida baja),
        // pero sin el efecto - tal cual se pidió.
    }

    // ---- Knockback ----

    /**
     * Mientras está en medio de un ataque (melee o stomp), no lo mueve el retroceso al
     * recibir daño - el golpe sigue su curso igual (el daño y el telegraph no dependen de
     * esto), pero visualmente no se ve empujado/descolocado a mitad del salto o del golpe.
     * Fuera de un ataque, el knockback funciona normal.
     */
    @Override
    public void knockback(double strength, double x, double z) {
        if (getAttackState() != ATTACK_NONE) {
            return;
        }
        super.knockback(strength, x, z);
    }

    // ---- Sonidos ----

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.GOAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GLASS_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GOAT_DEATH;
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementPredicate));
        controllers.add(new AnimationController<>(this, "attack", 0, this::attackPredicate));
        controllers.add(new AnimationController<>(this, "death", 0, this::deathPredicate));
    }

    private PlayState movementPredicate(AnimationState<GnutEntity> state) {
        if (getAttackState() != ATTACK_NONE || this.isDeadOrDying()) {
            return PlayState.STOP;
        }
        state.getController().setAnimation(state.isMoving() ? WALK : IDLE);
        return PlayState.CONTINUE;
    }

    private PlayState attackPredicate(AnimationState<GnutEntity> state) {
        int attackState = getAttackState();

        if (attackState == ATTACK_NONE) {
            lastAttackStateAnimated = ATTACK_NONE;
            return PlayState.STOP;
        }

        RawAnimation animation = pickAnimationFor(attackState);
        if (animation == null) {
            return PlayState.STOP;
        }

        if (lastAttackStateAnimated != attackState) {
            // Sin esto, GeckoLib no reinicia la animación si es la misma instancia de
            // RawAnimation que ya se reprodujo antes (piensa que "ya está puesta").
            state.getController().forceAnimationReset();
            state.getController().setAnimation(animation);
            lastAttackStateAnimated = attackState;
        }
        return PlayState.CONTINUE;
    }

    @Nullable
    private RawAnimation pickAnimationFor(int attackState) {
        if (attackState == ATTACK_MELEE || attackState == ATTACK_STOMP) {
            return ATTACK_ANIM;
        }
        return null;
    }

    private PlayState deathPredicate(AnimationState<GnutEntity> state) {
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