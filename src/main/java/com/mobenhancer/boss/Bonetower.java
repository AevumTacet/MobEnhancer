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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.mobenhancer.MobEnhancer;

import java.util.*;

public class Bonetower extends Boss {
    private final List<LivingEntity> parts = new ArrayList<>(); // [0]=bottom, [1]=middle, [2]=top
    private double sharedHealth;
    private final double maxSharedHealth = 200.0;
    private int abilityCooldown = 0;
    private final Random random = new Random();

    // Claves para datos persistentes
    private static final NamespacedKey BONETOWER_POS_KEY = new NamespacedKey(MobEnhancer.getInstance(), "bonetower_pos");
    private static final NamespacedKey LIFESTEAL_ARROW_KEY = new NamespacedKey(MobEnhancer.getInstance(), "lifesteal_arrow");
    private static final NamespacedKey GRENADE_ARROW_KEY = new NamespacedKey(MobEnhancer.getInstance(), "mortar_arrow");

    public Bonetower(JavaPlugin plugin) {
        super(plugin, "bonetower", "Bone Tower", 200.0);
    }

    @Override
    public void spawn(Location location) {
        World world = location.getWorld();
        Location spawnLoc = location.clone();

        // Spawnear los tres esqueletos
        Skeleton bottom = (Skeleton) world.spawnEntity(spawnLoc, EntityType.SKELETON);
        Skeleton middle = (Skeleton) world.spawnEntity(spawnLoc.clone().add(0, 1.1, 0), EntityType.SKELETON);
        Skeleton top = (Skeleton) world.spawnEntity(spawnLoc.clone().add(0, 2.2, 0), EntityType.SKELETON);
        top.getPersistentDataContainer().remove(MobEnhancer.skeletonKey);
        top.getPersistentDataContainer().set(MobEnhancer.skeletonKey, PersistentDataType.STRING, "default_skeleton");
        middle.getPersistentDataContainer().remove(MobEnhancer.skeletonKey);
        middle.getPersistentDataContainer().set(MobEnhancer.skeletonKey, PersistentDataType.STRING, "default_skeleton");
        bottom.getPersistentDataContainer().remove(MobEnhancer.skeletonKey);
        bottom.getPersistentDataContainer().set(MobEnhancer.skeletonKey, PersistentDataType.STRING, "default_skeleton");
        // Configurar atributos y aspecto
        configureSkeleton(bottom, 1.5, "bottom");
        configureSkeleton(middle, 1.5, "middle");
        configureSkeleton(top, 1.5, "top");

        // Apilarlos: bottom lleva a middle, middle lleva a top
        bottom.addPassenger(middle);
        middle.addPassenger(top);

        // Guardar referencias
        parts.add(bottom);
        parts.add(middle);
        parts.add(top);

        // Salud compartida
        sharedHealth = maxSharedHealth;

        // Marcar como boss (todas las partes)
        for (LivingEntity part : parts) {
            markAsBoss(part);
        }

        // La entidad principal para la bossbar será el bottom
        this.entity = bottom;
        this.entityId = bottom.getUniqueId();
        this.active = true;

        initBossBar();
        startTicking();
    }

    private void configureSkeleton(Skeleton skele, double scale, String position) {
        skele.getAttribute(Attribute.SCALE).setBaseValue(scale);
        skele.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxSharedHealth);
        skele.setHealth(maxSharedHealth);
        skele.setInvulnerable(true); // Evita daño directo, manejamos daño manualmente
        skele.setRemoveWhenFarAway(false);
        skele.setCanPickupItems(false);

        // Marcar la posición en PersistentData
        skele.getPersistentDataContainer().set(BONETOWER_POS_KEY, PersistentDataType.STRING, position);

