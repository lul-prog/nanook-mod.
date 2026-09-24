package com.nanookmod.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * "Espejo" del Necrotic Knight -- réplica de la técnica que usa Confluence
 * Otherworld (terra_entity) con BrainFake para el Brain of Cthulhu. La idea
 * NO es clonar/copiar al jefe real, sino reflejar su posición respecto al
 * jugador al que está atacando: con 2 bits de mirrorTag (0-3) se obtienen
 * las 4 combinaciones (sin espejar / espejar X / espejar Z / espejar
 * ambos), o sea hasta 4 copias distintas alrededor del jugador con un
 * único jefe real moviéndose.
 *
 * TEST INICIAL (ver charla): todavía SIN el efecto de disolución con
 * ruido de BrainTranslucent -- por ahora es solo transparencia simple
 * fija (ver el override de getRenderColor/getRenderType en
 * NecroticKnightRenderer, con un chequeo instanceof en vez de una clase
 * de renderer separada). Si el resultado convence, el siguiente paso es
 * portar ese framebuffer+shader.
 *
 * Extiende NecroticKnightEntity (no Monster directo) a propósito, para
 * poder reusar tal cual el modelo/renderer/animaciones del jefe real sin
 * duplicar esas clases (GeoModel<NecroticKnightEntity> está atado al tipo
 * concreto) -- exactamente la misma razón por la que BrainFake extiende
 * BrainOfCthulhu en vez de ser una entidad independiente. isMirrorClone
 * (ver NecroticKnightEntity) es lo que evita que esta instancia dispare
 * la lógica de jefe (ataques, fases, portales, secuencia de aparición).
 */
public class NecroticKnightFakeEntity extends NecroticKnightEntity {

    // 10s de vida por defecto -- ver GHOST_SUMMON_DURATION_TICKS en
    // NecroticKnightEntity para la referencia de escala de tiempo que ya
    // usa el resto del jefe.
    private static final int DEFAULT_LIFETIME_TICKS = 200;

    @Nullable
    private java.util.UUID ownerId;
    private int mirrorTag;
    private int lifetimeTicks = DEFAULT_LIFETIME_TICKS;

    public NecroticKnightFakeEntity(EntityType<? extends NecroticKnightEntity> type, Level level) {
        super(type, level);
        this.isMirrorClone = true;
        this.noPhysics = true;
        this.setInvulnerable(true);
        this.setNoGravity(true);
    }

    /**
     * Hay que llamar esto justo después de addFreshEntity -- no hay
     * constructor con estos parámetros porque EntityType.Builder exige un
     * EntityFactory de 2 argumentos (type, level) sin margen para pasar
     * más datos ahí.
     */
    public void configure(NecroticKnightEntity owner, int mirrorTag, int lifetimeTicks) {
        this.ownerId = owner.getUUID();
        this.mirrorTag = mirrorTag;
        this.lifetimeTicks = lifetimeTicks;
    }

    @Override
    protected void registerGoals() {
        // Sin IA propia -- la posición se fuerza a mano en tick() según
        // el espejado. Override vacío a propósito (no llama a
        // super.registerGoals(), que es el que le pone todos los goals
        // de ataque/movimiento al jefe real).
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false; // invulnerable de verdad, igual que BrainFake
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
    public void push(Entity entity) {
        // no empuja ni es empujado
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public void tick() {
        // super.tick() = NecroticKnightEntity#tick(), que gracias a
        // isMirrorClone=true corre el tick base (Monster/Mob/LivingEntity/
        // Entity, animación) pero SE SALTA la lógica de jefe.
        super.tick();

        if (this.level().isClientSide) {
            return;
        }

        if (--lifetimeTicks <= 0) {
            this.discard();
            return;
        }

        updateMirrorPosition();
    }

    private void updateMirrorPosition() {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        Entity ownerEntity = ownerId != null ? serverLevel.getEntity(ownerId) : null;
        if (!(ownerEntity instanceof NecroticKnightEntity owner) || !owner.isAlive()) {
            this.discard();
            return;
        }

        LivingEntity target = owner.getTarget();
        Vec3 pivot = target != null ? target.position() : owner.position();

        double ownerX = owner.getX();
        double ownerZ = owner.getZ();

        // bit 1 = espejar en X, bit 2 = espejar en Z (reflejo respecto al
        // pivote: mirroredCoord = 2*pivote - coordenadaReal).
        double mirroredX = (mirrorTag & 1) != 0 ? (2.0D * pivot.x - ownerX) : ownerX;
        double mirroredZ = (mirrorTag & 2) != 0 ? (2.0D * pivot.z - ownerZ) : ownerZ;

        this.moveTo(mirroredX, owner.getY(), mirroredZ, owner.getYRot(), owner.getXRot());
        this.setYHeadRot(owner.getYHeadRot());
        this.yBodyRot = owner.yBodyRot;
    }
}
