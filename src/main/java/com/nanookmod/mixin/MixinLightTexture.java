package com.nanookmod.mixin;

import com.nanookmod.client.NanookNightTintHandler;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replica el efecto de la Luna de Sangre de Confluence Otherworld para
 * nuestras dos noches especiales (Escarchada y Crepuscular), pero SIN
 * MixinExtras (esa librería no está agregada a este proyecto - ver
 * build.gradle). Confluence usa @ModifyExpressionValue (de MixinExtras)
 * sobre la llamada a Vector3f#lerp dentro de
 * LightTexture#updateLightTexture, y sobreescribe el resultado con
 * (1,0,0) cuando la Luna de Sangre está activa.
 *
 * @ModifyExpressionValue no existe en el Mixin "de base" que ya usa este
 * proyecto (org.spongepowered.asm.mixin, sin extras) - el equivalente que
 * SÍ tenemos disponible es @Redirect sobre la MISMA llamada: en vez de
 * dejar pasar la llamada original y solo tocar el valor que devuelve,
 * @Redirect reemplaza la llamada entera, así que replicamos el lerp
 * original a mano (`original.lerp(target, delta)`) y DESPUÉS
 * sobreescribimos si corresponde. El resultado final es idéntico.
 *
 * Por qué esto tiñe "todo el mundo" y no solo la pantalla: este método
 * recalcula la textura de 16x16 (LightTexture) que Minecraft usa para
 * convertir cualquier combinación de luz de bloque + luz de cielo en un
 * color final - literalmente el lightmap que ilumina cada bloque y
 * entidad dibujados en pantalla. Sobreescribir el color base acá afecta a
 * todo lo ya renderizado, con el brillo de antorchas/luz de cielo
 * conservado (el bucle que multiplica por brillo sigue corriendo normal
 * después de esto), a diferencia de un overlay de pantalla que solo suma
 * un velo de color plano encima de la imagen final.
 *
 * ORDINAL: LightTexture#updateLightTexture puede tener más de una llamada
 * a Vector3f#lerp (por ejemplo el ajuste de "forceBrightLightmap" del
 * dimension type). ordinal=0 apunta a la PRIMERA - en el mapping oficial
 * de 1.20.1 esa es la que fija el color ambiente base ANTES del bucle de
 * 16x16 que multiplica por brillo (que es justo la que queremos). Si al
 * probar ves que se aplana el contraste antorcha/oscuridad (todo el mapa
 * de luz queda exactamente del mismo color sin importar brillo) en vez de
 * solo teñirse conservando ese contraste, es que en tu compilación el
 * ordinal correcto es otro (probá 1) - avisame y lo ajustamos.
 */
@Mixin(LightTexture.class)
public class MixinLightTexture {

    @Redirect(
            method = "updateLightTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Vector3f;lerp(Lorg/joml/Vector3fc;F)Lorg/joml/Vector3f;",
                    ordinal = 0
            )
    )
    private Vector3f nanookmod$tintAmbientLight(Vector3f original, Vector3fc target, float delta) {
        // Replica el lerp de vainilla tal cual - así si NINGUNA noche
        // especial está activa, el comportamiento es 100% idéntico a no
        // tener este mixin.
        original.lerp(target, delta);

        Vector3f override = NanookNightTintHandler.getActiveTint();
        if (override != null) {
            float blend = NanookNightTintHandler.getActiveTintBlend();
            if (blend >= 1.0F) {
                original.set(override);
            } else if (blend > 0.0F) {
                // v2: en vez de pisar el color de un salto, lo va
                // mezclando -- con blend=0 el lightmap queda 100% vainilla
                // (el ritual recién empezó), con blend=1 es el tinte
                // completo. Vector3f#lerp acepta un Vector3fc, así que
                // 'override' (ya es un Vector3f) sirve directo como target.
                original.lerp(override, blend);
            }
            // blend == 0: no tocar nada, dejar el resultado vainilla.
        }

        return original;
    }
}
