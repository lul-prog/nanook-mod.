package com.nanookmod.mixin;

import com.nanookmod.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Congela la cámara (girar con el mouse) mientras el jugador tiene el
 * efecto Stun. StunEffect ya deja la velocidad de movimiento en 0 (vía
 * atributo) y StunEffectHandler cancela ataques/items, pero nada de eso
 * toca el giro de cámara -- mirar alrededor es un input 100% de cliente
 * que vanilla procesa en MouseHandler#turnPlayer() cada frame, y Forge no
 * expone un evento para interceptarlo (a diferencia de clics o scroll).
 * Por eso hace falta Mixin acá, igual que MixinEffectRenderingInventoryScreen.
 *
 * @Inject en HEAD + cancel corta el método ANTES de que lea el delta
 * acumulado del mouse, así que yaw/pitch de la cámara quedan exactamente
 * donde estaban ese frame.
 *
 * OJO con accumulatedDX/DY: MouseHandler los va sumando en cada callback
 * de movimiento del mouse (onMove), y turnPlayer() normalmente los
 * consume y los vuelve a poner en 0. Si solo cancelamos el método sin
 * tocarlos, el delta de TODO el tiempo que dura el stun se queda
 * acumulado sin consumir, y en cuanto el efecto termina se aplicaría de
 * golpe -- un salto brusco de cámara. Por eso los reseteamos a mano cada
 * frame mientras el stun sigue activo: al terminar, la cámara sigue
 * desde donde el mouse esté EN ESE MOMENTO, sin salto.
 */
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void nanookmod$blockTurnWhileStunned(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null && player.hasEffect(ModEffects.STUN.get())) {
            this.accumulatedDX = 0.0D;
            this.accumulatedDY = 0.0D;
            ci.cancel();
        }
    }
}
