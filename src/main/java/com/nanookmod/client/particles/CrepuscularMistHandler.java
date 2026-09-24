package com.nanookmod.client.particles;

import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModBiomes;
import com.nanookmod.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Esparce jirones de niebla baja (nanookmod:crepuscular_mist) alrededor del
 * jugador mientras esté parado en el Bosque Crepuscular (o su variante de
 * río).
 *
 * IMPORTANTE: la altura de spawn se calcula con el heightmap real de cada
 * columna (x,z) elegida, NO con la altura del jugador. Si se usa la altura
 * del jugador como referencia, en terreno con desniveles la niebla queda
 * "flotando" en el aire en vez de pegada al piso (eso es lo que se veía como
 * "jirones" sueltos). Con el heightmap, cada partícula nace justo sobre el
 * bloque sólido más alto de esa columna, sea cual sea el desnivel.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CrepuscularMistHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;

        BlockPos playerPos = player.blockPosition();
        boolean inBiome = level.getBiome(playerPos).is(ModBiomes.BOSQUE_CREPUSCULAR_KEY)
                || level.getBiome(playerPos).is(ModBiomes.BOSQUE_CREPUSCULAR_RIVER_KEY);
        if (!inBiome) return;

        RandomSource random = level.getRandom();

        // Frecuencia baja a propósito: un jirón cada tanto, no una capa constante.
        if (random.nextInt(8) != 0) return;

        double angle = random.nextDouble() * Math.PI * 2.0D;
        double dist = 3.0D + random.nextDouble() * 9.0D;
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);

        // Altura real del terreno en esa columna (bloque sólido más alto,
        // ignorando hojas/agua para que no quede "flotando" sobre el follaje).
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

        // Si esa columna está demasiado lejos en altura del jugador (por
        // ejemplo un acantilado fuera de vista), no vale la pena spawnearla.
        if (Math.abs(groundY - player.getY()) > 6.0D) return;

        double spawnX = x + 0.5D;
        double spawnZ = z + 0.5D;
        double spawnY = groundY + 0.05D + random.nextDouble() * 0.25D;

        level.addParticle(ModParticles.CREPUSCULAR_MIST.get(), spawnX, spawnY, spawnZ, 0.0D, 0.0D, 0.0D);
    }
}
