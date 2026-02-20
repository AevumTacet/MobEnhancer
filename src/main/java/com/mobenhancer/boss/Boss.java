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

    protected void updateBossBarViewers(double radius) {
        if (bossBar == null || entity == null || !entity.isValid() || entity.isDead()) return;

        Location bossLoc = entity.getLocation();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(bossLoc.getWorld())) {
                bossBar.removePlayer(p);
                continue;
            }
            // distanceSquared evita la raíz cuadrada, más eficiente
            boolean inRange = p.getLocation().distanceSquared(bossLoc) <= radius * radius;
            if (inRange) {
                bossBar.addPlayer(p); // idempotente: si ya está, no hace nada
            } else {
                bossBar.removePlayer(p);
            }
        }
    }

    /**
     * Comprueba si un jugador específico debe ver la bossbar y actúa en consecuencia.
     * Útil tras respawn o cambio de mundo.
     */
    public void checkBossBarForPlayer(Player player, double radius) {
        if (bossBar == null || entity == null || entity.isDead()) return;
        if (!player.getWorld().equals(entity.getWorld())) {
            bossBar.removePlayer(player);
            return;
        }

        boolean inRange = player.getLocation().distanceSquared(entity.getLocation()) <= radius * radius;
        if (inRange) {
            bossBar.addPlayer(player);
        } else {
            bossBar.removePlayer(player);
        }
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