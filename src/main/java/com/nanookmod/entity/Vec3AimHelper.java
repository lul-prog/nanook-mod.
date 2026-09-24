package com.nanookmod.entity;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Utilidad chica para apuntar un proyectil no-vanilla directo a un punto,
 * evitando repetir la cuenta de dirección normalizada en cada mob que dispare.
 */
public final class Vec3AimHelper {

    private Vec3AimHelper() {
    }

    public static void aim(Projectile projectile, double targetX, double targetY, double targetZ, double speed) {
        Vec3 direction = new Vec3(
                targetX - projectile.getX(),
                targetY - projectile.getY(),
                targetZ - projectile.getZ()
        ).normalize();

        projectile.setDeltaMovement(direction.scale(speed));
    }
}