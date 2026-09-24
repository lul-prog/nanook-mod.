package com.nanookmod.entity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Cooldowns independientes por ataque (THRUST, GUARD_SLAP, WAVE,
 * GHOST_SUMMON, CHARGE). THRUST sigue siendo prioridad automática si está
 * pegado (es la reacción de "está al lado, le pego"). Los demás compiten
 * por peso cuando hay varios disponibles a la vez -- así no sale siempre
 * el mismo primero apenas coinciden los cooldowns. Todos requieren línea
 * de visión (hasLineOfSight) -- si hay una pared de por medio, el jefe no
 * dispara nada, solo se acerca. GUARD_SLAP también se activa antes de
 * tiempo si el jefe está siendo comboeado (ver
 * NecroticKnightEntity.isBeingComboed). Todos los cooldowns se escalan
 * hacia abajo según la fase de la pelea (ver
 * NecroticKnightEntity.getAggressionCooldownMultiplier) -- más avanzada
 * la pelea, menos tiempo muerto entre ataques.
 */
public class NecroticKnightAttackGoal extends Goal {

    private static final int THRUST_COOLDOWN_TICKS = 18;
    private static final int CHARGE_COOLDOWN_TICKS = 160;     // 8s
    private static final int GHOST_SUMMON_COOLDOWN_TICKS = 300; // 15s
    private static final int WAVE_COOLDOWN_TICKS = 240;         // 12s
    private static final int GUARD_SLAP_COOLDOWN_TICKS = 200;   // 10s
    // Ataque fuerte (desbloqueo de Corrupción IV) -- cooldown bastante más
    // largo que el resto, es más un "finisher" ocasional que parte del
    // ritmo normal de golpes.
    private static final int LIGHTNING_COOLDOWN_TICKS = 500;    // 25s
    // Vanish: no depende de ningún desbloqueo (a diferencia del Rayo),
    // disponible desde el arranque de la pelea. Cooldown parecido al de
    // Ghost Summon -- es un "mix-up" ocasional, no algo que se repita
    // todo el rato.
    private static final int VANISH_COOLDOWN_TICKS = 300;       // 15s
    // Anti-kiteo: si pasa este tiempo SIN lograr disparar ningún ataque
    // (por línea de visión bloqueada, por estar todo en cooldown, por lo
    // que sea), fuerza un Vanish YA -- saltándose su cooldown normal --
    // para cerrar la distancia de una. Es EXACTAMENTE el mecanismo que
    // usa "The Immortal" de EEEABsMobs para no dejarse kitear
    // (unableAttackTickCount en EntityImmortalBoss.java, ahí el umbral es
    // de 150 ticks/7.5s; achicamos un poco el margen porque nuestro jefe
    // no tiene tantas otras formas de presionar a distancia como el
    // Immortal). Esto es justo la pieza que le faltaba al pedido de "que
    // se sienta más agresivo como el Immortal" -- ya no importa cuánto lo
    // esquives o le cortes la línea de visión, en algún momento SÍ o SÍ
    // te va a alcanzar.
    private static final int FORCE_ACTION_TICKS = 100; // 5s
    // Velocidad de puntería del Rayo DURANTE EL CANALIZADO (no durante el
    // windup, ver más abajo) -- distinto de "grados por tick" fijos como
    // el resto de los ataques: escalado por distancia, calcado de la
    // fórmula que usa el mob de referencia (Nameless Guardian, mod
    // Beyond the Abyss) en su GuardianShootLaserGoal. yMaxRotSpeed queda
    // entre ~2.0 y ~5.0 grados/tick según la distancia real al jugador.
    private static final float LIGHTNING_CHANNEL_MIN_SPEED = 0.5F;
    private static final float LIGHTNING_CHANNEL_MAX_SPEED = 3.5F;
    private static final float LIGHTNING_CHANNEL_BASE_SPEED = 1.5F;
    private static final float LIGHTNING_CHANNEL_DISTANCE_SCALE = 7.5F;
    // El pitch (arriba/abajo) va bastante más rápido/suelto que el yaw --
    // igual que el mob de referencia (ahí usan 90°, básicamente sin
    // límite real). No hace falta que sea "esquivable" en el eje
    // vertical de la misma manera que el horizontal.
    private static final float LIGHTNING_PITCH_TURN_SPEED_DEG = 60.0F;
    private static final double GUARD_SLAP_TRIGGER_RANGE = 5.0D; // apenas más que el alcance real del golpe (4.5) -- si lo activamos más lejos, con 1.55s de guardia el jugador tiene tiempo de sobra para salir del rango antes del golpe
    // Piso de distancia para el Rayo -- por debajo de esto, el ángulo de
    // bajada necesario (espada a ~2.3 de altura, pecho del jugador a
    // ~0.9) se vuelve exagerado y se ve raro para un ataque que se supone
    // sale "recto y con fuerza" -- además a corta distancia ya hay de
    // sobra con THRUST/GUARD_SLAP. Con este mínimo, el ángulo de bajada
    // se mantiene entre ~4° (a los 20 de LIGHTNING_RANGE) y ~10° (acá,
    // a los 8) en todo el rango en el que el rayo puede salir.
    private static final double LIGHTNING_MIN_RANGE = 8.0D;
    private static final int GUARD_SLAP_PANIC_COOLDOWN_TICKS = 20; // si lo están comboeando, el cooldown se acorta a esto en vez de esperar el normal (10s) -- no se escala por fase, ya es corto de por sí
    private static final double SPEED_MODIFIER = 1.05D;

