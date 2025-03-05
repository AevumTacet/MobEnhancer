package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class WindBuster implements CustomType {
    @Override
    public String getId() {
        return "busted";
    }

    @Override
    public String getName() {
        return "Wind Busted";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.WIND_CHARGED, PotionEffect.INFINITE_DURATION, 1, false, false));

        incrAttribute(zombie, Attribute.GRAVITY, -.04);
        incrAttribute(zombie, Attribute.STEP_HEIGHT, 0.4);
        incrAttribute(zombie, Attribute.SAFE_FALL_DISTANCE, 3);
    }

    @Override
    public void whenAttacked(Zombie zombie, EntityDamageByEntityEvent e) {
        zombie.getWorld().spawn(zombie.getLocation().add(0, 0.25, 0), WindCharge.class, AbstractWindCharge::explode);
    }
}
