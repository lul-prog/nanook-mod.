package com.nanookmod.client.model;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.NecroticKnightEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;

import java.util.HashMap;
import java.util.Map;

/**
 *   assets/nanookmod/geo/entity/necrotic_knight.geo.json
 *   assets/nanookmod/textures/entity/necrotic_knight.png
 *   assets/nanookmod/animations/entity/necrotic_knight.animation.json
 *
 * ===================================================================
 * EL BRAZO DEL RAYO -- por qué no se levantaba y cómo funciona ahora
 * ===================================================================
 * PEDIDO: que el brazo de la espada suba y baje según la altura y la distancia del jugador, igual
 * que en el Nameless Guardian el rayo sale hacia donde apunta la cabeza. Acá el rayo sale hacia
 * donde apunta el jefe (yaw + pitch, ver NecroticLightningBeamEntity), y el brazo tiene que
 * apuntar a lo mismo.
 *
 * POR QUÉ LA VERSIÓN ANTERIOR NO FUNCIONABA. Sumaba un ángulo a rotX del hueso (repartido con
 * seno/coseno según el roll de Z). Pero GeckoLib compone la rotación de un hueso como
 * Rz * Ry * Rx: la X es la primera que se aplica, o sea, gira alrededor del eje X del propio
 * brazo YA rodado. Con el roll de -60.6° que trae la pose del canalizado, sumarle a la X no da un
 * cabeceo "vertical": da una diagonal, y un ajuste con seno/coseno solo lo aproxima. No se puede
 * arreglar sumando ángulos sueltos: hay que componer las ROTACIONES.
 *
 * QUÉ HACE AHORA (cada frame, solo durante el canalizado):
 *   1. Con las rotaciones ACTUALES de los huesos (ya con la animación aplicada) calcula la
 *      dirección real de la espada: cadena necroknight > knight > body > left_arm > weapon,
 *      con la misma composición Rz*Ry*Rx que usa GeckoLib para dibujar. La hoja apunta a -Z
 *      en reposo (el cubo de la hoja va de z=0 a z=12 y la empuñadura está en z>12).
 *   2. Mide su elevación actual y decide la elevación objetivo: la del rayo (-pitch).
 *   3. Calcula la rotación mínima que lleva la dirección actual a la deseada (misma orientación
 *      horizontal, otra elevación) y la traslada al marco del brazo:
 *          arm' = P^-1 * delta * P * arm        (P = rotación acumulada de los huesos padre)
 *   4. Descompone arm' de vuelta a rotX/rotY/rotZ (mismo orden ZYX) y los escribe.
 *   Es exacto, no una aproximación: la espada termina apuntando a la elevación pedida sea cual
 *   sea la pose que traiga la animación. Y NO depende de las convenciones de signo de los ejes de
 *   Blockbench/GeckoLib, porque trabaja con los valores ya guardados en los huesos.
 *
 * SUAVIZADO: la elevación se acerca al objetivo de a poco (AIM_SMOOTHING). Sin eso el brazo
 * "saltaba" de la pose de la animación a la del rayo en el primer frame del canalizado.
 *
 * SI SE MUEVE AL REVÉS (sube cuando debería bajar): ARM_PITCH_SIGN = -1. Es el único supuesto que
 * no puedo comprobar sin ejecutar el juego: que "arriba" sea +Y en el marco donde GeckoLib
 * aplica las rotaciones de los huesos.
 */
public class NecroticKnightModel extends HeadTrackingGeoModel<NecroticKnightEntity> {

    // Cadena de huesos de la espada (de arriba a abajo), según necrotic_knight.geo.json.
    private static final String[] PARENT_CHAIN = {"necroknight", "knight", "body"};
    private static final String ARM_BONE = "left_arm";
    private static final String WEAPON_BONE = "weapon";

    /** La punta de la hoja en el espacio local del hueso "weapon": apunta a -Z. */
    private static final double[] BLADE_TIP_LOCAL = {0.0D, 0.0D, -1.0D};

    /** Poné -1 si el brazo se mueve al revés (ver comentario de la clase). */
    private static final float ARM_PITCH_SIGN = 1.0F;

