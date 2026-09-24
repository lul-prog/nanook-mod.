package com.nanookmod.client.hud;

/**
 * Estado del lado cliente: qué entityId de NecroticKnight (si hay alguno)
 * tiene que mostrar su HUD custom (bossbar de vida + bossbar de Corrupción
 * + widget de Drenaje Vital) ahora mismo.
 *
 * Se actualiza desde SyncNecroticKnightHudPacket, que el servidor manda
 * cuando el jugador arranca/deja de ver al jefe (mismo evento que dispara
 * bossEvent.addPlayer/removePlayer del lado servidor). Soporta un solo
 * jefe activo a la vez -- si en algún momento hace falta pelear contra dos
 * a la vez, esto pasa a ser un Set<Integer> y NecroticKnightHudOverlay
 * itera y apila las barras de cada uno; no hace falta ahora.
 *
 * IMPORTANTE: guarda solo el entityId, NUNCA la instancia de la entidad --
 * la entidad real se busca en el render (level.getEntity(id)) para evitar
 * quedarnos con una referencia vieja si la entidad se descarga y se vuelve
 * a cargar con otro objeto.
 */
public final class NecroticKnightHudTracker {

    private static int activeEntityId = -1;

    private NecroticKnightHudTracker() {
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
