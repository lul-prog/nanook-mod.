package com.nanookmod.entity;

import com.nanookmod.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Cuervo del Bosque Crepuscular. Mob puramente ambiental (sin ataque propio):
 * siempre "vuela" en el sentido técnico (NoGravity + FlyingMoveControl, igual
 * que el Bat vanilla), pero visualmente pasa la mayor parte del tiempo posado
 * y quieto (animación idle) - solo se ve volando (animación fly) cuando se
 * está moviendo de verdad, ya sea por su comportamiento ambiente (reposar en
 * un sitio cercano y volar corto a otro cuando el jugador se acerca demasiado)
 * o por los comportamientos especiales de más prioridad (ver
 * CrowStructureCircleGoal y CrowPlayerTrackGoal).
 */
public class CrowEntity extends PathfinderMob implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Estado puramente informativo para depurar/afinar IA, no sincronizado al cliente. */
    public enum Behavior {
        AMBIENT,
        WATCHING,
        OMEN,
        STRUCTURE_CIRCLE
    }

    private Behavior behavior = Behavior.AMBIENT;

    // Usado por CrowPlayerTrackGoal para saber si ya sonó el aviso de "te noté".
    private boolean hasNoticedPlayer = false;

    // Ticks restantes de la animación "scared" (activada por CrowStartleGoal).
    private int scaredTicks = 0;

    // Marca los cuervos generados por el "llamado" de CrowOmenHandler, para
    // poder despawnearlos sin lag cuando el jugador recupera la vida (los
    // cuervos ambiente normales nunca llevan esta marca).
    private boolean summoned = false;

    public CrowEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 60, true);
        this.setNoGravity(true);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new CrowStartleGoal(this));
        this.goalSelector.addGoal(1, new CrowStructureCircleGoal(this));
        this.goalSelector.addGoal(2, new CrowSwarmOmenGoal(this));
        this.goalSelector.addGoal(3, new CrowPlayerTrackGoal(this));
        this.goalSelector.addGoal(4, new CrowPerchGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.FLYING_SPEED, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.9D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    public Behavior getBehavior() {
        return behavior;
    }

    public void setBehavior(Behavior behavior) {
        this.behavior = behavior;
    }

    public boolean hasNoticedPlayer() {
        return hasNoticedPlayer;
    }

    public void setNoticedPlayer(boolean value) {
        this.hasNoticedPlayer = value;
    }

    public boolean isSummoned() {
        return summoned;
    }

    public void setSummoned(boolean summoned) {
        this.summoned = summoned;
    }

    public boolean isScared() {
        return scaredTicks > 0;
    }

    /** Dispara la animación "scared" (una sola vez, se apaga sola en ~9s). */
    public void triggerScared() {
        this.scaredTicks = 180;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.scaredTicks > 0) {
            this.scaredTicks--;
        }
    }

    /** Vuela hacia un punto (usado por todos los goals; centraliza el moveControl). */
    public void flyTowards(Vec3 target, double speed) {
        this.getMoveControl().setWantedPosition(target.x, target.y, target.z, speed);
    }

    @Override
    public boolean isFlapping() {
        return this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-5 || Math.abs(this.getDeltaMovement().y) > 1.0E-5;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // Nunca recibe daño de caída: es un mob volador, siempre NoGravity.
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.AMBIENT_CREPUSCULAR_CROW.get();
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 4, this::movementPredicate));
    }

    private PlayState movementPredicate(AnimationState<CrowEntity> state) {
        if (this.isScared()) {
            state.getController().setAnimation(RawAnimation.begin().thenPlay("scared"));
            return PlayState.CONTINUE;
        }

        boolean moving = this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4
                || Math.abs(this.getDeltaMovement().y) > 1.0E-4;

        if (moving) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("fly"));
        } else {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
