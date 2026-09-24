package com.nanookmod.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
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

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * FANTASMITA TEMPORAL para el NecroticKnight - usa el modelo skull_wisps
 * (el único de los tres "wisp" que NO tiene el problema de textura animada
 * de ModelEngine, ver conversación) solo como stand-in visual hasta que
 * armemos el Deathbound Souls de verdad como el fantasmita definitivo.
 *
 * Comportamiento (tal como lo pediste): al invocarse, sale DISPARADO en
 * una dirección al azar (como una explosión de almas saliendo del jefe en
 * todas direcciones) durante un ratito breve (ver launch()/LAUNCH_
 * DURATION_TICKS) -- todavía no persigue a nadie en esta fase, solo vuela
 * en línea recta hacia donde lo mandó el impulso inicial. Pasado ese
 * ratito, recién ahí empieza a perseguir al jugador (vuelo homing
 * atravesando paredes, sin pathfinding ni colisión, como el Vex vainilla),
 * y muere apenas:
 *   a) toca al jugador (le pega daño de contacto y se autodestruye), o
 *   b) el jugador lo bloquea con el escudo (se autodestruye igual, sin
 *      pegar daño), o
 *   c) recibe cualquier golpe (vida 1, cualquier ataque lo mata).
 *
 * No tiene animación de ataque/muerte propia (el modelo no la trae) -
 * simplemente desaparece con partículas al morir, no hace falta más.
 */
public class TwilightWispEntity extends Monster implements GeoEntity {

    private static final float CONTACT_DAMAGE = 3.0F;
    private static final double SPEED_BLOCKS_PER_TICK = 0.35D;
    private static final double CONTACT_RANGE = 0.9D;
    private static final int MAX_LIFETIME_TICKS = 20 * 30; // se disipa solo a los 30s por si pierde el objetivo

    // --- Ráfaga inicial (ver launch()) ---
    // ~0.5s volando en la dirección del estallido antes de empezar a
    // perseguir -- lo suficiente para que se note el "salieron disparados
    // para todos lados" sin que se sienta como tiempo muerto.
    private static final int LAUNCH_DURATION_TICKS = 10;
    private static final double LAUNCH_SPEED = 0.42D;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Historial de posiciones (solo cliente) para la estela geométrica verde
    // neón -- se guarda 1 punto por tick de cliente, el más nuevo primero.
    // Lo lee TwilightWispTrailRenderLayer cada frame para armar la cinta.
    private static final int TRAIL_MAX_POINTS = 14;
    private final Deque<Vec3> trailHistory = new ArrayDeque<>(TRAIL_MAX_POINTS);

    // Cuenta regresiva de la fase de ráfaga -- mientras sea > 0, el wisp
    // ignora al objetivo y solo vuela en línea recta con la velocidad que
    // le dio launch().
    private int launchTicksRemaining = 0;

