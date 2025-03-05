package com.mobenhancer;

import org.bukkit.Particle;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.Random;

public class SkeletonControl implements Listener {

    private final JavaPlugin plugin;
    private final Random random = new Random();
    private static final double DASH_POWER = 1.5;
    
    public SkeletonControl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBowCharge(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Skeleton skeleton)) return;
        if (!(skeleton.getTarget() instanceof Player player)) return;
        
        // Guardar estado del bloqueo en el momento del disparo
        boolean wasBlocking = player.isBlocking();
        Vector playerDirection = player.getLocation().getDirection().clone();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!wasBlocking) return;
                if (!skeleton.isValid()) return;

                applyDash(skeleton, playerDirection);
                spawnDashEffects(skeleton);
            }
        }.runTaskLater(plugin, 1L); // 1 tick después del disparo
    }

    private void applyDash(Skeleton skeleton, Vector playerDir) {
        playerDir.setY(0).normalize();
        Vector dashVector = switch (random.nextInt(3)) {
            case 0 -> getLateralVector(playerDir, true); // Derecha
            case 1 -> getLateralVector(playerDir, false); // Izquierda
            default -> playerDir.multiply(-1.2); // Retroceso
        };

        skeleton.setVelocity(dashVector.multiply(DASH_POWER));
    }

    private Vector getLateralVector(Vector direction, boolean right) {
        return new Vector(-direction.getZ(), 0.25, direction.getX())
               .multiply(right ? 0.8 : -0.8)
               .add(direction.multiply(-0.3));
    }

    private void spawnDashEffects(Skeleton skeleton) {
        skeleton.getWorld().spawnParticle(
            Particle.SWEEP_ATTACK,
            skeleton.getLocation().add(0, 1, 0),
            5,
            0.4,
            0.5,
            0.4,
            0.1
        );
    }
}