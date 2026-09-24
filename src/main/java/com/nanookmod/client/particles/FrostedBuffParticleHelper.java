package com.nanookmod.client.particles;

import com.nanookmod.registry.ModParticles;
import net.minecraft.world.entity.LivingEntity;

/**
 * Llamar desde el tick() de cualquier mob que pueda estar potenciado por la
 * Noche Escarchada (ver FrostedNightMobBuffHandler). Solo hace algo del lado
 * del cliente: el servidor jamás dibuja partículas, así que esta llamada es
 * segura de hacer siempre, sin envolverla en un chequeo aparte de
 * isClientSide.
 */
public final class FrostedBuffParticleHelper {

    private FrostedBuffParticleHelper() {
    }

    public static void spawnIfBuffed(LivingEntity entity, boolean buffed) {
        if (!buffed) return;
        if (!entity.level().isClientSide) return;
        if (entity.getRandom().nextInt(4) != 0) return; // no en cada tick, se vería muy denso

        double x = entity.getX() + (entity.getRandom().nextDouble() - 0.5D) * entity.getBbWidth();
        double y = entity.getY() + entity.getRandom().nextDouble() * entity.getBbHeight();
        double z = entity.getZ() + (entity.getRandom().nextDouble() - 0.5D) * entity.getBbWidth();

        entity.level().addParticle(ModParticles.FROSTED_BUFF.get(), x, y, z, 0.0D, 0.0D, 0.0D);
    }
}