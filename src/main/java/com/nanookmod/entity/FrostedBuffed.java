package com.nanookmod.entity;

/**
 * Implementada por los mobs propios del mod que pueden recibir el buff de la
 * Noche Escarchada (ver FrostedNightMobBuffHandler). El estado se guarda vía
 * SynchedEntityData en cada clase (no aquí, las interfaces no pueden tener
 * campos), para que se sincronice del servidor al cliente y así el cliente
 * sepa cuándo dibujar la partícula de "potenciado".
 */
public interface FrostedBuffed {
    boolean isFrostedBuffed();

    void setFrostedBuffed(boolean buffed);
}
