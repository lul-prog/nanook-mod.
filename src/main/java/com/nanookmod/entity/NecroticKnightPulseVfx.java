package com.nanookmod.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
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
 * VFX de un solo uso: el pulso/estallido al aparecer el NecroticKnight
 * (vfx_pulse.bbmodel -- textura simple 16x16, sin el problema de tira
 * animada que tuvimos con el aura del warrior, así que este debería andar
 * sin drama apenas lo exportes).
 *
 * Se reproduce una vez (animación "skill3", ~21 ticks) y se autodestruye.
 */
public class NecroticKnightPulseVfx extends Entity implements GeoEntity {

    private static final int LIFETIME_TICKS = 22; // un poco más que la animación (21t) por margen

    private static final RawAnimation PULSE_ANIM = RawAnimation.begin().thenPlay("skill3");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public NecroticKnightPulseVfx(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    public NecroticKnightPulseVfx(Level level, Vec3 pos) {
        this(com.nanookmod.registry.ModEntities.NECROTIC_KNIGHT_PULSE_VFX.get(), level);
        this.setPos(pos.x, pos.y, pos.z);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > LIFETIME_TICKS) {
            this.discard();
        }
    }

    @Override
    protected void defineSynchedData() {
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
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "pulse", 0, this::pulsePredicate));
    }

    private PlayState pulsePredicate(AnimationState<NecroticKnightPulseVfx> state) {
        state.getController().setAnimation(PULSE_ANIM);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}