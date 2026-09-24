package com.nanookmod.entity;

import com.nanookmod.item.custom.NecroticScytheItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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

/**
 * La guadaña necrótica gigante (Necrotic Combo, portado del pack Awakened
 * Necromancer). Se crea UNA VEZ cuando el jugador empieza a sostener
 * NecroticScytheItem en la mano principal (ver
 * NecroticScytheItem#inventoryTick), lo sigue cada tick en animación "idle",
 * y reproduce brevemente la animación de golpe correspondiente cuando ataca.
 *
 * v6 -- dos correcciones sobre la v5:
 *
 * 1. Ya NO usamos 2 AnimationController en paralelo (swing + effect). Los
 *    datos del .bbmodel mostraron que left_horizontal2 / right_horizontal2 /
 *    scythe_raise2 YA traen el swing de h_weapon Y el efecto de h_ef_slash
 *    combinados en un solo clip -- no hace falta editar keyframes en
 *    Blockbench ni correr 2 controllers. Volvimos a un solo controller,
 *    más simple y sin el riesgo de que se pisen entre sí.
 *
 * 2. Se encadena ".thenLoop("idle")" directamente en la animación de golpe.
 *    Antes, al terminar un RawAnimation.thenPlay(...) (que se reproduce una
 *    sola vez), GeckoLib se quedaba sin qué reproducir hasta que nuestro
 *    contador de ticks reaccionaba un instante después -- en ese hueco se
 *    veía el modelo entero en su pose base (como en Blockbench, sin animar)
 *    superpuesto al jugador. Encadenando el "idle" en la misma animación,
 *    GeckoLib hace la transición él solo, sin ese hueco.
 */
public class NecroticSlashVfx extends Entity implements GeoEntity {

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Integer> DATA_STAGE =
            SynchedEntityData.defineId(NecroticSlashVfx.class, EntityDataSerializers.INT);

    // -1 = idle (loop), 0 = golpe 1, 1 = golpe 2, 2 = golpe final, 3 = giro (habilidad aparte)
    private static final int STAGE_IDLE = -1;
    public static final int STAGE_SPIN = 3;

    // Duraciones reales (segundos del .bbmodel * 20, + 1 tick de margen).
    private static final int HIT1_DURATION_TICKS = 16; // left_horizontal2  (0.75s)
    private static final int HIT2_DURATION_TICKS = 16; // right_horizontal2 (0.75s)
    private static final int HIT3_DURATION_TICKS = 11; // scythe_raise2     (0.5s)
    private static final int SPIN_DURATION_TICKS = 40; // 2 segundos, a pedido -- ver SWING_SPIN abajo

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    // El ".thenLoop("idle")" al final es la clave del fix #2 de arriba.
    private static final RawAnimation SWING_1 =
            RawAnimation.begin().thenPlay("left_horizontal2").thenLoop("idle");
    private static final RawAnimation SWING_2 =
            RawAnimation.begin().thenPlay("right_horizontal2").thenLoop("idle");
    private static final RawAnimation SWING_3 =
            RawAnimation.begin().thenPlay("scythe_raise2").thenLoop("idle");
    // A diferencia de los otros 3 golpes, el giro SE REPITE en loop (la
    // animación en sí dura 0.5s) hasta que nuestro contador de ticks
    // (SPIN_DURATION_TICKS, 2s) le pide volver a "idle" -- así conseguimos
    // que dure 2 segundos como pediste, en vez de la duración nativa corta.
    private static final RawAnimation SWING_SPIN =
            RawAnimation.begin().thenLoop("vertical_spining_slash2");

    /**
     * Offset de rotación entre "hacia dónde mira el modelo en Blockbench" y
     * "hacia dónde mira el jugador en Minecraft".
     */
    private static final float MODEL_YAW_OFFSET = 180.0F;

    @Nullable
    private Player owner;
    private int swingTicksRemaining = 0;
    private int lastAnimatedStage = Integer.MIN_VALUE;

