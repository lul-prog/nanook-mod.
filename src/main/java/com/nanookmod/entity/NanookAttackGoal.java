package com.nanookmod.entity;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * IA de combate de Nanook.
 *
 * Estructura: cooldown por ataque + sorteo ponderado + anti-repetición +
 * anti-kiteo + recovery global + strafe + memoria de línea de visión.
 *
 * ===================================================================
 * NOVEDAD DE ESTA REVISIÓN: COMBOS
 * ===================================================================
 * Pedido: "quizás que pueda encadenar combos, digamos roar y luego el
 * salto".
 *
 * Antes cada ataque era una isla: se disparaba, terminaba, y el jefe
 * volvía a sortear desde cero con un colchón de recovery. Eso hace que
 * la pelea se sienta como una máquina tragamonedas en vez de como un
 * oponente con intención.
 *
 * Ahora, al disparar un ataque, se consulta una TABLA DE CONTINUACIONES
 * (buildCombo). Si sale, el siguiente ataque queda RESERVADO: cuando el
 * actual termina, se dispara ese y no otro, con un recovery mucho más
 * corto. El resultado se lee como una secuencia planeada:
 *
 *   rugido -> (te tira lejos y aturdido) -> SALTO encima tuyo
 *   embestida que conecta -> (quedás aturdido) -> DOBLE TAJO
 *   embestida que falla -> (rugido de frustración, ya automático)
 *   salto -> (estás en el piso) -> GOLPE DOBLE AL SUELO
 *   golpe doble al suelo -> (en furia) -> EJECUCIÓN
 *
 * La probabilidad de que un combo salga sube con la fase: en fase 0 casi
 * nunca (para que la primera parte de la pelea se pueda aprender), en
 * furia casi siempre.
 *
 * ===================================================================
 * ATAQUES NUEVOS EN LA ROTACIÓN
 * ===================================================================
 *  - DOUBLE_SLASH: melee rápido, daño fijo + % de vida MÁXIMA del
 *    objetivo. Es el relleno de presión en distancia corta.
 *  - EXECUTION (el aplauso casi letal): desde fase 2 (<= 50 % de vida), con un
 *    cooldown de 22 s, y desde furia (<= 25 %) con uno de 13 s. Ver
 *    executionCooldownTicks() y canUseExecution() para el porqué.
 *    Nunca sale como primera acción al entrar en furia (hay un delay de
 *    cortesía), porque entrar en furia ya es suficiente sorpresa.
 */
public class NanookAttackGoal extends Goal {

    // --- Cooldowns base (en ticks, a fase 0) ---
    private static final int CLAW_COOLDOWN_TICKS = 30;             // 1.5s
    private static final int ICE_FIST_COOLDOWN_TICKS = 50;         // 2.5s
    private static final int CLAW_PROJECTILE_COOLDOWN_TICKS = 100;
    private static final int ICE_FIST_GROUND_COOLDOWN_TICKS = 160;
    private static final int ICE_CHARGE_COOLDOWN_TICKS = 140;
    private static final int JUMP_SMASH_COOLDOWN_TICKS = 240;
    private static final int CHANNELLING_COOLDOWN_TICKS = 360;
    private static final int ROAR_COOLDOWN_TICKS = 200;            // 10s
    private static final int DOUBLE_GROUND_COOLDOWN_TICKS = 220;   // 11s
    private static final int DOUBLE_SLASH_COOLDOWN_TICKS = 90;     // 4.5s
    // Ejecución. ANTES: solo en furia (el último 25 % de vida, ~30-60 s de
    // pelea), 6 s de gracia, 30 s de cooldown SIN escalar y peso 1.0 contra ~12
    // del resto: salía 1-2 veces por combate. Ahora:
    private static final int EXECUTION_COOLDOWN_PHASE2_TICKS = 440;    // 22s  (<= 50 % de vida)
    private static final int EXECUTION_COOLDOWN_ENRAGED_TICKS = 260;   // 13s  (<= 25 % de vida)
    /** Ticks de espera tras llegar a fase 2, para que no salga apenas cruzás el 50 %. */
    private static final int EXECUTION_FIRST_USE_DELAY_TICKS = 120;    // 6s