        // Equipar arco a todos
        skele.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
        skele.getEquipment().setItemInMainHandDropChance(0);
    }

    private void startTicking() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || parts.isEmpty() || parts.stream().allMatch(Entity::isDead)) {
                    cancel();
                    return;
                }
                tick();
            }
        }.runTaskTimer(plugin, 0L, 20L); // cada segundo
    }

    @Override
    public void tick() {
        // Actualizar bossbar
        updateBossBar();
        updateBossBarViewers(50);

        // Sincronizar objetivos: todas las partes deben tener el mismo target (el del bottom)
        LivingEntity target = ((Mob) parts.get(0)).getTarget();
        for (LivingEntity part : parts) {
            if (part instanceof Mob) {
                ((Mob) part).setTarget(target);
            }
        }

        if (target == null) return;

        // Ejecutar habilidades según posición (cada segundo)
        for (LivingEntity part : parts) {
            String pos = part.getPersistentDataContainer().get(BONETOWER_POS_KEY, PersistentDataType.STRING);
            if (pos == null) continue;

            switch (pos) {
                case "bottom":
                    // Lifesteal arrow (20% de probabilidad)
                    if (random.nextDouble() < 0.2) {
                        shootLifestealArrow((Skeleton) part, target);
                    }
                    break;
                case "middle":
                    // Multishot arrows (15% de probabilidad)
                    if (random.nextDouble() < 0.15) {
                        shootMultishot((Skeleton) part, target);
                    }
                    break;
                case "top":
                    // Grenade arrow (10% de probabilidad)
                    if (random.nextDouble() < 0.1) {
                        shootGrenadeArrow((Skeleton) part, target);
                    }
                    break;
            }
        }
    }

    // ==================== HABILIDADES ====================

    /**
     * Dispara una flecha con robo de vida (rastro rojo).
     */
    private void shootLifestealArrow(Skeleton shooter, LivingEntity target) {
        Arrow arrow = shooter.launchProjectile(Arrow.class);
        arrow.setDamage(0); // El daño se maneja manualmente al impactar
        arrow.setShooter(shooter);
        arrow.setVelocity(calculateVelocity(shooter.getEyeLocation(), target.getEyeLocation(), 1.8));
        arrow.setColor(Color.RED);
        arrow.setGlowing(true);

        // Marcar la flecha
        arrow.getPersistentDataContainer().set(LIFESTEAL_ARROW_KEY, PersistentDataType.BYTE, (byte) 1);

        // Partículas de rastro rojo
        startArrowTrail(arrow, Particle.DUST, new Particle.DustOptions(Color.RED, 1));

        shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.5f, 1.2f);
    }

    /**
     * Dispara tres flechas con efectos variados (multishot).
     */
    private void shootMultishot(Skeleton shooter, LivingEntity target) {
        Location eyeLoc = shooter.getEyeLocation();
        Vector baseDir = target.getEyeLocation().toVector().subtract(eyeLoc.toVector()).normalize();

        for (int i = 0; i < 3; i++) {
            // Añadir pequeña dispersión
            double spread = 0.1;
            Vector dir = baseDir.clone().add(new Vector(
                    random.nextGaussian() * spread,
                    random.nextGaussian() * spread,
                    random.nextGaussian() * spread)).normalize();

            Arrow arrow = shooter.launchProjectile(Arrow.class);
            arrow.setShooter(shooter);
            arrow.setVelocity(dir.multiply(1.6));
            arrow.setDamage(2.0); // daño base

            // Aplicar efectos aleatorios
            if (random.nextDouble() < 0.3) {
                // Flecha de fuego
                arrow.setFireTicks(100); // 5 segundos de fuego
                arrow.setColor(Color.ORANGE);
            } else if (random.nextDouble() < 0.3) {
                // Flecha de lentitud (como stray)
                arrow.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1), true);
                arrow.setColor(Color.AQUA);
            } else {
                arrow.setColor(Color.WHITE);
            }

            // Partículas de rastro según el efecto
            if (arrow.getFireTicks() > 0) {
                startArrowTrail(arrow, Particle.FLAME, null);
            } else if (arrow.hasCustomEffect(PotionEffectType.SLOWNESS)) {
                startArrowTrail(arrow, Particle.SNOWFLAKE, null);
            }
        }

        shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.5f, 1.0f);
    }

    /**
     * Dispara una flecha explosiva (granada) similar al Grenadier.
     */
    private void shootGrenadeArrow(Skeleton shooter, LivingEntity target) {
        Arrow arrow = shooter.launchProjectile(Arrow.class);
        arrow.setShooter(shooter);

        // Calcular trayectoria parabólica (similar a Grenadier)
        Location start = shooter.getEyeLocation();
        Location end = target.getEyeLocation();
        double distance = start.distance(end);
        double angle = 70.0; // grados
        double baseSpeed = 0.8;
        double angleRad = Math.toRadians(angle);
        double horizontalSpeed = baseSpeed * Math.cos(angleRad);
        double verticalSpeed = baseSpeed * Math.sin(angleRad);

        Vector toTarget = end.toVector().subtract(start.toVector());
        Vector horizontalDir = new Vector(toTarget.getX(), 0, toTarget.getZ()).normalize();
        Vector velocity = horizontalDir.multiply(horizontalSpeed).setY(verticalSpeed);
        arrow.setVelocity(velocity);

        // Marcar como flecha de granada
        arrow.getPersistentDataContainer().set(GRENADE_ARROW_KEY, PersistentDataType.BYTE, (byte) 1);
        arrow.setColor(Color.RED);
        arrow.setGlowing(true);

        // Iniciar rastro de humo
        startGrenadeTrail(arrow);

        shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.0f);
    }

    // ==================== MANEJO DE IMPACTOS ====================

    /**
     * Procesa el impacto de un proyectil (llamado desde el listener).
     */
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!active) return;
        if (!(event.getEntity() instanceof Arrow arrow)) return;

        if (arrow.getPersistentDataContainer().has(LIFESTEAL_ARROW_KEY, PersistentDataType.BYTE)) {
            handleLifestealArrow(arrow, event);
        } else if (arrow.getPersistentDataContainer().has(GRENADE_ARROW_KEY, PersistentDataType.BYTE)) {
            handleGrenadeArrow(arrow, event);
        }
    }

    private void handleLifestealArrow(Arrow arrow, ProjectileHitEvent event) {
        if (event.getHitEntity() instanceof LivingEntity target && !(target instanceof ArmorStand)) {
            double damage = 5.0;
            if (arrow.getShooter() instanceof Entity damager) {
                target.damage(damage, damager);
            } else {
                target.damage(damage);
            }

            sharedHealth = Math.min(maxSharedHealth, sharedHealth + damage);
            for (LivingEntity part : parts) {
                if (!part.isDead()) {
                    part.setHealth(sharedHealth);
                }
            }
            bossBar.setProgress(sharedHealth / maxSharedHealth);

            target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        }
        arrow.remove();
    }

    private void handleGrenadeArrow(Arrow arrow, ProjectileHitEvent event) {
        Location loc = arrow.getLocation();
        World world = loc.getWorld();

        Entity source = (arrow.getShooter() instanceof Entity) ? (Entity) arrow.getShooter() : null;
        world.createExplosion(loc.getX(), loc.getY(), loc.getZ(), 2.0f, false, false, source);

        world.getNearbyEntities(loc, 3, 3, 3).stream()
                .filter(e -> e instanceof LivingEntity && !(e instanceof ArmorStand) && !parts.contains(e))
                .forEach(e -> {
                    LivingEntity le = (LivingEntity) e;
                    if (source != null) {
                        le.damage(4.0, source);
                    } else {
                        le.damage(4.0);
                    }
                    Vector knockback = le.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.2).setY(0.4);
                    le.setVelocity(knockback);
                });

        world.spawnParticle(Particle.EXPLOSION, loc, 1);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);

        arrow.remove();
    }

    // ==================== UTILIDADES ====================

    private Vector calculateVelocity(Location from, Location to, double speed) {
        return to.toVector().subtract(from.toVector()).normalize().multiply(speed);
    }

    private void startArrowTrail(Arrow arrow, Particle particle, Object data) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isOnGround() || arrow.isDead()) {
                    cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(particle, arrow.getLocation(), 2, 0.05, 0.05, 0.05, 0, data);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startGrenadeTrail(Arrow arrow) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isOnGround() || arrow.isDead()) {
                    cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(Particle.SMOKE, arrow.getLocation(), 3, 0.1, 0.1, 0.1, 0.02);
                arrow.getWorld().spawnParticle(Particle.SPIT, arrow.getLocation(), 2, 0.1, 0.1, 0.1, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ==================== MANEJO DE DAÑO Y MUERTE ====================

    @Override
    public void onDamage(EntityDamageByEntityEvent event) {
        // Cancelar el daño directo a las partes
        event.setCancelled(true);
        applyDamage(event);
    }

    public void applyDamage(EntityDamageByEntityEvent event) {
        if (!active) return;

        double damage = event.getDamage();
        sharedHealth -= damage;
        if (sharedHealth < 0) sharedHealth = 0;

        // Actualizar salud de todas las partes (para la barra visual)
        for (LivingEntity part : parts) {
            if (!part.isDead()) {
                part.setHealth(Math.max(0.5, sharedHealth));
            }
        }

        bossBar.setProgress(sharedHealth / maxSharedHealth);

        // Partículas de daño
        event.getEntity().getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, event.getEntity().getLocation(), 10, 0.5, 0.5, 0.5, 0);

        if (sharedHealth <= 0) {
            // Matar a todas las partes
            for (LivingEntity part : parts) {
                if (!part.isDead()) {
                    part.setInvulnerable(false);
                    part.damage(1000);
                }
            }
        }
    }

    @Override
    public void onDeath(EntityDeathEvent event) {
        if (!active) return;
        active = false;

        // Limpiar drops por defecto
        event.getDrops().clear();

        // Añadir loot configurable (ejemplo)
        event.getDrops().add(new ItemStack(Material.BONE, 10 + random.nextInt(11)));
        event.getDrops().add(new ItemStack(Material.ARROW, 8 + random.nextInt(9)));
        event.getDrops().add(new ItemStack(Material.EXPERIENCE_BOTTLE, 5));
        event.setDroppedExp(100);

        // Eliminar las demás partes (por si alguna no ha muerto)
        for (LivingEntity part : parts) {
            if (!part.isDead() && !part.equals(event.getEntity())) {
                part.remove();
            }
        }

        despawn();
    }

    @Override
    public List<LivingEntity> getEntities() {
        return parts;
    }
}