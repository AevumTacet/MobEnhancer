package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Tank implements CustomType {
    @Override
    public String getId() {
        return "tank";
    }

    @Override
    public String getName() {
        return "Tanker";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        zombie.setAdult();
        if (zombie.getEquipment() != null) {
            zombie.getEquipment().setHelmet(new ItemStack(Material.ANVIL), false);
            zombie.getEquipment().setHelmetDropChance(0);
        }

        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 1, false, false));
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 2, false, false));

        incrAttribute(zombie, Attribute.ATTACK_DAMAGE, 15);
        incrAttribute(zombie, Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, 100);
        incrAttribute(zombie, Attribute.KNOCKBACK_RESISTANCE, 100);
        incrAttribute(zombie, Attribute.SCALE, 0.5);
    }

    @Override
    public void onAttack(Zombie zombie, EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p && p.isBlocking()) p.setVelocity(p.getVelocity().setY(.75));
    }

    @Override
    public void whenAttacked(Zombie zombie, EntityDamageByEntityEvent e) {
        zombie.getWorld().playSound(zombie.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.25f, 1);
    }
}
