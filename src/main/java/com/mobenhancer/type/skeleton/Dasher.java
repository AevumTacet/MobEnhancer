package com.mobenhancer.type.skeleton;

import com.mobenhancer.SkeletonCustomType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import java.util.Random;

public class Dasher implements SkeletonCustomType {

    private static final double DASH_CHANCE = 0.4; // 75% de probabilidad de dash
    private static final int MAX_DASH_DISTANCE = 2; // Máximo 3 bloques de distancia
    private final Random random = new Random();

    @Override
    public String getId() {
        return "dasher";
    }

    @Override
    public String getName() {
        return "Dasher";
    }

    @Override
    public void onSpawn(Skeleton skeleton, CreatureSpawnEvent e) {
        // No necesita equipamiento especial
    }

    @Override
    public void whenAttacked(Skeleton skeleton, EntityDamageByEntityEvent e) {
        // 66% de probabilidad de ejecutar dash evasivo
        if (random.nextDouble() < DASH_CHANCE) {
            executeDash(skeleton, e);
        }
    }

    private void executeDash(Skeleton skeleton, EntityDamageByEntityEvent event) {
        // Cancelar el daño
        event.setCancelled(true);

        // Crear nube de humo en la posición original
        createSmokeCloud(skeleton.getLocation());

        // Encontrar ubicación segura para el dash
        Location dashLocation = findSafeDashLocation(skeleton);

        if (dashLocation != null) {
            // Efectos de sonido del dash
            playDashEffects(skeleton.getLocation());

            // Teletransportar al esqueleto
            skeleton.teleport(dashLocation);

            // Efectos en la nueva ubicación
            playDashEffects(dashLocation);
        }
    }

    private void createSmokeCloud(Location location) {
        World world = location.getWorld();

        // Partículas de humo intensas
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, location, 15, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.SMOKE, location, 8, 0.7, 0.7, 0.7, 0.05);

    }

    private Location findSafeDashLocation(Skeleton skeleton) {
        Location originalLocation = skeleton.getLocation();
        World world = originalLocation.getWorld();

        // Intentar varias direcciones aleatorias
        for (int i = 0; i < 10; i++) {
            // Generar dirección aleatoria dentro del radio
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 1 + random.nextDouble() * (MAX_DASH_DISTANCE - 1);

            double x = originalLocation.getX() + Math.cos(angle) * distance;
            double z = originalLocation.getZ() + Math.sin(angle) * distance;

            Location potentialLocation = new Location(world, x, originalLocation.getY(), z);

            // Ajustar la posición Y para que esté en un bloque sólido
            potentialLocation = findSafeVerticalPosition(potentialLocation);

            if (potentialLocation != null && isLocationSafe(potentialLocation)) {
                return potentialLocation;
            }
        }

        // Si no encuentra ubicación segura, devolver null
        return null;
    }

    private Location findSafeVerticalPosition(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int originalY = location.getBlockY();

        // Buscar hacia arriba y hacia abajo desde la posición original
        for (int yOffset = -2; yOffset <= 2; yOffset++) {
            int y = originalY + yOffset;
            Location testLocation = new Location(world, x + 0.5, y, z + 0.5);

            if (isLocationSafe(testLocation)) {
                return testLocation;
            }
        }

        return null;
    }

    private boolean isLocationSafe(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Verificar que el bloque en los pies no sea peligroso
        Block feetBlock = world.getBlockAt(x, y, z);
        Block groundBlock = world.getBlockAt(x, y - 1, z);
        Block headBlock = world.getBlockAt(x, y + 1, z);

        // Verificar que no sea agua, lava o bloque peligroso
        if (feetBlock.isLiquid() || groundBlock.isLiquid()) {
            return false;
        }

        // Verificar materiales peligrosos
        Material feetMaterial = feetBlock.getType();
        Material groundMaterial = groundBlock.getType();

        if (feetMaterial == Material.LAVA || feetMaterial == Material.WATER ||
                groundMaterial == Material.LAVA || groundMaterial == Material.WATER ||
                feetMaterial == Material.CACTUS || feetMaterial == Material.MAGMA_BLOCK ||
                feetMaterial == Material.CAMPFIRE || feetMaterial == Material.SOUL_CAMPFIRE) {
            return false;
        }

        // Verificar que tenga espacio (bloques de los pies y cabeza deben ser
        // transitables)
        return feetMaterial.isAir() && headBlock.getType().isAir() &&
                !groundMaterial.isAir() && groundMaterial.isSolid();
    }

    private void playDashEffects(Location location) {
        World world = location.getWorld();

        // Partículas de viento/velocidad
        world.spawnParticle(Particle.CLOUD, location, 5, 0.3, 0.3, 0.3, 0.05);
        world.spawnParticle(Particle.SWEEP_ATTACK, location, 3, 0.5, 0.5, 0.5, 0.02);

        // Sonido de movimiento rápido
        world.playSound(location, Sound.ENTITY_BAT_TAKEOFF, 0.6f, 1.5f);
    }

}