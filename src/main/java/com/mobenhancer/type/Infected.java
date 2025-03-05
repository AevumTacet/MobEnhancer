package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Infected implements CustomType {
    @Override
    public String getId() {
        return "infected";
    }

    @Override
    public String getName() {
        return "Infected";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.WEAVING, PotionEffect.INFINITE_DURATION, 2, false, false));
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.INFESTED, PotionEffect.INFINITE_DURATION, 6, false, true));
    }

    @Override
    public void onAttack(Zombie zombie, EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity l)) return;
        if (e.getEntity() instanceof Player p && p.isBlocking()) return;

        l.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 1, false, true));
        l.addPotionEffect(new PotionEffect(PotionEffectType.OOZING, 400, 1, false, true));
    }
}