    // --- Pesos del sorteo cuando hay varios disponibles a la vez ---
    // Tocá SOLO estos números para ajustar qué tan seguido sale cada ataque.
    private static final double CLAW_WEIGHT = 2.2D;
    private static final double ICE_FIST_WEIGHT = 1.6D;
    private static final double CLAW_PROJECTILE_WEIGHT = 1.2D;
    private static final double ICE_FIST_GROUND_WEIGHT = 1.0D;
    private static final double ICE_CHARGE_WEIGHT = 1.1D;
    private static final double JUMP_SMASH_WEIGHT = 1.0D;
    private static final double CHANNELLING_WEIGHT = 0.8D;
    private static final double ROAR_WEIGHT = 0.9D;
    private static final double DOUBLE_GROUND_WEIGHT = 1.35D;
    private static final double DOUBLE_SLASH_WEIGHT = 1.8D;
    // Peso 3.0 (antes 1.0): solo cuenta cuando el ataque YA está disponible y hay
    // alguien en rango, así que no lo vuelve más frecuente por sí solo, pero
    // evita que se pierda entre los otros 8-10 candidatos y espere turno.
    private static final double EXECUTION_WEIGHT = 3.0D;

    /** Anti-kiteo: si pasa esto sin lograr atacar, fuerza un gap-closer. */
    private static final int FORCE_ACTION_TICKS = 100; // 5s

    private static final double SPEED_MODIFIER = 1.0D;

    /** Distancia mínima para embestida/salto (necesitan espacio). */
    private static final double MIN_GAP_CLOSER_RANGE = 7.0D;

    /** Rango en el que el double_ground tiene sentido. */
    private static final double DOUBLE_GROUND_MIN_RANGE = 2.5D;

    /** Si está más cerca que esto y no tiene melee listo, se separa. */
    private static final double TOO_CLOSE_RANGE = 3.2D;

    /** Cuántos ticks recuerda que te vio. */
    private static final int LINE_OF_SIGHT_MEMORY_TICKS = 20;

    /** Colchón mínimo entre dos ataques cualesquiera. */
    private static final int MIN_RECOVERY_TICKS = 8;

    /** Recovery cuando el siguiente ataque es parte de un combo. */
    private static final int COMBO_RECOVERY_TICKS = 5;

    /** Tras entrar en furia, cuántos ticks espera antes de poder ejecutar. */
    private static final int EXECUTION_GRACE_TICKS = 100; // 5s tras entrar en furia (antes 6)

    private final NanookEntity nanook;

    private int clawCooldown = 0;
    private int iceFistCooldown = 20;
    private int clawProjectileCooldown = 40;
    private int iceFistGroundCooldown = 80;
    private int iceChargeCooldown = 60;
    private int jumpSmashCooldown = 120;
    private int channellingCooldown = 200;
    private int roarCooldown = 60;
    private int doubleGroundCooldown = 100;
    private int doubleSlashCooldown = 40;
    private int executionCooldown = 0;
    /** True desde que el jefe llegó por primera vez a fase 2 (ahí arranca el aplauso). */
    private boolean executionArmed = false;

    private int globalRecoveryCooldown = 0;
    private int pathRecalcCooldown = 0;
    private int lastChosenAttackId = -1;
    private int ticksStuckWithoutAttack = 0;
    private int lineOfSightMemory = 0;

    /** Ataque reservado por un combo. -1 = ninguno. */
    private int pendingComboAttack = -1;
    /** Si el combo no se puede ejecutar en esta cantidad de ticks, se cancela. */
    private int pendingComboExpiry = 0;

    /** Ticks desde que entró en furia (para EXECUTION_GRACE_TICKS). */
    private int ticksSinceEnrage = -1;

    // Strafe: sentido y cuánto falta para cambiarlo.
    private int strafeDirection = 1;
    private int strafeSwitchCooldown = 0;

    public NanookAttackGoal(NanookEntity nanook) {
        this.nanook = nanook;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = nanook.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.strafeDirection = nanook.getRandom().nextBoolean() ? 1 : -1;
        this.strafeSwitchCooldown = 30 + nanook.getRandom().nextInt(40);
    }

    @Override
    public void stop() {
        nanook.getNavigation().stop();
        this.pendingComboAttack = -1;
    }

