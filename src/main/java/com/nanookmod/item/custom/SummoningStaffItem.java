package com.nanookmod.item.custom;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SummoningStaffItem extends Item {

    public SummoningStaffItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pUsedHand);

        if (!pLevel.isClientSide()) {
            // === LÓGICA DEL SERVIDOR ===

            // 1. Mensaje en el chat
            pPlayer.sendSystemMessage(Component.literal("§b¡El Bastón de Nanook ha sido activado!"));

            // 2. Dirección hacia donde mira el jugador
            Vec3 look = pPlayer.getLookAngle();
            Vec3 right = new Vec3(-look.z, 0, look.x).normalize();

            // 3. Calcular posiciones del triángulo
            double x1 = pPlayer.getX() + look.x * 1.5;
            double y1 = pPlayer.getY();
            double z1 = pPlayer.getZ() + look.z * 1.5;

            double x2 = pPlayer.getX() + look.x * 0.5 + right.x * 1.2;
            double y2 = pPlayer.getY();
            double z2 = pPlayer.getZ() + look.z * 0.5 + right.z * 1.2;

            double x3 = pPlayer.getX() + look.x * 0.5 - right.x * 1.2;
            double y3 = pPlayer.getY();
            double z3 = pPlayer.getZ() + look.z * 0.5 - right.z * 1.2;

            // 4. Spawnea los 3 Zombies
            spawnZombie(pLevel, x1, y1, z1, pPlayer.getYRot());
            spawnZombie(pLevel, x2, y2, z2, pPlayer.getYRot());
            spawnZombie(pLevel, x3, y3, z3, pPlayer.getYRot());

            // 5. Cooldown de 2 segundos (40 ticks)
            pPlayer.getCooldowns().addCooldown(this, 40);

        } else {
            // === LÓGICA DEL CLIENTE (PARTÍCULAS) ===
            // Las partículas SOLO se pueden ver y crear en el lado del cliente

            // Creamos un efecto de explosión de hielo alrededor del jugador
            for (int i = 0; i < 40; i++) {
                // Generamos posiciones aleatorias alrededor del jugador
                double offsetX = (pLevel.random.nextDouble() - 0.5) * 2.0;
                double offsetY = pLevel.random.nextDouble() * 2.0;
                double offsetZ = (pLevel.random.nextDouble() - 0.5) * 2.0;

                // Generamos velocidades aleatorias para que salgan disparadas
                double speedX = (pLevel.random.nextDouble() - 0.5) * 0.1;
                double speedY = pLevel.random.nextDouble() * 0.15;
                double speedZ = (pLevel.random.nextDouble() - 0.5) * 0.1;

                // Añadimos copos de nieve (Efecto hielo)
                pLevel.addParticle(
                        ParticleTypes.SNOWFLAKE,
                        pPlayer.getX() + offsetX,
                        pPlayer.getY() + 1.0 + offsetY,
                        pPlayer.getZ() + offsetZ,
                        speedX, speedY, speedZ
                );

                // Añadimos partículas de la Vara del End (Efecto mágico brillante)
                if (i % 2 == 0) { // Solo la mitad para no saturar
                    pLevel.addParticle(
                            ParticleTypes.END_ROD,
                            pPlayer.getX() + offsetX,
                            pPlayer.getY() + 1.0 + offsetY,
                            pPlayer.getZ() + offsetZ,
                            speedX, speedY, speedZ
                    );
                }
            }
        }

        return InteractionResultHolder.sidedSuccess(itemstack, pLevel.isClientSide());
    }

    private void spawnZombie(Level pLevel, double x, double y, double z, float yaw) {
        var entity = EntityType.ZOMBIE.create(pLevel);
        if (entity != null) {
            entity.moveTo(x, y, z, yaw, 0.0F);
            pLevel.addFreshEntity(entity);
        }
    }
}
