package com.mobenhancer.type.skeleton;

import com.mobenhancer.SkeletonCustomType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.UUID;

public class Invader implements SkeletonCustomType {

    private static final double SPECIAL_ARROW_CHANCE = 0.25; // 25% de probabilidad
    private static final int SEARCH_RADIUS = 3; // Radio de 3 bloques = volumen 6x6x6

    private HashMap<UUID, Integer> arrowTrailTasks = new HashMap<>();

    @Override
    public String getId() {
        return "invader";
    }

    @Override
    public String getName() {
        return "Invader";
    }

    @Override
    public void onShootBow(Skeleton skeleton, EntityShootBowEvent event) {
        if (Math.random() < SPECIAL_ARROW_CHANCE) {
            Arrow arrow = (Arrow) event.getProjectile();

            // Marcar la flecha como especial
            NamespacedKey specialKey = new NamespacedKey(
                    org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mobenhancer.MobEnhancer.class),
                    "invader_arrow"); // Cambiado a "invader_arrow" para consistencia
            arrow.getPersistentDataContainer().set(specialKey, PersistentDataType.BYTE, (byte) 1);

            // Rastro y Sonido
            startSimpleArrowTrail(arrow);
            arrow.getWorld().playSound(arrow.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);
        }
    }

    @Override
    public void onProjectileHit(Skeleton skeleton, ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow))
            return;

        NamespacedKey specialKey = new NamespacedKey(
                org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mobenhancer.MobEnhancer.class),
                "invader_arrow");

        if (arrow.getPersistentDataContainer().has(specialKey, PersistentDataType.BYTE)) {
            // Detener las partículas
            stopArrowTrail(arrow);

            // Verificar que el shooter sea un esqueleto
            if (arrow.getShooter() instanceof Skeleton shooter) {
                handleSpecialArrowHit(shooter, arrow, event);
            }
        }
    }

    // ===== SISTEMA SIMPLE DE RASTRO =====

    private void startSimpleArrowTrail(Arrow arrow) {
        UUID arrowId = arrow.getUniqueId();

        int taskId = Bukkit.getScheduler().runTaskTimer(
                org.bukkit.plugin.java.JavaPlugin.getPlugin(com.mobenhancer.MobEnhancer.class),
                () -> {
                    if (!arrow.isValid() || arrow.isOnGround() || arrow.isDead()) {
                        stopArrowTrail(arrow);
                        return;
                    }

                    arrow.getWorld().spawnParticle(
                            Particle.SPIT,
                            arrow.getLocation(),
                            2,
                            0.05, 0.05, 0.05,
                            0.01);
                },
                0L,
                1L).getTaskId();

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

    // ===== NUEVO SISTEMA DE BÚSQUEDA DE UBICACIÓN =====

    private void handleSpecialArrowHit(Skeleton shooter, Arrow arrow, ProjectileHitEvent event) {
        Location impactLocation = arrow.getLocation();

        // Buscar ubicación segura en un volumen 6x6x6
        Location safeLocation = findSafeLocationNearby(impactLocation);

        if (safeLocation != null) {
            executeTeleport(shooter, safeLocation);
        } else {
            // Si no encuentra ubicación segura, intentar teletransportarse detrás del
            // objetivo si existe
            Entity hitEntity = event.getHitEntity();
            if (hitEntity instanceof LivingEntity target) {
                Location behindTarget = calculatePositionBehindTarget(target);
                if (behindTarget != null && isLocationSafe(behindTarget)) {
                    executeTeleport(shooter, behindTarget);
                }
            }
        }

        // Eliminar la flecha
        arrow.remove();
    }

    private Location findSafeLocationNearby(Location center) {
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        // Buscar en un volumen 6x6x6 (radio 3 en cada dirección)
        for (int x = centerX - SEARCH_RADIUS; x <= centerX + SEARCH_RADIUS; x++) {
            for (int y = centerY - SEARCH_RADIUS; y <= centerY + SEARCH_RADIUS; y++) {
                for (int z = centerZ - SEARCH_RADIUS; z <= centerZ + SEARCH_RADIUS; z++) {
                    Location testLocation = new Location(world, x + 0.5, y, z + 0.5);

                    if (isLocationSafe(testLocation)) {
                        return testLocation;
                    }
                }
            }
        }

        return null; // No se encontró ubicación segura
    }

    private Location calculatePositionBehindTarget(LivingEntity target) {
        Vector direction = target.getLocation().getDirection();
        Location behindTarget = target.getLocation().clone().subtract(direction.multiply(2));
        behindTarget.setY(target.getLocation().getY());
        return behindTarget;
    }

    private boolean isLocationSafe(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Verificar que el bloque en los pies sea seguro y sólido
        Block feetBlock = world.getBlockAt(x, y, z);
        Block groundBlock = world.getBlockAt(x, y - 1, z);
        Block headBlock = world.getBlockAt(x, y + 1, z);

        // El bloque de los pies y la cabeza deben ser aire (transitable)
        boolean hasSpace = feetBlock.getType().isAir() && headBlock.getType().isAir();

        // El bloque del suelo debe ser sólido (para pararse)
        boolean hasSolidGround = groundBlock.getType().isSolid();

        // Verificar que no sea un bloque peligroso
        boolean isNotDangerous = !groundBlock.isLiquid() &&
                !groundBlock.getType().toString().contains("LAVA") &&
                !groundBlock.getType().toString().contains("FIRE") &&
                !groundBlock.getType().toString().contains("CACTUS") &&
                !groundBlock.getType().toString().contains("MAGMA");

        return hasSpace && hasSolidGround && isNotDangerous;
    }

    private void executeTeleport(Skeleton shooter, Location teleportLocation) {
        // Efectos en la ubicación original
        playTeleportEffects(shooter.getLocation());

        // Teletransportar
        shooter.teleport(teleportLocation);

        // Efectos en la nueva ubicación
        playTeleportEffects(teleportLocation);

        // Opcional: Hacer que el esqueleto ataque inmediatamente si tiene target
        if (shooter.getTarget() != null) {
            shooter.attack(shooter.getTarget());
        }
    }

    private void playTeleportEffects(Location location) {
        World world = location.getWorld();
        world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        world.spawnParticle(Particle.PORTAL, location, 10, 0.5, 0.5, 0.5, 0.1);
    }
}