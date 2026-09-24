package com.nanookmod.event;

import com.nanookmod.NanookMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;

/**
 * Base reutilizable para spawners dedicados que NO dependen de NaturalSpawner
 * (bypasean por completo el cap regional compartido y cualquier comportamiento
 * día/noche inexplicado que tenga la vía vanilla). Cada mob nuevo solo necesita
 * una subclase chica que indique su EntityType y su regla de validación -
 * todo el resto (throttle, chequeo de bioma, tope de población, creación real)
 * vive acá una sola vez.
 *
 * NO usa @Mod.EventBusSubscriber con métodos static (a diferencia del resto
 * del mod) porque cada instancia necesita su propio estado (tickCounter,
 * configuración). Las instancias concretas se registran a mano en el bus de
 * eventos de Forge - ver ModSpawnHandlers para el punto único de registro.
 *
 * @param <T> tipo de mob que este spawner puede crear.
 */
public abstract class AbstractDedicatedSpawner<T extends Mob> {

    protected final EntityType<T> entityType;
    protected final ResourceKey<Biome> biome;
    protected final int attemptIntervalTicks;
    protected final int minRadius;
    protected final int maxRadius;
    protected final int maxNearby;
    protected final double populationCheckRadius;
    protected final boolean debugLog;

    private int tickCounter = 0;

    protected AbstractDedicatedSpawner(EntityType<T> entityType, ResourceKey<Biome> biome,
                                       int attemptIntervalTicks, int minRadius, int maxRadius,
                                       int maxNearby, double populationCheckRadius, boolean debugLog) {
        this.entityType = entityType;
        this.biome = biome;
        this.attemptIntervalTicks = attemptIntervalTicks;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.maxNearby = maxNearby;
        this.populationCheckRadius = populationCheckRadius;
        this.debugLog = debugLog;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        tickCounter++;
        if (tickCounter < attemptIntervalTicks) {
            return;
        }
        tickCounter = 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            attemptSpawnNear(player);
        }
    }

    private void attemptSpawnNear(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }

        if (isPopulationFull(level, player.blockPosition())) {
            debugLog(player.blockPosition(), "RECHAZADO: población cercana al tope (" + maxNearby + ")");
            return;
        }

        RandomSource random = level.getRandom();
        BlockPos candidate = findValidCandidate(level, player, random);

        if (candidate == null) {
            return; // la subclase ya logueó el motivo si correspondía
        }

        spawnAt(level, candidate, random);
    }

    /**
     * Punto de entrada para el spawn real, una vez que ya hay un candidato válido.
     * Por defecto crea UNA entidad. Las subclases que quieran spawnear en manada
     * (ver GnutSurfaceSpawner) overridean esto en vez de tocar attemptSpawnNear.
     */
    protected void spawnAt(ServerLevel level, BlockPos pos, RandomSource random) {
        T mob = createAndSpawnOne(level, pos, random);
        if (mob != null) {
            debugLog(pos, "SPAWNEADO");
        }
    }

    /**
     * Crea y agrega UNA entidad en la posición dada, sin loguear (el llamador decide
     * qué y cuándo loguear - útil para spawns en manada donde se loguea una sola vez
     * o de forma distinta). Devuelve null si falló la creación.
     */
    @Nullable
    protected T createAndSpawnOne(ServerLevel level, BlockPos pos, RandomSource random) {
        T mob = entityType.create(level);
        if (mob == null) {
            return null;
        }

        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                random.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);

        level.addFreshEntity(mob);
        return mob;
    }

    /**
     * Busca un punto candidato cerca del jugador Y LO VALIDA (llamando a canSpawnAt las
     * veces que haga falta internamente), ya filtrado por bioma. Devuelve null si no
     * encontró nada válido en este ciclo. Implementado por SurfaceSpawnHandler (un solo
     * candidato, calculado por heightmap) y CaveSpawnHandler (barrido de varias columnas
     * y alturas, probando canSpawnAt en cada una hasta encontrar una válida).
     */
    @Nullable
    protected abstract BlockPos findValidCandidate(ServerLevel level, Player player, RandomSource random);

    /**
     * Validación final específica del mob (equivalente a lo que hacía
     * SpawnPlacementHandler.canSnowyBlizzSpawn para el Blizz): reglas de piso, hitbox,
     * spacing propio del mob, etc. Cada mob nuevo implementa la suya acá. Las subclases
     * de findValidCandidate son las que llaman a este método.
     */
    protected abstract boolean canSpawnAt(ServerLevel level, BlockPos pos, RandomSource random);

    /**
     * Cuenta población cercana para decidir si ya se llegó al tope. Por defecto cuenta
     * TODAS las entidades de este tipo en el radio, sin distinguir superficie/cueva -
     * las subclases (Surface/Cave) lo afinan filtrando por canSeeSky.
     */
    protected boolean isPopulationFull(ServerLevel level, BlockPos pos) {
        AABB checkArea = new AABB(pos).inflate(populationCheckRadius);
        long nearby = level.getEntities(entityType, checkArea, e -> true).size();
        return nearby >= maxNearby;
    }

    protected void debugLog(BlockPos pos, String result) {
        if (debugLog) {
            NanookMod.LOGGER.info("[{} {}] pos={} -> {}",
                    entityType.getDescriptionId(), logTag(), pos, result);
        }
    }

    /** Etiqueta corta para distinguir en el log qué subtipo de spawner generó la línea. */
    protected abstract String logTag();
}