package com.nanookmod.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class PermafrostEffect extends MobEffect {

    public PermafrostEffect() {
        super(MobEffectCategory.HARMFUL, 0x88CCFF); // Color azul hielo
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // El daño se maneja en el evento del jugador, no aquí
        // Este método se llama cada tick mientras el efecto está activo
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true; // Se aplica cada tick
    }
}
