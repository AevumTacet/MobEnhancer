package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Flash implements CustomType {
    @Override
    public String getId() {
        return "flash";
    }

    @Override
    public String getName() {
        return "Flash";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        if (zombie instanceof PigZombie) {
            e.setCancelled(true);
            return;
        }

        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 4, false, false));
        incrAttribute(zombie, Attribute.WATER_MOVEMENT_EFFICIENCY, 1.5);
    }
}
