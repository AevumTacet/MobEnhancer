package com.mobenhancer.type.zombie;

import com.mobenhancer.ZombieCustomType;
import com.mobenhancer.MobEnhancer;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public class Pillar implements ZombieCustomType {
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
            Zombie z = (Zombie) zombie.getWorld().spawn(zombie.getLocation(), zombie.getType().getEntityClass(),
                    it -> it.getPersistentDataContainer().set(MobEnhancer.zombieKey, PersistentDataType.STRING,
                            "default"));

            incrAttribute(z, Attribute.MAX_HEALTH, -10);

            last.addPassenger(z);
            last = z;
        }
    }

    @Override
    public void whenAttacked(Zombie zombie, EntityDamageByEntityEvent e) {
        if (!zombie.getPassengers().isEmpty())
            e.setDamage(e.getDamage() / 2);
    }
}
