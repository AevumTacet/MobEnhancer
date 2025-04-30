package com.mobenhancer;

import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Random;

public class HordeControl implements Listener {
    @SuppressWarnings("unused")
    private final JavaPlugin plugin;
    private final Random random = new Random();
    private static final double CALL_RADIUS = 60.0;
    private static final double CALL_CHANCE = 0.25;

    public HordeControl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Zombie) && 
            !(event.getEntity() instanceof Skeleton)) return;
            
        if (!(event.getTarget() instanceof Player)) return;

        Mob caller = (Mob) event.getEntity(); // Usar Mob en lugar de LivingEntity
        Player targetPlayer = (Player) event.getTarget();

        if (random.nextDouble() > CALL_CHANCE) return;

        playCallSound(caller);
        alertNearbyMobs(caller, targetPlayer);
    }

    private void playCallSound(Mob caller) {
        caller.getWorld().playSound(
            caller.getLocation(),
            Sound.ENTITY_BLAZE_DEATH,
            2.0f,
            0.3f
        );
    }

    private void alertNearbyMobs(Mob caller, Player target) {
        Class<? extends Mob> mobType = caller instanceof Zombie ? 
            Zombie.class : Skeleton.class;

        caller.getWorld().getNearbyEntities(caller.getLocation(), CALL_RADIUS, CALL_RADIUS/2, CALL_RADIUS)
            .stream()
            .filter(e -> mobType.isInstance(e)) // Filtrar por tipo
            .map(e -> (Mob) e) // Castear a Mob
            .forEach(mob -> {
                if (mob.getTarget() == null || 
                   (mob.getTarget() instanceof Player && 
                   !mob.getTarget().getUniqueId().equals(target.getUniqueId()))) {
                    
                    mob.setTarget(target); // Usar setTarget de Mob
                }
            });
    }
}