package com.mobenhancer.boss;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

import java.util.Map;
import java.util.UUID;

public class BossListener implements Listener {
    private final Map<UUID, Boss> activeBosses;

    public BossListener(Map<UUID, Boss> activeBosses) {
        this.activeBosses = activeBosses;
    }

    @EventHandler
    public void onBossDamage(EntityDamageByEntityEvent event) {
        // Cuando el boss recibe daño
        Boss boss = activeBosses.get(event.getEntity().getUniqueId());
        if (boss != null) {
            boss.onDamage(event);
        }

        // Cuando el boss causa daño (ataque)
        Boss damager = activeBosses.get(event.getDamager().getUniqueId());
        if (damager != null) {
            damager.onDamage(event);
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        Boss boss = activeBosses.remove(event.getEntity().getUniqueId());
        if (boss != null) {
            boss.onDeath(event);
            boss.despawn(); // limpia bossbar y remueve entidad si es necesario
        }
    }

    @EventHandler
    public void onBossShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof LivingEntity shooter) {
            Boss boss = activeBosses.get(shooter.getUniqueId());
            if (boss != null) {
                boss.onShootBow(event);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity().getShooter() instanceof LivingEntity shooter) {
            Boss boss = activeBosses.get(shooter.getUniqueId());
            if (boss != null) {
                boss.onProjectileHit(event);
            }
        }
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        Boss boss = activeBosses.get(event.getEntity().getUniqueId());
        if (boss != null) {
            boss.onShootBow(event);
        }
    }
}