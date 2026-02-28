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

    private static final double MAX_HEALTH    = 1000.0;
    private static final double SCALE         = 2.5;
    private static final double ATTACK_DAMAGE = 12.0;

    private static final double WALK_FOLLOW_RANGE = 6.0;

    private static final double PULL_CHANCE        = 0.4;
    private static final int    PULL_COOLDOWN_SECS = 3;

    private static final double BLOCK_THROW_MAX_RANGE   = 24.0;
    private static final double BLOCK_THROW_CHANCE      = 0.4;
    private static final double BLOCK_THROW_DAMAGE      = 25.0;
    private static final double BLOCK_THROW_RADIUS      = 3.0;
    private static final int    BLOCK_THROW_CHARGE_SECS = 2;

    private static final double   ENDERFIRE_AURA_RADIUS         = 8.0;
    private static final double[] ENDERFIRE_AURA_DAMAGE         = { 15.0, 9.0, 5.0 };
    private static final long     ENDERFIRE_AURA_PULSE_INTERVAL = 20L;

    // Feature C: duración del anillo de partículas en el suelo (ticks)
    private static final int ENDERFIRE_GROUND_RING_TICKS = 60;

    private static final int    DISARM_WARNING_SECS = 5;

    private static final double SUMMON_CHANCE      = 0.4;
    private static final int    SUMMON_PER_WAVE    = 2;
    private static final int    SUMMON_MAX_WAVES   = 3;
    private static final long   SUMMON_COOLDOWN_MS = 40_000L;
    private static final double SUMMON_RADIUS      = 6.0;

    private static final double DISARM_CHANCE = 0.3;
    private static final double DISARM_RANGE  = 8.0;

    private static final long   RAIN_COOLDOWN_MS   = 8_000L;
    private static final double RAIN_CHANCE        = 0.3;
    private static final int    RAIN_FIRE_COUNT    = 8;
    private static final double RAIN_SPAWN_HEIGHT  = 12.0;
    private static final double RAIN_OUTER_RADIUS  = 12.0;
    private static final double RAIN_INNER_RADIUS  = 2.0;
    private static final int    RING_WARNING_TICKS = 3 * 20;
    private static final int    RING_POST_TICKS    = 2 * 20;

    private static final double PHASE2_HEALTH_PERCENT       = 0.30;
    private static final int    DISARM_COOLDOWN_PHASE2_SECS = 60;
    private boolean phase2Active = false;

    private boolean aiEnabled = false;
    private final Random random = new Random();
    private LivingEntity target;

    private int pullCooldown   = 0;
    private int disarmCooldown = 0;
    private int attackCooldown = 0;

    private long    lastRainTime      = 0L;
    private boolean rainWarningActive = false;

    private long    nextSummonAllowedTime = 0L;
    private boolean summonWarningActive   = false;
    private int     summonWaveCount       = 0;

    private boolean isChargingBlockThrow  = false;
    private boolean isDisarmWarningActive = false;

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
        aiEnabled = false;

        overseer.getAttribute(Attribute.SCALE).setBaseValue(SCALE);
        overseer.getAttribute(Attribute.MAX_HEALTH).setBaseValue(MAX_HEALTH);
        overseer.setHealth(MAX_HEALTH);
        overseer.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(ATTACK_DAMAGE);
        overseer.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.25);
        overseer.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(32.0);

        overseer.getEquipment().setHelmet(resolveHelmet(new ItemStack(Material.CARVED_PUMPKIN)));
        overseer.getEquipment().setHelmetDropChance(0);
        overseer.setCarriedBlock(null);

        // Feature B: nombre invisible — controla el kill message sin mostrar nametag
        overseer.setCustomName(ChatColor.DARK_PURPLE + "Enderman Overseer");
        overseer.setCustomNameVisible(false);

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
                if (entity == null || !entity.isValid()) {
                    if (++deadCheckCounter >= 5) { cancel(); return; }
                    return;
                }
                if (entity.isDead()) {
                    if (++deadCheckCounter >= 3) { cancel(); return; }
                    return;
                }
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

        if (target != null) {
            lookAtTarget();
            // Bug 2 + consistencia con bug 1: actualizar lastTargetTime mientras haya target
            lastTargetTime = System.currentTimeMillis();
        }

        if (target == null) {
            setAIState(false);
            return;
        }

        if (pullCooldown   > 0) pullCooldown--;
        if (disarmCooldown > 0) disarmCooldown--;
        if (attackCooldown > 0) attackCooldown--;

        double distance = entity.getLocation().distance(target.getLocation());

        if (distance <= WALK_FOLLOW_RANGE) {
            setAIState(true);
        } else {
            setAIState(false);

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

            if (phase2Active && !isDisarmWarningActive
                    && disarmCooldown == 0
                    && distance <= DISARM_RANGE
                    && shouldDisarm()) {
                startDisarmWarning();
                disarmCooldown = DISARM_COOLDOWN_PHASE2_SECS;
            }

            long now = System.currentTimeMillis();

            if (!summonWarningActive
                    && summonWaveCount < SUMMON_MAX_WAVES
                    && now >= nextSummonAllowedTime
                    && random.nextDouble() < SUMMON_CHANCE) {
                summonWarningActive = true;
                nextSummonAllowedTime = Long.MAX_VALUE;
                startSummonWarning();
            }

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
    // TARGET — Bug 2 fix: Bukkit.getOnlinePlayers() en vez de
    //          entity.getWorld().getPlayers() para no perder al jugador
    //          durante el respawn (carga transitoria de mundo)
    // ══════════════════════════════════════════════════════════════════

    private void updateTarget() {
        double closest = Double.MAX_VALUE;
        Player nearest = null;

        for (Player p : Bukkit.getOnlinePlayers()) {
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
        return target != null && random.nextDouble() <= PULL_CHANCE;
    }

    private void applyPull() {
        if (target == null) return;

        Location bossLoc   = entity.getLocation();
        Location targetLoc = target.getLocation();

        if (target instanceof LivingEntity living &&
                living.hasPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION)) {

            Location landingLoc = bossLoc.clone();
            landingLoc.add(
                    Math.sin(Math.toRadians(bossLoc.getYaw())) * -1.5, 0,
                    Math.cos(Math.toRadians(bossLoc.getYaw())) *  1.5);
            landingLoc.setY(bossLoc.getWorld()
                    .getHighestBlockYAt(landingLoc.getBlockX(), landingLoc.getBlockZ()));
            living.removePotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION);
            target.teleport(landingLoc);
            target.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                    landingLoc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.15);
            target.getWorld().playSound(landingLoc,
                    Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.5f);
            return;
        }

        Vector horizontal = new Vector(
                bossLoc.getX() - targetLoc.getX(), 0,
                bossLoc.getZ() - targetLoc.getZ()
        ).normalize().multiply(1.4);

        double heightDiff = bossLoc.getY() - targetLoc.getY();
        double vy = heightDiff > 1.0 ? Math.min(0.5 + heightDiff * 0.05, 0.6) : 0.35;
        horizontal.setY(vy);
        target.setVelocity(horizontal);

        World world = target.getWorld();
        world.spawnParticle(Particle.REVERSE_PORTAL,
                targetLoc.clone().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.1);
        world.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.7f);
        world.spawnParticle(Particle.ELECTRIC_SPARK,
                entity.getEyeLocation(), 10, 0.3, 0.3, 0.3, 0.05);
    }

    // ══════════════════════════════════════════════════════════════════
    // BLOCK THROW
    // ══════════════════════════════════════════════════════════════════

    private void startBlockThrowCharge() {
        if (!(entity instanceof Enderman enderman)) return;
        isChargingBlockThrow = true;
        final LivingEntity throwTarget = target;

        enderman.setCarriedBlock(Bukkit.createBlockData(Material.GRASS_BLOCK));
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_GRASS_BREAK,    1.0f, 0.5f);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 0.7f, 0.4f);

        new BukkitRunnable() {
            int ticks = 0;
            final int chargeTicks = BLOCK_THROW_CHARGE_SECS * 20;

            @Override
            public void run() {
                if (!active || entity == null || entity.isDead()) {
                    isChargingBlockThrow = false; cancel(); return;
                }
                if (ticks >= chargeTicks) {
                    launchBlock(throwTarget); cancel(); return;
                }
                Location handLoc = entity.getLocation().clone().add(0, SCALE * 1.5, 0);
                double chargeAngle = ticks * 0.4;
                for (int i = 0; i < 3; i++) {
                    double a = chargeAngle + i * (Math.PI * 2 / 3);
                    double x = handLoc.getX() + 0.8 * Math.cos(a);
                    double z = handLoc.getZ() + 0.8 * Math.sin(a);
                    handLoc.getWorld().spawnParticle(Particle.BLOCK,
                            new Location(handLoc.getWorld(), x, handLoc.getY(), z),
                            2, 0.1, 0.1, 0.1, 0.05,
                            Bukkit.createBlockData(Material.GRASS_BLOCK));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void launchBlock(LivingEntity throwTarget) {
        if (!(entity instanceof Enderman enderman)) {
            isChargingBlockThrow = false; return;
        }
        enderman.setCarriedBlock(null);
        isChargingBlockThrow = false;
        if (throwTarget == null || throwTarget.isDead() || !throwTarget.isValid()) return;

        Location launchLoc = entity.getLocation().clone().add(0, SCALE * 1.2, 0);
        World world = launchLoc.getWorld();

        world.playSound(entity.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.6f);
        world.playSound(entity.getLocation(), Sound.BLOCK_GRASS_BREAK,        1.0f, 0.8f);

        FallingBlock fallingBlock = world.spawnFallingBlock(
                launchLoc, Bukkit.createBlockData(Material.GRASS_BLOCK));
        fallingBlock.setDropItem(false);
        fallingBlock.setHurtEntities(false);
        fallingBlock.setGlowing(true);
        fallingBlock.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "overseer_block_throw"),
                PersistentDataType.BYTE, (byte) 1);

        Location targetLoc = throwTarget.getLocation().clone().add(0, 1, 0);
        Vector toTarget = targetLoc.toVector().subtract(launchLoc.toVector());
        double horizontalDist = Math.sqrt(
                toTarget.getX() * toTarget.getX() + toTarget.getZ() * toTarget.getZ());
        double flightTime = Math.max(0.5, horizontalDist / 12.0);
        double gravity = 0.04;
        double vx = toTarget.getX() / (flightTime * 20);
        double vz = toTarget.getZ() / (flightTime * 20);
        double vy = (toTarget.getY() / (flightTime * 20))
                + (gravity * flightTime * 20) / 2.0 + 0.3;
        fallingBlock.setVelocity(new Vector(vx, vy, vz));

        new BukkitRunnable() {
            int     ticks    = 0;
            boolean impacted = false;
            final int maxTicks = (int) (flightTime * 20) + 60;

            @Override
            public void run() {
                if (impacted || ticks > maxTicks) { cancel(); return; }
                if (!fallingBlock.isValid()) { cancel(); return; }

                world.spawnParticle(Particle.BLOCK, fallingBlock.getLocation(),
                        3, 0.2, 0.2, 0.2, 0.05,
                        Bukkit.createBlockData(Material.GRASS_BLOCK));

                for (Entity e : world.getNearbyEntities(
                        fallingBlock.getLocation(), 1.5, 1.5, 1.5)) {
                    if (!(e instanceof LivingEntity)) continue;
                    if (e.equals(entity)) continue;
                    impacted = true;
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
        world.playSound(impactLoc, Sound.BLOCK_GRASS_BREAK,      1.5f, 0.4f);
        world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);

        new BukkitRunnable() {
            double currentRadius = 0.2;
            int    waveTicks     = 0;

            @Override
            public void run() {
                if (waveTicks >= 12 || currentRadius > BLOCK_THROW_RADIUS + 0.5) {
                    cancel(); return;
                }
                int points = Math.max(8, (int) (currentRadius * 10));
                double angleStep = 2 * Math.PI / points;
                for (int i = 0; i < points; i++) {
                    double angle = i * angleStep;
                    double x = impactLoc.getX() + currentRadius * Math.cos(angle);
                    double z = impactLoc.getZ() + currentRadius * Math.sin(angle);
                    int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
                    double t = currentRadius / BLOCK_THROW_RADIUS;
                    world.spawnParticle(Particle.DUST, new Location(world, x, y, z),
                            2, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(
                                    (int)(100*(1-t)+150*t),
                                    (int)(150*(1-t)+150*t),
                                    (int)(50 *(1-t)+150*t)), 1.5f));
                    world.spawnParticle(Particle.BLOCK, new Location(world, x, y, z),
                            1, 0.1, 0.1, 0.1, 0.05,
                            Bukkit.createBlockData(Material.GRASS_BLOCK));
                }
                if (waveTicks == 0) {
                    world.spawnParticle(Particle.BLOCK, impactLoc.clone().add(0, 0.5, 0),
                            40, 0.5, 0.5, 0.5, 0.15,
                            Bukkit.createBlockData(Material.GRASS_BLOCK));
                }
                currentRadius += BLOCK_THROW_RADIUS / 10.0;
                waveTicks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        for (Entity e : world.getNearbyEntities(
                impactLoc, BLOCK_THROW_RADIUS, BLOCK_THROW_RADIUS, BLOCK_THROW_RADIUS)) {
            if (!(e instanceof LivingEntity living)) continue;
            if (e.equals(entity)) continue;
            double dist         = e.getLocation().distance(impactLoc);
            double damageFactor = 1.0 - (dist / BLOCK_THROW_RADIUS);
            living.damage(BLOCK_THROW_DAMAGE * Math.max(0.3, damageFactor), entity);
            Vector kbDir = living.getLocation().toVector().subtract(impactLoc.toVector());
            if (kbDir.lengthSquared() < 0.0001)
                kbDir = new Vector((Math.random()-0.5)*2, 0, (Math.random()-0.5)*2);
            living.setVelocity(kbDir.normalize().multiply(1.2).setY(0.5));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // DISARM
    // ══════════════════════════════════════════════════════════════════

    private boolean shouldDisarm() {
        if (target == null || !(entity instanceof Enderman enderman)) return false;
        if (!hasItemInHand(target)) return false;
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

        if (warnTarget instanceof org.bukkit.entity.Player player) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.NAUSEA,
                    DISARM_WARNING_SECS * 20 + 10, 0, false, false, false));
        }

        new BukkitRunnable() {
            int    ticks       = 0;
            final int warningTicks = DISARM_WARNING_SECS * 20;
            double spiralAngle = 0;

            @Override
            public void run() {
                if (!active || entity == null || entity.isDead()) {
                    cleanup(); cancel(); return;
                }
                if (warnTarget == null || !warnTarget.isValid() || warnTarget.isDead()) {
                    cleanup(); cancel(); return;
                }
                if (ticks >= warningTicks) {
                    try { if (hasItemInHand(warnTarget)) applyDisarm(warnTarget); }
                    finally { cleanup(); cancel(); }
                    return;
                }
                Location targetLoc = warnTarget.getLocation().clone().add(0, 1, 0);
                for (int i = 0; i < 3; i++) {
                    double armAngle     = spiralAngle + i * (Math.PI * 2.0 / 3.0);
                    double spiralRadius = 0.8 + Math.sin(ticks * 0.2) * 0.2;
                    double heightOffset = ((ticks % 20) / 20.0) * 2.0 - 1.0;
                    double x = targetLoc.getX() + spiralRadius * Math.cos(armAngle);
                    double z = targetLoc.getZ() + spiralRadius * Math.sin(armAngle);
                    double y = targetLoc.getY() + heightOffset;
                    Color color = (i == 0) ? Color.fromRGB(255, 20, 147)
                            : (i == 1)    ? Color.fromRGB(255, 105, 180)
                                          : Color.fromRGB(255, 182, 193);
                    targetLoc.getWorld().spawnParticle(Particle.DUST,
                            new Location(targetLoc.getWorld(), x, y, z),
                            1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.3f));
                }
                if (ticks % 20 == 0) {
                    warnTarget.getWorld().playSound(warnTarget.getLocation(),
                            Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.5f);
                }
                spiralAngle += 0.25;
                ticks++;
            }

            private void cleanup() {
                isDisarmWarningActive = false;
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

        disarmTarget.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                disarmTarget.getLocation().clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
        disarmTarget.getWorld().playSound(disarmTarget.getLocation(),
                Sound.ENTITY_ENDERMAN_SCREAM, 1.0f, 1.2f);
        entity.getWorld().spawnParticle(Particle.ENCHANT,
                entity.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0.1);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!dropped.isValid() || t >= 15) { cancel(); return; }
                dropped.getWorld().spawnParticle(Particle.DUST, dropped.getLocation(),
                        3, 0.05, 0.05, 0.05, 0,
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
            @Override public void run() {
                if (!active || entity == null || entity.isDead()) {
                    summonWarningActive = false; cancel(); return;
                }
                if (ticks >= 3) {
                    try { summonShulkers(); }
                    catch (Exception ex) {
                        plugin.getLogger().severe("[EndermanOverseer] Error en summonShulkers(): "
                                + ex.getMessage());
                    } finally { summonWarningActive = false; cancel(); }
                    return;
                }
                try {
                    entity.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                            entity.getLocation().clone().add(0, 2, 0), 50, 2, 1, 2, 0.2);
                } catch (Exception ex) {
                    plugin.getLogger().warning("[EndermanOverseer] Error en partícula: "
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
        nextSummonAllowedTime = System.currentTimeMillis() + SUMMON_COOLDOWN_MS;
    }

    // ══════════════════════════════════════════════════════════════════
    // ENDER FIRE RAIN
    // ══════════════════════════════════════════════════════════════════

    private void startRainWarning() {
        if (entity == null) return;
        final Location ringCenter = entity.getLocation().clone().add(0, RAIN_SPAWN_HEIGHT, 0);
        final World world = ringCenter.getWorld();

        entity.getWorld().playSound(entity.getLocation(),
                Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.4f);

        new BukkitRunnable() {
            int     totalTicks = 0;
            boolean fired      = false;
            double  ringAngle  = 0.0;

            @Override
            public void run() {
                if (!active || entity == null || entity.isDead()) {
                    rainWarningActive = false; cancel(); return;
                }
                int maxTicks = RING_WARNING_TICKS + RING_POST_TICKS;
                if (totalTicks >= maxTicks) {
                    rainWarningActive = false; cancel(); return;
                }

                if (!fired && totalTicks >= RING_WARNING_TICKS) {
                    fired = true;
                    try { startEnderFireRain(ringCenter); }
                    catch (Exception ex) {
                        plugin.getLogger().severe("[EndermanOverseer] Error en startEnderFireRain(): "
                                + ex.getMessage());
                    }
                }

                int    points        = 48;
                double baseRadius    = RAIN_OUTER_RADIUS;
                double waveAmplitude = 0.6;
                double waveFrequency = 4.0;

                for (int i = 0; i < points; i++) {
                    double t          = (double) i / points;
                    double pointAngle = ringAngle + t * 2 * Math.PI;
                    double wave       = waveAmplitude
                            * Math.sin(waveFrequency * t * 2 * Math.PI + ringAngle * 3);
                    double r = baseRadius + wave;
                    double x = ringCenter.getX() + r * Math.cos(pointAngle);
                    double z = ringCenter.getZ() + r * Math.sin(pointAngle);
                    double y = ringCenter.getY()
                            + Math.sin(waveFrequency * t * 2 * Math.PI + ringAngle * 2) * 0.3;
                    Color color = fired ? Color.fromRGB(40, 80, 255) : Color.fromRGB(120, 0, 200);
                    world.spawnParticle(Particle.DUST,
                            new Location(world, x, y, z), 1, 0, 0, 0, 0,
                            new Particle.DustOptions(color, 1.3f));
                    if (i % 6 == 0) {
                        world.spawnParticle(fired ? Particle.SOUL_FIRE_FLAME : Particle.PORTAL,
                                new Location(world, x, y, z), 1, 0.05, 0.05, 0.05, 0.01);
                    }
                }

                ringAngle += fired ? 0.08 : 0.15;
                if (ringAngle > Math.PI * 2) ringAngle -= Math.PI * 2;
                totalTicks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startEnderFireRain(Location spawnCenter) {
        if (entity == null) return;
        World world = spawnCenter.getWorld();

        // Capturar el centro al nivel del suelo para el anillo de impacto (feature C)
        final Location groundCenter = entity.getLocation().clone();

        for (int i = 0; i < RAIN_FIRE_COUNT; i++) {
            double angle  = (2 * Math.PI / RAIN_FIRE_COUNT) * i + random.nextDouble() * 0.3;
            double radius = RAIN_INNER_RADIUS
                    + random.nextDouble() * (RAIN_OUTER_RADIUS - RAIN_INNER_RADIUS);
            double x = spawnCenter.getX() + radius * Math.cos(angle);
            double z = spawnCenter.getZ() + radius * Math.sin(angle);
            spawnEnderFireball(new Location(world, x, spawnCenter.getY(), z));
        }

        long impactDelayTicks = 40L;
        startEnderFireAura(impactDelayTicks, groundCenter);
    }

    private void startEnderFireAura(long initialDelayTicks, Location groundCenter) {
        new BukkitRunnable() {
            int pulse = 0;

            @Override
            public void run() {
                if (!active || entity == null || entity.isDead()) { cancel(); return; }
                if (pulse >= ENDERFIRE_AURA_DAMAGE.length) { cancel(); return; }

                Location center = entity.getLocation().clone().add(0, 0.5, 0);
                World world = center.getWorld();
                double damage = ENDERFIRE_AURA_DAMAGE[pulse];

                // Feature C: anillo en el suelo solo en el primer pulso
                if (pulse == 0) {
                    spawnEnderFireGroundRing(world, groundCenter);
                }

                double particleRadius = ENDERFIRE_AURA_RADIUS - pulse * 1.5;
                int    particlePoints = 32;
                double angleStep      = 2 * Math.PI / particlePoints;

                for (int i = 0; i < particlePoints; i++) {
                    double a = i * angleStep;
                    double px = center.getX() + particleRadius * Math.cos(a);
                    double pz = center.getZ() + particleRadius * Math.sin(a);
                    int groundY = world.getHighestBlockYAt((int) px, (int) pz);
                    int rb = (int) (160 - pulse * 40);
                    world.spawnParticle(Particle.DUST,
                            new Location(world, px, groundY + 0.1, pz),
                            2, 0.1, 0.2, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(rb, 0, 255), 1.5f));
                    world.spawnParticle(Particle.PORTAL,
                            new Location(world, px, groundY + 0.1, pz),
                            1, 0.1, 0.3, 0.1, 0.05);
                }

                world.spawnParticle(Particle.WITCH, center,
                        20 - pulse * 5,
                        ENDERFIRE_AURA_RADIUS * 0.3, 1.0, ENDERFIRE_AURA_RADIUS * 0.3, 0.05);
                world.playSound(center, Sound.ENTITY_ENDER_EYE_DEATH,
                        1.0f, 0.6f + pulse * 0.2f);

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

    /**
     * Feature C: Anillo de partículas purpúreas en el suelo que cubre el área
     * afectada por el Enderfire durante ENDERFIRE_GROUND_RING_TICKS ticks.
     * Rota lentamente y se desvanece al final.
     */
    private void spawnEnderFireGroundRing(World world, Location center) {
        new BukkitRunnable() {
            int    ticks = 0;
            double angle = 0;

            @Override
            public void run() {
                if (ticks >= ENDERFIRE_GROUND_RING_TICKS) { cancel(); return; }

                double progress = (double) ticks / ENDERFIRE_GROUND_RING_TICKS;

                // Dos anillos concéntricos: exterior (radio completo) e interior (radio medio)
                drawGroundRingAtRadius(world, center, ENDERFIRE_AURA_RADIUS, angle, progress);
                drawGroundRingAtRadius(world, center, ENDERFIRE_AURA_RADIUS * 0.5,
                        angle + Math.PI / 16, progress);

                // Llamaradas dispersas dentro del área cada 5 ticks
                if (ticks % 5 == 0) {
                    double fx = center.getX()
                            + (random.nextDouble() * ENDERFIRE_AURA_RADIUS * 1.8
                               - ENDERFIRE_AURA_RADIUS * 0.9);
                    double fz = center.getZ()
                            + (random.nextDouble() * ENDERFIRE_AURA_RADIUS * 1.8
                               - ENDERFIRE_AURA_RADIUS * 0.9);
                    int fy = world.getHighestBlockYAt((int) fx, (int) fz);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                            new Location(world, fx, fy + 0.1, fz),
                            1, 0.1, 0.3, 0.1, 0.02);
                }

                angle += 0.1;
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawGroundRingAtRadius(World world, Location center,
                                        double radius, double angleOffset,
                                        double fadeProgress) {
        int    points    = 40;
        double angleStep = 2 * Math.PI / points;

        for (int i = 0; i < points; i++) {
            double a = angleOffset + i * angleStep;
            double x = center.getX() + radius * Math.cos(a);
            double z = center.getZ() + radius * Math.sin(a);
            int groundY = world.getHighestBlockYAt((int) x, (int) z);

            // Morado brillante → azul oscuro al desvanecerse
            int red  = (int) (140 * (1 - fadeProgress) + 20 * fadeProgress);
            int blue = (int) (255 * (1 - fadeProgress) + 80 * fadeProgress);
            float size = (float) (1.5f * (1 - fadeProgress * 0.7));

            world.spawnParticle(Particle.DUST,
                    new Location(world, x, groundY + 0.05, z),
                    1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(red, 0, blue), size));
        }
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
                .has(new NamespacedKey(plugin, "enderfire"), PersistentDataType.BYTE)) return;
        Location impact = snowball.getLocation().clone();
        World world = impact.getWorld();
        world.playSound(impact, Sound.ENTITY_ENDER_DRAGON_SHOOT, 0.6f, 1.2f);
        world.spawnParticle(Particle.PORTAL, impact, 20, 0.4, 0.3, 0.4, 0.2);
        world.spawnParticle(Particle.WITCH,  impact, 10, 0.3, 0.2, 0.3, 0.05);
        snowball.remove();
    }

    // ══════════════════════════════════════════════════════════════════
    // EVENTOS
    // ══════════════════════════════════════════════════════════════════

    @Override public void onDamage(EntityDamageByEntityEvent event) {}

    @Override
    public void onDeath(EntityDeathEvent event) {
        rollDrops(event);
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
                    if (++deadCheckCounter >= 5) cancel(); return;
                }
                if (entity.isDead()) {
                    if (++deadCheckCounter >= 3) cancel(); return;
                }
                deadCheckCounter = 0;

                Location base = entity.getLocation().clone();
                World world   = base.getWorld();
                for (int arm = 0; arm < 3; arm++) {
                    double armOffset = (2 * Math.PI / 3) * arm;
                    for (int step = 0; step < 16; step++) {
                        double t = (double) step / 16;
                        double a = angle + armOffset + t * Math.PI * 4;
                        double r = 0.8 + t * 0.5;
                        world.spawnParticle(Particle.DUST,
                                new Location(world,
                                        base.getX() + r * Math.cos(a),
                                        base.getY() + t * 5.5,
                                        base.getZ() + r * Math.sin(a)),
                                1, 0, 0, 0, 0,
                                new Particle.DustOptions(
                                        Color.fromRGB((int)(80+40*t), 0, (int)(180+75*t)), 1.1f));
                    }
                }
                world.spawnParticle(Particle.PORTAL, base.clone().add(0, 0.5, 0),
                        4, 0.4, 0.1, 0.4, 0.2);
                angle += 0.2;
                if (angle > Math.PI * 2) angle -= Math.PI * 2;
            }
        }.runTaskTimer(plugin, 20L, 3L);
    }

    private void activatePhase2() {
        phase2Active = true;
        entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.RESISTANCE,
                Integer.MAX_VALUE, 1, false, true, true));
        disarmCooldown = 0;

        Location loc   = entity.getLocation().clone();
        World    world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_ENDERMAN_SCREAM,    2.0f, 0.4f);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
        world.spawnParticle(Particle.REVERSE_PORTAL, loc.clone().add(0, 3, 0), 200, 3, 3, 3, 0.3);
        world.spawnParticle(Particle.PORTAL,          loc.clone().add(0, 3, 0), 150, 2, 2, 2, 0.5);

        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= 80 * 80) {
                p.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD
                        + "The Enderman Overseer grows stronger!");
            }
        }

        new BukkitRunnable() {
            double angle = 0; int ticks = 0;
            @Override public void run() {
                if (ticks >= 40 || !active || entity == null || entity.isDead()) {
                    cancel(); return;
                }
                double radius = 5.0 * Math.sin((ticks / 40.0) * Math.PI);
                for (int i = 0; i < 6; i++) {
                    double a = angle + i * (Math.PI / 3);
                    world.spawnParticle(Particle.DUST,
                            new Location(world,
                                    loc.getX() + radius * Math.cos(a),
                                    loc.getY() + 2 + Math.sin(ticks * 0.3 + i) * 1.5,
                                    loc.getZ() + radius * Math.sin(a)),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(160, 0, 255), 1.8f));
                }
                angle += 0.4; ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        plugin.getLogger().info("[EndermanOverseer] Fase 2 activada. HP: "
                + String.format("%.1f", entity.getHealth()) + "/" + MAX_HEALTH);
    }

    // ══════════════════════════════════════════════════════════════════
    // UTILIDADES
    // ══════════════════════════════════════════════════════════════════

    private void setAIState(boolean enable) {
        if (aiEnabled == enable) return;
        aiEnabled = enable;
        entity.setAI(enable);
        if (enable && entity instanceof Mob mob) mob.setTarget(target);
    }

    private boolean isClearPath(Location start, Location end) {
        Vector dir     = end.toVector().subtract(start.toVector());
        double maxDist = dir.length();
        dir.normalize();
        for (double d = 0.5; d < maxDist; d += 0.5) {
            if (start.clone().add(dir.clone().multiply(d)).getBlock().getType().isSolid())
                return false;
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
        entity.setRotation(
                (float) Math.toDegrees(Math.atan2(-dx, dz)),
                (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx*dx + dz*dz))));
    }
}