    @Override
    public void tick() {
        LivingEntity target = nanook.getTarget();
        if (target == null) {
            return;
        }

        // Mientras ataca, la orientación la maneja NanookEntity (tickAimTracking,
        // la ejecución, la embestida y el salto). Antes solo se respetaban
        // embestida/salto/canalizado; en el resto de ataques el LookControl
        // giraba la cabeza y el cuerpo visual por su cuenta mientras la lógica
        // de golpe miraba a otro lado, y el golpe no conectaba.
        if (!nanook.isAttacking()) {
            nanook.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        tickCooldowns();

        if (nanook.isAttacking()) {
            nanook.getNavigation().stop();
            ticksStuckWithoutAttack = 0;
            return;
        }

        double distSqr = nanook.distanceToSqr(target);

        // Memoria de línea de visión.
        if (nanook.hasLineOfSight(target)) {
            lineOfSightMemory = LINE_OF_SIGHT_MEMORY_TICKS;
        } else if (lineOfSightMemory > 0) {
            lineOfSightMemory--;
        }
        boolean canSeeTarget = lineOfSightMemory > 0;

        // ---------------------------------------------------------------
        // 1) COMBO PENDIENTE: tiene prioridad sobre el sorteo normal.
        // ---------------------------------------------------------------
        if (pendingComboAttack != -1 && globalRecoveryCooldown <= 0 && canSeeTarget) {
            if (tryRunCombo(pendingComboAttack, distSqr)) {
                pendingComboAttack = -1;
                ticksStuckWithoutAttack = 0;
                return;
            }
            // Si no se pudo (se alejó demasiado, etc.), se cae al sorteo normal.
            pendingComboAttack = -1;
        }

        // ---------------------------------------------------------------
        // 2) Sorteo normal.
        // ---------------------------------------------------------------
        if (globalRecoveryCooldown <= 0 && canSeeTarget) {
            List<AttackCandidate> candidates = buildCandidates(distSqr);
            if (!candidates.isEmpty()) {
                nanook.getNavigation().stop();
                AttackCandidate chosen = pickWeighted(candidates, nanook.getRandom(), lastChosenAttackId);
                lastChosenAttackId = chosen.attackId();
                chosen.trigger().run();
                globalRecoveryCooldown = chosen.recoveryTicks();
                ticksStuckWithoutAttack = 0;
                scheduleCombo(chosen.attackId());
                return;
            }
        }

        // ---------------------------------------------------------------
        // 3) No hay ataque disponible: moverse con criterio.
        // ---------------------------------------------------------------
        tickMovement(target, distSqr);

        if (++ticksStuckWithoutAttack >= FORCE_ACTION_TICKS) {
            ticksStuckWithoutAttack = 0;
            forceGapCloser(distSqr);
        }
    }

    private void tickCooldowns() {
        if (clawCooldown > 0) clawCooldown--;
        if (iceFistCooldown > 0) iceFistCooldown--;
        if (clawProjectileCooldown > 0) clawProjectileCooldown--;
        if (iceFistGroundCooldown > 0) iceFistGroundCooldown--;
        if (iceChargeCooldown > 0) iceChargeCooldown--;
        if (jumpSmashCooldown > 0) jumpSmashCooldown--;
        if (channellingCooldown > 0) channellingCooldown--;
        if (roarCooldown > 0) roarCooldown--;
        if (doubleGroundCooldown > 0) doubleGroundCooldown--;
        if (doubleSlashCooldown > 0) doubleSlashCooldown--;
        if (!executionArmed && nanook.getPhaseIndex() >= 2) {
            executionArmed = true;
            executionCooldown = EXECUTION_FIRST_USE_DELAY_TICKS;
        }
        if (executionCooldown > 0) executionCooldown--;
        if (globalRecoveryCooldown > 0) globalRecoveryCooldown--;
        if (strafeSwitchCooldown > 0) strafeSwitchCooldown--;

        if (pendingComboAttack != -1 && --pendingComboExpiry <= 0) {
            pendingComboAttack = -1;
        }

        if (nanook.isEnraged()) {
            if (ticksSinceEnrage < 0) {
                ticksSinceEnrage = 0;
            } else if (ticksSinceEnrage < EXECUTION_GRACE_TICKS) {
                ticksSinceEnrage++;
            }
        }
    }

    // ===================================================================
    // COMBOS
    // ===================================================================

    /**
     * Probabilidad de que un ataque encadene, por fase. En fase 0 es baja a
     * propósito: la primera cuarta parte de la pelea es donde el jugador
     * aprende los tiempos de cada ataque, y encadenar ahí lo único que hace
     * es que no llegue a leer nada.
     */
    private double comboChance() {
        return switch (nanook.getPhaseIndex()) {
            case 0 -> 0.20D;
            case 1 -> 0.40D;
            case 2 -> 0.60D;
            default -> 0.80D;
        };
    }

    /** Tras disparar 'attackId', decide si reserva una continuación. */
    private void scheduleCombo(int attackId) {
        if (nanook.getRandom().nextDouble() > comboChance()) {
            return;
        }
        int follow = pickFollowUp(attackId);
        if (follow == -1) {
            return;
        }
        pendingComboAttack = follow;
        // El combo caduca si no se puede ejecutar en ~4s (por ejemplo si el
        // jugador se fue corriendo lejísimos en el medio).
        pendingComboExpiry = 80;
        globalRecoveryCooldown = COMBO_RECOVERY_TICKS;
    }

    /**
     * TABLA DE CONTINUACIONES. Cada entrada es "después de X, qué tiene
     * sentido encadenar". El criterio no es "qué pega más", sino "qué
     * aprovecha el estado en el que quedó el jugador":
     *
     *   ROAR deja al jugador LEJOS y aturdido -> salto (cubre distancia).
     *   ICE_CHARGE que conectó lo deja aturdido y cerca -> doble tajo.
     *   ICE_CHARGE que falló: el propio ataque ya encadena un rugido.
     *   JUMP_SMASH lo deja en el piso, pegado -> APLAUSO si está disponible; si no, golpe doble al suelo.
     *   DOUBLE_GROUND lo deja aturdido y sin escudo -> APLAUSO si está disponible.
     *   DOUBLE_SLASH (melee) -> APLAUSO si está disponible, remate natural de una
     *   ráfaga cuerpo a cuerpo; si no, zarpazo para seguir la presión.
     *   ICE_FIST_GROUND (pinchos) lo obliga a moverse -> embestida.
     */
    private int pickFollowUp(int attackId) {
        RandomSource random = nanook.getRandom();
        return switch (attackId) {
            case NanookEntity.ATTACK_ROAR ->
                    NanookEntity.ATTACK_JUMP_SMASH;
            case NanookEntity.ATTACK_ICE_CHARGE ->
                    nanook.didLastChargeConnect()
                            ? NanookEntity.ATTACK_DOUBLE_SLASH
                            : -1;
            case NanookEntity.ATTACK_JUMP_SMASH ->
                    canUseExecution()
                            ? NanookEntity.ATTACK_EXECUTION
                            : (random.nextBoolean()
                                    ? NanookEntity.ATTACK_DOUBLE_GROUND
                                    : NanookEntity.ATTACK_CLAW);
            case NanookEntity.ATTACK_DOUBLE_GROUND ->
                    canUseExecution()
                            ? NanookEntity.ATTACK_EXECUTION
                            : NanookEntity.ATTACK_CLAW;
            case NanookEntity.ATTACK_DOUBLE_SLASH ->
                    canUseExecution()
                            ? NanookEntity.ATTACK_EXECUTION
                            : NanookEntity.ATTACK_CLAW;
            case NanookEntity.ATTACK_ICE_FIST_GROUND ->
                    NanookEntity.ATTACK_ICE_CHARGE;
            case NanookEntity.ATTACK_CLAW ->
                    random.nextBoolean() ? NanookEntity.ATTACK_DOUBLE_SLASH : -1;
            default -> -1;
        };
    }

    /**
     * Intenta disparar el ataque reservado. Devuelve false si en este
     * momento no tiene sentido (fuera de rango, etc.) para que el sorteo
     * normal tome el control.
     *
     * OJO: los combos IGNORAN el cooldown individual del ataque a
     * propósito -- si no, "rugido -> salto" casi nunca saldría porque el
     * salto tiene 12s de cooldown. Lo que sí hacen es RESETEAR ese
     * cooldown, así que un combo te "gasta" el ataque igual.
     */
    private boolean tryRunCombo(int attackId, double distSqr) {
        switch (attackId) {
            case NanookEntity.ATTACK_JUMP_SMASH -> {
                if (distSqr > sqr(NanookEntity.JUMP_SMASH_TRIGGER_RANGE)) return false;
                nanook.triggerJumpSmash();
                jumpSmashCooldown = scaled(JUMP_SMASH_COOLDOWN_TICKS);
                globalRecoveryCooldown = 26;
            }
            case NanookEntity.ATTACK_DOUBLE_SLASH -> {
                if (distSqr > sqr(NanookEntity.DOUBLE_SLASH_RANGE)) return false;
                nanook.triggerDoubleSlash();
                doubleSlashCooldown = scaled(DOUBLE_SLASH_COOLDOWN_TICKS);
                globalRecoveryCooldown = 14;
            }
            case NanookEntity.ATTACK_DOUBLE_GROUND -> {
                if (distSqr > sqr(NanookEntity.DOUBLE_GROUND_RANGE)) return false;
                nanook.triggerDoubleGround();
                doubleGroundCooldown = scaled(DOUBLE_GROUND_COOLDOWN_TICKS);
                globalRecoveryCooldown = 24;
            }
            case NanookEntity.ATTACK_CLAW -> {
                if (distSqr > sqr(NanookEntity.CLAW_RANGE)) return false;
                nanook.triggerClawAttack();
                clawCooldown = scaled(CLAW_COOLDOWN_TICKS);
                globalRecoveryCooldown = 10;
            }
            case NanookEntity.ATTACK_ICE_CHARGE -> {
                if (distSqr < sqr(MIN_GAP_CLOSER_RANGE)
                        || distSqr > sqr(NanookEntity.ICE_CHARGE_TRIGGER_RANGE)) return false;
                nanook.triggerIceCharge();
                iceChargeCooldown = scaled(ICE_CHARGE_COOLDOWN_TICKS);
                globalRecoveryCooldown = 20;
            }
            case NanookEntity.ATTACK_EXECUTION -> {
                if (!canUseExecution() || distSqr > sqr(NanookEntity.EXECUTION_RANGE)) return false;
                nanook.triggerExecution();
                executionCooldown = executionCooldownTicks();
                globalRecoveryCooldown = 30;
            }
            default -> {
                return false;
            }
        }
        lastChosenAttackId = attackId;
        return true;
    }

    /**
     * Disponible desde fase 2 (<= 50 % de vida), no solo en furia. En furia se
     * espera además EXECUTION_GRACE_TICKS tras el rugido de entrada: entrar en
     * furia ya es sorpresa suficiente.
     */
    private boolean canUseExecution() {
        return executionArmed
                && executionCooldown <= 0
                && (!nanook.isEnraged() || ticksSinceEnrage >= EXECUTION_GRACE_TICKS);
    }

    /** Cooldown tras usarlo: más corto en furia. */
    private int executionCooldownTicks() {
        return nanook.isEnraged() ? EXECUTION_COOLDOWN_ENRAGED_TICKS : EXECUTION_COOLDOWN_PHASE2_TICKS;
    }

    // ===================================================================
    // Movimiento
    // ===================================================================

    private void tickMovement(LivingEntity target, double distSqr) {
        double tooCloseSqr = sqr(TOO_CLOSE_RANGE);
        double meleeSqr = sqr(NanookEntity.CLAW_RANGE);

        if (distSqr < tooCloseSqr) {
            // Está literalmente encima: separarse.
            Vec3 away = nanook.position().subtract(target.position());
            if (away.lengthSqr() < 1.0E-4) {
                away = nanook.getForward().reverse();
            }
            away = away.normalize().scale(4.0D);
            if (--pathRecalcCooldown <= 0) {
                pathRecalcCooldown = 8;
                nanook.getNavigation().moveTo(
                        nanook.getX() + away.x, nanook.getY(), nanook.getZ() + away.z, 0.8D);
            }
            return;
        }

        if (distSqr <= meleeSqr) {
            // Strafe alrededor del objetivo.
            if (strafeSwitchCooldown <= 0) {
                strafeDirection = -strafeDirection;
                strafeSwitchCooldown = 30 + nanook.getRandom().nextInt(40);
            }
            if (--pathRecalcCooldown <= 0) {
                pathRecalcCooldown = 6;
                Vec3 toTarget = target.position().subtract(nanook.position());
                double angle = Math.atan2(toTarget.z, toTarget.x) + strafeDirection * Math.PI / 2.0D;
                double radius = Mth.clamp(Math.sqrt(distSqr), 3.0D, NanookEntity.CLAW_RANGE);
                double x = target.getX() + Math.cos(angle) * radius;
                double z = target.getZ() + Math.sin(angle) * radius;
                nanook.getNavigation().moveTo(x, nanook.getY(), z, 0.75D);
            }
            return;
        }

        // Lejos: acercarse, recalculando el path cada tantos ticks.
        if (--pathRecalcCooldown <= 0) {
            pathRecalcCooldown = 4 + nanook.getRandom().nextInt(3);
            nanook.getNavigation().moveTo(target, SPEED_MODIFIER);
        }
    }

    // ===================================================================
    // Sorteo
    // ===================================================================

    private List<AttackCandidate> buildCandidates(double distSqr) {
        List<AttackCandidate> candidates = new ArrayList<>();

        double clawReachSqr = sqr(NanookEntity.CLAW_RANGE);
        double roarReachSqr = sqr(NanookEntity.ROAR_RANGE);
        double minGapSqr = sqr(MIN_GAP_CLOSER_RANGE);

        // --- Ejecución (furia): se evalúa PRIMERO porque es el momento
        // estelar del jefe, pero entra al bombo como uno más. ---
        if (canUseExecution() && distSqr <= sqr(NanookEntity.EXECUTION_RANGE)) {
            candidates.add(new AttackCandidate(NanookEntity.ATTACK_EXECUTION, EXECUTION_WEIGHT, 30, () -> {
                nanook.triggerExecution();
                executionCooldown = executionCooldownTicks();
            }));
        }

        // --- Melee ---
        if (distSqr <= clawReachSqr && clawCooldown <= 0) {
            candidates.add(new AttackCandidate(NanookEntity.ATTACK_CLAW, CLAW_WEIGHT, 10, () -> {
                nanook.triggerClawAttack();
                clawCooldown = scaled(CLAW_COOLDOWN_TICKS);
            }));
        }
        if (distSqr <= clawReachSqr && iceFistCooldown <= 0) {
            candidates.add(new AttackCandidate(NanookEntity.ATTACK_ICE_FIST, ICE_FIST_WEIGHT, 12, () -> {
                nanook.triggerIceFistAttack();
                iceFistCooldown = scaled(ICE_FIST_COOLDOWN_TICKS);
            }));
        }
        if (distSqr <= sqr(NanookEntity.DOUBLE_SLASH_RANGE) && doubleSlashCooldown <= 0) {
            candidates.add(new AttackCandidate(NanookEntity.ATTACK_DOUBLE_SLASH, DOUBLE_SLASH_WEIGHT, 14, () -> {
                nanook.triggerDoubleSlash();
                doubleSlashCooldown = scaled(DOUBLE_SLASH_COOLDOWN_TICKS);
            }));
        }
        // Rugido: su botón de "sacármelos de encima" cuando lo comboean.
        if (distSqr <= roarReachSqr && roarCooldown <= 0) {
            candidates.add(new AttackCandidate(NanookEntity.ATTACK_ROAR, ROAR_WEIGHT, 16, () -> {
                nanook.triggerRoar();
                roarCooldown = scaled(ROAR_COOLDOWN_TICKS);
            }));
        }

        // --- Golpe al suelo ---
        if (distSqr >= sqr(DOUBLE_GROUND_MIN_RANGE)
                && distSqr <= sqr(NanookEntity.DOUBLE_GROUND_RANGE)
                && doubleGroundCooldown <= 0) {
            candidates.add(new AttackCandidate(NanookEntity.ATTACK_DOUBLE_GROUND, DOUBLE_GROUND_WEIGHT, 24, () -> {
                nanook.triggerDoubleGround();
                doubleGroundCooldown = scaled(DOUBLE_GROUND_COOLDOWN_TICKS);
            }));
        }

        // --- Distancia ---
        if (distSqr > clawReachSqr && distSqr <= sqr(NanookEntity.CLAW_PROJECTILE_RANGE)
                && clawProjectileCooldown <= 0) {
            candidates.add(new AttackCandidate(NanookEntity.ATTACK_CLAW_PROJECTILE, CLAW_PROJECTILE_WEIGHT, 12, () -> {
                nanook.triggerClawProjectileAttack();
                clawProjectileCooldown = scaled(CLAW_PROJECTILE_COOLDOWN_TICKS);
            }));
        }
        if (distSqr <= sqr(NanookEntity.ICE_GROUND_RANGE) && iceFistGroundCooldown <= 0) {
            candidates.add(new AttackCandidate(NanookEntity.ATTACK_ICE_FIST_GROUND, ICE_FIST_GROUND_WEIGHT, 16, () -> {
                nanook.triggerIceFistGround();
                iceFistGroundCooldown = scaled(ICE_FIST_GROUND_COOLDOWN_TICKS);
            }));
        }
        if (distSqr <= sqr(NanookEntity.CHANNELLING_RANGE) && channellingCooldown <= 0) {
            candidates.add(new AttackCandidate(NanookEntity.ATTACK_CHANNELLING, CHANNELLING_WEIGHT, 30, () -> {
                nanook.triggerChannelling();
                channellingCooldown = scaled(CHANNELLING_COOLDOWN_TICKS);
            }));
        }

        // --- Gap-closers (solo con espacio real de por medio) ---
        if (distSqr > minGapSqr && distSqr <= sqr(NanookEntity.ICE_CHARGE_TRIGGER_RANGE)
                && iceChargeCooldown <= 0) {
            candidates.add(new AttackCandidate(NanookEntity.ATTACK_ICE_CHARGE, ICE_CHARGE_WEIGHT, 20, () -> {
                nanook.triggerIceCharge();
                iceChargeCooldown = scaled(ICE_CHARGE_COOLDOWN_TICKS);
            }));
        }
        if (distSqr > minGapSqr && distSqr <= sqr(NanookEntity.JUMP_SMASH_TRIGGER_RANGE)
                && jumpSmashCooldown <= 0) {
            candidates.add(new AttackCandidate(NanookEntity.ATTACK_JUMP_SMASH, JUMP_SMASH_WEIGHT, 26, () -> {
                nanook.triggerJumpSmash();
                jumpSmashCooldown = scaled(JUMP_SMASH_COOLDOWN_TICKS);
            }));
        }

        return candidates;
    }

    /**
     * Anti-kiteo. Prioriza el salto (atraviesa terreno porque el arco es
     * manual); si el jugador está pegado, el golpe al suelo o el rugido.
     * Se saltea el cooldown normal a propósito.
     */
    private void forceGapCloser(double distSqr) {
        nanook.getNavigation().stop();
        if (distSqr > sqr(MIN_GAP_CLOSER_RANGE)) {
            nanook.triggerJumpSmash();
            jumpSmashCooldown = scaled(JUMP_SMASH_COOLDOWN_TICKS);
            globalRecoveryCooldown = 26;
            scheduleCombo(NanookEntity.ATTACK_JUMP_SMASH);
        } else if (doubleGroundCooldown <= 0) {
            nanook.triggerDoubleGround();
            doubleGroundCooldown = scaled(DOUBLE_GROUND_COOLDOWN_TICKS);
            globalRecoveryCooldown = 24;
            scheduleCombo(NanookEntity.ATTACK_DOUBLE_GROUND);
        } else {
            nanook.triggerRoar();
            roarCooldown = scaled(ROAR_COOLDOWN_TICKS);
            globalRecoveryCooldown = 16;
            scheduleCombo(NanookEntity.ATTACK_ROAR);
        }
    }

    private int scaled(int baseTicks) {
        return Math.max(1, (int) Math.round(baseTicks * nanook.getAggressionCooldownMultiplier()));
    }

    private static double sqr(double v) {
        return v * v;
    }

    private static AttackCandidate pickWeighted(List<AttackCandidate> candidates, RandomSource random,
                                                int lastChosenAttackId) {
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

    private record AttackCandidate(int attackId, double weight, int recoveryTicks, Runnable trigger) {
        AttackCandidate {
            recoveryTicks = Math.max(MIN_RECOVERY_TICKS, recoveryTicks);
        }
    }
}
