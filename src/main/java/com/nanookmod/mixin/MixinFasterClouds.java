package com.nanookmod.mixin;

import com.nanookmod.event.TwilightNightHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * PEDIDO ORIGINAL: "que las nubes vayan más rápido". Vanilla NO anima las nubes con el correr del
 * tiempo -- están fijas en coordenadas de mundo, así que lo único que las hace "moverse" en el
 * juego normal es que vos (la cámara) te movés.
 *
 * BUG REPORTADO (v1 de este mixin): la aceleración corría SIEMPRE, de día, de noche, con o sin
 * ritual -- porque usaba {@code level.getGameTime()} directo, sin ningún condicional. La idea
 * real era que las nubes "corran" como parte de la ambientación del Necroknight/Luna Crepuscular,
 * no todo el tiempo. v2: ahora solo corre mientras {@link TwilightNightHandler#isTwilightNight}
 * es true (mismo flag que ya usan MixinFrostedSky/MixinFrostedMoon/MixinLightTexture para saber
 * si hay que mezclar el cielo oscurecido), y el offset arranca en CERO exactamente en el instante
 * en que {@link TwilightNightHandler#beginTransition} prende la transición -- usando
 * {@link TwilightNightHandler#getClientTransitionStartGameTime()} como punto de partida en vez de
 * {@code level.getGameTime()} a secas, evitamos que las nubes "salten" de golpe a una posición
 * lejana apenas se activa el evento (con gameTime absoluto, el offset ya sería gigante desde el
 * primer frame porque el mundo ya lleva miles de ticks corridos).
 *
 * Cuando termina el evento (el Knight muere, {@link TwilightNightHandler#setActive} lo apaga), el
 * offset simplemente deja de sumarse y las nubes vuelven a su posición normal (fija en coordenadas
 * de mundo) de un frame a otro -- un salto único e instantáneo, pero coincide con un momento ya de
 * por sí muy visual (fin de la pelea), así que no llama la atención como sí lo haría un salto en
 * medio de un cielo por lo demás tranquilo. Ajustá CLOUD_SPEED_X / CLOUD_SPEED_Z para cambiar qué
 * tan rápido "vuelan" (en bloques por tick -- a 20 ticks/seg, 1.0 acá son 20 bloques/seg de deriva).
 *
 * ===================================================================
 * TÉCNICA
 * ===================================================================
 * {@code LevelRenderer#renderClouds} recibe la posición de cámara (camX, camY, camZ) y arma la
 * malla de nubes centrada ahí. Sumándole un offset a camX/camZ ANTES de que el método haga nada
 * con ellos, conseguimos que la malla se calcule como si la cámara estuviera un poco más adelante
 * de lo que en realidad está -- el efecto visual es idéntico a que las nubes viajen solas, sin
 * tocar textura, culling, blending ni nada más del método.
 *
 * {@code @ModifyVariable} con {@code argsOnly = true} + {@code ordinal} cuenta SOLO los parámetros
 * del método, en su orden de declaración, filtrando por tipo ({@code double}): ordinal 0 = camX,
 * ordinal 1 = camY (no la tocamos -- es la altura de la cámara, no de las nubes), ordinal 2 = camZ.
 * Esto es intencionalmente por POSICIÓN y no por nombre: con mappings oficiales, los nombres de
 * parámetro que ves en el código decompilado (camX, camZ, etc.) no siempre sobreviven el remapeo
 * del refmap en un build real -- atarse a "qué tipo es y en qué orden aparece en la firma" es más
 * robusto que atarse al nombre que Forge le puso al decompilar.
 */
@Mixin(LevelRenderer.class)
public class MixinFasterClouds {

    // Bloques por tick de "viento" extra en cada eje. 0.6 en los dos ejes ~ deriva diagonal
    // pareja, del orden de 12 bloques/seg -- notorio sin ser un tornado. Poné en 0.0 el eje que
    // no quieras mover para una deriva en línea recta (por ejemplo, solo hacia +X).
    private static final double CLOUD_SPEED_X = 0.6D;
    private static final double CLOUD_SPEED_Z = 0.6D;

    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double nanookmod$speedCloudsX(double camX) {
        return camX + windOffset(CLOUD_SPEED_X);
    }

    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private double nanookmod$speedCloudsZ(double camZ) {
        return camZ + windOffset(CLOUD_SPEED_Z);
    }

    private static double windOffset(double blocksPerTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !TwilightNightHandler.isTwilightNight(level)) {
            return 0.0D; // fuera del evento: nubes vanilla normales, sin tocar nada
        }
        long elapsed = level.getGameTime() - TwilightNightHandler.getClientTransitionStartGameTime();
        if (elapsed <= 0L) {
            return 0.0D; // primer frame del evento: arranca desde donde ya estaban, sin salto
        }
        return elapsed * blocksPerTick;
    }
}

