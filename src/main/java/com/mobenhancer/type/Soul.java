package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.OminousItemSpawner;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Soul implements CustomType {
    private static final List<ItemStack> soulItems = new ArrayList<>();
    private final Random random;

    static {
        soulItems.add(splashPot(PotionType.HARMING));
        soulItems.add(splashPot(PotionType.SLOWNESS));
        soulItems.add(new ItemStack(Material.FIRE_CHARGE));
        soulItems.add(new ItemStack(Material.ARROW));
    }

    public Soul(Random random) {
        this.random = random;
    }

    @Override
    public String getId() {
        return "soul";
    }

    @Override
    public String getName() {
        return "Soul";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        EntityEquipment zEquip = zombie.getEquipment();
        if (zEquip != null) {
            zEquip.setHelmet(new ItemStack(Material.ZOMBIE_HEAD), false);
            if (zombie.getType() == EntityType.ZOMBIFIED_PIGLIN) zEquip.setHelmet(new ItemStack(Material.PIGLIN_HEAD), false);

            zEquip.setHelmetDropChance(0);
            zEquip.setItemInMainHand(null);
            zEquip.setItemInOffHand(null);
        }

        zombie.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 1, false, false));
        zombie.setCollidable(false);
        zombie.setSilent(true);
        zombie.setLootTable(null);
    }

    @Override
    public void whenAttacked(Zombie zombie, EntityDamageByEntityEvent e) {
        Location dropperSpawn = e.getDamager().getLocation().add(random.nextDouble() * 1.5 - 0.75, 3 + random.nextInt(4), random.nextDouble() * 1.5 - 0.75);

        if (!dropperSpawn.getWorld().getBlockAt(dropperSpawn).isEmpty()) dropperSpawn = e.getDamager().getLocation().add(0, 1.5, 0);

        e.getDamager().getWorld().spawn(dropperSpawn, OminousItemSpawner.class, it -> {
            it.setItem(soulItems.get(random.nextInt(soulItems.size())));
            it.setSpawnItemAfterTicks(40);
        });
    }

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent e) {
        World w = zombie.getWorld();

        w.spawnParticle(Particle.LARGE_SMOKE, zombie.getLocation().add(0, 1, 0), 1); // TODO
        w.playSound(zombie.getLocation(), Sound.ENTITY_VEX_DEATH, 0.25f, 1);
    }

    private static ItemStack splashPot(PotionType potionType) {
        ItemStack pot = new ItemStack(Material.SPLASH_POTION);
        PotionMeta potMeta = (PotionMeta) pot.getItemMeta();

        if (potMeta != null) potMeta.setBasePotionType(potionType);
        pot.setItemMeta(potMeta);

        return pot;
    }
}
