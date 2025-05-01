package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;

public class Thief implements CustomType {
    private final Random random;

    public Thief(Random random) {
        this.random = random;
    }

    @Override
    public String getId() {
        return "thief";
    }

    @Override
    public String getName() {
        return "Thief";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        zombie.setAdult();
        if (zombie.getEquipment() == null) return;

        zombie.getEquipment().setHelmet(new ItemStack(Material.DARK_OAK_FENCE_GATE), false);
        zombie.getEquipment().setHelmetDropChance(0);

        zombie.setCanPickupItems(false);
    }

    @Override
    public void onAttack(Zombie zombie, EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity attacked)) return;

        List<Zombie> nearbyZombies = zombie.getNearbyEntities(3, 1, 3).stream().filter(it -> it instanceof Zombie).map(it -> (Zombie) it).toList();
        if (nearbyZombies.size() > 3) return;

        EntityEquipment lEquip = attacked.getEquipment();
        if (lEquip == null) return;

        ItemStack item = lEquip.getItemInOffHand();
        lEquip.setItemInOffHand(null);

        if (item.getType() == Material.AIR) {
            item = lEquip.getItemInMainHand();
            lEquip.setItemInMainHand(null);
        }
        if (item.getType() == Material.AIR) return;

        Location location = attacked.getLocation();
        World world = attacked.getWorld();

        EntityEquipment zEquip = zombie.getEquipment();

        if (zEquip.getItemInMainHand().getType() == Material.AIR && random.nextInt(24) == 0) {
            zEquip.setItemInMainHand(item);
            zEquip.setItemInMainHandDropChance(1f);
        } else {
            world.dropItemNaturally(location, item);
        }

        world.playSound(location, Sound.ENTITY_CHICKEN_EGG, .5f, 1);
    }

    @Override
    public void onTarget(Zombie zombie, EntityTargetEvent e) {
        zombie.swingOffHand();
    }
}
