package com.mobenhancer;

import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;

import java.util.*;

public class Listen implements Listener {
    private final Random random;
    private final MobEnhancer instance;

    private static final EnumSet<EntityType> REPLACEABLE = EnumSet.of(EntityType.CREEPER, EntityType.SPIDER, EntityType.WITCH, EntityType.STRAY);

    public Listen(Random random, MobEnhancer instance) {
        this.random = random;
        this.instance = instance;
    }

    @EventHandler
    public void spawn(CreatureSpawnEvent e) {
        Entity entity = e.getEntity();
        World world = entity.getWorld();

        // Extra Spawns || Entity Replace
        if ((instance.getConfig().getBoolean("endSpawn") && world.getEnvironment() == World.Environment.THE_END && entity.getType() == EntityType.ENDERMAN && random.nextInt(8) == 0) || (instance.getConfig().getBoolean("replaceHostiles") && REPLACEABLE.contains(entity.getType()))) {
            world.spawnEntity(entity.getLocation(), EntityType.ZOMBIE);
            e.setCancelled(true);
            return;
        }

        if (entity instanceof Zombie zombie) {
            CustomType type = instance.getType(zombie);
            if (type == null) type = instance.getRandomType();
            instance.setType(zombie, type);

            type.onSpawn(zombie, e);
            if (instance.getConfig().getBoolean("displayType") && !type.getName().isEmpty()) zombie.setCustomName(type.getName());

            // Zombified Piglin random auto target
            if (instance.getConfig().getBoolean("zombifiedPiglinAttack") && zombie instanceof PigZombie pigZombie) pigZombie.setAngry(true);
        }
    }

    @EventHandler
    public void combust(EntityCombustEvent e) {
        if (instance.getConfig().getBoolean("disableCombustion") && e.getEntity() instanceof Zombie) e.setCancelled(true);
    }

    @EventHandler
    public void target(EntityTargetEvent e) {
        if (!(e.getEntity() instanceof Zombie z)) return;

        CustomType type = instance.getType(z);
        if (type != null) type.onTarget(z, e);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void dmg(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Zombie z) {
            CustomType type = instance.getType(z);
            if (type != null) type.onAttack(z, e);
        }


        if (e.getEntity() instanceof Zombie z) {
            CustomType type = instance.getType(z);
            if (type != null) type.whenAttacked(z, e);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void kill(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Zombie z)) return;

        CustomType type = instance.getType(z);
        if (type != null) type.onDeath(z, e);
    }
}
