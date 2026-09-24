package com.nanookmod.entity;

import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModEntities;
import mod.chloeprime.aaaparticles.api.common.AAALevel;
import mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
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
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Portal de invasión que abre el NecroticKnight en 75/50/25% de vida.
 * Modelo real de GeckoLib (ver charla del bbmodel): la base (star +
 * ring_effects + lightball) es un modelo con animación de huesos propia
 * ("idle", en loop), más 3 capas extra (middle_effect chico/mediano/
 * grande) dibujadas por TwilightPortalRenderer con rotación real por
 * código -- ver TwilightPortalRenderer.
 *
 * Reglas (tal como las definiste):
 *   - Máximo 5 mobs vivos generados por este portal a la vez.
 *   - Cuando uno de esos muere, pasan 30s hasta que sale el próximo.
 *   - Los mobs que genera son más fuertes que los normales (+40% vida,
 *     +25% daño).
 *   - Se cierra a golpes (tiene vida propia, cualquier daño cuenta) o
 *     cuando el jefe muere (forceClose()).
 */
public class TwilightPortalEntity extends Monster implements GeoEntity {

    private static final int MAX_ALIVE_SPAWNS = 3; // antes 5 -- con 3 forman triángulo, con 4 cuadrado (ver formationBaseAngle)
    private static final int RESPAWN_COOLDOWN_TICKS = 20 * 30; // 30s
    private static final double SPAWN_HEALTH_MULTIPLIER = 1.4D;
    private static final double SPAWN_DAMAGE_MULTIPLIER = 1.25D;
    // Un poco más separados que antes (1.2) para que la formación se note.
    private static final double SPAWN_FORMATION_RADIUS = 1.8D;

    // Efecto Effekseer (AAA Particles) que reemplaza a ParticleTypes.REVERSE_PORTAL
    // en spawnReinforcedMob(). Portado del "Last of Deepslate" de Archaion
    // (lod_archaic_summon.efkefc) y recoloreado de azul a verde para matchear
    // TETHER_PARTICLE_COLOR (#62f694). Ver assets/nanookmod/effeks/.
    private static final ResourceLocation NECROTIC_PORTAL_SUMMON_EFFECT =
            new ResourceLocation(NanookMod.MOD_ID, "necrotic_portal_summon");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    // UUID del mob -> qué "punta" de la formación ocupa (0..MAX_ALIVE_SPAWNS-1).
    // Antes era una simple lista de UUIDs con posición 100% al azar cada
    // vez; ahora cada mob nuevo ocupa la primera punta libre, así con 3
    // vivos a la vez arman un triángulo alrededor del portal (o cuadrado
    // con 4), en vez de aparecer todos amontonados en cualquier lado.
    private final Map<UUID, Integer> aliveSpawnSlots = new LinkedHashMap<>();
    private int respawnCooldown = 40; // primer mob sale rápido, no a los 30s
    // true para los portales de invocación de wisps de NecroticKnightEntity
    // (ver spawnWispPortals/spawnSingleWispPortal) -- esos NO deben generar
    // refuerzos (Skeletal Warrior/Mage/Wisp) por su cuenta, son un ataque
    // de combate de vida cortísima, no un portal de invasión de fase. Con
    // los tiempos actuales (se cierran bastante antes de que
    // respawnCooldown llegue a 0) nunca se dispararía igual, pero mejor
    // no depender de esa coincidencia de timing si en algún momento se
    // ajustan las constantes de uno de los dos sistemas.
    private boolean reinforcementsDisabled = false;

    public void disableReinforcements() {
        this.reinforcementsDisabled = true;
    }
    // Rotación propia de ESTE portal para su formación (fija, elegida una
    // sola vez al crearse) -- así el triángulo/cuadrado no siempre apunta
    // para el mismo lado en todos los portales que abre el jefe.
    private final double formationBaseAngle;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    // Yaw fijo de "hacia dónde mira" el portal, sincronizado nosotros
    // mismos como dato propio de la entidad. A propósito NO usamos
    // setYRot()/moveTo(): esos dependen del sistema de interpolación de
    // MOVIMIENTO de vanilla (pensado para mobs que caminan), y para una
    // entidad noPhysics que nunca se mueve ese sistema queda inestable en
    // el cliente -- se veía como si cada capa temblara/girara para
    // cualquier lado. Con un dato propio, fijo, sincronizado una sola vez
    // al spawnear, no hay ninguna interpolación de por medio.
    private static final EntityDataAccessor<Float> DATA_FACING_YAW =
            SynchedEntityData.defineId(TwilightPortalEntity.class, EntityDataSerializers.FLOAT);

