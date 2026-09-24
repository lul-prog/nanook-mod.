package com.nanookmod.entity;

import com.nanookmod.registry.ModEntities;
import com.nanookmod.registry.ModParticles;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cristal de hielo de Nanook: crece del suelo, golpea a quien esté encima al
 * surgir, se queda un rato y se hunde.
 *
 * ===================================================================
 * PARA QUÉ SE USA
 * ===================================================================
 * Reemplaza a los NanookClawProjectile en dos ataques:
 *   - el abanico de garras (forward_claw_projectile): ahora son TRES o CINCO
 *     hileras de cristales que avanzan hacia el jugador como una ola;
 *   - el aterrizaje del salto: en vez del anillo de 18 garras, rayos de
 *     cristales que se abren desde el punto de impacto.
 * Ver NanookEntity#spawnCrystalRay.
 *
 * ===================================================================
 * CICLO DE VIDA (todo medido en ticks desde que nace la entidad)
 * ===================================================================
 *
 *   0 .. delay-TELEGRAPH   invisible y en silencio
 *   delay-TELEGRAPH .. delay   AVISO: grietas de nieve y bloque en el suelo
 *   delay .. delay+RISE    SURGE (easeOutBack, con un pequeño rebote). Es la
 *                          ÚNICA ventana de daño: quien esté dentro de la caja
 *                          en esos ticks recibe el golpe, una sola vez.
 *   .. +HOLD               se queda en pie
 *   .. +SINK               se hunde y se rompe en esquirlas de hielo
 *
 * El "delay" lo pone quien lo crea: crear varios cristales en fila con delay
 * creciente es lo que da el efecto de ola.
 *
 * ===================================================================
 * DISEÑO TÉCNICO
 * ===================================================================
 * - Igual que el modo BULGE de NanookRisingBlock: la animación es una función
 *   PURA de tickCount y de tres datos sincronizados (delay, tamaño, semilla).
 *   Cliente y servidor no simulan nada por separado y no se manda ninguna
 *   actualización de posición: la entidad nace quieta y se borra sola.
 * - La forma del cristal (un cúmulo de cubos apilados) sale de la semilla, así que
 *   cada cristal es distinto sin sincronizar nada más.
 * - Sin colisión y sin guardado (noSave en el registro).
 *
 * (Idea general inspirada en cómo otros mods hacen crecer cristales del
 * suelo. El código, el modelo y la textura son propios: el mod de referencia
 * tiene el código bajo CC BY-NC-ND y los assets con todos los derechos
 * reservados, así que no se tomó nada de ahí.)
 */
public class NanookCrystal extends Entity {

