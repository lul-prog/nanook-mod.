package com.nanookmod.entity;

import com.nanookmod.network.ModNetworking;
import com.nanookmod.network.SyncLightningBeamSoundPacket;
import com.nanookmod.registry.ModEffects;
import com.nanookmod.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * El rayo del Necrotic Knight (Corrupción IV), como entidad real y
 * separada -- inspirado directamente en cómo arma el suyo Beyond the
 * Abyss (EntityAbsBeam/EntityGuardianLaser, mob "Nameless Guardian"):
 *
 * ANTES: la posición/dirección del rayo se calculaba con una fórmula del
 * lado SERVIDOR (para el daño) y con OTRA del lado CLIENTE (bone de
 * GeckoLib + un tracker de ticks propio, para el dibujado) -- dos
 * cuentas separadas que tenían que coincidir a mano, y que en la
 * práctica NUNCA terminaban de coincidir del todo.
 *
 * AHORA: el rayo es una entidad de Minecraft como cualquier otra.
 * Minecraft YA sabe sincronizar e interpolar la posición y la rotación
 * de cualquier entidad automáticamente. Cada tick, {@link #followOwner}
 * copia la posición/rotación del jefe con un offset fijo simple
 * (moveTo), server-side únicamente; el cliente recibe esa
 * posición/rotación YA interpolada por el sistema estándar de Minecraft.
 *
 * El PUNTO FINAL del rayo (dónde termina, tanto para el daño como para
 * el dibujado) ahora se calcula con un RAYCAST DE VERDAD
 * ({@link #computeBeamEnd}) -- bloques sólidos Y entidades, lo que se
 * cruce primero corta el rayo ahí. Antes el rayo siempre "medía" su
 * alcance máximo fijo y solo pegaba si algo pasaba cerca de esa línea
 * infinita -- por eso podía "atravesar" al jugador o quedar visualmente
 * flotando en el aire si no había nada en el camino. Con el raycast, el
 * rayo VISUALMENTE termina exactamente donde pega (pared, piso, o el
 * jugador) -- y como ya dibujamos un disco brillante en la punta del
 * rayo (ver NecroticLightningRenderer), eso funciona solo como un
 * "destello de impacto" en el lugar real, sin código extra.
 *
 * El daño (servidor, en {@link #applyDamage}) y el dibujado
 * (NecroticLightningRenderer, cliente) hacen CADA UNO su propio raycast,
 * pero con el MISMO método/misma entrada (posición+rotación de esta
 * entidad, que están sincronizadas) -- en la enorme mayoría de los casos
 * dan el mismo resultado, igual que pasa con cualquier otra colisión de
 * Minecraft calculada en los dos lados (flechas, por ejemplo).
 */
public class NecroticLightningBeamEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_OWNER_ID =
            SynchedEntityData.defineId(NecroticLightningBeamEntity.class, EntityDataSerializers.INT);

    // Offset fijo desde la posición del jefe -- mismo criterio que
    // EntityAbsBeam#updateWithEntity (un wOffset hacia adelante según el
    // yaw, un hOffset de altura), pero con un empuje extra hacia la
    // DERECHA porque la espada está en la mano derecha del jefe. Tiene
    // que salir de la punta de la espada, no del pecho -- si al probarlo
    // no cae exacto, estos son los 3 números a tocar:
    //   - FORWARD_OFFSET: + = más lejos hacia adelante del jefe.
    //   - RIGHT_OFFSET:   + = más hacia SU derecha / - = hacia su izquierda.
    //   - HEIGHT_OFFSET_FRACTION: fracción de getBbHeight() -- + = más alto.
    private static final double FORWARD_OFFSET = 0.9D;
    private static final double RIGHT_OFFSET = 0.55D;
    private static final double HEIGHT_OFFSET_FRACTION = 0.82D; // fracción de getBbHeight() del jefe -- ajustado en el juego

    public static final double RANGE = NecroticKnightEntity.LIGHTNING_RANGE;
    // Alcance REAL del raycast (daño + partículas de impacto), separado de
    // RANGE. RANGE (20 bloques) es solo el rango que usa el jefe para
    // DECIDIR si usar este ataque (ver NecroticKnightAttackGoal) -- una vez
    // que el canalizado ya arrancó, si el objetivo se aleja más de eso (o
    // el rayo termina pegando en una pared lejana), el rayo tiene que
    // seguir funcionando igual. Antes computeGrowthRange usaba RANGE como
    // techo DURO para siempre (no solo durante el crecimiento inicial), y
    // por eso ni el daño ni las partículas de impacto aparecían pasados
    // los 20 bloques.
    private static final double MAX_HIT_DISTANCE = 300.0D; // "prácticamente ilimitado" para efectos de combate
    // Margen de "grosor" del rayo para el raycast contra entidades --
    // cuánto se infla la hitbox de cada candidato antes de probar si el
    // rayo la cruza. No es un radio de daño en área (eso ya no existe,
    // el rayo pega a UNA sola cosa, la que lo corta) -- es solo margen de
    // generosidad para no tener que apuntar pixel-perfecto.
    private static final double HIT_MARGIN = 0.75D;
    private static final int DAMAGE_INTERVAL_TICKS = 10; // 0.5s -- pega apenas nace y cada 0.5s después

    // --- Rotura de escudo por bloqueos repetidos ---
    // Pedido explícito: 3 golpes del rayo bloqueados con escudo lo rompen.
    // A diferencia de Guard Slap/slash_loop (golpes puntuales, un chequeo
    // c/u), acá hace falta CONTAR bloqueos a lo largo de todo el
    // canalizado (hasta ~11 pulsos en los 5.4s que dura, ver
    // DAMAGE_INTERVAL_TICKS y LIGHTNING_ACTIVE_CHANNEL_TICKS en el jefe) --
    // por objetivo, por si el rayo llegase a cambiar de blanco a mitad de
    // camino. Vive y muere con esta instancia del rayo (no persiste entre
    // ataques -- cada Lightning nuevo es una entidad nueva, arranca en 0).
    private static final int SHIELD_DISABLE_AFTER_BLOCKS = 3;
    private final Map<UUID, Integer> shieldBlockedHitsPerTarget = new HashMap<>();

    // --- Maldición Necrótica (nanookmod:corruption) aplicada por el rayo ---
    // El rayo pega cada 0.5s (DAMAGE_INTERVAL_TICKS), pero el nivel de la
    // maldición solo SUBE cada CORRUPTION_LEVEL_INTERVAL_TICKS -- así que no todo golpe de daño escala el
    // nivel, solo 1 de cada 4. Se controla con una marca de tiempo guardada
    // en la persistentData del objetivo (independiente del propio efecto,
    // para que sobreviva aunque el efecto se reaplique/refresque).
    private static final int CORRUPTION_LEVEL_INTERVAL_TICKS = 30; // 1.5s
    private static final int CORRUPTION_DURATION_TICKS = 35 * 20; // 35s
    private static final String CORRUPTION_NEXT_LEVEL_TAG = "NanookNecroticCurseNextLevelTick";
    // El rayo no aparece a su largo completo de una -- crece desde el
    // pecho hacia adelante hasta MAX_HIT_DISTANCE en este tiempo (en la
    // práctica, a los 0.5s ya llegó a lo que sea que tenga delante, por
    // más lejos que esté). MISMA curva para el daño (acá) y el visual
    // (NecroticLightningRenderer, que llama a computeGrowthRange con el
    // tickCount de esta entidad).
    public static final int GROWTH_TICKS = 10; // 0.5s

    private int ticksAlive = 0;

    public static double computeGrowthRange(float ticksSinceSpawn) {
        float t = Mth.clamp(ticksSinceSpawn / GROWTH_TICKS, 0.0F, 1.0F);
        float inv = 1.0F - t;
        float eased = 1.0F - inv * inv * inv; // ease-out cúbica
        return MAX_HIT_DISTANCE * eased;
    }

    public NecroticLightningBeamEntity(EntityType<? extends Entity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    public void setOwner(NecroticKnightEntity owner) {
        this.getEntityData().set(DATA_OWNER_ID, owner.getId());
        followOwner(owner); // posición inicial correcta desde el primer frame, no esperar al próximo tick
    }

    /**
     * Le avisa a todos los clientes que están viendo esta entidad que
     * arranquen el loop de lightning_beam siguiendo su posición (ver
     * NecroticLightningBeamSoundHandler). Se llama UNA vez, apenas se crea
     * la entidad -- el corte (con fade) lo maneja {@link #stopLoopingSound}.
     */
    public void startLoopingSound() {
        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> this),
                new SyncLightningBeamSoundPacket(this.getId(), true));
    }

    /** Idempotente a propósito -- se puede llamar desde más de un lugar en tick() sin problema. */
    private void stopLoopingSound() {
        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> this),
                new SyncLightningBeamSoundPacket(this.getId(), false));
    }

    @Nullable
    public NecroticKnightEntity getOwnerKnight() {
        int id = this.getEntityData().get(DATA_OWNER_ID);
        Entity entity = this.level().getEntity(id);
        return entity instanceof NecroticKnightEntity knight ? knight : null;
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(DATA_OWNER_ID, -1);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            return; // el cliente solo LEE posición/rotación (ya interpoladas por Minecraft), no recalcula nada
        }

        NecroticKnightEntity owner = getOwnerKnight();
        if (owner == null || !owner.isAlive() || owner.getAttackState() != NecroticKnightEntity.ATTACK_LIGHTNING) {
            stopLoopingSound();
            this.discard();
            return;
        }
        if (ticksAlive >= NecroticKnightEntity.LIGHTNING_ACTIVE_CHANNEL_TICKS) {
            // El rayo se apaga en el segundo 5.4 aunque la animación del
            // jefe siga hasta el final -- pedido explícito. El sonido hace
            // fade out acá mismo, no cuando termina la animación completa.
            stopLoopingSound();
            this.discard();
            return;
        }

        this.setOldPosAndRot();
        followOwner(owner);

        ticksAlive++;

        // --- Chispas/destello en el punto de impacto, cada tick ---
        // Mismo raycast que ya usa applyDamage (computeBeamEnd), pero
        // separado del intervalo de daño -- así el parpadeo se ve fluido
        // aunque el daño solo pegue cada DAMAGE_INTERVAL_TICKS.
        if (this.level() instanceof ServerLevel impactLevel) {
            Vec3 origin = this.position();
            Vec3 dir = this.getViewVector(1.0F);
            double maxRange = computeGrowthRange(ticksAlive);
            BeamHit impactHit = computeBeamEnd(this.level(), this, owner, origin, dir, maxRange);
            // Solo hay "impacto" real si el rayo no llegó a su alcance máximo
            // (o sea, tocó algo -- bloque o entidad -- antes de eso).
            boolean touchedSomething = impactHit.hitEntity != null
                    || origin.distanceTo(impactHit.endPoint) < maxRange - 0.05;
            if (touchedSomething && this.ticksAlive % 3 == 0) {
                spawnImpactSparks(impactLevel, impactHit.endPoint, 1);
            }
        }

        if ((ticksAlive - 1) % DAMAGE_INTERVAL_TICKS == 0 && this.level() instanceof ServerLevel serverLevel) {
            applyDamage(serverLevel, owner);
        }
    }

    /** Chispas de impacto en el punto exacto donde el rayo toca algo -- ver NecroticLightningImpactParticle. */
    private void spawnImpactSparks(ServerLevel serverLevel, Vec3 point, int amount) {
        for (int i = 0; i < amount; i++) {
            serverLevel.sendParticles(com.nanookmod.registry.ModParticles.NECROTIC_LIGHTNING_IMPACT.get(),
                    point.x, point.y, point.z, 1, 0.15, 0.15, 0.15, 0.0);
        }
    }

    /**
     * Punto donde nace el rayo (punta de la espada), en coordenadas de
     * mundo. ÚNICA fórmula -- la usan TANTO followOwner (dónde se
     * posiciona esta entidad) COMO NecroticKnightEntity#updateLightningAim
     * (desde dónde se calcula hacia dónde apuntar). Antes había DOS
     * fórmulas separadas para esto (una acá, otra en
     * NecroticKnightEntity#getLightningOrigin) que se fueron
     * desincronizando cada vez que se ajustaba una sin tocar la otra --
     * eso es lo que causaba que "el rayo siga sin apuntar bien" incluso
     * después de arreglar la puntería en sí: el ÁNGULO se calculaba bien,
     * pero desde un punto de partida distinto al real.
     *
     * OJO -- el "forward" acá usa YAW *Y* PITCH (3D completo, igual que
     * Entity#getViewVector), no solo yaw. Antes la altura del origen era
     * un offset FIJO (heightOffset solo), así que por más que el jefe
     * apuntara bien hacia arriba/abajo, el punto de origen NUNCA se
     * movía con eso -- se veía desconectado de la punta de la espada del
     * modelo, que sí sube/baja con la animación. Con el forward en 3D,
     * FORWARD_OFFSET también empuja el origen hacia arriba/abajo según
     * el pitch real (como una espada sostenida por un brazo que gira),
     * así que el punto de partida matemático del rayo acompaña la
     * inclinación real en vez de quedarse plano.
     */
    public static Vec3 computeOriginFor(NecroticKnightEntity owner) {
        float yawRad = (float) Math.toRadians(owner.getYRot());
        float pitchRad = (float) Math.toRadians(owner.getXRot());
        double cosPitch = Math.cos(pitchRad);

        // Forward en 3D completo (yaw + pitch) -- misma convención que
        // Entity#getViewVector: x=-sin(yaw)*cos(pitch), y=-sin(pitch),
        // z=cos(yaw)*cos(pitch).
        double forwardX = -Math.sin(yawRad) * cosPitch;
        double forwardY = -Math.sin(pitchRad);
        double forwardZ = Math.cos(yawRad) * cosPitch;

        // "Right" se queda solo en yaw a propósito -- el costado del
        // cuerpo no rota con el pitch, solo con hacia dónde mira
        // horizontalmente (si lo hiciéramos rotar con pitch también, a
        // pitch alto el offset lateral empezaría a mezclarse con
        // adelante/atrás, cosa rara para "la mano derecha del jefe").
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        double x = owner.getX() + forwardX * FORWARD_OFFSET + rightX * RIGHT_OFFSET;
        double z = owner.getZ() + forwardZ * FORWARD_OFFSET + rightZ * RIGHT_OFFSET;
        double y = owner.getY() + owner.getBbHeight() * HEIGHT_OFFSET_FRACTION + forwardY * FORWARD_OFFSET;
        return new Vec3(x, y, z);
    }

    /**
     * Copia posición+rotación del jefe -- ver computeOriginFor para el offset.
     *
     * OJO con el yaw: Entity#getViewVector (lo que lee el renderer) interpola
     * linealmente entre el yRot VIEJO de esta entidad y el nuevo usando
     * partialTick. Si el jefe cruza el borde de -180/180 grados en un solo
     * tick (pasa fácil si el jugador rodea al jefe), moveTo() dejaría un
     * salto de ~360 grados entre el yRot viejo y el nuevo -- por una
     * fracción de tick, el rayo renderizado apuntaría para cualquier lado
     * (un veredicto exacto de "a veces el rayo se va lejos, para cualquier
     * lado"). Acá se corrige el nuevo yaw para que quede en el mismo giro
     * que el viejo ANTES de moveTo -- así la interpolación siempre toma el
     * camino corto, nunca la vuelta larga.
     */
    private void followOwner(NecroticKnightEntity owner) {
        Vec3 origin = computeOriginFor(owner);
        float continuousYaw = this.yRotO + Mth.wrapDegrees(owner.getYRot() - this.yRotO);
        this.moveTo(origin.x, origin.y, origin.z, continuousYaw, owner.getXRot());
    }

    private void applyDamage(ServerLevel serverLevel, NecroticKnightEntity owner) {
        Vec3 origin = this.position();
        Vec3 dir = this.getViewVector(1.0F);
        double maxRange = computeGrowthRange(ticksAlive);

        BeamHit hit = computeBeamEnd(this.level(), this, owner, origin, dir, maxRange);

        if (hit.hitEntity != null) {
            LivingEntity target = hit.hitEntity;
            DamageSource source = owner.damageSources().mobAttack(owner);
            float damage = owner.getLightningDamage();
            boolean wasBlocking = target.isBlocking();
            if (target.hurt(source, damage)) {
                owner.onKnightLandedHit();
                applyNecroticCurse(serverLevel, target, owner);
                // Robo de vida específico del rayo (aparte del general, que ya se dispara solo
                // vía ModEvents#onLivingDamage para CUALQUIER ataque) -- ver la explicación
                // completa en NecroticKnightEntity#applyLightningLifesteal.
                owner.applyLightningLifesteal(target);
            }
            if (wasBlocking) {
                int blocked = shieldBlockedHitsPerTarget.merge(target.getUUID(), 1, Integer::sum);
                if (blocked >= SHIELD_DISABLE_AFTER_BLOCKS) {
                    owner.disablePlayerShield(target);
                    shieldBlockedHitsPerTarget.remove(target.getUUID());
                }
            }
        }

        // OJO: el sonido tiene que sonar donde IMPACTA el rayo (hit.endPoint),
        // no en this.blockPosition() -- esta entidad sigue al jefe
        // (followOwner) y vive pegada a él, así que reproducir el sonido en
        // su propia posición lo deja sonando siempre "desde el jefe". Con
        // rangos de rayo largos eso significa que, aunque el rayo te esté
        // pegando a vos, el sonido nace lejos tuyo y Minecraft lo atenúa por
        // distancia hasta casi no oírse -- de ahí que solo se escuchara bien
        // estando cerca del NecroKnight.
        serverLevel.playSound(null, BlockPos.containing(hit.endPoint),
                ModSounds.NECROKNIGHT_LIGHTNING_HIT.get(), SoundSource.HOSTILE, 1.0F,
                0.9F + this.random.nextFloat() * 0.2F);
    }

    /**
     * Aplica (o escala) la Maldición Necrótica sobre el objetivo golpeado
     * por el rayo.
     *
     * El primer golpe que conecta da Nivel 1 (amplifier 0) con
     * CORRUPTION_DURATION_TICKS (35s) de duración. Mientras el rayo lo
     * siga golpeando, cada CORRUPTION_LEVEL_INTERVAL_TICKS el nivel
     * sube en 1 (con la duración reiniciada a 35s), hasta el nivel máximo
     * que define el propio NecroticKnightEntity
     * ({@link NecroticKnightEntity#getMaxCorruptionLevel()}, hoy 6 -- un
     * solo lugar donde tocar el tope si cambia más adelante).
     *
     * El intervalo de subida de nivel se controla con una marca de tiempo aparte guardada en
     * la persistentData del objetivo (CORRUPTION_NEXT_LEVEL_TAG) -- el
     * rayo en sí pega cada 0.5s (DAMAGE_INTERVAL_TICKS), así que sin este
     * control el nivel subiría 4 veces más rápido de lo pedido.
     */
    private static void applyNecroticCurse(ServerLevel serverLevel, LivingEntity target, NecroticKnightEntity owner) {
        long now = serverLevel.getGameTime();
        CompoundTag persistentData = target.getPersistentData();
        long nextLevelTick = persistentData.getLong(CORRUPTION_NEXT_LEVEL_TAG);

        MobEffectInstance current = target.getEffect(ModEffects.CORRUPTION.get());
        if (current != null && now < nextLevelTick) {
            // Ya tiene el efecto y todavía no pasaron los 2s desde la
            // última subida de nivel -- no hacemos nada, ni refrescamos
            // duración (la duración solo se renueva al subir de nivel).
            return;
        }

        int currentAmplifier = current != null ? current.getAmplifier() : -1;
        int maxAmplifier = owner.getMaxCorruptionLevel() - 1; // amplifier 0 = Nivel 1
        int newAmplifier = Math.min(currentAmplifier + 1, maxAmplifier);

        target.addEffect(new MobEffectInstance(
                ModEffects.CORRUPTION.get(),
                CORRUPTION_DURATION_TICKS,
                newAmplifier,
                false,  // ambient
                false,  // showParticles -- sin partículas, ni las propias ni el remolino vanilla
                true    // showIcon -- se ve el ícono en el HUD de efectos
        ));
        persistentData.putLong(CORRUPTION_NEXT_LEVEL_TAG, now + CORRUPTION_LEVEL_INTERVAL_TICKS);
    }

    /**
     * Raycast de verdad: bloques sólidos Y entidades, lo primero que se
     * cruza corta el rayo ahí. Público y estático para que
     * NecroticLightningRenderer pueda llamarlo del lado cliente con la
     * MISMA lógica (ver el comentario largo de la clase).
     */
    public static BeamHit computeBeamEnd(Level level, Entity self, @Nullable Entity owner,
                                         Vec3 origin, Vec3 dir, double maxRange) {
        Vec3 farPoint = origin.add(dir.scale(maxRange));

        ClipContext clipContext = new ClipContext(origin, farPoint,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self);
        HitResult blockHit = level.clip(clipContext);
        double blockDist = blockHit.getType() == HitResult.Type.BLOCK
                ? origin.distanceTo(blockHit.getLocation())
                : maxRange;

        Vec3 blockClippedEnd = origin.add(dir.scale(blockDist));
        AABB searchArea = new AABB(origin, blockClippedEnd).inflate(HIT_MARGIN);

        double closestDist = blockDist;
        LivingEntity closestEntity = null;

        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, searchArea,
                e -> e != owner && e != self && e.isAlive())) {
            AABB box = candidate.getBoundingBox().inflate(HIT_MARGIN);
            Optional<Vec3> clip = box.clip(origin, blockClippedEnd);
            if (clip.isEmpty()) {
                continue;
            }
            double dist = origin.distanceTo(clip.get());
            if (dist < closestDist) {
                closestDist = dist;
                closestEntity = candidate;
            }
        }

        Vec3 endPoint = origin.add(dir.scale(Math.min(closestDist, blockDist)));
        return new BeamHit(endPoint, closestEntity);
    }

    /**
     * SOLO para el dibujado -- nunca se llama desde applyDamage (el daño
     * se queda limitado a computeGrowthRange, sin excepciones). Si el
     * rayo llegó a su alcance máximo real sin chocar contra nada (ni
     * bloque ni jugador -- el jugador esquivó o está fuera de la línea),
     * antes se cortaba ahí no más, flotando en el aire a la altura del
     * pecho. Esto sigue la MISMA línea recta (mismo origen, misma
     * dirección -- no cambia el ángulo) más allá de ese punto hasta que
     * choque contra un bloque sólido de verdad (normalmente el piso), o
     * hasta maxExtra si no encuentra nada (por si el rayo apunta al
     * cielo abierto). Así el rayo SIEMPRE termina en algo físico --
     * jugador, pared, o piso -- nunca cortado en el vacío.
     */
    public static Vec3 extendVisualToSolidBlock(Level level, Entity self, Vec3 from, Vec3 dir, double maxExtra) {
        Vec3 farPoint = from.add(dir.scale(maxExtra));
        ClipContext clipContext = new ClipContext(from, farPoint,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self);
        HitResult blockHit = level.clip(clipContext);
        return blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : from;
    }

    /** Resultado del raycast: dónde termina el rayo, y qué entidad lo cortó (si fue una entidad y no un bloque/el alcance máximo). */
    public static final class BeamHit {
        public final Vec3 endPoint;
        @Nullable
        public final LivingEntity hitEntity;

        public BeamHit(Vec3 endPoint, @Nullable LivingEntity hitEntity) {
            this.endPoint = endPoint;
            this.hitEntity = hitEntity;
        }
    }

    // ---- Sin guardado en disco: vive y muere junto con el ataque del jefe ----

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