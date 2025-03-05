package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public class Ninja implements CustomType {
    final Random random;

    public Ninja(Random random) {
        this.random = random;
    }

    @Override
    public String getId() {
        return "ninja";
    }

    @Override
    public String getName() {
        return "Ninja";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        if (zombie instanceof PigZombie) {
            e.setCancelled(true);
            return;
        }
        if (zombie.getEquipment() != null) {
            zombie.getEquipment().setHelmet(new ItemStack(Material.PUMPKIN), false);
            zombie.getEquipment().setHelmetDropChance(0);
        }

        zombie.setAdult();
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false, false));

        incrAttribute(zombie, Attribute.WATER_MOVEMENT_EFFICIENCY, 3);
    }

    @Override
    public void onAttack(Zombie zombie, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p && p.isBlocking()) return;

        zombie.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30, 1, false, false));
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 4, false, false));

        if (random.nextInt(4) == 0) {
            if (!(e.getEntity() instanceof LivingEntity l)) return;

            l.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 1, false, true));
            zombie.getWorld().playSound(zombie, Sound.ENTITY_SPLASH_POTION_BREAK, 1, 1);
        }
    }
}
