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

    private static final String CUSTOM_TEXTURE_HASH = "ddbc98f98e71537e2b66317a32731b924a9e4de6963f8fd75f94a2208740cb3c";

    @SuppressWarnings("deprecation")
    private ItemStack createCustomHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "Custom");
        PlayerTextures textures = profile.getTextures();

        try {
            URL skinUrl = new URL("https://textures.minecraft.net/texture/" + CUSTOM_TEXTURE_HASH);
            textures.setSkin(skinUrl);
        } catch (MalformedURLException ex) {
            throw new RuntimeException("URL de textura inválida", ex);
        }

        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
        head.setItemMeta(meta);

        return head;
    }

    public PutridColossus(JavaPlugin plugin) {
        super(plugin, "putrid_colossus", "Putrid Colossus", 400.0);
    }

    @Override
    public void spawn(Location location) {
        World world = location.getWorld();
        Zombie zombie = (Zombie) world.spawnEntity(location, EntityType.ZOMBIE);
        zombie.setAdult();
        ItemStack customHead = createCustomHead();
        zombie.getEquipment().setHelmet(customHead);
        zombie.getEquipment().setHelmetDropChance(0);

        zombie.getAttribute(Attribute.SCALE).setBaseValue(2.5);
        zombie.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
        zombie.setHealth(maxHealth);
        zombie.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.2);
        zombie.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(10.0);
        zombie.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        zombie.setRemoveWhenFarAway(false);

        // Eliminar cualquier tipo zombie previo y forzar default
        zombie.getPersistentDataContainer().remove(MobEnhancer.zombieKey);
        zombie.getPersistentDataContainer().set(MobEnhancer.zombieKey, PersistentDataType.STRING, "default");

        // Marcar como boss
        markAsBoss(zombie);

        this.entity = zombie;
        this.entityId = zombie.getUniqueId();
        this.active = true;

        initBossBar();
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

        if (jumpCooldown > 0) jumpCooldown--;
        if (hordeCallCooldown > 0) hordeCallCooldown--;

        LivingEntity target = ((Zombie) entity).getTarget();

        if (target != null && jumpCooldown == 0 && random.nextDouble() < JUMP_CHANCE) {
            jumpTowards(target);
            jumpCooldown = JUMP_COOLDOWN_SECONDS;
        }

        if (target != null && hordeCallCooldown == 0 && random.nextDouble() < HORDE_CALL_CHANCE) {
            callHorde(target);
            hordeCallCooldown = HORDE_CALL_COOLDOWN;
        }
    }

    private void jumpTowards(LivingEntity target) {
        Vector direction = target.getLocation().toVector()
                .subtract(entity.getLocation().toVector())
                .normalize();
        double strength = 1.1;
        Vector velocity = direction.multiply(strength).setY(0.8);
        entity.setVelocity(velocity);

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
                            double damage = 6 * (1 - (distance / 8));
                            p.damage(damage, entity);

                            Vector kb = p.getLocation().toVector()
                                    .subtract(landLoc.toVector())
                                    .normalize()
                                    .multiply(1.5)
                                    .setY(0.5);
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
                    if (zombie.getPersistentDataContainer().has(new NamespacedKey(plugin, "boss_type"),
                            PersistentDataType.STRING)) return;

                    if (zombie.getTarget() == null || !(zombie.getTarget() instanceof Player)) {
                        zombie.setTarget(target);
                        zombie.addPotionEffect(new PotionEffect(
                                PotionEffectType.SPEED,
                                120,
                                1,
                                false,
                                false));
                    }
                });

        world.playSound(entity.getLocation(), Sound.ENTITY_BLAZE_DEATH, 2.0f, 0.3f);
        world.spawnParticle(Particle.SHRIEK, entity.getLocation().add(0, 6, 0), 10, 2, 2, 2, 0, 0);
    }

    @Override
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!active || entity == null) return;

        // Si el coloso recibe daño
        if (entity.equals(event.getEntity())) {
            handleDefense(event);
        }

        // Si el coloso ataca (cuerpo a cuerpo)
        if (event.getDamager().equals(entity) && event.getEntity() instanceof LivingEntity target) {
            handleAttack(target);
        }
    }

    /**
     * Maneja los ataques del coloso (cuando él es el dañador)
     */
        private void handleAttack(LivingEntity target) {
        // Ataque de lanzamiento con probabilidad THROW_CHANCE
        if (random.nextDouble() < THROW_CHANCE) {
            performThrowAttack(target);
        }
    }


    /**
     * Maneja cuando el coloso recibe daño (defensa)
     */
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

                        world.getNearbyEntities(center, 8, 4, 8).stream()
                                .filter(e -> e instanceof Player)
                                .forEach(e -> {
                                    Player p = (Player) e;
                                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 0));
                                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1));
                                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 0));
                                });

                        poisoning = false;
                        cancel();
                        return;
                    }

                    entity.getWorld().spawnParticle(Particle.SNEEZE,
                            entity.getLocation().add(0, 2, 0), 30, 1, 1, 1, 0.1);
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }
    }

    /**
     * Realiza el ataque de agarrar y lanzar (similar a Thrower).
     */
    private void performThrowAttack(LivingEntity target) {

        Zombie zombie = (Zombie) entity;
        zombie.addPassenger(target);
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_HORSE_SADDLE, 1, 1);

        Bukkit.getScheduler().runTaskLater(MobEnhancer.getInstance(), () -> {
            if (!zombie.getPassengers().isEmpty() && zombie.getPassengers().getFirst() == target) {
                zombie.removePassenger(target);
                Bukkit.getScheduler().runTaskLater(MobEnhancer.getInstance(), () -> {
                    Vector d = zombie.getLocation().getDirection().normalize();
                    Vector v = d.multiply(1 + random.nextDouble(1.25)).setY(1 + random.nextDouble(0.25));

                    zombie.getWorld().playSound(zombie.getLocation(), Sound.BLOCK_PISTON_EXTEND, .75f, 1);

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
            Location particleLoc = new Location(world, x, y, z);

            world.spawnParticle(Particle.ENTITY_EFFECT, particleLoc, 60, 0.0, 0.1, 0.0, 20.0,
                    Color.fromRGB(0, 255, 0));
        }
        world.playSound(center, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.0f, 0.3f);
    }

    @Override
    public void onDeath(EntityDeathEvent event) {
        event.getDrops().clear();
        event.getDrops().add(new ItemStack(Material.DIAMOND, 3));
        despawn();
    }
}