    // Pesos para el sorteo entre GUARD_SLAP / WAVE / GHOST_SUMMON / CHARGE
    // cuando más de uno está disponible a la vez. Más alto = más chance.
    // Tocá estos números para ajustar qué tan seguido sale cada uno.
    private static final double GUARD_SLAP_WEIGHT = 1.2D;
    private static final double WAVE_WEIGHT = 1.0D;
    private static final double GHOST_SUMMON_WEIGHT = 1.0D;
    private static final double CHARGE_WEIGHT = 1.0D;
    private static final double LIGHTNING_WEIGHT = 1.0D;
    private static final double VANISH_WEIGHT = 0.7D; // un poco menos que el resto -- es el "sorpresa ocasional", no debería salir todo el rato
    // Thrust AHORA compite en el mismo pool que el resto (antes tenía
    // prioridad automática incondicional -- ver el comentario largo más
    // abajo, en tick(), sobre por qué eso hacía que nunca le tocara el
    // turno a nada más estando pegado). Peso bien por encima del resto
    // para que siga siendo, con diferencia, la reacción más común en
    // melee (es la esperable: "está al lado, le pego") -- pero ya no
    // GARANTIZADA, así que de vez en cuando entra Guard Slap u otra cosa
    // en su lugar.
    private static final double THRUST_WEIGHT = 2.5D;

    private final NecroticKnightEntity boss;

    private int thrustCooldown = 0;
    private int chargeCooldown = 0;
    private int ghostCooldown = 100; // no invoca fantasmitas en el primer segundo, ya tuvo la oleada inicial
    private int waveCooldown = 60;
    private int guardSlapCooldown = 140; // margen inicial, no queremos que salga en el primer segundo de pelea
    // Margen inicial más largo: si el jefe ya arranca la pelea con la
    // cicatriz en IV (poco común, pero puede pasar con /summon con NBT a
    // mano), tampoco queremos que el rayo sea lo primerísimo que tira.
    private int lightningCooldown = 260; // ~13s
    private int vanishCooldown = 180; // margen inicial (~9s) -- no queremos que sea de las primeras cosas que hace
    private int pathRecalcCooldown = 0;
    // Anti-repetición: qué ataque salió la última vez, para no dejarlo
    // repetirse 2 veces seguidas cuando hay otra opción disponible (ver
    // pickWeighted). -1 = todavía no atacó nada en esta pelea.
    private int lastChosenAttackId = -1;
    // Anti-kiteo -- ver el bloque grande de comentario más abajo, junto a
    // FORCE_ACTION_TICKS, para el porqué (inspirado directo en cómo
    // "The Immortal" de EEEABsMobs resuelve exactamente este mismo
    // problema).
    private int ticksStuckWithoutAttack = 0;

