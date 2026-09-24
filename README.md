# Nanook Mod — Proyecto base (Forge 1.20.1 + GeckoLib)

## Qué incluye este proyecto

- Estructura completa de un mod de Forge para Minecraft 1.20.1
- Dependencia de GeckoLib ya configurada en `build.gradle`
- La entidad `NanookEntity` con:
  - Stats copiados del archivo original de MythicMobs (vida 500, daño 10, velocidad 0.2, etc.)
  - Movimiento básico (persigue y ataca al jugador) heredado de las clases vanilla de Forge
  - El "puente" de animación (GeckoLib) ya conectado a las animaciones `idle` y `walk`
- Registro de entidad, renderer, y archivos de idioma

## Lo que NO incluye todavía (próximos pasos)

- El modelo `.geo.json` y la animación `.animation.json` reales — hay que exportarlos
  desde Blockbench (ver paso 2 abajo)
- La textura `.png` de Nanook
- Las habilidades especiales (carga de hielo, proyectiles de garra, salto, etc.) —
  estas se añadirán como clases de "Goals" personalizadas en el siguiente paso

## Paso 1: Abrir el proyecto en IntelliJ

1. Descarga/copia esta carpeta completa (`nanook-mod`) a tu PC.
2. Abre IntelliJ IDEA → "Open" → selecciona la carpeta `nanook-mod`.
3. IntelliJ detectará que es un proyecto Gradle y empezará a descargar Forge y
   GeckoLib automáticamente (puede tardar varios minutos la primera vez).
4. Si te pregunta qué JDK usar, selecciona **Java 17** (Forge 1.20.1 lo requiere,
   aunque tengas Java 21 instalado para otras cosas — puedes tener ambos a la vez).
5. Una vez que Gradle termine de sincronizar, en el panel derecho de Gradle busca:
   `Tasks > forgegradle runs > runClient`
   Dale doble clic. Esto debería abrir un Minecraft de prueba con el mod cargado
   (sin el modelo de Nanook todavía, pero sin errores).

## Paso 2: Exportar el modelo de Nanook desde Blockbench

1. Abre Blockbench (gratis, blockbench.net).
2. Instala el plugin **GeckoLib Animation Utils** desde el menú de plugins de Blockbench.
3. Abre `nocsy_icebear_boss.bbmodel` (el archivo del pack que ya tienes).
4. Con el plugin activo, exporta como "Geckolib Model" — esto genera el `.geo.json`.
5. Exporta las animaciones como "Geckolib Animation" — esto genera el `.animation.json`.
6. Copia los archivos generados a:
   - `src/main/resources/assets/nanookmod/geckolib_models/entity/nanook.geo.json`
   - `src/main/resources/assets/nanookmod/geckolib_animations/entity/nanook.animation.json`
7. Exporta la textura del modelo como PNG y cópiala a:
   - `src/main/resources/assets/nanookmod/textures/entity/nanook.png`

## Paso 3: Probar

Vuelve a correr `runClient` y usa el comando de Minecraft:
```
/summon nanookmod:nanook
```
Si todo está bien conectado, deberías ver a Nanook aparecer con su modelo y
animación de idle/walk funcionando.

## Notas importantes

- Los nombres de animación en `NanookEntity.java` (`idle`, `walk`) deben coincidir
  EXACTO con los nombres dentro del archivo `.animation.json` exportado. Ya confirmamos
  que el .bbmodel original usa esos mismos nombres, así que no debería haber que cambiar nada.
- Si Gradle falla la primera sincronización, revisa tu conexión a internet — necesita
  descargar Forge (~200MB) y las librerías de GeckoLib la primera vez.
