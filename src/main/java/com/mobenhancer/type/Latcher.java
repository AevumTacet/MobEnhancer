package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class Latcher implements CustomType {
    @Override
    public String getId() {
        return "latcher";
    }

    @Override
    public String getName() {
        return "Latcher";
    }

    @Override
    public void onAttack(Zombie zombie, EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity l)) return;

        l.addPassenger(zombie);
        if (!l.getPassengers().contains(zombie)) zombie.getWorld().playSound(zombie.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1, 1);
    }
}
