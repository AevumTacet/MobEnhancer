package com.mobenhancer;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import java.util.*;

public class DespawnControl implements Listener {

    private final JavaPlugin plugin;
    private final Set<UUID> despawningMobs = new HashSet<>();
    private final List<EntityType> affectedTypes = Arrays.asList(
        EntityType.ZOMBIE,
        EntityType.SKELETON,
        EntityType.CREEPER,
        EntityType.SPIDER,
        EntityType.SILVERFISH
    );

    public DespawnControl(JavaPlugin plugin) {
        this.plugin = plugin;
        startDespawnTask();
    }

    private void startDespawnTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : plugin.getServer().getWorlds()) {
                    checkDespawnTime(world);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Verificación cada segundo
    }

    private void checkDespawnTime(World world) {
        long time = world.getFullTime() % 24000;
        if ((time >= 0 && time <= 600) || (time >= 12000 && time <= 12600)) {
            initiateMassDespawn(world);
        }
    }

    private void initiateMassDespawn(World world) {
        for (Entity entity : world.getEntities()) {
            if (shouldDespawn(entity)) {
                startDespawnProcess((LivingEntity) entity);
            }
        }
    }

    private boolean shouldDespawn(Entity entity) {
        if (!(entity instanceof Mob)) return false;
        Mob mob = (Mob) entity;
        
        return affectedTypes.contains(entity.getType()) &&
            !mob.isDead() &&
            mob.getCustomName() == null &&
            mob.getTarget() == null;
    }

    private void startDespawnProcess(LivingEntity mob) {
        UUID mobId = mob.getUniqueId();
        despawningMobs.add(mobId);
        
        new BukkitRunnable() {
            int ticks = 0;
        final double maxHealth = mob.getAttribute(Attribute.MAX_HEALTH).getValue();
            final double damagePerSecond = maxHealth / 4;

            @Override
            public void run() {
                if (!mob.isValid() || ticks >= 30) {
                    despawningMobs.remove(mobId);
                    cancel();
                    return;
                }

                // Efectos visuales
                spawnParticles(mob);
                
                // Aplicar daño
                mob.setHealth(Math.max(0, mob.getHealth() - damagePerSecond));
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 20L); // Daño cada segundo
    }

    private void spawnParticles(LivingEntity mob) {
        Location loc = mob.getLocation().add(0, 1, 0);
        mob.getWorld().spawnParticle(
            Particle.ANGRY_VILLAGER,
            loc,
            15,
            0.5,
            0.5,
            0.5,
            0.1
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (despawningMobs.contains(event.getEntity().getUniqueId())) {
            // Limpiar drops y experiencia
            event.getDrops().clear();
            event.setDroppedExp(0);
            
            // Eliminar equipamiento
            if (event.getEntity() instanceof LivingEntity) {
                LivingEntity mob = (LivingEntity) event.getEntity();
                if (mob.getEquipment() != null) {
                    mob.getEquipment().clear();
                }
            }
            
            // Eliminar del conjunto
            despawningMobs.remove(event.getEntity().getUniqueId());
        }
    }
}