    // Igual que DATA_FACING_YAW pero para la inclinación (arriba/abajo) --
    // lo necesitan los portales flotantes de invocación de wisps (ver
    // NecroticKnightEntity#spawnWispPortals), que aparecen en el aire
    // mirando hacia abajo, hacia el jugador. Los portales de fase
    // (openPortalsAroundKnight) simplemente no lo tocan y se quedan en 0
    // (mirando derecho, como siempre).
    private static final EntityDataAccessor<Float> DATA_FACING_PITCH =
            SynchedEntityData.defineId(TwilightPortalEntity.class, EntityDataSerializers.FLOAT);

    // El knight que abrió este portal -- lo necesita el cliente para saber
    // hacia dónde dibujar el cordón de energía (ver
    // NecroticCorruptionTetherRenderer). -1 = sin dueño (no debería pasar
    // en la práctica, todo portal lo abre un knight, pero por las dudas).
    private static final EntityDataAccessor<Integer> DATA_OWNER_KNIGHT_ID =
            SynchedEntityData.defineId(TwilightPortalEntity.class, EntityDataSerializers.INT);

    public void setFacingYaw(float yawDegrees) {
        this.entityData.set(DATA_FACING_YAW, yawDegrees);
    }

    public float getFacingYaw() {
        return this.entityData.get(DATA_FACING_YAW);
    }

    public void setFacingPitch(float pitchDegrees) {
        this.entityData.set(DATA_FACING_PITCH, pitchDegrees);
    }

    public float getFacingPitch() {
        return this.entityData.get(DATA_FACING_PITCH);
    }

    public void setOwnerKnightId(int entityId) {
        this.entityData.set(DATA_OWNER_KNIGHT_ID, entityId);
    }

    public int getOwnerKnightId() {
        return this.entityData.get(DATA_OWNER_KNIGHT_ID);
    }

    // --- Animación de aparecer/desaparecer (agrandarse/achicarse) ---
    // El "crecer" al aparecer no necesita nada sincronizado -- el
    // renderer lo calcula solo con tickCount (cuántos ticks lleva
    // existiendo desde que este cliente la vio por primera vez), como
    // cualquier otra animación de intro. El "achicarse" al morir SÍ
    // necesita este dato: en vez de discard() instantáneo, esperamos
    // DEATH_ANIM_TICKS mostrando la cuenta regresiva acá, y el cliente la
    // lee para achicar el modelo hasta 0 antes de que la entidad
    // desaparezca de verdad -- ver startDying()/tick() más abajo y
    // TwilightPortalRenderer#computeScale.
    public static final int SPAWN_ANIM_TICKS = 12;  // 0.6s
    public static final int DEATH_ANIM_TICKS = 12;  // 0.6s

    private static final EntityDataAccessor<Integer> DATA_DYING_TICKS_REMAINING =
            SynchedEntityData.defineId(TwilightPortalEntity.class, EntityDataSerializers.INT);

    /** true desde que se rompió/el jefe murió hasta que termina de achicarse (ver DEATH_ANIM_TICKS). */
    public boolean isDying() {
        return this.entityData.get(DATA_DYING_TICKS_REMAINING) >= 0;
    }

