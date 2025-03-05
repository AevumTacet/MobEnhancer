package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class Giant implements CustomType {
    @Override
    public String getId() {
        return "giant";
    }

    @Override
    public String getName() {
        return "Giant";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        if (!zombie.getLocation().add(0,8,0).getBlock().getType().isAir()) {
            e.setCancelled(true);
            return;
        }

        zombie.setAdult();
        zombie.setRemoveWhenFarAway(true);

        incrAttribute(zombie, Attribute.SCALE, 2);
        incrAttribute(zombie, Attribute.JUMP_STRENGTH, .2);
        incrAttribute(zombie, Attribute.BURNING_TIME, -.2);
        incrAttribute(zombie, Attribute.ATTACK_DAMAGE, 5);
        incrAttribute(zombie, Attribute.ATTACK_KNOCKBACK, 1.5);
        incrAttribute(zombie, Attribute.ARMOR, 0.75);
        incrAttribute(zombie, Attribute.FOLLOW_RANGE, 16);
        incrAttribute(zombie, Attribute.SAFE_FALL_DISTANCE, 6);
        incrAttribute(zombie, Attribute.STEP_HEIGHT, 2);
        incrAttribute(zombie, Attribute.WATER_MOVEMENT_EFFICIENCY, 1);
    }
}
