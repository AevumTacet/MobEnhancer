package com.mobenhancer.boss;

import com.mobenhancer.integration.CraftEngineHook;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
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
    private static final int    MAX_DROP_SLOTS      = 10;
    private static final Random dropRandom          = new Random();

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

    public abstract void spawn(Location location);

    public abstract void tick();

    public abstract void onDamage(EntityDamageByEntityEvent event);

    public abstract void onDeath(EntityDeathEvent event);

    // ══════════════════════════════════════════════════════════════════
    // SISTEMA DE DROPS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Lee los drops configurados para este boss en config.yml y los aplica al evento
     * de muerte. Reemplaza completamente los drops vanilla.
     *
     * Estructura esperada en config.yml:
     *
     *   bosses:
     *     enderman_overseer:
     *       drops:
     *         exp: 150
     *         items:
     *           - item: "minecraft:elytra"
     *             amount: 1
     *             chance: 1.0
     *           - item: "regnum:void_shard"
     *             amount: 2
     *             chance: 0.15
     *
     * Acepta hasta MAX_DROP_SLOTS (10) entradas. Las entradas adicionales se ignoran
     * con un warning en consola. Items que no se puedan resolver (CraftEngine no
     * disponible, material no existe) se saltan silenciosamente.
     *
     * Si la sección "drops" no existe en config.yml para este boss, el método
     * no modifica los drops del evento (comportamiento vanilla intacto).
     *
     * @param event El evento de muerte del boss
     */
    protected void rollDrops(EntityDeathEvent event) {
        String configPath = "bosses." + id + ".drops";
        ConfigurationSection dropsSection = plugin.getConfig()
                .getConfigurationSection(configPath);

        if (dropsSection == null) {
            // Sin configuración de drops para este boss — dejar los drops vanilla
            plugin.getLogger().warning("[Boss:" + id + "] No hay sección 'drops' en config.yml. "
                    + "Se usarán los drops vanilla.");
            return;
        }

        // Limpiar drops vanilla antes de añadir los nuestros
        event.getDrops().clear();

        // ── EXP ──────────────────────────────────────────────────────────────
        int expAmount = dropsSection.getInt("exp", 0);
        if (expAmount > 0) {
            event.setDroppedExp(expAmount);
        }

        // ── ITEMS ─────────────────────────────────────────────────────────────
        List<?> itemsList = dropsSection.getList("items");
        if (itemsList == null || itemsList.isEmpty()) return;

        if (itemsList.size() > MAX_DROP_SLOTS) {
            plugin.getLogger().warning("[Boss:" + id + "] Se configuraron " + itemsList.size()
                    + " drops pero el máximo es " + MAX_DROP_SLOTS
                    + ". Los slots adicionales serán ignorados.");
        }

        int slotsToProcess = Math.min(itemsList.size(), MAX_DROP_SLOTS);

        for (int i = 0; i < slotsToProcess; i++) {
            Object entry = itemsList.get(i);

            // Las listas de config.yml se deserializan como Map<String, Object>
            if (!(entry instanceof java.util.Map<?, ?> rawMap)) {
                plugin.getLogger().warning("[Boss:" + id + "] Slot de drop #" + i
                        + " tiene formato inválido — ignorado.");
                continue;
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) rawMap;

            // Leer campos del slot
            String itemId = getString(map, "item");
            int    amount = getInt(map,    "amount", 1);
            double chance = getDouble(map, "chance", 1.0);

            // Slot vacío o mal configurado
            if (itemId == null || itemId.isBlank() || itemId.equals("0")) continue;

            // Chance: 0.0 = nunca, 1.0 = siempre
            if (chance <= 0.0) continue;
            if (chance < 1.0 && dropRandom.nextDouble() > chance) continue;

            // Resolver el item (vanilla o CraftEngine)
            ItemStack stack = CraftEngineHook.resolveItem(itemId, Math.max(1, amount));
            if (stack == null) {
                // Warning ya emitido por CraftEngineHook si corresponde
                continue;
            }

            event.getDrops().add(stack);
        }
    }

    // ── Helpers para leer el Map de YAML con tipos seguros ────────────────────

    private String getString(java.util.Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private int getInt(java.util.Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val != null) {
            try { return Integer.parseInt(val.toString()); }
            catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    private double getDouble(java.util.Map<String, Object> map, String key, double defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val != null) {
            try { return Double.parseDouble(val.toString()); }
            catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    // ══════════════════════════════════════════════════════════════════
    // HELMET HELPER
    // ══════════════════════════════════════════════════════════════════

    /**
     * Resuelve el casco del boss desde config.yml.
     * Si hay un helmet configurado bajo "bosses.<bossId>.helmet", lo resuelve
     * via CraftEngineHook (soporta vanilla y CraftEngine).
     * Si no está configurado o no se puede resolver, devuelve el fallback.
     *
     * @param fallback ItemStack a usar si no hay configuración o falla la resolución
     * @return El ItemStack del casco a equipar
     */
    protected ItemStack resolveHelmet(ItemStack fallback) {
        String helmetId = plugin.getConfig().getString("bosses." + id + ".helmet");

        if (helmetId == null || helmetId.isBlank()) {
            return fallback;
        }

        ItemStack resolved = CraftEngineHook.resolveItem(helmetId);
        if (resolved == null) {
            plugin.getLogger().warning("[Boss:" + id + "] No se pudo resolver el helmet '"
                    + helmetId + "' — usando fallback.");
            return fallback;
        }

        return resolved;
    }

    // ══════════════════════════════════════════════════════════════════
    // TARGET
    // ══════════════════════════════════════════════════════════════════

    protected boolean isValidTarget(Player player) {
        if (player == null || !player.isValid() || player.isDead()) return false;

        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return false;

        if (entity == null || !entity.isValid()) return false;

        if (!player.getWorld().equals(entity.getWorld())) return false;

        double distSq = player.getLocation().distanceSquared(entity.getLocation());
        return distSq <= MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE;
    }

    // ══════════════════════════════════════════════════════════════════
    // DESPAWN
    // ══════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════
    // BOSSBAR
    // ══════════════════════════════════════════════════════════════════

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
                if (p != null) bossBar.removePlayer(p);
                continue;
            }
            boolean inRange = p.getLocation().distanceSquared(bossLoc) <= radius * radius;
            if (inRange) {
                bossBar.addPlayer(p);
            } else {
                bossBar.removePlayer(p);
            }
        }
    }

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

    // ══════════════════════════════════════════════════════════════════
    // CHUNK
    // ══════════════════════════════════════════════════════════════════

    protected void holdChunk() {
        if (entity == null) return;
        World world = entity.getWorld();
        int cx = entity.getLocation().getChunk().getX();
        int cz = entity.getLocation().getChunk().getZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.addPluginChunkTicket(cx + dx, cz + dz, plugin);
                heldChunks.add(world.getChunkAt(cx + dx, cz + dz));
            }
        }
    }

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

    // ══════════════════════════════════════════════════════════════════
    // MARK
    // ══════════════════════════════════════════════════════════════════

    protected void markAsBoss(LivingEntity e) {
        NamespacedKey key = new NamespacedKey(plugin, "boss_type");
        e.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
    }

    // ══════════════════════════════════════════════════════════════════
    // GETTERS
    // ══════════════════════════════════════════════════════════════════

    public UUID getEntityId()       { return entityId; }
    public long getLastTargetTime() { return lastTargetTime; }
    public boolean isActive()       { return active; }
    public String getId()           { return id; }
    public LivingEntity getEntity() { return entity; }

    // ══════════════════════════════════════════════════════════════════
    // EVENTOS (override opcional)
    // ══════════════════════════════════════════════════════════════════

    public void onShootBow(EntityShootBowEvent event) {}
    public void onProjectileHit(ProjectileHitEvent event) {}
}