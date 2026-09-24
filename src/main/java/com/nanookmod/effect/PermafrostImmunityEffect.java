package com.nanookmod.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class PermafrostImmunityEffect extends MobEffect {
    public PermafrostImmunityEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x00FFFF); // Color cian/azul claro
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true; // Siempre activo mientras dure
    }
}