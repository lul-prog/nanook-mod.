package com.nanookmod.event;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.SnowyBlizzEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * SOLO DIAGNÓSTICO (temporal). Loguea cada vez que un SnowyBlizzEntity
 * REALMENTE se agrega al mundo (level.addFreshEntity ya ejecutado), sin
 * importar la causa (spawn natural, comando, etc.). Esto nos permite comparar
 * "cuántos ACEPTADO dijo el predicado de SpawnPlacementHandler" contra
 * "cuántos Snowy Blizz llegaron a existir de verdad" en la misma ventana de
 * tiempo, para descartar si el predicado aprueba pero algo después descarta
 * la entidad silenciosamente (ej. checkSpawnObstruction por hitbox real).
 *
 * Borrar esta clase (y su registro implícito vía @EventBusSubscriber) una
 * vez resuelto el bug de spawneo diurno.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SnowyBlizzSpawnDebugHandler {

    private static final boolean DEBUG_LOG = false;

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!DEBUG_LOG) {
            return;
        }

        if (event.getLevel().isClientSide()) {
            return;
        }

        if (event.getEntity() instanceof SnowyBlizzEntity blizz) {
            boolean canSeeSky = blizz.level().canSeeSky(blizz.blockPosition());
            long dayTime = blizz.level().getDayTime() % 24000L;
            boolean isDay = blizz.level().isDay();
            BlockState belowState = blizz.level().getBlockState(blizz.blockPosition().below());

            NanookMod.LOGGER.info(
                    "[snowy_blizz JOIN] pos={} dayTime={} isDay={} canSeeSky={} belowBlock={} fromDisk={}",
                    blizz.blockPosition(), dayTime, isDay, canSeeSky, belowState.getBlock(), event.loadedFromDisk()
            );
        }
    }
}