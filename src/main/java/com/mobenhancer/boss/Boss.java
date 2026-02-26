package com.mobenhancer.boss;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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

import java.util.ArrayList;
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
    protected long lastTargetTime = System.currentTimeMillis();

    private static final double MAX_TARGET_DISTANCE = 100.0;

    private final List<org.bukkit.Chunk> heldChunks = new ArrayList<>();

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

    protected boolean isValidTarget(Player player) {
        if (player == null || !player.isValid() || player.isDead()) return false;

        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return false;

        if (entity == null || !entity.isValid()) return false;

        if (!player.getWorld().equals(entity.getWorld())) return false;

        double distSq = player.getLocation().distanceSquared(entity.getLocation());
        return distSq <= MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE;
    }
    
    /**
     * Elimina el boss del mundo y la bossbar.
     */
    public void despawn() {
        
        releaseChunk();
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
            if (p == null || !p.getWorld().equals(bossLoc.getWorld())) {
                if (p != null) {
                    bossBar.removePlayer(p);
                }
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
    public void checkBossBarForPlayer(Player player, double range) {
        if (bossBar == null || entity == null || !entity.isValid()) return;
        if (entity.isDead() || !active) return;

        double distSq = player.getLocation().distanceSquared(entity.getLocation());
        if (distSq <= range * range) {
            bossBar.addPlayer(player);
        } else {
            bossBar.removePlayer(player);
        }
    }

    protected void markAsBoss(LivingEntity e) {
        NamespacedKey key = new NamespacedKey(plugin, "boss_type");
        e.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
    }

    /**
     * Fuerza la carga del chunk donde está el boss y los 8 chunks adyacentes.
     * Llamar desde spawn() después de asignar this.entity.
     * Previene que el boss pierda sus habilidades cuando el único jugador muere
     * y reespawnea lejos, descargando el chunk.
     */
    protected void holdChunk() {
        if (entity == null) return;
        World world = entity.getWorld();
        int cx = entity.getLocation().getChunk().getX();
        int cz = entity.getLocation().getChunk().getZ();

        // Mantener cargado el chunk central + los 8 adyacentes (radio 1)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.addPluginChunkTicket(cx + dx, cz + dz, plugin);
                heldChunks.add(world.getChunkAt(cx + dx, cz + dz));
            }
        }
    }

    /**
     * Libera los tickets de chunk retenidos por este boss.
     * Llamar desde despawn() para no mantener chunks cargados innecesariamente.
     */
    protected void releaseChunk() {
        if (entity == null) return;
        World world = entity.getWorld();
        for (org.bukkit.Chunk chunk : heldChunks) {
            try {
                world.removePluginChunkTicket(chunk.getX(), chunk.getZ(), plugin);
            } catch (Exception ex) {
                plugin.getLogger().warning("[Boss] Error liberando chunk ticket: "
                        + ex.getMessage());
            }
        }
        heldChunks.clear();
    }

    public UUID getEntityId() {
        return entityId;
    }

    public long getLastTargetTime() {
        return lastTargetTime;
    }

    public boolean isActive() {
        return active;
    }

    public String getId() {
        return id;
    }

    public LivingEntity getEntity() {
        return entity;
    }
    
    public void onShootBow(EntityShootBowEvent event) {}

    public void onProjectileHit(ProjectileHitEvent event) {}
}