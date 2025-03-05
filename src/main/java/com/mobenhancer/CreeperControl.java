package com.mobenhancer;

import org.bukkit.Location;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.HeightMap;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.HashMap;
import java.util.UUID;

public class CreeperControl implements Listener {

    private final HashMap<UUID, Location> lastLocations = new HashMap<>();
    private final JavaPlugin plugin;
    public CreeperControl(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Verificar si es un creeper y spawn natural
        if (event.getEntityType() != EntityType.CREEPER 
            || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }

        Location spawnLocation = event.getLocation();
        // Verificar condiciones de spawn
        if (!isUnderSurface(spawnLocation)) {
            event.setCancelled(true);
            return;
        }
        
        if (spawnLocation.getBlock().getLightLevel() > 0) {
            event.setCancelled(true);
            return;
        }

        // Iniciar sistema de seguimiento para invisibilidad
        trackCreeperMovement((Creeper) event.getEntity());
    }

    private void trackCreeperMovement(Creeper creeper) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!creeper.isValid()) {
                    lastLocations.remove(creeper.getUniqueId());
                    cancel();
                    return;
                }

                Location currentLoc = creeper.getLocation();
                Location lastLoc = lastLocations.get(creeper.getUniqueId());

                // Verificar movimiento
                if (lastLoc != null && currentLoc.distanceSquared(lastLoc) > 0.0001) { // 0.01 bloques^2
                    // Está moviéndose - hacer visible
                    creeper.removePotionEffect(PotionEffectType.INVISIBILITY);
                } else {
                    // Está quieto - hacer invisible
                    creeper.addPotionEffect(new PotionEffect(
                        PotionEffectType.INVISIBILITY, 
                        Integer.MAX_VALUE, 
                        1, 
                        false, // Sin partículas
                        false // No mostrar icono
                    ));
                }

                // Actualizar última posición
                lastLocations.put(creeper.getUniqueId(), currentLoc);
            }
        }.runTaskTimer(plugin, 0L, 20L); // Revisar cada segundo
    }

    private boolean isUnderSurface(Location location) {
        int surfaceY = location.getWorld().getHighestBlockYAt(
            location.getBlockX(), 
            location.getBlockZ(), 
            HeightMap.WORLD_SURFACE
        );
        return location.getBlockY() < surfaceY;
    }
}