    /** Tope de la corrección, en grados de elevación. */
    private static final float MAX_AIM_ELEVATION_DEGREES = 55.0F;
    /** Fracción del camino al objetivo que se recorre en cada frame (0..1). */
    private static final float AIM_SMOOTHING = 0.30F;

    /** Elevación suavizada de la espada (grados), por entidad: el modelo es uno solo para todos los Knights. */
    private final Map<Integer, Float> smoothedElevation = new HashMap<>();

    @Override
    protected String headBoneName() {
        return "h_head";
    }

    /**
     * Mientras canaliza el rayo, la pose de la cabeza la manda la animación (mira a lo largo de la
     * hoja). Sumarle el seguimiento del jugador ahí rompe la lectura del ataque.
     */
    @Override
    protected boolean shouldTrackHead(NecroticKnightEntity animatable) {
        return !animatable.isLightningChanneling();
    }

    @Override
    public ResourceLocation getModelResource(NecroticKnightEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "geo/entity/necrotic_knight.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(NecroticKnightEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "textures/entity/necrotic_knight.png");
    }

    @Override
    public ResourceLocation getAnimationResource(NecroticKnightEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "animations/entity/necrotic_knight.animation.json");
    }

    /**
     * Animación PROCEDURAL -- corre DESPUÉS de que GeckoLib aplicó la animación base.
     * super.setCustomAnimations(...) es el seguimiento de cabeza de HeadTrackingGeoModel; si lo
     * sacás, el Knight vuelve a mirar siempre al frente.
     */
    @Override
    public void setCustomAnimations(NecroticKnightEntity animatable, long instanceId,
                                    AnimationState<NecroticKnightEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // Solo durante el CANALIZADO. OJO: isLightningChanneling() (SynchedEntityData, confiable en
        // el cliente), NO isLightningWindingUp(), que lee attackTicksElapsed y del lado del cliente
        // se queda pegado en 0 para siempre.
        if (!animatable.isLightningChanneling()) {
            smoothedElevation.remove(animatable.getId());
            return;
        }

        CoreGeoBone arm = this.getAnimationProcessor().getBone(ARM_BONE);
        CoreGeoBone weapon = this.getAnimationProcessor().getBone(WEAPON_BONE);
        if (arm == null || weapon == null) {
            return;
        }

        // 1) Rotación acumulada de los huesos PADRE (P).
        double[][] parent = identity();
        for (String name : PARENT_CHAIN) {
            CoreGeoBone bone = this.getAnimationProcessor().getBone(name);
            if (bone != null) {
                parent = multiply(parent, rotationZYX(bone.getRotZ(), bone.getRotY(), bone.getRotX()));
            }
        }
        double[][] armRot = rotationZYX(arm.getRotZ(), arm.getRotY(), arm.getRotX());
        double[][] weaponRot = rotationZYX(weapon.getRotZ(), weapon.getRotY(), weapon.getRotX());

        // 2) Hacia dónde apunta la espada AHORA (en el espacio del modelo).
        double[][] total = multiply(multiply(parent, armRot), weaponRot);
        double[] dir = apply(total, BLADE_TIP_LOCAL);
        double horizontal = Math.hypot(dir[0], dir[2]);
        if (horizontal < 1.0E-4D) {
            return; // apunta casi vertical: la orientación horizontal no está definida
        }
        double currentElevationDeg = Math.toDegrees(Math.atan2(dir[1], horizontal));

        // 3) Elevación objetivo: la del rayo. getSyncedLightningPitch() es el xRot del jefe
        //    (positivo = mirando hacia abajo), y la elevación es positiva hacia arriba.
        float targetDeg = Mth.clamp(ARM_PITCH_SIGN * -animatable.getSyncedLightningPitch(),
                -MAX_AIM_ELEVATION_DEGREES, MAX_AIM_ELEVATION_DEGREES);

        float smoothed = smoothedElevation.getOrDefault(animatable.getId(), (float) currentElevationDeg);
        smoothed += (targetDeg - smoothed) * AIM_SMOOTHING;
        smoothedElevation.put(animatable.getId(), smoothed);

        // 4) Dirección deseada: la misma orientación horizontal, pero con la elevación suavizada.
        double elevRad = Math.toRadians(smoothed);
        double[] wanted = {
                dir[0] / horizontal * Math.cos(elevRad),
                Math.sin(elevRad),
                dir[2] / horizontal * Math.cos(elevRad)
        };

        // 5) Rotación mínima dir -> wanted, llevada al marco del brazo: arm' = P^T * delta * P * arm.
        double[][] delta = rotationBetween(dir, wanted);
        double[][] newArm = multiply(multiply(multiply(transpose(parent), delta), parent), armRot);

        double[] euler = eulerZYX(newArm);
        arm.setRotX((float) euler[0]);
        arm.setRotY((float) euler[1]);
        arm.setRotZ((float) euler[2]);
    }

    // ---------------------------------------------------------------
    // Matemática 3x3 mínima (doubles). Convención GeckoLib: R = Rz * Ry * Rx.
    // ---------------------------------------------------------------

    private static double[][] identity() {
        return new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
    }

    private static double[][] rotationZYX(double z, double y, double x) {
        double cx = Math.cos(x), sx = Math.sin(x);
        double cy = Math.cos(y), sy = Math.sin(y);
        double cz = Math.cos(z), sz = Math.sin(z);
        double[][] rx = {{1, 0, 0}, {0, cx, -sx}, {0, sx, cx}};
        double[][] ry = {{cy, 0, sy}, {0, 1, 0}, {-sy, 0, cy}};
        double[][] rz = {{cz, -sz, 0}, {sz, cz, 0}, {0, 0, 1}};
        return multiply(multiply(rz, ry), rx);
    }

    private static double[][] multiply(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
            }
        }
        return r;
    }

    private static double[][] transpose(double[][] m) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i][j] = m[j][i];
            }
        }
        return r;
    }

    private static double[] apply(double[][] m, double[] v) {
        return new double[]{
                m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
                m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
                m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]
        };
    }

    /** Rotación mínima que lleva el vector unitario a hacia el vector unitario b (fórmula de Rodrigues). */
    private static double[][] rotationBetween(double[] a, double[] b) {
        double ax = a[1] * b[2] - a[2] * b[1];
        double ay = a[2] * b[0] - a[0] * b[2];
        double az = a[0] * b[1] - a[1] * b[0];
        double sin = Math.sqrt(ax * ax + ay * ay + az * az);
        double cos = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        if (sin < 1.0E-9D) {
            return identity(); // ya coinciden (el caso opuesto no se da: solo cambia la elevación)
        }
        double kx = ax / sin, ky = ay / sin, kz = az / sin;
        double oneMinusCos = 1.0D - cos;
        return new double[][]{
                {cos + kx * kx * oneMinusCos, kx * ky * oneMinusCos - kz * sin, kx * kz * oneMinusCos + ky * sin},
                {ky * kx * oneMinusCos + kz * sin, cos + ky * ky * oneMinusCos, ky * kz * oneMinusCos - kx * sin},
                {kz * kx * oneMinusCos - ky * sin, kz * ky * oneMinusCos + kx * sin, cos + kz * kz * oneMinusCos}
        };
    }

    /**
     * Inversa de rotationZYX: devuelve {x, y, z} tales que rotationZYX(z, y, x) reproduce la matriz.
     * Para R = Rz(g) Ry(b) Rx(a):  R20 = -sin b,  R21 = cos b sin a,  R22 = cos b cos a,
     * R10 = sin g cos b,  R00 = cos g cos b.
     */
    private static double[] eulerZYX(double[][] m) {
        double sinB = Mth.clamp(-m[2][0], -1.0D, 1.0D);
        double beta = Math.asin(sinB);
        double alpha;
        double gamma;
        if (Math.abs(Math.cos(beta)) > 1.0E-6D) {
            alpha = Math.atan2(m[2][1], m[2][2]);
            gamma = Math.atan2(m[1][0], m[0][0]);
        } else {
            // Gimbal lock (el brazo apuntando recto arriba/abajo): se fija gamma = 0.
            alpha = Math.atan2(-m[1][2], m[1][1]);
            gamma = 0.0D;
        }
        return new double[]{alpha, beta, gamma};
    }
}
