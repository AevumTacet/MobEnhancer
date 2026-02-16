package com.mobenhancer;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;

public class Listen implements Listener {
    private final Random random;
    private final MobEnhancer instance;

    private static final EnumSet<EntityType> REPLACEABLE = EnumSet.of(
            EntityType.CREEPER, EntityType.SPIDER, EntityType.WITCH, EntityType.STRAY);

    public Listen(Random random, MobEnhancer instance) {
        this.random = random;
        this.instance = instance;
    }

    @EventHandler
    public void spawn(CreatureSpawnEvent e) {
        Bukkit.getScheduler().runTaskLater(instance, () -> {
            Entity entity = e.getEntity();
            World world = entity.getWorld();

            // Lógica existente de reemplazo
            if ((instance.getConfig().getBoolean("endSpawn") && world.getEnvironment() == World.Environment.THE_END
                    && entity.getType() == EntityType.ENDERMAN && random.nextInt(8) == 0)
                    || (instance.getConfig().getBoolean("replaceHostiles") && REPLACEABLE.contains(entity.getType()))) {
                world.spawnEntity(entity.getLocation(), EntityType.ZOMBIE);
                e.setCancelled(true);
                return;
            }

            // Manejar Zombies
            if (entity instanceof Zombie zombie) {
                // Si ya es un boss, no asignar tipo
                NamespacedKey bossKey = new NamespacedKey(instance, "boss_type");
                if (zombie.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING)) {
                    return; // Salir, no hacer nada
                }

                ZombieCustomType type = instance.getZombieType(zombie);
                if (type == null)
                    type = instance.getRandomZombieType();
                instance.setZombieType(zombie, type);

                type.onSpawn(zombie, e);
                if (instance.getConfig().getBoolean("displayType") && !type.getName().isEmpty()) {
                    zombie.setCustomName(type.getName());
                }

                // Zombified Piglin
                /* if (instance.getConfig().getBoolean("zombifiedPiglinAttack") && zombie instanceof PigZombie pigZombie) {
                    pigZombie.setAngry(true);
                } */
            }
            // Manejar Skeletons (nuevo)
            else if (entity instanceof Skeleton skeleton) {
                SkeletonCustomType type = instance.getSkeletonType(skeleton);
                if (type == null)
                    type = instance.getRandomSkeletonType();
                instance.setSkeletonType(skeleton, type);

                type.onSpawn(skeleton, e);
                if (instance.getConfig().getBoolean("displayType") && !type.getName().isEmpty()) {
                    skeleton.setCustomName(type.getName());
                }
            }
        }, 2L); // Delay de 2 ticks
    }

    // Eventos para Zombies (mantener existente)

    @EventHandler
    public void target(EntityTargetEvent e) {
        if (e.getEntity() instanceof Zombie z) {
            ZombieCustomType type = instance.getZombieType(z);
            if (type != null)
                type.onTarget(z, e);
        } else if (e.getEntity() instanceof Skeleton s) {
            SkeletonCustomType type = instance.getSkeletonType(s);
            if (type != null)
                type.onTarget(s, e);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void dmg(EntityDamageByEntityEvent e) {
        // Zombies como atacantes
        if (e.getDamager() instanceof Zombie z) {
            ZombieCustomType type = instance.getZombieType(z);
            if (type != null)
                type.onAttack(z, e);
        }
        // Skeletons como atacantes (nuevo)
        else if (e.getDamager() instanceof Skeleton s) {
            SkeletonCustomType type = instance.getSkeletonType(s);
            if (type != null)
                type.onAttack(s, e);
        }

        // Zombies como víctimas
        if (e.getEntity() instanceof Zombie z) {
            ZombieCustomType type = instance.getZombieType(z);
            if (type != null)
                type.whenAttacked(z, e);
        }
        // Skeletons como víctimas (nuevo)
        else if (e.getEntity() instanceof Skeleton s) {
            SkeletonCustomType type = instance.getSkeletonType(s);
            if (type != null)
                type.whenAttacked(s, e);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void kill(EntityDeathEvent e) {
        if (e.getEntity() instanceof Zombie z) {
            ZombieCustomType type = instance.getZombieType(z);
            if (type != null)
                type.onDeath(z, e);
        } else if (e.getEntity() instanceof Skeleton s) {
            SkeletonCustomType type = instance.getSkeletonType(s);
            if (type != null)
                type.onDeath(s, e);
        }
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Skeleton skeleton) {
            SkeletonCustomType type = instance.getSkeletonType(skeleton);
            if (type != null) {
                type.onShootBow(skeleton, e);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        ProjectileSource source = e.getEntity().getShooter();
        if (source instanceof Skeleton skeleton) {
            SkeletonCustomType type = instance.getSkeletonType(skeleton);
            if (type != null) {
                type.onProjectileHit(skeleton, e);
            }
        }
    }
}