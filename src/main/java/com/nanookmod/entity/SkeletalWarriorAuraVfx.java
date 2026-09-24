package com.nanookmod.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * VFX del aura de alma (fuego cian) del Skeletal Warrior/Mago. Portado desde
 * aura_soul_long.png (textura animada NATIVA de ModelEngine, 8 frames,
 * frame_time=1 tick) - GeckoLib no soporta ese tipo de animación de textura,
 * así que en vez de intentar meterla dentro del modelo principal (donde
 * queda como un parche estático, ver captura del problema), la separamos en
 * esta entidad chica que sigue al dueño y cicla manualmente entre 8
 * texturas estáticas (SkeletalWarriorAuraVfxModel#getTextureResource decide
 * cuál según el tickCount). Mismo enfoque de "VFX satélite" que ya usás para
 * NecroticSlashVfx con la guadaña.
 *
 * v3: dejamos de confiar en que ESTA entidad sincronice bien su propia
 * posición/rotación por red en paralelo a la del dueño - dos entidades
 * separadas sincronizándose cada una por su cuenta (aunque sea al mismo
 * tick rate) se desfasan visualmente apenas hay movimiento, porque cada una
 * puede interpolar/actualizarse un frame antes o después que la otra. En
 * vez de eso, sincronizamos únicamente el ID del dueño (ver DATA_OWNER_ID):
 * el renderer lo resuelve del lado cliente y dibuja el aura pegada
 * exactamente a la posición/rotación YA renderizada (interpolada) del
 * dueño, tick a tick. Así es imposible que se vean como "dos entidades".
 *
 * No tiene colisión, no se puede lastimar, no persiste en el save (VFX puro).
 */
public class SkeletalWarriorAuraVfx extends Entity implements GeoEntity {

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public static final int FRAME_COUNT = 8;
    // Original del YAML/ModelEngine: frame_time=1 (1 frame por tick, ciclo
    // completo en 8 ticks = 0.4s -- muy rápido/parpadeante para Java).
    // Lo bajamos a 4 ticks por frame (ciclo completo en 32 ticks = 1.6s),
    // se ve como una llama respirando en vez de parpadear. Es solo este
    // número - subilo más si lo querés todavía más lento (ej. 6-8).
    public static final int TICKS_PER_FRAME = 4;

    private static final EntityDataAccessor<Integer> DATA_OWNER_ID =
            SynchedEntityData.defineId(SkeletalWarriorAuraVfx.class, EntityDataSerializers.INT);

    @Nullable
    private LivingEntity owner;

    public SkeletalWarriorAuraVfx(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    public SkeletalWarriorAuraVfx(Level level, LivingEntity owner) {
        this(com.nanookmod.registry.ModEntities.SKELETAL_WARRIOR_AURA_VFX.get(), level);
        this.owner = owner;
        this.entityData.set(DATA_OWNER_ID, owner.getId());
        // Sin offset Y manual: el .geo.json ya trae la altura correcta
        // bakeada (coordenadas originales del elemento "ef", Y 22->45 en
        // unidades de modelo = ~1.4 a ~2.8 bloques sobre los pies). Sumar
        // algo acá duplicaba la altura y la mandaba muy por encima de la
        // entidad, fuera de su propia bounding box.
        this.setPos(owner.getX(), owner.getY(), owner.getZ());
    }

    @Nullable
    public LivingEntity getOwner() {
        return owner;
    }

    /** ID del dueño, sincronizado a todos los clientes. -1 si no hay dueño. */
    public int getOwnerId() {
        return this.entityData.get(DATA_OWNER_ID);
    }

    @Override
    public void tick() {
        super.tick();

        // El cliente NUNCA tiene el campo `owner` seteado (ese campo solo
        // se llena en el constructor que se llama del lado servidor) - pero
        // ahora eso ya no importa para el render (ver
        // SkeletalWarriorAuraVfxRenderer, que resuelve el dueño por
        // DATA_OWNER_ID). Este bloque server-side solo mantiene la posición
        // "real" de la entidad al día por las dudas (hitbox, /tp, etc.).
        if (this.level().isClientSide) {
            return;
        }

        if (owner == null || !owner.isAlive() || owner.isRemoved()) {
            this.discard();
            return;
        }

        this.setPos(owner.getX(), owner.getY(), owner.getZ());
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
    }

    public int getCurrentFrame() {
        return (this.tickCount / TICKS_PER_FRAME) % FRAME_COUNT;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_OWNER_ID, -1);
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
    public boolean isPushable() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No hay animación de huesos que reproducir - toda la "animación"
        // es el swap de textura por tick (ver Model#getTextureResource).
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}