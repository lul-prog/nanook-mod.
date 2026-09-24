package com.nanookmod.client;

import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModBiomes;
import com.nanookmod.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Supplier;

/**
 * Capas extra de ambientación del bioma "bosque crepuscular" que el sistema
 * vanilla de ambient_sound/additions_sound no puede dar por sí solo (un solo
 * sonido base + un solo "extra" con tick_chance, sin control de volumen). El
 * viento fuerte sigue yendo por ambient_sound en el JSON del bioma; cuervos,
 * ramas, cadenas y susurros se disparan todos aquí, cada uno en su propio
 * ciclo aleatorio, con pitch/volumen variables y ocasional "eco" rápido para
 * que no suene mecánico.
 *
 * Se usa ClientTickEvent (no PlayerTickEvent) a propósito: en singleplayer,
 * PlayerTickEvent se postea tanto para el lado lógico del cliente como para
 * el del servidor integrado, y filtrar por "side" es frágil. ClientTickEvent
 * garantiza un solo disparo por tick de juego.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CrepuscularAmbienceHandler {

    private static final RandomSource RANDOM = RandomSource.create();

    // Cada capa: {minTicks, maxTicks} entre reproducciones. 20 ticks = 1 segundo.
    private static final Layer CROW = new Layer(() -> ModSounds.AMBIENT_CREPUSCULAR_CROW.get(), 20 * 25, 20 * 60, 0.6f, 1.0f);
    private static final Layer BRANCH_CREAK = new Layer(() -> ModSounds.AMBIENT_CREPUSCULAR_BRANCH_CREAK.get(), 20 * 20, 20 * 45, 0.6f, 1.0f);
    private static final Layer CHAINS = new Layer(() -> ModSounds.AMBIENT_CREPUSCULAR_CHAINS.get(), 20 * 60, 20 * 130, 0.6f, 1.0f);
    private static final Layer WHISPERS = new Layer(() -> ModSounds.AMBIENT_CREPUSCULAR_WHISPERS.get(), 20 * 20, 20 * 50, 0.85f, 1.15f);

    private static final Layer[] LAYERS = {CROW, BRANCH_CREAK, CHAINS, WHISPERS};

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            resetAll();
            return;
        }

        var biome = player.level().getBiome(player.blockPosition());
        boolean inBiome = biome.is(ModBiomes.BOSQUE_CREPUSCULAR_KEY)
                || biome.is(ModBiomes.BOSQUE_CREPUSCULAR_RIVER_KEY);

        for (Layer layer : LAYERS) {
            layer.tick(player, inBiome);
        }
    }

    private static void resetAll() {
        for (Layer layer : LAYERS) layer.cooldown = -1;
    }

    private static class Layer {
        private final Supplier<SoundEvent> sound;
        private final int minDelay;
        private final int maxDelay;
        private final float minVolume;
        private final float maxVolume;
        private int cooldown = -1; // -1 = todavía sin inicializar

        // Probabilidad de que, tras sonar, se agende un "eco" corto (3-6s) en vez
        // del intervalo normal — simula p.ej. un cuervo que llama dos veces seguidas.
        private static final float QUICK_REPEAT_CHANCE = 0.18f;

        Layer(Supplier<SoundEvent> sound, int minDelay, int maxDelay, float minVolume, float maxVolume) {
            this.sound = sound;
            this.minDelay = minDelay;
            this.maxDelay = maxDelay;
            this.minVolume = minVolume;
            this.maxVolume = maxVolume;
        }

        void tick(LocalPlayer player, boolean inBiome) {
            if (!inBiome) {
                cooldown = -1; // al salir del bioma se reinicia, para no acumular disparos atrasados
                return;
            }

            if (cooldown < 0) {
                cooldown = rollDelay();
                return;
            }

            if (cooldown > 0) {
                cooldown--;
                return;
            }

            // Pitch bien variado: a veces más grave, a veces más agudo
            float pitch = 0.75f + RANDOM.nextFloat() * 0.55f;
            float volume = minVolume + RANDOM.nextFloat() * (maxVolume - minVolume);
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                    sound.get(), SoundSource.AMBIENT, volume, pitch, false);

            cooldown = RANDOM.nextFloat() < QUICK_REPEAT_CHANCE
                    ? 20 * 3 + RANDOM.nextInt(20 * 3) // eco corto: 3-6s
                    : rollDelay();
        }

        private int rollDelay() {
            return minDelay + RANDOM.nextInt(maxDelay - minDelay + 1);
        }
    }
}
