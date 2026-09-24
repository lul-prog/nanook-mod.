package com.nanookmod.client.sky;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Una estrella fugaz FIJA en la bóveda celeste: su posición (una orientación
 * aleatoria alrededor de la cámara) se calcula una sola vez al crearla y
 * jamás vuelve a cambiar. NO es una partícula que viaja por el mundo.
 *
 * La sensación de "movimiento" la da únicamente la textura: un spritesheet
 * con {@link #FRAME_COUNT} cuadros apilados verticalmente (cada uno debe
 * medir lo mismo de ancho que de alto). Cada cierto número de ticks avanza
 * al siguiente cuadro, igual que una animación de sprite 2D clásica.
 */
public class ShootingStarSkySprite {

    // Debe coincidir con cuántos cuadros tiene tu spritesheet apilados
    // verticalmente. tu textura shooting_star.png es 32x224 = 7 cuadros de
    // 32x32, por eso 7.
    public static final int FRAME_COUNT = 7;
    private static final float FRAME_V_STEP = 1.0F / FRAME_COUNT;

    // Cuántos ticks se queda cada cuadro en pantalla antes de pasar al
    // siguiente (2 ticks = 0.1s por cuadro -> toda la animación dura 0.7s).
    private static final int TICKS_PER_FRAME = 2;

    private final float radius;
    private final float alpha;
    private final Quaternionf orientation;

    private int tick;
    private float v0;
    private float v1 = FRAME_V_STEP;
    private boolean finished;

    public ShootingStarSkySprite(RandomSource random) {
        this.radius = 4.0F + random.nextFloat() * 2.0F;
        this.alpha = 0.6F + random.nextFloat() * 0.4F;

        // Esta rotación decide EN QUÉ PARTE del cielo aparece la estrella.
        // Se calcula una sola vez aquí y no se vuelve a tocar: por eso el
        // sprite se queda fijo en su sitio durante toda su vida.
        // El primer ángulo se limita a un cono alrededor de "arriba" (entre
        // -135° y -45°) para que siempre aparezcan en la mitad alta del
        // cielo, nunca cerca del horizonte/suelo.
        this.orientation = new Quaternionf().rotationXYZ(
                random.nextFloat() * -1.5707964F - 0.7853982F,
                random.nextFloat() * 1.5707964F * 0.95F,
                random.nextFloat() * ((float) Math.PI * 2)
        );
    }

    public void tick() {
        this.tick++;
        int frame = (this.tick / TICKS_PER_FRAME) % FRAME_COUNT;
        this.v0 = FRAME_V_STEP * frame;
        this.v1 = this.v0 + FRAME_V_STEP;
        if (frame == FRAME_COUNT - 1) {
            this.finished = true;
        }
    }

    public boolean isFinished() {
        return this.finished;
    }

    /**
     * Agrega el quad de esta estrella al buffer compartido. La poseStack ya
     * debe venir orientada con la matriz de la bóveda celeste (eso lo hace
     * ShootingStarSkyRenderer antes de llamar esto para todas las estrellas).
     */
    public void addToBuffer(BufferBuilder builder, PoseStack poseStack, float globalAlpha) {
        poseStack.pushPose();
        poseStack.mulPose(this.orientation);
        Matrix4f matrix = poseStack.last().pose();

        float a = this.alpha * globalAlpha;

        // y=100 fijo: se dibuja siempre "en el infinito", exactamente igual
        // que el sol/la luna/las estrellas vanilla. Por eso jamás se acerca
        // al jugador ni al terreno, sin importar dónde estés parado.
        builder.vertex(matrix, -radius, 100.0F, -radius).uv(0.0F, v1).color(1F, 1F, 1F, a).endVertex();
        builder.vertex(matrix, radius, 100.0F, -radius).uv(1.0F, v1).color(1F, 1F, 1F, a).endVertex();
        builder.vertex(matrix, radius, 100.0F, radius).uv(1.0F, v0).color(1F, 1F, 1F, a).endVertex();
        builder.vertex(matrix, -radius, 100.0F, radius).uv(0.0F, v0).color(1F, 1F, 1F, a).endVertex();

        poseStack.popPose();
    }
}
