package com.nanookmod.entity;

import com.nanookmod.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Bloque VISUAL de Nanook. Nunca toca el mundo: ni borra el bloque real, ni
 * lo vuelve a colocar, ni hace daño. Tiene DOS modos:
 *
 * ===================================================================
 * MODO FLY (el de siempre): escombro que sale volando
 * ===================================================================
 * El bloque salta del piso girando, sube, cae y se borra solo. Sin física
 * (noPhysics), así atraviesa el terreno y no se traba en cuevas.
 *
 * ===================================================================
 * MODO BULGE (nuevo): la ABOLLADURA
 * ===================================================================
 * Esto es lo que pediste después de revisar The Immortal: el golpe NO rompe
 * el suelo, lo MUEVE. El terreno real queda intacto; encima se dibuja una
 * copia de cada bloque de la zona que:
 *
 *   1. SUBE unas décimas de bloque (RISE_TICKS, en pocos ticks),
 *   2. se INCLINA (tilt) como una losa levantada,
 *   3. se QUEDA ahí un rato (hold, distinto para cada bloque, así no se
 *      hunden todos a la vez),
 *   4. y se HUNDE de nuevo hasta quedar exactamente sobre el bloque real,
 *      donde deja de verse.
 *
 * La copia no tiene colisión: el jugador sigue pisando el bloque real, que
 * queda unas décimas más abajo que lo que ve. Es el mismo truco de EEEAB's
 * Mobs (ver FallingMoveType.SIMULATE_RUPTURE en su EntityFallingBlock), y
 * lo que hace que parezca una abolladura y no un montón de bloques sueltos
 * es que CADA BLOQUE SE INCLINA DISTINTO y que el borde sube más que el
 * centro (lo decide NanookEntity al spawnear: ver spawnGroundDent).
 *
 * Toda la animación es una función PURA de tickCount y de tres datos
 * sincronizados (subida, hold, inclinación). No hay estado incremental que
 * el cliente y el servidor puedan simular distinto, y NO se manda ninguna
 * actualización de posición: la entidad nace quieta y se borra sola. Eso
 * importa porque el golpe al suelo spawnea cientos.
 *
 * (Reimplementación propia de la mecánica: EEEAB's Mobs es LGPL-3.0 y
 * NanookMod no, así que no se copió código, solo se replicó el efecto.)
 */
public class NanookRisingBlock extends Entity {