    private static final EntityDataAccessor<Integer> DATA_DELAY =
            SynchedEntityData.defineId(NanookCrystal.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(NanookCrystal.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(NanookCrystal.class, EntityDataSerializers.INT);

    /** Ticks de subida. */
    public static final int RISE_TICKS = 5;
    /** Ticks que se queda en pie. */
    public static final int HOLD_TICKS = 18;
    /** Ticks que tarda en hundirse. */
    public static final int SINK_TICKS = 6;
    /** Ticks de aviso (grietas) antes de surgir. */
    private static final int TELEGRAPH_TICKS = 5;

    /** Radio horizontal de la caja de daño con tamaño 1.0. */
    private static final double HIT_RADIUS = 0.95D;
    /** Altura de la caja de daño con tamaño 1.0. */
    private static final double HIT_HEIGHT = 2.4D;
    /** Impulso vertical al golpear. */
    private static final double LAUNCH_VELOCITY = 0.55D;

    // --- Solo servidor ---
    private float damage = 6.0F;
    private UUID ownerId = null;
    private BlockState groundState = Blocks.SNOW_BLOCK.defaultBlockState();
    private final Set<Integer> hitIds = new HashSet<>();

    public NanookCrystal(EntityType<? extends NanookCrystal> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    /**
     * Crea un cristal cuya base está en (x, groundY, z). groundY es la CARA
     * SUPERIOR del suelo (el renderer dibuja hacia arriba desde ahí).
     *
     * @param delay  ticks hasta que empieza a surgir (0 = ya)
     * @param size   1.0 = ~2.6 de alto; 0.6 = chico, 1.3 = grande
     * @param damage daño que hace al surgir (ya escalado por el llamador)
     */
    public static void spawn(ServerLevel level, double x, double groundY, double z,
                             int delay, float size, float damage,
                             LivingEntity owner, BlockState groundState) {
        NanookCrystal crystal = new NanookCrystal(ModEntities.NANOOK_CRYSTAL.get(), level);
        crystal.setPos(x, groundY, z);
        crystal.xo = x;
        crystal.yo = groundY;
        crystal.zo = z;
        crystal.damage = damage;
        crystal.ownerId = owner != null ? owner.getUUID() : null;
        crystal.groundState = groundState;
        crystal.getEntityData().set(DATA_DELAY, Math.max(0, delay));
        crystal.getEntityData().set(DATA_SIZE, size);
        crystal.getEntityData().set(DATA_SEED, level.random.nextInt());
        level.addFreshEntity(crystal);
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(DATA_DELAY, 0);
        this.getEntityData().define(DATA_SIZE, 1.0F);
        this.getEntityData().define(DATA_SEED, 0);
    }

    // ---------------------------------------------------------------
    // Lectura para el renderer
    // ---------------------------------------------------------------

    public int getDelay() {
        return this.getEntityData().get(DATA_DELAY);
    }

    public float getCrystalSize() {
        return this.getEntityData().get(DATA_SIZE);
    }

    public int getSeed() {
        return this.getEntityData().get(DATA_SEED);
    }

    /**
     * Cuánto ha crecido, de 0 (nada) a ~1 (completo). Sube con easeOutBack
     * (se pasa un poco de 1.0 y vuelve: eso es el "rebote" al surgir) y se
     * hunde cuadráticamente al final.
     */
    public float getGrowth(float partialTick) {
        float t = this.tickCount + partialTick - this.getDelay();
        if (t <= 0.0F) {
            return 0.0F;
        }
        if (t < RISE_TICKS) {
            float x = t / RISE_TICKS;
            float c1 = 1.70158F;
            float c3 = c1 + 1.0F;
            float k = x - 1.0F;
            return 1.0F + c3 * k * k * k + c1 * k * k;
        }
        float holdEnd = RISE_TICKS + HOLD_TICKS;
        if (t <= holdEnd) {
            return 1.0F;
        }
        float s = (t - holdEnd) / SINK_TICKS;
        return s >= 1.0F ? 0.0F : 1.0F - s * s;
    }

    private int lifeEnd() {
        return this.getDelay() + RISE_TICKS + HOLD_TICKS + SINK_TICKS;
    }

    // ---------------------------------------------------------------
    // Tick (solo el servidor hace algo; el cliente se dibuja solo)
    // ---------------------------------------------------------------

    @Override
    public void tick() {
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        if (this.level().isClientSide || !(this.level() instanceof ServerLevel server)) {
            return;
        }

        // tickCount ya vale 1 en el primer tick del servidor (el Level lo
        // incrementa antes de llamar a tick()), así que con delay 0 la
        // comparación t == delay no ocurriría nunca y el cristal surgiría sin
        // efectos ni sonido. Por eso el delay efectivo es como mínimo 1.
        int delay = Math.max(1, this.getDelay());
        int t = this.tickCount;

        // AVISO: grietas justo antes de surgir.
        if (t >= delay - TELEGRAPH_TICKS && t < delay && (t - delay) % 2 == 0) {
            telegraphFx(server);
        }

        if (t == delay) {
            eruptFx(server);
        }
        if (t >= delay && t < delay + RISE_TICKS) {
            tickDamage(server);
        }

        int shatterTick = delay + RISE_TICKS + HOLD_TICKS + SINK_TICKS / 2;
        if (t == shatterTick) {
            shatterFx(server);
        }
        if (t >= this.lifeEnd()) {
            this.discard();
        }
    }

    private void telegraphFx(ServerLevel server) {
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, this.groundState),
                this.getX(), this.getY() + 0.1D, this.getZ(),
                3, 0.35D * this.getCrystalSize(), 0.02D, 0.35D * this.getCrystalSize(), 0.02D);
        server.sendParticles(ModParticles.SNOWY_DUST_SMALL.get(),
                this.getX(), this.getY() + 0.1D, this.getZ(),
                1, 0.3D, 0.02D, 0.3D, 0.01D);
    }

    private void eruptFx(ServerLevel server) {
        float size = this.getCrystalSize();
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, this.groundState),
                this.getX(), this.getY() + 0.2D, this.getZ(),
                8, 0.45D * size, 0.1D, 0.45D * size, 0.12D);
        server.sendParticles(ModParticles.SNOWY_DUST_SMALL.get(),
                this.getX(), this.getY() + 0.15D, this.getZ(),
                2, 0.35D * size, 0.05D, 0.35D * size, 0.03D);
        // Un sonido de cada tres cristales: una hilera de 14 sonando a la vez
        // se vuelve un ruido blanco.
        if (Math.floorMod(this.getSeed(), 3) == 0) {
            server.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE,
                    0.9F, 0.75F + (Math.floorMod(this.getSeed() >> 4, 30)) / 100.0F);
        }
    }

    private void shatterFx(ServerLevel server) {
        float size = this.getCrystalSize();
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                this.getX(), this.getY() + 1.0D * size, this.getZ(),
                10, 0.35D * size, 0.9D * size, 0.35D * size, 0.05D);
        server.sendParticles(ParticleTypes.SNOWFLAKE,
                this.getX(), this.getY() + 1.0D * size, this.getZ(),
                5, 0.3D * size, 0.7D * size, 0.3D * size, 0.02D);
        if (Math.floorMod(this.getSeed() >> 8, 3) == 0) {
            server.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.7F, 1.1F);
        }
    }

    private void tickDamage(ServerLevel server) {
        float size = this.getCrystalSize();
        double r = HIT_RADIUS * size;
        AABB box = new AABB(
                this.getX() - r, this.getY() - 0.2D, this.getZ() - r,
                this.getX() + r, this.getY() + HIT_HEIGHT * size, this.getZ() + r);

        Entity ownerEntity = this.ownerId != null ? server.getEntity(this.ownerId) : null;
        LivingEntity owner = ownerEntity instanceof LivingEntity living ? living : null;

        List<LivingEntity> targets = server.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !(e instanceof NanookEntity) && e != owner);

        for (LivingEntity target : targets) {
            if (!this.hitIds.add(target.getId())) {
                continue; // ya golpeado por ESTE cristal
            }
            DamageSource source = owner != null
                    ? server.damageSources().mobAttack(owner)
                    : server.damageSources().magic();
            target.hurt(source, this.damage);

            target.setTicksFrozen(60);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));

            // Lo levanta: es un cristal saliendo debajo de sus pies.
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(motion.x * 0.4D, Math.max(motion.y, LAUNCH_VELOCITY), motion.z * 0.4D);
            target.hurtMarked = true;
        }
    }

    // ---------------------------------------------------------------
    // Boilerplate de entidad
    // ---------------------------------------------------------------

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // noSave() en el registro: nunca se guarda ni se carga.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }
}
