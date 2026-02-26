package com.mobenhancer.boss;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public class BossListener implements Listener {
    private final Map<UUID, Boss> activeBosses;

    private final JavaPlugin plugin;

    public BossListener(Map<UUID, Boss> activeBosses, JavaPlugin plugin) {
        this.activeBosses = activeBosses;
        this.plugin = plugin;
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

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossTeleport(EntityTeleportEvent event) {
        if (activeBosses.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        Boss boss = activeBosses.get(event.getEntity().getUniqueId());
        if (boss != null) {
            boss.onShootBow(event);
        }
    }

    @EventHandler
    public void onFallingBlockLand(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fb)) return;
        // Verificar si es un block throw del Overseer mediante metadata
        if (!fb.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "overseer_block_throw"),
                PersistentDataType.BYTE)) return;

        // Notificar al boss correspondiente
        for (Boss boss : activeBosses.values()) {
            if (boss instanceof EndermanOverseer overseer) {
                overseer.onBlockThrowLand(event.getBlock().getLocation().clone()
                        .add(0.5, 1, 0.5));
                break;
            }
        }
    }

    @EventHandler
    public void onBossEnvironmentalDamage(EntityDamageEvent event) {
        Boss boss = activeBosses.get(event.getEntity().getUniqueId());
        if (boss != null && event.getCause() == EntityDamageEvent.DamageCause.DROWNING) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Boss boss : activeBosses.values()) {
                boss.checkBossBarForPlayer(event.getPlayer(), 50.0);
            }
        }, 40L); // 2 segundos de delay
    }
}