    private static final EntityDataAccessor<Integer> DATA_BLOCK_STATE_ID =
            SynchedEntityData.defineId(NanookRisingBlock.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(NanookRisingBlock.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_BULGE_RISE =
            SynchedEntityData.defineId(NanookRisingBlock.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_BULGE_HOLD =
            SynchedEntityData.defineId(NanookRisingBlock.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Quaternionf> DATA_BULGE_TILT =
            SynchedEntityData.defineId(NanookRisingBlock.class, EntityDataSerializers.QUATERNION);

    private static final int MODE_FLY = 0;
    private static final int MODE_BULGE = 1;

    // --- Modo FLY ---
    /** Cuánto vive como máximo (se autodestruye igual al caer bien abajo). */
    private static final int MAX_LIFETIME_TICKS = 50;
    private static final double GRAVITY = 0.055D;

    // --- Modo BULGE ---
    /** Ticks que tarda en subir a su altura máxima (easeOut). */
    public static final int BULGE_RISE_TICKS = 3;
    /** Ticks que tarda en volver a asentarse (smoothstep, sin golpe seco). */
    public static final int BULGE_SINK_TICKS = 9;

    /** Altura de partida, para saber cuándo "ya cayó" y borrarlo. */
    private double originY;

    /** Giro visual, puramente cosmético (lo lee el renderer). */
    private float spinYaw;
    private float spinPitch;
    private float spinSpeedYaw;
    private float spinSpeedPitch;

    public NanookRisingBlock(EntityType<? extends NanookRisingBlock> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
        this.spinSpeedYaw = (this.random.nextFloat() - 0.5F) * 18.0F;
        this.spinSpeedPitch = (this.random.nextFloat() - 0.5F) * 14.0F;
    }

    /** Constructor del modo FLY (el escombro de siempre). */
    public NanookRisingBlock(Level level, double x, double y, double z, BlockState state, Vec3 motion) {
        this(ModEntities.NANOOK_RISING_BLOCK.get(), level);
        this.setPos(x, y, z);
        this.originY = y;
        this.setDeltaMovement(motion);
        this.setBlockStateId(Block.getId(state));
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    // ---------------------------------------------------------------
    // Fábrica del modo BULGE
    // ---------------------------------------------------------------

    /**
     * Levanta VISUALMENTE el bloque de suelo 'ground' (no lo toca).
     *
     * @param ground bloque real del piso; la copia se dibuja sobre él
     * @param copy   qué bloque dibujar (normalmente el mismo; con nieve
     *               encima se usa SNOW_BLOCK, ver NanookEntity)
     * @param tilt   inclinación máxima de la losa
     * @param rise   cuánto sube, en bloques (0.1 = apenas, 0.4 = mucho)
     * @param hold   cuántos ticks se queda arriba antes de hundirse
     */
    public static void spawnBulge(ServerLevel level, BlockPos ground, BlockState copy,
                                  Quaternionf tilt, float rise, int hold) {
        NanookRisingBlock block = new NanookRisingBlock(ModEntities.NANOOK_RISING_BLOCK.get(), level);
        // El origen de la entidad es la CARA SUPERIOR del bloque de piso: así
        // la luz que muestrea el renderer es la del aire de encima (clara) y
        // no la del interior del bloque sólido (oscura).
        double x = ground.getX() + 0.5D;
        double y = ground.getY() + 1.0D;
        double z = ground.getZ() + 0.5D;
        block.setPos(x, y, z);
        block.xo = x;
        block.yo = y;
        block.zo = z;
        block.setBlockStateId(Block.getId(copy));
        block.getEntityData().set(DATA_MODE, MODE_BULGE);
        block.getEntityData().set(DATA_BULGE_RISE, rise);
        block.getEntityData().set(DATA_BULGE_HOLD, Math.max(0, hold));
        block.getEntityData().set(DATA_BULGE_TILT, tilt);
        level.addFreshEntity(block);
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(DATA_BLOCK_STATE_ID, Block.getId(Blocks.SNOW_BLOCK.defaultBlockState()));
        this.getEntityData().define(DATA_MODE, MODE_FLY);
        this.getEntityData().define(DATA_BULGE_RISE, 0.2F);
        this.getEntityData().define(DATA_BULGE_HOLD, 20);
        this.getEntityData().define(DATA_BULGE_TILT, new Quaternionf());
    }

    private void setBlockStateId(int id) {
        this.getEntityData().set(DATA_BLOCK_STATE_ID, id);
    }

    public BlockState getRenderedBlockState() {
        BlockState state = Block.stateById(this.getEntityData().get(DATA_BLOCK_STATE_ID));
        // Red de seguridad: si por lo que sea llegó un id inválido, mejor
        // dibujar nieve que un cubo de aire invisible.
        return state.isAir() ? Blocks.SNOW_BLOCK.defaultBlockState() : state;
    }

    public float getSpinYaw(float partialTick) {
        return this.spinYaw + this.spinSpeedYaw * partialTick;
    }

    public float getSpinPitch(float partialTick) {
        return this.spinPitch + this.spinSpeedPitch * partialTick;
    }

    // ---------------------------------------------------------------
    // BULGE: lectura para el renderer
    // ---------------------------------------------------------------

    public boolean isBulge() {
        return this.getEntityData().get(DATA_MODE) == MODE_BULGE;
    }

    public Quaternionf getBulgeTilt() {
        return this.getEntityData().get(DATA_BULGE_TILT);
    }

    private int bulgeHold() {
        return this.getEntityData().get(DATA_BULGE_HOLD);
    }

    /** Tick (con decimales) en el que termina de hundirse del todo. */
    private float bulgeEndTime() {
        return BULGE_RISE_TICKS + bulgeHold() + BULGE_SINK_TICKS;
    }

    /**
     * Cuánto está levantada la copia, en bloques, en este instante.
     * Sube con easeOutCubic, se mantiene, y baja con smoothstep hasta 0.
     */
    public float getBulgeOffset(float partialTick) {
        float rise = this.getEntityData().get(DATA_BULGE_RISE);
        float t = this.tickCount + partialTick;
        if (t < BULGE_RISE_TICKS) {
            float inv = 1.0F - (t / BULGE_RISE_TICKS);
            return rise * (1.0F - inv * inv * inv);
        }
        float holdEnd = BULGE_RISE_TICKS + bulgeHold();
        if (t <= holdEnd) {
            return rise;
        }
        return rise * (1.0F - sinkSmooth(t - holdEnd));
    }

    /**
     * Cuánto de la inclinación máxima se aplica (0..1). Arranca plana, se
     * inclina mientras sube y se endereza mientras se hunde: así vuelve a
     * quedar exactamente alineada con el bloque real y no "salta" al final.
     */
    public float getBulgeTiltFactor(float partialTick) {
        float t = this.tickCount + partialTick;
        if (t < BULGE_RISE_TICKS) {
            return Mth.clamp(t / BULGE_RISE_TICKS, 0.0F, 1.0F);
        }
        float holdEnd = BULGE_RISE_TICKS + bulgeHold();
        if (t <= holdEnd) {
            return 1.0F;
        }
        return 1.0F - sinkSmooth(t - holdEnd);
    }

    private static float sinkSmooth(float ticksIntoSink) {
        float s = Mth.clamp(ticksIntoSink / BULGE_SINK_TICKS, 0.0F, 1.0F);
        return s * s * (3.0F - 2.0F * s);
    }

    // ---------------------------------------------------------------
    // Tick
    // ---------------------------------------------------------------

    @Override
    public void tick() {
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        if (this.isBulge()) {
            // Quieto. La animación es función de tickCount (lo incrementa el
            // Level, no hace falta super.tick()). Solo el servidor decide
            // cuándo desaparecer.
            if (!this.level().isClientSide && this.tickCount > this.bulgeEndTime() + 1.0F) {
                this.discard();
            }
            return;
        }

        this.spinYaw += this.spinSpeedYaw;
        this.spinPitch += this.spinSpeedPitch;

        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x * 0.985D, motion.y - GRAVITY, motion.z * 0.985D);
        this.setPos(this.getX() + this.getDeltaMovement().x,
                this.getY() + this.getDeltaMovement().y,
                this.getZ() + this.getDeltaMovement().z);

        if (this.level().isClientSide) {
            return;
        }

        // Se borra cuando ya volvió bien por debajo del punto de salida (o
        // sea: terminó de caer y está "dentro" del piso) o si vivió de más.
        if (this.tickCount > MAX_LIFETIME_TICKS || this.getY() < this.originY - 2.5D) {
            this.discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setBlockStateId(tag.getInt("BlockStateId"));
        this.originY = tag.getDouble("OriginY");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("BlockStateId", this.getEntityData().get(DATA_BLOCK_STATE_ID));
        tag.putDouble("OriginY", this.originY);
    }

    /** Sin esto, en servidor dedicado la entidad no se le manda al cliente. */
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    /** Nunca colisiona ni empuja: es decoración. */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /** Utilidad para el spawner: el bloque de piso que está debajo de pos. */
    public static BlockPos findGroundBelow(Level level, BlockPos start, int maxDepth) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        for (int i = 0; i < maxDepth; i++) {
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir() && !state.getCollisionShape(level, cursor).isEmpty()) {
                return cursor.immutable();
            }
            cursor.move(0, -1, 0);
        }
        return null;
    }
}
