package com.nanookmod.client.model;

import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Base para los GeoModel del mod que agrega lo que hace un cerdo vanilla y
 * los mobs de GeckoLib NO hacen solo: GIRAR LA CABEZA HACIA EL JUGADOR.
 *
 * -------------------------------------------------------------------
 * POR QUÉ HACÍA FALTA
 * -------------------------------------------------------------------
 * Los modelos vanilla (EntityModel) reciben netHeadYaw / headPitch en
 * setupAnim y los aplican a mano al cubo de la cabeza. GeckoLib NO lo hace
 * por su cuenta: reproduce el .animation.json y nada más. Si la animación
 * no mueve la cabeza, la cabeza queda pegada al cuerpo para siempre --
 * exactamente lo que estabas viendo.
 *
 * GeckoLib sí te PASA los datos (EntityModelData, vía DataTickets), pero
 * sos vos quien tiene que aplicarlos en setCustomAnimations. Eso es lo que
 * hace esta clase, una sola vez, para todos los mobs.
 *
 * -------------------------------------------------------------------
 * CÓMO USARLA
 * -------------------------------------------------------------------
 *   public class GnutModel extends HeadTrackingGeoModel<GnutEntity> {
 *       @Override protected String headBoneName() { return "hi_head"; }
 *       ... (los 3 getXResource de siempre)
 *   }
 *
 * El nombre del hueso es el del .geo.json, y cambia por modelo:
 *   nanook / skeletal_mage / skeletal_warrior / necrotic_knight -> "h_head"
 *   gnut / snowy_blizz                                          -> "hi_head"
 *   crow                                                        -> "head"
 *
 * -------------------------------------------------------------------
 * DETALLES DE IMPLEMENTACIÓN QUE IMPORTAN
 * -------------------------------------------------------------------
 *  - SUMA sobre la pose de la animación (setRotX(getRotX() + ...)) en vez
 *    de pisarla. Si la pisara, cualquier animación que mueva la cabeza
 *    (rugidos, ataques, idle con respiración) se rompería.
 *  - Recorta el giro (MAX_YAW / MAX_PITCH). Sin recorte, un jugador parado
 *    justo detrás hace que la cabeza dé la vuelta 180° y se vea el cuello
 *    roto del revés.
 *  - HEAD_FOLLOW_FACTOR < 1: la cabeza no sigue al jugador al 100%, sigue
 *    la mayor parte. Da sensación de peso en vez de torreta.
 *  - shouldTrackHead() permite apagarlo en momentos donde la animación
 *    manda (embestidas, saltos, cinemáticas).
 */
public abstract class HeadTrackingGeoModel<T extends GeoAnimatable> extends GeoModel<T> {

    /** Cuánto del giro real del cuello se aplica (1.0 = torreta perfecta). */
    protected static final float HEAD_FOLLOW_FACTOR = 0.85F;
    /** Tope de giro horizontal, en grados. */
    protected static final float MAX_YAW = 70.0F;
    /** Tope de giro vertical, en grados. */
    protected static final float MAX_PITCH = 40.0F;

    /** Nombre del hueso de la cabeza en el .geo.json. */
    protected abstract String headBoneName();

    /**
     * Devolvé false para congelar la cabeza en momentos puntuales (por
     * ejemplo mientras el mob está en plena embestida y la animación ya se
     * encarga de la pose). Por defecto siempre sigue al jugador.
     */
    protected boolean shouldTrackHead(T animatable) {
        return true;
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        if (animationState == null || !shouldTrackHead(animatable)) {
            return;
        }

        CoreGeoBone head = getAnimationProcessor().getBone(headBoneName());
        if (head == null) {
            // Nombre de hueso equivocado: mejor no hacer nada que reventar
            // el render. Si la cabeza no sigue al jugador, revisá acá
            // primero (abrí el .geo.json y buscá el nombre real).
            return;
        }

        EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (data == null) {
            return;
        }

        float yaw = Mth.clamp(data.netHeadYaw() * HEAD_FOLLOW_FACTOR, -MAX_YAW, MAX_YAW);
        float pitch = Mth.clamp(data.headPitch() * HEAD_FOLLOW_FACTOR, -MAX_PITCH, MAX_PITCH);

        head.setRotX(head.getRotX() + pitch * Mth.DEG_TO_RAD);
        head.setRotY(head.getRotY() + yaw * Mth.DEG_TO_RAD);
    }
}
