package com.nanookmod.event;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.CrowEntity;
import com.nanookmod.registry.ModEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * "El llamado": si un jugador se mantiene con 25% de vida o menos durante
 * un rato sostenido (no un simple golpe puntual), los cuervos cercanos lo
 * "notan" y se genera un enjambre extra que empieza a dar vueltas
 * alrededor suyo (ver CrowSwarmOmenGoal). Si el jugador se recupera, ese
 * enjambre extra despawnea solo para no generar lag; los cuervos que ya
 * estaban ahí antes del llamado simplemente vuelven a su comportamiento
 * normal.
 *
 * Usa el mismo patrón que PermafrostDamageHandler: datos sincronizados
 * adjuntos directamente al Player.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CrowOmenHandler {

    public static final EntityDataAccessor<Integer> LOW_HEALTH_CHARGE =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> SWARM_ACTIVE =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);

    private static final float LOW_HEALTH_FRACTION = 0.25F;
    private static final int CALL_THRESHOLD = 100; // ~5s sostenidos con poca vida
    private static final int CHARGE_GAIN_PER_TICK = 1;
    private static final int CHARGE_DECAY_PER_TICK = 4; // se recupera más rápido de lo que se acumula

    private static final int SUMMON_COUNT = 5;
    private static final double SUMMON_MIN_RADIUS = 8.0D;
    private static final double SUMMON_MAX_RADIUS = 16.0D;
    private static final double DESPAWN_SEARCH_RADIUS = 64.0D;

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            try {
                player.getEntityData().define(LOW_HEALTH_CHARGE, 0);
            } catch (Exception ignored) {
            }
            try {
                player.getEntityData().define(SWARM_ACTIVE, false);
            } catch (Exception ignored) {
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!player.isAlive()) return;

        boolean lowHealth = player.getHealth() <= player.getMaxHealth() * LOW_HEALTH_FRACTION;

        int charge = player.getEntityData().get(LOW_HEALTH_CHARGE);
        charge = lowHealth
                ? Math.min(CALL_THRESHOLD, charge + CHARGE_GAIN_PER_TICK)
                : Math.max(0, charge - CHARGE_DECAY_PER_TICK);
        player.getEntityData().set(LOW_HEALTH_CHARGE, charge);

        boolean swarmActive = player.getEntityData().get(SWARM_ACTIVE);

        if (!swarmActive && charge >= CALL_THRESHOLD) {
            player.getEntityData().set(SWARM_ACTIVE, true);
            summonCrows(serverPlayer);
        } else if (swarmActive && charge <= 0) {
            player.getEntityData().set(SWARM_ACTIVE, false);
            despawnSummonedCrows(serverPlayer);
        }
    }

    public static boolean isSwarmActive(Player player) {
        try {
            return player.getEntityData().get(SWARM_ACTIVE);
        } catch (Exception e) {
            return false;
        }
    }

    private static void summonCrows(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();

        for (int i = 0; i < SUMMON_COUNT; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = SUMMON_MIN_RADIUS + random.nextDouble() * (SUMMON_MAX_RADIUS - SUMMON_MIN_RADIUS);
            double x = player.getX() + Math.cos(angle) * dist;
            double z = player.getZ() + Math.sin(angle) * dist;
            double y = player.getY() + 3 + random.nextInt(6);

            CrowEntity crow = ModEntities.CROW.get().create(level);
            if (crow == null) continue;

            crow.moveTo(x, y, z, random.nextFloat() * 360.0F, 0.0F);
            crow.setSummoned(true);
            crow.finalizeSpawn(level, level.getCurrentDifficultyAt(crow.blockPosition()), MobSpawnType.EVENT, null, null);
            level.addFreshEntity(crow);
        }
    }

    private static void despawnSummonedCrows(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB area = new AABB(player.blockPosition()).inflate(DESPAWN_SEARCH_RADIUS);

        for (CrowEntity crow : level.getEntitiesOfClass(CrowEntity.class, area)) {
            if (crow.isSummoned()) {
                crow.discard();
            }
        }
    }
}
