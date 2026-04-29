package com.mobenhancer.boss;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.mobenhancer.MobEnhancer;
import com.mobenhancer.integration.CraftEngineHook;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;
import java.util.UUID;

public class PutridColossus extends Boss {
    private final Random random = new Random();
    private boolean poisoning = false;
    private int jumpCooldown = 0;
    private int hordeCallCooldown = 0;

    private static final int JUMP_COOLDOWN_SECONDS = 3;
    private static final double JUMP_CHANCE = 0.15;
    private static final double THROW_CHANCE = 0.2;
    private static final double HORDE_CALL_CHANCE = 0.1;
    private static final int HORDE_CALL_COOLDOWN = 10;

    private static final double PHASE2_HEALTH_PERCENT   = 0.30;
    private static final double PHASE2_JUMP_CHANCE      = JUMP_CHANCE  * 1.5;
    private static final double PHASE2_THROW_CHANCE     = THROW_CHANCE * 1.5;

    private boolean phase2Active = false;

    private static final String CUSTOM_TEXTURE_HASH =
            "ddbc98f98e71537e2b66317a32731b924a9e4de6963f8fd75f94a2208740cb3c";

    @SuppressWarnings("deprecation")
    private ItemStack createCustomHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "Custom");
        PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(new URL("https://textures.minecraft.net/texture/" + CUSTOM_TEXTURE_HASH));
        } catch (MalformedURLException ex) {
            throw new RuntimeException("URL de textura inválida", ex);
        }
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    public PutridColossus(JavaPlugin plugin) {
        super(plugin, "putrid_colossus", "Putrid Colossus", 1600.0);
    }

    @Override
    public void spawn(Location location) {
        World world = location.getWorld();
        Zombie zombie = (Zombie) world.spawnEntity(location, EntityType.ZOMBIE);
        zombie.setAdult();

        zombie.getEquipment().setHelmet(resolveHelmet(createCustomHead()));
        zombie.getEquipment().setHelmetDropChance(0);

        // ── Maza custom ───────────────────────────────────────────
        ItemStack mace = CraftEngineHook.resolveItem("regnum:colossus_mace");
        if (mace == null) mace = new ItemStack(Material.MACE); // fallback vanilla
        zombie.getEquipment().setItemInMainHand(mace);
        zombie.getEquipment().setItemInMainHandDropChance(0);

        zombie.getAttribute(Attribute.SCALE).setBaseValue(2.5);
        zombie.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
        zombie.setHealth(maxHealth);
        zombie.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.2);
        zombie.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(20.0);
        zombie.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        zombie.setRemoveWhenFarAway(false);

        // ── Nombre invisible para el kill message (feature B) ────────────────
        // setCustomName con formato controla el mensaje de muerte.
        // setCustomNameVisible(false) oculta el nametag sobre la cabeza.
        zombie.setCustomName(ChatColor.DARK_GREEN + "Putrid Colossus");
        zombie.setCustomNameVisible(false);

        zombie.getPersistentDataContainer().remove(MobEnhancer.zombieKey);
        zombie.getPersistentDataContainer().set(
                MobEnhancer.zombieKey, PersistentDataType.STRING, "default");

        markAsBoss(zombie);

        this.entity   = zombie;
        this.entityId = zombie.getUniqueId();
        this.active   = true;

        initBossBar();
        holdChunk();
        startTicking();
    }

    private void startTicking() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || entity == null || entity.isDead()) {
                    cancel();
                    return;
                }
                tick();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @Override
    public void tick() {
        updateBossBar();
        updateBossBarViewers(50);

        if (!phase2Active && entity.getHealth() <= maxHealth * PHASE2_HEALTH_PERCENT) {
            activatePhase2();
        }

        if (jumpCooldown > 0) jumpCooldown--;
        if (hordeCallCooldown > 0) hordeCallCooldown--;

        LivingEntity target = ((Zombie) entity).getTarget();

        if (target instanceof Player player && !isValidTarget(player)) {
            ((Zombie) entity).setTarget(null);
            target = null;
        }

        // ── Bug 1 fix: actualizar lastTargetTime mientras haya target activo ──
        if (target != null) {
            lastTargetTime = System.currentTimeMillis();
        }

        double currentJumpChance  = phase2Active ? PHASE2_JUMP_CHANCE  : JUMP_CHANCE;

        if (target != null && jumpCooldown == 0
                && random.nextDouble() < currentJumpChance) {
            jumpTowards(target);
            jumpCooldown = JUMP_COOLDOWN_SECONDS;
        }

        if (target != null && hordeCallCooldown == 0
                && random.nextDouble() < HORDE_CALL_CHANCE) {
            callHorde(target);
            hordeCallCooldown = HORDE_CALL_COOLDOWN;
        }
    }

    private void jumpTowards(LivingEntity target) {
        Vector direction = target.getLocation().toVector()
                .subtract(entity.getLocation().toVector())
                .normalize();
        entity.setVelocity(direction.multiply(1.1).setY(0.8));

        entity.getWorld().spawnParticle(Particle.CLOUD, entity.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 0.8f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active || entity.isDead()) return;

            Location landLoc = entity.getLocation();
            World world = landLoc.getWorld();

            world.getNearbyEntities(landLoc, 8, 4, 8).stream()
                    .filter(e -> e instanceof Player)
                    .forEach(e -> {
                        Player p = (Player) e;
                        double distance = p.getLocation().distance(landLoc);
                        if (distance <= 8) {
                            double damage = 10 * (1 - (distance / 8));
                            p.damage(damage, entity);
                            Vector kb = p.getLocation().toVector()
                                    .subtract(landLoc.toVector())
                                    .normalize().multiply(1.5).setY(0.5);
                            p.setVelocity(kb);
                        }
                    });

            world.spawnParticle(Particle.EXPLOSION, landLoc, 1);
            world.playSound(landLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
        }, 20L);
    }

    private void callHorde(LivingEntity target) {
        World world = entity.getWorld();
        double radius = 80.0;

        world.getNearbyEntities(entity.getLocation(), radius, radius / 2, radius).stream()
                .filter(e -> e instanceof Zombie && !e.equals(entity))
                .map(e -> (Zombie) e)
                .forEach(zombie -> {
                    if (zombie.getPersistentDataContainer().has(
                            new NamespacedKey(plugin, "boss_type"),
                            PersistentDataType.STRING)) return;
                    if (zombie.getTarget() == null || !(zombie.getTarget() instanceof Player)) {
                        zombie.setTarget(target);
                        zombie.addPotionEffect(new PotionEffect(
                                PotionEffectType.SPEED, 120, 1, false, false));
                    }
                });

        world.playSound(entity.getLocation(), Sound.ENTITY_BLAZE_DEATH, 2.0f, 0.3f);
        world.spawnParticle(Particle.SHRIEK, entity.getLocation().add(0, 6, 0), 10, 2, 2, 2, 0, 0);
    }

    @Override
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!active || entity == null) return;
        if (entity.equals(event.getEntity())) handleDefense(event);
        if (event.getDamager().equals(entity)
                && event.getEntity() instanceof LivingEntity t) handleAttack(t);
    }

    private void handleAttack(LivingEntity target) {
        double currentThrowChance = phase2Active ? PHASE2_THROW_CHANCE : THROW_CHANCE;
        if (random.nextDouble() < currentThrowChance) performThrowAttack(target);
    }

    private void handleDefense(EntityDamageByEntityEvent event) {
        if (random.nextDouble() < 0.2 && !poisoning) {
            poisoning = true;
            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (!active || entity.isDead()) {
                        poisoning = false;
                        cancel();
                        return;
                    }
                    if (ticks >= 3) {
                        Location center = entity.getLocation();
                        World world = center.getWorld();
                        world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center, 80, 4, 2, 4, 0);
                        drawPoisonRing(center, 8.0);

                        int poisonAmplifier = phase2Active ? 1 : 0;
                        int slowAmplifier   = phase2Active ? 3 : 1;
                        int weakAmplifier   = phase2Active ? 1 : 0;
                        int duration        = phase2Active ? 400 : 200;

                        world.getNearbyEntities(center, 8, 4, 8).stream()
                                .filter(e -> e instanceof Player)
                                .forEach(e -> {
                                    Player p = (Player) e;
                                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON,   duration, poisonAmplifier));
                                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  duration, slowAmplifier));
                                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,  duration, weakAmplifier));
                                    if (phase2Active)
                                        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duration, 0));
                                });
                        poisoning = false;
                        cancel();
                        return;
                    }
                    int particleCount = phase2Active ? 60 : 30;
                    entity.getWorld().spawnParticle(Particle.SNEEZE,
                            entity.getLocation().clone().add(0, 2, 0),
                            particleCount, 1, 1, 1, 0.1);
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }
    }

    private void performThrowAttack(LivingEntity target) {
        Zombie zombie = (Zombie) entity;
        zombie.addPassenger(target);
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_HORSE_SADDLE, 1, 1);

        Bukkit.getScheduler().runTaskLater(MobEnhancer.getInstance(), () -> {
            if (!zombie.getPassengers().isEmpty()
                    && zombie.getPassengers().getFirst() == target) {
                zombie.removePassenger(target);
                Bukkit.getScheduler().runTaskLater(MobEnhancer.getInstance(), () -> {
                    Vector d = zombie.getLocation().getDirection().normalize();
                    Vector v = d.multiply(1 + random.nextDouble(1.25))
                                .setY(1 + random.nextDouble(0.25));
                    zombie.getWorld().playSound(zombie.getLocation(),
                            Sound.BLOCK_PISTON_EXTEND, .75f, 1);
                    target.setVelocity(v);
                }, 1L);
            }
        }, 8L);
    }

    private void drawPoisonRing(Location center, double radius) {
        World world = center.getWorld();
        int points = 36;
        double angleStep = 2 * Math.PI / points;
        for (int i = 0; i < points; i++) {
            double angle = i * angleStep;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
            world.spawnParticle(Particle.ENTITY_EFFECT,
                    new Location(world, x, y, z),
                    60, 0.0, 0.1, 0.0, 20.0, Color.fromRGB(0, 255, 0));
        }
        world.playSound(center, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.0f, 0.3f);
    }

    private void activatePhase2() {
        phase2Active = true;

        entity.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, true, true));
        entity.addPotionEffect(new PotionEffect(
                PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1, false, true, true));

        Location loc = entity.getLocation().clone();
        World world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE,              1.5f, 0.4f);
        world.playSound(loc, Sound.ENTITY_EVOKER_CAST_SPELL,            1.0f, 0.3f);
        world.playSound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED,    1.0f, 0.5f);

        world.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 3);
        world.spawnParticle(Particle.SPORE_BLOSSOM_AIR,
                loc.clone().add(0, 2, 0), 120, 3, 2, 3, 0.05);

        drawPoisonRing(loc, 8.0);
        drawPoisonRing(loc, 5.0);
        drawPoisonRing(loc, 2.5);

        new BukkitRunnable() {
            double angle = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 50 || !active || entity == null || entity.isDead()) {
                    cancel(); return;
                }
                double progress = ticks / 50.0;
                double radius = 6.0 * Math.sin(progress * Math.PI);
                for (int i = 0; i < 8; i++) {
                    double a = angle + i * (Math.PI / 4);
                    double x = loc.getX() + radius * Math.cos(a);
                    double z = loc.getZ() + radius * Math.sin(a);
                    double y = loc.getY() + 1.5 + Math.sin(ticks * 0.25 + i) * 1.2;
                    world.spawnParticle(Particle.ENTITY_EFFECT,
                            new Location(world, x, y, z),
                            1, 0, 0, 0, 0, Color.fromRGB(0, 200, 50));
                }
                if (ticks == 25) {
                    world.spawnParticle(Particle.SPORE_BLOSSOM_AIR,
                            loc.clone().add(0, 3, 0), 200, 4, 3, 4, 0.02);
                    world.playSound(loc, Sound.ENTITY_SLIME_SQUISH_SMALL, 2.0f, 0.3f);
                }
                angle += 0.3;
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        plugin.getLogger().info("[PutridColossus] Fase 2 activada. HP: "
                + String.format("%.1f", entity.getHealth()) + "/" + maxHealth);
    }

    @Override
    public void onDeath(EntityDeathEvent event) {
        rollDrops(event);
        despawn();
    }
}