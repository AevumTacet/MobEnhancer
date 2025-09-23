package com.mobenhancer.type.skeleton;

import com.mobenhancer.SkeletonCustomType;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.UUID;

public class Invader implements SkeletonCustomType {

    private static final double SPECIAL_ARROW_CHANCE = 0.25; // 25% de probabilidad

    private HashMap<UUID, Integer> arrowTrailTasks = new HashMap<>();

    @Override
    public String getId() {
        return "invader";
    }

    @Override
    public String getName() {
        return "Invader";
    }

    public void onShootBow(Skeleton skeleton, EntityShootBowEvent event) {
        if (Math.random() < SPECIAL_ARROW_CHANCE) {
            Arrow arrow = (Arrow) event.getProjectile();

            // Marcar la flecha como especial
            NamespacedKey specialKey = new NamespacedKey(
                    org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mobenhancer.MobEnhancer.class), "invasor_arrow");
            arrow.getPersistentDataContainer().set(specialKey, PersistentDataType.BYTE, (byte) 1);

            // Rastro y Sonido
            startSimpleArrowTrail(arrow);
            arrow.getWorld().playSound(arrow.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);
        }
    }

    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow))
            return;
        if (!(arrow.getShooter() instanceof Skeleton shooter))
            return;

        NamespacedKey specialKey = new NamespacedKey(
                org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mobenhancer.MobEnhancer.class), "invasor_arrow");
        if (arrow.getPersistentDataContainer().has(specialKey, PersistentDataType.BYTE)) {
            // Detener las partículas
            stopArrowTrail(arrow);
            handleSpecialArrowHit(shooter, arrow, event);
        }
    }

    // ===== SISTEMA SIMPLE DE RASTRO =====

    private void startSimpleArrowTrail(Arrow arrow) {
        UUID arrowId = arrow.getUniqueId();

        // Tarea simple que spawnea partículas cada tick
        int taskId = Bukkit.getScheduler().runTaskTimer(
                org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mobenhancer.MobEnhancer.class),
                () -> {
                    if (!arrow.isValid() || arrow.isOnGround() || arrow.isDead()) {
                        stopArrowTrail(arrow);
                        return;
                    }

                    // Partículas idénticas al escupitajo de llama
                    arrow.getWorld().spawnParticle(
                            Particle.SPIT, // Misma partícula que usa la llama
                            arrow.getLocation(),
                            2, // Misma cantidad que el spit de llama
                            0.05, 0.05, 0.05, // Offset pequeño
                            0.01 // Velocidad baja
                    );
                },
                0L, // Empezar inmediatamente
                1L // Cada tick
        ).getTaskId();

        arrowTrailTasks.put(arrowId, taskId);
    }

    private void stopArrowTrail(Arrow arrow) {
        UUID arrowId = arrow.getUniqueId();
        Integer taskId = arrowTrailTasks.get(arrowId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
            arrowTrailTasks.remove(arrowId);
        }
    }

    // ===== LÓGICA DE TELETRANSPORTE SIMPLIFICADA =====

    private void handleSpecialArrowHit(Skeleton shooter, Arrow arrow, ProjectileHitEvent event) {

        Entity hitEntity = event.getHitEntity();
        Location impactLocation = arrow.getLocation();

        Location teleportLocation = calculateTeleportLocation(hitEntity, impactLocation);

        if (teleportLocation != null && isSafeLocation(teleportLocation)) {
            executeTeleport(shooter, teleportLocation, hitEntity);
        }
    }

    private Location calculateTeleportLocation(Entity hitEntity, Location impactLocation) {
        if (hitEntity instanceof LivingEntity target) {
            // Calcular posición detrás del objetivo
            Vector direction = target.getLocation().getDirection();
            Location behindTarget = target.getLocation().clone().subtract(direction.multiply(2));
            behindTarget.setY(target.getLocation().getY());
            return behindTarget;
        } else {
            // Impactó en un bloque, teletransportarse al lado del impacto
            return impactLocation.clone().add(0.5, 0.5, 0.5);
        }
    }

    private boolean isSafeLocation(Location location) {
        // Verificar que la ubicación no esté dentro de un bloque sólido
        return location.getBlock().getType().isAir() &&
                location.clone().add(0, 1, 0).getBlock().getType().isAir();
    }

    private void executeTeleport(Skeleton shooter, Location teleportLocation, Entity target) {
        // Efectos de sonido y partículas en la ubicación original
        shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        shooter.getWorld().spawnParticle(Particle.PORTAL, shooter.getLocation(), 10);

        // Teletransportar
        shooter.teleport(teleportLocation);

        // Efectos en la nueva ubicación
        shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        shooter.getWorld().spawnParticle(Particle.PORTAL, shooter.getLocation(), 10);

    }
}