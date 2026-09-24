package com.nanookmod.event;

import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Todo lo que el Stun necesita del mundo pero que MobEffect no puede
 * resolver por sí solo (ver StunEffect para el resto: registro, color, y
 * el atributo de velocidad de movimiento).
 *
 * 1) No puede atacar mientras dura -- cancela AttackEntityEvent. La
 *    velocidad en 0 ya lo inmoviliza, pero sin esto igual podría pegar
 *    parado en el sitio.
 *
 * 2) No puede saltar NI avanzar por inercia -- LivingJumpEvent ya dispara
 *    el salto ANTES de que podamos cancelarlo (no es cancelable), así que en
 *    vez de eso le anulamos TODA la velocidad (vertical y horizontal) que el
 *    salto le acaba de dar: si solo cortáramos la Y, el jugador heredaría el
 *    impulso del avance y podría "surfear" el stun saltando en diagonal,
 *    porque en el aire el atributo de velocidad a 0 no frena la inercia.
 *    Además, cada tick se recorta a 0 cualquier velocidad horizontal que el
 *    stuneado acumule por otras vías (empujones, agua...), dejando intacta
 *    la vertical para no romper la caída libre.
 *
 * 3) No puede usar items -- en vez de escuchar cada evento de interacción
 *    por separado (RightClickItem, RightClickBlock, UseItem...), cada
 *    tick que el jugador sigue stuneado le reaplicamos un cooldown de
 *    vanilla (2 ticks) sobre la mano principal Y la secundaria. Como se
 *    vuelve a poner en cuanto se libera, en la práctica nunca se libera
 *    mientras el efecto siga activo -- y de paso el jugador VE el swipe
 *    gris en la hotbar, que es señal más clara de "no puedo usar esto
 *    ahora" que un click que simplemente no hace nada. stopUsingItem()
 *    además corta en seco cualquier arco/comida/escudo que ya estuviera
 *    a medio usar cuando llegó el golpe.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StunEffectHandler {

    private static final int ITEM_COOLDOWN_TICKS = 2;

    // Por debajo de esto la velocidad horizontal es simplemente fracción de
    // bloque residual (deslizamiento al parar, agua...) y no merece un write.
    private static final double HORIZONTAL_EPSILON = 1.0E-4;

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !entity.hasEffect(ModEffects.STUN.get())) {
            return;
        }

        // Red de seguridad: si por cualquier vía (ímpetu heredado del salto,
        // empujones, corrientes de agua...) el stuneado acumula velocidad
        // horizontal, se la recortamos a 0. La vertical NO se toca para no
        // romper la caída libre ni el daño por caída. Va antes del gate de
        // Player porque aplica a cualquier LivingEntity.
        Vec3 delta = entity.getDeltaMovement();
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horiz > HORIZONTAL_EPSILON) {
            entity.setDeltaMovement(new Vec3(0, delta.y, 0));
        }

        if (!(entity instanceof Player player)) {
            return;
        }

        player.stopUsingItem();

        ItemStack mainhand = player.getMainHandItem();
        if (!mainhand.isEmpty()) {
            player.getCooldowns().addCooldown(mainhand.getItem(), ITEM_COOLDOWN_TICKS);
        }
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            player.getCooldowns().addCooldown(offhand.getItem(), ITEM_COOLDOWN_TICKS);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity().hasEffect(ModEffects.STUN.get())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.hasEffect(ModEffects.STUN.get())) {
            // Anulamos TAMBIÉN la horizontal, no solo la vertical: el salto
            // hereda el impulso que llevaba el jugador por ir avanzando, y si
            // solo cortáramos la Y, podría "surfear" el stun saltando en
            // diagonal (en el aire MULTIPLY_TOTAL no frena la inercia).
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }
}