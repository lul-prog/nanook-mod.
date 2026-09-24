package com.nanookmod.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Aturdimiento -- lo aplica el NecroticKnight al conectar el golpe final
 * de la embestida (charge + slash_loop) y el manotazo del escudo
 * (ATTACK_GUARD_SLAP). Ver NecroticKnightEntity#applyChargePulseDamage /
 * #applyGuardSlapHit para dónde se dispara y cuánto dura cada uno.
 *
 * IMPORTANTE: esta clase es solo el efecto en sí (registro, color, y el
 * modificador de atributo que deja la velocidad de movimiento en 0). Todo
 * lo demás -- no poder atacar, no poder saltar, no poder usar items --
 * vive en StunEffectHandler porque MobEffect no tiene hooks directos para
 * interceptar esas acciones (mismo criterio que CorruptionEffect /
 * CorruptionEffectHandler).
 *
 * Para probarlo mientras se ajustan los tiempos del jefe:
 * /effect give @s nanookmod:stun <segundos>
 * (el amplifier no se usa -- el aturdimiento no tiene niveles, o estás
 * stuneado o no).
 */
public class StunEffect extends MobEffect {

    // Gris metálico -- ni el rojo de daño ni el violeta de Corrupción, para
    // que se distinga de un vistazo en el HUD de efectos activos.
    private static final int STUN_COLOR = 0xB0B0B0;

    private static final String MOVEMENT_SPEED_MODIFIER_UUID = "d4e1a6b2-7f3c-4a9e-8b1d-2c3e4f5a6b7c";

    public StunEffect() {
        super(MobEffectCategory.HARMFUL, STUN_COLOR);
        // MULTIPLY_TOTAL en -1 dejar la velocidad final en 0 sin importar
        // botas de velocidad, Prisa, etc. -- no cancela el movimiento a
        // mano (WASD sigue "presionado"), simplemente no hay atributo que
        // lo mueva.
        this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                MOVEMENT_SPEED_MODIFIER_UUID,
                -1.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }
}
