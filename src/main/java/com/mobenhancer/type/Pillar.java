package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import com.mobenhancer.MobEnhancer;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public class Pillar implements CustomType {
    @Override
    public String getId() {
        return "pillar";
    }

    @Override
    public String getName() {
        return "Pillar";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        Zombie last = zombie;
        for (int i = 0; i < 2; i++) {
            Zombie z = (Zombie) zombie.getWorld().spawn(zombie.getLocation(), zombie.getType().getEntityClass(), it -> it.getPersistentDataContainer().set(MobEnhancer.key, PersistentDataType.STRING, "default"));

            incrAttribute(z, Attribute.MAX_HEALTH, -10);

            last.addPassenger(z);
            last = z;
        }
    }

    @Override
    public void whenAttacked(Zombie zombie, EntityDamageByEntityEvent e) {
        if (!zombie.getPassengers().isEmpty()) e.setDamage(e.getDamage() / 2);
    }
}