    public NecroticSlashVfx(EntityType<? extends Entity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setOwner(Player owner) {
        this.owner = owner;
    }

    @Nullable
    public Player getOwner() {
        return owner;
    }

    /** Dispara la pose de golpe (0/1/2); vuelve a idle sola al terminar. */
    public void playSwing(int stage) {
        this.getEntityData().set(DATA_STAGE, stage);
        this.swingTicksRemaining = durationFor(stage);
    }

    private int durationFor(int stage) {
        return switch (stage) {
            case 1 -> HIT2_DURATION_TICKS;
            case 2 -> HIT3_DURATION_TICKS;
            case 3 -> SPIN_DURATION_TICKS;
            default -> HIT1_DURATION_TICKS;
        };
    }

    private int getStage() {
        return this.getEntityData().get(DATA_STAGE);
    }

    /** Para que el renderer sepa si tiene que aplicar el ajuste extra de orientación del giro. */
    public boolean isSpinning() {
        return getStage() == STAGE_SPIN;
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(DATA_STAGE, STAGE_IDLE);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            return;
        }

        if (!isOwnerStillWieldingScythe()) {
            this.discard();
            return;
        }

        this.setOldPosAndRot();
        followOwner(owner);

        if (this.level() instanceof ServerLevel serverLevel && owner.getMainHandItem().hasFoil()) {
            spawnGlintParticles(serverLevel);
        }

        if (swingTicksRemaining > 0) {
            swingTicksRemaining--;
            if (swingTicksRemaining == 0) {
                this.getEntityData().set(DATA_STAGE, STAGE_IDLE);
            }
        }
    }

    private boolean isOwnerStillWieldingScythe() {
        return owner != null
                && owner.isAlive()
                && !owner.isRemoved()
                && owner.getMainHandItem().getItem() instanceof NecroticScytheItem;
    }

    /**
     * Posiciona la guadaña al lado derecho del jugador, relativa a hacia
     * dónde mira el CUERPO (yaw), no la cámara.
     *
     * Vector "derecha" en coordenadas de Minecraft (yaw=0 => mira al sur,
     * +Z): right = (cos(yaw), sin(yaw)).
     */
    private void followOwner(Player player) {
        float yawRad = (float) Math.toRadians(player.getYRot());

        // El giro usa un pivot distinto en el modelo (rota sobre sí mismo en
        // vez de "pararse" al costado), así que tiene su propio ajuste fino
        // de posición, independiente del de los otros 3 golpes. Tocá estos
        // 3 números si el giro sigue viéndose corrido.
        boolean spinning = getStage() == STAGE_SPIN;
        double spinForwardDelta = -0.3D;
        double spinSideDelta = 0.0D;
        double spinHeightDelta = -0.2D;

        double forwardOffset = 0.6D + (spinning ? spinForwardDelta : 0.0D);
        double sideOffset = 0.4D + (spinning ? spinSideDelta : 0.0D);
        double heightOffset = player.getEyeHeight() * 0.55D + (spinning ? spinHeightDelta : 0.0D);

        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        double dx = forwardX * forwardOffset + rightX * sideOffset;
        double dz = forwardZ * forwardOffset + rightZ * sideOffset;

        Vec3 pos = player.position().add(dx, heightOffset, dz);
        this.moveTo(pos.x, pos.y, pos.z, player.getYRot() + MODEL_YAW_OFFSET, 0.0F);
    }

    /**
     * "Glint" del arma encantada, versión simple y confiable: partículas
     * saliendo de la guadaña. No es el shimmer real sobre la textura del
     * modelo (eso requeriría la API de render layers de GeckoLib).
     *
     * Usando SOUL (alitas azules) en vez de ENCHANT (las runas violetas) --
     * encaja más con la temática necro/hielo del mod. Otras opciones para
     * probar, cambiando solo esta línea:
     *   ParticleTypes.SOUL_FIRE_FLAME  -- llamas celestes chicas
     *   ParticleTypes.END_ROD          -- brillitos blancos (la usa SummoningStaffItem)
     *   ParticleTypes.SNOWFLAKE        -- copos (la usa el resto del mod para Permafrost)
     */
    private void spawnGlintParticles(ServerLevel level) {
        if (this.tickCount % 4 != 0) {
            return;
        }
        level.sendParticles(ParticleTypes.SOUL,
                this.getX(), this.getY() + 0.5D, this.getZ(),
                2, 0.4D, 0.6D, 0.4D, 0.0D);
    }

    // ---- Soporte GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "swing", 0, this::swingPredicate));
    }

    private PlayState swingPredicate(AnimationState<NecroticSlashVfx> state) {
        int stage = getStage();

        if (stage == STAGE_IDLE) {
            lastAnimatedStage = STAGE_IDLE;
            return state.setAndContinue(IDLE);
        }

        if (lastAnimatedStage != stage) {
            lastAnimatedStage = stage;
            return state.setAndContinue(pickSwingAnimation(stage));
        }

        return PlayState.CONTINUE;
    }

    private RawAnimation pickSwingAnimation(int stage) {
        return switch (stage) {
            case 1 -> SWING_2;
            case 2 -> SWING_3;
            case 3 -> SWING_SPIN;
            default -> SWING_1;
        };
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // ---- Sin guardado en disco: vive y muere junto con la sesión del dueño ----

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