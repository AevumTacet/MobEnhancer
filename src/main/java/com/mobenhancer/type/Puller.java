package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import com.mobenhancer.MobEnhancer;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class Puller implements CustomType {
    @Override
    public String getId() {
        return "puller";
    }

    @Override
    public String getName() {
        return "Puller";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        zombie.setAdult();
    }

    @Override
    public void onAttack(Zombie zombie, EntityDamageByEntityEvent e) {
        Vector direction = e.getEntity().getLocation().toVector().subtract(zombie.getLocation().toVector());

        Bukkit.getScheduler().runTaskLater(MobEnhancer.getInstance(), () -> {
            e.getEntity().setVelocity(direction.multiply(-.75));
            zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_SLIME_JUMP, 0.3f, 1);

            if (!(e.getEntity() instanceof LivingEntity l)) return;

            l.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 8, 2, false, false));
            zombie.getWorld().spawnParticle(Particle.DUST_PLUME, l.getLocation().add(0, 1, 0), 6);
        }, 5L);
    }
}
