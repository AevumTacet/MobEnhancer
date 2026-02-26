package com.mobenhancer.boss;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EndermanOverseer extends Boss {

    // ── Atributos base ───────────────────────────────────────────────
    private static final double MAX_HEALTH    = 800.0;
    private static final double SCALE         = 2.5;
    private static final double ATTACK_DAMAGE = 10.0;

    // ── Movimiento manual ────────────────────────────────────────────
    private static final double WALK_FOLLOW_RANGE = 6.0; // sigue al target hasta 6 bloques

    private static final double PULL_CHANCE          = 0.4;
    private static final int    PULL_COOLDOWN_SECS   = 3;

    private static final double BLOCK_THROW_MAX_RANGE  = 24.0;
    private static final double BLOCK_THROW_CHANCE     = 0.4;
    private static final double BLOCK_THROW_DAMAGE     = 25.0;
    private static final double BLOCK_THROW_RADIUS     = 3.0;
    private static final int    BLOCK_THROW_CHARGE_SECS = 2;

    private static final double ENDERFIRE_AURA_RADIUS  = 8.0;
    private static final double[] ENDERFIRE_AURA_DAMAGE = { 15.0, 9.0, 5.0 };
    // Intervalo entre pulsos en ticks (20 = 1 segundo)
    private static final long ENDERFIRE_AURA_PULSE_INTERVAL = 20L;
    // ── Disarm warning ───────────────────────────────────────────────
    private static final int    DISARM_WARNING_SECS    = 5;

    // ── Mustering ───────────────────────────────────────────────────
    private static final double SUMMON_CHANCE       = 0.3;
    private static final int    SUMMON_PER_WAVE     = 2;
    private static final int    SUMMON_MAX_WAVES    = 3;      
    private static final long   SUMMON_COOLDOWN_MS  = 40_000L;
    private static final double SUMMON_RADIUS       = 6.0;

    // ── Disarm ───────────────────────────────────────────────────────
    private static final double DISARM_CHANCE              = 0.3;
    private static final double DISARM_RANGE               = 8.0;

    // ── Ender Fire Rain ──────────────────────────────────────────────
    private static final long   RAIN_COOLDOWN_MS    = 8_000L;
    private static final double RAIN_CHANCE         = 0.3;
    private static final int    RAIN_FIRE_COUNT     = 8;
    private static final double RAIN_SPAWN_HEIGHT   = 12.0;
    private static final double RAIN_OUTER_RADIUS   = 12.0;
    private static final double RAIN_INNER_RADIUS   = 2.0;
    // Duración del anillo: 3s advertencia + 2s post-caída = 5s total = 100 ticks
    private static final int    RING_WARNING_TICKS  = 3 * 20; // 60 ticks antes de caída
    private static final int    RING_POST_TICKS     = 2 * 20; // 40 ticks después

    // ── Fase 2 ───────────────────────────────────────────────────────
    private static final double PHASE2_HEALTH_PERCENT = 0.30;
    private static final int    DISARM_COOLDOWN_PHASE2_SECS = 60;
    private boolean phase2Active = false;

    // ── Estado ───────────────────────────────────────────────────────
    private boolean aiEnabled = false; // rastrea el estado actual de la IA
    private final Random random = new Random();
    private LivingEntity target;

    private int pullCooldown   = 0;
    private int disarmCooldown = 0;
    private int attackCooldown = 0;

    private long lastRainTime   = 0L;
    private boolean rainWarningActive   = false;

    private long nextSummonAllowedTime = 0L; // timestamp absoluto del próximo mustering permitido
    private boolean summonWarningActive = false;
    private int summonWaveCount = 0;
    
    private boolean isChargingBlockThrow = false;
    private boolean isDisarmWarningActive = false;

    // Los shulkers sobreviven al boss, por eso no los matamos en onDeath
    private final List<Shulker> shulkers = new ArrayList<>();

    public EndermanOverseer(JavaPlugin plugin) {
        super(plugin, "enderman_overseer", "Enderman Overseer", MAX_HEALTH);
    }

    // ══════════════════════════════════════════════════════════════════
    // SPAWN
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void spawn(Location location) {
        World world = location.getWorld();

        Enderman overseer = (Enderman) world.spawnEntity(location, EntityType.ENDERMAN);
        overseer.setRemoveWhenFarAway(false);
        overseer.setAI(false);
        aiEnabled = false; // ← IA completamente desactivada, controlamos todo manualmente
        overseer.getAttribute(Attribute.SCALE).setBaseValue(SCALE);
        overseer.getAttribute(Attribute.MAX_HEALTH).setBaseValue(MAX_HEALTH);
        overseer.setHealth(MAX_HEALTH);
        overseer.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(ATTACK_DAMAGE);
        overseer.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.25);
        overseer.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(32.0);
        overseer.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
        overseer.getEquipment().setHelmetDropChance(0);
        overseer.setCarriedBlock(null);

        markAsBoss(overseer);

        this.entity   = overseer;
        this.entityId = overseer.getUniqueId();
        this.active   = true;

        initBossBar();
        holdChunk();
        nextSummonAllowedTime = System.currentTimeMillis() + 15_000L;
        startTicking();
        startSpiralParticles();
    }

    // ══════════════════════════════════════════════════════════════════
    // TICK
    // ══════════════════════════════════════════════════════════════════

    private void startTicking() {
        new BukkitRunnable() {
            private int deadCheckCounter = 0;

            @Override
            public void run() {
                if (!active) { cancel(); return; }

                // Entidad nula o inválida — dar gracia antes de cancelar
                if (entity == null || !entity.isValid()) {
                    if (++deadCheckCounter >= 5) {
                        plugin.getLogger().warning(
                            "[EndermanOverseer] Entidad inválida por "
                            + deadCheckCounter + "s consecutivos. Cancelando tick.");
                        cancel();
                    }
                    return;
                }

                // isDead() — también dar gracia, puede ser transitorio
                // (ocurre cuando el único target muere y el servidor recarga chunks)
                if (entity.isDead()) {
                    if (++deadCheckCounter >= 3) {
                        cancel();
                    }
                    return;
                }

                // Entidad válida y viva — resetear contador y ejecutar
                deadCheckCounter = 0;
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @Override
    public void tick() {
        if (!active || entity == null || entity.isDead()) return;

        updateBossBar();
        updateBossBarViewers(50.0);

        if (!isChargingBlockThrow) {
            if (entity instanceof Enderman enderman && enderman.getCarriedBlock() != null) {
                enderman.setCarriedBlock(null);
            }
        }

        if (!phase2Active && entity.getHealth() <= MAX_HEALTH * PHASE2_HEALTH_PERCENT) {
            activatePhase2();
        }

        updateTarget();

        // Rotar siempre hacia el target si existe, independientemente del estado de la IA
        if (target != null) {
            lookAtTarget();
        }

        if (target == null) {
            // Sin target: asegurarse de que la IA esté desactivada
            setAIState(false);
            return;
        }

        lastTargetTime = System.currentTimeMillis();

        if (pullCooldown   > 0) pullCooldown--;
        if (disarmCooldown > 0) disarmCooldown--;
        if (attackCooldown > 0) attackCooldown--;

        double distance = entity.getLocation().distance(target.getLocation());

        if (distance <= WALK_FOLLOW_RANGE) {
            // ── Zona cercana: activar IA para movimiento y melee nativo ──
            setAIState(true);

        } else {
            // ── Zona lejana: desactivar IA, usar habilidades a distancia ──
            setAIState(false);

            // 1. Pull vs Block Throw
            if (!isChargingBlockThrow && pullCooldown == 0) {
                boolean inBlockThrowRange = distance <= BLOCK_THROW_MAX_RANGE;

                if (inBlockThrowRange) {
                    if (random.nextDouble() < BLOCK_THROW_CHANCE) {
                        startBlockThrowCharge();
                        pullCooldown = PULL_COOLDOWN_SECS;
                    } else if (shouldPull()) {
                        applyPull();
                        pullCooldown = PULL_COOLDOWN_SECS;
                    }
                } else {
                    if (shouldPull()) {
                        applyPull();
                        pullCooldown = PULL_COOLDOWN_SECS;
                    }
                }
            }

            // 2. Disarm — solo fase 2
            if (phase2Active && !isDisarmWarningActive
                    && disarmCooldown == 0
                    && distance <= DISARM_RANGE
                    && shouldDisarm()) {
                startDisarmWarning();
                disarmCooldown = DISARM_COOLDOWN_PHASE2_SECS;
            }

            long now = System.currentTimeMillis();

            // 3. Mustering
            if (!summonWarningActive
                    && summonWaveCount < SUMMON_MAX_WAVES
                    && now >= nextSummonAllowedTime
                    && random.nextDouble() < SUMMON_CHANCE) {
                summonWarningActive = true;
                nextSummonAllowedTime = Long.MAX_VALUE;
                startSummonWarning();
            }

            // 4. Ender Fire Rain
            if (!rainWarningActive
                    && now - lastRainTime >= RAIN_COOLDOWN_MS
                    && random.nextDouble() < RAIN_CHANCE) {
                rainWarningActive = true;
                lastRainTime = now;
                startRainWarning();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // TARGET
    // ══════════════════════════════════════════════════════════════════

    private void updateTarget() {
        double closest = Double.MAX_VALUE;
        Player nearest = null;

        for (Player p : entity.getWorld().getPlayers()) {
            // isValidTarget() comprueba: vivo, modo de juego, mundo, y distancia <= 100
            if (!isValidTarget(p)) continue;

            double dist = p.getLocation().distanceSquared(entity.getLocation());
            if (dist < closest) {
                closest = dist;
                nearest = p;
            }
        }

        target = nearest;
        if (entity instanceof Mob mob) mob.setTarget(target);
    }

    // ══════════════════════════════════════════════════════════════════
    // PULL
    // ══════════════════════════════════════════════════════════════════

    private boolean shouldPull() {
        if (target == null) return false;
        return random.nextDouble() <= PULL_CHANCE;
    }

    private void applyPull() {
        if (target == null) return;

        Location bossLoc   = entity.getLocation();
        Location targetLoc = target.getLocation();

        // Si el target tiene levitación (p.ej. impactado por shulker),
        // teletransportarlo directamente a los pies del Overseer en lugar
        // de aplicar velocidad parabólica que lo lanzaría aún más arriba
        if (target instanceof LivingEntity living &&
                living.hasPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION)) {

            // Encontrar el suelo junto al Overseer para aterrizar al target
            Location landingLoc = bossLoc.clone();
            // Offset lateral de 1.5 bloques para que no quede dentro del Overseer
            landingLoc.add(
                    Math.sin(Math.toRadians(bossLoc.getYaw())) * -1.5,
                    0,
                    Math.cos(Math.toRadians(bossLoc.getYaw())) * 1.5
            );
            // Asegurar que la Y sea el suelo real, no flotando
            landingLoc.setY(bossLoc.getWorld()
                    .getHighestBlockYAt(landingLoc.getBlockX(), landingLoc.getBlockZ()));

            // Quitar levitación antes de teletransportar para que no siga subiendo
            living.removePotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION);
            target.teleport(landingLoc);

            // Efectos visuales de la teletransportación forzada
            target.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                    landingLoc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.15);
            target.getWorld().playSound(landingLoc,
                    Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.5f);
            return;
        }

        // Comportamiento normal: vector horizontal hacia el boss
        Vector horizontal = new Vector(
                bossLoc.getX() - targetLoc.getX(),
                0,
                bossLoc.getZ() - targetLoc.getZ()
        ).normalize().multiply(1.4);

        double heightDiff = bossLoc.getY() - targetLoc.getY();
        double vy = heightDiff > 1.0
                ? Math.min(0.5 + heightDiff * 0.05, 0.6)
                : 0.35;

        horizontal.setY(vy);
        target.setVelocity(horizontal);

        World world = target.getWorld();
        world.spawnParticle(Particle.REVERSE_PORTAL,
                targetLoc.clone().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.1);
        world.playSound(targetLoc,
                Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.7f);
        world.spawnParticle(Particle.ELECTRIC_SPARK,
                entity.getEyeLocation(), 10, 0.3, 0.3, 0.3, 0.05);
    }

    // ══════════════════════════════════════════════════════════════════
    // BLOCK THROW
    // ══════════════════════════════════════════════════════════════════
    private void startBlockThrowCharge() {
        if (!(entity instanceof Enderman enderman)) return;
        isChargingBlockThrow = true;

        // Capturar el target en el momento de inicio de la carga
        // para que el lanzamiento apunte a donde estaba, no a donde está al lanzar
        final LivingEntity throwTarget = target;

        // Paso 1: colocar el bloque en las manos (postura nativa de brazos levantados)
        enderman.setCarriedBlock(
                Bukkit.createBlockData(Material.GRASS_BLOCK));

        // Sonido de "levantar" algo pesado
        entity.getWorld().playSound(entity.getLocation(),
                Sound.BLOCK_GRASS_BREAK, 1.0f, 0.5f);
        entity.getWorld().playSound(entity.getLocation(),
                Sound.ENTITY_ENDERMAN_SCREAM, 0.7f, 0.4f);

        // Partículas de tierra alrededor del bloque en manos durante la carga
        new BukkitRunnable() {
            int ticks = 0;
            final int chargeTicks = BLOCK_THROW_CHARGE_SECS * 20;

            @Override
            public void run() {
                if (!active || entity == null || entity.isDead()) {
                    isChargingBlockThrow = false;
                    cancel();
                    return;
                }

                if (ticks >= chargeTicks) {
                    // Lanzar el bloque
                    launchBlock(throwTarget);
                    cancel();
                    return;
                }

                // Partículas de tierra girando alrededor del boss durante la carga
                Location handLoc = entity.getLocation().clone().add(0, SCALE * 1.5, 0);
                double chargeRadius = 0.8;
                double chargeAngle = ticks * 0.4;

                for (int i = 0; i < 3; i++) {
                    double a = chargeAngle + i * (Math.PI * 2 / 3);
                    double x = handLoc.getX() + chargeRadius * Math.cos(a);
                    double z = handLoc.getZ() + chargeRadius * Math.sin(a);
                    Location pLoc = new Location(handLoc.getWorld(), x, handLoc.getY(), z);

                    handLoc.getWorld().spawnParticle(Particle.BLOCK,
                            pLoc, 2, 0.1, 0.1, 0.1, 0.05,
                            Bukkit.createBlockData(Material.GRASS_BLOCK));
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void launchBlock(LivingEntity throwTarget) {
        if (!(entity instanceof Enderman enderman)) {
            isChargingBlockThrow = false;
            return;
        }

        // Quitar el bloque de las manos
        enderman.setCarriedBlock(null);
        isChargingBlockThrow = false;

        if (throwTarget == null || throwTarget.isDead() || !throwTarget.isValid()) return;

        // Posición de lanzamiento: a la altura de las manos del Overseer
        Location launchLoc = entity.getLocation().clone().add(0, SCALE * 1.2, 0);
        World world = launchLoc.getWorld();

        // Sonido de lanzamiento
        world.playSound(entity.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.6f);
        world.playSound(entity.getLocation(), Sound.BLOCK_GRASS_BREAK, 1.0f, 0.8f);

        // Spawnar el FallingBlock
        FallingBlock fallingBlock = world.spawnFallingBlock(
                launchLoc,
                Bukkit.createBlockData(Material.GRASS_BLOCK));
        fallingBlock.setDropItem(false);      // no dropearlo al aterrizar
        fallingBlock.setHurtEntities(false);  // desactivar daño nativo — lo manejamos nosotros
        fallingBlock.setGlowing(true);
        fallingBlock.getPersistentDataContainer().set(
        new NamespacedKey(plugin, "overseer_block_throw"),
        PersistentDataType.BYTE, (byte) 1);

        // Calcular velocidad parabólica hacia el target
        Location targetLoc = throwTarget.getLocation().clone().add(0, 1, 0);
        Vector toTarget = targetLoc.toVector().subtract(launchLoc.toVector());
        double horizontalDist = Math.sqrt(toTarget.getX() * toTarget.getX()
                + toTarget.getZ() * toTarget.getZ());

        // Tiempo de vuelo estimado basado en distancia horizontal
        // A mayor distancia, más tiempo y más velocidad horizontal necesitamos
        double flightTime = Math.max(0.5, horizontalDist / 12.0);
        double gravity = 0.04; // gravedad de FallingBlock en Minecraft

        double vx = toTarget.getX() / (flightTime * 20);
        double vz = toTarget.getZ() / (flightTime * 20);
        // vy debe compensar la caída por gravedad durante el vuelo
        double vy = (toTarget.getY() / (flightTime * 20))
                + (gravity * flightTime * 20) / 2.0;

        // Pequeño boost vertical para que la trayectoria sea claramente parabólica
        vy += 0.3;

        fallingBlock.setVelocity(new Vector(vx, vy, vz));

        // Runnable que monitorea el bloque en vuelo
        new BukkitRunnable() {
            int ticks = 0;
            boolean impacted = false;
            final int maxTicks = (int) (flightTime * 20) + 60;

            @Override
            public void run() {
                // Si ya no es válido (aterrizó y se convirtió en bloque),
                // EntityChangeBlockEvent ya disparó — no hacer nada más
                if (impacted || ticks > maxTicks) {
                    cancel();
                    return;
                }

                if (!fallingBlock.isValid()) {
                    // El bloque aterrizó — EntityChangeBlockEvent se encarga del impacto
                    cancel();
                    return;
                }

                // Estela de partículas durante el vuelo
                world.spawnParticle(Particle.BLOCK,
                        fallingBlock.getLocation(), 3, 0.2, 0.2, 0.2, 0.05,
                        Bukkit.createBlockData(Material.GRASS_BLOCK));

                // Detección de impacto directo con entidades en vuelo
                for (Entity e : world.getNearbyEntities(
                        fallingBlock.getLocation(), 1.5, 1.5, 1.5)) {
                    if (!(e instanceof LivingEntity)) continue;
                    if (e.equals(entity)) continue;

                    impacted = true;
                    // Usar la posición del jugador como punto de impacto, no la del bloque
                    // El bloque estaba a 1.5 bloques del jugador — la posición del jugador
                    // es más precisa para centrar la onda de choque
                    triggerBlockImpact(e.getLocation().clone(), true);
                    fallingBlock.remove();
                    cancel();
                    return;
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void onBlockThrowLand(Location exactImpactLoc) {
        triggerBlockImpact(exactImpactLoc, false);
    }

    private void triggerBlockImpact(Location impactLoc, boolean directHit) {
        World world = impactLoc.getWorld();

        world.playSound(impactLoc, Sound.BLOCK_GRASS_BREAK, 1.5f, 0.4f);
        world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);

        // Onda de choque — sin cambios
        new BukkitRunnable() {
            double currentRadius = 0.2;
            int waveTicks = 0;

            @Override
            public void run() {
                if (waveTicks >= 12 || currentRadius > BLOCK_THROW_RADIUS + 0.5) {
                    cancel();
                    return;
                }

                int points = Math.max(8, (int) (currentRadius * 10));
                double angleStep = 2 * Math.PI / points;

                for (int i = 0; i < points; i++) {
                    double angle = i * angleStep;
                    double x = impactLoc.getX() + currentRadius * Math.cos(angle);
                    double z = impactLoc.getZ() + currentRadius * Math.sin(angle);
                    int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
                    Location ringLoc = new Location(world, x, y, z);

                    double t = currentRadius / BLOCK_THROW_RADIUS;
                    int r = (int) (100 * (1 - t) + 150 * t);
                    int g = (int) (150 * (1 - t) + 150 * t);
                    int b = (int) (50  * (1 - t) + 150 * t);

                    world.spawnParticle(Particle.DUST, ringLoc,
                            2, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(r, g, b), 1.5f));
                    world.spawnParticle(Particle.BLOCK, ringLoc,
                            1, 0.1, 0.1, 0.1, 0.05,
                            Bukkit.createBlockData(Material.GRASS_BLOCK));
                }

                if (waveTicks == 0) {
                    world.spawnParticle(Particle.BLOCK,
                            impactLoc.clone().add(0, 0.5, 0),
                            40, 0.5, 0.5, 0.5, 0.15,
                            Bukkit.createBlockData(Material.GRASS_BLOCK));
                }

                currentRadius += BLOCK_THROW_RADIUS / 10.0;
                waveTicks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // Daño en área con knockback seguro
        for (Entity e : world.getNearbyEntities(
                impactLoc, BLOCK_THROW_RADIUS, BLOCK_THROW_RADIUS, BLOCK_THROW_RADIUS)) {
            if (!(e instanceof LivingEntity living)) continue;
            if (e.equals(entity)) continue;

            double dist = e.getLocation().distance(impactLoc);
            double damageFactor = 1.0 - (dist / BLOCK_THROW_RADIUS);
            double finalDamage = BLOCK_THROW_DAMAGE * Math.max(0.3, damageFactor);
            living.damage(finalDamage, entity);

            // ── Knockback seguro: verificar que el vector no sea cero ──────
            Vector kbDir = living.getLocation().toVector()
                    .subtract(impactLoc.toVector());

            // Si el jugador está exactamente en el punto de impacto,
            // kbDir es (0,0,0) y normalize() produciría NaN
            // En ese caso usamos una dirección aleatoria horizontal
            if (kbDir.lengthSquared() < 0.0001) {
                kbDir = new Vector(
                        (Math.random() - 0.5) * 2,
                        0,
                        (Math.random() - 0.5) * 2
                );
            }

            kbDir.normalize().multiply(1.2).setY(0.5);
            living.setVelocity(kbDir);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // DISARM
    // ══════════════════════════════════════════════════════════════════

    private boolean shouldDisarm() {
        if (target == null || !(entity instanceof Enderman enderman)) return false;
        if (!hasItemInHand(target)) return false;
        // La chance del 30% se evalúa aquí además del cooldown ya comprobado en tick()
        return random.nextDouble() <= DISARM_CHANCE
                && enderman.hasLineOfSight(target)
                && enderman.getLocation().distance(target.getLocation()) <= DISARM_RANGE
                && isClearPath(enderman.getEyeLocation(), target.getEyeLocation());
    }

    private boolean hasItemInHand(LivingEntity e) {
        if (e.getEquipment() == null) return false;
        return !e.getEquipment().getItemInMainHand().getType().isAir()
                || !e.getEquipment().getItemInOffHand().getType().isAir();
    }

    private void startDisarmWarning() {
        if (target == null || !phase2Active) return;
        isDisarmWarningActive = true;

        final LivingEntity warnTarget = target;

        entity.getWorld().playSound(entity.getLocation(),
                Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.8f);

        // Aplicar Nausea nivel 0 al target para distorsionar su FOV
        // durante toda la duración del warning
        if (warnTarget instanceof org.bukkit.entity.Player player) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.NAUSEA,
                    DISARM_WARNING_SECS * 20 + 10, // duración: warning + margen
                    0,      // amplifier 0 = nivel 1, distorsión mínima
                    false,  // no ambient
                    false,  // sin partículas propias del efecto
                    false   // sin icono en HUD para no revelar el mechanic
            ));
        }

        new BukkitRunnable() {
            int ticks = 0;
            final int warningTicks = DISARM_WARNING_SECS * 20;
            double spiralAngle = 0;

            @Override
            public void run() {
                if (!active || entity == null || entity.isDead()) {
                    cleanup();
                    cancel();
                    return;
                }

                if (warnTarget == null || !warnTarget.isValid() || warnTarget.isDead()) {
                    cleanup();
                    cancel();
                    return;
                }

                if (ticks >= warningTicks) {
                    try {
                        if (hasItemInHand(warnTarget)) {
                            applyDisarm(warnTarget);
                        }
                    } finally {
                        cleanup();
                        cancel();
                    }
                    return;
                }

                // Espiral de 3 brazos — color rosa
                Location targetLoc = warnTarget.getLocation().clone().add(0, 1, 0);

                for (int i = 0; i < 3; i++) {
                    double armAngle     = spiralAngle + i * (Math.PI * 2.0 / 3.0);
                    double spiralRadius = 0.8 + Math.sin(ticks * 0.2) * 0.2;
                    double heightOffset = ((ticks % 20) / 20.0) * 2.0 - 1.0;

                    double x = targetLoc.getX() + spiralRadius * Math.cos(armAngle);
                    double z = targetLoc.getZ() + spiralRadius * Math.sin(armAngle);
                    double y = targetLoc.getY() + heightOffset;

                    // Rosa intenso en el primer brazo, rosa claro en los otros dos
                    Color color = (i == 0)
                            ? Color.fromRGB(255, 20, 147)   // rosa intenso (deep pink)
                            : (i == 1)
                                ? Color.fromRGB(255, 105, 180) // rosa medio (hot pink)
                                : Color.fromRGB(255, 182, 193); // rosa claro (light pink)

                    targetLoc.getWorld().spawnParticle(Particle.DUST,
                            new Location(targetLoc.getWorld(), x, y, z),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(color, 1.3f));
                }

                // Sonido de advertencia cada segundo
                if (ticks % 20 == 0) {
                    warnTarget.getWorld().playSound(warnTarget.getLocation(),
                            Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.5f);
                }

                spiralAngle += 0.25;
                ticks++;
            }

            // Limpiar efectos aplicados durante el warning
            private void cleanup() {
                isDisarmWarningActive = false;
                // Quitar nausea si el target sigue vivo y es jugador
                if (warnTarget instanceof org.bukkit.entity.Player player
                        && player.isValid() && !player.isDead()) {
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.NAUSEA);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void applyDisarm(LivingEntity disarmTarget) {
        if (disarmTarget == null || disarmTarget.getEquipment() == null) return;

        ItemStack item = null;
        if (!disarmTarget.getEquipment().getItemInOffHand().getType().isAir()) {
            item = disarmTarget.getEquipment().getItemInOffHand().clone();
            disarmTarget.getEquipment().setItemInOffHand(new ItemStack(Material.AIR));
        } else if (!disarmTarget.getEquipment().getItemInMainHand().getType().isAir()) {
            item = disarmTarget.getEquipment().getItemInMainHand().clone();
            disarmTarget.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
        }
        if (item == null) return;

        Item dropped = disarmTarget.getWorld().dropItem(
                disarmTarget.getLocation().clone().add(0, 1, 0), item);
        dropped.setPickupDelay(40);
        dropped.setVelocity(entity.getLocation().toVector()
                .subtract(disarmTarget.getLocation().toVector())
                .normalize().multiply(1.1).setY(0.6));

        // Efectos de disarm
        disarmTarget.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                disarmTarget.getLocation().clone().add(0, 1, 0),
                15, 0.3, 0.5, 0.3, 0.1);
        disarmTarget.getWorld().playSound(disarmTarget.getLocation(),
                Sound.ENTITY_ENDERMAN_SCREAM, 1.0f, 1.2f);
        entity.getWorld().spawnParticle(Particle.ENCHANT,
                entity.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0.1);

        // Rastro de partículas moradas en el item mientras vuela
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                // Rastro breve: solo los primeros 15 ticks (~0.75 segundos)
                if (!dropped.isValid() || t >= 15) {
                    cancel();
                    return;
                }
                dropped.getWorld().spawnParticle(Particle.DUST,
                        dropped.getLocation(), 3, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(120, 0, 200), 1.2f));
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ══════════════════════════════════════════════════════════════════
    // MUSTERING
    // ══════════════════════════════════════════════════════════════════

    private void startSummonWarning() {
        entity.getWorld().playSound(entity.getLocation(),
                Sound.ENTITY_ENDERMAN_SCREAM, 1.0f, 0.6f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // Guardia de seguridad
                if (!active || entity == null || entity.isDead()) {
                    summonWarningActive = false;
                    cancel();
                    return;
                }

                if (ticks >= 3) {
                    // try-finally garantiza que cancel() y el flag
                    // se limpien SIEMPRE, incluso si summonShulkers() falla
                    try {
                        summonShulkers();
                    } catch (Exception ex) {
                        plugin.getLogger().severe(
                                "[EndermanOverseer] Error en summonShulkers(): "
                                + ex.getMessage());
                    } finally {
                        summonWarningActive = false;
                        cancel();
                    }
                    return;
                }

                // Partículas de advertencia
                try {
                    Location loc = entity.getLocation().clone().add(0, 2, 0);
                    entity.getWorld().spawnParticle(
                            Particle.REVERSE_PORTAL, loc, 50, 2, 1, 2, 0.2);
                } catch (Exception ex) {
                    // No dejar que un error de partículas rompa el flujo
                    plugin.getLogger().warning(
                            "[EndermanOverseer] Error en partícula de advertencia: "
                            + ex.getMessage());
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void summonShulkers() {
        if (entity == null) return;

        shulkers.removeIf(s -> s == null || s.isDead() || !s.isValid());

        World world = entity.getWorld();
        Location center = entity.getLocation().clone();

        for (int i = 0; i < SUMMON_PER_WAVE; i++) {
            double angle   = random.nextDouble() * 2 * Math.PI;
            double x       = center.getX() + SUMMON_RADIUS * Math.cos(angle);
            double z       = center.getZ() + SUMMON_RADIUS * Math.sin(angle);
            int    y       = world.getHighestBlockYAt((int) x, (int) z) + 1;
            Location spawn = new Location(world, x + 0.5, y, z + 0.5);

            Shulker shulker = (Shulker) world.spawnEntity(spawn, EntityType.SHULKER);
            shulker.setTarget(target);
            shulker.setRemoveWhenFarAway(false);
            shulker.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "shulker_familiar"),
                    PersistentDataType.BYTE, (byte) 1);
            shulkers.add(shulker);

            world.spawnParticle(Particle.WITCH, spawn, 20, 0.5, 0.5, 0.5, 0.1);
            world.playSound(spawn, Sound.ENTITY_SHULKER_OPEN, 1.0f, 1.2f);
        }

        summonWaveCount++;
        
        // ← CRÍTICO: resetear el cooldown para la siguiente oleada
        nextSummonAllowedTime = System.currentTimeMillis() + SUMMON_COOLDOWN_MS;

    }

    // ══════════════════════════════════════════════════════════════════
    // ENDER FIRE RAIN
    // ══════════════════════════════════════════════════════════════════

    private void startRainWarning() {
        if (entity == null) return;

        // Capturar la posición base en el momento de activación
        // El anillo se dibuja a RAIN_SPAWN_HEIGHT bloques sobre los pies del Overseer
        final Location ringCenter = entity.getLocation().clone()
                .add(0, RAIN_SPAWN_HEIGHT, 0);
        final World world = ringCenter.getWorld();

        entity.getWorld().playSound(entity.getLocation(),
                Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.4f);

        new BukkitRunnable() {
            // totalTicks: 0 → RING_WARNING_TICKS: advertencia
            //             RING_WARNING_TICKS → RING_WARNING_TICKS + RING_POST_TICKS: post-caída
            int totalTicks  = 0;
            boolean fired   = false;
            double ringAngle = 0.0; // ángulo de rotación acumulado del anillo

            @Override
            public void run() {
                if (!active || entity == null || entity.isDead()) {
                    rainWarningActive = false;
                    cancel();
                    return;
                }

                int maxTicks = RING_WARNING_TICKS + RING_POST_TICKS; // 100 ticks = 5 segundos

                if (totalTicks >= maxTicks) {
                    rainWarningActive = false;
                    cancel();
                    return;
                }

                // Disparar las enderfire exactamente cuando termina la advertencia
                if (!fired && totalTicks >= RING_WARNING_TICKS) {
                    fired = true;
                    try {
                        startEnderFireRain(ringCenter);
                    } catch (Exception ex) {
                        plugin.getLogger().severe(
                                "[EndermanOverseer] Error en startEnderFireRain(): "
                                + ex.getMessage());
                    }
                }

                // ── Dibujar anillo rotante con ondulación ──────────────────
                int points = 48; // puntos del anillo — más puntos = más denso
                double baseRadius = RAIN_OUTER_RADIUS;

                // La ondulación hace que el radio varíe sinusoidalmente
                // creando un efecto de "pulso" que confirma visualmente la rotación
                double waveAmplitude = 0.6;
                double waveFrequency = 4.0; // cuántas ondas hay en el anillo

                for (int i = 0; i < points; i++) {
                    double t = (double) i / points; // 0.0 → 1.0

                    double pointAngle = ringAngle + t * 2 * Math.PI;
                    double wave = waveAmplitude
                            * Math.sin(waveFrequency * t * 2 * Math.PI + ringAngle * 3);
                    double r = baseRadius + wave;

                    double x = ringCenter.getX() + r * Math.cos(pointAngle);
                    double z = ringCenter.getZ() + r * Math.sin(pointAngle);
                    // Pequeña variación vertical para que no sea plano
                    double y = ringCenter.getY()
                            + Math.sin(waveFrequency * t * 2 * Math.PI + ringAngle * 2) * 0.3;

                    Location particleLoc = new Location(world, x, y, z);

                    // Color: antes de la caída morado, después azul
                    Color color = fired
                            ? Color.fromRGB(40, 80, 255)   // azul post-caída
                            : Color.fromRGB(120, 0, 200);  // morado advertencia

                    world.spawnParticle(Particle.DUST, particleLoc,
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(color, 1.3f));

                    // Cada 6 puntos, partícula PORTAL o SOUL_FIRE_FLAME para contraste
                    if (i % 6 == 0) {
                        world.spawnParticle(
                                fired ? Particle.SOUL_FIRE_FLAME : Particle.PORTAL,
                                particleLoc, 1, 0.05, 0.05, 0.05, 0.01);
                    }
                }

                // Avanzar ángulo: más rápido en advertencia, más lento post-caída
                ringAngle += fired ? 0.08 : 0.15;
                if (ringAngle > Math.PI * 2) ringAngle -= Math.PI * 2;

                totalTicks++;
            }
        }.runTaskTimer(plugin, 0L, 1L); // cada tick para rotación fluida
    }

    private void startEnderFireRain(Location spawnCenter) {
        if (entity == null) return;
        World world = spawnCenter.getWorld();

        for (int i = 0; i < RAIN_FIRE_COUNT; i++) {
            double angle  = (2 * Math.PI / RAIN_FIRE_COUNT) * i
                    + random.nextDouble() * 0.3;
            double radius = RAIN_INNER_RADIUS
                    + random.nextDouble() * (RAIN_OUTER_RADIUS - RAIN_INNER_RADIUS);

            double x = spawnCenter.getX() + radius * Math.cos(angle);
            double z = spawnCenter.getZ() + radius * Math.sin(angle);

            spawnEnderFireball(new Location(world, x, spawnCenter.getY(), z));
        }

        // Calcular tiempo de caída aproximado desde RAIN_SPAWN_HEIGHT
        // Los proyectiles caen con velocidad Y = -0.5, acelerando por gravedad
        // A 12 bloques de altura, tardan aproximadamente 40 ticks en llegar al suelo
        long impactDelayTicks = 40L;
        startEnderFireAura(impactDelayTicks);
    }

    private void startEnderFireAura(long initialDelayTicks) {
        new BukkitRunnable() {
            int pulse = 0;

            @Override
            public void run() {
                if (!active || entity == null || entity.isDead()) {
                    cancel();
                    return;
                }

                if (pulse >= ENDERFIRE_AURA_DAMAGE.length) {
                    cancel();
                    return;
                }

                Location center = entity.getLocation().clone().add(0, 0.5, 0);
                World world = center.getWorld();
                double damage = ENDERFIRE_AURA_DAMAGE[pulse];

                // Partículas del aura centradas en el Overseer
                // Radio decrece con cada pulso para simular disipación
                double particleRadius = ENDERFIRE_AURA_RADIUS - pulse * 1.5;
                int particlePoints = 32;
                double angleStep = 2 * Math.PI / particlePoints;

                for (int i = 0; i < particlePoints; i++) {
                    double a = i * angleStep;
                    double px = center.getX() + particleRadius * Math.cos(a);
                    double pz = center.getZ() + particleRadius * Math.sin(a);
                    int groundY = world.getHighestBlockYAt((int) px, (int) pz);

                    Location ringLoc = new Location(world, px, groundY + 0.1, pz);

                    // Color: morado intenso en primer pulso, se desvanece
                    int rb = (int) (160 - pulse * 40);
                    world.spawnParticle(Particle.DUST, ringLoc,
                            2, 0.1, 0.2, 0.1, 0,
                            new Particle.DustOptions(
                                    Color.fromRGB(rb, 0, 255), 1.5f));
                    world.spawnParticle(Particle.PORTAL,
                            ringLoc, 1, 0.1, 0.3, 0.1, 0.05);
                }

                // Partículas centrales en el propio Overseer
                world.spawnParticle(Particle.WITCH,
                        center, 20 - pulse * 5,
                        ENDERFIRE_AURA_RADIUS * 0.3, 1.0,
                        ENDERFIRE_AURA_RADIUS * 0.3, 0.05);

                // Sonido del pulso
                world.playSound(center,
                        Sound.ENTITY_ENDER_EYE_DEATH,
                        1.0f, 0.6f + pulse * 0.2f);

                // Aplicar daño a jugadores en el radio del aura
                for (Entity e : world.getNearbyEntities(
                        center, ENDERFIRE_AURA_RADIUS, 4.0, ENDERFIRE_AURA_RADIUS)) {
                    if (!(e instanceof LivingEntity living)) continue;
                    if (e.equals(entity) || shulkers.contains(e)) continue;
                    living.damage(damage, entity);
                }

                pulse++;
            }
        }.runTaskTimer(plugin, initialDelayTicks, ENDERFIRE_AURA_PULSE_INTERVAL);
    }
    private void spawnEnderFireball(Location loc) {
        Snowball snowball = loc.getWorld().spawn(loc, Snowball.class);
        snowball.setShooter(entity);
        snowball.setVisualFire(true);
        snowball.setGlowing(true);
        snowball.setVelocity(new Vector(0, -0.5, 0));
        snowball.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "enderfire"),
                PersistentDataType.BYTE, (byte) 1);

        new BukkitRunnable() {
            @Override public void run() {
                if (!snowball.isValid() || snowball.isDead()) { cancel(); return; }
                snowball.getWorld().spawnParticle(Particle.PORTAL,
                        snowball.getLocation(), 3, 0.1, 0.1, 0.1, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @Override
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        if (!snowball.getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "enderfire"),
                        PersistentDataType.BYTE)) return;

        Location impact = snowball.getLocation().clone();
        World world = impact.getWorld();

        // Solo efectos visuales y sonoros — el daño lo maneja startEnderFireAura()
        world.playSound(impact, Sound.ENTITY_ENDER_DRAGON_SHOOT, 0.6f, 1.2f);
        world.spawnParticle(Particle.PORTAL, impact, 20, 0.4, 0.3, 0.4, 0.2);
        world.spawnParticle(Particle.WITCH,  impact, 10, 0.3, 0.2, 0.3, 0.05);

        snowball.remove();
    }

    // ══════════════════════════════════════════════════════════════════
    // EVENTOS
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void onDamage(EntityDamageByEntityEvent event) {
        // Reservado para futuras reacciones
    }

    @Override
    public void onDeath(EntityDeathEvent event) {
        // Los shulkers sobreviven intencionalmente, no se eliminan aquí

        event.getDrops().clear();
        event.getDrops().add(new ItemStack(Material.ELYTRA, 1));
        event.getDrops().add(new ItemStack(Material.CHORUS_FRUIT, 4));

        despawn();
    }

    // ══════════════════════════════════════════════════════════════════
    // PARTÍCULAS CONSTANTES
    // ══════════════════════════════════════════════════════════════════

    private void startSpiralParticles() {
        new BukkitRunnable() {
            private double angle = 0;
            private int deadCheckCounter = 0;

            @Override public void run() {
                if (!active) { cancel(); return; }

                if (entity == null || !entity.isValid()) {
                    if (++deadCheckCounter >= 5) cancel();
                    return;
                }

                // isDead() con gracia igual que en startTicking
                if (entity.isDead()) {
                    if (++deadCheckCounter >= 3) cancel();
                    return;
                }

                deadCheckCounter = 0;

                Location base = entity.getLocation().clone();
                World world   = base.getWorld();

                int    arms         = 3;
                int    stepsPerArm  = 16;
                double totalHeight  = 5.5;

                for (int arm = 0; arm < arms; arm++) {
                    double armOffset = (2 * Math.PI / arms) * arm;
                    for (int step = 0; step < stepsPerArm; step++) {
                        double t            = (double) step / stepsPerArm;
                        double currentAngle = angle + armOffset + t * Math.PI * 4;
                        double radius       = 0.8 + t * 0.5;
                        double height       = t * totalHeight;

                        double x = base.getX() + radius * Math.cos(currentAngle);
                        double z = base.getZ() + radius * Math.sin(currentAngle);
                        double y = base.getY() + height;

                        int r = (int) (80  + 40  * t);
                        int g = 0;
                        int b = (int) (180 + 75  * t);

                        world.spawnParticle(Particle.DUST,
                                new Location(world, x, y, z),
                                1, 0, 0, 0, 0,
                                new Particle.DustOptions(
                                        Color.fromRGB(r, g, b), 1.1f));
                    }
                }

                world.spawnParticle(Particle.PORTAL,
                        base.clone().add(0, 0.5, 0),
                        4, 0.4, 0.1, 0.4, 0.2);

                angle += 0.2;
                if (angle > Math.PI * 2) angle -= Math.PI * 2;
            }
        }.runTaskTimer(plugin, 20L, 3L);
    }

    private void activatePhase2() {
        phase2Active = true;

        // Resistencia 2 permanente mientras viva el boss
        // Amplifier 1 = nivel 2 (es base-0), duration Integer.MAX_VALUE = permanente
        entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.RESISTANCE,
                Integer.MAX_VALUE,
                1,       // amplifier: 0 = nivel 1, 1 = nivel 2
                false,   // ambient: false para que las partículas sean visibles
                true,    // particles: visibles para que los jugadores vean el cambio
                true     // icon: mostrar ícono en HUD
        ));

        // Resetear cooldown de disarm para que esté disponible inmediatamente en fase 2
        disarmCooldown = 0;

        // Efectos visuales y sonoros de transición
        Location loc = entity.getLocation().clone();
        World world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_ENDERMAN_SCREAM, 2.0f, 0.4f);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);

        // Explosión de partículas moradas intensas centrada en el boss
        world.spawnParticle(Particle.REVERSE_PORTAL,
                loc.clone().add(0, 3, 0), 200, 3, 3, 3, 0.3);
        world.spawnParticle(Particle.PORTAL,
                loc.clone().add(0, 3, 0), 150, 2, 2, 2, 0.5);

        // Anuncio a todos los jugadores cercanos
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= 80 * 80) {
                p.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD
                        + "The Enderman Overseer grows stronger!");
            }
        }

        // Animación de espiral de expansión durante 2 segundos para marcar la transición
        new BukkitRunnable() {
            double angle = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 40 || !active || entity == null || entity.isDead()) {
                    cancel();
                    return;
                }

                // Radio que crece y luego se contrae para dar sensación de pulso
                double progress = ticks / 40.0; // 0.0 → 1.0
                double radius = 5.0 * Math.sin(progress * Math.PI); // 0 → 5 → 0

                for (int i = 0; i < 6; i++) {
                    double a = angle + i * (Math.PI / 3);
                    double x = loc.getX() + radius * Math.cos(a);
                    double z = loc.getZ() + radius * Math.sin(a);
                    double y = loc.getY() + 2 + Math.sin(ticks * 0.3 + i) * 1.5;

                    world.spawnParticle(Particle.DUST,
                            new Location(world, x, y, z),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(
                                    Color.fromRGB(160, 0, 255), 1.8f));
                }

                angle += 0.4;
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        plugin.getLogger().info("[EndermanOverseer] Fase 2 activada. HP: "
                + String.format("%.1f", entity.getHealth()) + "/" + MAX_HEALTH);
    }
    // ══════════════════════════════════════════════════════════════════
    // UTILIDADES
    // ══════════════════════════════════════════════════════════════════

    private void setAIState(boolean enable) {
        if (aiEnabled == enable) return; // ya está en el estado correcto
        aiEnabled = enable;
        entity.setAI(enable);

        if (enable) {
            // Al activar la IA, asegurarse de que el target esté asignado
            // para que el pathfinding empiece inmediatamente
            if (entity instanceof Mob mob) mob.setTarget(target);
        }
    }
    private boolean isClearPath(Location start, Location end) {
        Vector dir      = end.toVector().subtract(start.toVector());
        double maxDist  = dir.length();
        dir.normalize();
        for (double d = 0.5; d < maxDist; d += 0.5) {
            if (start.clone().add(dir.clone().multiply(d))
                    .getBlock().getType().isSolid()) return false;
        }
        return true;
    }

    private void lookAtTarget() {
        if (target == null || entity == null) return;

        Location bossLoc   = entity.getLocation();
        Location targetLoc = target.getLocation();

        double dx = targetLoc.getX() - bossLoc.getX();
        double dy = targetLoc.getY() + 1.0 - (bossLoc.getY() + entity.getHeight() * 0.9);
        double dz = targetLoc.getZ() - bossLoc.getZ();

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Yaw: rotación horizontal hacia el target
        float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        // Pitch: inclinación vertical hacia el target (positivo = mirar abajo)
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontalDist));

        entity.setRotation(yaw, pitch);
    }
}