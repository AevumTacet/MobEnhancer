package com.mobenhancer;


import org.bukkit.*;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class EndermanControl implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, LivingEntity> trackedEndermen = new HashMap<>();
    private final Random random = new Random();
    private static final double PULL_RANGE = 5.0;
    private static final double PULL_CHANCE = 0.5;

    public EndermanControl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEndermanTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Enderman enderman && event.getTarget() != null) {
            trackedEndermen.put(enderman.getUniqueId(), event.getTarget());
            startPullCheck(enderman);
        }
    }

    private void startPullCheck(Enderman enderman) {
        new BukkitRunnable() {
            public void run() {
                // Verificar estado y target actual
                if (!enderman.isValid() || 
                    !trackedEndermen.containsKey(enderman.getUniqueId()) || 
                    enderman.getTarget() != trackedEndermen.get(enderman.getUniqueId())) {
                    
                    trackedEndermen.remove(enderman.getUniqueId());
                    cancel();
                    return;
                }

                LivingEntity target = trackedEndermen.get(enderman.getUniqueId());
                if (shouldPull(enderman, target)) {
                    applyPull(enderman, target);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Check cada 1 segundo
    }

    private boolean shouldPull(Enderman enderman, LivingEntity target) {
        return enderman.getLocation().distance(target.getLocation()) > PULL_RANGE &&
                random.nextDouble() <= PULL_CHANCE &&
                enderman.getTarget() != null &&
                enderman.getTarget() == target &&
                enderman.hasLineOfSight(target) &&
               isClearPath(enderman.getEyeLocation(), target.getEyeLocation());
    }

    private boolean isClearPath(Location start, Location end) {
        Vector direction = end.toVector().subtract(start.toVector());
        double maxDistance = direction.length();
        direction.normalize();

        for (double d = 0; d < maxDistance; d += 0.5) {
            Location checkPoint = start.clone().add(direction.clone().multiply(d));
            if (checkPoint.getBlock().getType().isSolid()) return false;
        }
        return true;
    }

    private void applyPull(Enderman enderman, LivingEntity target) {
        Vector pullDirection = enderman.getLocation().toVector()
            .subtract(target.getLocation().toVector())
            .normalize()
            .multiply(1.3)
            .setY(0.5);

        target.setVelocity(pullDirection);
        playPullEffects(enderman, target);
    }

    private void playPullEffects(Enderman enderman, LivingEntity target) {
        // Efectos en el objetivo
        target.getWorld().spawnParticle(
            Particle.REVERSE_PORTAL,
            target.getLocation().add(0, 1, 0),
            25,
            0.5, 0.5, 0.5,
            0.1
        );
        
        target.getWorld().playSound(
            target.getLocation(),
            Sound.ENTITY_ENDERMAN_TELEPORT,
            1.2f,
            0.7f
        );

        // Efectos en el Enderman
        enderman.getWorld().spawnParticle(
            Particle.ELECTRIC_SPARK,
            enderman.getEyeLocation(),
            10,
            0.3, 0.3, 0.3,
            0.05
        );
    }
}