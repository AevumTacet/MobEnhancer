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
        if (event.getEntityType() != EntityType.CREEPER 
            || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }

        Location spawnLoc = event.getLocation();
        
        // Verificar condiciones de spawn mejoradas
        if (!isValidCaveSpawn(spawnLoc)) {
            event.setCancelled(true);
            return;
        }

        trackCreeperMovement((Creeper) event.getEntity());
    }

    private boolean isValidCaveSpawn(Location loc) {
        // 1. Debe estar bajo el terreno sólido (ignorando vegetación)
        int solidSurfaceY = loc.getWorld().getHighestBlockYAt(
            loc.getBlockX(), 
            loc.getBlockZ(), 
            HeightMap.MOTION_BLOCKING_NO_LEAVES // Ignora hojas y vegetación
        );
        
        // 2. Debe tener al menos 3 bloques de profundidad desde la superficie
        boolean deepEnough = (solidSurfaceY - loc.getBlockY()) >= 3;
        
        // 3. Sin luz ambiental ni de sky (total oscuridad)
        boolean totalDarkness = loc.getBlock().getLightLevel() == 0;
        
        // 4. No exposición al cielo (verificación vertical)
        boolean skyExposed = loc.getWorld().getHighestBlockYAt(loc, HeightMap.MOTION_BLOCKING) <= loc.getBlockY();
        
        return deepEnough && totalDarkness && !skyExposed;
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

                Location currentLoc = creeper.getLocation().clone();
                Location lastLoc = lastLocations.get(creeper.getUniqueId());

                if (lastLoc != null) {
                    double distance = currentLoc.distanceSquared(lastLoc);
                    
                    if (distance > 0.0025) { // Equivale a 0.05 bloques de movimiento
                        creeper.removePotionEffect(PotionEffectType.INVISIBILITY);
                    } else {
                        applyInvisibility(creeper);
                    }
                } else {
                    applyInvisibility(creeper);
                }

                lastLocations.put(creeper.getUniqueId(), currentLoc);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void applyInvisibility(Creeper creeper) {
        creeper.addPotionEffect(new PotionEffect(
            PotionEffectType.INVISIBILITY, 
            25, // 1.25 segundos (renovable)
            1, 
            false, 
            false
        ));
    }
}