    public NecroticKnightAttackGoal(NecroticKnightEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = boss.getTarget();
        // El límite de distancia al origen: un objetivo fuera del área de persecución no cuenta.
        // Nada de atacar/perseguir mientras todavía se está materializando (animación "spawn").
        return !boss.isSpawningIn() && target != null && target.isAlive() && boss.isInsideChaseArea(target);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        boss.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = boss.getTarget();
        if (target == null) {
            return;
        }

        // El Rayo puede seguir girando sobre su propio eje para mirar al
        // target, pero con DOS velocidades distintas según la fase --
        // este es EL bug que faltaba resolver: si usás la misma
        // puntería lenta durante TODO el ataque (windup incluido), el
        // jefe puede no terminar de girar hacia el jugador ni siquiera
        // para cuando arranca el canalizado real -- pega poco y de
        // costado los primeros segundos porque todavía está "poniéndose
        // a tiro". El mob de referencia (Nameless Guardian) hace
        // exactamente esto: rápido mientras carga, recién se pone lento
        // una vez que el rayo está saliendo de verdad.
        // NO usa LookControl (a diferencia del resto de los ataques) --
        // LookControl solo mueve la cabeza, no el cuerpo, y con el jefe
        // parado quieto el cuerpo nunca la alcanza (ver el comentario
        // largo en NecroticKnightEntity#updateLightningAim). Solo la
        // Embestida se excluye del todo -- necesita apuntar fijo a la
        // posición de arranque para no "curvarse" a mitad de la carga.
        if (boss.getAttackState() == NecroticKnightEntity.ATTACK_LIGHTNING) {
            if (boss.isLightningWindingUp()) {
                // Todavía cargando (sword_beam_windup) -- rápido, igual
                // que el resto de los ataques, para que el jefe YA esté
                // apuntando más o menos bien antes de que el rayo
                // aparezca de verdad.
                boss.updateLightningAim(target, 30.0F, 30.0F);
            } else {
                float distance = boss.distanceTo(target);
                float speed = Math.min(LIGHTNING_CHANNEL_MAX_SPEED,
                        Math.max(LIGHTNING_CHANNEL_MIN_SPEED, distance / LIGHTNING_CHANNEL_DISTANCE_SCALE));
                float yawSpeed = LIGHTNING_CHANNEL_BASE_SPEED + speed; // ~2.0 a 5.0 grados/tick
                boss.updateLightningAim(target, yawSpeed, LIGHTNING_PITCH_TURN_SPEED_DEG);
            }
        } else if (!boss.isAttacking()) {
            // Solo FUERA de los ataques. Durante el thrust y el guard slap la orientación la maneja
            // NecroticKnightEntity#tickAimTracking (sigue al objetivo y después se compromete).
            // Antes el LookControl giraba la cabeza y el cuerpo visual por su cuenta mientras la
            // lógica del golpe miraba a otro lado, y el golpe no coincidía con la animación.
            boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        if (thrustCooldown > 0) thrustCooldown--;
        if (chargeCooldown > 0) chargeCooldown--;
        if (ghostCooldown > 0) ghostCooldown--;
        if (waveCooldown > 0) waveCooldown--;
        if (guardSlapCooldown > 0) guardSlapCooldown--;
        if (lightningCooldown > 0) lightningCooldown--;
        if (vanishCooldown > 0) vanishCooldown--;

        // "Pánico": si lo están golpeando seguido, acortamos lo que le
        // falte de cooldown a Guard & Slap en vez de esperar los 10s
        // completos -- no interrumpe el ataque en curso, solo hace que
        // el shield esté listo antes la próxima vez que se evalúe.
        if (boss.isBeingComboed() && guardSlapCooldown > GUARD_SLAP_PANIC_COOLDOWN_TICKS) {
            guardSlapCooldown = GUARD_SLAP_PANIC_COOLDOWN_TICKS;
        }

        if (boss.isAttacking()) {
            boss.getNavigation().stop();
            ticksStuckWithoutAttack = 0; // está en medio de algo -- no cuenta como "estancado"
            return;
        }

        // Sin línea de visión (pared de por medio, etc.) no se dispara
        // ningún ataque -- solo se sigue acercando con pathfinding normal.
        boolean hasLineOfSight = boss.hasLineOfSight(target);

        double distSqr = boss.distanceToSqr(target);
        double thrustReachSqr = NecroticKnightEntity.THRUST_RANGE * NecroticKnightEntity.THRUST_RANGE;
        double chargeReachSqr = NecroticKnightEntity.CHARGE_TRIGGER_RANGE * NecroticKnightEntity.CHARGE_TRIGGER_RANGE;
        double guardSlapReachSqr = GUARD_SLAP_TRIGGER_RANGE * GUARD_SLAP_TRIGGER_RANGE;

        if (hasLineOfSight) {
            // El resto compite por peso -- se arma la lista de lo que esté
            // disponible ahora mismo y se sortea entre esas opciones. Antes
            // Thrust tenía un "if... return" acá arriba que lo disparaba
            // ANTES de siquiera armar esta lista -- el problema (reportado
            // en juego) era que su cooldown (18 base, ~10 en Final Phase)
            // es MÁS CORTO que su propia animación (23 ticks), así que en
            // cuanto terminaba de pegar ya estaba listo para pegar de
            // nuevo, y con el jugador pegado eso significaba: Thrust,
            // Thrust, Thrust... para siempre, sin que Guard Slap ni nada
            // más entrase nunca en juego. Ahora Thrust es un candidato más
            // (con peso alto, ver THRUST_WEIGHT, para que siga siendo la
            // reacción más común) y pickWeighted() de más abajo, además,
            // evita repetir el mismo dos veces seguidas cuando hay otra
            // opción disponible.
            List<AttackCandidate> candidates = new ArrayList<>();

            if (distSqr <= thrustReachSqr && thrustCooldown <= 0) {
                candidates.add(new AttackCandidate(NecroticKnightEntity.ATTACK_THRUST, THRUST_WEIGHT, () -> {
                    boss.getNavigation().stop();
                    boss.triggerThrust();
                    thrustCooldown = scaledCooldown(THRUST_COOLDOWN_TICKS);
                }));
            }
            if (distSqr <= guardSlapReachSqr && guardSlapCooldown <= 0) {
                candidates.add(new AttackCandidate(NecroticKnightEntity.ATTACK_GUARD_SLAP, GUARD_SLAP_WEIGHT, () -> {
                    boss.triggerGuardSlap();
                    guardSlapCooldown = scaledCooldown(GUARD_SLAP_COOLDOWN_TICKS);
                }));
            }
            // No tiene sentido tirar la onda con el jugador pegadísimo, ya
            // lo va a golpear con THRUST o GUARD_SLAP.
            if (waveCooldown <= 0 && distSqr > thrustReachSqr) {
                candidates.add(new AttackCandidate(NecroticKnightEntity.ATTACK_WAVE, WAVE_WEIGHT, () -> {
                    boss.triggerWave();
                    waveCooldown = scaledCooldown(WAVE_COOLDOWN_TICKS);
                }));
            }
            if (ghostCooldown <= 0) {
                candidates.add(new AttackCandidate(NecroticKnightEntity.ATTACK_GHOST_SUMMON, GHOST_SUMMON_WEIGHT, () -> {
                    boss.triggerGhostSummon();
                    ghostCooldown = scaledCooldown(GHOST_SUMMON_COOLDOWN_TICKS);
                }));
            }
            // Embestida como gap-closer, solo si está lejos.
            if (distSqr <= chargeReachSqr && distSqr > thrustReachSqr && chargeCooldown <= 0) {
                candidates.add(new AttackCandidate(NecroticKnightEntity.ATTACK_CHARGE, CHARGE_WEIGHT, () -> {
                    boss.triggerCharge();
                    chargeCooldown = scaledCooldown(CHARGE_COOLDOWN_TICKS);
                }));
            }
            // Vanish: sin sentido pegadísimo (ya está resuelto con
            // THRUST/GUARD_SLAP) -- mismo filtro que Wave.
            if (vanishCooldown <= 0 && distSqr > thrustReachSqr) {
                candidates.add(new AttackCandidate(NecroticKnightEntity.ATTACK_VANISH, VANISH_WEIGHT, () -> {
                    boss.triggerVanish();
                    vanishCooldown = scaledCooldown(VANISH_COOLDOWN_TICKS);
                }));
            }
            // Rayo: solo si ya lo desbloqueó en esta pelea (Corrupción IV
            // alguna vez), el objetivo entra en el alcance real del rayo,
            // Y no está demasiado cerca (ver LIGHTNING_MIN_RANGE).
            double lightningReachSqr = NecroticKnightEntity.LIGHTNING_RANGE * NecroticKnightEntity.LIGHTNING_RANGE;
            double lightningMinReachSqr = LIGHTNING_MIN_RANGE * LIGHTNING_MIN_RANGE;
            if (boss.isLightningUnlocked() && lightningCooldown <= 0
                    && distSqr <= lightningReachSqr && distSqr >= lightningMinReachSqr) {
                candidates.add(new AttackCandidate(NecroticKnightEntity.ATTACK_LIGHTNING, LIGHTNING_WEIGHT, () -> {
                    boss.triggerLightning();
                    lightningCooldown = scaledCooldown(LIGHTNING_COOLDOWN_TICKS);
                }));
            }

            if (!candidates.isEmpty()) {
                boss.getNavigation().stop();
                AttackCandidate chosen = pickWeighted(candidates, boss.getRandom(), lastChosenAttackId);
                lastChosenAttackId = chosen.attackId();
                chosen.trigger().run();
                ticksStuckWithoutAttack = 0;
                return;
            }
        }

        if (--pathRecalcCooldown <= 0) {
            pathRecalcCooldown = 4 + boss.getRandom().nextInt(3);
            boss.getNavigation().moveTo(target, SPEED_MODIFIER);
        }

        // Nada se disparó este tick (sin línea de visión, o todo en
        // cooldown) -- ver FORCE_ACTION_TICKS.
        if (++ticksStuckWithoutAttack >= FORCE_ACTION_TICKS) {
            ticksStuckWithoutAttack = 0;
            boss.getNavigation().stop();
            boss.triggerVanish();
            vanishCooldown = scaledCooldown(VANISH_COOLDOWN_TICKS);
        }
    }

    // Escala un cooldown base según qué tan avanzada está la pelea (ver
    // NecroticKnightEntity.getAggressionCooldownMultiplier). Con un
    // mínimo de 1 tick para no dejar nunca un cooldown en 0 permanente.
    private int scaledCooldown(int baseTicks) {
        return Math.max(1, (int) Math.round(baseTicks * boss.getAggressionCooldownMultiplier()));
    }

    private static AttackCandidate pickWeighted(List<AttackCandidate> candidates, RandomSource random,
                                                 int lastChosenAttackId) {
        // Anti-repetición: si hay MÁS de una opción disponible, sacamos
        // del sorteo la que salió la última vez -- así nunca se repite el
        // mismo ataque 2 veces seguidas mientras haya alternativa real.
        // Si la única opción disponible ahora mismo es justo esa (ej.
        // estás pegadísimo y solo Thrust está en rango y sin cooldown),
        // se queda en la lista tal cual -- mejor repetir que no atacar.
        List<AttackCandidate> pool = candidates;
        if (candidates.size() > 1) {
            List<AttackCandidate> filtered = new ArrayList<>(candidates.size() - 1);
            for (AttackCandidate candidate : candidates) {
                if (candidate.attackId() != lastChosenAttackId) {
                    filtered.add(candidate);
                }
            }
            if (!filtered.isEmpty()) {
                pool = filtered;
            }
        }

        double totalWeight = 0.0D;
        for (AttackCandidate candidate : pool) {
            totalWeight += candidate.weight();
        }
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0.0D;
        for (AttackCandidate candidate : pool) {
            cumulative += candidate.weight();
            if (roll <= cumulative) {
                return candidate;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private record AttackCandidate(int attackId, double weight, Runnable trigger) {
    }
}