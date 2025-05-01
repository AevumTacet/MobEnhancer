package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public class Explosive implements CustomType {
    @Override
    public String getId() {
        return "explosive";
    }

    @Override
    public String getName() {
        return "Explosive";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        zombie.setAdult();
        
        if (zombie.getEquipment() == null) return;

        zombie.getEquipment().setHelmet(new ItemStack(Material.TNT), false);
        zombie.getEquipment().setHelmetDropChance(0);

        zombie.setLootTable(Bukkit.getLootTable(NamespacedKey.minecraft("entities/creeper")));
    }

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent e) {
        zombie.getWorld().createExplosion(zombie.getLocation(), 2f);
    }
}
