package com.mobenhancer;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.entity.*;

public interface SkeletonCustomType {
    String getId();

    String getName();

    default void onSpawn(Skeleton skeleton, CreatureSpawnEvent e) {
    }

    default void onDamage(Skeleton skeleton, EntityDamageEvent e) {
    }

    default void whenAttacked(Skeleton skeleton, EntityDamageByEntityEvent e) {
    }

    default void onAttack(Skeleton skeleton, EntityDamageByEntityEvent e) {
    }

    default void onDeath(Skeleton skeleton, EntityDeathEvent e) {
    }

    default void onTarget(Skeleton skeleton, EntityTargetEvent e) {
    }

    // Nuevos métodos para manejar disparos y impactos de flechas
    default void onShootBow(Skeleton skeleton, EntityShootBowEvent e) {
    }

    default void onProjectileHit(Skeleton skeleton, ProjectileHitEvent e) {
    }

    default void incrAttribute(Skeleton skeleton, Attribute attribute, double v) {
        AttributeInstance i = skeleton.getAttribute(attribute);
        if (i != null)
            i.setBaseValue(i.getBaseValue() + v);
    }
}