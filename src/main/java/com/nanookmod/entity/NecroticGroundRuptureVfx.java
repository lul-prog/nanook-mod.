package com.nanookmod.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Grieta en el piso del golpe final del combo (scythe_raise2), portada de
 * VFX_SAMUS_Rupture_Small_NECROMANCER / vfx_earthquake_rupture_1 del pack
 * Awakened Necromancer. Confirmado en el YAML original: se spawnea ~4
 * bloques adelante del jugador, sobre el piso, junto con el sonido de
 * rotura (que ya usamos en NecroticScytheItem#performFinisher).
 *
 * Entidad de un solo uso, mucho más simple que NecroticSlashVfx: no sigue
 * a nadie ni recibe golpes de combo. Se crea una vez, reproduce "skill"
 * (aparece), después "death" (se apaga), y se autodestruye sola.
 *
 * El pack original usaba 5 variantes (vfx_earthquake_rupture_1..5) con un
 * "changepart" rapidísimo entre ellas cerca del final -- simplificado acá
 * a usar solo la _1 (ya trae sus propias animaciones skill/death), para no
 * sumar otra ronda de conversión de Blockbench. Si más adelante querés el
 * efecto de parpadeo entre las 5 variantes, se puede sumar después.
 */
public class NecroticGroundRuptureVfx extends Entity implements GeoEntity {

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // Punto de partida -- ajustalos una vez veas la duración real de
    // "skill"/"death" en Blockbench (no los medí como sí hice con la
    // guadaña, todavía no se pidió una duración específica para esto).
    private static final int SKILL_DURATION_TICKS = 20;
    private static final int DEATH_DURATION_TICKS = 20;

    private static final RawAnimation SKILL = RawAnimation.begin().thenPlay("skill");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("death");

    private enum Phase { SKILL, DEATH, DONE }

    private Phase phase = Phase.SKILL;
    private Phase lastAnimatedPhase = null;
    private int phaseTicksRemaining = SKILL_DURATION_TICKS;

    public NecroticGroundRuptureVfx(EntityType<? extends Entity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        // No hace falta sincronizar nada: las 2 fases están fijas en el
        // tiempo y tanto cliente como servidor las recorren igual solas.
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            return;
        }

        if (phaseTicksRemaining > 0) {
            phaseTicksRemaining--;
            return;
        }

        switch (phase) {
            case SKILL -> {
                phase = Phase.DEATH;
                phaseTicksRemaining = DEATH_DURATION_TICKS;
            }
            case DEATH -> {
                phase = Phase.DONE;
                this.discard();
            }
            default -> this.discard();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "rupture", 0, this::rupturePredicate));
    }

    private PlayState rupturePredicate(AnimationState<NecroticGroundRuptureVfx> state) {
        if (phase == Phase.DONE) {
            return PlayState.STOP;
        }

        if (lastAnimatedPhase != phase) {
            lastAnimatedPhase = phase;
            return state.setAndContinue(phase == Phase.DEATH ? DEATH : SKILL);
        }

        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}
