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
        // Aceptar baby zombies normales y zombified piglin bebés
        return !zombie.isAdult() && (
            zombie.getType() == EntityType.ZOMBIE ||
            zombie.getType() == EntityType.ZOMBIFIED_PIGLIN
        );
    }

    private void adjustZombieHealth(Zombie zombie) {
        // Obtener salud máxima original
        double originalMaxHealth = zombie.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        
        // Calcular nueva salud (1/3 de la original)
        double newMaxHealth = originalMaxHealth * 0.25;
        
        // Aplicar cambios
        zombie.getAttribute(Attribute.MAX_HEALTH).setBaseValue(newMaxHealth);
        zombie.setHealth(Math.min(zombie.getHealth(), newMaxHealth));  // Ajustar salud actual
        
    }
}