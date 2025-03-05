package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.Random;

public class Dash implements CustomType {
    private final Random random;

    public Dash(Random random) {
        this.random = random;
    }

    @Override
    public String getId() {
        return "dasher";
    }

    @Override
    public String getName() {
        return "Dasher";
    }

    @Override
    public void onAttack(Zombie zombie, EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p) || !p.isBlocking()) return;

        Vector pDir = p.getLocation().getDirection().setY(0).normalize();
        Vector kb;

        switch (random.nextInt(3)) {
            case 1 -> kb = new Vector(-pDir.getZ(), 0, pDir.getX()).multiply(0.8).add(pDir.clone().multiply(-0.4)); // Right
            case 2 -> kb = new Vector(pDir.getZ(), 0, -pDir.getX()).multiply(0.8).add(pDir.clone().multiply(-0.4)); // Left
            default -> kb = pDir.multiply(-1);
        }

        zombie.setVelocity(kb);
    }
}
