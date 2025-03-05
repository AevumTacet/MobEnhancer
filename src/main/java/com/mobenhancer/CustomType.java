package com.mobenhancer;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.*;

public interface CustomType {
    String getId();
    String getName();

    default void onSpawn(Zombie zombie, CreatureSpawnEvent e) {}
    default void onDamage(Zombie zombie, EntityDamageEvent e) {}
    default void whenAttacked(Zombie zombie, EntityDamageByEntityEvent e) {}
    default void onAttack(Zombie zombie, EntityDamageByEntityEvent e) {}
    default void onDeath(Zombie zombie, EntityDeathEvent e) {}
    default void onTarget(Zombie zombie, EntityTargetEvent e) {}

    // Increments values to existing attributes.
    default void incrAttribute(Zombie zombie, Attribute attribute, double v) {
        AttributeInstance i = zombie.getAttribute(attribute);
        if (i != null) i.setBaseValue(i.getBaseValue() + v);
    }
}
