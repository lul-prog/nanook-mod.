#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // Shimmer GLOBAL -- a propósito NO depende de ninguna posición de
    // mundo/cámara, para no repetir los bugs de las dos vueltas
    // anteriores (calcular "distancia al jefe" adentro del shader salía
    // mal de maneras que no pude reproducir sin poder correr el juego).
    // TODA la forma real del anillo (radio actual, grosor, degradado
    // suave en los bordes interior/exterior) ya viene resuelta como
    // geometría de verdad, armada en Java por NecroticWaveRenderer
    // (banda de vértices con alpha 0 en los bordes y 1 en el medio) --
    // acá el shader solo le da un pulso de brillo parejo a toda la onda,
    // usando el alpha que ya viene bakeado por vértice.
    float shimmer = 0.85 + 0.15 * sin(GameTime * 5.0);

    float alpha = vertexColor.a * shimmer;
    if (alpha <= 0.001) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb * ColorModulator.rgb, alpha * ColorModulator.a);
}
