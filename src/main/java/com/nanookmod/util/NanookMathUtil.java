package com.nanookmod.util;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilidades matemáticas para la generación del árbol crepuscular (ramas en
 * ángulo real + copa tipo elipsoide). Portado/adaptado del enfoque que usa
 * Eternal Starlight para su árbol lunar (mismo tipo de matemática estándar de
 * rotación 3D y trazado de líneas de Bresenham, reescrito para este mod).
 */
public class NanookMathUtil {

    /**
     * Calcula un punto a cierta distancia (radius) y ángulo (pitch/yaw, en
     * grados) desde un punto de partida. Se usa para hacer que las ramas
     * salgan del tronco en direcciones distintas en vez de solo hacia arriba.
     */
    public static Vec3 rotationToPosition(Vec3 startPos, float radius, float pitch, float yaw) {
        double endPosX = radius * Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        double endPosY = radius * Math.sin(Math.toRadians(pitch));
        double endPosZ = radius * Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        return startPos.add(endPosX, endPosY, endPosZ);
    }

    /**
     * ¿El punto (x,y,z) cae dentro o justo en el borde de un elipsoide con
     * radios (a,b,c)? Se usa para darle forma redondeada/ovalada a la copa.
     */
    public static boolean isPointInOrOnEllipsoid(double x, double y, double z, double a, double b, double c) {
        double value = (x * x) / (a * a) + (y * y) / (b * b) + (z * z) / (c * c);
        return value <= 1.0;
    }

    /**
     * Devuelve todos los puntos de una línea recta 3D entre dos coordenadas
     * enteras (algoritmo de Bresenham en 3D). Se usa para dibujar cada rama
     * como una línea sólida de troncos entre su base y su punta.
     */
    public static List<int[]> getBresenham3DPoints(int x1, int y1, int z1, int x2, int y2, int z2) {
        List<int[]> points = new ArrayList<>();
        points.add(new int[]{x1, y1, z1});

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);
        int xs = x2 > x1 ? 1 : -1;
        int ys = y2 > y1 ? 1 : -1;
        int zs = z2 > z1 ? 1 : -1;

        if (dx >= dy && dx >= dz) {
            int p1 = 2 * dy - dx;
            int p2 = 2 * dz - dx;
            while (x1 != x2) {
                x1 += xs;
                if (p1 >= 0) {
                    y1 += ys;
                    p1 -= 2 * dx;
                }
                if (p2 >= 0) {
                    z1 += zs;
                    p2 -= 2 * dx;
                }
                p1 += 2 * dy;
                p2 += 2 * dz;
                points.add(new int[]{x1, y1, z1});
            }
        } else if (dy >= dx && dy >= dz) {
            int p1 = 2 * dx - dy;
            int p2 = 2 * dz - dy;
            while (y1 != y2) {
                y1 += ys;
                if (p1 >= 0) {
                    x1 += xs;
                    p1 -= 2 * dy;
                }
                if (p2 >= 0) {
                    z1 += zs;
                    p2 -= 2 * dy;
                }
                p1 += 2 * dx;
                p2 += 2 * dz;
                points.add(new int[]{x1, y1, z1});
            }
        } else {
            int p1 = 2 * dy - dz;
            int p2 = 2 * dx - dz;
            while (z1 != z2) {
                z1 += zs;
                if (p1 >= 0) {
                    y1 += ys;
                    p1 -= 2 * dz;
                }
                if (p2 >= 0) {
                    x1 += xs;
                    p2 -= 2 * dz;
                }
                p1 += 2 * dy;
                p2 += 2 * dx;
                points.add(new int[]{x1, y1, z1});
            }
        }
        return points;
    }
}
