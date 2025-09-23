package com.mobenhancer.type.skeleton;

import com.mobenhancer.SkeletonCustomType;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Random;

public class Grenadier implements SkeletonCustomType {

    private static final double GRENADE_CHANCE = 0.30; // 30% de probabilidad de granada
    private static final float EXPLOSION_POWER = 2.0f; // Poder de la explosión (similar a Wind Charge)
    private static final double KNOCKBACK_STRENGTH = 1.5; // Fuerza del knockback

    private final Random random = new Random();

    @Override
    public String getId() {
        return "grenadier";
    }

    @Override
    public String getName() {
        return "Grenadier";
    }

    @Override
    public void onSpawn(Skeleton skeleton, CreatureSpawnEvent e) {
        // No necesita equipamiento especial
    }

    @Override
    public void onShootBow(Skeleton skeleton, EntityShootBowEvent e) {
        // 30% de probabilidad de disparar una granada
        if (random.nextDouble() < GRENADE_CHANCE) {
            Arrow arrow = (Arrow) e.getProjectile();

            // Marcar la flecha como granada
            NamespacedKey grenadeKey = new NamespacedKey(
                    org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mobenhancer.MobEnhancer.class),
                    "grenade_arrow");
            arrow.getPersistentDataContainer().set(grenadeKey, PersistentDataType.BYTE, (byte) 1);

            // Efectos visuales y sonoros especiales para la granada
            customizeGrenadeArrow(arrow);
        }
    }

    private void customizeGrenadeArrow(Arrow arrow) {
        // Hacer la flecha más visible y especial
        arrow.setColor(Color.RED); // Flecha roja
        arrow.setGlowing(true); // Brillo para distinguirla

        // Sonido de carga/activación
        arrow.getWorld().playSound(arrow.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.5f, 1.5f);

        // Partículas durante el vuelo
        startGrenadeParticles(arrow);
    }

    private void startGrenadeParticles(Arrow arrow) {
        // Partículas simples durante el vuelo (cada 2 ticks)
        Bukkit.getScheduler().runTaskTimer(
                org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mobenhancer.MobEnhancer.class),
                task -> {
                    if (!arrow.isValid() || arrow.isOnGround() || arrow.isDead()) {
                        task.cancel();
                        return;
                    }

                    // Partículas de firework
                    arrow.getWorld().spawnParticle(Particle.FIREWORK, arrow.getLocation(), 1, 0.1, 0.1, 0.1, 0.01);
                },
                0L, 2L);
    }

    @Override
    public void onProjectileHit(Skeleton skeleton, ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow))
            return;

        // Verificar si es una flecha-granada
        NamespacedKey grenadeKey = new NamespacedKey(
                org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mobenhancer.MobEnhancer.class),
                "grenade_arrow");

        if (arrow.getPersistentDataContainer().has(grenadeKey, PersistentDataType.BYTE)) {
            handleGrenadeExplosion(arrow, e);
        }
    }

    private void handleGrenadeExplosion(Arrow arrow, ProjectileHitEvent e) {
        Location impactLocation = arrow.getLocation();
        World world = impactLocation.getWorld();

        // 1. Crear explosión falsa (sin daño a bloques)
        world.createExplosion(
                impactLocation.getX(), impactLocation.getY(), impactLocation.getZ(),
                EXPLOSION_POWER, // Poder de la explosión
                false, // ¿Provoca fuego? (no)
                false);

        // 2. Aplicar knockback a entidades cercanas (como Wind Charge)
        applyKnockbackToNearbyEntities(impactLocation);

        // 3. Efectos visuales y sonoros adicionales
        playExplosionEffects(impactLocation);

        // 4. Eliminar la flecha
        arrow.remove();
    }

    private void applyKnockbackToNearbyEntities(Location center) {
        double radius = 3.0; // Radio de efecto

        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity livingEntity && !(entity instanceof ArmorStand)) {
                // Calcular dirección del knockback (alejándose del centro)
                Vector direction = entity.getLocation().toVector().subtract(center.toVector()).normalize();

                // Aplicar knockback (más fuerte cuanto más cerca del centro)
                double distance = entity.getLocation().distance(center);
                double strength = KNOCKBACK_STRENGTH * (1.0 - (distance / radius));

                if (strength > 0) {
                    Vector velocity = direction.multiply(strength).setY(strength * 0.5);
                    livingEntity.setVelocity(velocity);
                }
            }
        }
    }

    private void playExplosionEffects(Location location) {
        World world = location.getWorld();

        // Sonido de explosión
        world.playSound(location, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 0.8f);

        // Partículas de explosión
        world.spawnParticle(Particle.EXPLOSION, location, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.SMOKE, location, 15, 1.0, 1.0, 1.0, 0.2);
        world.spawnParticle(Particle.GUST, location, 8, 0.5, 0.5, 0.5, 0.05);
    }

}