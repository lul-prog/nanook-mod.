package com.nanookmod.entity;

import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModEffects;
import com.nanookmod.registry.ModEntities;
import com.nanookmod.registry.ModParticles;
import com.nanookmod.registry.ModSounds;
import com.nanookmod.event.TwilightNightHandler;
import com.nanookmod.network.ModNetworking;
import com.nanookmod.network.SyncNecroticKnightHudPacket;
import com.nanookmod.network.SyncNecroticKnightMusicPacket;
import mod.chloeprime.aaaparticles.api.common.AAALevel;
import mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * NecroticKnight - el jefe. Reutiliza (copiado, no heredado - ver charla)
 * el THRUST/CHARGE ya probado de SkeletalKnightEntity, más:
 *   - Invocación de fantasmitas (TwilightWispEntity, temporal).
 *   - Ataque de onda: se esquiva saltando.
 *   - Secuencia de aparición: pulso + explosión de knockback + oleada de
 *     fantasmitas iniciales.
 *   - Prende la Luna Crepuscular al aparecer, la apaga al morir.
 *   - Portales en 75/50/25% de vida, a TODOS los jugadores cerca.
 *
 * TODO pendiente de decisiones tuyas (no lo toqué): altar/ofrendas todavía
 * no invocan esto - por ahora hay que hacer /summon a mano para probarlo.
 * Cuando tengamos el altar, ese es el que va a llamar a
 * level.addFreshEntity(new NecroticKnightEntity(...)).
 */
public class NecroticKnightEntity extends Monster implements GeoEntity {

