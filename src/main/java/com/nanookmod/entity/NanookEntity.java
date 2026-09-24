package com.nanookmod.entity;

import com.nanookmod.network.ModNetworking;
import com.nanookmod.network.NanookScreenShakePacket;
import com.nanookmod.network.SyncNanookHudPacket;
import com.nanookmod.registry.ModEffects;
import com.nanookmod.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Quaternionf;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Nanook, el Oso de Hielo.
 *
 * ===================================================================
 * CAMBIOS DE ESTA REVISIÓN (lista de bugs/pedidos)
 * ===================================================================
 *
 * 1. DASH: la animación de correr arrancaba ~0.7s ANTES de moverse.
 *    Causa: el estado ATTACK_ICE_CHARGE se seteaba en el tick 0 y la
 *    animación icefist_run se disparaba ahí mismo, pero el movimiento
 *    recién empezaba en el tick 14. Solución: un flag sincronizado
 *    (DATA_DASHING) que separa WINDUP de DASH. Durante el windup se
 *    reproduce 'idle' (postura de afirmarse) y recién al empezar a
 *    moverse cambia a icefist_run. Además el windup bajó de 14 a 7 ticks.
 *
 * 2. DASH TRABADO EN DESNIVELES DE 1 BLOQUE. El auto-step "a mano" no
 *    alcanzaba porque Nanook es ancho y chequeaba un solo bloque. Ahora,
 *    además, se sube maxUpStep() a 1.15 durante el dash: con eso el
 *    propio Entity#move() sube el escalón solo, igual que un jugador. Es
 *    la misma solución robusta que se puede portar al NecroKnight.
 *
 * 3. SALTO HACIA ATRÁS. El jefe saltaba sin corregir su orientación, así
 *    que si el jugador estaba detrás, volaba de espaldas. Ahora gira
 *    durante el windup (JUMP_AIM_TURN_DEGREES por tick) y al despegar
 *    fija el yaw exacto hacia el punto de aterrizaje, manteniéndolo
 *    durante todo el arco.
 *
 * 4. ABOLLADURA EN EL SUELO al aterrizar del salto y en el golpe doble.
 *    CORREGIDO en la segunda ronda: la primera versión ROMPÍA bloques de
 *    verdad (setBlock). The Immortal no hace eso: MUEVE los bloques. Ahora
 *    (ver spawnGroundDent) el mundo no se toca; se dibujan copias visuales
 *    de los bloques que suben inclinadas, se quedan un rato y se hunden.
 *
 * 5. ANILLO DE ONDA en los impactos (ModParticles.SNOWY_RING, aro plano en
 *    el piso) y AROS DE VIENTO durante el dash (ModParticles.SNOWY_WIND_RING,
 *    aros verticales por delante del oso, ver spawnWindRing).
 *
 * 6. ESTADÍSTICAS Y ENRAGE. Más vida, más armadura, daño escalado por
 *    fase y un ENRAGE real al 25% (rugido + buff de velocidad + daño).
 *
 * 7. ATAQUES NUEVOS que usan la animación double_slash, que estaba sin
 *    usar:
 *      - ATTACK_DOUBLE_SLASH: dos tajos; cada uno pega daño fijo MÁS un
 *        porcentaje de la vida MÁXIMA del objetivo (tu pedido de "no un
 *        valor fijo").
 *      - ATTACK_EXECUTION: el casi-letal (desde fase 2, ver AttackGoal), telegrafiado
 *        larguísimo, esquivable, y deja al jugador con un hilo de vida
 *        si conecta.
 *
 * 8. Los combos (rugido -> salto, etc.) los arma NanookAttackGoal.
 *
 * 9. CRISTALES DE HIELO (NanookCrystal). El abanico de garras y el aterrizaje
 *    del salto ya no lanzan NanookClawProjectile: hacen surgir hileras de
 *    cristales del suelo, como una ola (spawnCrystalRay).
 *
 * 10. PARTÍCULAS LEGIBLES. Los ataques básicos usan la variante chica
 *    (snowy_dust_small) y salen delante del oso y a ras de piso, para no tapar
 *    la animación que el jugador necesita leer.
 *
 * ===================================================================
 * CRÉDITOS / LICENCIAS
 * ===================================================================
 * Las IDEAS de VFX (onda de bloques, anillo del dash, abolladura) están
 * inspiradas en cómo se ven los jefes de EEEAB's Mobs. Nada de este
 * archivo es código ni assets de ese mod: es una reimplementación propia
 * de la mecánica. El comportamiento de partícula "tipo Chesed" también
 * está reimplementado desde cero (FD Bosses es All Rights Reserved: no se
 * tomó nada de ahí, solo se usó como referencia verbal de cómo se ve).
 */
public class NanookEntity extends Monster implements GeoEntity {

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // ---------------------------------------------------------------
    // Estados de ataque
    // ---------------------------------------------------------------

    private static final EntityDataAccessor<Integer> DATA_ATTACK_STATE =
            SynchedEntityData.defineId(NanookEntity.class, EntityDataSerializers.INT);

    /** True SOLO mientras el dash se está moviendo de verdad (ver bug 1). */
    private static final EntityDataAccessor<Boolean> DATA_DASHING =
            SynchedEntityData.defineId(NanookEntity.class, EntityDataSerializers.BOOLEAN);

    /** Se enciende una sola vez al cruzar el umbral de furia. */
    /**
     * True mientras Nanook tiene un objetivo (está peleando). Lo lee el cliente
     * para decidir si suena el tema de batalla (ver NanookMusicHandler): no se
     * puede usar "está siendo visto", porque el rango de tracking es enorme.
     */
    private static final EntityDataAccessor<Boolean> DATA_ENGAGED =
            SynchedEntityData.defineId(NanookEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> DATA_ENRAGED =
            SynchedEntityData.defineId(NanookEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int ATTACK_NONE = 0;
    public static final int ATTACK_CLAW = 1;
    public static final int ATTACK_ICE_FIST = 2;
    public static final int ATTACK_CLAW_PROJECTILE = 3;
    public static final int ATTACK_ROAR = 4;
    public static final int ATTACK_ICE_FIST_GROUND = 5;
    public static final int ATTACK_ICE_CHARGE = 6;
    public static final int ATTACK_JUMP_SMASH = 7;
    public static final int ATTACK_CHANNELLING = 8;
    public static final int ATTACK_DOUBLE_GROUND = 9;
    public static final int ATTACK_DOUBLE_SLASH = 10;
    public static final int ATTACK_EXECUTION = 11;

    // ---------------------------------------------------------------
    // Animaciones (nombres exactos de nanook.animation.json)
    // ---------------------------------------------------------------

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    /** 'run' estaba sin usar: ahora sale cuando se mueve rápido de verdad. */
    private static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation CLAW_PUNCH_ANIM = RawAnimation.begin().thenPlay("claw_punch");
    private static final RawAnimation ICE_PUNCH_ANIM = RawAnimation.begin().thenPlay("ice_punch");
    private static final RawAnimation CLAW_PROJECTILE_ANIM = RawAnimation.begin().thenPlay("forward_claw_projectile");
    private static final RawAnimation ROAR_ANIM = RawAnimation.begin().thenPlay("roar");
    private static final RawAnimation ICEPUNCH_GROUND_ANIM = RawAnimation.begin().thenPlay("icepunch_ground");
    private static final RawAnimation ICEFIST_RUN_ANIM = RawAnimation.begin().thenLoop("icefist_run");
    private static final RawAnimation JUMP_SMASH_ANIM = RawAnimation.begin().thenPlay("jump_smash");
    private static final RawAnimation CHANNELLING_ANIM = RawAnimation.begin().thenLoop("channelling");
    private static final RawAnimation DOUBLE_GROUND_ANIM = RawAnimation.begin().thenPlay("double_ground");
    /** double_slash (2.0s) -- la usan tanto el combo doble como la ejecución. */
    private static final RawAnimation DOUBLE_SLASH_ANIM = RawAnimation.begin().thenPlay("double_slash");

    /**
     * Claves internas SOLO para el controlador de animación (no son estados
     * de ataque). Sirven para que el predicate sepa que tiene que cambiar de
     * animación DENTRO de un mismo estado de ataque (windup -> dash).
     */
    private static final int ANIM_KEY_CHARGE_WINDUP = -100;
    private static final int ANIM_KEY_CHARGE_DASH = -101;

    // ---------------------------------------------------------------
    // Timings y números de cada ataque
    // ---------------------------------------------------------------

    // --- 1. Zarpazo (claw_punch: 2.0s = 40t) ---
    public static final double CLAW_RANGE = 5.0D;
    private static final int CLAW_DURATION_TICKS = 40;
    private static final int CLAW_DAMAGE_TICK = 20;
    private static final float CLAW_DAMAGE = 14.0F;
    private static final int CLAW_SHIELD_DISABLE_TICKS = 100; // 5s

    // --- 2. Puño de hielo (ice_punch: 2.0s = 40t) ---
    private static final int ICE_FIST_DURATION_TICKS = 40;
    private static final int ICE_FIST_DAMAGE_TICK = 20;
    private static final float ICE_FIST_DAMAGE = 12.0F;
    private static final int ICE_FIST_FREEZE_TICKS = 40;

    // --- 3. Garras a distancia (forward_claw_projectile: 1.9s = 38t) ---
    public static final double CLAW_PROJECTILE_RANGE = 30.0D;
    private static final int CLAW_PROJECTILE_DURATION_TICKS = 38;
    private static final int CLAW_PROJECTILE_FIRE_TICK = 25;

    // --- 4. Rugido (roar: 4.1s = 82t) ---
    public static final double ROAR_RANGE = 7.0D;
    private static final int ROAR_DURATION_TICKS = 82;
    private static final int ROAR_BURST_TICK = 36;
    private static final float ROAR_DAMAGE = 5.0F;
    private static final double ROAR_KB_HORIZONTAL = 1.1D;
    private static final double ROAR_KB_VERTICAL = 0.85D;
    private static final int ROAR_STUN_TICKS = 40;   // 2s
    private static final int ROAR_SHIELD_DISABLE_TICKS = 120; // 6s
    private static final int ROAR_RING_TICKS = 26;
    private static final int ROAR_RING_POINTS = 14;
    private static final double ROAR_RING_SPIN_DEGREES = 17.0D;
    private static final double ROAR_RING_START_RADIUS = 1.2D;
    private static final double ROAR_RING_GROWTH_PER_TICK = 0.32D;

    // --- 5. Pinchos de hielo en anillo (icepunch_ground: 2.9s = 58t) ---
    public static final double ICE_GROUND_RANGE = 22.0D;
    private static final int ICE_GROUND_DURATION_TICKS = 70;
    private static final int ICE_GROUND_FIRE_TICK = 30;
    private static final int ICE_GROUND_SPIKE_COUNT = 9;

    // --- 6. Embestida (icefist_run) ---
    public static final double ICE_CHARGE_TRIGGER_RANGE = 26.0D;
    private static final int ICE_CHARGE_DURATION_TICKS = 70;
    /**
     * BUG 1: antes 14 ticks (0.7s) de windup CON la animación de correr ya
     * puesta. Ahora son 7 ticks y encima el windup usa otra animación, así
     * que la de correr arranca justo cuando arranca el movimiento.
     */
    private static final int ICE_CHARGE_DASH_START_TICK = 7;
    private static final int ICE_CHARGE_DASH_END_TICK = 50;
    private static final double ICE_CHARGE_SPEED = 0.95D;
    private static final double ICE_CHARGE_HIT_RADIUS = 2.6D;
    private static final float ICE_CHARGE_DAMAGE = 9.0F;
    private static final double ICE_CHARGE_KB_HORIZONTAL = 1.6D;
    private static final double ICE_CHARGE_KB_VERTICAL = 0.9D;
    private static final int ICE_CHARGE_STUN_TICKS = 40;
    private static final int ICE_CHARGE_SHIELD_DISABLE_TICKS = 160; // 8s
    private static final int ICE_CHARGE_REAIM_INTERVAL = 4;
    private static final float ICE_CHARGE_MAX_TURN_DEGREES = 7.0F;
    private static final double ICE_CHARGE_STEP_ASSIST_VELOCITY = 0.5D;
    /**
     * BUG 2: altura de escalón durante el dash. Con esto Entity#move() sube
     * solo los desniveles de 1 bloque en vez de frenarse contra ellos.
     */
    private static final float ICE_CHARGE_STEP_HEIGHT = 1.15F;
    /**
     * Ticks seguidos frenado contra algo para cortar el dash. Subido de 4 a
     * 8: con el step height arreglado, 4 ticks todavía daba falsos
     * positivos al rozar una esquina en diagonal.
     */
    private static final int ICE_CHARGE_WALL_STALL_TICKS = 8;
    private static final int ICE_CHARGE_WAKE_INTERVAL = 3;
    /** Cada cuántos ticks del dash deja un anillo grande en el piso. */
    private static final int ICE_CHARGE_RING_INTERVAL = 6;
    // --- Aros de viento del dash (efecto "romper el aire") ---
    /** Cada cuántos ticks nace un aro de viento. */
    private static final int ICE_CHARGE_WIND_RING_INTERVAL = 3;
    /** A cuántos bloques POR DELANTE del oso aparece (él lo atraviesa después). */
    private static final double ICE_CHARGE_WIND_RING_LEAD = 5.5D;
    /** Radio máximo del aro, en bloques (el oso mide 3.1 de alto). */
    private static final double ICE_CHARGE_WIND_RING_SIZE = 3.5D;
    /** Multiplicador de opacidad del aro (1.0 = la base de SnowyWindRingParticle). */
    private static final double ICE_CHARGE_WIND_RING_ALPHA = 1.0D;
    /** Cada cuántos ticks salen las vetas de nieve hacia atrás a los costados. */
    private static final int ICE_CHARGE_WIND_STREAK_INTERVAL = 2;

    // --- 7. Salto aplastante (jump_smash: 3.25s = 65t) ---
    public static final double JUMP_SMASH_TRIGGER_RANGE = 24.0D;
    private static final int JUMP_SMASH_DURATION_TICKS = 78;
    private static final int JUMP_SMASH_LAUNCH_TICK = 18;   // se despega del suelo
    private static final int JUMP_SMASH_FLIGHT_TICKS = 26;  // lo que tarda el arco
    private static final double JUMP_SMASH_ARC_HEIGHT = 9.0D;
    private static final double JUMP_SMASH_MAX_DISTANCE = 22.0D;
    private static final double JUMP_SMASH_IMPACT_RADIUS = 6.0D;
    private static final float JUMP_SMASH_DAMAGE = 16.0F;
    private static final int JUMP_SMASH_STUN_TICKS = 60;    // 3s
    private static final int JUMP_SMASH_SHIELD_DISABLE_TICKS = 160; // 8s
    private static final double JUMP_SMASH_KB_HORIZONTAL = 1.3D;
    private static final double JUMP_SMASH_KB_VERTICAL = 0.7D;
    // Ola de cristales al aterrizar (reemplaza al anillo de 18 garras).
    private static final int JUMP_SMASH_CRYSTAL_RAYS = 10;
    private static final int JUMP_SMASH_CRYSTAL_PER_RAY = 5;
    private static final double JUMP_SMASH_CRYSTAL_START = 2.6D;
    private static final double JUMP_SMASH_CRYSTAL_SPACING = 2.0D;
    /** 2 ticks por escalón y 2.0 de separación = la ola sale a ~1 bloque/tick. */
    private static final int JUMP_SMASH_CRYSTAL_DELAY_PER_STEP = 2;
    private static final float JUMP_SMASH_CRYSTAL_DAMAGE = 6.0F;

    // Abanico de cristales (ataque forward_claw_projectile).
    private static final float CRYSTAL_FAN_DAMAGE = 7.0F;
    private static final double CRYSTAL_FAN_START_DISTANCE = 3.0D;
    private static final double CRYSTAL_FAN_SPACING = 1.8D;
    /** 1 tick por escalón y 1.8 de separación = la ola avanza a ~1.8 bloques/tick. */
    private static final int CRYSTAL_FAN_DELAY_PER_STEP = 1;
    private static final int CRYSTAL_FAN_MAX_COUNT = 14;
    /** Apertura entre hileras (antes ±8° con proyectiles; los cristales son más anchos). */
    private static final double CRYSTAL_FAN_SPREAD_DEGREES = 14.0D;
    /**
     * BUG 3: cuántos grados por tick puede girar mientras se prepara para
     * saltar. 18 ticks de windup x 22° = 396°, así que SIEMPRE llega a
     * encarar al jugador antes de despegar, esté donde esté.
     */
    private static final float JUMP_AIM_TURN_DEGREES = 22.0F;
    // Abolladura visual al aterrizar (ver spawnGroundDent).
    private static final double JUMP_SMASH_DENT_RADIUS = 5.5D;
    private static final float JUMP_SMASH_DENT_RISE = 0.34F;
    private static final int JUMP_SMASH_DENT_HOLD = 16;        // ticks base arriba
    private static final int JUMP_SMASH_DENT_HOLD_RANDOM = 30; // + hasta 30 al azar

    // --- 8. Canalizado de meteoros (channelling) ---
    public static final double CHANNELLING_RANGE = 34.0D;
    private static final int CHANNELLING_DURATION_TICKS = 120; // 6s
    private static final int CHANNELLING_METEOR_START_TICK = 30;
    private static final int CHANNELLING_METEOR_END_TICK = 105;
    private static final int CHANNELLING_METEOR_INTERVAL = 6;
    private static final int CHANNELLING_METEORS_PER_WAVE = 2;
    private static final double CHANNELLING_METEOR_RADIUS = 13.0D;
    private static final double CHANNELLING_METEOR_SPAWN_HEIGHT = 18.0D;

    // --- 9. Golpe al suelo a dos manos (double_ground: 3.0s = 60t) ---
    public static final double DOUBLE_GROUND_RANGE = 17.0D;
    private static final int DOUBLE_GROUND_DURATION_TICKS = 72;
    private static final int DOUBLE_GROUND_IMPACT_TICK = 34;
    private static final int DOUBLE_GROUND_WAVE_TICKS = 16;
    private static final double DOUBLE_GROUND_START_RADIUS = 1.8D;
    private static final double DOUBLE_GROUND_WAVE_SPEED = 0.95D; // bloques por tick
    private static final double DOUBLE_GROUND_BAND_HALF_WIDTH = 1.4D;
    private static final float DOUBLE_GROUND_DAMAGE = 13.0F;
    private static final int DOUBLE_GROUND_STUN_TICKS = 50;  // 2.5s
    private static final int DOUBLE_GROUND_SHIELD_DISABLE_TICKS = 200; // 10s
    private static final double DOUBLE_GROUND_KB_HORIZONTAL = 0.85D;
    private static final double DOUBLE_GROUND_KB_VERTICAL = 0.85D;
    private static final int DOUBLE_GROUND_BLOCKS_PER_RING = 11;
    private static final double DOUBLE_GROUND_BLOCK_UP_SPEED = 0.48D;
    // Abolladura visual del golpe doble: un disco inicial + un anillo por
    // cada tick de la onda (ver spawnGroundDent).
    private static final double DOUBLE_GROUND_DENT_INITIAL_RADIUS = 3.2D;
    /** Radio al que llega la onda: los bloques del borde suben más que los del centro. */
    private static final double DOUBLE_GROUND_DENT_REF_RADIUS =
            DOUBLE_GROUND_START_RADIUS + (DOUBLE_GROUND_WAVE_TICKS - 1) * DOUBLE_GROUND_WAVE_SPEED;
    private static final float DOUBLE_GROUND_DENT_RISE = 0.30F;
    /** Semiancho de la banda que se abolla en cada tick de la onda. */
    private static final double DOUBLE_GROUND_DENT_BAND = 0.7D;
    /** Cuántas copias como máximo por tick (tope de entidades: rendimiento). */
    private static final int DOUBLE_GROUND_DENT_MAX_PER_TICK = 45;

    // --- 10. Doble tajo (double_slash: 2.0s = 40t) ---
    // Ataque de COMBO: pega dos veces seguidas y cada golpe suma un
    // PORCENTAJE de la vida máxima del objetivo. La parte porcentual es
    // deliberada: hace que el ataque siga importando aunque el jugador
    // tenga 40 corazones de mods de vida extra, en vez de volverse
    // irrelevante como un número fijo.
    public static final double DOUBLE_SLASH_RANGE = 6.0D;
    private static final int DOUBLE_SLASH_DURATION_TICKS = 44;
    // Timing sacado de los keyframes de double_slash (40t): hay DOS cruces de
    // brazos. Uno rápido hacia el tick 6-8 y el fuerte hacia el 21-23 (el pecho
    // llega a 46° hacia adelante). Antes estaban en 13 y 27, que caen en la
    // preparación (brazos abiertos) y en la recuperación: golpeaba a destiempo.
    private static final int DOUBLE_SLASH_HIT_1_TICK = 8;
    private static final int DOUBLE_SLASH_HIT_2_TICK = 22;
    private static final float DOUBLE_SLASH_FLAT_DAMAGE = 6.0F;
    /** Porción de la vida MÁXIMA del objetivo que suma cada tajo. */
    private static final float DOUBLE_SLASH_MAX_HEALTH_PCT = 0.10F;
    private static final int DOUBLE_SLASH_SHIELD_DISABLE_TICKS = 80;

    // --- 11. Ejecución: "el aplauso" (casi letal, usa double_slash) ---
    // Disponible desde fase 2 (ver NanookAttackGoal). Es el aplauso de las dos manos: el oso abre los brazos,
    // se lanza hacia adelante y los cierra con todo. Deja al objetivo en un
    // hilo de vida (EXECUTION_FLOOR) sin matarlo nunca de golpe.
    //
    // SEGUNDA REVISIÓN (el jugador contó que "casi no pega"). Causas halladas:
    //  1. El daño pasaba por armadura: con diamante/netherite + Protection IV
    //     el "80% de la vida" se convertía en ~10-15% (ver applyExecutionDamage).
    //  2. Pegaba en el tick 34, pero el aplauso VISIBLE es en el 21-23: la
    //     animación ya había terminado. Ahora pega en el 22.
    //  3. El cono usaba getYRot() (rumbo de la última caminata, que al hacer
    //     strafe queda a ~90° del jugador) y era de solo 110°. Ahora usa el
    //     yaw visual, sigue al jugador hasta el tick 14 y es de 200°.
    //  4. El modelo mide 7.4 de ancho y 5.4 de alto; la hitbox 2.0 x 3.1. Un
    //     jugador "pegado" al oso puede estar a un costado, fuera de un cono
    //     angosto. Ahora hay además un círculo de radio 2.8 que pega hacia
    //     cualquier lado.
    /** A qué distancia decide lanzarlo el AttackGoal (antes 8: se lanzaba y fallaba de lejos). */
    public static final double EXECUTION_RANGE = 6.0D;
    private static final int EXECUTION_DURATION_TICKS = 58;
    /** Hasta este tick sigue al jugador; después se "compromete" y el golpe queda fijo. */
    private static final int EXECUTION_TRACK_UNTIL_TICK = 14;
    private static final float EXECUTION_TRACK_DEGREES_PER_TICK = 7.0F;
    /** Tick del aplauso en la animación (los brazos se cierran entre el 21 y el 23). */
    private static final int EXECUTION_CLAP_TICK = 22;
    /** Cada cuántos ticks pulsa el anillo que marca hasta dónde llega el aplauso. */
    private static final int EXECUTION_TELEGRAPH_RING_INTERVAL = 7;
    /** Alcance del aplastamiento (desde el centro del oso, contando el ancho del objetivo). */
    private static final double EXECUTION_CRUSH_RADIUS = 4.5D;
    private static final double EXECUTION_CRUSH_CONE_DEGREES = 200.0D;
    /** Dentro de este radio pega hacia CUALQUIER lado (el modelo es ancho). */
    private static final double EXECUTION_POINT_BLANK_RADIUS = 2.8D;
    /** Diferencia de altura máxima para que cuente como "a su alcance". */
    private static final double EXECUTION_CRUSH_HEIGHT = 4.0D;
    /** Fracción de la vida ACTUAL del objetivo que se lleva. */
    private static final float EXECUTION_CURRENT_HEALTH_PCT = 0.80F;
    /** Vida que nunca se atraviesa con este golpe (en puntos, 2 = 1 corazón). */
    private static final float EXECUTION_FLOOR = 2.0F;
    private static final int EXECUTION_SHIELD_DISABLE_TICKS = 200;
    private static final int EXECUTION_STUN_TICKS = 30;

    // --- Onda del aplauso: el "efecto colateral" para quien no fue aplastado ---
    private static final int CLAP_WAVE_TICKS = 6;
    private static final double CLAP_WAVE_START_RADIUS = 3.0D;
    /** Bloques por tick que avanza el frente de la onda. */
    private static final double CLAP_WAVE_SPEED = 1.5D;
    /** Semiancho de la banda que golpea en cada tick. */
    private static final double CLAP_WAVE_BAND = 1.7D;
    private static final float CLAP_WAVE_DAMAGE = 5.0F;
    private static final double CLAP_WAVE_KB_HORIZONTAL = 1.35D;
    private static final double CLAP_WAVE_KB_VERTICAL = 0.55D;
    /** Dónde se cierran las manos, relativo a los pies del oso (ajustable a ojo). */
    private static final double CLAP_POINT_FORWARD = 2.4D;
    private static final double CLAP_POINT_HEIGHT = 2.6D;

    // --- Viento y niebla DESPUÉS del aplauso ---
    // Las nubes GRANDES (snowy_dust) vuelven acá, pero solo desde el tick del
    // impacto en adelante: antes del aplauso la pose es lo que avisa del ataque
    // y no se puede tapar; después ya no importa y el ataque merece el espectáculo.
    /** Ticks (desde el aplauso) que sigue saliendo niebla. */
    private static final int CLAP_FOG_TICKS = 16;
    /** Ticks (desde el aplauso) que sale la ráfaga hacia adelante. */
    private static final int CLAP_GUST_TICKS = 6;
    private static final int CLAP_GUST_PUFFS_PER_TICK = 4;
    private static final int CLAP_FOG_PUFFS_PER_TICK = 3;
    /** Radio alrededor del oso donde se reparte la niebla que se queda. */
    private static final double CLAP_FOG_RADIUS = 7.0D;

    // ---------------------------------------------------------------
    // Camera shake
    // ---------------------------------------------------------------

    private static final double SHAKE_RADIUS = 26.0D;

    // ---------------------------------------------------------------
    // Enrage
    // ---------------------------------------------------------------

    private static final UUID ENRAGE_SPEED_UUID = UUID.fromString("6a7c3f18-9d4e-4f53-8a2b-1f0c7d9e4b22");
    private static final AttributeModifier ENRAGE_SPEED_MODIFIER = new AttributeModifier(
            ENRAGE_SPEED_UUID, "nanook_enrage_speed", 0.35D, AttributeModifier.Operation.MULTIPLY_TOTAL);

    private static final UUID ENRAGE_ARMOR_UUID = UUID.fromString("2b9f61aa-17c5-4d0e-9a36-6cc1f2a80d71");
    private static final AttributeModifier ENRAGE_ARMOR_MODIFIER = new AttributeModifier(
            ENRAGE_ARMOR_UUID, "nanook_enrage_armor", 4.0D, AttributeModifier.Operation.ADDITION);

    // ---------------------------------------------------------------
    // Fases
    // ---------------------------------------------------------------

    public int getPhaseIndex() {
        float pct = this.getMaxHealth() > 0 ? this.getHealth() / this.getMaxHealth() : 1.0F;
        if (pct > 0.75F) return 0;
        if (pct > 0.50F) return 1;
        if (pct > 0.25F) return 2;
        return 3;
    }

    /** Solo fiable en el cliente para la música; en el servidor usá getTarget(). */
    public boolean isEngaged() {
        return this.getEntityData().get(DATA_ENGAGED);
    }

    public boolean isEnraged() {
        return this.getEntityData().get(DATA_ENRAGED);
    }

    public double getAggressionCooldownMultiplier() {
        return switch (getPhaseIndex()) {
            case 0 -> 1.0D;
            case 1 -> 0.85D;
            case 2 -> 0.72D;
            default -> 0.55D;
        };
    }

    /**
     * Multiplicador de daño por fase. Antes el jefe pegaba EXACTAMENTE lo
     * mismo con 100% de vida que con 5%: por eso al final de la pelea se
     * sentía una esponja inofensiva. Ahora escala.
     */
    public float getDamageMultiplier() {
        return switch (getPhaseIndex()) {
            case 0 -> 1.0F;
            case 1 -> 1.12F;
            case 2 -> 1.25F;
            default -> 1.45F;
        };
    }

    private float dmg(float base) {
        return base * getDamageMultiplier();
    }

    // ---------------------------------------------------------------
    // Estado interno de la ventana de ataque
    // ---------------------------------------------------------------

    private int attackTicksElapsed = 0;
    private int currentAttackDuration = 0;
    private boolean attackEffectFired = false;
    private int lastAttackStateAnimated = ATTACK_NONE;

    // Embestida
    private double chargeDirX = 0.0D;
    private double chargeDirZ = 0.0D;
    private boolean chargeConnected = false;
    private final Set<Integer> chargeHitIds = new HashSet<>();
    private int chargeWallStallTicks = 0;
    private boolean chargeFinished = false;

    // Salto
    private Vec3 jumpStartPos = Vec3.ZERO;
    private Vec3 jumpLandPos = Vec3.ZERO;
    private boolean jumpLaunched = false;
    private boolean jumpLanded = false;

    // Canalizado
    private final Set<Integer> meteorWavesFired = new HashSet<>();

    // Golpe al suelo
    private final Set<Integer> doubleGroundHitIds = new HashSet<>();

    // Doble tajo: para que el segundo golpe no dependa de attackEffectFired
    // (que es de un solo uso).
    private boolean doubleSlashHit1Done = false;
    private boolean doubleSlashHit2Done = false;

    // ---------------------------------------------------------------
    // Bossbar
    // ---------------------------------------------------------------

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);

    public NanookEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.bossEvent.setVisible(false);
        this.noCulling = true;
    }

    /**
     * ESTADÍSTICAS.
     *
     * Vida 500 -> 760. El problema que describiste ("es una esponja de
     * daño" y a la vez "le falta vida") en realidad es UNO solo: la pelea
     * duraba mucho porque el jefe tenía vida pero no PRESIÓN. Subir la
     * vida a secas empeora eso. Por eso el cambio va acompañado de:
     *   - armadura 4 -> 9 (recorta el daño de las armas tochas sin volver
     *     inmortal al jefe frente a encantamientos de perforación)
     *   - daño base de cada ataque más alto
     *   - multiplicador de daño por fase (getDamageMultiplier)
     *   - cooldowns más cortos al bajar de vida (ya existía)
     *   - enrage real al 25%
     *
     * Si te sigue pareciendo tanque, TOCÁ ARMOR ANTES QUE MAX_HEALTH: es
     * mucho más fácil de balancear y no alarga la pelea artificialmente.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 760.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.FOLLOW_RANGE, 50.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 9.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new NanookAttackGoal(this));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.getEntityData().define(DATA_ATTACK_STATE, ATTACK_NONE);
        this.getEntityData().define(DATA_DASHING, false);
        this.getEntityData().define(DATA_ENRAGED, false);
        this.getEntityData().define(DATA_ENGAGED, false);
    }

    /**
     * BUG 2 -- desniveles de 1 bloque durante la embestida.
     *
     * El dash mueve con setDeltaMovement directo, así que no pasa por el
     * MoveControl y no hereda el auto-step del pathfinding. La forma
     * correcta de resolverlo NO es empujar hacia arriba a mano (eso hace
     * que el jefe "salte" y se vea raro), sino decirle al motor que
     * durante el dash puede subir escalones de 1.15 bloques. Entity#move()
     * hace el resto solo, igual que con un jugador.
     *
     * Este mismo override se puede copiar tal cual al NecroticKnight.
     */
    @Override
    public float maxUpStep() {
        if (getAttackState() == ATTACK_ICE_CHARGE) {
            return ICE_CHARGE_STEP_HEIGHT;
        }
        return super.maxUpStep();
    }

    // ---------------------------------------------------------------
    // Bossbar
    // ---------------------------------------------------------------

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
        ModNetworking.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncNanookHudPacket(this.getId(), true));
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
        ModNetworking.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncNanookHudPacket(this.getId(), false));
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            for (ServerPlayer p : bossEvent.getPlayers()) {
                ModNetworking.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> p),
                        new SyncNanookHudPacket(this.getId(), false));
            }
            bossEvent.removeAllPlayers();
        }
        super.remove(reason);
    }

    // ---------------------------------------------------------------
    // GeckoLib
    // ---------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Transición bajada de 5 a 3 ticks: con 5, el blend hacia icefist_run
        // ya se empezaba a ver antes de que el jefe se moviera, que era
        // parte de lo que se leía como "empieza a correr antes de correr".
        controllers.add(new AnimationController<>(this, "movement", 3, this::movementPredicate));
    }

    private PlayState movementPredicate(AnimationState<NanookEntity> state) {
        int attackState = this.getAttackState();

        if (attackState != ATTACK_NONE) {
            // BUG 1: el dash tiene DOS animaciones dentro del mismo estado.
            if (attackState == ATTACK_ICE_CHARGE) {
                boolean dashing = this.isDashing();
                int animKey = dashing ? ANIM_KEY_CHARGE_DASH : ANIM_KEY_CHARGE_WINDUP;
                if (this.lastAttackStateAnimated != animKey) {
                    this.lastAttackStateAnimated = animKey;
                    return state.setAndContinue(dashing ? ICEFIST_RUN_ANIM : IDLE_ANIM);
                }
                return PlayState.CONTINUE;
            }

            if (this.lastAttackStateAnimated != attackState) {
                this.lastAttackStateAnimated = attackState;
                return state.setAndContinue(pickAnimationFor(attackState));
            }
            return PlayState.CONTINUE;
        }

        this.lastAttackStateAnimated = ATTACK_NONE;

        if (state.isMoving()) {
            // 'run' estaba sin usar en el JSON. Se elige por velocidad real.
            double speedSqr = this.getDeltaMovement().horizontalDistanceSqr();
            return state.setAndContinue(speedSqr > 0.02D ? RUN_ANIM : WALK_ANIM);
        }
        return state.setAndContinue(IDLE_ANIM);
    }

    private RawAnimation pickAnimationFor(int attackState) {
        return switch (attackState) {
            case ATTACK_CLAW -> CLAW_PUNCH_ANIM;
            case ATTACK_ICE_FIST -> ICE_PUNCH_ANIM;
            case ATTACK_CLAW_PROJECTILE -> CLAW_PROJECTILE_ANIM;
            case ATTACK_ROAR -> ROAR_ANIM;
            case ATTACK_ICE_FIST_GROUND -> ICEPUNCH_GROUND_ANIM;
            case ATTACK_ICE_CHARGE -> ICEFIST_RUN_ANIM;
            case ATTACK_JUMP_SMASH -> JUMP_SMASH_ANIM;
            case ATTACK_CHANNELLING -> CHANNELLING_ANIM;
            case ATTACK_DOUBLE_GROUND -> DOUBLE_GROUND_ANIM;
            case ATTACK_DOUBLE_SLASH, ATTACK_EXECUTION -> DOUBLE_SLASH_ANIM;
            default -> IDLE_ANIM;
        };
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // ---------------------------------------------------------------
    // Disparadores
    // ---------------------------------------------------------------

    public int getAttackState() {
        return this.getEntityData().get(DATA_ATTACK_STATE);
    }

    public boolean isAttacking() {
        return this.getAttackState() != ATTACK_NONE;
    }

    /** True solo mientras la embestida se está moviendo de verdad. */
    public boolean isDashing() {
        return this.getEntityData().get(DATA_DASHING);
    }

    /** Lo consulta el AttackGoal para encadenar combos tras una embestida. */
    public boolean didLastChargeConnect() {
        return this.chargeConnected;
    }

    /** Mientras esto sea true, el AttackGoal NO debe mover ni girar a Nanook. */
    public boolean isLockedInPlace() {
        int state = getAttackState();
        return state == ATTACK_ICE_CHARGE || state == ATTACK_JUMP_SMASH || state == ATTACK_CHANNELLING;
    }

    public void triggerClawAttack() {
        if (!isAttacking()) startAttack(ATTACK_CLAW, CLAW_DURATION_TICKS);
    }

    public void triggerIceFistAttack() {
        if (!isAttacking()) startAttack(ATTACK_ICE_FIST, ICE_FIST_DURATION_TICKS);
    }

    public void triggerClawProjectileAttack() {
        if (!isAttacking()) startAttack(ATTACK_CLAW_PROJECTILE, CLAW_PROJECTILE_DURATION_TICKS);
    }

    public void triggerRoar() {
        if (!isAttacking()) {
            startAttack(ATTACK_ROAR, ROAR_DURATION_TICKS);
            playBossSound(SoundEvents.RAVAGER_ROAR, 4.0F, 0.35F);
        }
    }

    public void triggerIceFistGround() {
        if (!isAttacking()) {
            startAttack(ATTACK_ICE_FIST_GROUND, ICE_GROUND_DURATION_TICKS);
            playBossSound(SoundEvents.RAVAGER_ATTACK, 4.0F, 0.5F);
        }
    }

    public void triggerIceCharge() {
        if (!isAttacking()) {
            startAttack(ATTACK_ICE_CHARGE, ICE_CHARGE_DURATION_TICKS);
            chargeConnected = false;
            chargeFinished = false;
            chargeWallStallTicks = 0;
            chargeHitIds.clear();
            this.getEntityData().set(DATA_DASHING, false);
            LivingEntity target = this.getTarget();
            if (target != null) {
                Vec3 dir = target.position().subtract(this.position()).normalize();
                chargeDirX = dir.x;
                chargeDirZ = dir.z;
                applyChargeVisualYaw();
            } else {
                Vec3 fwd = this.getForward();
                chargeDirX = fwd.x;
                chargeDirZ = fwd.z;
            }
            playBossSound(SoundEvents.POLAR_BEAR_WARNING, 8.0F, 0.8F);
        }
    }

    public void triggerJumpSmash() {
        if (!isAttacking()) {
            startAttack(ATTACK_JUMP_SMASH, JUMP_SMASH_DURATION_TICKS);
            jumpLaunched = false;
            jumpLanded = false;
            jumpStartPos = this.position();
            jumpLandPos = this.position();
            dentedColumns.clear();
            playBossSound(SoundEvents.POLAR_BEAR_WARNING, 6.0F, 0.6F);
        }
    }

    public void triggerDoubleGround() {
        if (!isAttacking()) {
            startAttack(ATTACK_DOUBLE_GROUND, DOUBLE_GROUND_DURATION_TICKS);
            doubleGroundHitIds.clear();
            dentedColumns.clear();
            playBossSound(SoundEvents.RAVAGER_ROAR, 5.0F, 0.45F);
        }
    }

    public void triggerChannelling() {
        if (!isAttacking()) {
            startAttack(ATTACK_CHANNELLING, CHANNELLING_DURATION_TICKS);
            meteorWavesFired.clear();
            playBossSound(SoundEvents.RAVAGER_AMBIENT, 4.0F, 0.4F);
        }
    }

    public void triggerDoubleSlash() {
        if (!isAttacking()) {
            startAttack(ATTACK_DOUBLE_SLASH, DOUBLE_SLASH_DURATION_TICKS);
            doubleSlashHit1Done = false;
            doubleSlashHit2Done = false;
            playBossSound(SoundEvents.RAVAGER_ATTACK, 3.0F, 0.9F);
        }
    }

    public void triggerExecution() {
        if (!isAttacking()) {
            startAttack(ATTACK_EXECUTION, EXECUTION_DURATION_TICKS);
            executionHitIds.clear();
            playBossSound(SoundEvents.WARDEN_SONIC_CHARGE, 6.0F, 0.6F);
            playBossSound(SoundEvents.RAVAGER_ROAR, 5.0F, 0.25F);
        }
    }

    private void startAttack(int attackState, int durationTicks) {
        this.getEntityData().set(DATA_ATTACK_STATE, attackState);
        this.attackTicksElapsed = 0;
        this.currentAttackDuration = durationTicks;
        this.attackEffectFired = false;
    }

    // ---------------------------------------------------------------
    // Tick principal
    // ---------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            return;
        }

        bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        this.getEntityData().set(DATA_ENGAGED, this.isAlive() && this.getTarget() != null);
        tickEnrageCheck();
        tickAttackWindow();
    }

    /**
     * ENRAGE. Al cruzar el 25% de vida, una sola vez: rugido, VFX, y buffs
     * permanentes de velocidad y armadura. A partir de acá, además, se
     * habilita el ataque de ejecución (ver NanookAttackGoal).
     */
    private void tickEnrageCheck() {
        if (isEnraged() || getPhaseIndex() < 3) {
            return;
        }
        this.getEntityData().set(DATA_ENRAGED, true);

        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(ENRAGE_SPEED_MODIFIER)) {
            speed.addPermanentModifier(ENRAGE_SPEED_MODIFIER);
        }
        AttributeInstance armor = this.getAttribute(Attributes.ARMOR);
        if (armor != null && !armor.hasModifier(ENRAGE_ARMOR_MODIFIER)) {
            armor.addPermanentModifier(ENRAGE_ARMOR_MODIFIER);
        }

        playBossSound(SoundEvents.RAVAGER_ROAR, 14.0F, 0.2F);
        playBossSound(SoundEvents.WARDEN_ANGRY, 8.0F, 0.5F);
        shakeCamerasNearby(0.8F, 24);
        spawnSnowyBurst(this.getX(), this.getY() + 1.5D, this.getZ(), 90, 2.5D, 2.0D, 0.25D);
        spawnGroundRing(this.position(), 2.2D, 2.0D);

        // Un empujón de cortesía: avisa físicamente que cambió la pelea.
        for (LivingEntity victim : nearbyLivingTargets(8.0D)) {
            Vec3 away = victim.position().subtract(this.position());
            if (away.lengthSqr() < 1.0E-4) {
                away = this.getForward();
            }
            away = away.normalize();
            victim.setDeltaMovement(away.x * 0.9D, 0.5D, away.z * 0.9D);
            markVelocityDirty(victim);
        }
    }

    private void tickAttackWindow() {
        int state = getAttackState();
        if (state == ATTACK_NONE) {
            return;
        }

        tickAimTracking(state);

        switch (state) {
            case ATTACK_CLAW -> {
                if (fireOnce(CLAW_DAMAGE_TICK)) applyClawDamage();
            }
            case ATTACK_ICE_FIST -> {
                if (fireOnce(ICE_FIST_DAMAGE_TICK)) applyIceFistEffects();
            }
            case ATTACK_CLAW_PROJECTILE -> {
                if (fireOnce(CLAW_PROJECTILE_FIRE_TICK)) fireClawProjectileFan();
            }
            case ATTACK_ROAR -> tickRoar();
            case ATTACK_ICE_FIST_GROUND -> {
                if (fireOnce(ICE_GROUND_FIRE_TICK)) spawnIceSpikeRing();
            }
            case ATTACK_ICE_CHARGE -> tickIceCharge();
            case ATTACK_JUMP_SMASH -> tickJumpSmash();
            case ATTACK_CHANNELLING -> tickChannelling();
            case ATTACK_DOUBLE_GROUND -> tickDoubleGround();
            case ATTACK_DOUBLE_SLASH -> tickDoubleSlash();
            case ATTACK_EXECUTION -> tickExecution();
            default -> { }
        }

        attackTicksElapsed++;

        if (attackTicksElapsed >= currentAttackDuration) {
            endAttack(state);
        }
    }

    private boolean fireOnce(int tick) {
        if (attackTicksElapsed >= tick && !attackEffectFired) {
            attackEffectFired = true;
            return true;
        }
        return false;
    }

    private void endAttack(int endedState) {
        this.getEntityData().set(DATA_ATTACK_STATE, ATTACK_NONE);
        this.getEntityData().set(DATA_DASHING, false);
        this.attackEffectFired = false;

        if (endedState == ATTACK_ICE_CHARGE || endedState == ATTACK_JUMP_SMASH) {
            this.setNoGravity(false);
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        }

        // Si la embestida NO conectó, Nanook queda "clavado" y termina
        // rugiendo de frustración. El AttackGoal puede encadenar desde ahí.
        if (endedState == ATTACK_ICE_CHARGE && !chargeConnected && this.getTarget() != null) {
            triggerRoar();
        }
    }

    // ---------------------------------------------------------------
    // 1. Zarpazo
    // ---------------------------------------------------------------

    private void applyClawDamage() {
        playBossSound(SoundEvents.RAVAGER_ATTACK, 2.0F, 1.4F);
        forEachTargetInCone(CLAW_RANGE, 180.0, target -> {
            boolean wasBlocking = target.isBlocking();
            target.hurt(this.damageSources().mobAttack(this), dmg(CLAW_DAMAGE));
            if (wasBlocking) {
                disablePlayerShield(target, CLAW_SHIELD_DISABLE_TICKS);
            }
        });
        // Ataque básico: el polvo sale DELANTE del oso, chico y a ras de piso. Antes
        // era una nube grande a la altura del cuerpo y tapaba la animación.
        Vec3 fwd = flatForward();
        spawnSnowyBurstSmall(this.getX() + fwd.x * 3.0D, this.getY() + 0.4D, this.getZ() + fwd.z * 3.0D,
                8, 1.2D, 0.3D, 0.04D);
    }

    // ---------------------------------------------------------------
    // 2. Puño de hielo
    // ---------------------------------------------------------------

    private void applyIceFistEffects() {
        playBossSound(SoundEvents.RAVAGER_ATTACK, 2.0F, 1.4F);
        forEachTargetInCone(CLAW_RANGE, 180.0, target -> {
            target.hurt(this.damageSources().mobAttack(this), dmg(ICE_FIST_DAMAGE));
            target.setTicksFrozen(ICE_FIST_FREEZE_TICKS);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1));
        });
        Vec3 fwd = flatForward();
        spawnSnowyBurstSmall(this.getX() + fwd.x * 3.0D, this.getY() + 0.4D, this.getZ() + fwd.z * 3.0D,
                10, 1.4D, 0.3D, 0.04D);
    }

    // ---------------------------------------------------------------
    // 3. Abanico de garras
    // ---------------------------------------------------------------

    private void fireClawProjectileFan() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            return;
        }
        playBossSound(SoundEvents.EVOKER_FANGS_ATTACK, 4.0F, 1.0F);

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Ya no son garras proyectil: son HILERAS DE CRISTALES que salen del suelo
        // una tras otra hacia el jugador, como una ola. El método conserva el
        // nombre para no tocar el resto de la cadena (tickAttackWindow, etc.).
        boolean lowHealth = getPhaseIndex() >= 2;
        double s = CRYSTAL_FAN_SPREAD_DEGREES;
        double[] offsets = lowHealth
                ? new double[]{0, s, -s, 2 * s, -2 * s}
                : new double[]{0, s, -s};

        Vec3 toTarget = flatDirectionTo(target);
        double horizontal = Math.sqrt(
                Math.pow(target.getX() - this.getX(), 2) + Math.pow(target.getZ() - this.getZ(), 2));
        // Que la hilera llegue un poco más allá del jugador, sin pasarse.
        int count = Mth.clamp(
                (int) Math.ceil((horizontal + 5.0D - CRYSTAL_FAN_START_DISTANCE) / CRYSTAL_FAN_SPACING),
                6, CRYSTAL_FAN_MAX_COUNT);

        for (double offsetDegrees : offsets) {
            Vec3 direction = rotateAroundY(toTarget, offsetDegrees);
            spawnCrystalRay(serverLevel, this.position(), direction.x, direction.z,
                    count, CRYSTAL_FAN_START_DISTANCE, CRYSTAL_FAN_SPACING,
                    0, CRYSTAL_FAN_DELAY_PER_STEP, dmg(CRYSTAL_FAN_DAMAGE), 0.8F, 1.2F);
        }
    }

    // ---------------------------------------------------------------
    // Cristales de hielo (ola que sale del suelo)
    // ---------------------------------------------------------------

    private record CrystalGround(double topY, BlockState state) {
    }

    /**
     * Una HILERA de cristales que salen del suelo uno tras otro a lo largo de
     * una dirección. El efecto de "ola" es exactamente esto: cada cristal
     * tiene un retraso mayor que el anterior (baseDelay + i * delayPerStep), así
     * que el frente de la hilera avanza a spacing / delayPerStep bloques por tick.
     *
     * Cada cristal busca SU PROPIO suelo (la hilera sube y baja con el
     * terreno) y se salta las columnas donde no hay piso, hay agua, o el terreno
     * está demasiado alto.
     *
     * @param sizeStart tamaño del primer cristal
     * @param sizeEnd   tamaño del último (la hilera crece hacia el final)
     */
    private void spawnCrystalRay(ServerLevel level, Vec3 origin, double dirX, double dirZ,
                                 int count, double startDistance, double spacing,
                                 int baseDelay, int delayPerStep, float damage,
                                 float sizeStart, float sizeEnd) {
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 1.0E-6D) {
            return;
        }
        dirX /= len;
        dirZ /= len;
        double perpX = -dirZ;
        double perpZ = dirX;

        for (int i = 0; i < count; i++) {
            double distance = startDistance + i * spacing;
            // Un poco de desvío lateral: una fila perfecta se ve artificial.
            double lateral = (this.random.nextDouble() - 0.5D) * 0.6D;
            double x = origin.x + dirX * distance + perpX * lateral;
            double z = origin.z + dirZ * distance + perpZ * lateral;

            CrystalGround ground = findCrystalGround(level, x, origin.y, z);
            if (ground == null) {
                continue;
            }
            float t = count > 1 ? i / (float) (count - 1) : 0.0F;
            float size = Mth.lerp(t, sizeStart, sizeEnd) * (0.9F + this.random.nextFloat() * 0.2F);
            NanookCrystal.spawn(level, x, ground.topY(), z,
                    baseDelay + i * delayPerStep, size, damage, this, ground.state());
        }
    }

    /**
     * La cara superior del suelo en la columna (x, z), buscando desde 3 bloques
     * por encima de 'aroundY'. null si no hay piso (vacío), hay agua, o el
     * terreno ya está más alto que el punto de partida (una ladera: el cristal
     * quedaría enterrado dentro del cerro). Usa la forma de colisión, así que
     * una losa o una capa de nieve dan su altura real y no la del bloque entero.
     */
    private CrystalGround findCrystalGround(ServerLevel level, double x, double aroundY, double z) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(x, aroundY + 3.0D, z).mutable();
        for (int i = 0; i < 10; i++) {
            BlockState state = level.getBlockState(cursor);
            if (!state.getFluidState().isEmpty()) {
                return null;
            }
            if (!state.isAir() && !state.is(BlockTags.LEAVES)) {
                VoxelShape shape = state.getCollisionShape(level, cursor);
                if (!shape.isEmpty()) {
                    if (i == 0) {
                        return null; // el punto de partida ya está dentro del terreno
                    }
                    return new CrystalGround(cursor.getY() + shape.max(Direction.Axis.Y), state);
                }
            }
            cursor.move(Direction.DOWN);
        }
        return null;
    }

    // ---------------------------------------------------------------
    // 4. Rugido
    // ---------------------------------------------------------------

    private void tickRoar() {
        if (fireOnce(ROAR_BURST_TICK)) {
            applyRoarBurst();
        }

        int ringTick = attackTicksElapsed - ROAR_BURST_TICK;
        if (ringTick >= 0 && ringTick < ROAR_RING_TICKS) {
            spawnRoarRing(ringTick);
        }
    }

    private void spawnRoarRing(int ringTick) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        double radius = ROAR_RING_START_RADIUS + ringTick * ROAR_RING_GROWTH_PER_TICK;
        double spin = ringTick * ROAR_RING_SPIN_DEGREES;
        double y = this.getY() + Math.max(0.15D, 1.6D - ringTick * 0.09D);

        for (int i = 0; i < ROAR_RING_POINTS; i++) {
            double angle = Math.toRadians(spin + (360.0D / ROAR_RING_POINTS) * i);
            double dirX = Math.cos(angle);
            double dirZ = Math.sin(angle);

            double px = this.getX() + dirX * radius;
            double pz = this.getZ() + dirZ * radius;

            // count = 0 hace que MC interprete los 3 doubles siguientes como
            // VELOCIDAD en vez de dispersión.
            serverLevel.sendParticles(ModParticles.SNOWY_DUST.get(),
                    px, y, pz,
                    0,
                    dirX * 0.35D, -0.05D, dirZ * 0.35D,
                    1.0D);
        }
    }

    private void applyRoarBurst() {
        playBossSound(SoundEvents.RAVAGER_ROAR, 12.0F, 0.3F);
        shakeCamerasNearby(0.55F, 16);
        spawnGroundRing(this.position(), 1.6D, 1.4D);
        DamageSource source = this.damageSources().mobAttack(this);

        for (LivingEntity target : nearbyLivingTargets(ROAR_RANGE)) {
            boolean wasBlocking = target.isBlocking();
            target.hurt(source, dmg(ROAR_DAMAGE));

            Vec3 away = target.position().subtract(this.position());
            if (away.lengthSqr() < 1.0E-4) {
                away = this.getForward();
            }
            away = away.normalize();
            target.setDeltaMovement(
                    away.x * ROAR_KB_HORIZONTAL,
                    ROAR_KB_VERTICAL,
                    away.z * ROAR_KB_HORIZONTAL);
            markVelocityDirty(target);

            target.addEffect(new MobEffectInstance(ModEffects.STUN.get(), ROAR_STUN_TICKS, 0));
            if (wasBlocking) {
                disablePlayerShield(target, ROAR_SHIELD_DISABLE_TICKS);
            }
        }
    }

    // ---------------------------------------------------------------
    // 5. Pinchos de hielo en anillo
    // ---------------------------------------------------------------

    private void spawnIceSpikeRing() {
        playBossSound(SoundEvents.GENERIC_EXPLODE, 4.0F, 0.4F);

        double startAngle = this.random.nextDouble() * 360.0D;
        for (int i = 0; i < ICE_GROUND_SPIKE_COUNT; i++) {
            double angle = startAngle + (360.0D / ICE_GROUND_SPIKE_COUNT) * i;
            double rad = Math.toRadians(angle);
            Vec3 direction = new Vec3(Math.cos(rad), 0.0D, Math.sin(rad));

            NanookIceSpikeProjectile spike = new NanookIceSpikeProjectile(this.level(), this);
            spike.setPos(this.getX() + direction.x * 1.5D,
                    this.getY() + 0.2D,
                    this.getZ() + direction.z * 1.5D);
            spike.setDeltaMovement(direction.scale(NanookIceSpikeProjectile.SPEED));
            this.level().addFreshEntity(spike);
        }
    }

    // ---------------------------------------------------------------
    // 6. Embestida
    // ---------------------------------------------------------------

    private void tickIceCharge() {
        boolean inDashWindow = attackTicksElapsed >= ICE_CHARGE_DASH_START_TICK
                && attackTicksElapsed <= ICE_CHARGE_DASH_END_TICK;

        if (!inDashWindow || chargeFinished) {
            // Windup o recovery: quieto en el lugar y SIN la animación de
            // correr (DATA_DASHING = false -> el predicate usa idle).
            this.getEntityData().set(DATA_DASHING, false);
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);

            // Durante el windup sigue corrigiendo el rumbo: si el jugador se
            // mueve en esos 7 ticks, la embestida sale apuntada igual.
            if (attackTicksElapsed < ICE_CHARGE_DASH_START_TICK) {
                LivingEntity target = this.getTarget();
                if (target != null) {
                    Vec3 dir = target.position().subtract(this.position()).normalize();
                    chargeDirX = dir.x;
                    chargeDirZ = dir.z;
                    applyChargeVisualYaw();
                }
                // Telegraph: polvo saliendo de las patas traseras.
                if (attackTicksElapsed % 2 == 0) {
                    spawnSnowyBurst(this.getX(), this.getY() + 0.15D, this.getZ(), 2, 0.8D, 0.1D, 0.02D);
                }
            }
            return;
        }

        this.getEntityData().set(DATA_DASHING, true);
        int ticksIntoDash = attackTicksElapsed - ICE_CHARGE_DASH_START_TICK;

        if (ticksIntoDash == 0) {
            // Arranque: onda de choque circular a los pies.
            spawnSnowyShockwaveRing(this.position(), 1.6D, 20, 0.55D, 0.02D);
            spawnGroundRing(this.position(), 1.5D, 1.2D);
            shakeCamerasNearby(0.3F, 8);
        }

        if (ticksIntoDash % ICE_CHARGE_REAIM_INTERVAL == 0) {
            LivingEntity target = this.getTarget();
            if (target != null) {
                steerChargeDirTowards(target.position().subtract(this.position()).normalize());
            }
        }

        this.setDeltaMovement(chargeDirX * ICE_CHARGE_SPEED, this.getDeltaMovement().y, chargeDirZ * ICE_CHARGE_SPEED);
        tickChargeAutoStep();

        if (this.level() instanceof ServerLevel serverLevel && ticksIntoDash % 2 == 0) {
            spawnSnowyBurst(this.getX(), this.getY() + 0.2D, this.getZ(), 4, 0.8D, 0.1D, 0.02D);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ZOGLIN_STEP, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        if (ticksIntoDash % ICE_CHARGE_WAKE_INTERVAL == 0) {
            spawnSnowyShockwaveRing(this.position(), 1.9D, 10, 0.32D, 0.0D);
        }
        // Aro plano en el piso (snowy_ring): la onda que se abre a los pies.
        if (ticksIntoDash % ICE_CHARGE_RING_INTERVAL == 0) {
            spawnGroundRing(this.position(), 1.0D, 0.9D);
        }
        // SEGUNDA RONDA -- el efecto de VIENTO del Nameless Guardian: aros
        // verticales por delante del oso, que él va atravesando.
        if (ticksIntoDash % ICE_CHARGE_WIND_RING_INTERVAL == 0) {
            spawnWindRing();
        }
        if (ticksIntoDash % ICE_CHARGE_WIND_STREAK_INTERVAL == 0) {
            spawnWindStreaks();
        }

        checkChargeImpact();
        tickChargeWallStall();
    }

    private void tickChargeWallStall() {
        if (this.horizontalCollision) {
            chargeWallStallTicks++;
        } else {
            chargeWallStallTicks = 0;
        }

        if (chargeWallStallTicks >= ICE_CHARGE_WALL_STALL_TICKS) {
            chargeFinished = true;
            this.getEntityData().set(DATA_DASHING, false);
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
            playBossSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.5F);
            spawnSnowyShockwaveRing(this.position(), 2.2D, 18, 0.6D, 0.05D);
            spawnGroundRing(this.position(), 1.8D, 1.3D);
            shakeCamerasNearby(0.45F, 10);
            attackTicksElapsed = Math.max(attackTicksElapsed, ICE_CHARGE_DASH_END_TICK);
        }
    }

    private void checkChargeImpact() {
        DamageSource source = this.damageSources().mobAttack(this);
        List<LivingEntity> hits = nearbyLivingTargets(ICE_CHARGE_HIT_RADIUS);
        if (hits.isEmpty()) {
            return;
        }

        for (LivingEntity target : hits) {
            if (!chargeHitIds.add(target.getId())) {
                continue; // ya lo atropelló en esta misma embestida
            }
            chargeConnected = true;

            boolean wasBlocking = target.isBlocking();
            target.hurt(source, dmg(ICE_CHARGE_DAMAGE));

            Vec3 kb = new Vec3(chargeDirX, 0.0D, chargeDirZ).normalize().scale(ICE_CHARGE_KB_HORIZONTAL);
            target.setDeltaMovement(kb.x, ICE_CHARGE_KB_VERTICAL, kb.z);
            markVelocityDirty(target);

            target.addEffect(new MobEffectInstance(ModEffects.STUN.get(), ICE_CHARGE_STUN_TICKS, 0));
            target.setTicksFrozen(60);
            if (wasBlocking) {
                disablePlayerShield(target, ICE_CHARGE_SHIELD_DISABLE_TICKS);
            }

            playBossSound(SoundEvents.GLASS_BREAK, 4.0F, 0.5F);
            spawnSnowyBurst(target.getX(), target.getY() + 1.0D, target.getZ(), 24, 1.2D, 1.0D, 0.08D);
            shakeCamerasNearby(0.5F, 10);
        }
    }

    /**
     * Red de seguridad del escalón. Con maxUpStep() arreglado esto casi
     * nunca hace falta, pero se deja para los casos raros (bloques con
     * colisión parcial, vallas, losas encimadas) donde el step del motor
     * no alcanza. A diferencia de la versión anterior, mira hacia donde va
     * EL DASH (chargeDir), no hacia donde apunta el yaw.
     */
    private void tickChargeAutoStep() {
        if (!this.horizontalCollision) {
            return;
        }
        Direction dir = Direction.fromYRot(Math.toDegrees(Mth.atan2(-chargeDirX, chargeDirZ)));
        BlockPos ahead = this.blockPosition().relative(dir);
        boolean blockedAtFeet = !this.level().getBlockState(ahead)
                .getCollisionShape(this.level(), ahead).isEmpty();
        boolean openAbove = this.level().getBlockState(ahead.above())
                .getCollisionShape(this.level(), ahead.above()).isEmpty();
        if (blockedAtFeet && openAbove) {
            this.setDeltaMovement(this.getDeltaMovement().x, ICE_CHARGE_STEP_ASSIST_VELOCITY, this.getDeltaMovement().z);
            chargeWallStallTicks = 0;
        }
    }

    private void steerChargeDirTowards(Vec3 desired) {
        double currentYaw = Math.toDegrees(Math.atan2(chargeDirZ, chargeDirX));
        double desiredYaw = Math.toDegrees(Math.atan2(desired.z, desired.x));
        double delta = Mth.wrapDegrees(desiredYaw - currentYaw);
        double clamped = Mth.clamp(delta, -ICE_CHARGE_MAX_TURN_DEGREES, (double) ICE_CHARGE_MAX_TURN_DEGREES);
        double newYaw = Math.toRadians(currentYaw + clamped);
        chargeDirX = Math.cos(newYaw);
        chargeDirZ = Math.sin(newYaw);
        applyChargeVisualYaw();
    }

    private void applyChargeVisualYaw() {
        float mcYaw = (float) (Mth.atan2(-chargeDirX, chargeDirZ) * (180.0D / Math.PI));
        this.setYRot(mcYaw);
        this.setYHeadRot(mcYaw);
        this.yBodyRot = mcYaw;
    }

    // ---------------------------------------------------------------
    // 7. Salto aplastante
    // ---------------------------------------------------------------

    /**
     * BUG 3 -- "saltaba hacia atrás".
     *
     * Antes, el jefe nunca corregía su orientación: si el jugador estaba
     * detrás, el arco lo llevaba hacia atrás mientras el modelo seguía
     * mirando al frente, y se leía como un salto de espaldas.
     *
     * Ahora hay tres correcciones encadenadas:
     *   a) Durante los 18 ticks de windup gira hacia el objetivo, de a
     *      JUMP_AIM_TURN_DEGREES por tick (alcanza para 360° completos).
     *   b) En el instante del despegue, fija el yaw EXACTO hacia el punto
     *      de aterrizaje ya calculado. Nada de "casi".
     *   c) Durante todo el arco mantiene ese yaw, así el modelo va
     *      efectivamente de frente hacia donde cae.
     */
    private void tickJumpSmash() {
        if (attackTicksElapsed < JUMP_SMASH_LAUNCH_TICK) {
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
            LivingEntity target = this.getTarget();
            if (target != null) {
                faceTowardsSmooth(target.position(), JUMP_AIM_TURN_DEGREES);
            }
            return;
        }

        if (!jumpLaunched) {
            jumpLaunched = true;
            jumpStartPos = this.position();
            jumpLandPos = resolveJumpLandingPos();
            // (b) Snap exacto hacia el destino antes de despegar.
            faceTowardsInstant(jumpLandPos);
            this.setNoGravity(true);
            playBossSound(SoundEvents.RAVAGER_ROAR, 5.0F, 0.7F);
            spawnGroundRing(this.position(), 1.4D, 1.0D);
        }

        if (!jumpLanded) {
            int flightTick = attackTicksElapsed - JUMP_SMASH_LAUNCH_TICK;
            if (flightTick <= JUMP_SMASH_FLIGHT_TICKS) {
                double t = flightTick / (double) JUMP_SMASH_FLIGHT_TICKS;
                double x = Mth.lerp(t, jumpStartPos.x, jumpLandPos.x);
                double z = Mth.lerp(t, jumpStartPos.z, jumpLandPos.z);
                double baseY = Mth.lerp(t, jumpStartPos.y, jumpLandPos.y);
                double arc = JUMP_SMASH_ARC_HEIGHT * 4.0D * t * (1.0D - t);
                this.setPos(x, baseY + arc, z);
                this.setDeltaMovement(Vec3.ZERO);
                // (c) mantener la orientación durante todo el vuelo.
                faceTowardsInstant(jumpLandPos);

                spawnSnowyBurst(this.getX(), this.getY(), this.getZ(), 3, 0.6D, 0.3D, 0.01D);
                return;
            }
            jumpLanded = true;
            this.setNoGravity(false);
            applyJumpSmashImpact();
        }
    }

    private Vec3 resolveJumpLandingPos() {
        LivingEntity target = this.getTarget();
        Vec3 desired = target != null ? target.position() : this.position();

        Vec3 delta = desired.subtract(jumpStartPos);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal > JUMP_SMASH_MAX_DISTANCE) {
            double scale = JUMP_SMASH_MAX_DISTANCE / horizontal;
            desired = jumpStartPos.add(delta.x * scale, 0.0D, delta.z * scale);
        }

        BlockPos ground = this.level().getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(desired.x, desired.y, desired.z));
        return new Vec3(desired.x, ground.getY(), desired.z);
    }

    private void applyJumpSmashImpact() {
        playBossSound(SoundEvents.GENERIC_EXPLODE, 6.0F, 0.4F);
        playBossSound(SoundEvents.RAVAGER_STUNNED, 4.0F, 0.6F);
        shakeCamerasNearby(0.9F, 16);

        DamageSource source = this.damageSources().mobAttack(this);
        for (LivingEntity target : nearbyLivingTargets(JUMP_SMASH_IMPACT_RADIUS)) {
            boolean wasBlocking = target.isBlocking();
            target.hurt(source, dmg(JUMP_SMASH_DAMAGE));

            Vec3 away = target.position().subtract(this.position());
            if (away.lengthSqr() < 1.0E-4) {
                away = this.getForward();
            }
            away = away.normalize();
            target.setDeltaMovement(
                    away.x * JUMP_SMASH_KB_HORIZONTAL,
                    JUMP_SMASH_KB_VERTICAL,
                    away.z * JUMP_SMASH_KB_HORIZONTAL);
            markVelocityDirty(target);

            target.addEffect(new MobEffectInstance(ModEffects.STUN.get(), JUMP_SMASH_STUN_TICKS, 0));
            target.setTicksFrozen(60);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            if (wasBlocking) {
                disablePlayerShield(target, JUMP_SMASH_SHIELD_DISABLE_TICKS);
            }
        }

        // Ola de cristales hacia afuera (reemplaza al anillo de garras): rayos
        // que se abren desde el punto de impacto, cada cristal un poco después
        // que el anterior.
        if (this.level() instanceof ServerLevel crystalLevel) {
            double startAngle = this.random.nextDouble() * Math.PI * 2.0D;
            for (int i = 0; i < JUMP_SMASH_CRYSTAL_RAYS; i++) {
                double rad = startAngle + (Math.PI * 2.0D / JUMP_SMASH_CRYSTAL_RAYS) * i;
                spawnCrystalRay(crystalLevel, this.position(), Math.cos(rad), Math.sin(rad),
                        JUMP_SMASH_CRYSTAL_PER_RAY, JUMP_SMASH_CRYSTAL_START, JUMP_SMASH_CRYSTAL_SPACING,
                        0, JUMP_SMASH_CRYSTAL_DELAY_PER_STEP, dmg(JUMP_SMASH_CRYSTAL_DAMAGE), 0.85F, 1.3F);
            }
        }

        spawnSnowyBurst(this.getX(), this.getY() + 0.6D, this.getZ(), 60, 3.0D, 1.0D, 0.12D);
        spawnSnowyShockwaveRing(this.position(), 2.5D, 26, 0.75D, 0.05D);
        spawnRisingBlockRing(this.position(), 3.0D, 10, 0.45D);
        // PEDIDO: anillo grande en el piso, como el del Nameless Guardian.
        spawnGroundRing(this.position(), 2.6D, 1.6D);
        // La abolladura de The Immortal: los bloques SE MUEVEN, no se rompen.
        // Disco completo, con el borde más levantado que el centro.
        spawnGroundDent(this.position(), 0.0D, JUMP_SMASH_DENT_RADIUS, JUMP_SMASH_DENT_RADIUS,
                JUMP_SMASH_DENT_RISE, JUMP_SMASH_DENT_HOLD, JUMP_SMASH_DENT_HOLD_RANDOM, 0.85D, 90);
    }

    // ---------------------------------------------------------------
    // 8. Canalizado de meteoros
    // ---------------------------------------------------------------

    private void tickChannelling() {
        this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        this.getNavigation().stop();

        if (attackTicksElapsed < CHANNELLING_METEOR_START_TICK
                || attackTicksElapsed > CHANNELLING_METEOR_END_TICK) {
            return;
        }
        if ((attackTicksElapsed - CHANNELLING_METEOR_START_TICK) % CHANNELLING_METEOR_INTERVAL != 0) {
            return;
        }
        if (!meteorWavesFired.add(attackTicksElapsed)) {
            return;
        }

        int count = CHANNELLING_METEORS_PER_WAVE + (isEnraged() ? 1 : 0);
        for (int i = 0; i < count; i++) {
            spawnIceMeteor();
        }

        spawnSnowyBurst(this.getX(), this.getY() + 2.0D, this.getZ(), 15, 1.5D, 1.0D, 0.02D);
    }

    private void spawnIceMeteor() {
        LivingEntity target = this.getTarget();
        Vec3 center = target != null ? target.position() : this.position();

        double angle = this.random.nextDouble() * Math.PI * 2.0D;
        double radius = this.random.nextDouble() * CHANNELLING_METEOR_RADIUS * 0.45D;
        double x = center.x + Math.cos(angle) * radius;
        double z = center.z + Math.sin(angle) * radius;
        double y = center.y + CHANNELLING_METEOR_SPAWN_HEIGHT;

        NanookIceMeteorProjectile meteor = new NanookIceMeteorProjectile(this.level(), this);
        meteor.setPos(x, y, z);
        meteor.setDeltaMovement(0.0D, -NanookIceMeteorProjectile.FALL_SPEED, 0.0D);
        this.level().addFreshEntity(meteor);

        playBossSound(SoundEvents.CHORUS_FLOWER_GROW, 1.2F, 1.2F);
    }

    // ---------------------------------------------------------------
    // 9. Golpe al suelo a dos manos (double_ground)
    // ---------------------------------------------------------------

    private void tickDoubleGround() {
        if (attackTicksElapsed < DOUBLE_GROUND_IMPACT_TICK) {
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
            this.getNavigation().stop();

            if (attackTicksElapsed > DOUBLE_GROUND_IMPACT_TICK - 12) {
                spawnSnowyBurst(this.getX(), this.getY() + 0.1D, this.getZ(), 2, 1.2D, 0.1D, 0.01D);
            }
            return;
        }

        int waveTick = attackTicksElapsed - DOUBLE_GROUND_IMPACT_TICK;

        if (waveTick == 0) {
            playBossSound(SoundEvents.GENERIC_EXPLODE, 7.0F, 0.35F);
            playBossSound(SoundEvents.RAVAGER_STUNNED, 5.0F, 0.5F);
            shakeCamerasNearby(1.0F, 20);
            spawnSnowyBurst(this.getX(), this.getY() + 0.4D, this.getZ(), 70, 2.5D, 0.8D, 0.15D);
            spawnGroundRing(this.position(), 2.2D, 1.5D);
            // Disco inicial bajo el oso; después la onda va abollando su anillo.
            spawnGroundDent(this.position(), 0.0D, DOUBLE_GROUND_DENT_INITIAL_RADIUS,
                    DOUBLE_GROUND_DENT_REF_RADIUS, DOUBLE_GROUND_DENT_RISE * 0.8F, 18, 26, 0.8D, 40);
        }

        if (waveTick >= DOUBLE_GROUND_WAVE_TICKS) {
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
            return;
        }

        double radius = DOUBLE_GROUND_START_RADIUS + waveTick * DOUBLE_GROUND_WAVE_SPEED;

        spawnRisingBlockRing(this.position(), radius, DOUBLE_GROUND_BLOCKS_PER_RING,
                DOUBLE_GROUND_BLOCK_UP_SPEED);
        // El suelo se abolla al pasar la onda (visual, no rompe nada).
        spawnGroundDent(this.position(), radius - DOUBLE_GROUND_DENT_BAND, radius + DOUBLE_GROUND_DENT_BAND,
                DOUBLE_GROUND_DENT_REF_RADIUS, DOUBLE_GROUND_DENT_RISE, 14, 22, 0.42D,
                DOUBLE_GROUND_DENT_MAX_PER_TICK);
        spawnSnowyShockwaveRing(this.position(), radius, DOUBLE_GROUND_BLOCKS_PER_RING + 6, 0.35D, 0.0D);

        if (waveTick % 4 == 0 && waveTick > 0) {
            shakeCamerasNearby(0.35F, 8);
        }

        applyDoubleGroundBand(radius);

        if (waveTick % 3 == 0) {
            playBossSound(SoundEvents.ROOTED_DIRT_BREAK, 3.0F, 0.5F);
        }
    }

    private void applyDoubleGroundBand(double radius) {
        DamageSource source = this.damageSources().mobAttack(this);
        double outer = radius + DOUBLE_GROUND_BAND_HALF_WIDTH;

        for (LivingEntity target : nearbyLivingTargets(outer)) {
            if (doubleGroundHitIds.contains(target.getId())) {
                continue;
            }
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(horizontal - radius) > DOUBLE_GROUND_BAND_HALF_WIDTH) {
                continue;
            }
            // Saltar en el momento exacto es el counterplay del ataque.
            if (!target.onGround()) {
                continue;
            }

            doubleGroundHitIds.add(target.getId());

            boolean wasBlocking = target.isBlocking();
            target.hurt(source, dmg(DOUBLE_GROUND_DAMAGE));

            Vec3 away = new Vec3(dx, 0.0D, dz);
            if (away.lengthSqr() < 1.0E-4) {
                away = this.getForward();
            }
            away = away.normalize();
            target.setDeltaMovement(
                    away.x * DOUBLE_GROUND_KB_HORIZONTAL,
                    DOUBLE_GROUND_KB_VERTICAL,
                    away.z * DOUBLE_GROUND_KB_HORIZONTAL);
            markVelocityDirty(target);

            target.addEffect(new MobEffectInstance(ModEffects.STUN.get(), DOUBLE_GROUND_STUN_TICKS, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            if (wasBlocking) {
                disablePlayerShield(target, DOUBLE_GROUND_SHIELD_DISABLE_TICKS);
            }

            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.6F);
            }
        }
    }

    // ---------------------------------------------------------------
    // 10. Doble tajo (double_slash) -- daño por % de vida MÁXIMA
    // ---------------------------------------------------------------

    private void tickDoubleSlash() {
        if (attackTicksElapsed >= DOUBLE_SLASH_HIT_1_TICK && !doubleSlashHit1Done) {
            doubleSlashHit1Done = true;
            applyDoubleSlashHit(1);
        }
        if (attackTicksElapsed >= DOUBLE_SLASH_HIT_2_TICK && !doubleSlashHit2Done) {
            doubleSlashHit2Done = true;
            applyDoubleSlashHit(2);
        }
    }

    private void applyDoubleSlashHit(int hitIndex) {
        playBossSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.5F, hitIndex == 1 ? 0.7F : 0.55F);
        DamageSource source = this.damageSources().mobAttack(this);

        forEachTargetInCone(DOUBLE_SLASH_RANGE, 180.0, target -> {
            boolean wasBlocking = target.isBlocking();
            // Daño fijo + porcentaje de la vida MÁXIMA del objetivo.
            float percentPart = target.getMaxHealth() * DOUBLE_SLASH_MAX_HEALTH_PCT;
            target.hurt(source, dmg(DOUBLE_SLASH_FLAT_DAMAGE) + percentPart);
            if (wasBlocking) {
                disablePlayerShield(target, DOUBLE_SLASH_SHIELD_DISABLE_TICKS);
            }
            spawnSnowyBurstSmall(target.getX(), target.getY() + 1.0D, target.getZ(), 6, 0.6D, 0.5D, 0.05D);
        });

        // Estela del tajo delante del jefe.
        Vec3 fwd = flatForward();
        spawnSnowyBurstSmall(this.getX() + fwd.x * 3.2D, this.getY() + 0.5D, this.getZ() + fwd.z * 3.2D,
                8, 1.2D, 0.3D, 0.06D);
        shakeCamerasNearby(0.2F, 6);
    }

    // ---------------------------------------------------------------
    // 11. Ejecución: "el aplauso" -- el golpe casi letal
    // ---------------------------------------------------------------

    /** Quién ya fue alcanzado en ESTE aplauso (aplastado o por la onda): no se golpea dos veces. */
    private final Set<Integer> executionHitIds = new HashSet<>();

    /**
     * Línea de tiempo (la animación double_slash dura 40 ticks):
     *
     *   tick 0..13   Sigue al jugador a 7°/tick. El anillo pulsa cada 7 ticks
     *                hasta el borde de la zona de aplastamiento: es el
     *                aviso de "hasta acá llega".
     *   tick 14..21  COMPROMETIDO: el yaw queda fijo. Esta es la ventana
     *                real para esquivar (irse de la zona o pasar por detrás).
     *   tick 22      APLAUSO. Aplasta a todo el que esté en la zona y lanza
     *                la onda. Coincide con el cierre de brazos de la animación.
     *   tick 22..27  La onda avanza 1.5 bloques/tick y empuja a los que NO
     *                fueron aplastados.
     *   tick 22..58  Recuperación larga: si lo esquivaste, es tu ventana.
     */
    private void tickExecution() {
        this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        this.getNavigation().stop();

        if (attackTicksElapsed < EXECUTION_TRACK_UNTIL_TICK) {
            LivingEntity target = this.getTarget();
            if (target != null) {
                faceTowardsSmooth(target.position(), EXECUTION_TRACK_DEGREES_PER_TICK);
            }
        } else {
            // Comprometido: cuerpo, cabeza y lógica miran EXACTAMENTE al mismo lado.
            pinYaw();
        }

        if (attackTicksElapsed < EXECUTION_CLAP_TICK) {
            tickExecutionTelegraph();
        }

        if (fireOnce(EXECUTION_CLAP_TICK)) {
            applyExecutionClap();
        }

        int waveTick = attackTicksElapsed - EXECUTION_CLAP_TICK;
        if (waveTick >= 0 && waveTick < CLAP_WAVE_TICKS) {
            tickClapWave(waveTick);
        }
        if (waveTick >= 1 && waveTick <= CLAP_FOG_TICKS) {
            tickClapFog(waveTick);
        }
    }

    private void tickExecutionTelegraph() {
        if (attackTicksElapsed == 0) {
            shakeCamerasNearby(0.25F, 20);
        }
        // Cada pulso se abre hasta el borde de la zona de aplastamiento. El
        // anillo de snowy_ring llega a ~3.7 bloques por unidad de tamaño.
        if (attackTicksElapsed % EXECUTION_TELEGRAPH_RING_INTERVAL == 0) {
            spawnGroundRing(this.position(), EXECUTION_CRUSH_RADIUS / 3.7D, 1.4D);
        }
        // Solo un poco de polvo chico a ras de piso, alrededor de los pies. Antes
        // salían nubes a la altura del pecho y tapaban justo la parte de la
        // animación que avisa que viene el aplauso (los brazos abriéndose).
        if (attackTicksElapsed % 3 == 0) {
            spawnSnowyBurstSmall(this.getX(), this.getY() + 0.1D, this.getZ(), 2, 2.4D, 0.03D, 0.03D);
        }
    }

    /** Punto entre las manos en el instante del aplauso. */
    private Vec3 clapPoint() {
        Vec3 fwd = flatForward();
        return new Vec3(
                this.getX() + fwd.x * CLAP_POINT_FORWARD,
                this.getY() + CLAP_POINT_HEIGHT,
                this.getZ() + fwd.z * CLAP_POINT_FORWARD);
    }

    private void applyExecutionClap() {
        playBossSound(SoundEvents.WARDEN_SONIC_BOOM, 8.0F, 0.7F);
        playBossSound(SoundEvents.GENERIC_EXPLODE, 5.0F, 0.3F);
        // El "clap" seco de las manos.
        playBossSound(SoundEvents.ANVIL_LAND, 4.0F, 0.5F);
        shakeCamerasNearby(1.0F, 18);
        spawnClapEffects();

        for (LivingEntity target : nearbyLivingTargets(EXECUTION_CRUSH_RADIUS + 1.5D)) {
            if (!isInExecutionCrush(target)) {
                continue;
            }
            executionHitIds.add(target.getId());
            crushTarget(target);
        }
    }

    /**
     * ¿Está el objetivo dentro de lo que las manos alcanzan?
     * Dos zonas: un círculo pequeño hacia cualquier lado (el modelo mide 7.4
     * de ancho, un jugador "pegado" puede estar a un costado) y un abanico
     * amplio hacia adelante hasta EXECUTION_CRUSH_RADIUS.
     */
    private boolean isInExecutionCrush(LivingEntity target) {
        if (Math.abs(target.getY() - this.getY()) > EXECUTION_CRUSH_HEIGHT) {
            return false;
        }
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        // Distancia al BORDE del objetivo, no a su centro.
        double dist = Math.sqrt(dx * dx + dz * dz) - target.getBbWidth() * 0.5D;
        if (dist <= EXECUTION_POINT_BLANK_RADIUS) {
            return true;
        }
        return dist <= EXECUTION_CRUSH_RADIUS && isInFrontCone(target, EXECUTION_CRUSH_CONE_DEGREES);
    }

    private void crushTarget(LivingEntity target) {
        boolean wasBlocking = target.isBlocking();
        if (wasBlocking) {
            disablePlayerShield(target, EXECUTION_SHIELD_DISABLE_TICKS);
        }

        applyExecutionDamage(target, wasBlocking);

        target.addEffect(new MobEffectInstance(ModEffects.STUN.get(), EXECUTION_STUN_TICKS, 0));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
        target.setTicksFrozen(80);

        Vec3 away = target.position().subtract(this.position());
        if (away.lengthSqr() < 1.0E-4) {
            away = flatForward();
        }
        away = away.normalize();
        double kb = wasBlocking ? 0.7D : 1.4D;
        target.setDeltaMovement(away.x * kb, 0.55D, away.z * kb);
        markVelocityDirty(target);
    }

    /**
     * El daño "casi letal", esta vez de verdad.
     *
     * ANTES: target.hurt(mobAttack, 80% de la vida). Ese daño pasa por
     * armadura, dureza, Protection y Resistance, así que contra diamante o
     * netherite con Protection IV el "casi letal" se llevaba ~10-15% de la
     * vida (cuentas en el LEEME). Además hurt() no hace nada si el objetivo
     * está en sus ticks de invulnerabilidad o si otro mod cancela el evento.
     *
     * AHORA:
     *  1. Se calcula cuánta vida se debe llevar (80% de la actual, mitad si
     *     bloqueó, sin bajar nunca de EXECUTION_FLOOR).
     *  2. Se llama a hurt() con daño de sonic boom (la fuente del Warden: ignora
     *     armadura, efectos y encantamientos) solo por el FEEDBACK: animación
     *     de daño, sonido, retroceso, mensaje de muerte, eventos de otros mods.
     *  3. Se FUERZA la vida final con setHealth(). Sea lo que sea que haya
     *     pasado en el paso 2, el objetivo termina con exactamente la vida
     *     calculada. Como el piso es de 1 corazón, esto nunca mata, así que no
     *     salta el tótem ni hay nada que "saltarse".
     *  4. Se borran los corazones de absorción: con manzanas doradas el golpe
     *     "casi letal" no dejaría ni un rasguño.
     *
     * Los jugadores en creativo/espectador son inmunes (isInvulnerableTo).
     */
    private void applyExecutionDamage(LivingEntity target, boolean wasBlocking) {
        DamageSource source = this.damageSources().sonicBoom(this);
        if (target.isInvulnerableTo(source)) {
            return;
        }

        float current = target.getHealth();
        float loss = current * EXECUTION_CURRENT_HEALTH_PCT * (wasBlocking ? 0.5F : 1.0F);
        loss = Math.min(loss, current - EXECUTION_FLOOR);
        if (loss <= 0.0F) {
            return;
        }

        target.hurt(source, loss);

        float wanted = current - loss;
        if (target.isAlive() && target.getHealth() > wanted) {
            target.setHealth(wanted);
        }
        if (!wasBlocking) {
            target.setAbsorptionAmount(0.0F);
        }
    }

    /**
     * Onda del aplauso. Avanza como un frente circular; el que NO fue
     * aplastado y está en la banda recibe daño menor + un empujón fuerte.
     * Es el "efecto colateral" para quien esquivó el golpe: esquivar te
     * salva de la vida, pero no de que te tiren lejos.
     */
    private void tickClapWave(int waveTick) {
        double radius = CLAP_WAVE_START_RADIUS + waveTick * CLAP_WAVE_SPEED;
        double maxRadius = CLAP_WAVE_START_RADIUS + (CLAP_WAVE_TICKS - 1) * CLAP_WAVE_SPEED;
        DamageSource source = this.damageSources().mobAttack(this);

        for (LivingEntity target : nearbyLivingTargets(radius + CLAP_WAVE_BAND + 1.0D)) {
            if (executionHitIds.contains(target.getId())) {
                continue;
            }
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(horizontal - radius) > CLAP_WAVE_BAND) {
                continue;
            }
            executionHitIds.add(target.getId());

            boolean wasBlocking = target.isBlocking();
            // Más débil cuanto más lejos del oso.
            double falloff = Mth.clamp(1.0D - 0.45D * (radius / maxRadius), 0.4D, 1.0D);

            target.hurt(source, dmg(CLAP_WAVE_DAMAGE) * (wasBlocking ? 0.5F : 1.0F));

            Vec3 away = new Vec3(dx, 0.0D, dz);
            if (away.lengthSqr() < 1.0E-4) {
                away = flatForward();
            }
            away = away.normalize();
            double kb = CLAP_WAVE_KB_HORIZONTAL * falloff * (wasBlocking ? 0.5D : 1.0D);
            target.setDeltaMovement(away.x * kb, CLAP_WAVE_KB_VERTICAL, away.z * kb);
            markVelocityDirty(target);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));

            playBossSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 2.0F, 0.6F);
        }

        // --- Visual de la onda ---
        Vec3 p = clapPoint();
        // Anillo de polvo GRANDE a ras de piso siguiendo el frente de la onda,
        // pero recién desde el tick siguiente al aplauso: en el instante del
        // impacto no tapa las manos. (El que iba a la altura de las manos sigue
        // fuera: caía justo sobre los brazos de 7 bloques de ancho.)
        if (waveTick >= 1) {
            spawnSnowyShockwaveRing(this.position(), radius, 18, 0.45D, 0.02D);
        }
        // Aros de viento verticales saliendo hacia adelante, uno por tick:
        // el aire empujado entre las dos manos.
        if (waveTick >= 1 && waveTick <= 3) {
            Vec3 fwd = flatForward();
            double d = CLAP_POINT_FORWARD + waveTick * 2.0D;
            spawnWindRingAt(this.getX() + fwd.x * d, p.y, this.getZ() + fwd.z * d,
                    this.yBodyRot, 3.6D + waveTick * 0.5D, 1.0D);
        }
    }

    /**
     * PEDIDO: "las partículas grandes, pero que salgan después del aplauso, como
     * si moviera mucho el viento o niebla".
     *
     * Dos capas, las dos con la nube GRANDE (snowy_dust):
     *
     *  1. RÁFAGA (ticks 1..CLAP_GUST_TICKS): nubes lanzadas desde el punto donde
     *     se cierran las manos, hacia adelante y abriéndose un poco. Como
     *     SnowyDustParticle no tiene gravedad y frena de a poco (fricción 0.90),
     *     salen disparadas y se van quedando: se lee como aire empujado. Salen
     *     hacia el frente, o sea lejos del oso, no sobre él.
     *  2. NIEBLA (ticks 1..CLAP_FOG_TICKS): parches repartidos a ras de piso en
     *     un radio de 7 alrededor, con una deriva lenta hacia afuera. Es lo que
     *     queda flotando después del golpe, como polvo y nieve levantados.
     *
     * Los tres números de velocidad viajan como "velocidad" con count = 0 (ver
     * SnowyDustParticle: no le suma nada, sale exactamente con esa velocidad).
     */
    private void tickClapFog(int waveTick) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 p = clapPoint();
        Vec3 fwd = flatForward();
        Vec3 right = new Vec3(-fwd.z, 0.0D, fwd.x);

        if (waveTick <= CLAP_GUST_TICKS) {
            for (int i = 0; i < CLAP_GUST_PUFFS_PER_TICK; i++) {
                double speed = 0.55D + this.random.nextDouble() * 0.35D;
                double side = (this.random.nextDouble() - 0.5D) * 0.5D;
                double vx = fwd.x * speed + right.x * side;
                double vz = fwd.z * speed + right.z * side;
                double vy = (this.random.nextDouble() - 0.5D) * 0.06D;
                double lateral = (this.random.nextDouble() - 0.5D) * 2.0D;
                serverLevel.sendParticles(ModParticles.SNOWY_DUST.get(),
                        p.x + right.x * lateral,
                        p.y - 0.6D + this.random.nextDouble() * 1.2D,
                        p.z + right.z * lateral,
                        0, vx, vy, vz, 1.0D);
            }
        }

        for (int i = 0; i < CLAP_FOG_PUFFS_PER_TICK; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double dist = 2.0D + this.random.nextDouble() * (CLAP_FOG_RADIUS - 2.0D);
            double dirX = Math.cos(angle);
            double dirZ = Math.sin(angle);
            serverLevel.sendParticles(ModParticles.SNOWY_DUST.get(),
                    this.getX() + dirX * dist,
                    this.getY() + 0.3D + this.random.nextDouble() * 1.2D,
                    this.getZ() + dirZ * dist,
                    0, dirX * 0.10D, 0.01D + this.random.nextDouble() * 0.03D, dirZ * 0.10D, 1.0D);
        }
    }

    private void spawnClapEffects() {
        Vec3 p = clapPoint();
        // 1) Aro plano en el piso (el que ya te gustó).
        spawnGroundRing(this.position(), 3.0D, 1.5D);
        // 2) Disco de onda a la altura de las manos.
        spawnRingAt(p.x, p.y, p.z, 2.6D, 1.0D);
        // 3) Aro vertical grande justo en el punto del aplauso.
        spawnWindRingAt(p.x, p.y, p.z, this.yBodyRot, 4.6D, 1.3D);
        // 4) Estallido de nieve entre las manos (chico: el clímax es el aro y la onda).
        spawnSnowyBurstSmall(p.x, p.y, p.z, 22, 1.0D, 0.8D, 0.20D);
    }

    // ---------------------------------------------------------------
    // Abolladura del terreno -- VISUAL, no toca ningún bloque
    // ---------------------------------------------------------------

    /**
     * Columnas (x,z) ya abolladas en el ataque en curso. Sin esto, los
     * anillos consecutivos de la onda (que se solapan) apilarían varias
     * copias en el mismo bloque.
     */
    private final Set<Long> dentedColumns = new HashSet<>();

    /**
     * PEDIDO (corregido): "la abolladura de The Immortal no rompe el suelo,
     * MUEVE los bloques".
     *
     * La primera versión de esto borraba bloques del mundo. Eso estaba mal.
     * Lo que hace The Immortal (verificado leyendo su EntityFallingBlock y
     * su ShockWaveUtils) es no tocar el mundo en absoluto: spawnea copias
     * VISUALES de los bloques del piso, cada una con una inclinación
     * aleatoria, que suben unas décimas, se quedan un rato y se hunden.
     * Eso es NanookRisingBlock en modo BULGE; acá solo se decide DÓNDE
     * spawnearlas y con qué parámetros.
     *
     * Lo que le da forma de abolladura (y no de bloques sueltos) es que la
     * altura crece con la distancia al centro: el centro del golpe sube
     * poco (35 % de maxRise) y el borde sube mucho (100 %). Queda un
     * cuenco con el reborde levantado. Además cada losa se inclina un poco
     * hacia afuera, más cuanto más lejos del centro.
     *
     * @param center      centro del golpe
     * @param innerRadius radio interior de la zona (0 = disco lleno)
     * @param outerRadius radio exterior de la zona
     * @param refRadius   radio al que se considera "el borde" para escalar la altura
     * @param maxRise     altura máxima (bloques) que alcanzan los del borde
     * @param holdBase    ticks mínimos que se quedan arriba
     * @param holdRandom  ticks extra al azar (hace que no se hundan todos a la vez)
     * @param density     probabilidad (0..1) de que cada bloque de la zona se abolle
     * @param maxCount    tope de copias por llamada (cada una es una entidad)
     */
    private void spawnGroundDent(Vec3 center, double innerRadius, double outerRadius, double refRadius,
                                 float maxRise, int holdBase, int holdRandom, double density, int maxCount) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int cx = Mth.floor(center.x);
        int cz = Mth.floor(center.z);
        int surfaceY = Mth.floor(center.y);
        int r = Mth.ceil(outerRadius);
        int spawned = 0;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double px = (cx + dx + 0.5D) - center.x;
                double pz = (cz + dz + 0.5D) - center.z;
                double dist = Math.sqrt(px * px + pz * pz);
                if (dist < innerRadius || dist > outerRadius) {
                    continue;
                }
                if (this.random.nextDouble() > density) {
                    continue;
                }
                if (spawned >= maxCount) {
                    return;
                }

                long key = BlockPos.asLong(cx + dx, 0, cz + dz);
                if (!dentedColumns.add(key)) {
                    continue;
                }

                BlockPos ground = findDentSurface(serverLevel, cx + dx, surfaceY, cz + dz);
                if (ground == null) {
                    continue;
                }

                // 0 en el centro, 1 en el borde.
                double t = Mth.clamp(dist / Math.max(0.5D, refRadius), 0.0D, 1.0D);
                float rise = (float) (maxRise * (0.35D + 0.65D * t) * (0.8D + this.random.nextDouble() * 0.4D));
                int hold = holdBase + this.random.nextInt(Math.max(1, holdRandom));

                // Dirección radial (hacia afuera del centro).
                double rx;
                double rz;
                if (dist > 1.0E-3D) {
                    rx = px / dist;
                    rz = pz / dist;
                } else {
                    double a = this.random.nextDouble() * Math.PI * 2.0D;
                    rx = Math.cos(a);
                    rz = Math.sin(a);
                }

                // Losa inclinada hacia afuera (más cuanto más lejos del
                // centro) + un poco de desorden en los tres ejes. Sin el
                // desorden se ve un abanico perfecto y artificial.
                float tiltRad = (float) Math.toRadians(3.0D + 12.0D * t + (this.random.nextDouble() * 8.0D - 4.0D));
                Quaternionf tilt = new Quaternionf().rotationAxis(tiltRad, (float) rz, 0.0F, (float) -rx);
                tilt.rotateY((float) Math.toRadians(this.random.nextDouble() * 40.0D - 20.0D));
                tilt.rotateX((float) Math.toRadians(this.random.nextDouble() * 10.0D - 5.0D));
                tilt.rotateZ((float) Math.toRadians(this.random.nextDouble() * 10.0D - 5.0D));

                // Con nieve encima (capa de nieve), la copia es un bloque de
                // nieve: se lee como la costra de nieve levantándose. Si no,
                // sería tierra atravesando la nieve.
                BlockState above = serverLevel.getBlockState(ground.above());
                BlockState copy = (above.is(Blocks.SNOW) || above.is(Blocks.POWDER_SNOW))
                        ? Blocks.SNOW_BLOCK.defaultBlockState()
                        : serverLevel.getBlockState(ground);

                NanookRisingBlock.spawnBulge(serverLevel, ground, copy, tilt, rise, hold);
                spawned++;
            }
        }
    }

    /**
     * El bloque de piso de la columna (x, z), buscando desde un poco por
     * encima de 'aroundY'. Devuelve null si ahí no tiene sentido abollar:
     * agua, losas/escaleras/vallas (su forma no es un cubo), hojas, bloques
     * con BlockEntity (cofres, hornos, spawners).
     */
    private BlockPos findDentSurface(ServerLevel level, int x, int aroundY, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, aroundY + 2, z);
        for (int i = 0; i < 8; i++) {
            BlockState state = level.getBlockState(cursor);
            if (!state.getFluidState().isEmpty()) {
                return null;
            }
            if (!state.isAir()) {
                if (state.is(BlockTags.LEAVES) || state.hasBlockEntity()) {
                    return null;
                }
                if (state.getRenderShape() == RenderShape.MODEL
                        && state.isCollisionShapeFullBlock(level, cursor)) {
                    return cursor.immutable();
                }
                if (!state.getCollisionShape(level, cursor).isEmpty()) {
                    return null; // losa, escalera, valla: un cubo copiado se vería mal
                }
                // Sin colisión (pasto alto, capa de nieve fina): seguir bajando.
            }
            cursor.move(Direction.DOWN);
        }
        return null;
    }

    // ---------------------------------------------------------------
    // VFX compartidos
    // ---------------------------------------------------------------

    /**
     * Anillo PLANO en el piso (la partícula snowy_ring). Es el equivalente
     * propio al "big_ring" del Nameless Guardian.
     *
     * OJO con la firma: snowy_ring usa los argumentos de velocidad como
     * PARÁMETROS (ver SnowyRingParticle):
     *   dx -> multiplicador de tamaño
     *   dy -> multiplicador de duración
     */
    private void spawnGroundRing(Vec3 center, double sizeMultiplier, double timeMultiplier) {
        spawnRingAt(center.x, center.y + 0.08D, center.z, sizeMultiplier, timeMultiplier);
    }

    /** El mismo aro plano, pero a la altura que se quiera (p. ej. la de las manos). */
    private void spawnRingAt(double x, double y, double z, double sizeMultiplier, double timeMultiplier) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(ModParticles.SNOWY_RING.get(),
                x, y, z,
                0,
                sizeMultiplier, timeMultiplier, 0.0D,
                1.0D);
    }

    /** Un aro de viento VERTICAL en cualquier punto, orientado según 'yaw'. */
    private void spawnWindRingAt(double x, double y, double z, float yaw, double peakRadius, double alphaMult) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(ModParticles.SNOWY_WIND_RING.get(),
                x, y, z,
                0,
                yaw, peakRadius, alphaMult,
                1.0D);
    }

    /**
     * Un aro de VIENTO: el efecto de "romper el aire" del dash.
     *
     * Nace por DELANTE del oso (ICE_CHARGE_WIND_RING_LEAD bloques, a la
     * altura del pecho) y de pie, perpendicular a la dirección de la
     * carrera. Como el oso corre a ~0.95 bloques/tick y el aro tarda 26
     * ticks en apagarse, lo atraviesa unos 6 ticks después de que nace y
     * queda atrás desvaneciéndose: la cadena de aros forma un túnel.
     *
     * Ver SnowyWindRingParticle para el formato de los parámetros (yaw,
     * radio y opacidad viajan como "velocidad" con count = 0).
     */
    private void spawnWindRing() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 dir = new Vec3(chargeDirX, 0.0D, chargeDirZ).normalize();
        double x = this.getX() + dir.x * ICE_CHARGE_WIND_RING_LEAD;
        double y = this.getY() + this.getBbHeight() * 0.5D;
        double z = this.getZ() + dir.z * ICE_CHARGE_WIND_RING_LEAD;

        serverLevel.sendParticles(ModParticles.SNOWY_WIND_RING.get(),
                x, y, z,
                0,
                this.getYRot(), ICE_CHARGE_WIND_RING_SIZE, ICE_CHARGE_WIND_RING_ALPHA,
                1.0D);
    }

    /**
     * Vetas de nieve que se van hacia ATRÁS a los costados del oso mientras
     * corre. Acompañan a los aros: los aros dan el "rompe el aire", esto da
     * la sensación de que el aire pasa a los lados.
     */
    private void spawnWindStreaks() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 dir = new Vec3(chargeDirX, 0.0D, chargeDirZ).normalize();
        Vec3 right = new Vec3(-dir.z, 0.0D, dir.x);

        for (int side = -1; side <= 1; side += 2) {
            double x = this.getX() + right.x * side * 1.5D;
            double y = this.getY() + 0.8D + this.random.nextDouble() * 1.6D;
            double z = this.getZ() + right.z * side * 1.5D;
            // Hacia atrás (opuesto a la carrera) y un poco hacia afuera.
            double vx = -dir.x * 0.65D + right.x * side * 0.10D;
            double vz = -dir.z * 0.65D + right.z * side * 0.10D;
            serverLevel.sendParticles(ModParticles.SNOWY_DUST.get(),
                    x, y, z, 0, vx, 0.0D, vz, 1.0D);
        }
    }

    private void spawnRisingBlockRing(Vec3 center, double radius, int points, double upSpeed) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        double startAngle = this.random.nextDouble() * 360.0D;
        for (int i = 0; i < points; i++) {
            double angle = Math.toRadians(startAngle + (360.0D / points) * i);
            double jitter = (this.random.nextDouble() - 0.5D) * 1.2D;
            double x = center.x + Math.cos(angle) * (radius + jitter);
            double z = center.z + Math.sin(angle) * (radius + jitter);

            BlockPos start = BlockPos.containing(x, center.y + 1.5D, z);
            BlockPos ground = NanookRisingBlock.findGroundBelow(serverLevel, start, 6);
            if (ground == null) {
                continue;
            }
            BlockState state = serverLevel.getBlockState(ground);
            if (state.isAir()) {
                continue;
            }

            Vec3 motion = new Vec3(
                    Math.cos(angle) * 0.09D + (this.random.nextDouble() - 0.5D) * 0.05D,
                    upSpeed + this.random.nextDouble() * 0.22D,
                    Math.sin(angle) * 0.09D + (this.random.nextDouble() - 0.5D) * 0.05D);

            NanookRisingBlock block = new NanookRisingBlock(serverLevel,
                    ground.getX() + 0.5D, ground.getY() + 0.9D, ground.getZ() + 0.5D, state, motion);
            serverLevel.addFreshEntity(block);

            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                    ground.getX() + 0.5D, ground.getY() + 1.0D, ground.getZ() + 0.5D,
                    6, 0.3D, 0.1D, 0.3D, 0.05D);
        }
    }

    private void spawnSnowyShockwaveRing(Vec3 center, double radius, int points,
                                         double outwardSpeed, double verticalSpeed) {
        spawnSnowyShockwaveRing(center, radius, points, outwardSpeed, verticalSpeed, ModParticles.SNOWY_DUST.get());
    }

    /** Ráfaga de la variante CHICA (snowy_dust_small): para ataques básicos, no tapa la animación. */
    private void spawnSnowyBurstSmall(double x, double y, double z, int count,
                                      double spreadXZ, double spreadY, double speed) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.SNOWY_DUST_SMALL.get(),
                    x, y, z, count, spreadXZ, spreadY, spreadXZ, speed);
        }
    }

    private void spawnSnowyShockwaveRing(Vec3 center, double radius, int points,
                                         double outwardSpeed, double verticalSpeed,
                                         SimpleParticleType particle) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double startAngle = this.random.nextDouble() * 360.0D;

        for (int i = 0; i < points; i++) {
            double angle = Math.toRadians(startAngle + (360.0D / points) * i);
            double dirX = Math.cos(angle);
            double dirZ = Math.sin(angle);

            serverLevel.sendParticles(particle,
                    center.x + dirX * radius, center.y + 0.25D, center.z + dirZ * radius,
                    0, // count = 0 -> los 3 doubles siguientes son VELOCIDAD
                    dirX * outwardSpeed, verticalSpeed, dirZ * outwardSpeed,
                    1.0D);
        }
    }

    private void spawnSnowyBurst(double x, double y, double z, int count,
                                 double spreadXZ, double spreadY, double speed) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.SNOWY_DUST.get(),
                    x, y, z, count, spreadXZ, spreadY, spreadXZ, speed);
        }
    }

    private void shakeCamerasNearby(float intensity, int durationTicks) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (ServerPlayer player : serverLevel.players()) {
            double distance = player.distanceTo(this);
            if (distance > SHAKE_RADIUS) {
                continue;
            }
            float falloff = (float) (1.0D - (distance / SHAKE_RADIUS));
            float finalIntensity = intensity * falloff * falloff;
            if (finalIntensity < 0.02F) {
                continue;
            }
            ModNetworking.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new NanookScreenShakePacket(finalIntensity, durationTicks));
        }
    }

    // ---------------------------------------------------------------
    // Orientación
    // ---------------------------------------------------------------

    /** Gira hacia un punto, como mucho maxDegrees por llamada. */
    private void faceTowardsSmooth(Vec3 point, float maxDegrees) {
        float desired = yawTowards(point);
        // La base es el yaw VISUAL (yBodyRot), que es el que ve el jugador. Con
        // getYRot() (el rumbo de la última caminata) el oso "saltaba" de golpe
        // al empezar a girar.
        float delta = Mth.wrapDegrees(desired - this.yBodyRot);
        float applied = Mth.clamp(delta, -maxDegrees, maxDegrees);
        setAllYaw(this.yBodyRot + applied);
    }

    /** Snap instantáneo hacia un punto (para el despegue del salto). */
    private void faceTowardsInstant(Vec3 point) {
        setAllYaw(yawTowards(point));
    }

    private float yawTowards(Vec3 point) {
        double dx = point.x - this.getX();
        double dz = point.z - this.getZ();
        if (dx * dx + dz * dz < 1.0E-6) {
            return this.getYRot();
        }
        // Misma convención que applyChargeVisualYaw.
        return (float) (Mth.atan2(-dx, dz) * (180.0D / Math.PI));
    }

    /**
     * Setea los TRES yaw a la vez. Si solo tocás setYRot(), el cuerpo sigue
     * girando por su cuenta (yBodyRot se interpola aparte) y el modelo
     * termina mirando a otro lado que el movimiento: exactamente el
     * síntoma de "salta de espaldas".
     */
    private void setAllYaw(float yaw) {
        float wrapped = Mth.wrapDegrees(yaw);
        this.setYRot(wrapped);
        this.yRotO = wrapped;
        this.setYHeadRot(wrapped);
        this.yHeadRotO = wrapped;
        this.yBodyRot = wrapped;
        this.yBodyRotO = wrapped;
    }

    // ---------------------------------------------------------------
    // Utilidades compartidas
    // ---------------------------------------------------------------

    private void disablePlayerShield(LivingEntity target, int disableTicks) {
        if (!(target instanceof Player player) || !player.isBlocking()) {
            return;
        }
        player.getCooldowns().addCooldown(player.getUseItem().getItem(), disableTicks);
        player.stopUsingItem();
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.broadcastEntityEvent(player, (byte) 30);
        }
    }

    private List<LivingEntity> nearbyLivingTargets(double radius) {
        AABB area = this.getBoundingBox().inflate(radius);
        return this.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != this && e.isAlive() && !(e instanceof NanookEntity) && this.distanceTo(e) <= radius);
    }

    private void forEachTargetInCone(double radius, double coneAngleDegrees,
                                     java.util.function.Consumer<LivingEntity> action) {
        for (LivingEntity target : nearbyLivingTargets(radius)) {
            if (isInFrontCone(target, coneAngleDegrees)) {
                action.accept(target);
            }
        }
    }

    private boolean isInFrontCone(LivingEntity target, double coneAngleDegrees) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double angleToTarget = Math.toDegrees(Math.atan2(dz, dx));
        // yBodyRot, no getYRot(): es hacia donde MIRA el modelo. getYRot() es el
        // rumbo de la última caminata y, al hacer strafe alrededor del jugador,
        // queda a ~90° de él: por eso los golpes con cono angosto no conectaban.
        double yaw = Mth.wrapDegrees(this.yBodyRot + 90.0);
        double diff = Mth.wrapDegrees(angleToTarget - yaw);
        return Math.abs(diff) <= coneAngleDegrees / 2.0;
    }

    /** Frente del modelo en el plano horizontal (sin la inclinación de la cabeza). */
    private Vec3 flatForward() {
        double rad = Math.toRadians(this.yBodyRot);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
    }

    /** Fija cuerpo, cabeza y lógica en el yaw visual actual. */
    private void pinYaw() {
        setAllYaw(this.yBodyRot);
    }

    /**
     * Puntería de los ataques cuerpo a cuerpo. ANTES ningún ataque orientaba
     * al oso: el cono de golpe salía hacia donde apuntaba su último paso, no
     * hacia el jugador. Ahora, durante la preparación, gira hacia el objetivo
     * y, pasado ese punto, se compromete (queda fijo, cuerpo y cabeza juntos).
     * Es el patrón estándar de jefe: sigue mientras prepara, se cierra antes
     * de pegar, y ese último tramo es tu ventana para esquivar.
     */
    private void tickAimTracking(int state) {
        int trackUntil = 0;
        float degreesPerTick = 0.0F;
        switch (state) {
            case ATTACK_CLAW, ATTACK_ICE_FIST -> {
                trackUntil = CLAW_DAMAGE_TICK - 6;
                degreesPerTick = 25.0F;
            }
            case ATTACK_CLAW_PROJECTILE -> {
                trackUntil = CLAW_PROJECTILE_FIRE_TICK - 5;
                degreesPerTick = 20.0F;
            }
            case ATTACK_DOUBLE_SLASH -> {
                trackUntil = DOUBLE_SLASH_HIT_1_TICK - 3;
                degreesPerTick = 30.0F;
            }
            case ATTACK_ICE_FIST_GROUND, ATTACK_ROAR, ATTACK_DOUBLE_GROUND -> {
                // Golpes en todas direcciones: la orientación es solo estética.
                trackUntil = 20;
                degreesPerTick = 15.0F;
            }
            default -> {
                return;
            }
        }

        LivingEntity target = this.getTarget();
        if (attackTicksElapsed < trackUntil && target != null) {
            faceTowardsSmooth(target.position(), degreesPerTick);
        } else {
            pinYaw();
        }
    }

    private Vec3 flatDirectionTo(LivingEntity target) {
        Vec3 flat = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
        if (flat.lengthSqr() < 1.0E-4) {
            flat = this.getForward();
        }
        return flat.normalize();
    }

    private Vec3 rotateAroundY(Vec3 vec, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(vec.x * cos - vec.z * sin, vec.y, vec.x * sin + vec.z * cos);
    }

    /** Sin esto el cliente no ve el empujón que le acabamos de aplicar. */
    private void markVelocityDirty(LivingEntity target) {
        if (target instanceof ServerPlayer sp) {
            sp.hurtMarked = true;
        }
    }

    private void playBossSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ---------------------------------------------------------------
    // Sonidos vanilla del jefe
    // ---------------------------------------------------------------

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.POLAR_BEAR_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }
}
