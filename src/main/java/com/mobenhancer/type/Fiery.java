package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public class Fiery implements CustomType {
    private final Random random;

    public Fiery(Random random) {
        this.random = random;
    }

    @Override
    public String getId() {
        return "fiery";
    }

    @Override
    public String getName() {
        return "Fiery";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        zombie.setAdult();
        if (zombie instanceof PigZombie) {
            e.setCancelled(true);
            return;
        }

        if (zombie.getEquipment() != null) {
            zombie.getEquipment().setHelmet(new ItemStack(Material.FLINT_AND_STEEL), false);
            zombie.getEquipment().setHelmetDropChance(0);
        }

        zombie.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 1, false, false));
        zombie.setVisualFire(true);
    }

    @Override
    public void onAttack(Zombie zombie, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p && p.isBlocking()) return;

        e.getEntity().setFireTicks(20 + 30 * (random.nextInt(4) + 1));
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 0.25f, 1);
    }
}
