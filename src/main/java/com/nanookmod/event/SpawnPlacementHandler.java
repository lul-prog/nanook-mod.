package com.nanookmod.event;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.SnowyBlizzEntity;
import com.nanookmod.registry.ModBiomes;
import com.nanookmod.registry.ModBlocks;
import com.nanookmod.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SpawnPlacementHandler {

    private static final double MIN_SPACING = 10.0D;
    static final boolean DEBUG_LOG = false;

    // Mismo rango horario que usa FrostedNightHandler para "es de noche"
    // (dayTime % 24000 entre 13000 y 23000) - lo reutilizamos acá tal cual
    // para que "de noche" signifique lo mismo en todo el mod.
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;

    @SubscribeEvent
    public static void onSpawnPlacementRegister(SpawnPlacementRegisterEvent event) {
        event.register(
                ModEntities.SNOWY_BLIZZ.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SpawnPlacementHandler::canSnowyBlizzSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );

        event.register(
                ModEntities.SKELETAL_WARRIOR.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SpawnPlacementHandler::canSkeletalNightHunterSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );

        event.register(
                ModEntities.SKELETAL_MAGE.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SpawnPlacementHandler::canSkeletalNightHunterSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
    }

    /**
     * Predicado compartido por Skeletal Warrior y Skeletal Mage.
     *
     * Reglas:
     *  - Nunca en Peaceful.
     *  - SOLO de noche (no alcanza con estar oscuro, como sí les pasa a los
     *    monstruos vanilla en cuevas de día - acá exigimos la ventana
     *    horaria real, igual que la Noche Escarchada).
     *  - Fuera del Bosque Crepuscular: además tiene que estar oscuro
     *    (igual que cualquier monstruo vanilla), para que una base con
     *    antorchas siga siendo segura.
     *  - Dentro del Bosque Crepuscular (y su río): el nivel de luz NO
     *    importa, porque el bioma está iluminado de forma natural por su
     *    vegetación - ahí alcanza con que sea de noche.
     *  - Siempre se valida que haya piso sólido y espacio real para pararse
     *    (igual que canSnowyBlizzSpawn), sin importar el bioma.
     */
    // Flag de debug independiente de DEBUG_LOG (que es de Snowy Blizz). Poné
    // esto en 'true', recompilá, y el log de consola va a mostrar el motivo
    // exacto de cada rechazo con el tag [skeletal_night_hunter spawn].
    private static final boolean DEBUG_LOG_SKELETAL = false;

    static <T extends Mob> boolean canSkeletalNightHunterSpawn(EntityType<T> type, ServerLevelAccessor level,
                                                               MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            debugLogSkeletal(type, level, pos, "RECHAZADO: dificultad Peaceful");
            return false;
        }

        boolean canSeeSky = level.getLevel().canSeeSky(pos);
        boolean inTwilightForest = level.getBiome(pos).is(ModBiomes.BOSQUE_CREPUSCULAR_KEY)
                || level.getBiome(pos).is(ModBiomes.BOSQUE_CREPUSCULAR_RIVER_KEY);

        if (!canSeeSky) {
            // Bajo tierra (cuevas, minas, etc.): igual que zombies/esqueletos
            // vanilla, no importa la hora del día - solo que esté oscuro.
            // Excepción: dentro del Bosque Crepuscular tampoco importa la luz
            // (plantas como crepuscular_sprout/vines brillan y no deberían
            // bloquear el spawn, igual que en superficie).
            if (!inTwilightForest && !isDarkEnoughToSpawn(level, pos, random)) {
                debugLogSkeletal(type, level, pos, "RECHAZADO (cueva): no está lo bastante oscuro");
                return false;
            }
        } else {
            // Superficie.
            if (inTwilightForest) {
                // Bosque Crepuscular: ya no exigimos la ventana horaria real.
                // De día: el factor de luz se activa - solo spawnea en sombra
                // densa (bajo follaje espeso), con un umbral más exigente que
                // el de "oscuro" genérico para que no alcance cualquier sombrita.
                // De noche: el factor de luz se apaga - spawnea sin
                // importar cuánta luz haya (torch-proof solo fuera del bioma).
                if (!isNightWindow(level) && !isDarkEnoughToSpawnDaytimeStrict(level, pos, random)) {
                    debugLogSkeletal(type, level, pos, "RECHAZADO: de día y no está en sombra densa");
                    return false;
                }
            } else {
                // Fuera del bioma: se mantiene la regla original (solo de
                // noche, siempre oscuro).
                if (!isNightWindow(level)) {
                    debugLogSkeletal(type, level, pos, "RECHAZADO: no es de noche (dayTime=" + (level.getLevel().getDayTime() % 24000L) + ")");
                    return false;
                }

                if (!isDarkEnoughToSpawn(level, pos, random)) {
                    debugLogSkeletal(type, level, pos, "RECHAZADO: no está lo bastante oscuro");
                    return false;
                }
            }
        }

        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (!belowState.isFaceSturdy(level, belowPos, Direction.UP)) {
            debugLogSkeletal(type, level, pos, "RECHAZADO: piso no válido (" + belowState.getBlock() + ")");
            return false;
        }

        if (!isPassable(level, pos) || !isPassable(level, pos.above())) {
            debugLogSkeletal(type, level, pos, "RECHAZADO: sin espacio libre");
            return false;
        }

        AABB entityBox = type.getAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (!level.getLevel().noCollision(entityBox)) {
            debugLogSkeletal(type, level, pos, "RECHAZADO: hitbox choca con el entorno");
            return false;
        }

        debugLogSkeletal(type, level, pos, "ACEPTADO");
        return true;
    }

    private static void debugLogSkeletal(EntityType<?> type, ServerLevelAccessor level, BlockPos pos, String result) {
        if (DEBUG_LOG_SKELETAL) {
            String biomeId = level.getBiome(pos).unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse("?");
            NanookMod.LOGGER.info("[skeletal_night_hunter spawn] type={} pos={} biome={} -> {}",
                    type.getDescriptionId(), pos, biomeId, result);
        }
    }

    private static boolean isNightWindow(ServerLevelAccessor level) {
        long timeOfDay = level.getLevel().getDayTime() % 24000L;
        return timeOfDay >= NIGHT_START && timeOfDay <= NIGHT_END;
    }

    // Copia local del chequeo de oscuridad de Monster#isDarkEnoughToSpawn
    // (vanilla, package-private/protected -> no accesible desde acá), para
    // mantener el mismo criterio "oscuro" que zombies/skeletons/brujas.
    private static boolean isDarkEnoughToSpawn(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        if (level.getBrightness(LightLayer.SKY, pos) > random.nextInt(32)) {
            return false;
        }
        int rawBrightness = level.getLevel().isThundering()
                ? level.getMaxLocalRawBrightness(pos, 10)
                : level.getMaxLocalRawBrightness(pos);
        return rawBrightness <= random.nextInt(8);
    }

    // Versión más estricta de isDarkEnoughToSpawn, usada SOLO para permitir
    // spawns de día en el Bosque Crepuscular. El chequeo vanilla es bastante
    // permisivo (incluso con luz de cielo alta hay chance de pasar), así que
    // acá exigimos: luz de cielo baja de forma fija (no probabilística) y
    // luz local aún más baja que el umbral normal - o sea, sombra realmente
    // densa (varias capas de hojas encima), no cualquier sombrita parcial.
    private static final int STRICT_MAX_SKY_LIGHT = 4;
    private static final int STRICT_MAX_LOCAL_LIGHT_BOUND = 4;

    private static boolean isDarkEnoughToSpawnDaytimeStrict(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        if (level.getBrightness(LightLayer.SKY, pos) > STRICT_MAX_SKY_LIGHT) {
            return false;
        }
        int rawBrightness = level.getLevel().isThundering()
                ? level.getMaxLocalRawBrightness(pos, 10)
                : level.getMaxLocalRawBrightness(pos);
        return rawBrightness <= random.nextInt(STRICT_MAX_LOCAL_LIGHT_BOUND);
    }

    // Sin "private": lo reutiliza SnowyBlizzSurfaceSpawnHandler (mismo paquete) para no
    // duplicar la lógica de validación en el spawner dedicado de superficie.
    static boolean canSnowyBlizzSpawn(EntityType<SnowyBlizzEntity> type, ServerLevelAccessor level,
                                      MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            debugLog(level, pos, "RECHAZADO: dificultad Peaceful");
            return false;
        }

        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);

        if (belowState.is(BlockTags.ICE) || belowState.is(ModBlocks.FROST_ICE.get())) {
            debugLog(level, pos, "RECHAZADO: piso es hielo");
            return false;
        }

        boolean standableSurface = belowState.is(Blocks.SNOW)
                || belowState.isFaceSturdy(level, belowPos, Direction.UP);

        if (!standableSurface) {
            debugLog(level, pos, "RECHAZADO: superficie no válida (" + belowState.getBlock() + ")");
            return false;
        }

        if (!isPassable(level, pos) || !isPassable(level, pos.above())) {
            BlockState blockingLow = level.getBlockState(pos);
            BlockState blockingHigh = level.getBlockState(pos.above());
            debugLog(level, pos, "RECHAZADO: sin espacio libre (bloque en pos=" + blockingLow.getBlock()
                    + ", bloque en pos+1=" + blockingHigh.getBlock() + ")");
            return false;
        }

        // Chequeo de columna 1x1 ya pasado arriba, pero la hitbox REAL del mob es más
        // ancha (0.6 bloques para Snowy Blizz). En terreno con "ventisqueros" (nieve de
        // altura variable entre tiles vecinos, ver FrostFreezeFeature), la columna central
        // puede estar libre mientras la nieve de un tile vecino más alto choca contra el
        // cuerpo del mob igual. Usamos la AABB real de la entidad (la misma que usará
        // Minecraft después con level.noCollision) para detectar esto ANTES de aprobar,
        // en vez de aprobar en falso y que el spawn real se descarte en silencio.
        AABB entityBox = type.getAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (!level.getLevel().noCollision(entityBox)) {
            debugLog(level, pos, "RECHAZADO: hitbox real choca con el entorno (nieve/terreno vecino)");
            return false;
        }

        AABB nearbyArea = new AABB(pos).inflate(MIN_SPACING);
        int nearbyCount = level.getEntitiesOfClass(SnowyBlizzEntity.class, nearbyArea).size();
        if (nearbyCount > 0) {
            debugLog(level, pos, "RECHAZADO: ya hay " + nearbyCount + " cerca (spacing)");
            return false;
        }

        debugLog(level, pos, "ACEPTADO");
        return true;
    }

    static void debugLog(ServerLevelAccessor level, BlockPos pos, String result) {
        if (DEBUG_LOG) {
            long dayTime = level.getLevel().getDayTime() % 24000L;
            boolean isDay = level.getLevel().isDay();
            boolean canSeeSky = level.getLevel().canSeeSky(pos);
            // NUEVO: bloque justo debajo del candidato, para confirmar si los spawns
            // exitosos ocurren sobre nieve (minecraft:snow) o solo sobre suelo pelado.
            BlockState belowState = level.getBlockState(pos.below());
            NanookMod.LOGGER.info("[snowy_blizz spawn] pos={} dayTime={} isDay={} canSeeSky={} belowBlock={} -> {}",
                    pos, dayTime, isDay, canSeeSky, belowState.getBlock(), result);
        }
    }

    private static boolean isPassable(ServerLevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }
}