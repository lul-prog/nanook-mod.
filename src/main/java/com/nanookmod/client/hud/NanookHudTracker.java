package com.nanookmod.client.hud;

/**
 * Estado del lado cliente: qué entityId de Nanook (si hay alguno) tiene que
 * mostrar su bossbar custom ahora mismo.
 *
 * Mismo patrón que NecroticKnightHudTracker -- se actualiza desde
 * SyncNanookHudPacket, que el servidor manda cuando el jugador arranca/deja
 * de ver al jefe (el mismo evento que dispara bossEvent.addPlayer /
 * removePlayer del lado servidor).
 *
 * A diferencia del Knight, Nanook usa UNA SOLA barra (vida) -- no hay
 * Corrupción ni Drenaje Vital que apilar debajo, así que el overlay es
 * bastante más simple.
 *
 * IMPORTANTE: guarda solo el entityId, NUNCA la instancia de la entidad --
 * la entidad real se busca en el render (level.getEntity(id)) para no
 * quedarnos con una referencia vieja si se descarga y se vuelve a cargar.
 */
public final class NanookHudTracker {

    private static int activeEntityId = -1;

    private NanookHudTracker() {
    }

    public static void setActive(int entityId) {
        activeEntityId = entityId;
    }

    /** Solo limpia si el que se está yendo es justo el que teníamos activo (evita carreras add/remove cruzadas). */
    public static void clear(int entityId) {
        if (activeEntityId == entityId) {
            activeEntityId = -1;
        }
    }

    public static int getActiveEntityId() {
        return activeEntityId;
    }

    public static boolean hasActive() {
        return activeEntityId != -1;
    }
}
