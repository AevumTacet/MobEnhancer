package com.mobenhancer;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class BabyZombieControl implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBabyZombieSpawn(CreatureSpawnEvent event) {
        // Verificar si es un zombie o zombified piglin
        if (!(event.getEntity() instanceof Zombie)) return;
        
        Zombie zombie = (Zombie) event.getEntity();
        
        // Verificar si es bebé y tipo válido
        if (isValidBabyZombie(zombie)) {
            adjustZombieHealth(zombie);
        }
    }

    private boolean isValidBabyZombie(Zombie zombie) {
        // Verificar si ya es un tipo personalizado
        if (MobEnhancer.getInstance().getType(zombie) != null) {
            return false;
        }
        // Aceptar baby zombies normales y zombified piglin bebés
        return !zombie.isAdult() && (
            zombie.getType() == EntityType.ZOMBIE ||
            zombie.getType() == EntityType.ZOMBIFIED_PIGLIN
        );
    }

    private void adjustZombieHealth(Zombie zombie) {
        // Usar Attribute.GENERIC_MAX_HEALTH (correcto)
        double originalMaxHealth = zombie.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        
        // Reducir al 20% (ajustable)
        double newMaxHealth = originalMaxHealth * 0.20; 
        
        // Aplicar cambios
        zombie.getAttribute(Attribute.MAX_HEALTH).setBaseValue(newMaxHealth);
        zombie.setHealth(newMaxHealth); // Forzar salud actual al nuevo máximo
        
        // Debug (opcional)
        // zombie.setCustomName("HP: " + newMaxHealth);
        // zombie.setCustomNameVisible(true);
    }
}