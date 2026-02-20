package com.mobenhancer.boss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public abstract class Boss {
    protected final JavaPlugin plugin;
    protected final String id;
    protected final String displayName;
    protected final double maxHealth;
    protected LivingEntity entity;
    protected BossBar bossBar;
    protected boolean active = false;
    protected UUID entityId;

    public Boss(JavaPlugin plugin, String id, String displayName, double maxHealth) {
        this.plugin = plugin;
        this.id = id;
        this.displayName = displayName;
        this.maxHealth = maxHealth;
    }

    public List<LivingEntity> getEntities() {
        return entity != null ? Collections.singletonList(entity) : Collections.emptyList();
    }

    /**
     * Spawnea la entidad principal del boss en la ubicación dada.
     * Debe configurar atributos, marcarla con PersistentDataContainer,
     * inicializar la bossbar y comenzar las tareas periódicas.
     */
    public abstract void spawn(Location location);

    /**
     * Llamado cada segundo (o según la frecuencia definida) para habilidades continuas,
     * actualización de la bossbar, etc.
     */
    public abstract void tick();

    /**
     * Maneja el evento de daño a la entidad del boss.
     */
    public abstract void onDamage(EntityDamageByEntityEvent event);

    /**
     * Maneja la muerte del boss: drops configurados, limpieza.
     */
    public abstract void onDeath(EntityDeathEvent event);

    /**
     * Elimina el boss del mundo y la bossbar.
     */
    public void despawn() {
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
        if (bossBar != null) {
            bossBar.removeAll();
        }
        active = false;
    }

    protected void initBossBar() {
        bossBar = Bukkit.createBossBar(displayName, BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setProgress(1.0);
        bossBar.setVisible(true);
    }

    protected void updateBossBar() {
        if (entity != null && bossBar != null && !entity.isDead()) {
            bossBar.setProgress(entity.getHealth() / maxHealth);
        }
    }

    /**
     * Añade la bossbar a todos los jugadores dentro de un radio (ej. 50 bloques).
     */
    protected void updateBossBarViewers(double radius) {
        if (bossBar == null || entity == null) return;
        // Quitar jugadores que ya no estén cerca
        bossBar.getPlayers().forEach(p -> {
            if (p.getLocation().distance(entity.getLocation()) > radius) {
                bossBar.removePlayer(p);
            }
        });
        // Añadir nuevos jugadores cercanos
        entity.getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .forEach(p -> {
                    if (!bossBar.getPlayers().contains(p)) {
                        bossBar.addPlayer(p);
                    }
                });
    }

    protected void markAsBoss(LivingEntity e) {
        NamespacedKey key = new NamespacedKey(plugin, "boss_type");
        e.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void onShootBow(EntityShootBowEvent event) {}
    public void onProjectileHit(ProjectileHitEvent event) {}
}