    public TwilightWispEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true; // sin colisión física propia, la movemos a mano
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D) // no se usa, el movimiento es manual
                .add(Attributes.ATTACK_DAMAGE, CONTACT_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D); // que no lo empuje nada mientras vuela
    }

    @Override
    protected void registerGoals() {
        // Sin goals de movimiento -- el vuelo homing atraviesa-paredes se
        // hace a mano en tick(), no con pathfinding/navegación normal.
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /**
     * Llamar justo después de addFreshEntity -- lo manda disparado en una
     * dirección al azar (con algo de variación vertical para que no
     * salgan todos perfectamente horizontales) durante LAUNCH_DURATION_TICKS,
     * antes de que empiece a perseguir al jugador. Así el jefe puede
     * invocar varios de una y que salgan esparcidos "para cualquier lado"
     * en vez de aparecer ya parados mirando al jugador.
     */
    public void launch(net.minecraft.util.RandomSource random) {
        float angle = random.nextFloat() * (float) (Math.PI * 2.0D);
        float pitch = (random.nextFloat() - 0.5F) * 0.8F; // ~ ±23°, variación vertical del estallido
        double horizontalScale = Math.cos(pitch);
        double dx = Math.cos(angle) * horizontalScale;
        double dz = Math.sin(angle) * horizontalScale;
        double dy = Math.sin(pitch);
        this.setDeltaMovement(dx * LAUNCH_SPEED, dy * LAUNCH_SPEED, dz * LAUNCH_SPEED);
        this.launchTicksRemaining = LAUNCH_DURATION_TICKS;
    }

    /**
     * Variante APUNTADA (portales de invocación) -- en vez de una
     * dirección al azar, sale disparado derecho hacia donde le digan
     * (normalmente, hacia el jugador, con algo de dispersión ya aplicada
     * por quien llama a esto -- ver NecroticKnightEntity#launchWispFromPortal).
     * Mismo LAUNCH_DURATION_TICKS/LAUNCH_SPEED que la variante al azar --
     * solo cambia CÓMO se elige la dirección, no el resto del
     * comportamiento (ráfaga recta y después homing, igual que siempre).
     */
    public void launch(Vec3 direction) {
        Vec3 normalized = direction.lengthSqr() > 1.0E-4 ? direction.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
        this.setDeltaMovement(normalized.scale(LAUNCH_SPEED));
        this.launchTicksRemaining = LAUNCH_DURATION_TICKS;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            // Un punto por tick alcanza y sobra -- a 20 ticks/s con
            // TRAIL_MAX_POINTS=14 la cola cubre ~0.7s de recorrido, que a
            // la velocidad del wisp (0.35 bloques/tick) da una estela de
            // unos 5 bloques de largo. Si la querés más larga, subí
            // TRAIL_MAX_POINTS, no la frecuencia de muestreo (muestrear
            // menos seguido hace la cinta "poligonal", se nota el quiebre
            // entre segmentos).
            trailHistory.addFirst(this.position());
            while (trailHistory.size() > TRAIL_MAX_POINTS) {
                trailHistory.removeLast();
            }
            return;
        }

        if (this.tickCount > MAX_LIFETIME_TICKS) {
            this.discard();
            return;
        }

        if (launchTicksRemaining > 0) {
            // Fase de ráfaga: todavía no persigue a nadie, solo sigue de
            // largo con la velocidad que le dio launch() -- setPos directo
            // (no this.move()), igual que el vuelo homing, para que
            // atraviese bloques sin frenarse contra nada.
            launchTicksRemaining--;
            Vec3 delta = this.getDeltaMovement();
            this.setPos(this.getX() + delta.x, this.getY() + delta.y, this.getZ() + delta.z);
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return; // se queda flotando quieto hasta que el targetSelector le asigne uno nuevo
        }

        Vec3 toTarget = target.getEyePosition().subtract(this.position());
        double distance = toTarget.length();

        if (distance <= CONTACT_RANGE) {
            onContact(target);
            return;
        }

        Vec3 dir = toTarget.scale(1.0D / distance);
        this.setDeltaMovement(dir.scale(SPEED_BLOCKS_PER_TICK));

        // setPos directo (no this.move()) - así atraviesa bloques sin que
        // la colisión normal del juego lo frene, igual que pediste.
        this.setPos(this.getX() + dir.x * SPEED_BLOCKS_PER_TICK,
                this.getY() + dir.y * SPEED_BLOCKS_PER_TICK,
                this.getZ() + dir.z * SPEED_BLOCKS_PER_TICK);

        this.lookAt(target, 180.0F, 180.0F);
    }

    private void onContact(LivingEntity target) {
        if (target instanceof Player player) {
            if (!player.isBlocking()) {
                DamageSource source = this.damageSources().mobAttack(this);
                player.hurt(source, CONTACT_DAMAGE);
            }
            // Se muere igual esté bloqueado o no -- "golpean o los bloquean, mueren".
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            // Se sacó el estallido de partículas de acá (SOUL) -- el
            // trail geométrico ya le da suficiente lenguaje visual, no
            // hacía falta sumarle más efectos encima.
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 0.7F, 1.3F);
        }

        this.discard();
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SOUL_ESCAPE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SOUL_ESCAPE;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    /**
     * Copia del historial de posiciones, más nueva primero, para que
     * TwilightWispTrailRenderLayer arme la cinta. Copia defensiva simple
     * (14 elementos, se llama 1 vez por frame) -- no vale la pena
     * complicarse con vistas inmutables para esto.
     */
    public List<Vec3> getTrailHistory() {
        return new ArrayList<>(trailHistory);
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 0, this::movementPredicate));
    }

    private PlayState movementPredicate(AnimationState<TwilightWispEntity> state) {
        state.getController().setAnimation(IDLE);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}