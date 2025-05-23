package com.mobenhancer;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.Listener;

public class IgnitionControl implements Listener {

    private final JavaPlugin plugin;

    public IgnitionControl(JavaPlugin plugin) {
        this.plugin = plugin;
        InitIgnitionTask();
    }

    private void InitIgnitionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : plugin.getServer().getWorlds()) {
                    if (IsSunnyDay(world)) {
                        processEntities(world);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private boolean IsSunnyDay(World world) {
        // Cálculo manual de si es de día (compatible con todas versiones)
        long time = world.getTime();
        boolean esDia = time >= 0 && time < 12300; // 0-12300 ticks = día
        return esDia && !world.isThundering() && !world.hasStorm();
    }

    private void processEntities(World world) {
        for (Entity entidad : world.getEntities()) {
            if (IsMobVulnerable(entidad)) {
                verifyandBurn((LivingEntity) entidad);
            }
        }
    }

    private boolean IsMobVulnerable(Entity entidad) {
        return entidad instanceof Zombie || 
               entidad instanceof Skeleton || 
               entidad instanceof Spider;
    }

    private void verifyandBurn(LivingEntity mob) {
        if (mob.isDead() || mob.isInWater()) return;
        
        Location ubicacion = mob.getLocation();
        int HighestBlock = ubicacion.getWorld().getHighestBlockYAt(
            ubicacion, 
            HeightMap.MOTION_BLOCKING
        );
        
        boolean SunExposed = ubicacion.getBlockY() >= HighestBlock;
        
        if (SunExposed) {
            // Aumentamos el tiempo de fuego para mayor efecto
            mob.setFireTicks(Math.max(mob.getFireTicks(), 200));
        }
    }
}