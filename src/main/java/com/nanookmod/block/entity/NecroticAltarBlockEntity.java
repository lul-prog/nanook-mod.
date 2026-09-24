package com.nanookmod.block.entity;

import com.nanookmod.entity.NecroticKnightEntity;
import com.nanookmod.event.TwilightNightHandler;
import com.nanookmod.network.ModNetworking;
import com.nanookmod.network.NanookScreenShakePacket;
import com.nanookmod.registry.ModBlockEntities;
import com.nanookmod.registry.ModEntities;
import com.nanookmod.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Altar Necromante -- el sistema de ofrendas para invocar al Necrotic Knight.
 *
 * ===================================================================
 * CÓMO SE USA (jugador)
 * ===================================================================
 * Clic derecho sobre el altar con uno de los tres materiales en la mano lo va
 * ofrendando de a uno. Clic derecho con la MANO VACÍA muestra el estado
 * actual (qué falta, cuánto tenés de cada cosa) como un mensaje de acción
 * -- el mismo tipo de texto que ya usan los avisos de "el altar solo acepta
 * ofrendas de noche" o "ofrenda aceptada": una línea que aparece arriba de
 * la barra de objetos y se desvanece sola, sin abrir nada ni tapar la
 * pantalla. Es el mismo criterio que usa el altar de Confluence (LGPL-3.0,
 * https://github.com/Magic-team-jvav/confluence): nada de paneles ni
 * texturas de estado, todo por mensaje.
 *
 * PEDIDO EXPLÍCITO: antes había un panelito flotante que aparecía al mirar
 * el altar (ver AltarRequirementsOverlay, que esta entrega BORRA por
 * completo) -- se sacó porque no encajaba con el resto del mod, que ya
 * comunica todo por estos mensajes de acción.
 *
 * Al completar los tres, el altar entra en RITUAL (temblor de cámara +
 * mensajes de progreso, ~3 segundos) y al final aparece el Knight, usando su
 * propia animación de "spawn" (ver NecroticKnightEntity -- ese cambio forma
 * parte de una entrega anterior) con `setHomePos()` puesto en el altar: así
 * el límite de distancia de la Ronda 6 usa el altar como centro solo.
 *
 * Mientras el Knight invocado siga con vida, el altar queda BLOQUEADO (no
 * acepta más ofrendas). Cuando muere (se revisa cada 20 ticks), se
 * desbloquea solo y se puede volver a invocar.
 *
 * ===================================================================
 * POR QUÉ ESTA FORMA Y NO UNA GUI CUSTOM
 * ===================================================================
 * El mod ya tiene el patrón para una GUI de verdad (ver FrostBrewingStandMenu
 * / FrostBrewingStandBlockEntity, con Container + Menu + Screen), así que si
 * en algún momento preferís esa vía, el camino ya está pavimentado en el
 * proyecto -- no es que faltara la herramienta. Elegí la versión sin
 * inventario ni panel porque encaja mejor con "todo por mensaje, cerca de
 * la hotbar": no interrumpe la vista del jugador, y es coherente con cómo
 * el resto del mod (y el altar de Confluence) resuelve lo mismo.
 */
public class NecroticAltarBlockEntity extends BlockEntity implements BlockEntityTicker<NecroticAltarBlockEntity> {

    /**
     * Un requisito del ritual: qué ítem y cuántos.
     *
     * Las cantidades son un punto de partida razonable, no una cifra sagrada -- son las tres
     * primeras que se me ocurrieron mirando la rareza relativa de cada ítem. Cambiálas libremente
     * acá mismo.
     */
    public record Requirement(Supplier<Item> item, int count, Component displayName) {
        public Item resolvedItem() {
            return item.get();
        }
    }

    public static final List<Requirement> REQUIREMENTS = List.of(
            new Requirement(ModItems.NECROTIC_BONE, 30, Component.translatable("item.nanookmod.necrotic_bone")),
            new Requirement(ModItems.SPECTRAL_CLOTH, 45, Component.translatable("item.nanookmod.spectral_cloth")),
            new Requirement(ModItems.SKELETAL_HEAD, 1, Component.translatable("item.nanookmod.skeletal_head"))
    );

    /** Ticks de temblor de cámara + partículas ANTES de que aparezca el Knight, una vez completo el ritual. */
    private static final int RITUAL_BUILDUP_TICKS = 64; // ~3.2s
    private static final int KNIGHT_ALIVE_CHECK_INTERVAL_TICKS = 20;
    private static final double SCREEN_SHAKE_RADIUS = 24.0D;

    // PEDIDO: "el altar solo debería poder usarse de noche". Mismo rango horario que ya usa
    // TwilightNightHandler para decidir "ya era de noche" al invocar al jefe (12542..23459 -- el
    // mismo tramo en el que vanilla deja dormir / spawnean mobs a la intemperie), así que el altar
    // queda coherente con el resto del mod: no acepta ofrendas fuera de ese rango.
    private static final long NIGHT_RANGE_START = 12542L;
    private static final long NIGHT_RANGE_END = 23459L;

    private final int[] progress = new int[REQUIREMENTS.size()];
    /** > 0 mientras corre la cuenta regresiva del ritual (ya se completaron las ofrendas). */
    private int ritualTicksRemaining = 0;
    @Nullable
    private UUID boundKnightId = null;
    private int knightCheckCooldown = KNIGHT_ALIVE_CHECK_INTERVAL_TICKS;

    /**
     * UUID del último jugador que ofrendó un ítem. Se usa SOLO para orientar
     * al Knight hacia él al terminar el ritual (ver completeRitual) -- no se
     * guarda en NBT a propósito: si el mundo se recarga a mitad de un
     * ritual en curso, el fallback a "jugador más cercano" en
     * completeRitual ya cubre el caso, y no vale la pena persistir un UUID
     * que en el 99% de los casos es irrelevante 10 segundos después.
     */
    @Nullable
    private UUID lastOfferingPlayerId = null;

    public NecroticAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NECROTIC_ALTAR.get(), pos, state);
    }

    // ---------------------------------------------------------------
    // Lectura (para el bloque y para el overlay de cliente)
    // ---------------------------------------------------------------

    public int getProgress(int index) {
        return progress[index];
    }

    public boolean isLocked() {
        return boundKnightId != null || ritualTicksRemaining > 0;
    }

    public boolean isRitualRunning() {
        return ritualTicksRemaining > 0;
    }

    public float getRitualProgress() {
        return ritualTicksRemaining <= 0 ? 0.0F : 1.0F - (ritualTicksRemaining / (float) RITUAL_BUILDUP_TICKS);
    }

    /**
     * Fracción 0..1 de cuánto se ofrendó del requisito `index` -- lo usa
     * NecroticAltarRenderer para iluminar cada uno de los 3 anillos del
     * círculo de arriba por separado (uno por ingrediente).
     */
    public float getRequirementFraction(int index) {
        int required = REQUIREMENTS.get(index).count();
        return required <= 0 ? 0.0F : Mth.clamp(progress[index] / (float) required, 0.0F, 1.0F);
    }

    /** Promedio de los 3 anillos -- qué tan "despierto" se ve el altar en general. */
    public float getOverallOfferingFraction() {
        float sum = 0.0F;
        for (int i = 0; i < REQUIREMENTS.size(); i++) {
            sum += getRequirementFraction(i);
        }
        return sum / REQUIREMENTS.size();
    }

    // ---------------------------------------------------------------
    // Interacción (clic derecho con un ítem)
    // ---------------------------------------------------------------

    /**
     * @return true si el clic hizo algo (consumir el ítem o rechazarlo con un mensaje) -- el
     * bloque usa esto para decidir si devuelve CONSUME o PASS.
     */
    public boolean interactWithItem(Player player, ItemStack stack) {
        if (this.level == null || this.level.isClientSide) {
            return true; // el cliente predice "sí lo manejé" para el swing de brazo; el servidor manda
        }

        if (isLocked()) {
            player.displayClientMessage(Component.translatable(
                    boundKnightId != null ? "nanookmod.altar.occupied" : "nanookmod.altar.locked"), true);
            return true;
        }

        if (!isNight(this.level)) {
            player.displayClientMessage(Component.translatable("nanookmod.altar.day"), true);
            return true;
        }

        int index = indexOfRequirement(stack.getItem());
        if (index < 0) {
            player.displayClientMessage(Component.translatable("nanookmod.altar.rejected"), true);
            return true;
        }
        if (progress[index] >= REQUIREMENTS.get(index).count()) {
            player.displayClientMessage(Component.translatable("nanookmod.altar.full"), true);
            return true;
        }

        progress[index]++;
        stack.shrink(1);
        lastOfferingPlayerId = player.getUUID();
        player.displayClientMessage(Component.translatable("nanookmod.altar.accepted"), true);
        playOfferingFx(index);
        syncToClients();

        if (isFullyOffered()) {
            startRitual();
        }
        return true;
    }

    /**
     * PEDIDO: "todo lo digamos por mensaje... incluyendo la lista de ofrendas". Clic derecho con
     * la mano VACÍA arma y manda una sola línea con el estado de las tres ofrendas -- reemplaza al
     * panel flotante que antes aparecía al mirar el altar (AltarRequirementsOverlay, borrado en
     * esta entrega). Si el altar está bloqueado o es de día, avisa eso primero (no tiene sentido
     * mostrar la lista si ahora mismo no se puede ofrendar nada).
     *
     * @return true si mandó algún mensaje (siempre, salvo que level sea null) -- el bloque lo usa
     * para decidir CONSUME/PASS, igual que interactWithItem.
     */
    public boolean showStatus(Player player) {
        if (this.level == null || this.level.isClientSide) {
            return true;
        }

        if (isLocked()) {
            player.displayClientMessage(Component.translatable(
                    boundKnightId != null ? "nanookmod.altar.occupied" : "nanookmod.altar.locked"), true);
            return true;
        }
        if (!isNight(this.level)) {
            player.displayClientMessage(Component.translatable("nanookmod.altar.day"), true);
            return true;
        }

        player.displayClientMessage(buildStatusLine(), true);
        return true;
    }

    /**
     * Arma la línea de estado: título + los 3 requisitos con su color según cuánto falte --
     * §a (verde) completo, §e (amarillo) empezado, §7 (gris) todavía en 0. Todo en UNA línea de
     * texto porque el mensaje de acción (displayClientMessage con overlay=true) no soporta varias
     * líneas -- si en algún momento preferís partirlo en 3 mensajes seguidos, tenés que mandarlos
     * como chat normal (overlay=false) en vez de esto, porque el de acción se pisa a sí mismo si
     * llegan varios juntos.
     */
    private Component buildStatusLine() {
        MutableComponent line = Component.translatable("nanookmod.altar.title")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("  "));

        for (int i = 0; i < REQUIREMENTS.size(); i++) {
            Requirement req = REQUIREMENTS.get(i);
            int have = progress[i];
            int need = req.count();
            ChatFormatting color = have >= need ? ChatFormatting.GREEN
                    : have > 0 ? ChatFormatting.YELLOW
                    : ChatFormatting.GRAY;

            line.append(req.displayName().copy().withStyle(color))
                    .append(Component.literal(" " + have + "/" + need).withStyle(color));

            if (i < REQUIREMENTS.size() - 1) {
                line.append(Component.literal("  §8·  "));
            }
        }
        return line;
    }

    /**
     * PEDIDO: "el altar no debería poder usarse de día, solo de noche". {@code Level.isDay()}
     * existe en vanilla pero se basa en {@code skyDarken} (un valor pensado para el renderizado del
     * cielo, no siempre fiable como corte de "de noche" para lógica de juego). Usamos en cambio el
     * mismo rango de {@code dayTime % 24000} que ya es la fuente de verdad del resto del mod para
     * "es de noche" (ver TwilightNightHandler#beginTransition), así el altar y la Luna Crepuscular
     * están de acuerdo en qué cuenta como noche.
     */
    private static boolean isNight(Level level) {
        long timeOfDay = level.getDayTime() % 24000L;
        return timeOfDay >= NIGHT_RANGE_START && timeOfDay <= NIGHT_RANGE_END;
    }

    private int indexOfRequirement(Item item) {
        for (int i = 0; i < REQUIREMENTS.size(); i++) {
            if (REQUIREMENTS.get(i).resolvedItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private boolean isFullyOffered() {
        for (int i = 0; i < REQUIREMENTS.size(); i++) {
            if (progress[i] < REQUIREMENTS.get(i).count()) {
                return false;
            }
        }
        return true;
    }

    private void playOfferingFx(int index) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(this.worldPosition).add(0.0D, 0.35D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y, center.z, 10, 0.2D, 0.15D, 0.2D, 0.02D);
        // Un sonido levemente distinto por material, para que se sienta que "reconoce" cada ofrenda.
        var sound = switch (index) {
            case 0 -> SoundEvents.BONE_BLOCK_PLACE;
            case 1 -> SoundEvents.WOOL_PLACE;
            default -> SoundEvents.SOUL_ESCAPE;
        };
        serverLevel.playSound(null, this.worldPosition, sound, SoundSource.BLOCKS, 1.0F, 0.8F);
    }

    // ---------------------------------------------------------------
    // Ritual
    // ---------------------------------------------------------------

    private void startRitual() {
        ritualTicksRemaining = RITUAL_BUILDUP_TICKS;
        syncToClients();
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, this.worldPosition,
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 3.0F, 0.6F);
            broadcastActionBar(serverLevel, Component.translatable("nanookmod.altar.complete")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));

            // PEDIDO: "que aparezca de forma progresiva" en vez de "todo se
            // vuelve noche de la nada". Antes esto lo prendía
            // NecroticKnightEntity#tickSpawnSequence, en el MISMO tick en
            // que el Knight ya aparecía -- de un salto y a la vez que la
            // explosión de knockback, el sonido de invocación, etc. Ahora
            // arranca ACÁ, al completarse las ofrendas, así la transición
            // dispone de los mismos RITUAL_BUILDUP_TICKS (~3.2s) que ya
            // tenía el temblor de cámara para desarrollarse -- el cielo se
            // va oscureciendo EN PARALELO a como sube la tensión, y llega a
            // la noche completa justo cuando el Knight se materializa, no
            // antes ni de golpe.
            //
            // tickRitualBuildup() de acá abajo es quien hace avanzar la
            // transición tick a tick (reusa el mismo progressT que ya
            // manejaba el temblor). Ver TwilightNightHandler para el
            // detalle de cómo se mueve el dayTime real sin saltar de golpe.
            TwilightNightHandler.beginTransition(serverLevel, RITUAL_BUILDUP_TICKS);
        }
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state, NecroticAltarBlockEntity blockEntity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (ritualTicksRemaining > 0) {
            tickRitualBuildup(serverLevel);
            return;
        }

        if (boundKnightId != null && --knightCheckCooldown <= 0) {
            knightCheckCooldown = KNIGHT_ALIVE_CHECK_INTERVAL_TICKS;
            if (!(serverLevel.getEntity(boundKnightId) instanceof NecroticKnightEntity knight) || !knight.isAlive()) {
                boundKnightId = null; // murió o ya no existe: el altar se libera solo
                setChanged();
            }
        }
    }

    /**
     * Cuenta regresiva del ritual. El temblor y las partículas van CRECIENDO hacia el final
     * (progress 0..1), para que se sienta que algo se está acumulando y no un temporizador
     * plano -- el pedido era justo "la cámara empieza a temblar", así que la intensidad tiene que
     * notarse subir, no aparecer de golpe en el último tick.
     */
    private void tickRitualBuildup(ServerLevel serverLevel) {
        float progressT = getRitualProgress();
        ritualTicksRemaining--;

        if (ritualTicksRemaining % 4 == 0) {
            float shakeIntensity = 0.08F + 0.55F * progressT * progressT; // easeIn: casi nada al principio, fuerte al final
            shakeCamerasNearby(serverLevel, shakeIntensity, 6);
        }

        // PEDIDO: "en lugar de usar partículas del vanilla usemos shaders
        // para representar esa energía fluyendo". El SOUL/WITCH que
        // spameaba acá se sacó -- la "energía fluyendo antes de que se
        // genere el boss" la dibuja enteramente el cliente vía los
        // filamentos que convergen desde afuera hacia el altar (ver
        // AltarFilamentManager), al estilo Scylla de Cataclysm. No hace
        // falta que el servidor mande nada: leen getRitualProgress() directo
        // del BlockEntity, que ya está sincronizado por syncToClients() más
        // abajo. Es la técnica que ya usa el resto del mod para "energía"
        // (ver CorruptionFilamentRenderer, NecroticAuraRenderLayer): listones
        // con blending aditivo dibujados a mano, full-bright -- el mismo
        // resultado visual que un shader de verdad, pero sin escribir GLSL
        // ni arriesgar compatibilidad con shader packs. El progreso NUMÉRICO
        // (antes un disco/núcleo creciendo, ver NecroticAltarRenderer) ahora
        // se anuncia por mensaje unos párrafos más abajo.

        // Avanza la transición día->noche EN PARALELO al temblor, con el
        // mismo progressT -- ver TwilightNightHandler.tickTransition.
        TwilightNightHandler.tickTransition(serverLevel, progressT);

        if (ritualTicksRemaining % 10 == 0) {
            serverLevel.playSound(null, this.worldPosition, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS,
                    1.5F + 2.0F * progressT, 0.5F + 0.3F * progressT);
        }

        // PEDIDO: "todo lo digamos por mensaje". Antes el progreso del ritual solo se veía en el
        // núcleo que crecía arriba del altar (ver NecroticAltarRenderer, esa parte se sacó en esta
        // misma entrega); ahora se anuncia cada 20 ticks (~1s) como mensaje de acción a quien esté
        // cerca, mismo estilo que usa Confluence para sus altares.
        if (ritualTicksRemaining % 20 == 0 && ritualTicksRemaining > 0) {
            int percent = Math.round(progressT * 100.0F);
            broadcastActionBar(serverLevel, Component.translatable("nanookmod.altar.ritual_progress", percent)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        if (ritualTicksRemaining <= 0) {
            completeRitual(serverLevel);
        } else {
            syncToClients();
        }
    }

    private void completeRitual(ServerLevel serverLevel) {
        for (int i = 0; i < progress.length; i++) {
            progress[i] = 0; // las ofrendas se consumen para siempre -- no vuelven si el ritual falla ni si termina
        }

        shakeCamerasNearby(serverLevel, 0.9F, 14);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 1.0D, this.worldPosition.getZ() + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverLevel.playSound(null, this.worldPosition, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 2.0F, 0.7F);

        NecroticKnightEntity knight = ModEntities.NECROTIC_KNIGHT.get().create(serverLevel);
        if (knight != null) {
            BlockPos spawnPos = this.worldPosition.above();
            float facingYaw = resolveSpawnFacingYaw(serverLevel,
                    spawnPos.getX() + 0.5D, spawnPos.getZ() + 0.5D);
            knight.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, facingYaw, 0.0F);
            knight.setHomePos(this.worldPosition); // el límite de distancia de la Ronda 6 usa el altar como origen
            serverLevel.addFreshEntity(knight);
            boundKnightId = knight.getUUID();

            // Un empujón de Lentitud a los jugadores muy cerca: da un instante para que la cámara
            // termine de asentarse antes de que el Knight pueda empezar a actuar (su propia
            // secuencia de aparición ya se encarga del resto -- pulso, sonido, animación "spawn").
            for (ServerPlayer nearby : serverLevel.getPlayers(p -> p.distanceToSqr(knight) <= 36.0D)) {
                nearby.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1));
            }
        }

        setChanged();
        syncToClients();
    }

    /**
     * BUG REPORTADO: "el Knight siempre aparece dando la espalda al
     * jugador". Causa exacta: acá arriba se llamaba a
     * {@code knight.moveTo(x, y, z, 0.0F, 0.0F)} -- el yaw quedaba
     * SIEMPRE fijo en 0° (sur), sin importar dónde estuviera parado el
     * jugador. Si el jugador ofrendaba parado al sur del altar mirando
     * hacia el norte (lo más natural, cara a cara con el altar), el Knight
     * aparecía TAMBIÉN mirando al sur -- exactamente de espaldas a él.
     *
     * Ahora se calcula el yaw hacia el jugador que hizo la última ofrenda
     * (lastOfferingPlayerId): es más intencional que "el jugador más
     * cercano en este instante", porque es quien de verdad completó el
     * ritual y va a estar mirando al altar en ese momento. Si por lo que
     * sea ese jugador ya no está (se desconectó justo entre la última
     * ofrenda y que termine la cuenta regresiva, algo poco probable pero
     * posible con ~3s de por medio), cae a "el jugador vivo más cercano
     * dentro de SCREEN_SHAKE_RADIUS"; si tampoco hay ninguno (invocado por
     * comando sin nadie cerca), se queda con 0.0F como antes -- no hay
     * nada mejor que mirar.
     */
    private float resolveSpawnFacingYaw(ServerLevel serverLevel, double spawnX, double spawnZ) {
        ServerPlayer target = lastOfferingPlayerId != null
                ? serverLevel.getServer().getPlayerList().getPlayer(lastOfferingPlayerId)
                : null;

        if (target == null) {
            double bestDistSqr = Double.MAX_VALUE;
            for (ServerPlayer candidate : serverLevel.getPlayers(p -> !p.isSpectator())) {
                double dx = candidate.getX() - spawnX;
                double dz = candidate.getZ() - spawnZ;
                double distSqr = dx * dx + dz * dz;
                if (distSqr < bestDistSqr && distSqr <= SCREEN_SHAKE_RADIUS * SCREEN_SHAKE_RADIUS) {
                    bestDistSqr = distSqr;
                    target = candidate;
                }
            }
        }

        if (target == null) {
            return 0.0F; // nadie cerca -- no hay hacia dónde orientarlo, se queda como estaba
        }

        double dx = target.getX() - spawnX;
        double dz = target.getZ() - spawnZ;
        if (dx * dx + dz * dz < 1.0E-4) {
            return 0.0F; // el jugador está literalmente parado en el mismo punto que el spawn
        }
        // Misma convención de yaw que usa el resto del mod para "encarar un
        // punto" (ver NanookEntity#yawTowards): con esta fórmula el modelo
        // queda mirando DE FRENTE hacia (dx, dz), no de espaldas.
        return (float) (Mth.atan2(-dx, dz) * (180.0D / Math.PI));
    }

    /** Manda un mensaje de acción a todos los jugadores dentro de SCREEN_SHAKE_RADIUS -- mismo radio que ya usa el temblor de cámara, buen proxy de "está viviendo el ritual". */
    private void broadcastActionBar(ServerLevel serverLevel, Component message) {
        for (ServerPlayer player : serverLevel.players()) {
            double dx = player.getX() - (this.worldPosition.getX() + 0.5D);
            double dz = player.getZ() - (this.worldPosition.getZ() + 0.5D);
            if (dx * dx + dz * dz <= SCREEN_SHAKE_RADIUS * SCREEN_SHAKE_RADIUS) {
                player.displayClientMessage(message, true);
            }
        }
    }

    private void shakeCamerasNearby(ServerLevel serverLevel, float intensity, int durationTicks) {
        for (ServerPlayer player : serverLevel.players()) {
            double dx = player.getX() - (this.worldPosition.getX() + 0.5D);
            double dz = player.getZ() - (this.worldPosition.getZ() + 0.5D);
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > SCREEN_SHAKE_RADIUS) {
                continue;
            }
            float falloff = 1.0F - (float) (distance / SCREEN_SHAKE_RADIUS);
            float finalIntensity = intensity * falloff * falloff;
            if (finalIntensity < 0.02F) {
                continue;
            }
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new NanookScreenShakePacket(finalIntensity, durationTicks));
        }
    }

    // ---------------------------------------------------------------
    // Guardado / sincronización
    // ---------------------------------------------------------------

    private void syncToClients() {
        setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putIntArray("Progress", progress);
        tag.putInt("RitualTicksRemaining", ritualTicksRemaining);
        if (boundKnightId != null) {
            tag.putUUID("BoundKnight", boundKnightId);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int[] loaded = tag.getIntArray("Progress");
        for (int i = 0; i < progress.length && i < loaded.length; i++) {
            progress[i] = loaded[i];
        }
        ritualTicksRemaining = tag.getInt("RitualTicksRemaining");
        boundKnightId = tag.hasUUID("BoundKnight") ? tag.getUUID("BoundKnight") : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    /**
     * BUG REPORTADO: "los anillos/núcleo/filamentos del altar no se ven en el juego". Causa: esta
     * clase nunca sobreescribía {@code getUpdatePacket()}. La implementación por defecto en
     * {@link BlockEntity} devuelve {@code null}, así que aunque {@code syncToClients()} llamaba a
     * {@code level.sendBlockUpdated(...)} en cada ofrenda, el servidor jamás llegaba a construir ni
     * enviar el paquete de datos al cliente -- {@code load()} nunca se ejecutaba del lado cliente,
     * así que {@code progress[]} y {@code ritualTicksRemaining} quedaban SIEMPRE en 0 ahí, aunque
     * en el servidor sí avanzaran. Como NecroticAltarRenderer y AltarFilamentManager (vía el
     * renderer) leen esos valores directo del BlockEntity del lado cliente, ambos veían el altar
     * "vacío" para siempre, sin importar cuánto ofrendaras.
     *
     * Con este override, {@code sendBlockUpdated} sí arma y despacha el paquete (usando el mismo
     * {@code getUpdateTag()}/{@code load()} que ya estaban escritos), y el cliente se entera de cada
     * cambio en tiempo real.
     */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
