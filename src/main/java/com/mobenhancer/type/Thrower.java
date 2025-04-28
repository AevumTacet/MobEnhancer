package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import com.mobenhancer.MobEnhancer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Random;

public class Thrower implements CustomType {
    private final Random random;

    public Thrower(Random random) {
        this.random = random;
    }

    @Override
    public String getId() {
        return "thrower";
    }

    @Override
    public String getName() {
        return "Flinger";
    }
    

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        if (zombie.getEquipment() != null) {
            zombie.getEquipment().setHelmet(new ItemStack(Material.ANVIL), false);
            zombie.getEquipment().setHelmetDropChance(0);
        }

        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 1, false, false));
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 5, false, false));

        incrAttribute(zombie, Attribute.ATTACK_DAMAGE, 8);
        incrAttribute(zombie, Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, 100);
        incrAttribute(zombie, Attribute.KNOCKBACK_RESISTANCE, 100);
        incrAttribute(zombie, Attribute.SCALE, 0.2);
    }

    @Override
    public void onAttack(Zombie zombie, EntityDamageByEntityEvent e) { // TODO
        if (!(e.getEntity() instanceof LivingEntity l)) return;

        zombie.addPassenger(l);
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_HORSE_SADDLE, 1, 1);

        Bukkit.getScheduler().runTaskLater(MobEnhancer.getInstance(), () -> {
            if (!zombie.getPassengers().isEmpty() && zombie.getPassengers().getFirst() == l) {
                zombie.removePassenger(l);
                Bukkit.getScheduler().runTaskLater(MobEnhancer.getInstance(), () -> {
                    Vector d = zombie.getLocation().getDirection().normalize();
                    Vector v = d.multiply(1 + random.nextDouble(1.25)).setY(1 + random.nextDouble(0.25));

                    zombie.getWorld().playSound(zombie.getLocation(), Sound.BLOCK_PISTON_EXTEND, .75f, 1);

                    l.setVelocity(v);
                }, 1L);
            }
        }, 8L);
    }

    @Override
    public void whenAttacked(Zombie zombie, EntityDamageByEntityEvent e) {
        zombie.getWorld().playSound(zombie.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.25f, 1);
    }

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent e) {
        zombie.eject();
    }
}