    private static final EntityDataAccessor<Integer> DATA_ATTACK_STATE =
            SynchedEntityData.defineId(NecroticKnightEntity.class, EntityDataSerializers.INT);
    // --- Corrupción (Pacto Necromántico) ---
    // Nivel actual (0..MAX_CORRUPTION, sube/baja durante la pelea) y
    // "cicatriz" (0..MAX_CORRUPTION, el pico más alto alcanzado ALGUNA vez
    // -- nunca baja, ver setScarLevel). Sincronizados porque el cliente los
    // necesita para el glow del modelo / texto de la bossbar (bossbar ya
    // se sincroniza sola, pero el resto del HUD/VFX que se sume después sí
    // va a necesitar leer esto del lado cliente).
    private static final EntityDataAccessor<Integer> DATA_CORRUPTION_LEVEL =
            SynchedEntityData.defineId(NecroticKnightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SCAR_LEVEL =
            SynchedEntityData.defineId(NecroticKnightEntity.class, EntityDataSerializers.INT);
    // Cargas de Drenaje Vital -- sincronizadas para cuando armemos el HUD/VFX de esto.
    private static final EntityDataAccessor<Integer> DATA_DRENAJE_CHARGES =
            SynchedEntityData.defineId(NecroticKnightEntity.class, EntityDataSerializers.INT);
    // ID de entidad del target actual DURANTE el Rayo -- getTarget() es
    // server-only (no sincronizado por Minecraft), y el cliente necesita
    // saber a QUIÉN apunta el rayo para calcular la misma dirección que
    // el servidor (ver getLightningDirection más abajo). -1 = sin target
    // (no hay rayo activo, o el target se invalidó).
    private static final EntityDataAccessor<Integer> DATA_LIGHTNING_TARGET_ID =
            SynchedEntityData.defineId(NecroticKnightEntity.class, EntityDataSerializers.INT);
    // Ángulo ACTUAL (con retraso) del rayo -- distinto del yaw/pitch del
    // cuerpo del jefe. Se actualiza girando un poco por tick hacia el
    // target (ver updateLightningAim), nunca salta directo a apuntarlo.
    // Esto es lo que hace que el rayo sea ESQUIVABLE: si te movés rápido,
    // el rayo tarda un rato en volver a encuadrarte.
    private static final EntityDataAccessor<Float> DATA_LIGHTNING_AIM_YAW =
            SynchedEntityData.defineId(NecroticKnightEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LIGHTNING_AIM_PITCH =
            SynchedEntityData.defineId(NecroticKnightEntity.class, EntityDataSerializers.FLOAT);
    // True exactamente durante sword_beam_channel (NO durante el windup).
    // OJO -- por qué existe este campo y no alcanza con
    // isLightningWindingUp(): esa función lee attackTicksElapsed, que
    // SOLO se incrementa server-side (tickAttackWindow tiene un
    // "if (!isClientSide)"). Del lado del cliente -- que es donde corre
    // el render/setCustomAnimations del brazo -- attackTicksElapsed se
    // queda pegado en 0 para siempre, así que isLightningWindingUp()
    // devuelve true todo el rato visto desde el cliente. Por eso este
    // campo SÍ se sincroniza explícitamente, mismo patrón que
    // DATA_LIGHTNING_AIM_PITCH.
    // Ticks que faltan para terminar la animación de aparición -- 0 = ya terminó (o el jefe
    // vino de una fuente vieja, como un comando /summon, que no pasa por el altar).
    private static final EntityDataAccessor<Integer> DATA_SPAWN_ANIM_TICKS_REMAINING =
            SynchedEntityData.defineId(NecroticKnightEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Boolean> DATA_LIGHTNING_CHANNELING =
            SynchedEntityData.defineId(NecroticKnightEntity.class, EntityDataSerializers.BOOLEAN);
    // Opacidad de render de la entidad REAL (0-255), sincronizada para que
    // NecroticKnightRenderer sepa cómo dibujarla. Cubre dos cosas
    // distintas que comparten el mismo mecanismo visual (alpha blend):
    // el fundido a invisible del Vanish (ver tickVanish) y el flash de
    // transparencia al recibir un golpe (ver hurt()/tickHurtFlash). Si
    // ambos quisieran tocar el alpha al mismo tiempo, Vanish manda (ver
    // tickHurtFlash, se salta a sí mismo si ATTACK_VANISH está activo).
    // 255 = totalmente opaco/normal, default.
    private static final EntityDataAccessor<Integer> DATA_RENDER_ALPHA =
            SynchedEntityData.defineId(NecroticKnightEntity.class, EntityDataSerializers.INT);

    public static final int ATTACK_NONE = 0;
    public static final int ATTACK_THRUST = 1;
    public static final int ATTACK_CHARGE = 2;
    public static final int ATTACK_GHOST_SUMMON = 3;
    public static final int ATTACK_WAVE = 4;
    public static final int ATTACK_GUARD_SLAP = 5;
    public static final int ATTACK_LIGHTNING = 6;
    public static final int ATTACK_VANISH = 7;

    // --- Thrust (idéntico al Skeletal Knight normal) ---
    private static final int THRUST_DURATION_TICKS = 23;
    private static final int THRUST_DAMAGE_TICK = 8;
    private static final float THRUST_DAMAGE = 10.0F; // un poco más que el mob normal (8), es el jefe
    public static final double THRUST_RANGE = 6.0D;

    // --- Charge (idéntico al Skeletal Knight normal) ---
    private static final int CHARGE_DURATION_TICKS = 97;
    private static final int CHARGE_DASH_START_TICK = 20;
    private static final int CHARGE_DASH_END_TICK = 90;
    private static final int[] CHARGE_DAMAGE_TICKS = {21, 23, 28, 35, 41, 47, 53, 59, 65, 71};
    private static final float CHARGE_DAMAGE_PER_HIT = THRUST_DAMAGE * 0.7F;
    // Solo el último pulso de la ráfaga manda a volar -- los anteriores
    // pegan sin empujón (ver applyChargePulseDamage) para que conecten
    // todos en secuencia, como la animación.
    private static final double CHARGE_FINAL_KB_HORIZONTAL = 2.0D;
    private static final double CHARGE_FINAL_KB_VERTICAL = 0.8D;
    private static final double CHARGE_HIT_RADIUS = 2.0D;
    private static final double CHARGE_SPEED = 0.55D;
    public static final double CHARGE_TRIGGER_RANGE = 9.0D;
    // Aturdimiento (StunEffect) que deja el golpe final de la embestida --
    // 3.5s, un poco más largo que el del manotazo porque cerrar toda la
    // ráfaga de charge+slash_loop cuesta más que esquivar un solo golpe.
    private static final int CHARGE_STUN_DURATION_TICKS = 70;

    // --- Charge: reajuste de rumbo (ver triggerCharge) ---
    // Cada cuántos ticks corrige la dirección hacia el objetivo durante el
    // dash. 12 ticks = 0.6s, en el rango que pediste (0.5-0.7s).
    private static final int CHARGE_REAIM_INTERVAL_TICKS = 12;
    // Grados máximos que puede corregir en cada reajuste. Así no es un
    // misil teledirigido perfecto: un esquive lateral fuerte todavía sirve,
    // solo que el jefe le sigue el rastro con cierto delay/límite.
    private static final float CHARGE_MAX_TURN_DEGREES = 130.0F;
    // Impulso vertical para pasar desniveles de 1 bloque sin trabarse --
    // el Charge mueve con setDeltaMovement directo, así que no hereda el
    // auto-step normal de los mobs (eso es cosa del MoveControl/pathing).
    private static final double CHARGE_STEP_ASSIST_VELOCITY = 0.5D;

    // --- Ghost summon: portales flotantes de invocación de wisps ---
    // Rediseñado a pedido: antes era una "explosión" de wisps saliendo
    // del pecho del jefe en direcciones al azar. Ahora son 2 portales
    // (reusa TwilightPortalEntity, el mismo que abre en los umbrales de
    // fase, pero SIN el cordón de energía de ese sistema -- ver el
    // comentario en spawnSingleWispPortal) que aparecen FLOTANDO arriba
    // del KNIGHT (no del jugador, para que quede en cámara -- ver
    // WISP_PORTAL_HEIGHT_ABOVE_KNIGHT), uno a cada lado, mirando hacia el
    // jugador e inclinados hacia abajo. Disparan los wisps YA apuntados a
    // él y se cierran solos, sin depender de que los golpeen. Línea de
    // tiempo completa dentro de GHOST_SUMMON_DURATION_TICKS:
    //   tick 0                    -> aparecen los 2 portales (arrancan su
    //                                 propio crecimiento, ver
    //                                 TwilightPortalEntity.SPAWN_ANIM_TICKS)
    //   WISP_PORTAL_FIRE_TICK     -> ya terminaron de crecer -- cada
    //                                 portal dispara sus propios
    //                                 GHOST_COUNT_PER_SUMMON wisps (10 en
    //                                 total entre los 2)
    //   WISP_PORTAL_CLOSE_TICK    -> arrancan a cerrarse solos (forceClose,
    //                                 ver TwilightPortalEntity.DEATH_ANIM_TICKS)
    // Pensado para que se sienta RÁPIDO -- de punta a punta, bastante
    // menos de 2 segundos.
    private static final int GHOST_SUMMON_DURATION_TICKS = 34;
    private static final int WISP_PORTAL_FIRE_TICK = 14;  // ~0.7s -- ya crecieron del todo (SPAWN_ANIM_TICKS=12) + un pelo de aire
    private static final int WISP_PORTAL_CLOSE_TICK = 18; // ~0.9s -- laten un toque después de disparar, no se cierran de un.
    private static final int GHOST_COUNT_PER_SUMMON = 5; // por PORTAL -- son 2 portales, 10 wisps en total
    // Altura sobre el KNIGHT (no sobre el jugador) a la que aparecen los
    // portales -- a propósito: si aparecieran arriba del jugador, con la
    // cámara en primera/tercera persona bien puede que ni los llegue a
    // ver (están literalmente arriba suyo, fuera de cuadro). Arriba del
    // jefe, en cambio, están en el campo de visión del jugador casi
    // siempre (ya lo está mirando A ÉL para pelear).
    // PEDIDO: "el portal flotante no me termina de gustar, o lo quitamos o lo mejoramos".
    // A 15 bloques de altura y sin ningún vínculo visual con el jefe, se leía como un objeto
    // aparte flotando en el cielo, no como parte del ataque. Dos cambios:
    //   1. Bajó a una altura mucho más legible (sigue arriba de la cabeza, pero cerca).
    //   2. Ahora SÍ tienen "dueño" (setOwnerKnightId), así que NecroticCorruptionTetherRenderer
    //      les dibuja el mismo cordón de energía verde que ya usan los portales de fase -- ese
    //      cordón es justo lo que faltaba para que se sienta conectado al jefe y no un objeto
    //      suelto. (Antes se dejaba en -1 A PROPÓSITO para que NO tuvieran cordón; era una
    //      decisión de diseño de una revisión anterior, no un bug -- se cambia de opinión acá.)
    //
    // Si con esto TODAVÍA no convence: WISP_PORTAL_ENABLED = false más abajo saca el portal
    // por completo y los wisps salen directo desde el cuerpo del jefe, sin ceremonia visual.
    private static final double WISP_PORTAL_HEIGHT_ABOVE_KNIGHT = 6.5D;
    /** Apagalo para sacar el portal del todo (ver arriba). */
    private static final boolean WISP_PORTAL_ENABLED = true;
    // Separación lateral entre los 2 portales (a cada lado, perpendicular
    // a la línea jefe-jugador -- ver spawnWispPortals()).
    private static final double WISP_PORTAL_SIDE_OFFSET = 3.0D;
    // Dispersión al azar (grados) sobre la dirección "derecho al jugador"
    // de cada wisp individual -- sin esto, los 2-3 wisps de un mismo
    // portal saldrían todos superpuestos en la misma línea recta.
    private static final double WISP_PORTAL_AIM_JITTER_DEGREES = 12.0D;

    // --- Wave (nuevo): anillo que se expande desde el jefe, hay que
    // saltarlo -- si tus pies están muy cerca del suelo cuando pasa por tu
    // posición, te pega. Si estás en el aire (saltando), no.
    // Públicas porque NecroticWaveRenderer (cliente) las necesita para
    // saber qué radio máximo/ancho de banda dibujar -- ver esa clase.
    // (antes esto era UNA sola onda con WAVE_DURATION_TICKS/WAVE_MAX_RADIUS;
    // ahora son VARIAS ondas, sincronizadas con el temblor de la espada
    // mientras está clavada -- ver WAVE_PULSES más abajo)
    //
    // WAVE_DURATION_TICKS es más largo que la animación wave_cast (5.45s =
    // 109 ticks) a propósito: la última onda (release, tick 102) tarda 34
    // ticks en terminar de expandirse y apagarse, así que el ataque tiene
    // que seguir "vivo" hasta el tick 136 (102+34) o esa onda se cortaría
    // de golpe a mitad de camino. El knight ya volvió a su pose idle bastante
    // antes de eso (la animación termina en el tick 109) -- solo se queda
    // quieto un instante extra mientras las últimas ondas terminan de sonar.
    public static final int WAVE_DURATION_TICKS = 140;
    public static final double WAVE_BAND_WIDTH = 1.2D;
    private static final double WAVE_JUMP_CLEARANCE = 0.9D; // cuánto tenés que saltar para esquivar UNA onda
    private static final float WAVE_DAMAGE = 8.0F;

    public record WavePulseDef(int tick, double maxRadius, int expansionTicks, float damageFraction) {}

    // tick = en que momento de la animacion nace la onda (ver keyframes del forcejeo)
    // maxRadius = que tan lejos llega ESA onda en particular
    // expansionTicks = cuanto tarda en llegar a maxRadius, y por lo tanto
    //                   cuanto tiempo esta "viva" en el campo antes de apagarse
    // damageFraction = multiplica a WAVE_DAMAGE -- las de temblor pegan flojo, impacto/release pegan fuerte
    public static final WavePulseDef[] WAVE_PULSES = {
            new WavePulseDef(59,  10.0D, 26, 1.0F),   // 2.95s -- la espada se clava
            new WavePulseDef(65,   6.5D, 20, 0.4F),   // forcejeo
            new WavePulseDef(73,   6.5D, 20, 0.4F),
            new WavePulseDef(81,   6.5D, 20, 0.4F),
            new WavePulseDef(89,   6.5D, 20, 0.4F),
            new WavePulseDef(96,   6.5D, 20, 0.4F),
            new WavePulseDef(102, 18.0D, 34, 1.3F),   // 5.1s -- sale la espada, remate grande
    };

    // --- Guard & Slap (nuevo): se cubre un rato (telegraph bien visible),
    // y después tira un manotazo en cono frente a él que manda a volar.
    // A diferencia de Thrust (rápido, casi sin aviso), este SÍ le da al
    // jugador una ventana clara para alejarse -- rompe el patrón de "todo
    // es rápido y hay que tankear".
    private static final int GUARD_SLAP_COVER_TICK = 8;         // 0.40s: el escudo termina de cubrir
    private static final int GUARD_SLAP_GUARD_TICKS = 31;        // 1.55s: termina la guardia, arranca el swing del escudo
    private static final int GUARD_SLAP_HIT_TICK = 34;           // 1.70s: termina el swing -- acá conecta el golpe
    private static final int GUARD_SLAP_DURATION_TICKS = 46;     // 2.30s: vuelve a la posición idle
    private static final double GUARD_SLAP_RANGE = 4.5D;           // alcance del manotazo
    private static final double GUARD_SLAP_ANGLE_DEGREES = 100.0D; // amplitud del cono frente al jefe
    private static final float GUARD_SLAP_DAMAGE = 14.0F;
    private static final double GUARD_SLAP_KB_HORIZONTAL = 2.2D;
    private static final double GUARD_SLAP_KB_VERTICAL = 0.9D;
    private static final int GUARD_SLAP_STUN_DURATION_TICKS = 60; // 3.0s de StunEffect a quien conecte el manotazo
    // Detección de "me están comboeando": si recibe HIT_TRACK_THRESHOLD
    // golpes dentro de HIT_TRACK_WINDOW_TICKS, el AttackGoal acorta el
    // cooldown de Guard & Slap para que reaccione casi al toque.
    private static final int HIT_TRACK_WINDOW_TICKS = 40; // 2s
    private static final int HIT_TRACK_THRESHOLD = 3;

    // --- Vanish (nuevo, inspirado en el teleport "BEHIND" de The Immortal
    // de EEEABsMobs -- LGPL-3.0, ver créditos en README): el jefe se
    // desvanece, se reposiciona detrás del jugador mientras es invisible,
    // y reaparece ahí. A diferencia del original (que lo dispara por
    // pacing de combate -- lo están golpeando mucho, no lo pueden
    // alcanzar, etc.), acá se dispara como un candidato más del
    // AttackGoal, con su propio cooldown -- más simple, mismo espíritu.
    //
    // Fases dentro de la duración total (VANISH_DURATION_TICKS):
    //   0                        -> arranca, empieza el fundido a invisible
    //   VANISH_FADE_OUT_TICKS    -> ya está totalmente invisible (alpha 0)
    //   VANISH_TELEPORT_TICK     -> (a oscuras) se reposiciona detrás del jugador
    //   VANISH_FADE_IN_START_TICK-> empieza a reaparecer donde se reposicionó
    //   +VANISH_FADE_IN_TICKS    -> ya está totalmente visible de nuevo
    //   VANISH_DURATION_TICKS    -> termina el estado; acá es donde, con
    //                                VANISH_SURPRISE_ATTACK_CHANCE de
    //                                probabilidad, se encadena un Thrust
    //                                inmediato como "golpe sorpresa"
    //                                (ver el final de tickAttackWindow).
    private static final int VANISH_DURATION_TICKS = 50;      // 2.5s totales
    private static final int VANISH_FADE_OUT_TICKS = 12;      // 0.6s para desaparecer
    private static final int VANISH_TELEPORT_TICK = 18;       // se reposiciona ya invisible
    private static final int VANISH_FADE_IN_START_TICK = 28;  // arranca a reaparecer
    private static final int VANISH_FADE_IN_TICKS = 12;       // 0.6s para reaparecer (termina en el tick 40)
    // Qué tan lejos, detrás del jugador (en la dirección opuesta a hacia
    // donde mira), reaparece el jefe. Ojo: esto es la distancia deseada,
    // no garantizada -- si ese punto está enterrado en un bloque,
    // findSafeTeleportPos prueba puntos alternativos (ver ese método).
    private static final double VANISH_BEHIND_DISTANCE = 2.5D;
    // Probabilidad de encadenar un Thrust apenas termina de reaparecer --
    // el "golpe sorpresa". Si no sale, el jefe simplemente vuelve a quedar
    // en manos del AttackGoal normal (puede que ni te ataque de inmediato).
    private static final double VANISH_SURPRISE_ATTACK_CHANCE = 0.5D;

    // --- Flash de transparencia al recibir daño ---
    // Marcado (pedido explícito): baja a ~35% de opacidad y vuelve, todo
    // en poco menos de medio segundo. Ida y vuelta triangular (fade out
    // rápido, fade in un toque más lento) en vez de un salto brusco, para
    // que no se vea como un parpadeo/glitch.
    private static final int HURT_FLASH_ALPHA = 90;      // ~35% de 255
    private static final int HURT_FLASH_OUT_TICKS = 3;   // 0.15s bajando
    private static final int HURT_FLASH_HOLD_TICKS = 2;  // 0.10s en el mínimo
    private static final int HURT_FLASH_IN_TICKS = 4;    // 0.20s volviendo a 255
    private static final int HURT_FLASH_TOTAL_TICKS =
            HURT_FLASH_OUT_TICKS + HURT_FLASH_HOLD_TICKS + HURT_FLASH_IN_TICKS;

    // --- "Esquive fantasma" de flechas ---
    // La flecha lo atraviesa de verdad (ver ProjectileImpactEvent en
    // ModEvents -- se cancela el impacto ANTES de que llegue a hurt(), la
    // flecha sigue volando con su trayectoria intacta) y, en el mismo
    // instante, un parpadeo rápido a CASI invisible -- la idea es vender
    // "no estaba ahí de verdad" en vez de un bloqueo plano. Más marcado y
    // mucho más corto que el flash de daño normal (que sigue existiendo
    // para golpes cuerpo a cuerpo que SÍ conectan).
    private static final int ARROW_PHASE_ALPHA = 35;     // casi invisible, no del todo (0 se confunde con Vanish)
    private static final int ARROW_PHASE_OUT_TICKS = 2;  // 0.10s bajando -- tiene que sentirse instantáneo
    private static final int ARROW_PHASE_HOLD_TICKS = 3; // 0.15s en el mínimo
    private static final int ARROW_PHASE_IN_TICKS = 3;   // 0.15s volviendo a 255
    private static final int ARROW_PHASE_TOTAL_TICKS =
            ARROW_PHASE_OUT_TICKS + ARROW_PHASE_HOLD_TICKS + ARROW_PHASE_IN_TICKS;

    // --- Fases / portales ---
    // Umbrales de vida en los que se abren portales nuevos, y cuántos.
    private static final double[] PHASE_HP_THRESHOLDS = {0.75D, 0.50D, 0.25D};
    private static final int[] PORTALS_PER_PHASE = {2, 3, 4};
    // Los portales ahora rodean al JEFE en círculo (no aparecen cerca de
    // cada jugador como antes) -- radio entre estos dos valores, elegido
    // al azar por portal para que el círculo no quede perfectamente
    // regular.
    private static final double PORTAL_SPAWN_MIN_RADIUS_FROM_KNIGHT = 15.0D;
    private static final double PORTAL_SPAWN_MAX_RADIUS_FROM_KNIGHT = 20.0D;
    // Cuánto puede desviarse cada portal de su ángulo "perfecto" en el
    // círculo (repartido 360°/cantidad), para cada lado -- así la
    // formación no se ve perfectamente geométrica/artificial.
    private static final double PORTAL_RING_ANGLE_JITTER_DEGREES = 12.0D;
    // Separación mínima entre CUALQUIER par de portales (los que se están
    // por abrir en esta misma tanda entre sí, Y contra los que ya estaban
    // abiertos de fases anteriores). Pedido: 5-10 bloques -- 7 queda en el
    // medio. Se chequea en línea recta (distancia 3D), así que ajustalo acá
    // nomás si lo querés más separado/apretado.
    private static final double PORTAL_MIN_SEPARATION = 7.0D;
    // Cuántas veces reintenta encontrar un punto válido (ángulo + altura)
    // antes de rendirse y abrir el portal ahí nomás aunque quede algo
    // pegado a otro. Con el radio actual (20-25 bloques alrededor del
    // jefe), la separación angular necesaria para cumplir
    // PORTAL_MIN_SEPARATION es todavía menor que con radios chicos (a
    // más radio, más distancia real por el mismo grado de separación),
    // así que 24 intentos sigue siendo de sobra incluso con 4 portales a
    // la vez.
    // Ya tiene modelo/renderer (TwilightPortalRenderer) -- ver conversación
    // sobre el modelo de 3 texturas animadas + 1 estática.
    private static final boolean PORTALS_ENABLED = true;

    // Efecto Effekseer (AAA Particles) que se dispara cuando el portal
    // TERMINA de aparecer (no cuando genera un mob) -- portado del
    // "Last of Deepslate" de Archaion (lod_archaic_summon.efkefc) y
    // recoloreado de azul a verde necrótico. Además del color de los
    // píxeles de las texturas (01/09/11.png), el propio .efkefc traía
    // varios colores RGBA azules HARDCODEADOS por dentro del binario
    // (parámetros de color de cada capa) -- esos también se parchearon
    // byte a byte a la misma familia de verde que ya usan el wave
    // (NecroticWaveRenderer.COLOR_R/G/B, #9CF6DE) y el rayo
    // (NecroticLightningRenderer.GLOW_R/G/B, #62F694). Ver
    // assets/nanookmod/effeks/.
    private static final ResourceLocation PORTAL_OPEN_EFFECT =
            new ResourceLocation(NanookMod.MOD_ID, "necrotic_portal_summon");

    // --- Escalado de agresividad por fase ---
    // Multiplica los cooldowns según cuántos umbrales de fase ya cruzó
    // (nextPhaseIndex): más avanzada la pelea, menos tiempo muerto entre
    // ataques. Índice 0 = fase inicial (sin cambios). Lo consulta
    // NecroticKnightAttackGoal al reasignar cada cooldown.
    private static final double[] PHASE_COOLDOWN_MULTIPLIER = {1.0D, 0.85D, 0.70D, 0.55D};
    // En la última fase (ya perdió el 75% de vida, cruzó los 3 umbrales)
    // los ataques de área pegan un poco más fuerte -- "modo desesperado".
    // ENRAGE (fase final, <= 25 % de vida): multiplicador de daño de TODOS los ataques.
    // ANTES valía 1.2 y solo lo aplicaban el rayo, la onda y el guard slap; el thrust y el
    // charge (los más usados) no lo tenían. Ahora vive en getTotalDamageMultiplier() y
    // afecta a todo por igual. Subilo/bajalo acá.
    private static final double FINAL_PHASE_DAMAGE_MULTIPLIER = 1.30D;

    // --- Robo de vida (ver applyLifesteal) ---
    // OJO: antes de esta revisión el Knight NO tenía robo de vida. "Drenaje Vital" solo da un
    // bonus de DAÑO por cargas; lo único que curaba era la regeneración por Corrupción III+ y
    // el anti-estancamiento de 5 min. Esto es NUEVO.
    /** Fracción del daño que le hace al jugador que se cura, una vez desbloqueado Drenaje Vital. */
    private static final double LIFESTEAL_BASE_FRACTION = 0.05D;
    /** Ídem en enrage (fase final). Poné LIFESTEAL_BASE_FRACTION en 0 si querés que exista SOLO en enrage. */
    private static final double LIFESTEAL_ENRAGED_FRACTION = 0.20D;
    /** Tope de curación por golpe, como fracción de la vida máxima: sin esto un rayo canalizado curaba de más. */
    private static final double LIFESTEAL_MAX_HEAL_FRACTION_OF_MAX_HP = 0.015D;

    // --- Robo de vida ESPECÍFICO del rayo (aparte del general de arriba) ---
    // Pedido explícito: "el rayo también roba vida dependiendo de la vida del jugador, y
    // dependiendo de la vida máxima del Knight por si un mod externo le sube la vida a los mobs".
    // Es DISTINTO del general (LIFESTEAL_BASE_FRACTION), que cura según el daño ya MITIGADO por
    // armadura. Este cura una fracción de la vida MÁXIMA del OBJETIVO, sin que la armadura la
    // reduzca -- así el rayo sigue drenando en serio contra un jugador bien blindado, y escala
    // solo si el jugador tiene más vida (otro mod le dio corazones extra), no si tiene más
    // armadura. Se suma al robo de vida general, no lo reemplaza -- las dos cosas ocurren.
    /** Fracción de la vida MÁXIMA del objetivo que cura el Knight por cada impacto del rayo (cada 0.5s mientras dura el canalizado). */
    private static final double LIGHTNING_LIFESTEAL_TARGET_FRACTION = 0.02D;
    /**
     * Tope por impacto, como fracción de la vida MÁXIMA DEL PROPIO KNIGHT (no un número fijo de
     * puntos de vida) -- si algún mod externo le sube la vida máxima al Knight (o a los mobs en
     * general), este robo de vida escala CON esa vida máxima nueva en vez de quedarse chico en
     * proporción a una barra ahora mucho más larga.
     */
    private static final double LIGHTNING_LIFESTEAL_KNIGHT_CAP_FRACTION = 0.02D;

    // --- Puntería de los golpes cuerpo a cuerpo ---
    // ANTES applyThrustDamage() solo miraba la DISTANCIA al objetivo: no importaba hacia dónde
    // mirara el Knight, y por eso recibías daño con la animación yendo hacia otro lado. Ahora hay
    // un cono horizontal contra el yaw VISUAL (yBodyRot), y durante la preparación gira hacia el
    // objetivo y después "se compromete" (queda fijo) antes de pegar.
    /** Apertura TOTAL del cono del thrust (grados). */
    private static final double THRUST_CONE_DEGREES = 100.0D;
    /** El thrust sigue al objetivo hasta este tick; el golpe de la animación llega en el 6-7. */
    private static final int THRUST_TRACK_UNTIL_TICK = 5;
    private static final float THRUST_TRACK_DEGREES_PER_TICK = 40.0F;
    /** El guard slap tiene el escudo arriba hasta el tick 31: sigue lento y se compromete en el 26. */
    private static final int GUARD_SLAP_TRACK_UNTIL_TICK = 26;
    private static final float GUARD_SLAP_TRACK_DEGREES_PER_TICK = 10.0F;
    /** Diferencia de altura máxima para que un golpe cuerpo a cuerpo cuente. */
    private static final double MELEE_HEIGHT_TOLERANCE = 3.5D;

    // --- Charge / slash_loop: frenado ---
    // La animación slash_loop (70t) queda casi quieta desde su tick 62 (energía de movimiento 74
    // contra 306 de media), o sea el tick 82 del ataque, pero el dash seguía a velocidad plena
    // hasta el 90: ~4.4 bloques "deslizándose". Ahora frena entre estos dos ticks.
    private static final int CHARGE_DECEL_START_TICK = 74;
    private static final int CHARGE_DECEL_END_TICK = 84;

    // --- Límite de distancia al punto de aparición ---
    /** No persigue a un objetivo que esté a más de esto de su punto de origen (bloques). */
    private static final double LEASH_CHASE_RADIUS = 40.0D;
    /** Si el jefe termina más lejos que esto (empujado, dash...), vuelve de golpe. */
    private static final double LEASH_HARD_RADIUS = 60.0D;
    /** Sin objetivo y a más de esto del origen, camina de vuelta. */
    private static final double LEASH_RETURN_TRIGGER = 12.0D;
    private static final int LEASH_REPATH_TICKS = 20;

    // --- Corrupción (Pacto Necromántico) ---
    // Máximo nivel alcanzable. A propósito NO sigue subiendo indefinido --
    // pedido explícito: que un jugador se distraiga 30s no puede convertir
    // al jefe en algo imposible de matar, tiene que tener un techo.
    private static final int MAX_CORRUPTION = 6;
    // Un portal/mob invocado recién aparecido no "presiona" todavía --
    // tiene que llevar al menos esto vivo (ver countPressuringSources).
    // Le da al jugador una ventana real para reaccionar antes de que
    // empiece a contar en contra.
    private static final int CORRUPTION_GRACE_TICKS = 20 * 5; // 5s
    // Cuántos ticks hacen falta para +1 de Corrupción según CUÁNTAS
    // fuentes (portales + mobs suyos, ya pasada la gracia) estén
    // presionando A LA VEZ ahora mismo -- índice 0 no se usa (0 fuentes =
    // no sube, empieza a bajar, ver tickCorruption). Con 5+ fuentes se usa
    // el último valor de la tabla (no sigue acelerando sin techo).
    private static final int[] CORRUPTION_RISE_TICKS = {
            0, 20 * 10, 20 * 7, 20 * 5, 20 * 4
    };
    // Cuando NINGUNA fuente presiona, baja 1 nivel cada esto -- ventana de
    // recuperación real si el jugador hizo bien el trabajo.
    private static final int CORRUPTION_DECAY_TICKS = 20 * 4; // 4s por nivel
    private static final int CORRUPTION_REGEN_INTERVAL_TICKS = 20 * 2; // 2s
    private static final double CORRUPTION_REGEN_FRACTION_OF_MAX_HP = 0.005D; // 0.5% de vida máx por intervalo

    private static final int CORRUPTION_SPEED_LEVEL = 2;  // nivel 2+: +5% velocidad
    private static final int CORRUPTION_REGEN_LEVEL = 3;  // nivel 3+: regen lenta

    // --- Rayo (nuevo, desbloqueo de Corrupción IV) ---
    // Igual mecánica que Drenaje Vital (ver más abajo): se desbloquea
    // PERMANENTEMENTE la primera vez que la CICATRIZ llega a este nivel,
    // así que aunque la Corrupción actual baje después de nuevo, el
    // ataque se queda disponible el resto de la pelea. Es un desbloqueo
    // por INSTANCIA del jefe (scarLevel vive en esta entidad, no es
    // global) -- un Knight distinto (otra pelea, /summon nuevo) arranca
    // de cero y tiene que volver a subir hasta IV para tenerlo.
    private static final int LIGHTNING_UNLOCK_SCAR_LEVEL = 4;

    // Duración de cada fase, calcada de la duración real de las
    // animaciones (sword_beam_windup = 2.2s, sword_beam_channel = 6.0s) --
    // si el día de mañana reexportás esas animaciones con otra duración,
    // actualizá estos dos números para que sigan calzando (ver
    // LIGHTNING_ANIM más abajo, que encadena las dos tal cual).
    public static final int LIGHTNING_WINDUP_TICKS = 44;   // 2.2s
    public static final int LIGHTNING_CHANNEL_TICKS = 120; // 6.0s

    // El rayo (entidad NecroticLightningBeamEntity) nace apenas termina
    // el windup, exactamente al arrancar sword_beam_channel -- pedido
    // explícito. Su propio crecimiento (0.5s hasta el alcance máximo) y
    // su fade in/out visual viven en esa clase y en el renderer, ya no
    // acá (antes esto se coordinaba a mano entre servidor y cliente con
    // fórmulas separadas -- ahora la entidad es la única fuente de verdad).
    // El rayo deja de estar activo (daño Y visual) 5.4s después de que
    // arranca sword_beam_channel -- pedido explícito ("desde el segundo 0
    // hasta el segundo 5.4"). La ANIMACIÓN sigue hasta los 6.0s completos
    // (LIGHTNING_CHANNEL_TICKS), solo el rayo en sí se corta 0.6s antes
    // del final, como si el jefe lo apagara justo antes de terminar la
    // pose.
    public static final int LIGHTNING_ACTIVE_CHANNEL_TICKS = 108; // 5.4s

    // El intervalo/radio de golpe reales viven ahora en
    // NecroticLightningBeamEntity (la entidad se ocupa sola del daño) --
    // acá solo queda el multiplicador base, que sí sigue haciendo falta
    // para getLightningDamage() (fase final, dificultad, etc.).
    private static final float LIGHTNING_DAMAGE_PER_TICK = 4.0F; // ajustable -- ver NecroticLightningBeamEntity para cuántas veces pega
    // Cuánto se extiende el rayo desde la espada -- lo usa
    // NecroticLightningBeamEntity para el alcance real del daño/visual.
    public static final double LIGHTNING_RANGE = 20.0D;

    // Nivel 5 (próxima habilidad) queda para la próxima tanda.

    private static final double CORRUPTION_DAMAGE_BONUS = 0.05D;  // nivel 1+: +5% daño (flat, no escala más con el nivel)
    private static final double CORRUPTION_SPEED_BONUS = 0.05D;   // nivel 2+: +5% velocidad (flat)
    // Cicatriz: cada vez que el jefe alcanza un pico NUEVO de Corrupción
    // (nunca antes visto en esta pelea), se queda con un +X% de armadura
    // PERMANENTE el resto del fight, aunque la Corrupción baje después a
    // 0 -- no es reversible, a diferencia del nivel de Corrupción en sí.
    private static final double SCAR_ARMOR_BONUS_PER_LEVEL = 0.05D;

    private static final UUID CORRUPTION_SPEED_MODIFIER_ID =
            UUID.fromString("7c9e1a2e-4b3d-4f5a-9c1e-2a3b4c5d6e7f");
    private static final UUID SCAR_ARMOR_MODIFIER_ID =
            UUID.fromString("8d0f2b3f-5c4e-4a6b-8d2f-3b4c5d6e7f80");

    // Público porque NecroticKnightHudOverlay (cliente) lo reutiliza para
    // el número romano al lado de la bossbar de Corrupción -- así no hay
    // dos tablas iguales que puedan desincronizarse si el día de mañana
    // cambia MAX_CORRUPTION.
    public static final String[] CORRUPTION_ROMAN = {"", "I", "II", "III", "IV", "V", "VI"};

    // --- Drenaje Vital (pasiva, se desbloquea PERMANENTEMENTE al llegar
    // por primera vez a Corrupción III -- reusamos la cicatriz para esto,
    // ver getScarLevel(), es exactamente "el pico más alto alcanzado,
    // nunca baja" que necesitábamos). CADA VEZ que el jefe le conecta un
    // golpe al jugador sin que el jugador le haya devuelto ninguno, suma
    // 1 carga (no es por tiempo/quietud -- es por combo real). Un solo
    // golpe recibido las borra todas de un saque (ver
    // onSuccessfulHitReceived, colgado de hurt()). Cuánto da cada carga
    // escala con la Corrupción ACTUAL (no la cicatriz) -- básico en 0-II,
    // más fuerte en III-IV, máximo en V-VI.
    private static final int DRENAJE_UNLOCK_SCAR_LEVEL = 3;
    private static final int DRENAJE_MAX_CHARGES = 5;
    // % de daño por carga según en qué "tramo" de Corrupción actual esté
    // el jefe -- ver getDrenajeTier().
    private static final double[] DRENAJE_TIER_BONUS_PER_CHARGE = {0.05D, 0.06D, 0.08D};

    // Si el jefe pasa 5 minutos SIN recibir ningún daño (jugador se fue,
    // se rindió, está troleando desde lejos, etc), empieza a curarse solo
    // -- anti-estancamiento, totalmente aparte de las cargas de arriba, y
    // siempre activo (no depende de haber desbloqueado nada).
    private static final int STALL_HEAL_DELAY_TICKS = 20 * 60 * 5; // 5 min
    private static final int STALL_HEAL_INTERVAL_TICKS = 20 * 2; // cada 2s, una vez arrancó
    private static final double STALL_HEAL_FRACTION_OF_MAX_HP = 0.01D; // 1% de vida máx por tick de curación

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation THRUST_ANIM = RawAnimation.begin().thenPlay("thrust").thenLoop("idle");
    private static final RawAnimation CHARGE_ANIM =
            RawAnimation.begin().thenPlay("charge").thenPlay("slash_loop").thenLoop("idle");
    // Sin animaciones propias todavía para invocar fantasmitas / onda -- de
    // momento reutiliza "idle" quieto mientras dura el ataque (no se ve
    // mal, pero cuando tengas animaciones de jefe dedicadas es cambiar
    // estas dos líneas nomás).
    private static final RawAnimation GHOST_SUMMON_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WAVE_ANIM = RawAnimation.begin().thenPlay("wave_cast");
    private static final RawAnimation GUARD_SLAP_ANIM = RawAnimation.begin().thenPlay("ATTACK_GUARD_SLAP").thenLoop("idle");
    // Windup (carga) + channel (el rayo en sí) encadenados en un solo
    // clip -- igual truco que CHARGE_ANIM. Las duraciones de
    // LIGHTNING_WINDUP_TICKS/LIGHTNING_CHANNEL_TICKS están calcadas de
    // estos dos clips (ver esas constantes), así que terminan JUSTO
    // cuando termina el ataque -- no hace falta un ".thenLoop(idle)" al
    // final, mismo motivo por el que WAVE_ANIM tampoco lo tiene.
    private static final RawAnimation LIGHTNING_ANIM =
            RawAnimation.begin().thenPlay("sword_beam_windup").thenPlay("sword_beam_channel");
    // Sin animación propia todavía (igual que Ghost Summon/Wave) -- el
    // fundido de opacidad ya hace la mayor parte del trabajo visual, así
    // que quedarse en idle mientras dura no se ve mal. Cuando tengas una
    // animación dedicada (ej. un gesto de "disolverse"), es cambiar esta
    // línea nomás.
    private static final RawAnimation VANISH_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("despawn");
    // PEDIDO: "que se use la animación de spawn que ya tiene en su set de animaciones" -- estaba
    // en el .animation.json (42t, hold_on_last_frame) pero ningún controlador la reproducía
    // nunca. Ver SPAWN_ANIM_DURATION_TICKS / isSpawningIn() / el controlador "spawn_anim" más abajo.
    private static final RawAnimation SPAWN_ANIM = RawAnimation.begin().thenPlay("spawn");
    /** Duración real del clip "spawn" (42t) -- si algún día cambiás la animación, actualizá esto junto. */
    private static final int SPAWN_ANIM_DURATION_TICKS = 42;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int attackTicksElapsed = 0;
    private int currentAttackDuration = 0;
    private boolean thrustEffectsApplied = false;
    // Si ESTE Thrust en particular llegó a conectar de verdad (ver
    // applyThrustDamage) -- se usa para el combo de Enrage, no tiene
    // sentido "premiar" con un segundo golpe uno que erró del todo.
    private boolean thrustLandedThisAttack = false;
    // --- Portales de invocación de wisps (ver spawnWispPortals) ---
    private int wispPortalIdA = -1;
    private int wispPortalIdB = -1;
    private boolean wispsFiredThisAttack = false;
    private boolean wispPortalsClosedThisAttack = false;
    private final Set<Integer> chargePulsesFiredThisAttack = new HashSet<>();
    // --- Enrage (Final Phase, <=25% de vida) ---
    // Después de un Thrust que CONECTÓ (ver applyThrustDamage -- no
    // importa si erró, no tiene sentido "premiar" con un combo un golpe
    // que no pegó), chance de encadenar OTRO Thrust inmediato, sin la
    // pausa/cooldown normal -- el "ya no tengo nada que perder" del jefe.
    // Tope de un solo encadenado (ver comboChainActive) para que no se
    // vuelva una cadena infinita de rolls afortunados.
    private static final double ENRAGE_COMBO_CHANCE = 0.4D;
    // Empujón de velocidad de movimiento al entrar a Final Phase -- se
    // aplica UNA vez (ver tickPhaseCheck) y se queda, igual de
    // permanente que la fase misma. Mismo patrón que
    // CORRUPTION_SPEED_BONUS/MODIFIER_ID, ver ahí para el porqué de
    // MULTIPLY_TOTAL en vez de un flat add.
    private static final double ENRAGE_SPEED_BONUS = 0.15D; // +15%
    private static final UUID ENRAGE_SPEED_MODIFIER_ID =
            UUID.fromString("7c9e6a1e-3b3d-4b9a-8e2a-9f1c2d3b4a5c");

    // --- Rotura de escudo ---
    // Mismo valor que usa el hacha vanilla contra un jugador bloqueando
    // (Player#disableShield) -- 5s. No reinventamos la rueda: es el
    // tiempo que los jugadores YA conocen de pelear contra un Piglin
    // Brute o un Ravager, así que la lectura ("me rompieron el escudo")
    // es inmediata sin tener que explicarles un número nuevo.
    private static final int SHIELD_DISABLE_TICKS = 100; // 5s -- valor genérico (lo usa el Rayo, que no pediste cambiar)
    // Guard Slap -- pedido explícito: 8s, aparte del genérico de arriba.
    private static final int GUARD_SLAP_SHIELD_DISABLE_TICKS = 160; // 8s
    // Golpe 1 del slash_loop -- pedido explícito de poder ajustarlo por
    // separado del resto. Como es literalmente el PRIMER contacto de toda
    // la ráfaga, tiene sentido que castigue más fuerte que un golpe
    // cualquiera (Guard Slap, el golpe 10, el rayo): "te agarré
    // bloqueando justo al arrancar, ahora aguantate más tiempo sin
    // escudo". Subilo/bajalo acá nomás, no hace falta tocar nada más.
    private static final int SLASH_LOOP_FIRST_HIT_SHIELD_DISABLE_TICKS = 160; // 8s
    // Golpe 10 (el último, el que además manda a volar) -- el más largo
    // de todos: es el cierre de la ráfaga completa, el "broche" del
    // combo.
    private static final int SLASH_LOOP_LAST_HIT_SHIELD_DISABLE_TICKS = 200; // 10s (11s = 220 si lo querés un toque más)

    private final Map<Integer, Set<UUID>> waveHitPlayersPerPulse = new HashMap<>();
    private int lastAttackStateAnimated = ATTACK_NONE;

    // --- Vanish ---
    private boolean vanishTeleportDone = false;
    // Se decide UNA vez, apenas arranca el Vanish (ver triggerVanish), no
    // al final -- así es un dato fijo de esta ejecución del ataque y no
    // depende de en qué momento se evalúe.
    private boolean vanishSurpriseAttackPending = false;

    // --- Enrage (Final Phase) ---
    // true SOLO durante el Thrust encadenado en sí (el "segundo golpe") --
    // así, al terminar ESE, no vuelve a rollear otro encadenado encima
    // (tope de un combo de 2, no una cadena infinita). Un Thrust normal
    // (disparado por el AttackGoal) siempre arranca con esto en false.
    private boolean comboChainActive = false;
    private boolean enrageTelegraphed = false;

    // --- Flash de transparencia al recibir daño ---
    // Cuenta ticks DESDE que arrancó el flash actual (0 = recién
    // empezado), no una cuenta regresiva -- más fácil de mapear a las
    // tres fases (out/hold/in) en tickHurtFlash(). -1 = sin flash activo.
    private int hurtFlashTicks = -1;
    // Mismo patrón que hurtFlashTicks, contador aparte -- ver
    // tickArrowPhase() para el porqué de no compartir uno solo.
    private int arrowPhaseTicks = -1;

    // --- Detección de movimiento manual (ver charla: NO usar state.isMoving()
    // de GeckoLib acá, se desincroniza del movimiento real en mobs grandes /
    // con pathing custom y causa el bug de "se desliza sin animar"). En vez
    // de eso, comparamos la posición real tick a tick.
    private double lastTickX = Double.NaN;
    private double lastTickZ = Double.NaN;
    private boolean reallyMoving = false;
    private static final double MOVING_THRESHOLD_SQR = 4.0E-4D; // ~0.02 bloques/tick

    private double chargeDirX = 0.0D;
    private double chargeDirZ = 0.0D;
    private boolean chargeHomingThisAttack = false;
    private final ArrayDeque<Integer> recentHitTicks = new ArrayDeque<>();

    // --- Secuencia de aparición ---
    private boolean spawnSequencePlayed = false;

    // --- Fases ---
    private int nextPhaseIndex = 0; // 0 = espera cruzar 75%, 1 = espera 50%, etc.
    private final List<UUID> activePortals = new ArrayList<>();

    // --- Corrupción ---
    private int corruptionProgressTicks = 0; // contador interno hacia el próximo +1 o -1
    private int corruptionRegenTicks = 0;

    // --- Drenaje Vital ---
    private int ticksSinceKnightLastDamaged = 0;
    private int stallHealTicks = 0;

    // --- Espejos (NecroticKnightFakeEntity) ---
    // true SOLO en las instancias "espejo": evita que corran spawn
    // sequence / ventana de ataque / chequeo de fases del jefe real (esos
    // métodos son privados, así que en vez de poder overridearlos en la
    // subclase, los guardeamos acá con este flag - ver tick() más abajo y
    // NecroticKnightFakeEntity).
    protected boolean isMirrorClone = false;

    // Punto de origen del jefe (dónde apareció), para el límite de distancia. Se persiste en NBT.
    // Cuando exista el sistema de ofrendas, quien spawnee al Knight debería llamar setHomePos().
    @Nullable
    private BlockPos homePos = null;
    private int leashRepathCooldown = 0;

    // --- Bossbar ---
    // NOTCHED_10 porque el jefe tiene fases marcadas en 75/50/25% (ver
    // tickPhaseCheck) -- las muescas dan una referencia visual de esos
    // cortes, no hace falta que coincidan exactamente para que se vea
    // bien. El objeto se crea siempre (más simple que complicar el
    // constructor), pero en las instancias "espejo" nunca se le agrega
    // ningún jugador (ver startSeenByPlayer/tick más abajo) -- aunque
    // ahora mismo ese efecto está desconectado igual: spawnMirrorClone()
    // sigue entero más abajo pero no se llama desde ningún lado.
    //
    // NOTA (HUD custom): seguimos usando ServerBossEvent para el tracking
    // de jugadores y la interpolación de progreso -- es codigo vanilla ya
    // probado y no hay razón para reinventarlo. Lo que cambiamos es que
    // ahora está SIEMPRE en visible=false: eso hace que el servidor nunca
    // mande el paquete que dibuja la bossbar vanilla (ver
    // ServerBossEvent#setVisible), así que no aparece en el HUD por
    // default. El renderer custom (NecroticKnightHudOverlay, cliente) es
    // el que dibuja la barra de vida + Corrupción + Drenaje Vital ahora,
    // leyendo directo de la entidad (getHealth/getCorruptionLevel/
    // getDrenajeCharges, todo ya sincronizado como SynchedEntityData).
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

    public NecroticKnightEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.bossEvent.setVisible(false);
        // Evita que el frustum culling corte el render cuando el jugador
        // gira la cámara: el hitbox es más chico que el modelo/animaciones
        // (espada, brazos extendidos en los ataques), así que sin esto
        // Minecraft deja de renderizar (y por lo tanto de avanzar la
        // animación de GeckoLib) apenas el hitbox sale de cámara, aunque
        // el modelo visual siga "dentro". Es un jefe: el costo de
        // renderizar siempre es insignificante.
        this.noCulling = true;
        // EL BUG REAL DEL RAYO (que no era de sincronización, era de
        // AI): NecroticKnightAttackGoal reserva Flag.LOOK así que
        // LookAtPlayerGoal no puede pisarnos, PERO eso solo bloquea a
        // OTROS Goals -- no evita que el propio LookControl del mob
        // (this.lookControl) siga corriendo su tick() de todos modos, y
        // el LookControl vanilla, cuando no tiene un "wanted target"
        // activo (hasWanted == false, que es siempre durante el Rayo
        // porque a propósito NO usamos setLookAt acá, ver el comentario
        // en NecroticKnightAttackGoal#tick), hace
        // resetXRotOnTick() == true por defecto y llama
        // this.mob.setXRot(0.0F) TODOS los ticks. Eso corre en
        // Mob#customServerAiStep() justo DESPUÉS del goalSelector, o
        // sea después de que updateLightningAim() ya puso el pitch
        // real -- lo pisa a 0 en el mismo tick, siempre. El bone del
        // brazo (getSyncedLightningPitch(), SynchedEntityData aparte)
        // nunca se entera porque no depende de getXRot(), pero
        // NecroticLightningBeamEntity#followOwner sí lee getXRot()
        // directo -- por eso el rayo quedaba siempre "plano" en juego
        // real sin importar la distancia real al jugador. Fix: mismo
        // patrón que usa vanilla en Fox/Bee/Frog/Camel -- reemplazar el
        // LookControl por uno que no resetee el pitch mientras estamos
        // en ATTACK_LIGHTNING.
        this.lookControl = new LightningAwareLookControl(this);
    }

    /**
     * LookControl vanilla, salvo que no pisa el pitch (xRot) a 0.0F cada
     * tick mientras el jefe está canalizando el Rayo -- ver el comentario
     * largo en el constructor de arriba para el porqué. Fuera del Rayo se
     * comporta exactamente igual que el LookControl normal (THRUST, GUARD
     * SLAP, etc. usan setLookAt() y no se ven afectados).
     */
    private static class LightningAwareLookControl extends LookControl {
        private final NecroticKnightEntity boss;

        LightningAwareLookControl(NecroticKnightEntity boss) {
            super(boss);
            this.boss = boss;
        }

        @Override
        protected boolean resetXRotOnTick() {
            return boss.getAttackState() != NecroticKnightEntity.ATTACK_LIGHTNING;
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.ATTACK_DAMAGE, THRUST_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new NecroticKnightAttackGoal(this));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        // El predicado del final descarta jugadores fuera del área de persecución (ver isInsideChaseArea).
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isInsideChaseArea));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ATTACK_STATE, ATTACK_NONE);
        this.entityData.define(DATA_CORRUPTION_LEVEL, 0);
        this.entityData.define(DATA_SCAR_LEVEL, 0);
        this.entityData.define(DATA_DRENAJE_CHARGES, 0);
        this.entityData.define(DATA_LIGHTNING_TARGET_ID, -1);
        this.entityData.define(DATA_LIGHTNING_AIM_YAW, 0.0F);
        this.entityData.define(DATA_LIGHTNING_AIM_PITCH, 0.0F);
        this.entityData.define(DATA_LIGHTNING_CHANNELING, false);
        this.entityData.define(DATA_RENDER_ALPHA, 255);
        // Arranca en SPAWN_ANIM_DURATION_TICKS: por defecto TODO Knight nuevo (altar, /summon,
        // huevo de invocación) reproduce su animación de aparición al nacer. readAdditionalSaveData
        // la apaga (pone 0) cuando en realidad se está CARGANDO uno que ya existía de antes -- si no,
        // cada recarga de chunk la haría repetirse.
        this.entityData.define(DATA_SPAWN_ANIM_TICKS_REMAINING, SPAWN_ANIM_DURATION_TICKS);
    }

    public int getRenderAlpha() {
        return this.entityData.get(DATA_RENDER_ALPHA);
    }

    private void setRenderAlpha(int alpha) {
        int clamped = Mth.clamp(alpha, 0, 255);
        if (this.entityData.get(DATA_RENDER_ALPHA) != clamped) {
            this.entityData.set(DATA_RENDER_ALPHA, clamped);
        }
    }

    public int getAttackState() {
        return this.entityData.get(DATA_ATTACK_STATE);
    }

    public boolean isAttacking() {
        return getAttackState() != ATTACK_NONE;
    }

    public void triggerThrust() {
        if (!isAttacking()) {
            startAttack(ATTACK_THRUST, THRUST_DURATION_TICKS);
            comboChainActive = false; // Thrust disparado normalmente (desde el AttackGoal) -- no es un encadenado
        }
    }

    /**
     * El "segundo golpe" del combo de Enrage (ver ENRAGE_COMBO_CHANCE) --
     * a diferencia de triggerThrust(), no chequea isAttacking() porque
     * SOLO se llama desde el final de tickAttackWindow, justo después de
     * resetear el estado a ATTACK_NONE (ya es false, sería un chequeo
     * redundante).
     */
    private void triggerComboThrust() {
        startAttack(ATTACK_THRUST, THRUST_DURATION_TICKS);
        comboChainActive = true;
    }

    public void triggerCharge() {
        if (!isAttacking()) {
            startAttack(ATTACK_CHARGE, CHARGE_DURATION_TICKS);
            // A partir de fase 1 (ya cruzó el primer umbral de vida) la
            // embestida empieza a corregir rumbo. En fase 0 queda fija,
            // como la tenías.
            chargeHomingThisAttack = nextPhaseIndex >= 1;
            LivingEntity target = this.getTarget();
            if (target != null) {
                Vec3 dir = target.position().subtract(this.position()).normalize();
                chargeDirX = dir.x;
                chargeDirZ = dir.z;
                applyChargeVisualYaw();
            }
        }
    }

    public void triggerGuardSlap() {
        if (!isAttacking()) startAttack(ATTACK_GUARD_SLAP, GUARD_SLAP_DURATION_TICKS);
    }

    public void triggerGhostSummon() {
        if (!isAttacking()) startAttack(ATTACK_GHOST_SUMMON, GHOST_SUMMON_DURATION_TICKS);
    }

    public void triggerWave() {
        if (!isAttacking()) {
            startAttack(ATTACK_WAVE, WAVE_DURATION_TICKS);
            waveHitPlayersPerPulse.clear();
        }
    }

    /** No hace nada si todavía no llegó a Corrupción IV en ESTA pelea -- ver isLightningUnlocked(). */
    public void triggerLightning() {
        if (!isAttacking() && isLightningUnlocked()) {
            LivingEntity target = this.getTarget();
            this.entityData.set(DATA_LIGHTNING_TARGET_ID, target != null ? target.getId() : -1);
            // Arranca apuntando a donde el jefe YA está mirando (no de
            // golpe al jugador) -- el giro hacia el target pasa después,
            // gradual, en updateLightningAim.
            this.entityData.set(DATA_LIGHTNING_AIM_YAW, this.getYRot());
            this.entityData.set(DATA_LIGHTNING_AIM_PITCH, this.getXRot());
            startAttack(ATTACK_LIGHTNING, LIGHTNING_WINDUP_TICKS + LIGHTNING_CHANNEL_TICKS);

            // Telegraph de carga -- arranca YA, junto con sword_beam_windup,
            // y los 2 archivos (lightning_windup_1/2, con pitch stretcheado
            // a mano para calzar los 2.2s de esa animación) duran
            // exactamente lo mismo que el windup, así que termina justo
            // cuando arranca sword_beam_channel sin necesidad de cortarlo.
            if (this.level() instanceof ServerLevel serverLevel) {
                float windupPitch = 0.97F + this.random.nextFloat() * 0.06F;
                serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                        ModSounds.NECROKNIGHT_LIGHTNING_WINDUP.get(), SoundSource.HOSTILE, 1.0F, windupPitch);
            }
        }
    }

    /** Cicatriz >= IV -- una vez true, se queda true el resto de la pelea aunque la Corrupción actual baje. */
    public boolean isLightningUnlocked() {
        return getScarLevel() >= LIGHTNING_UNLOCK_SCAR_LEVEL;
    }

    public void triggerVanish() {
        if (!isAttacking()) {
            startAttack(ATTACK_VANISH, VANISH_DURATION_TICKS);
            vanishTeleportDone = false;
            vanishSurpriseAttackPending = this.random.nextDouble() < VANISH_SURPRISE_ATTACK_CHANCE;
        }
    }

    private void startAttack(int attackState, int durationTicks) {
        this.entityData.set(DATA_ATTACK_STATE, attackState);
        this.attackTicksElapsed = 0;
        this.currentAttackDuration = durationTicks;
        this.thrustEffectsApplied = false;
        this.thrustLandedThisAttack = false;
        this.wispPortalIdA = -1;
        this.wispPortalIdB = -1;
        this.wispsFiredThisAttack = false;
        this.wispPortalsClosedThisAttack = false;
        this.chargePulsesFiredThisAttack.clear();
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!isMirrorClone) {
            bossEvent.addPlayer(player);
            ModNetworking.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SyncNecroticKnightHudPacket(this.getId(), true));
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (!isMirrorClone) {
            bossEvent.removePlayer(player);
            ModNetworking.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SyncNecroticKnightHudPacket(this.getId(), false));
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateReallyMoving();
        if (!this.level().isClientSide && !isMirrorClone) {
            tickSpawnAnim();
            tickSpawnSequence();
            tickAttackWindow();
            tickPhaseCheck();
            tickCorruption();
            tickDrenajeVital();
            tickArrowPhase();
            tickHurtFlash();
            tickHomeLeash(); // no llamarlo tickLeash(): PathfinderMob ya tiene un tickLeash() protected (el del lead de vanilla) con otra firma -- override involuntario, no compilaba.
            bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        }
    }

    // Compara posición real tick a tick en vez de usar el isMoving() de
    // GeckoLib (ver comentario en la declaración de los campos). Corre en
    // cliente Y servidor: la predicate de animación se evalúa del lado
    // cliente, así que necesita su propia lectura de "me estoy moviendo de
    // verdad" ahí también, no solo en el servidor.
    private void updateReallyMoving() {
        if (!Double.isNaN(lastTickX)) {
            double dx = this.getX() - lastTickX;
            double dz = this.getZ() - lastTickZ;
            reallyMoving = (dx * dx + dz * dz) > MOVING_THRESHOLD_SQR;
        }
        lastTickX = this.getX();
        lastTickZ = this.getZ();
    }

    // ---------------------------------------------------------------
    // Secuencia de aparición: pulso + knockback + primera oleada
    // ---------------------------------------------------------------

    /**
     * Cuenta regresiva de la animación de aparición (42t / 2.1s). Server-authoritative: el
     * cliente solo LEE el dato sincronizado (DATA_SPAWN_ANIM_TICKS_REMAINING) para decidir la
     * animación -- ver spawnAnimPredicate. Mientras dura, el Knight queda plantado (sin
     * navegación ni deslizamiento horizontal): "materializándose" no debería resbalar por el
     * suelo.
     */
    private void tickSpawnAnim() {
        int remaining = this.entityData.get(DATA_SPAWN_ANIM_TICKS_REMAINING);
        if (remaining <= 0) {
            return;
        }
        this.entityData.set(DATA_SPAWN_ANIM_TICKS_REMAINING, remaining - 1);
        this.getNavigation().stop();
        this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
    }

    /**
     * True mientras dura la animación de aparición -- bloquea ataques y movimiento propio.
     *
     * isMirrorClone SIEMPRE cuenta como "ya terminó de aparecer": tick() se salta por completo
     * tickSpawnAnim() para los clones espejo (misma guarda que el resto de los tickers propios,
     * ver tick()), así que su DATA_SPAWN_ANIM_TICKS_REMAINING nunca bajaría de
     * SPAWN_ANIM_DURATION_TICKS por su cuenta -- sin este chequeo, un clon quedaría "materializándose"
     * para siempre y jamás mostraría la animación que en verdad le toca copiar del Knight real.
     */
    public boolean isSpawningIn() {
        return !isMirrorClone && this.entityData.get(DATA_SPAWN_ANIM_TICKS_REMAINING) > 0;
    }

    private void tickSpawnSequence() {
        if (spawnSequencePlayed) {
            return;
        }
        spawnSequencePlayed = true;

        if (this.level() instanceof ServerLevel serverLevel) {
            // Red de seguridad para spawns SIN altar (/summon, otro mod,
            // debug). Cuando el Knight nace desde el Altar Necromante, la
            // transición día->noche ya arrancó minutos-segundos antes, al
            // EMPEZAR el ritual (ver NecroticAltarBlockEntity#startRitual),
            // así que acá TwilightNightHandler.setActive ya encuentra
            // serverTwilightActive=true y no hace nada (early-return). Si
            // en cambio no hay ningún altar de por medio, esto la prende
            // de forma instantánea, como se comportaba antes.
            TwilightNightHandler.setActive(serverLevel, true);

            // Arranca el tema de batalla (con fade in). Manda a TODOS los
            // conectados (igual que TwilightNightHandler arriba), no solo a
            // los que ya están "trackeando" al knight -- si mandáramos con
            // TRACKING_ENTITY justo el mismo tick en el que aparece, el
            // servidor puede no haber terminado de armar el tracking
            // todavía y el paquete se perdería en el camino.
            NanookMod.LOGGER.info("Nanook Mod: mandando SyncNecroticKnightMusicPacket(active=true) para entity id {}", this.getId());
            ModNetworking.CHANNEL.send(
                    PacketDistributor.ALL.noArg(),
                    new SyncNecroticKnightMusicPacket(this.getId(), true)
            );

            // Pulso visual (vfx_pulse.bbmodel -- todavía sin exportar, ver
            // TODO más abajo).
            NecroticKnightPulseVfx pulse = new NecroticKnightPulseVfx(serverLevel, this.position());
            serverLevel.addFreshEntity(pulse);

            // Explosión de knockback: todos los jugadores cerca salen
            // volando desde el centro del jefe.
            double knockbackRadius = 6.0D;
            for (ServerPlayer player : serverLevel.getPlayers(p ->
                    p.distanceToSqr(this) <= knockbackRadius * knockbackRadius)) {
                Vec3 away = player.position().subtract(this.position());
                if (away.lengthSqr() < 1.0E-4) {
                    away = new Vec3(0, 0, 1);
                }
                away = away.normalize();
                player.setDeltaMovement(away.x * 1.6D, 0.8D, away.z * 1.6D);
                player.hurtMarked = true; // fuerza el reenvío de velocidad al cliente
            }

            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.NECROKNIGHT_SPAWN.get(), SoundSource.HOSTILE, 2.0F, 1.0F);

            // Oleada inicial de fantasmitas.
            spawnIntroWispBurst(GHOST_COUNT_PER_SUMMON);
        }
    }

    // ---------------------------------------------------------------
    // Ventana de ataque
    // ---------------------------------------------------------------

    private void tickAttackWindow() {
        int state = getAttackState();
        if (state == ATTACK_NONE) {
            return;
        }

        attackTicksElapsed++;
        tickAimTracking(state);

        switch (state) {
            case ATTACK_THRUST -> {
                if (!thrustEffectsApplied && attackTicksElapsed >= THRUST_DAMAGE_TICK) {
                    applyThrustDamage();
                    thrustEffectsApplied = true;
                }
            }
            case ATTACK_CHARGE -> {
                tickChargeMovement();
                tickChargeDamagePulses();
            }
            case ATTACK_GHOST_SUMMON -> tickWispPortalSummon();
            case ATTACK_WAVE -> tickWave();
            case ATTACK_GUARD_SLAP -> tickGuardSlap();
            case ATTACK_LIGHTNING -> tickLightning();
            case ATTACK_VANISH -> tickVanish();
            default -> {
            }
        }

        if (attackTicksElapsed >= currentAttackDuration) {
            boolean wasVanish = state == ATTACK_VANISH;
            boolean wasThrust = state == ATTACK_THRUST;
            this.entityData.set(DATA_ATTACK_STATE, ATTACK_NONE);
            if (this.entityData.get(DATA_LIGHTNING_TARGET_ID) != -1) {
                this.entityData.set(DATA_LIGHTNING_TARGET_ID, -1);
            }
            if (this.entityData.get(DATA_LIGHTNING_CHANNELING)) {
                this.entityData.set(DATA_LIGHTNING_CHANNELING, false);
            }
            // Red de seguridad: si por lo que sea (Vanish interrumpido,
            // findSafeTeleportPos falló, etc.) el alpha no llegó de vuelta
            // a 255 solo, lo forzamos acá para no dejarlo semi-invisible
            // para siempre.
            if (wasVanish) {
                setRenderAlpha(255);
                // Golpe sorpresa: se decidió al arrancar el Vanish (ver
                // triggerVanish), NO acá -- así no importa si el target
                // cambió o se murió mientras tanto, ya está resuelto.
                // triggerThrust() ya valida por su cuenta que haya alcance
                // real (THRUST_RANGE) antes de pegar -- si el jugador se
                // movió lejos justo cuando reapareció, simplemente no
                // conecta, no rompe nada.
                if (vanishSurpriseAttackPending) {
                    vanishSurpriseAttackPending = false;
                    triggerThrust();
                }
            }
            // Enrage (Final Phase, <=25% de vida): un Thrust que CONECTÓ
            // (thrustLandedThisAttack) tiene chance de encadenar otro de
            // inmediato -- el "doble estocada" desesperado. comboChainActive
            // asegura que sea como mucho UN encadenado (si el que está
            // terminando ahora YA era el encadenado, no vuelve a rollear).
            if (wasThrust) {
                boolean thisWasComboChain = comboChainActive;
                comboChainActive = false;
                if (!thisWasComboChain && thrustLandedThisAttack
                        && this.random.nextDouble() < getThrustComboChance()) {
                    triggerComboThrust();
                }
            }
        }
    }

    /**
     * Rompe el escudo de un jugador que estaba bloqueando -- mismo
     * mecanismo que usa el hacha vanilla contra un shield (cooldown del
     * ítem + soltar el bloqueo + el sonido/animación vanilla de "escudo
     * roto", vía el entity event 30). No depende de un método interno de
     * Player que pueda cambiar de nombre entre mappings -- son 3 llamadas
     * a API pública y estable.
     *
     * Se llama SIEMPRE que el objetivo estaba bloqueando en el momento
     * del golpe, sin importar si target.hurt() devolvió true o false
     * (un bloqueo total -- que sigue contando como "te rompí el
     * escudo" -- puede hacer que hurt() devuelva false).
     *
     * disableTicks es configurable por golpe -- cada lugar que llama a
     * esto decide cuánto castiga (ver SHIELD_DISABLE_TICKS de base y
     * SLASH_LOOP_FIRST_HIT_SHIELD_DISABLE_TICKS para el golpe 1 del
     * slash_loop, que castiga más fuerte).
     */
    void disablePlayerShield(LivingEntity target, int disableTicks) {
        if (!(target instanceof Player player) || !player.isBlocking()) {
            return;
        }
        player.getCooldowns().addCooldown(player.getUseItem().getItem(), disableTicks);
        player.stopUsingItem();
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.broadcastEntityEvent(player, (byte) 30);
        }
    }

    /** Sobrecarga de conveniencia: duración por defecto (ver SHIELD_DISABLE_TICKS). */
    void disablePlayerShield(LivingEntity target) {
        disablePlayerShield(target, SHIELD_DISABLE_TICKS);
    }

    private void applyThrustDamage() {
        LivingEntity target = this.getTarget();
        boolean connected = false;
        // Tiene que estar al alcance Y dentro del cono hacia donde MIRA el modelo.
        if (target != null && target.isAlive() && isInMeleeArc(target, THRUST_RANGE, THRUST_CONE_DEGREES)) {
            if (target.hurt(this.damageSources().mobAttack(this), THRUST_DAMAGE * (float) getTotalDamageMultiplier())) {
                onKnightLandedHit();
                thrustLandedThisAttack = true; // ver getThrustComboChance -- solo se premia con combo un golpe que SÍ conectó
            }
            connected = true;
        }
        // Conectó: el golpe seco de siempre. Erró: solo el tajo al aire, así el sonido no miente.
        this.level().playSound(null, this.blockPosition(),
                connected ? SoundEvents.TRIDENT_HIT : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE, 0.8F, connected ? 0.8F : 0.7F);
    }

    private void tickChargeMovement() {
        if (attackTicksElapsed < CHARGE_DASH_START_TICK || attackTicksElapsed > CHARGE_DASH_END_TICK) {
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
            return;
        }

        if (chargeHomingThisAttack) {
            int ticksIntoDash = attackTicksElapsed - CHARGE_DASH_START_TICK;
            if (ticksIntoDash % CHARGE_REAIM_INTERVAL_TICKS == 0) {
                LivingEntity target = this.getTarget();
                if (target != null) {
                    Vec3 desired = target.position().subtract(this.position()).normalize();
                    steerChargeDirTowards(desired);
                }
            }
        }

        // Frena entre CHARGE_DECEL_START_TICK y CHARGE_DECEL_END_TICK: el slash_loop ya está casi
        // quieto ahí y seguir a velocidad plena se veía como deslizarse.
        double speedFactor = 1.0D;
        if (attackTicksElapsed > CHARGE_DECEL_START_TICK) {
            speedFactor = Math.max(0.0D, 1.0D - (attackTicksElapsed - CHARGE_DECEL_START_TICK)
                    / (double) (CHARGE_DECEL_END_TICK - CHARGE_DECEL_START_TICK));
        }
        this.setDeltaMovement(chargeDirX * CHARGE_SPEED * speedFactor, this.getDeltaMovement().y,
                chargeDirZ * CHARGE_SPEED * speedFactor);
        tickChargeAutoStep();
    }

    // Si el último movimiento chocó contra algo (horizontalCollision) y hay
    // un bloque libre justo arriba de lo que lo bloquea, le damos un
    // impulso vertical para saltarlo -- así no se traba en un desnivel de
    // 1 bloque en plena embestida.
    private void tickChargeAutoStep() {
        if (!this.horizontalCollision) {
            return;
        }
        BlockPos aheadPos = this.blockPosition().relative(Direction.fromYRot(this.getYRot()));
        boolean blockedAtFeet = !this.level().getBlockState(aheadPos)
                .getCollisionShape(this.level(), aheadPos).isEmpty();
        boolean openAboveThatBlock = this.level().getBlockState(aheadPos.above())
                .getCollisionShape(this.level(), aheadPos.above()).isEmpty();
        if (blockedAtFeet && openAboveThatBlock) {
            this.setDeltaMovement(this.getDeltaMovement().x, CHARGE_STEP_ASSIST_VELOCITY, this.getDeltaMovement().z);
        }
    }

    // Gira chargeDir hacia 'desired', recortando el giro a
    // CHARGE_MAX_TURN_DEGREES por reajuste -- así el jugador que esquiva
    // fuerte de costado todavía le puede sacar el cuerpo, en vez de que
    // el jefe lo siga como un misil perfecto.
    private void steerChargeDirTowards(Vec3 desired) {
        double currentYaw = Math.toDegrees(Math.atan2(chargeDirZ, chargeDirX));
        double desiredYaw = Math.toDegrees(Math.atan2(desired.z, desired.x));
        double deltaYaw = Mth.wrapDegrees(desiredYaw - currentYaw);
        double clampedDelta = Mth.clamp(deltaYaw, -CHARGE_MAX_TURN_DEGREES, (double) CHARGE_MAX_TURN_DEGREES);
        double newYaw = Math.toRadians(currentYaw + clampedDelta);
        chargeDirX = Math.cos(newYaw);
        chargeDirZ = Math.sin(newYaw);
        applyChargeVisualYaw();
    }

    // El AttackGoal deja de mover el LookControl durante el Charge (a
    // propósito, para que no gire raro la cabeza en plena embestida), así
    // que si chargeDir cambia (homing) tenemos que rotar el modelo acá a
    // mano o se queda "pintado" mirando la dirección original aunque se
    // mueva hacia otro lado.
    private void applyChargeVisualYaw() {
        // Conversión del ángulo matemático (x=cos, z=sin) a la convención
        // de yaw de Minecraft (0° = sur/+Z, x = -sin(yaw), z = cos(yaw)).
        float mcYaw = (float) (Mth.atan2(-chargeDirX, chargeDirZ) * (180.0D / Math.PI));
        this.setYRot(mcYaw);
        this.setYHeadRot(mcYaw);
        this.yBodyRot = mcYaw;
    }

    // Índice (0-based) del golpe "de la mitad" dentro de CHARGE_DAMAGE_TICKS
    // (10 golpes en total -- índices 0..9) que también aturde, además del
    // primero y el último. Pedido explícito: golpe 1, 5 y 10 (1-based) ==
    // índices 0, 4 y 9.
    private static final int CHARGE_MID_STUN_PULSE_INDEX = 4;

    private void tickChargeDamagePulses() {
        int lastPulseTick = CHARGE_DAMAGE_TICKS[CHARGE_DAMAGE_TICKS.length - 1];
        for (int i = 0; i < CHARGE_DAMAGE_TICKS.length; i++) {
            int pulseTick = CHARGE_DAMAGE_TICKS[i];
            if (attackTicksElapsed == pulseTick && chargePulsesFiredThisAttack.add(pulseTick)) {
                applyChargePulseDamage(i, pulseTick == lastPulseTick);
            }
        }
    }

    private void applyChargePulseDamage(int pulseIndex, boolean isFinalPulse) {
        boolean isFirstPulse = pulseIndex == 0;
        boolean isMidPulse = pulseIndex == CHARGE_MID_STUN_PULSE_INDEX;
        AABB area = this.getBoundingBox().inflate(CHARGE_HIT_RADIUS);
        List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != this && e.isAlive() && this.distanceTo(e) <= CHARGE_HIT_RADIUS);
        if (nearby.isEmpty()) {
            return;
        }
        DamageSource source = this.damageSources().mobAttack(this);
        float chargeDamage = (float) (CHARGE_DAMAGE_PER_HIT * getTotalDamageMultiplier());
        for (LivingEntity target : nearby) {
            boolean wasBlocking = target.isBlocking();
            boolean landed;
            if (isFinalPulse) {
                // Último golpe de la ráfaga -- acá sí lo mandamos a volar,
                // en la dirección en la que el jefe venía embistiendo.
                landed = target.hurt(source, chargeDamage);
                Vec3 kb = new Vec3(chargeDirX, 0.0D, chargeDirZ).normalize().scale(CHARGE_FINAL_KB_HORIZONTAL);
                target.setDeltaMovement(kb.x, CHARGE_FINAL_KB_VERTICAL, kb.z);
            } else {
                // Neutralizamos el knockback de este pulso puntual -- si
                // no, el empujón por defecto de hurt() saca al jugador
                // del radio de golpe antes del próximo pulso, y en vez de
                // "ir pegando espadazos" (como marca la animación) solo
                // conecta uno.
                Vec3 velocityBeforeHit = target.getDeltaMovement();
                landed = target.hurt(source, chargeDamage);
                target.setDeltaMovement(velocityBeforeHit);
            }
            if (landed) {
                onKnightLandedHit();
                // Golpe 1, 5 y 10 (pedido explícito) aturden -- el resto
                // de los golpes intermedios ya encadenan por su cuenta
                // (uno atrás del otro, sin ventana para reaccionar), no
                // hace falta apilar más stun ahí.
                if (isFinalPulse || isFirstPulse || isMidPulse) {
                    target.addEffect(new MobEffectInstance(ModEffects.STUN.get(), CHARGE_STUN_DURATION_TICKS, 0));
                }
            }
            // Golpe 1 y 10 (pedido explícito) rompen el escudo si el
            // objetivo estaba bloqueando -- independiente de si landed
            // dio true (un bloqueo TOTAL, que igual cuenta como "te até
            // el escudo", puede hacer que hurt() devuelva false).
            if (wasBlocking && (isFirstPulse || isFinalPulse)) {
                disablePlayerShield(target, isFirstPulse
                        ? SLASH_LOOP_FIRST_HIT_SHIELD_DISABLE_TICKS
                        : SLASH_LOOP_LAST_HIT_SHIELD_DISABLE_TICKS);
            }
            if (target instanceof ServerPlayer sp) {
                sp.hurtMarked = true; // reenvía la velocidad al cliente
            }
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    this.getX(), this.getY() + 1.0D, this.getZ(), 1, 0, 0, 0, 0);

            // Un sonido por CADA instancia de daño que conecta (hasta 10 en
            // este ataque, algunas separadas por solo 2 ticks -- ver
            // CHARGE_DAMAGE_TICKS). No los frenamos ni esperamos a que
            // termine el anterior: los dejamos superponerse a propósito,
            // que es justo el efecto de "espadazos encimados" que se busca
            // con la espada moviéndose tan rápido. 3 variantes de archivo
            // (sweep_attack_1/2/3, elegidas al azar por vanilla) + pitch al
            // azar en cada llamada para que ni siquiera dos pulsos con la
            // misma variante suenen calcados.
            float sweepPitch = 0.9F + this.random.nextFloat() * 0.2F;
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.NECROKNIGHT_SWEEP_ATTACK.get(), SoundSource.HOSTILE, 0.7F, sweepPitch);
        }
    }

    /**
     * Ráfaga de fantasmitas puramente decorativa para la secuencia de
     * aparición del jefe (ver tickSpawnSequence) -- a diferencia de
     * spawnWispPortals() (el ataque de combate, apuntado al jugador con
     * portales), esta es la explosión radial de siempre desde el pecho
     * del jefe, sin apuntar a nadie en particular (no hace falta target
     * todavía en este punto de la secuencia de aparición).
     */
    private void spawnIntroWispBurst(int count) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (int i = 0; i < count; i++) {
            TwilightWispEntity ghost = ModEntities.TWILIGHT_WISP.get().create(serverLevel);
            if (ghost != null) {
                ghost.moveTo(this.getX(), this.getY() + 1.2D, this.getZ(), this.getYRot(), 0.0F);
                serverLevel.addFreshEntity(ghost);
                ghost.launch(this.getRandom());
            }
        }
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                this.getX(), this.getY() + 1.0D, this.getZ(), 20, 0.5D, 0.5D, 0.5D, 0.05D);
        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.5F, 0.8F);
    }

    /**
     * Línea de tiempo completa del ataque -- ver el bloque de constantes
     * WISP_PORTAL_* / GHOST_SUMMON_DURATION_TICKS para el detalle de cada
     * tick. Los 3 pasos (aparecer / disparar / cerrar) están separados en
     * sub-métodos solo por prolijidad, cada uno se ejecuta una única vez
     * (guardado por sus propios flags -- ver startAttack()).
     */
    private void tickWispPortalSummon() {
        if (attackTicksElapsed == 1) { // no en el tick 0 -- currentAttackDuration recién se fija en startAttack(), evitamos dudas de orden
            spawnWispPortals();
        }
        if (!wispsFiredThisAttack && attackTicksElapsed >= WISP_PORTAL_FIRE_TICK) {
            fireWispsFromPortals();
            wispsFiredThisAttack = true;
        }
        if (!wispPortalsClosedThisAttack && attackTicksElapsed >= WISP_PORTAL_CLOSE_TICK) {
            closeWispPortals();
            wispPortalsClosedThisAttack = true;
        }
    }

    /**
     * Crea los 2 portales FLOTANDO arriba del KNIGHT (no del jugador, ver
     * WISP_PORTAL_HEIGHT_ABOVE_KNIGHT), uno a cada lado -- perpendicular a
     * la línea jefe-jugador, para que queden flanqueando esa línea en vez
     * de uno atrás del otro. Cada uno mira hacia el jugador (yaw) e
     * inclinado hacia abajo (pitch), como pediste. Sin chequeo de
     * "espacio libre" tipo findPortalSpawnY -- son voladores y de vida
     * muy corta, no vale la pena la complejidad extra por el caso raro de
     * aparecer pegados a un techo.
     */
    private void spawnWispPortals() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null) {
            return; // sin objetivo no hay a quién apuntar los portales -- el ataque simplemente no hace nada visible este casteo
        }
        if (!WISP_PORTAL_ENABLED) {
            // Portal desactivado (ver WISP_PORTAL_ENABLED): los wisps salen directo del jefe, sin
            // portal ni ceremonia visual. wispPortalIdA/B quedan en -1 -- fireWispsFromPortals()
            // e closeWispPortals() ya manejan bien un id inválido (serverLevel.getEntity(-1) da
            // null), así que no hace falta tocar nada más.
            Vec3 origin = this.position().add(0.0D, this.getBbHeight() * 0.6D, 0.0D);
            for (int i = 0; i < GHOST_COUNT_PER_SUMMON * 2; i++) {
                spawnDirectWisp(serverLevel, origin, target);
            }
            serverLevel.playSound(null, origin.x, origin.y, origin.z,
                    SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.2F, 1.5F);
            return;
        }

        Vec3 targetPos = target.position();
        Vec3 toTarget = targetPos.subtract(this.position());
        Vec3 flatDir = new Vec3(toTarget.x, 0.0D, toTarget.z);
        Vec3 forward = flatDir.lengthSqr() > 1.0E-4 ? flatDir.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
        // Perpendicular en el plano horizontal (rotar 90°): (x,z) -> (-z,x).
        Vec3 perpendicular = new Vec3(-forward.z, 0.0D, forward.x);

        // Centrados en el KNIGHT (X/Z), no en el jugador -- ver el
        // comentario de WISP_PORTAL_HEIGHT_ABOVE_KNIGHT sobre el porqué.
        Vec3 knightBase = new Vec3(this.getX(), this.getY() + WISP_PORTAL_HEIGHT_ABOVE_KNIGHT, this.getZ());
        Vec3 posA = knightBase.add(perpendicular.scale(WISP_PORTAL_SIDE_OFFSET));
        Vec3 posB = knightBase.add(perpendicular.scale(-WISP_PORTAL_SIDE_OFFSET));

        wispPortalIdA = spawnSingleWispPortal(serverLevel, posA, targetPos);
        wispPortalIdB = spawnSingleWispPortal(serverLevel, posB, targetPos);
        if (serverLevel.getEntity(wispPortalIdA) instanceof TwilightPortalEntity portalA) {
            portalA.setOwnerKnightId(this.getId());
        }
        if (serverLevel.getEntity(wispPortalIdB) instanceof TwilightPortalEntity portalB) {
            portalB.setOwnerKnightId(this.getId());
        }

        serverLevel.playSound(null, knightBase.x, knightBase.y, knightBase.z,
                SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.5F, 1.3F); // pitch más agudo que el de los portales de fase -- que no se confundan al oído
    }

    /** Usado solo cuando WISP_PORTAL_ENABLED = false: un wisp sale directo del jefe, ya apuntado. */
    private void spawnDirectWisp(ServerLevel serverLevel, Vec3 origin, LivingEntity target) {
        TwilightWispEntity wisp = ModEntities.TWILIGHT_WISP.get().create(serverLevel);
        if (wisp == null) {
            return;
        }
        wisp.moveTo(origin.x, origin.y, origin.z, this.getYRot(), 0.0F);
        serverLevel.addFreshEntity(wisp);
        Vec3 aimDir = target.getEyePosition().subtract(origin).normalize();
        double jitterRad = Math.toRadians((this.random.nextDouble() * 2.0D - 1.0D) * WISP_PORTAL_AIM_JITTER_DEGREES);
        Vec3 arbitraryAxis = Math.abs(aimDir.y) < 0.99D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 rotationAxis = aimDir.cross(arbitraryAxis).normalize();
        wisp.launch(rotateAroundAxis(aimDir, rotationAxis, jitterRad));
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.x, origin.y, origin.z, 8, 0.2D, 0.2D, 0.2D, 0.02D);
    }

    /** @return el entityId del portal creado, para poder recuperarlo después en fireWispsFromPortals()/closeWispPortals(). */
    private int spawnSingleWispPortal(ServerLevel serverLevel, Vec3 spawnPos, Vec3 lookAtPos) {
        TwilightPortalEntity portal = new TwilightPortalEntity(serverLevel, spawnPos);
        portal.disableReinforcements();

        Vec3 toLookAt = lookAtPos.subtract(spawnPos);
        float yaw = (float) (Mth.atan2(toLookAt.z, toLookAt.x) * (180.0D / Math.PI)) - 90.0F;
        double horizontalDist = Math.sqrt(toLookAt.x * toLookAt.x + toLookAt.z * toLookAt.z);
        // Positivo = inclinado hacia abajo (ver el comentario en
        // TwilightPortalRenderer sobre el eje). atan2 normal (no
        // invertido) porque toLookAt.y es negativo (el portal está arriba
        // del punto al que mira), lo que ya da un ángulo positivo acá.
        float pitch = (float) (Mth.atan2(-toLookAt.y, horizontalDist) * (180.0D / Math.PI));

        portal.setFacingYaw(yaw);
        portal.setFacingPitch(pitch);
        // A PROPÓSITO no seteamos setOwnerKnightId() acá (se queda en -1,
        // el default) -- NecroticCorruptionTetherRenderer dibuja el
        // cordón de energía verde a CUALQUIER TwilightPortalEntity que
        // tenga un dueño válido (ownerId >= 0), sin mirar para nada la
        // lista activePortals del lado servidor. Estos portales de ataque
        // no son parte del sistema de Corrupción/fase -- no deben tener
        // cordón, solo aparecer, disparar los wisps y cerrarse.
        serverLevel.addFreshEntity(portal);
        return portal.getId();
    }

    /**
     * Cada uno de los 2 portales dispara sus propios GHOST_COUNT_PER_SUMMON
     * wisps (10 en total, no repartidos), YA apuntados al jugador (con un
     * poco de dispersión al azar, ver WISP_PORTAL_AIM_JITTER_DEGREES, para
     * que no salgan todos pegados en la misma línea). Si algún portal ya
     * no existe (lo mataron, caso muy raro dado lo corto que vive) o el
     * objetivo se perdió, esa mitad simplemente no dispara -- no rompe
     * nada.
     */
    private void fireWispsFromPortals() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null) {
            return;
        }

        fireWispsFromOnePortal(serverLevel, wispPortalIdA, target);
        fireWispsFromOnePortal(serverLevel, wispPortalIdB, target);
    }

    /** GHOST_COUNT_PER_SUMMON wisps desde ESTE portal puntual -- si ya no existe (caso raro), no hace nada. */
    private void fireWispsFromOnePortal(ServerLevel serverLevel, int portalId, LivingEntity target) {
        if (!(serverLevel.getEntity(portalId) instanceof TwilightPortalEntity portal) || !portal.isAlive()) {
            return;
        }
        for (int i = 0; i < GHOST_COUNT_PER_SUMMON; i++) {
            launchWispFromPortal(serverLevel, portal, target);
        }
    }

    private void launchWispFromPortal(ServerLevel serverLevel, TwilightPortalEntity portal, LivingEntity target) {
        TwilightWispEntity wisp = ModEntities.TWILIGHT_WISP.get().create(serverLevel);
        if (wisp == null) {
            return;
        }
        wisp.moveTo(portal.getX(), portal.getY(), portal.getZ(), portal.getYRot(), 0.0F);
        serverLevel.addFreshEntity(wisp);

        Vec3 aimDir = target.getEyePosition().subtract(portal.position()).normalize();
        // Dispersión: rotamos aimDir un ángulo al azar dentro de
        // WISP_PORTAL_AIM_JITTER_DEGREES alrededor de un eje perpendicular
        // elegido al azar en el plano perpendicular a aimDir -- así el
        // cono de dispersión es parejo en todas direcciones (no solo
        // izq/der u arriba/abajo).
        double jitterRad = Math.toRadians((this.random.nextDouble() * 2.0D - 1.0D) * WISP_PORTAL_AIM_JITTER_DEGREES);
        Vec3 arbitraryAxis = Math.abs(aimDir.y) < 0.99D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 rotationAxis = aimDir.cross(arbitraryAxis).normalize();
        Vec3 jitteredDir = rotateAroundAxis(aimDir, rotationAxis, jitterRad);

        wisp.launch(jitteredDir);

        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                portal.getX(), portal.getY(), portal.getZ(), 8, 0.2D, 0.2D, 0.2D, 0.02D);
    }

    /** Rotación de Rodrigues -- vector v alrededor de axis (unitario) por angleRad. */
    private static Vec3 rotateAroundAxis(Vec3 v, Vec3 axis, double angleRad) {
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        Vec3 term1 = v.scale(cos);
        Vec3 term2 = axis.cross(v).scale(sin);
        Vec3 term3 = axis.scale(axis.dot(v) * (1.0D - cos));
        return term1.add(term2).add(term3);
    }

    private void closeWispPortals() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.getEntity(wispPortalIdA) instanceof TwilightPortalEntity portalA) {
            portalA.forceClose();
        }
        if (serverLevel.getEntity(wispPortalIdB) instanceof TwilightPortalEntity portalB) {
            portalB.forceClose();
        }
    }

    private void spawnMirrorClone(int mirrorTag) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        NecroticKnightFakeEntity fake = ModEntities.NECROTIC_KNIGHT_FAKE.get().create(serverLevel);
        if (fake == null) {
            return;
        }
        fake.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
        serverLevel.addFreshEntity(fake);
        fake.configure(this, mirrorTag, 200);
    }

    // Ahora dispara VARIAS ondas (ver WAVE_PULSES), sincronizadas con los
    // keyframes del forcejeo de la animación wave_cast: una al clavar la
    // espada, una por cada sacudida mientras forcejea para sacarla, y una
    // grande de remate cuando finalmente la arranca del piso. El anillo en
    // sí lo dibuja NecroticWaveRenderer (cliente) con el shader
    // nanookmod:necrotic_wave -- este método server-side decide el
    // sonido/telegraph de cada onda y el daño real (SIEMPRE lo decide el
    // servidor, nunca el shader del cliente).
    private void tickWave() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        for (WavePulseDef pulse : WAVE_PULSES) {
            if (attackTicksElapsed == pulse.tick()) {
                boolean isBigPulse = pulse.maxRadius() >= 10.0D;
                serverLevel.playSound(null, this.blockPosition(),
                        SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE,
                        0.5F, isBigPulse ? 1.2F : 1.7F); // grandes = graves, temblores = agudos y cortos
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        this.getX(), this.getY() + 0.2D, this.getZ(),
                        isBigPulse ? 20 : 8, 0.3D, 0.1D, 0.3D, 0.05D);
            }

            int sincePulse = attackTicksElapsed - pulse.tick();
            if (sincePulse < 0 || sincePulse > pulse.expansionTicks()) {
                continue; // esta onda todavía no nació, o ya se disipó del todo
            }

            double progress = (double) sincePulse / pulse.expansionTicks();
            double radius = progress * pulse.maxRadius();

            Set<UUID> hitByThisPulse = waveHitPlayersPerPulse.computeIfAbsent(pulse.tick(), k -> new HashSet<>());

            for (ServerPlayer player : serverLevel.getPlayers(p -> true)) {
                if (hitByThisPulse.contains(player.getUUID())) {
                    continue;
                }
                double dist = this.distanceTo(player);
                if (Math.abs(dist - radius) > WAVE_BAND_WIDTH) {
                    continue;
                }

                double playerHeightAboveKnight = player.getY() - this.getY();
                boolean jumpedOverIt = playerHeightAboveKnight >= WAVE_JUMP_CLEARANCE;

                hitByThisPulse.add(player.getUUID());

                if (!jumpedOverIt) {
                    if (player.hurt(this.damageSources().mobAttack(this), getWaveDamage() * pulse.damageFraction())) {
                        onKnightLandedHit();
                    }
                    player.setDeltaMovement(player.getDeltaMovement().add(0, 0.35D, 0));
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Guard & Slap
    // ---------------------------------------------------------------

    private void tickGuardSlap() {
        if (attackTicksElapsed == GUARD_SLAP_COVER_TICK) {
            // El escudo terminó de cubrir (0.40s) -- clank metálico.
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 0.7F);
        }
        if (attackTicksElapsed == GUARD_SLAP_GUARD_TICKS) {
            // Termina el segundo de guardia -- arranca el swing. Acá suena SOLO el silbido del
            // golpe. El yunque sonaba en este tick SIEMPRE, hubiera o no algo al alcance, y el
            // manotazo recién conecta 3 ticks después (GUARD_SLAP_HIT_TICK): ahora el yunque
            // suena en applyGuardSlapHit(), y solo si el escudo de verdad le pega a algo.
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
        if (attackTicksElapsed == GUARD_SLAP_HIT_TICK) {
            applyGuardSlapHit();
        }
    }

    private void applyGuardSlapHit() {
        Vec3 forward = flatForward();
        boolean hitSomething = false;
        AABB area = this.getBoundingBox().inflate(GUARD_SLAP_RANGE);
        // El portal no es un objetivo: antes el manotazo también le pegaba a los portales del propio jefe.
        for (LivingEntity nearby : this.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != this && e.isAlive() && !(e instanceof TwilightPortalEntity))) {
            // Alcance y cono HORIZONTALES contra el yaw visual (antes: 3D con getViewVector, que
            // incluye la inclinación de la cabeza y se desviaba con la altura del objetivo).
            if (!isInMeleeArc(nearby, GUARD_SLAP_RANGE, GUARD_SLAP_ANGLE_DEGREES)) {
                continue;
            }
            Vec3 away = new Vec3(nearby.getX() - this.getX(), 0.0D, nearby.getZ() - this.getZ());
            if (away.lengthSqr() < 1.0E-4D) {
                away = forward;
            }
            away = away.normalize();

            boolean wasBlocking = nearby.isBlocking();
            if (nearby.hurt(this.damageSources().mobAttack(this), getGuardSlapDamage())) {
                onKnightLandedHit();
                nearby.addEffect(new MobEffectInstance(ModEffects.STUN.get(), GUARD_SLAP_STUN_DURATION_TICKS, 0));
                hitSomething = true;
            }
            if (wasBlocking) {
                disablePlayerShield(nearby, GUARD_SLAP_SHIELD_DISABLE_TICKS); // te tapás con el escudo, te lo hago pedazos igual
                hitSomething = true;
            }
            Vec3 kb = away.scale(GUARD_SLAP_KB_HORIZONTAL);
            nearby.setDeltaMovement(kb.x, GUARD_SLAP_KB_VERTICAL, kb.z);
            if (nearby instanceof ServerPlayer sp) {
                sp.hurtMarked = true; // reenvía la velocidad al cliente, igual que en el knockback de aparición
            }
        }
        if (hitSomething) {
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    this.getX() + forward.x * 2.0D, this.getY() + 1.2D, this.getZ() + forward.z * 2.0D, 1, 0, 0, 0, 0);
        }
    }

    // ---------------------------------------------------------------
    // Rayo (Corrupción IV) -- windup + channel.
    //
    // El rayo en sí (visual Y daño) vive en NecroticLightningBeamEntity,
    // una entidad de verdad que nace acá al arrancar el canalizado y se
    // ocupa sola de todo lo demás (seguir al jefe, pegar, autodestruirse).
    // Este método ya no calcula nada de posición/daño -- solo dispara el
    // telegraph sonoro y crea la entidad.
    // ---------------------------------------------------------------

    private void tickLightning() {

        // --- Anillo que CRECE apenas arranca el windup (telegraph) ---
        if (attackTicksElapsed == 1 && this.level() instanceof ServerLevel serverLevelRing) {
            Vec3 ringOrigin = getLightningOrigin(1.0f);
            serverLevelRing.sendParticles(ModParticles.NECROTIC_LIGHTNING_RING_GROW.get(),
                    ringOrigin.x, ringOrigin.y, ringOrigin.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        if (attackTicksElapsed == LIGHTNING_WINDUP_TICKS) {
            // Arranca sword_beam_channel -- avisale al cliente para que
            // NecroticKnightModel empiece a inclinar el brazo (ver
            // DATA_LIGHTNING_CHANNELING más arriba para el porqué).
            this.entityData.set(DATA_LIGHTNING_CHANNELING, true);
        }
        if (attackTicksElapsed == LIGHTNING_WINDUP_TICKS && this.level() instanceof ServerLevel serverLevel) {

            // --- Anillo que SE ENCOGE justo cuando el rayo real arranca ---
            Vec3 shrinkOrigin = getLightningOrigin(1.0f);
            serverLevel.sendParticles(ModParticles.NECROTIC_LIGHTNING_RING_SHRINK.get(),
                    shrinkOrigin.x, shrinkOrigin.y, shrinkOrigin.z, 1, 0.0, 0.0, 0.0, 0.0);

            // Justo al arrancar sword_beam_channel -- acá nace la entidad
            // real del rayo. El sonido ya NO se dispara acá con
            // playSound() de una: le avisamos a NecroticLightningBeamEntity
            // que arranque su propio loop con fade (ver #startLoopingSound
            // en esa clase), atado a su propio ciclo de vida en vez de a
            // ticks fijos de esta entidad -- así se apaga solo, con fade,
            // en el mismo momento en que el rayo se descarta de verdad
            // (LIGHTNING_ACTIVE_CHANNEL_TICKS), no cuando termina la
            // animación completa del jefe (que sigue un rato más).
            NecroticLightningBeamEntity beam = new NecroticLightningBeamEntity(
                    ModEntities.NECROTIC_LIGHTNING_BEAM.get(), this.level());
            beam.setOwner(this);
            serverLevel.addFreshEntity(beam);
            beam.startLoopingSound();
        }
    }

    /** Daño de un pulso del rayo, con los mismos multiplicadores (fase final, dificultad) que el resto de los ataques. Sin modificador: NecroticLightningBeamEntity lo llama. */
    float getLightningDamage() {
        float base = LIGHTNING_DAMAGE_PER_TICK; // el bonus de enrage ya está en getTotalDamageMultiplier()
        return (float) (base * getTotalDamageMultiplier());
    }

    /**
     * Punto donde "nace" el rayo (la punta de la espada), en coordenadas
     * de mundo.
     *
     * ANTES este método tenía su PROPIA fórmula (solo yaw, altura fija en
     * 0.62*bbHeight) -- una fórmula DISTINTA a la que usa
     * NecroticLightningBeamEntity#computeOriginFor (yaw+pitch completo,
     * otros números de offset) para posicionar la entidad del rayo que
     * en verdad se dibuja y con la que se calcula el impacto. Es decir: el
     * jefe apuntaba (calculaba yaw/pitch ideal) mirando desde UN punto,
     * pero el rayo visualmente nacía y pegaba desde OTRO punto un poco
     * distinto -- pequeño pero notorio sobre todo en pitch, ya que esta
     * fórmula vieja ni siquiera lo tenía en cuenta.
     *
     * AHORA delega en computeOriginFor -- la única fórmula real, la
     * misma que usa el rayo para dibujarse y pegar. Así la puntería
     * (updateLightningAim) SIEMPRE calcula sus ángulos desde exactamente
     * el mismo punto de donde el rayo visualmente sale.
     */
    public Vec3 getLightningOrigin(float partialTick) {
        return NecroticLightningBeamEntity.computeOriginFor(this);
    }

    /**
     * Gira el jefe hacia el pecho del target -- CUERPO y CABEZA a la vez,
     * al mismo valor -- en vez de usar LookControl. Mismo motivo que
     * applyChargeVisualYaw un poco más arriba (ver ese comentario):
     * LookControl solo mueve la cabeza (yHeadRot/xRot), nunca el cuerpo
     * (yBodyRot, que es lo que gira el modelo ENTERO al renderizar). Como
     * durante el canalizado el jefe está parado quieto (sin caminar, que
     * es lo único que normalmente hace que el cuerpo "alcance" a la
     * cabeza), el modelo se quedaba mirando para cualquier lado mientras
     * el rayo (que depende de la MISMA rotación que usa LookControl para
     * apuntar) sí seguía al jugador -- justo el bug reportado ("el modelo
     * no se movió pero el rayo sí me seguía"). Al setear yRot/yHeadRot/
     * yBodyRot los tres iguales acá, no pueden desincronizarse nunca: lo
     * que ves girar ES lo que calcula el daño, siempre.
     *
     * Limitado a maxDegreesPerTick grados por tick (mismo mecanismo que
     * el maxYRot/maxXRot de LookControl, hecho a mano) -- esto es lo que
     * da el margen real para esquivar moviéndose rápido.
     */
    /** Sigue en sword_beam_windup (todavía no arrancó el canalizado real). Lo usa NecroticKnightAttackGoal para decidir la velocidad de puntería. */
    public boolean isLightningWindingUp() {
        return getAttackState() == ATTACK_LIGHTNING && attackTicksElapsed < LIGHTNING_WINDUP_TICKS;
    }

    /**
     * A diferencia de isLightningWindingUp(), este SÍ es seguro de leer
     * del lado del cliente (SynchedEntityData, no attackTicksElapsed).
     * Lo usa NecroticKnightModel para saber cuándo inclinar el brazo.
     */
    public boolean isLightningChanneling() {
        return this.entityData.get(DATA_LIGHTNING_CHANNELING);
    }

    public void updateLightningAim(LivingEntity target, float yawDegreesPerTick, float pitchDegreesPerTick) {
        // Mismo origen que usa el daño (getLightningOrigin) -- no
        // duplicamos una cuenta de altura aparte, así al menos la
        // puntería siempre es consistente con DÓNDE se calcula el golpe.
        Vec3 origin = getLightningOrigin(1.0F);
        double dx = target.getX() - origin.x;
        double dz = target.getZ() - origin.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        // Apunta a los PIES del target (ras de piso) -- a propósito, para
        // que el punto de impacto SIEMPRE esté a nivel de piso sin
        // importar la distancia (no solo cuando el rayo falla). Como la
        // hitbox del jugador tiene alto + el margen de HIT_MARGIN en
        // computeBeamEnd, en la práctica esto pega en algún punto entre
        // el pecho y los pies -- nunca por encima del pecho.
        double dy = target.getY() - origin.y;

        // Protección contra la inestabilidad de atan2 cerca del origen:
        // si el jugador está casi pegado/debajo del jefe (distancia
        // horizontal casi 0), el ángulo yaw queda matemáticamente
        // indefinido y puede saltar a cualquier valor con el más mínimo
        // ruido -- eso es EXACTAMENTE el bug de "de cerca el jefe apunta
        // para cualquier lado, como si estuviera mirando algo lejos".
        // Con esta guarda, si estás demasiado cerca simplemente dejamos
        // el yaw como estaba (no gira ese tick) en vez de calcular un
        // ángulo basura.
        if (horizontalDist > 0.35D) {
            // Misma convención que applyChargeVisualYaw (forwardX=-sin(yaw),
            // forwardZ=cos(yaw)) -- invertida acá para resolver el yaw dado
            // un dx/dz, en vez de al revés.
            float idealYaw = (float) (Mth.atan2(-dx, dz) * (180.0D / Math.PI));
            float newYaw = stepTowardsAngle(this.getYRot(), idealYaw, yawDegreesPerTick);
            this.setYRot(newYaw);
            this.setYHeadRot(newYaw);
            this.yBodyRot = newYaw;
        }

        // El pitch no tiene el mismo problema de inestabilidad (atan2 con
        // el segundo argumento en 0 da ±90°, no indefinido), así que se
        // sigue actualizando siempre, y a su PROPIA velocidad.
        float idealPitch = (float) -(Mth.atan2(dy, horizontalDist) * (180.0D / Math.PI));
        float newPitch = stepTowardsAngle(this.getXRot(), idealPitch, pitchDegreesPerTick);
        this.setXRot(newPitch);

        // FIX REAL DEL BUG -- getXRot()/xRotO de esta entidad NO le
        // llegan de forma confiable al cliente (confirmado con los logs:
        // el servidor calculaba bien el pitch, pero el modelo del
        // cliente siempre leía 0). En vez de depender de esa sync
        // implícita (que claramente no funciona para este mob -- puede
        // ser por la estructura jefe+caballo), escribimos el pitch acá
        // en un SynchedEntityData, que SÍ está garantizado que se
        // sincroniza al cliente. NecroticKnightModel#setCustomAnimations
        // ahora lee de acá (getSyncedLightningPitch()) en vez de
        // getXRot()/xRotO directamente.
        this.entityData.set(DATA_LIGHTNING_AIM_PITCH, newPitch);
    }

    /**
     * Pitch actual del rayo, tal como lo ve el CLIENTE -- viene de un
     * SynchedEntityData actualizado cada tick en updateLightningAim, NO
     * de getXRot()/xRotO (esos dos, para este mob en particular, no se
     * sincronizan de forma confiable al cliente -- confirmado con logs
     * de diagnóstico: el servidor tenía el pitch correcto, el cliente
     * siempre veía 0). Lo usa NecroticKnightModel para inclinar el
     * brazo/espada.
     */
    public float getSyncedLightningPitch() {
        return this.entityData.get(DATA_LIGHTNING_AIM_PITCH);
    }

    private static float stepTowardsAngle(float current, float target, float maxStep) {
        float delta = Mth.wrapDegrees(target - current);
        delta = Mth.clamp(delta, -maxStep, maxStep);
        return current + delta;
    }

    /** Hacia dónde apunta el rayo ahora mismo -- pura rotación del jefe (getViewVector). Lo usa NecroticLightningBeamEntity#setOwner/followOwner al nacer, y updateLightningAim indirectamente vía getYRot/getXRot. */
    public Vec3 getLightningDirection(float partialTick) {
        return this.getViewVector(partialTick);
    }

    /** Target guardado al arrancar ESTE rayo (ver triggerLightning) -- funciona igual en cliente y servidor, Level#getEntity(id) resuelve en los dos lados. Actualmente sin uso directo (la puntería usa updateLightningAim), lo dejamos por si hace falta más adelante. */
    @Nullable
    public LivingEntity getLightningTarget() {
        int id = this.entityData.get(DATA_LIGHTNING_TARGET_ID);
        if (id < 0) {
            return null;
        }
        Entity entity = this.level().getEntity(id);
        return (entity instanceof LivingEntity living && living.isAlive()) ? living : null;
    }

    // ---------------------------------------------------------------
    // Vanish -- desvanecerse, reposicionarse detrás del jugador,
    // reaparecer. Ver el bloque de constantes VANISH_* para la línea de
    // tiempo completa (créditos: idea tomada de "The Immortal" de
    // EEEABsMobs, licencia LGPL-3.0).
    // ---------------------------------------------------------------

    private void tickVanish() {
        // --- Fundido a invisible ---
        if (attackTicksElapsed <= VANISH_FADE_OUT_TICKS) {
            double progress = (double) attackTicksElapsed / VANISH_FADE_OUT_TICKS;
            setRenderAlpha((int) Math.round(255 * (1.0D - progress)));
        }

        // --- Reposicionamiento (ya totalmente invisible en este punto) ---
        if (attackTicksElapsed == VANISH_TELEPORT_TICK && !vanishTeleportDone) {
            vanishTeleportDone = true;
            if (this.level() instanceof ServerLevel serverLevel) {
                doVanishTeleport(serverLevel);
            }
        }

        // --- Fundido de vuelta a visible ---
        if (attackTicksElapsed >= VANISH_FADE_IN_START_TICK) {
            int sinceFadeIn = attackTicksElapsed - VANISH_FADE_IN_START_TICK;
            double progress = Mth.clamp((double) sinceFadeIn / VANISH_FADE_IN_TICKS, 0.0D, 1.0D);
            setRenderAlpha((int) Math.round(255 * progress));
        }
    }

    /**
     * Sonido de desvanecimiento/reaparición + el teletransporte en sí +
     * partículas en el punto de salida (para que quede claro, aunque sea
     * por un instante, DÓNDE estaba antes de desaparecer). El punto de
     * llegada lo calcula findSafeTeleportPos -- ver ese método para el
     * porqué de la búsqueda en varios intentos.
     */
    private void doVanishTeleport(ServerLevel serverLevel) {
        Vec3 originPos = this.position();
        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.6F); // pitch grave, más "pesado" que el enderman normal

        LivingEntity target = this.getTarget();
        Vec3 destination = target != null ? findSafeTeleportPos(serverLevel, target) : null;

        if (destination != null) {
            // target no puede ser null acá: destination solo sale
            // no-null si target tampoco lo era (ver el ternario arriba).
            //
            // Yaw hacia el jugador calculado a mano ANTES de moveTo --
            // mismo motivo/mismo truco que applyChargeVisualYaw(): si
            // usáramos getLookControl().setLookAt() acá, yHeadRot no se
            // actualiza hasta el próximo LookControl#tick() (que corre
            // más tarde en el ciclo), así que el primer frame reaparecería
            // mirando para cualquier lado en vez de al jugador. Misma
            // conversión ángulo matemático -> yaw de Minecraft que
            // applyChargeVisualYaw() (0° = sur/+Z, x = -sin(yaw), z =
            // cos(yaw)) -- ver ese método para el detalle.
            double dx = target.getX() - destination.x;
            double dz = target.getZ() - destination.z;
            float yawToTarget = (float) (Mth.atan2(-dx, dz) * (180.0D / Math.PI));
            this.moveTo(destination.x, destination.y, destination.z, yawToTarget, 0.0F);
            this.setYHeadRot(yawToTarget);
            this.yBodyRot = yawToTarget;
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    destination.x, destination.y + 1.0D, destination.z, 12, 0.3D, 0.5D, 0.3D, 0.05D);
        }
        // Si no encontró destino seguro (jugador rodeado de roca sólida
        // por todos lados, caso raro), no se mueve -- simplemente
        // reaparece en el mismo lugar donde desapareció. Sigue siendo un
        // "parpadeo" visualmente válido, solo que sin el reposicionamiento.

        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                originPos.x, originPos.y + 1.0D, originPos.z, 12, 0.3D, 0.5D, 0.3D, 0.05D);
    }

    /**
     * Busca un punto detrás del jugador (dirección opuesta a hacia donde
     * mira, VANISH_BEHIND_DISTANCE de distancia) con espacio libre real --
     * reutiliza findPortalSpawnY para el chequeo vertical (misma lógica
     * que ya usan los portales: NO usar el heightmap, así funciona igual
     * de bien en cuevas/interiores que a cielo abierto).
     *
     * Si ese punto exacto está enterrado, prueba también EN FRENTE del
     * jugador (mismo radio) antes de rendirse -- mejor aparecer de frente
     * que no aparecer. Devuelve null solo si ninguna de las dos direcciones
     * tiene espacio (jugador metido en un hueco muy cerrado).
     */
    @Nullable
    private Vec3 findSafeTeleportPos(ServerLevel serverLevel, LivingEntity target) {
        double yawRad = Math.toRadians(target.getYRot());
        // Vector "hacia adelante" del jugador en XZ (mismo criterio que
        // getViewVector, pero no hace falta el eje Y acá).
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);

        double behindX = target.getX() - lookX * VANISH_BEHIND_DISTANCE;
        double behindZ = target.getZ() - lookZ * VANISH_BEHIND_DISTANCE;
        Double behindY = findPortalSpawnY(serverLevel, behindX, target.getY(), behindZ);
        if (behindY != null) {
            return new Vec3(behindX, behindY, behindZ);
        }

        // Fallback: en frente, mismo radio.
        double frontX = target.getX() + lookX * VANISH_BEHIND_DISTANCE;
        double frontZ = target.getZ() + lookZ * VANISH_BEHIND_DISTANCE;
        Double frontY = findPortalSpawnY(serverLevel, frontX, target.getY(), frontZ);
        if (frontY != null) {
            return new Vec3(frontX, frontY, frontZ);
        }

        return null;
    }

    // ---------------------------------------------------------------
    // Flash de transparencia al recibir daño
    // ---------------------------------------------------------------

    private void startHurtFlash() {
        // Si ya está en pleno Vanish, el alpha lo maneja tickVanish() --
        // no lo pisamos, se vería como un parpadeo raro encima del fundido.
        if (getAttackState() != ATTACK_VANISH) {
            hurtFlashTicks = 0;
        }
    }

    private void tickHurtFlash() {
        if (arrowPhaseTicks >= 0) {
            return; // el esquive fantasma manda mientras esté activo, ver tickArrowPhase()
        }
        if (hurtFlashTicks < 0) {
            return;
        }
        if (getAttackState() == ATTACK_VANISH) {
            // El Vanish arrancó a mitad de un flash -- le cede el control
            // del alpha sin pelearse por él, y cancela el flash pendiente.
            hurtFlashTicks = -1;
            return;
        }

        if (hurtFlashTicks <= HURT_FLASH_OUT_TICKS) {
            double progress = (double) hurtFlashTicks / HURT_FLASH_OUT_TICKS;
            setRenderAlpha((int) Math.round(255 + (HURT_FLASH_ALPHA - 255) * progress));
        } else if (hurtFlashTicks <= HURT_FLASH_OUT_TICKS + HURT_FLASH_HOLD_TICKS) {
            setRenderAlpha(HURT_FLASH_ALPHA);
        } else if (hurtFlashTicks < HURT_FLASH_TOTAL_TICKS) {
            int sinceHold = hurtFlashTicks - HURT_FLASH_OUT_TICKS - HURT_FLASH_HOLD_TICKS;
            double progress = (double) sinceHold / HURT_FLASH_IN_TICKS;
            setRenderAlpha((int) Math.round(HURT_FLASH_ALPHA + (255 - HURT_FLASH_ALPHA) * progress));
        } else {
            setRenderAlpha(255);
            hurtFlashTicks = -1;
            return;
        }
        hurtFlashTicks++;
    }

    /**
     * Lo llama ModEvents cuando se cancela el impacto de una flecha (ver
     * el comentario largo en esa clase). Mismo esquema triangular que
     * tickHurtFlash(), constantes propias (ARROW_PHASE_*) -- más marcado
     * y bastante más corto, para que se sienta como un parpadeo
     * instantáneo y no un fundido con tiempo de sobra para pensarlo.
     *
     * Si ya está en pleno Vanish, no hace nada -- ese ya está totalmente
     * invisible o en pleno fundido, un parpadeo encima no se notaría ni
     * aportaría nada.
     */
    public void triggerArrowPhase() {
        if (getAttackState() != ATTACK_VANISH) {
            arrowPhaseTicks = 0;
            hurtFlashTicks = -1; // corta cualquier flash de daño en curso -- el esquive manda
        }
    }

    private void tickArrowPhase() {
        if (arrowPhaseTicks < 0) {
            return;
        }
        if (getAttackState() == ATTACK_VANISH) {
            arrowPhaseTicks = -1;
            return;
        }

        if (arrowPhaseTicks <= ARROW_PHASE_OUT_TICKS) {
            double progress = (double) arrowPhaseTicks / ARROW_PHASE_OUT_TICKS;
            setRenderAlpha((int) Math.round(255 + (ARROW_PHASE_ALPHA - 255) * progress));
        } else if (arrowPhaseTicks <= ARROW_PHASE_OUT_TICKS + ARROW_PHASE_HOLD_TICKS) {
            setRenderAlpha(ARROW_PHASE_ALPHA);
        } else if (arrowPhaseTicks < ARROW_PHASE_TOTAL_TICKS) {
            int sinceHold = arrowPhaseTicks - ARROW_PHASE_OUT_TICKS - ARROW_PHASE_HOLD_TICKS;
            double progress = (double) sinceHold / ARROW_PHASE_IN_TICKS;
            setRenderAlpha((int) Math.round(ARROW_PHASE_ALPHA + (255 - ARROW_PHASE_ALPHA) * progress));
        } else {
            setRenderAlpha(255);
            arrowPhaseTicks = -1;
            return;
        }
        arrowPhaseTicks++;
    }

    // ---------------------------------------------------------------
    // Fases / portales
    // ---------------------------------------------------------------

    private void tickPhaseCheck() {
        if (nextPhaseIndex >= PHASE_HP_THRESHOLDS.length) {
            return; // ya se abrieron todos los portales posibles
        }

        double hpRatio = this.getHealth() / this.getMaxHealth();
        if (hpRatio > PHASE_HP_THRESHOLDS[nextPhaseIndex]) {
            return;
        }

        int portalsToOpen = PORTALS_PER_PHASE[nextPhaseIndex];
        if (PORTALS_ENABLED) {
            openPortalsAroundKnight(portalsToOpen);
        }
        nextPhaseIndex++;

        // Justo cruzó el ÚLTIMO umbral (25%) -- es el instante exacto en
        // que isFinalPhase() pasa a true. Telegraph de una sola vez: nada
        // sutil, queremos que el jugador SIENTA el cambio de marcha del
        // jefe (ver conversación: "se siente como esponja de daño").
        if (isFinalPhase() && !enrageTelegraphed) {
            enrageTelegraphed = true;
            triggerEnrage();
        }
    }

    private void triggerEnrage() {
        AttributeInstance speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(ENRAGE_SPEED_MODIFIER_ID);
            speedAttr.addPermanentModifier(new AttributeModifier(
                    ENRAGE_SPEED_MODIFIER_ID,
                    "Enrage: Final Phase",
                    ENRAGE_SPEED_BONUS,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            // Rugido de "esto ya se puso feo" -- sonido vanilla (RAVAGER)
            // en vez de uno de los sonidos propios del knight porque
            // ninguno de esos (spawn/idle/hurt/death/sweep/lightning) está
            // pensado como un grito de rabia -- si en algún momento
            // grabás/conseguís uno propio, este es el único lugar que hay
            // que tocar.
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 0.75F); // pitch bajo, más amenazante que el ravager normal

            // Anillo de partículas expandiéndose desde el jefe -- mismo
            // truco visual que ya usás para el windup del Rayo
            // (NECROTIC_LIGHTNING_RING_GROW), pero con soul fire normal
            // para no confundirlo con el telegraph de un ataque puntual.
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX(), this.getY() + 1.0D, this.getZ(), 40, 0.6D, 0.8D, 0.6D, 0.12D);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 0.2D, this.getZ(), 20, 0.5D, 0.1D, 0.5D, 0.02D);
        }
    }

    // ---------------------------------------------------------------
    // Corrupción ("Pacto Necromántico")
    //
    // Mientras el jugador ignora los portales/mobs que van quedando
    // activos, el jefe va subiendo de nivel de Corrupción (0..MAX_CORRUPTION)
    // y se pone más peligroso (ver getCorruptionDamageMultiplier /
    // applyCorruptionSpeedModifier / tickCorruptionRegen). Si el jugador
    // hace bien el trabajo (rompe portales, mata los mobs reforzados), baja
    // de nuevo -- pero deja una "cicatriz" permanente la primera vez que
    // toca cada nivel nuevo (ver setScarLevel), así que ignorar portales
    // sigue siendo costoso incluso si después limpiás todo.
    //
    // OJO: esto es la base del sistema (subida/bajada + buffs de nivel
    // 1-3). El cordón visual portal↔jefe, el área necrótica de nivel 4, y
    // la habilidad nueva de nivel 5 quedan para más adelante -- ver charla.
    // ---------------------------------------------------------------

    private void tickCorruption() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int pressuringCount = countPressuringSources(serverLevel);
        int level = getCorruptionLevel();

        if (pressuringCount > 0) {
            int riseIndex = Math.min(pressuringCount, CORRUPTION_RISE_TICKS.length - 1);
            int ticksNeeded = CORRUPTION_RISE_TICKS[riseIndex];
            corruptionProgressTicks++;
            if (corruptionProgressTicks >= ticksNeeded) {
                corruptionProgressTicks = 0;
                if (level < MAX_CORRUPTION) {
                    setCorruptionLevel(level + 1);
                }
            }
        } else if (level > 0) {
            corruptionProgressTicks++;
            if (corruptionProgressTicks >= CORRUPTION_DECAY_TICKS) {
                corruptionProgressTicks = 0;
                setCorruptionLevel(level - 1);
            }
        } else {
            corruptionProgressTicks = 0;
        }

        tickTetherArrivalParticles(serverLevel);

        tickCorruptionRegen(level);
    }

    /**
     * Cuenta portales + mobs suyos que YA pasaron la ventana de gracia
     * (CORRUPTION_GRACE_TICKS) y por lo tanto están "presionando" ahora
     * mismo. De paso, limpia activePortals de cualquier UUID que ya no
     * apunte a un portal vivo -- esa lista nunca se depuraba sola salvo en
     * forceClose()/muerte del jefe, y para este chequeo (que corre TODOS
     * los ticks) nos conviene que esté al día.
     */
    private int countPressuringSources(ServerLevel serverLevel) {
        int count = 0;
        Iterator<UUID> it = activePortals.iterator();
        while (it.hasNext()) {
            UUID portalId = it.next();
            if (!(serverLevel.getEntity(portalId) instanceof TwilightPortalEntity portal) || !portal.isAlive()) {
                it.remove();
                continue;
            }
            if (portal.tickCount >= CORRUPTION_GRACE_TICKS) {
                count++;
            }
            count += portal.countPressuringMobs(serverLevel, CORRUPTION_GRACE_TICKS);
        }
        return count;
    }

    // Humo verde llegando al jefe por donde "entra" cada cordón activo --
    // simétrico al que emite TwilightPortalEntity#tickTetherParticles del
    // otro lado. Uno por portal conectado (no uno por partícula individual
    // -- con 4 portales serían 4 puntitos de humo alrededor del pecho, no
    // una explosión).
    private static final int TETHER_ARRIVAL_PARTICLE_INTERVAL_TICKS = 5;
    private static final org.joml.Vector3f TETHER_ARRIVAL_PARTICLE_COLOR =
            new org.joml.Vector3f(0.3843F, 0.9647F, 0.5804F); // #62f694

    private void tickTetherArrivalParticles(ServerLevel serverLevel) {
        // Desactivado a pedido -- el humo verde llegando al jefe ya no se
        // dibuja. Para volver a activarlo, descomentar el cuerpo de abajo.
        /*
        if (activePortals.isEmpty() || this.tickCount % TETHER_ARRIVAL_PARTICLE_INTERVAL_TICKS != 0) {
            return;
        }
        net.minecraft.core.particles.DustParticleOptions dust =
                new net.minecraft.core.particles.DustParticleOptions(TETHER_ARRIVAL_PARTICLE_COLOR, 1.3F);
        for (int i = 0; i < activePortals.size(); i++) {
            serverLevel.sendParticles(dust,
                    this.getX(), this.getY() + this.getBbHeight() * 0.65D, this.getZ(),
                    2, 0.3D, 0.3D, 0.3D, 0.01D);
        }
        */
    }

    public int getCorruptionLevel() {
        return this.entityData.get(DATA_CORRUPTION_LEVEL);
    }

    /** Para que el cliente (NecroticKnightGlowLayer) pueda normalizar el nivel actual sin duplicar la constante. */
    public int getMaxCorruptionLevel() {
        return MAX_CORRUPTION;
    }

    public int getScarLevel() {
        return this.entityData.get(DATA_SCAR_LEVEL);
    }

    private void setCorruptionLevel(int newLevel) {
        newLevel = Mth.clamp(newLevel, 0, MAX_CORRUPTION);
        int old = getCorruptionLevel();
        if (newLevel == old) {
            return;
        }
        this.entityData.set(DATA_CORRUPTION_LEVEL, newLevel);

        if (newLevel > getScarLevel()) {
            setScarLevel(newLevel); // pico nuevo -- la cicatriz sube y ya no vuelve a bajar
        }

        applyCorruptionSpeedModifier(newLevel);
        updateBossBarCorruptionText();

        if (this.level() instanceof ServerLevel serverLevel) {
            if (newLevel > old) {
                // Sube: el "hilo" de energía absorbido -- efectos simples
                // por ahora (sonido + partículas en el jefe); el cordón
                // visual real portal->jefe es el siguiente paso.
                serverLevel.playSound(null, this.blockPosition(),
                        SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.0F, 0.6F);
                serverLevel.sendParticles(ParticleTypes.SOUL,
                        this.getX(), this.getY() + 1.2D, this.getZ(), 20, 0.4D, 0.7D, 0.4D, 0.02D);
            } else {
                serverLevel.playSound(null, this.blockPosition(),
                        SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 0.6F, 1.4F);
            }
        }
    }

    /**
     * La cicatriz SOLO puede subir (ver llamada en setCorruptionLevel) --
     * a diferencia del nivel de Corrupción, esto no se resetea nunca en lo
     * que dure la pelea, ni siquiera si la Corrupción vuelve a 0.
     */
    private void setScarLevel(int newScarLevel) {
        newScarLevel = Mth.clamp(newScarLevel, 0, MAX_CORRUPTION);
        if (newScarLevel <= getScarLevel()) {
            return;
        }
        this.entityData.set(DATA_SCAR_LEVEL, newScarLevel);

        AttributeInstance armorAttr = this.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.removeModifier(SCAR_ARMOR_MODIFIER_ID);
            armorAttr.addPermanentModifier(new AttributeModifier(
                    SCAR_ARMOR_MODIFIER_ID,
                    "Cicatriz de Corrupcion",
                    newScarLevel * SCAR_ARMOR_BONUS_PER_LEVEL,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private void applyCorruptionSpeedModifier(int level) {
        AttributeInstance speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) {
            return;
        }
        speedAttr.removeModifier(CORRUPTION_SPEED_MODIFIER_ID);
        if (level >= CORRUPTION_SPEED_LEVEL) {
            speedAttr.addTransientModifier(new AttributeModifier(
                    CORRUPTION_SPEED_MODIFIER_ID,
                    "Corrupcion: velocidad",
                    CORRUPTION_SPEED_BONUS,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private void tickCorruptionRegen(int level) {
        if (level < CORRUPTION_REGEN_LEVEL) {
            corruptionRegenTicks = 0;
            return;
        }
        corruptionRegenTicks++;
        if (corruptionRegenTicks >= CORRUPTION_REGEN_INTERVAL_TICKS) {
            corruptionRegenTicks = 0;
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal((float) (this.getMaxHealth() * CORRUPTION_REGEN_FRACTION_OF_MAX_HP));
            }
        }
    }

    /** +X% de daño mientras Corrupción >= 1 -- se aplica en cada punto donde el jefe hace daño. */
    private double getCorruptionDamageMultiplier() {
        return getCorruptionLevel() >= 1 ? (1.0D + CORRUPTION_DAMAGE_BONUS) : 1.0D;
    }

    // Antes acá se le pegaba al nombre de la bossbar el texto de
    // "☠ Corrupción X" y "🩸x N" de Drenaje Vital. Ahora esos dos viven en
    // su propio HUD custom (NecroticKnightHudOverlay, cliente) así que el
    // nombre de la bossbar vanilla queda limpio -- de hecho la bossbar
    // vanilla ni siquiera se ve (ver bossEvent.setVisible(false) en el
    // constructor), pero mantenemos el nombre correcto igual por si algo
    // externo (comandos, mods de accesibilidad, F3) llega a leerlo.
    private void updateBossBarCorruptionText() {
        bossEvent.setName(this.getDisplayName());
    }

    // ---------------------------------------------------------------
    // Drenaje Vital -- ver el bloque grande de comentarios en las
    // constantes (arriba) para el diseño completo. Resumen: desbloqueo
    // PERMANENTE a la cicatriz (cuenta pase lo que pase con la Corrupción
    // actual después), pero la fuerza real escala con la Corrupción de
    // ESTE momento (getDrenajeTier()).
    // ---------------------------------------------------------------

    /** Colgado de hurt() -- CUALQUIER golpe recibido borra las cargas y reinicia el reloj de estancamiento. */
    private void onSuccessfulHitReceived() {
        ticksSinceKnightLastDamaged = 0;
        stallHealTicks = 0;
        setDrenajeCharges(0);
    }

    /** Colgado de cada punto donde el jefe le conecta un golpe al jugador -- suma 1 carga (tope DRENAJE_MAX_CHARGES). Sin modificador (package-private): NecroticLightningBeamEntity también lo llama. */
    void onKnightLandedHit() {
        if (getScarLevel() < DRENAJE_UNLOCK_SCAR_LEVEL) {
            return; // todavía no lo desbloqueó
        }
        setDrenajeCharges(getDrenajeCharges() + 1);
    }

    private void tickDrenajeVital() {
        ticksSinceKnightLastDamaged++;
        tickStallHeal();
    }

    /** Anti-estancamiento: 5 min sin recibir NADA de daño y el jefe empieza a curarse solo. Siempre activo. */
    private void tickStallHeal() {
        if (ticksSinceKnightLastDamaged < STALL_HEAL_DELAY_TICKS) {
            stallHealTicks = 0;
            return;
        }
        stallHealTicks++;
        if (stallHealTicks >= STALL_HEAL_INTERVAL_TICKS) {
            stallHealTicks = 0;
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal((float) (this.getMaxHealth() * STALL_HEAL_FRACTION_OF_MAX_HP));
            }
        }
    }

    /** 0 = Corrupción 0-II (básico), 1 = III-IV, 2 = V-VI (máximo) -- según la Corrupción ACTUAL, no la cicatriz. */
    private int getDrenajeTier() {
        int level = getCorruptionLevel();
        if (level >= 5) {
            return 2;
        }
        if (level >= 3) {
            return 1;
        }
        return 0;
    }

    private void setDrenajeCharges(int charges) {
        this.entityData.set(DATA_DRENAJE_CHARGES, Mth.clamp(charges, 0, DRENAJE_MAX_CHARGES));
        updateBossBarCorruptionText();
    }

    public int getDrenajeCharges() {
        return this.entityData.get(DATA_DRENAJE_CHARGES);
    }

    /** +X% de daño según las cargas actuales -- se multiplica CON getCorruptionDamageMultiplier(), no lo reemplaza. */
    private double getDrenajeDamageMultiplier() {
        int charges = getDrenajeCharges();
        if (charges <= 0) {
            return 1.0D;
        }
        return 1.0D + charges * DRENAJE_TIER_BONUS_PER_CHARGE[getDrenajeTier()];
    }

    /** Corrupción y Drenaje Vital combinados -- usar ESTE en los puntos donde el jefe hace daño, no los de arriba sueltos. */
    private double getTotalDamageMultiplier() {
        double enrage = isFinalPhase() ? FINAL_PHASE_DAMAGE_MULTIPLIER : 1.0D;
        return getCorruptionDamageMultiplier() * getDrenajeDamageMultiplier() * enrage;
    }

    /**
     * Busca un punto con espacio libre (2 bloques de aire, para que el
     * portal no quede clipeando con el piso/techo) cerca de (x, startY, z),
     * probando primero esa misma altura, y si no, subiendo y bajando de a
     * 1 bloque hasta 6 de rango. A propósito NO usa Heightmap: eso mide la
     * altura del terreno "desde el cielo", así que en una cueva o bajo un
     * techo de roca termina devolviendo la altura de la montaña de arriba
     * en vez de la del hueco donde está parado el jugador -- metía el
     * portal dentro de roca sólida.
     *
     * Devuelve null si no encuentra nada en ese rango vertical -- eso
     * significa que el punto (x,z) elegido está enterrado en la montaña
     * de lado a lado, no solo que la altura estaba mal, y hay que probar
     * OTRA dirección (ver findPortalSpawnPos).
     */
    private Double findPortalSpawnY(ServerLevel level, double x, double startY, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseY = Mth.floor(startY);
        int[] offsets = {0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6};

        for (int dy : offsets) {
            int y = baseY + dy;
            pos.set(Mth.floor(x), y, Mth.floor(z));
            if (level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())) {
                return y + 0.1;
            }
        }
        return null; // enterrado de lado a lado en este (x,z) -- probar otro ángulo
    }

    /**
     * Prueba hasta 8 direcciones distintas alrededor del jugador (no solo
     * alturas distintas en la MISMA dirección) hasta encontrar un punto
     * con espacio libre Y que respete PORTAL_MIN_SEPARATION contra
     * cualquier portal ya ubicado (existingPositions -- incluye tanto los
     * que ya estaban abiertos de fases anteriores como los que se van
     * abriendo en esta misma tanda, de este jugador o de otro). Si el
     * jugador está pegado a la pared de una montaña, una dirección random
     * puede apuntar derecho adentro de la roca -- ninguna altura cercana
     * ahí va a estar libre, hay que probar para otro lado.
     *
     * Si ningún intento cumple AMBAS condiciones (espacio libre + separación
     * mínima), nos quedamos con el mejor punto que haya tenido espacio
     * libre aunque quede más cerca de lo ideal de otro portal -- preferible
     * a no abrir portal, o a meterlo dentro de la roca. Si ni siquiera eso
     * hay (las 8 direcciones enterradas), usa la posición del jugador tal
     * cual -- ahí SÍ hay aire seguro, porque el jugador está parado justo
     * ahí.
     */
    /**
     * Prueba distintos ángulos alrededor del PUNTO OBJETIVO en el círculo
     * (baseAngle) hasta encontrar uno con espacio libre Y que respete
     * PORTAL_MIN_SEPARATION contra cualquier portal ya ubicado
     * (existingPositions). En cada reintento nos alejamos un poco más del
     * ángulo objetivo (7° para cada lado, alternando) en vez de tirar un
     * ángulo 100% al azar como antes -- así, si hay que esquivar terreno,
     * el portal se corre un poco pero sigue respetando la formación en
     * círculo, en vez de terminar en cualquier lado.
     *
     * Si ningún intento cumple ambas condiciones, nos quedamos con el
     * mejor punto que haya tenido espacio libre aunque quede más cerca de
     * lo ideal de otro portal. Si ni siquiera eso hay, usa la posición del
     * jefe tal cual -- ahí SÍ hay aire seguro.
     */
    private Vec3 findPortalSpawnPosAroundKnight(ServerLevel level, double baseAngle, List<Vec3> existingPositions) {
        Vec3 bestFreeButTooClose = null;

        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = baseAngle + Math.toRadians(attempt * 7.0D) * (attempt % 2 == 0 ? 1 : -1);
            double radius = PORTAL_SPAWN_MIN_RADIUS_FROM_KNIGHT
                    + this.random.nextDouble() * (PORTAL_SPAWN_MAX_RADIUS_FROM_KNIGHT - PORTAL_SPAWN_MIN_RADIUS_FROM_KNIGHT);

            double px = this.getX() + Math.cos(angle) * radius;
            double pz = this.getZ() + Math.sin(angle) * radius;
            Double py = findPortalSpawnY(level, px, this.getY(), pz);
            if (py == null) {
                continue; // enterrado en esa dirección, probar otro ángulo
            }

            Vec3 candidate = new Vec3(px, py, pz);
            if (isFarEnoughFromAll(candidate, existingPositions)) {
                return candidate;
            }
            if (bestFreeButTooClose == null) {
                bestFreeButTooClose = candidate; // guardamos el primero válido por si no aparece uno mejor
            }
        }

        if (bestFreeButTooClose != null) {
            return bestFreeButTooClose;
        }
        return new Vec3(this.getX(), this.getY(), this.getZ());
    }

    private static boolean isFarEnoughFromAll(Vec3 candidate, List<Vec3> existingPositions) {
        double minSeparationSqr = PORTAL_MIN_SEPARATION * PORTAL_MIN_SEPARATION;
        for (Vec3 existing : existingPositions) {
            if (candidate.distanceToSqr(existing) < minSeparationSqr) {
                return false;
            }
        }
        return true;
    }

    /**
     * Abre portalCount portales EN CÍRCULO alrededor del jefe (radio entre
     * PORTAL_SPAWN_MIN/MAX_RADIUS_FROM_KNIGHT), en vez de cerca de cada
     * jugador como antes -- repartidos parejo (360°/portalCount) con un
     * ángulo base al azar (para que la formación no siempre arranque
     * apuntando al mismo lado) y un poco de jitter para que no se vea
     * perfectamente geométrico.
     */
    private void openPortalsAroundKnight(int portalCount) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Arranca con las posiciones de los portales que ya estaban
        // abiertos de fases anteriores (todavía vivos) -- así los nuevos
        // tampoco se abren pegados a esos.
        List<Vec3> placedPositions = new ArrayList<>();
        for (UUID portalId : activePortals) {
            if (serverLevel.getEntity(portalId) instanceof TwilightPortalEntity existingPortal) {
                placedPositions.add(existingPortal.position());
            }
        }

        double baseAngle = this.random.nextDouble() * Math.PI * 2.0D;
        double angleStep = (Math.PI * 2.0D) / portalCount;

        for (int i = 0; i < portalCount; i++) {
            double targetAngle = baseAngle + angleStep * i
                    + Math.toRadians((this.random.nextDouble() - 0.5D) * PORTAL_RING_ANGLE_JITTER_DEGREES);

            Vec3 spawnPos = findPortalSpawnPosAroundKnight(serverLevel, targetAngle, placedPositions);
            placedPositions.add(spawnPos);

            TwilightPortalEntity portal = new TwilightPortalEntity(serverLevel, spawnPos);
            // Mirando HACIA EL JEFE -- ahora que los portales rodean al
            // knight en círculo en vez de aparecer cerca de cada jugador,
            // tiene más sentido visual que la "boca" del portal apunte
            // hacia él (de ahí sale/entra el cordón, ver
            // NecroticCorruptionTetherRenderer) en vez de hacia un jugador
            // puntual. Dato propio sincronizado (ver
            // TwilightPortalEntity.setFacingYaw), no moveTo/setYRot: esos
            // dependen del sistema de interpolación de MOVIMIENTO de
            // vanilla, que para una entidad que nunca se mueve queda
            // inestable en el cliente (cada capa temblaba).
            float yawToKnight = (float) (Mth.atan2(this.getZ() - spawnPos.z, this.getX() - spawnPos.x)
                    * (180.0 / Math.PI)) - 90.0F;
            portal.setFacingYaw(yawToKnight);
            portal.setOwnerKnightId(this.getId());
            serverLevel.addFreshEntity(portal);
            activePortals.add(portal.getUUID());

            // Línea de energía que "dibuja" el portal apareciendo -- el
            // efecto que se pidió portar del Last of Deepslate, disparado
            // acá (al ABRIRSE el portal), no en TwilightPortalEntity
            // cuando genera un mob.
            ParticleEmitterInfo portalOpenEffect = new ParticleEmitterInfo(PORTAL_OPEN_EFFECT);
            AAALevel.addParticle(serverLevel, portalOpenEffect
                    .position(spawnPos)
                    .scale(1.0F));
        }

        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    // ---------------------------------------------------------------
    // Muerte: apaga la luna y cierra los portales que queden abiertos
    // ---------------------------------------------------------------

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        closeOutBossFight();
    }

    @Override
    public void remove(RemovalReason reason) {
        closeOutBossFight();
        super.remove(reason);
    }

    private boolean closedOut = false;

    private void closeOutBossFight() {
        if (closedOut || isMirrorClone) {
            return;
        }
        closedOut = true;
        // removeAllPlayers() no manda nuestro paquete de HUD custom (solo
        // limpia la lista interna de ServerBossEvent), así que avisamos
        // aparte a cada jugador que lo tenía activo para que baje la barra.
        for (ServerPlayer p : bossEvent.getPlayers()) {
            ModNetworking.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> p),
                    new SyncNecroticKnightHudPacket(this.getId(), false));
        }
        bossEvent.removeAllPlayers();

        if (this.level() instanceof ServerLevel serverLevel) {
            TwilightNightHandler.setActive(serverLevel, false);

            // Corta el tema de batalla (con fade out), a todos igual que arriba.
            ModNetworking.CHANNEL.send(
                    PacketDistributor.ALL.noArg(),
                    new SyncNecroticKnightMusicPacket(this.getId(), false)
            );

            for (UUID portalId : activePortals) {
                if (serverLevel.getEntity(portalId) instanceof TwilightPortalEntity portal) {
                    portal.forceClose();
                }
            }
            // Por si el jefe muere justo en medio del ataque de portales
            // de wisps (ATTACK_GHOST_SUMMON) -- esos no viven en
            // activePortals (ver el comentario en spawnWispPortals sobre
            // por qué no: no deben dibujar el cordón de energía de fase),
            // así que sin esto quedarían huérfanos, flotando para
            // siempre. closeWispPortals() ya hace exactamente el chequeo
            // "existe todavía / es un TwilightPortalEntity" que hace
            // falta acá -- lo reusamos tal cual.
            closeWispPortals();
        }
    }

    // ---------------------------------------------------------------
    // Tracking de golpes recibidos (para el "pánico" de Guard & Slap)
    // ---------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Inmune a TODO tipo de proyectil (flechas, tridentes, bolas de nieve, proyectiles de otros
        // mods...). El camino normal lo cubre ModEvents#onProjectileImpact, que cancela el impacto
        // ANTES de llegar acá y hace que el proyectil lo atraviese. Esto cubre el resto: mods cuyos
        // proyectiles o rayos llaman a hurt() directo sin pasar por ProjectileImpactEvent.
        if (isProjectileDamage(source)) {
            if (!this.level().isClientSide && !isMirrorClone) {
                triggerArrowPhase();
            }
            return false;
        }
        if (isShieldBlocking() && !source.is(DamageTypeTags.BYPASSES_SHIELD)) {
            // Escudo en alto (entre GUARD_SLAP_COVER_TICK y GUARD_SLAP_GUARD_TICKS):
            // no recibe daño, pero SÍ suena el clank del escudo para que el
            // jugador sepa que su golpe fue absorbido y no un bug/lag.
            if (!this.level().isClientSide) {
                this.level().playSound(null, this.blockPosition(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            return false;
        }

        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide) {
            recentHitTicks.addLast(this.tickCount);
            trimOldHits();
            onSuccessfulHitReceived();
            startHurtFlash();
        }
        return result;
    }

    // Cierto solo en la ventana en la que el escudo YA terminó de subir y
    // TODAVÍA no arrancó el swing del manotazo (ver GUARD_SLAP_COVER_TICK /
    // GUARD_SLAP_GUARD_TICKS en tickGuardSlap()). Fuera de esa ventana --
    // subiendo el escudo, en pleno swing, o en cualquier otro ataque -- no
    // bloquea nada, es un jefe normal.
    private boolean isShieldBlocking() {
        return getAttackState() == ATTACK_GUARD_SLAP
                && attackTicksElapsed >= GUARD_SLAP_COVER_TICK
                && attackTicksElapsed < GUARD_SLAP_GUARD_TICKS;
    }

    private void trimOldHits() {
        while (!recentHitTicks.isEmpty() && this.tickCount - recentHitTicks.peekFirst() > HIT_TRACK_WINDOW_TICKS) {
            recentHitTicks.pollFirst();
        }
    }

    // Lo consulta NecroticKnightAttackGoal para acortar el cooldown de
    // Guard & Slap cuando lo están golpeando seguido.
    public boolean isBeingComboed() {
        trimOldHits();
        return recentHitTicks.size() >= HIT_TRACK_THRESHOLD;
    }

    // Lo consulta NecroticKnightAttackGoal para escalar todos los
    // cooldowns según la fase actual de la pelea.
    public double getAggressionCooldownMultiplier() {
        int index = Math.min(nextPhaseIndex, PHASE_COOLDOWN_MULTIPLIER.length - 1);
        return PHASE_COOLDOWN_MULTIPLIER[index];
    }

    private boolean isFinalPhase() {
        return nextPhaseIndex >= PHASE_HP_THRESHOLDS.length; // ya cruzó los 3 umbrales (<=25% de vida)
    }

    private float getWaveDamage() {
        float base = WAVE_DAMAGE; // el bonus de enrage ya está en getTotalDamageMultiplier()
        return (float) (base * getTotalDamageMultiplier());
    }

    private float getGuardSlapDamage() {
        float base = GUARD_SLAP_DAMAGE; // el bonus de enrage ya está en getTotalDamageMultiplier()
        return (float) (base * getTotalDamageMultiplier());
    }

    // ---------------------------------------------------------------
    // Proyectiles, orientación y puntería, robo de vida, límite de distancia
    // ---------------------------------------------------------------

    /** True para daño de CUALQUIER proyectil (flecha, tridente, bola de nieve, proyectiles de otros mods). */
    public static boolean isProjectileDamage(DamageSource source) {
        return source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile;
    }

    /** Frente del modelo en el plano horizontal. Usa yBodyRot: es lo que el jugador VE. */
    private Vec3 flatForward() {
        double rad = Math.toRadians(this.yBodyRot);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
    }

    private float yawTowards(Vec3 point) {
        double dx = point.x - this.getX();
        double dz = point.z - this.getZ();
        if (dx * dx + dz * dz < 1.0E-6D) {
            return this.yBodyRot;
        }
        return (float) (Mth.atan2(-dx, dz) * (180.0D / Math.PI));
    }

    /**
     * Setea los TRES yaw a la vez (lógica, cabeza y cuerpo visual). Si solo se toca setYRot(), el
     * cuerpo (yBodyRot, lo que renderiza el modelo) interpola por su cuenta y el modelo queda
     * mirando a otro lado que el golpe.
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

    /** Gira hacia un punto, como mucho maxDegrees por llamada, partiendo del yaw VISUAL. */
    private void faceTowardsSmooth(Vec3 point, float maxDegrees) {
        float delta = Mth.wrapDegrees(yawTowards(point) - this.yBodyRot);
        setAllYaw(this.yBodyRot + Mth.clamp(delta, -maxDegrees, maxDegrees));
    }

    private void pinYaw() {
        setAllYaw(this.yBodyRot);
    }

    /**
     * ¿Está el objetivo al alcance Y dentro del cono hacia donde MIRA el modelo? Horizontal (la
     * inclinación de la cabeza no cuenta) y con tolerancia de altura. Es lo que faltaba: antes el
     * thrust solo miraba la distancia.
     */
    private boolean isInMeleeArc(LivingEntity target, double reach, double coneDegrees) {
        if (Math.abs(target.getY() - this.getY()) > MELEE_HEIGHT_TOLERANCE) {
            return false;
        }
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double distSqr = dx * dx + dz * dz;
        if (distSqr > reach * reach) {
            return false;
        }
        if (distSqr < 0.09D) {
            return true; // encima del jefe: el ángulo no está definido, cuenta como golpe
        }
        double angleToTarget = Math.toDegrees(Math.atan2(dz, dx));
        double yaw = Mth.wrapDegrees(this.yBodyRot + 90.0D);
        return Math.abs(Mth.wrapDegrees(angleToTarget - yaw)) <= coneDegrees / 2.0D;
    }

    /**
     * Puntería de los golpes cuerpo a cuerpo: sigue al objetivo mientras prepara y, pasado cierto
     * tick, se COMPROMETE (cuerpo, cabeza y lógica quedan fijos hacia el mismo lado). El último
     * tramo es la ventana real para esquivar. Solo aplica al thrust y al guard slap: el charge, el
     * rayo y el vanish manejan su propia orientación.
     */
    private void tickAimTracking(int state) {
        int trackUntil;
        float degreesPerTick;
        switch (state) {
            case ATTACK_THRUST -> {
                trackUntil = THRUST_TRACK_UNTIL_TICK;
                degreesPerTick = THRUST_TRACK_DEGREES_PER_TICK;
            }
            case ATTACK_GUARD_SLAP -> {
                trackUntil = GUARD_SLAP_TRACK_UNTIL_TICK;
                degreesPerTick = GUARD_SLAP_TRACK_DEGREES_PER_TICK;
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

    /**
     * Chance de encadenar un segundo thrust tras uno que conectó. ANTES solo existía en la fase
     * final (40 %). Ahora también desde el 50 % de vida, a la mitad de esa chance: hace la fase
     * media más peligrosa sin tocar los cooldowns.
     */
    private double getThrustComboChance() {
        if (isFinalPhase()) {
            return ENRAGE_COMBO_CHANCE;
        }
        if (nextPhaseIndex >= 2) {
            return ENRAGE_COMBO_CHANCE * 0.5D;
        }
        return 0.0D;
    }

    /**
     * Robo de vida NUEVO: se cura una fracción del daño REAL (ya mitigado por armadura) que le hace
     * a un jugador. Lo llama ModEvents#onLivingDamage, así cubre todos los ataques a la vez sin
     * tocar cada uno. Base (5 %) solo desde que se desbloquea Drenaje Vital; en enrage 20 %. Con
     * tope por golpe para que el rayo canalizado no lo cure de más.
     */
    public void applyLifesteal(float damageDealt) {
        if (isMirrorClone || damageDealt <= 0.0F || !this.isAlive()) {
            return;
        }
        double fraction;
        if (isFinalPhase()) {
            fraction = LIFESTEAL_ENRAGED_FRACTION;
        } else if (getScarLevel() >= DRENAJE_UNLOCK_SCAR_LEVEL) {
            fraction = LIFESTEAL_BASE_FRACTION;
        } else {
            return;
        }
        float heal = (float) Math.min(damageDealt * fraction,
                this.getMaxHealth() * LIFESTEAL_MAX_HEAL_FRACTION_OF_MAX_HP);
        if (heal > 0.0F && this.getHealth() < this.getMaxHealth()) {
            this.heal(heal);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SOUL,
                        this.getX(), this.getY() + 1.6D, this.getZ(), 2, 0.3D, 0.4D, 0.3D, 0.02D);
            }
        }
    }

    /**
     * Robo de vida específico del rayo -- ver las constantes LIGHTNING_LIFESTEAL_* más arriba
     * para el porqué. Lo llama NecroticLightningBeamEntity#applyDamage tras cada impacto que
     * conecta, ADEMÁS del robo de vida general que ya dispara ModEvents#onLivingDamage para
     * cualquier ataque (los dos ocurren a la vez, uno no reemplaza al otro).
     */
    public void applyLightningLifesteal(LivingEntity target) {
        if (isMirrorClone || !this.isAlive()) {
            return;
        }
        float heal = (float) Math.min(
                target.getMaxHealth() * LIGHTNING_LIFESTEAL_TARGET_FRACTION,
                this.getMaxHealth() * LIGHTNING_LIFESTEAL_KNIGHT_CAP_FRACTION);
        if (heal > 0.0F && this.getHealth() < this.getMaxHealth()) {
            this.heal(heal);
        }
    }

    // --- Límite de distancia al punto de origen ---

    public void setHomePos(BlockPos pos) {
        this.homePos = pos.immutable();
        this.restrictTo(this.homePos, (int) LEASH_CHASE_RADIUS);
    }

    @Nullable
    public BlockPos getHomePos() {
        return this.homePos;
    }

    /** ¿Está este objetivo dentro de la zona que el jefe está dispuesto a defender/perseguir? */
    public boolean isInsideChaseArea(LivingEntity entity) {
        if (this.homePos == null) {
            return true; // todavía no fijó su origen (primer tick): no filtra nada
        }
        double dx = entity.getX() - (this.homePos.getX() + 0.5D);
        double dz = entity.getZ() - (this.homePos.getZ() + 0.5D);
        return dx * dx + dz * dz <= LEASH_CHASE_RADIUS * LEASH_CHASE_RADIUS;
    }

    private void tickHomeLeash() {
        if (this.homePos == null) {
            setHomePos(this.blockPosition()); // primer tick: donde apareció
        }
        Vec3 home = Vec3.atBottomCenterOf(this.homePos);
        double dx = this.getX() - home.x;
        double dz = this.getZ() - home.z;
        double distSqr = dx * dx + dz * dz;

        // Si el objetivo se fue fuera del área (o alguien le pegó desde lejos), suelta la
        // persecución. El predicado del target goal evita que lo vuelva a elegir de inmediato.
        LivingEntity target = this.getTarget();
        if (target != null && !isInsideChaseArea(target)) {
            this.setTarget(null);
        }

        if (distSqr > LEASH_HARD_RADIUS * LEASH_HARD_RADIUS) {
            teleportBackHome(home);
            return;
        }

        if (this.getTarget() == null && !isAttacking() && distSqr > LEASH_RETURN_TRIGGER * LEASH_RETURN_TRIGGER) {
            if (--leashRepathCooldown <= 0) {
                leashRepathCooldown = LEASH_REPATH_TICKS;
                this.getNavigation().moveTo(home.x, home.y, home.z, 1.0D);
            }
        }
    }

    /** Se pasó del radio duro (empujado, dash...): vuelve de golpe con un efecto. */
    private void teleportBackHome(Vec3 home) {
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX(), this.getY() + 1.0D, this.getZ(), 25, 0.5D, 0.8D, 0.5D, 0.06D);
            serverLevel.playSound(null, this.blockPosition(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
        this.moveTo(home.x, home.y, home.z, this.getYRot(), this.getXRot());
        this.setTarget(null);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.homePos != null) {
            tag.putInt("NanookHomeX", this.homePos.getX());
            tag.putInt("NanookHomeY", this.homePos.getY());
            tag.putInt("NanookHomeZ", this.homePos.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Se está CARGANDO una entidad que ya existía (guardado en disco, chunk recargado): no es
        // una aparición nueva, así que la animación de spawn no debe reproducirse.
        this.entityData.set(DATA_SPAWN_ANIM_TICKS_REMAINING, 0);
        if (tag.contains("NanookHomeX")) {
            setHomePos(new BlockPos(tag.getInt("NanookHomeX"), tag.getInt("NanookHomeY"), tag.getInt("NanookHomeZ")));
        }
    }

    // ---- Sonidos ----

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.NECROKNIGHT_IDLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        // getVoicePitch() de LivingEntity ya aplica una variación de pitch
        // (±0.2 sobre 1.0) automáticamente cada vez que se llama a este
        // sonido, así que aunque solo hay UN archivo de hurt no suena
        // idéntico dos veces seguidas -- no hace falta tocar nada más acá.
        return ModSounds.NECROKNIGHT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        // 3 variantes en sounds.json (death_1/2/3) -- vanilla elige una al
        // azar, y encima le suma la misma variación de pitch automática
        // que el hurt.
        return ModSounds.NECROKNIGHT_DEATH.get();
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementPredicate));
        controllers.add(new AnimationController<>(this, "attack", 0, this::attackPredicate));
        controllers.add(new AnimationController<>(this, "death", 0, this::deathPredicate));
        // PEDIDO: usar la animación "spawn" que ya estaba en el set pero sin reproducirse nunca.
        controllers.add(new AnimationController<>(this, "spawn_anim", 0, this::spawnAnimPredicate));
    }

    private PlayState spawnAnimPredicate(AnimationState<NecroticKnightEntity> state) {
        if (!isSpawningIn()) {
            return PlayState.STOP;
        }
        state.getController().setAnimation(SPAWN_ANIM);
        return PlayState.CONTINUE;
    }

    private PlayState movementPredicate(AnimationState<NecroticKnightEntity> state) {
        if (isAttacking() || this.isDeadOrDying() || isSpawningIn()) {
            return PlayState.STOP;
        }
        state.getController().setAnimation(reallyMoving ? WALK : IDLE);
        return PlayState.CONTINUE;
    }

    private PlayState attackPredicate(AnimationState<NecroticKnightEntity> state) {
        if (isSpawningIn()) {
            lastAttackStateAnimated = ATTACK_NONE;
            return PlayState.STOP;
        }
        int attackState = getAttackState();
        if (attackState == ATTACK_NONE) {
            lastAttackStateAnimated = ATTACK_NONE;
            return PlayState.STOP;
        }

        RawAnimation animation = switch (attackState) {
            case ATTACK_THRUST -> THRUST_ANIM;
            case ATTACK_CHARGE -> CHARGE_ANIM;
            case ATTACK_GHOST_SUMMON -> GHOST_SUMMON_ANIM;
            case ATTACK_WAVE -> WAVE_ANIM;
            case ATTACK_GUARD_SLAP -> GUARD_SLAP_ANIM;
            case ATTACK_LIGHTNING -> LIGHTNING_ANIM;
            case ATTACK_VANISH -> VANISH_ANIM;
            default -> IDLE;
        };

        if (lastAttackStateAnimated != attackState) {
            state.getController().forceAnimationReset();
            state.getController().setAnimation(animation);
            lastAttackStateAnimated = attackState;
        }
        return PlayState.CONTINUE;
    }

    private PlayState deathPredicate(AnimationState<NecroticKnightEntity> state) {
        if (this.isDeadOrDying()) {
            state.getController().setAnimation(DEATH);
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}