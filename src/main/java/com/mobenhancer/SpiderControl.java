package com.mobenhancer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Particle;

import java.util.HashSet;
import java.util.Random;
import java.util.UUID;

public class SpiderControl implements Listener {
    private final HashSet<UUID> cooldown = new HashSet<>();
    private static final double SCALE = 1.3;
    private static final double JUMP_MULTIPLIER = 3.0;
    private static final int COOLDOWN_TICKS = 60;
    private final Random random = new Random();
    private static final double HATCHLING_CHANCE = 0.3;
    private final JavaPlugin plugin;
    public SpiderControl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSpiderSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.SPIDER) return;

        LivingEntity spider = (LivingEntity) event.getEntity();
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            spider.setMetadata("mature", new FixedMetadataValue(plugin, true));
            configureSpider(spider, SCALE, JUMP_MULTIPLIER);
        }
    }

    @EventHandler
    public void onSpiderDeath(EntityDeathEvent event) {
        if (random.nextDouble() > HATCHLING_CHANCE) return;
        if (!(event.getEntity() instanceof Spider spider)) return;
        if (!spider.hasMetadata("mature") || !spider.getMetadata("mature").get(0).asBoolean()) return;

        spawnHatchlingParticles(spider.getLocation());

        for (int i = 0; i < 3; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Spider hatchling = (Spider) spider.getWorld().spawnEntity(spider.getLocation(), EntityType.SPIDER);
                configureHatchling(hatchling);
            }, 10L * i);
        }
    }

    private void configureSpider(LivingEntity spider, double scale, double jumpMultiplier) {
        spider.getAttribute(Attribute.SCALE).setBaseValue(scale);
        spider.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(
            spider.getAttribute(Attribute.JUMP_STRENGTH).getDefaultValue() * jumpMultiplier
        );
        spider.getAttribute(Attribute.FALL_DAMAGE_MULTIPLIER).setBaseValue(-1);
    }

    private void configureHatchling(Spider hatchling) {
        hatchling.setMetadata("mature", new FixedMetadataValue(plugin, false));
        hatchling.getAttribute(Attribute.MAX_HEALTH).setBaseValue(8.0);
        hatchling.setHealth(8.0);
        hatchling.getAttribute(Attribute.SCALE).setBaseValue(0.65);
    }

    @EventHandler
    public void onSpiderAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Spider spider)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (cooldown.contains(spider.getUniqueId())) return;

        // Colocar telaraña (funcionalidad común para todas las arañas)
        placeCobweb(spider, target);
    }
    
    private void placeCobweb(Spider spider, LivingEntity target) {
        Location cobwebLoc = target.getLocation().add(0, 0.5, 0);
        
        if (cobwebLoc.getBlock().getType().isAir() || cobwebLoc.getBlock().isLiquid()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    cobwebLoc.getBlock().setType(Material.COBWEB);
                    manageCooldown(spider);
                }
            }.runTask(plugin);
        }
    }

    private void spawnHatchlingParticles(Location location) {
        
        location.getWorld().spawnParticle(
            Particle.ITEM_COBWEB,
            location.add(0, 0.5, 0),
            20,
            0.5,
            0.5,
            0.5,
            0
        );
    }

    private void manageCooldown(Spider spider) {
        cooldown.add(spider.getUniqueId());
        new BukkitRunnable() {
            @Override
            public void run() {
                cooldown.remove(spider.getUniqueId());
            }
        }.runTaskLater(plugin, COOLDOWN_TICKS);
    }
}