    public int getDyingTicksRemaining() {
        return this.entityData.get(DATA_DYING_TICKS_REMAINING);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FACING_YAW, 0.0F);
        this.entityData.define(DATA_FACING_PITCH, 0.0F);
        this.entityData.define(DATA_OWNER_KNIGHT_ID, -1);
        this.entityData.define(DATA_DYING_TICKS_REMAINING, -1);
    }

    public TwilightPortalEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.formationBaseAngle = this.random.nextDouble() * Math.PI * 2.0D;
    }

    public TwilightPortalEntity(Level level, Vec3 pos) {
        this(ModEntities.TWILIGHT_PORTAL.get(), level);
        this.setPos(pos.x, pos.y, pos.z);
    }

    // ---------------------------------------------------------------
    // GeckoLib: sin animación de huesos (el modelo es estático), toda
    // la "animación" es el swap de frames de las 3 capas -- mismo
    // enfoque que SkeletalWarriorAuraVfx.
    // ---------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // La base (star + ring_effects + lightball) sí tiene animación de
        // huesos real -- gira, orbita, pulsa -- y ANTES no se reproducía
        // nunca porque acá no había ningún controller. Es en loop porque
        // el portal está siempre "activo" mientras la entidad exista.
        controllers.add(new AnimationController<>(this, "idle", 0, this::idlePredicate));
    }

    private PlayState idlePredicate(AnimationState<TwilightPortalEntity> state) {
        state.getController().setAnimation(IDLE);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    protected void registerGoals() {
        // Sin IA -- es un objeto estático que solo spawnea mobs y se puede
        // romper a golpes, no persigue ni ataca a nadie.
    }

    // Sin estos dos overrides, el portal quedaba sujeto al despawn aleatorio
    // de vanilla para monstruos (Mob#checkDespawn(): una chance random cada
    // tick si no hay jugadores cerca, o si el jugador está lejos y el mob
    // lleva rato "inactivo") -- por eso algunos desaparecían solos aunque el
    // jefe siguiera vivo. Su ciclo de vida real lo controla por completo
    // NecroticKnightEntity (abre/cierra portales según la pelea), nunca el
    // despawn de vanilla.
    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public void checkDespawn() {
        // No-op a propósito -- ver comentario de arriba.
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
        // No lo empuja nada -- ni entidades ni explosiones.
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // El golpe (hurt()) aplica knockback por un camino aparte de
        // push() -- sin esto, el portal igual se corría de lugar al
        // pegarle, aunque isPushable()/push() estuvieran bloqueados.
    }

    /**
     * Inmune a TODO tipo de proyectil. El camino normal lo cubre ModEvents#onProjectileImpact
     * (cancela el impacto y el proyectil atraviesa el portal); esto cubre a los mods cuyos
     * proyectiles llaman a hurt() directo. Los golpes cuerpo a cuerpo y la magia siguen
     * funcionando: el portal se cierra "a golpes", como decía su diseño.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (NecroticKnightEntity.isProjectileDamage(source)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    // Sin esto, al recibir daño sonaba el gruñido genérico de vanilla
    // (GENERIC_HURT) -- no encaja con un constructo mágico. Un "shink" de
    // energía drenándose se siente más apropiado que un mob gruñendo.
    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.RESPAWN_ANCHOR_DEPLETE.value();
    }

    @Override
    public void tick() {
        super.tick();

        // hurtTime es un campo público directo en LivingEntity (no hay
        // getter para overridear) -- el pipeline de render lo lee tal
        // cual para el tinte rojo/blanco de "golpeado". Reseteándolo acá,
        // el portal nunca lo muestra. NO tocamos deathTime (ver die()
        // abajo -- al morir se descarta directo, sin pasar por el conteo
        // de deathTime de vanilla).
        this.hurtTime = 0;

        if (this.level().isClientSide) {
            return;
        }

        if (isDying()) {
            int remaining = getDyingTicksRemaining();
            if (remaining <= 0) {
                this.discard();
            } else {
                this.entityData.set(DATA_DYING_TICKS_REMAINING, remaining - 1);
            }
            return; // ya no genera mobs ni partículas de cordón mientras se achica
        }

        cleanupDeadSpawns();

        if (respawnCooldown > 0) {
            respawnCooldown--;
        } else if (!reinforcementsDisabled && aliveSpawnSlots.size() < MAX_ALIVE_SPAWNS) {
            spawnReinforcedMob();
        }

        tickTetherParticles();
    }

    // Humo verde saliendo del portal por donde arranca el cordón hacia el
    // jefe (ver NecroticCorruptionTetherRenderer, que dibuja el cordón en
    // sí del lado cliente) -- esto es aparte, server-side, para que le
    // llegue a todos los que estén viendo el portal sin depender de que
    // cada cliente calcule su propio timing de partículas.
    private static final int TETHER_PARTICLE_INTERVAL_TICKS = 5;
    private static final Vector3f TETHER_PARTICLE_COLOR = new Vector3f(0.3843F, 0.9647F, 0.5804F); // #62f694

    private void tickTetherParticles() {
        // Desactivado a pedido -- el humo verde saliendo del portal ya no
        // se dibuja. Para volver a activarlo, descomentar el cuerpo de
        // abajo.
        /*
        if (getOwnerKnightId() < 0 || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.tickCount % TETHER_PARTICLE_INTERVAL_TICKS != 0) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(TETHER_PARTICLE_COLOR, 1.3F);
        serverLevel.sendParticles(dust,
                this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(),
                4, 0.25D, 0.35D, 0.25D, 0.01D);
        */
    }

    private void cleanupDeadSpawns() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> it = aliveSpawnSlots.entrySet().iterator();
        boolean anyDied = false;
        while (it.hasNext()) {
            UUID id = it.next().getKey();
            if (!(serverLevel.getEntity(id) instanceof Mob mob) || !mob.isAlive()) {
                it.remove();
                anyDied = true;
            }
        }
        if (anyDied && respawnCooldown <= 0) {
            respawnCooldown = RESPAWN_COOLDOWN_TICKS;
        }
    }

    /**
     * Cuántos de los mobs que generó ESTE portal siguen vivos Y ya llevan
     * al menos graceTicks vivos -- lo usa NecroticKnightEntity para el
     * sistema de Corrupción (ver tickCorruption/countPressuringSources
     * ahí). mob.tickCount cuenta ticks desde que la entidad se creó, así
     * que sirve tal cual como "tiempo que lleva vivo este mob puntual" sin
     * tener que trackear nada aparte acá.
     */
    public int countPressuringMobs(ServerLevel serverLevel, int graceTicks) {
        int count = 0;
        for (UUID id : aliveSpawnSlots.keySet()) {
            if (serverLevel.getEntity(id) instanceof Mob mob && mob.isAlive() && mob.tickCount >= graceTicks) {
                count++;
            }
        }
        return count;
    }

    private void spawnReinforcedMob() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Mob mob = createRandomMob(serverLevel);
        if (mob == null) {
            return;
        }

        // Primera punta libre de la formación (ver formationBaseAngle) en
        // vez de un ángulo 100% al azar -- así, con varios mobs vivos a la
        // vez, quedan repartidos en triángulo/cuadrado/etc en vez de
        // amontonados.
        int slot = findFreeFormationSlot();
        double angle = formationBaseAngle + slot * (Math.PI * 2.0D / MAX_ALIVE_SPAWNS);
        double spawnX = this.getX() + Math.cos(angle) * SPAWN_FORMATION_RADIUS;
        double spawnZ = this.getZ() + Math.sin(angle) * SPAWN_FORMATION_RADIUS;
        mob.moveTo(spawnX, this.getY(), spawnZ, this.random.nextFloat() * 360.0F, 0.0F);

        var maxHealthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(maxHealthAttr.getBaseValue() * SPAWN_HEALTH_MULTIPLIER);
            mob.setHealth(mob.getMaxHealth());
        }
        var damageAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() * SPAWN_DAMAGE_MULTIPLIER);
        }

        serverLevel.addFreshEntity(mob);
        aliveSpawnSlots.put(mob.getUUID(), slot);
        respawnCooldown = (aliveSpawnSlots.size() < MAX_ALIVE_SPAWNS) ? 0 : -1;

        ParticleEmitterInfo summonEffect = new ParticleEmitterInfo(NECROTIC_PORTAL_SUMMON_EFFECT);
        AAALevel.addParticle(serverLevel, summonEffect
                .position(new Vec3(spawnX, this.getY(), spawnZ))
                .scale(0.2F));
        serverLevel.playSound(null, spawnX, this.getY(), spawnZ,
                SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 1.0F, 0.6F);
    }

    private int findFreeFormationSlot() {
        for (int slot = 0; slot < MAX_ALIVE_SPAWNS; slot++) {
            if (!aliveSpawnSlots.containsValue(slot)) {
                return slot;
            }
        }
        return 0; // no debería pasar (solo se llama habiendo lugar), pero por las dudas
    }

    @Nullable
    private Mob createRandomMob(ServerLevel serverLevel) {
        int roll = this.random.nextInt(3);
        return switch (roll) {
            case 0 -> ModEntities.SKELETAL_WARRIOR.get().create(serverLevel);
            case 1 -> ModEntities.SKELETAL_MAGE.get().create(serverLevel);
            default -> ModEntities.TWILIGHT_WISP.get().create(serverLevel);
        };
    }

    /**
     * Lo llama NecroticKnightEntity cuando el jefe muere - arranca el
     * achicamiento (ver startDying()) en vez de descartar de una.
     */
    public void forceClose() {
        if (!this.level().isClientSide) {
            startDying();
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        // NO llamamos a super.die(): vanilla espera 20 ticks de deathTime
        // antes de recién ahí sacar la entidad, y nosotros dejamos de
        // incrementar/mostrar deathTime (ver tick()) para que no haga la
        // animación de "cae de lado" -- tenemos la nuestra propia
        // (achicarse, ver startDying()/DEATH_ANIM_TICKS).
        if (!this.level().isClientSide) {
            startDying();
        }
    }

    /** Sonido/partículas de cierre YA (como antes), pero el discard() real queda para cuando termine de achicarse. */
    private void startDying() {
        if (isDying()) {
            return; // ya se estaba achicando, no reiniciamos el contador
        }
        this.entityData.set(DATA_DYING_TICKS_REMAINING, DEATH_ANIM_TICKS);
        playCloseEffects();
    }

    private void playCloseEffects() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    this.getX(), this.getY() + 1.0D, this.getZ(), 30, 0.4D, 0.6D, 0.4D, 0.05D);
            // Los dos juntos a pedido: el chime del ojo de ender (sella el
            // portal) + un tono grave descendente por debajo (como si algo
            // se apagara), en vez de uno solo.
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.HOSTILE, 1.5F, 1.4F);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }
}