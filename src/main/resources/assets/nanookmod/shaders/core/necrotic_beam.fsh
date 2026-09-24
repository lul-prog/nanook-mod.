#version 150

uniform vec4 ColorModulator;
uniform float GameTime;

in vec4 vertexColor;
// texCoord0.x (U) = posición a lo ancho de la cara actual del prisma (0 en un borde, 1 en el otro).
// texCoord0.y (V) = posición a lo LARGO del rayo (0 en la espada, 1 en la punta lejana).
// No es una textura real -- NecroticLightningRenderer arma un cubo alargado de verdad
// (no un billboard) y este shader usa esas coordenadas para pintar el patrón por GPU.
in vec2 texCoord0;

out vec4 fragColor;

float hash(float n) {
    return fract(sin(n) * 43758.5453123);
}

void main() {
    float u = texCoord0.x;
    float v = texCoord0.y;

    // Energía viajando desde la espada hacia la punta -- dos ondas de
    // distinta frecuencia/velocidad sumadas para que no se vea como un
    // patrón perfectamente regular (como un solo sin() se ve).
    float flowA = sin(v * 20.0 - GameTime * 8.0) * 0.5 + 0.5;
    float flowB = sin(v * 7.0 - GameTime * 3.0 + 1.7) * 0.5 + 0.5;
    float energy = mix(flowA, flowB, 0.5);

    // Brillo tipo borde (fresnel aproximado sin normales/vista real):
    // más intenso cerca de los bordes de cada cara (u cerca de 0 o 1)
    // que en el centro de la cara -- ayuda a que se lea el volumen del
    // cubo en vez de una superficie plana pareja.
    float edge = abs(u - 0.5) * 2.0;
    float rim = pow(edge, 3.0) * 1.4;

    // Chispazo eléctrico: parpadeo random que cambia de "celda"
    // temporal/espacial cada poquito, no todos los frames (si no, es
    // ruido ilegible en vez de una chispa puntual).
    float crackle = hash(floor(GameTime * 30.0) + floor(v * 10.0));
    crackle = smoothstep(0.85, 1.0, crackle) * 0.6;

    float brightnessBoost = 0.65 + 0.35 * energy + rim + crackle;

    float alpha = vertexColor.a * (0.8 + 0.2 * energy);
    if (alpha <= 0.001) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb * ColorModulator.rgb * brightnessBoost, alpha * ColorModulator.a);
}
