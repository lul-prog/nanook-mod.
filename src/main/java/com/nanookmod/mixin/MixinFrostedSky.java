package com.nanookmod.mixin;

import com.nanookmod.event.FrostedNightHandler;
import com.nanookmod.event.TwilightNightHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Color de la Luna Crepuscular puesto como un violeta/verde necrótico
 * oscuro de arranque (0.28, 0.10, 0.30) - es SOLO un valor de partida,
 * cambialo a gusto en FROSTED... digo, TWILIGHT_SKY_COLOR de acá abajo.
 *
 * v2: en vez de PISAR el color con un corte binario, mezcla gradualmente
 * usando TwilightNightHandler#getTransitionProgress. Con progreso 0 el
 * cielo se ve exactamente como lo hubiera calculado el juego (cir ya trae
 * ese valor vainilla, ni lo tocamos); con progreso 1, es el color
 * crepuscular puro. En el medio, un lerp -- así el "empieza a atardecer"
 * se ve de verdad como un atardecer y no como un interruptor.
 */
@Mixin(ClientLevel.class)
public class MixinFrostedSky {

    private static final Vec3 FROSTED_SKY_COLOR = new Vec3(0.62, 0.78, 0.92);
    private static final Vec3 TWILIGHT_SKY_COLOR = new Vec3(0.32, 0.55, 0.42);

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void nanookmod$tintSpecialSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        ClientLevel self = (ClientLevel) (Object) this;

        if (TwilightNightHandler.isTwilightNight(self)) {
            Vec3 vanilla = cir.getReturnValue();
            float t = TwilightNightHandler.getTransitionProgress(self);
            if (t <= 0.0F) {
                return; // todavía no arrancó de verdad -- dejar el cielo tal cual
            }
            cir.setReturnValue(t >= 1.0F ? TWILIGHT_SKY_COLOR : vanilla.lerp(TWILIGHT_SKY_COLOR, t));
            return;
        }
        if (FrostedNightHandler.isFrostedNight(self)) {
            cir.setReturnValue(FROSTED_SKY_COLOR);
        }
    }
}