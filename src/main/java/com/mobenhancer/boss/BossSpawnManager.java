package com.mobenhancer.boss;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.function.Function;

public class BossSpawnManager implements Listener {
    private final JavaPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, PendingSpawn> pendingSpawns = new HashMap<>(); // key = armor stand UUID
    private final Map<UUID, Boss> activeBosses = new HashMap<>();
    private final Map<String, Function<JavaPlugin, Boss>> bossConstructors = new HashMap<>();

    // Clave para marcar el armor stand como placeholder
    private final NamespacedKey placeholderKey;

    public BossSpawnManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.placeholderKey = new NamespacedKey(plugin, "boss_placeholder");
        registerBosses();
        startSpawningTask();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(new BossListener(activeBosses, plugin), plugin);
    }

    private void registerBosses() {
        bossConstructors.put("putrid_colossus", PutridColossus::new);
        bossConstructors.put("necromancer_skeleton", NecromancerSkeleton::new);
        //bossConstructors.put("enderman_overseer", EndermanOverseer::new);
        //bossConstructors.put("wandering_warden", WanderingWarden::new);
        //bossConstructors.put("nether_raid", NetherRaid::new);
    }

    private void startSpawningTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Comprobar si el spawn está habilitado
                if (!plugin.getConfig().getBoolean("boss-spawn.enabled", true)) return;

                int minPlayers = plugin.getConfig().getInt("boss-spawn.min-players", 2);
                if (Bukkit.getOnlinePlayers().size() < minPlayers) return;

                int maxConcurrent = plugin.getConfig().getInt("boss-spawn.max-concurrent", 1);
                if (activeBosses.size() >= maxConcurrent) return;

                double chance = plugin.getConfig().getDouble("boss-spawn.chance-per-second", 0.001);
                if (random.nextDouble() > chance) return;

                // Seleccionar jugador aleatorio
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                Player target = players.get(random.nextInt(players.size()));

                // Calcular ubicación de spawn
                Location spawnLoc = findSurfaceLocationNear(target.getLocation());
                if (spawnLoc == null) return;

                // Seleccionar un boss aleatorio
                String bossId = selectRandomBossId();
                if (bossId == null) return;

                // Broadcast
                String message = plugin.getConfig().getString("boss-spawn.broadcast-message",
                        "&cA abomination is about to appear at X: %x Y: %y Z: %z!");
                message = ChatColor.translateAlternateColorCodes('&',
                        message.replace("%x", String.valueOf(spawnLoc.getBlockX()))
                               .replace("%y", String.valueOf(spawnLoc.getBlockY()))
                               .replace("%z", String.valueOf(spawnLoc.getBlockZ())));
                Bukkit.broadcastMessage(message);

                // Crear placeholder
                createPlaceholder(spawnLoc, bossId);
            }
        }.runTaskTimer(plugin, 0L, 20L); // cada segundo
    }

    /**
     * Crea un armor stand invisible en la ubicación con partículas visibles.
     */
    private void createPlaceholder(Location location, String bossId) {
        World world = location.getWorld();
        ArmorStand stand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.setSmall(true);
        stand.setRemoveWhenFarAway(false);
        stand.setPersistent(true);
        // Marcar como placeholder
        stand.getPersistentDataContainer().set(placeholderKey, PersistentDataType.BYTE, (byte) 1);

        // Programar partículas visibles alrededor del armor stand
        BukkitTask particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!stand.isValid() || stand.isDead()) {
                    cancel();
                    return;
                }
                // Partículas de portal (moradas) para indicar lugar de aparición
                world.spawnParticle(Particle.PORTAL, stand.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.1);
                // Añadir algunas partículas de llamas o dragón breath para que sea más visible
                world.spawnParticle(Particle.DRAGON_BREATH, stand.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0);
            }
        }.runTaskTimer(plugin, 0L, 10L); // cada 0.5 segundos

        // Programar timeout (3 minutos)
        BukkitTask timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                removePlaceholder(stand.getUniqueId());
            }
        }.runTaskLater(plugin, 20L * 60 * 3); // 3 minutos en ticks

        PendingSpawn ps = new PendingSpawn(location, bossId, stand, particleTask, timeoutTask);
        pendingSpawns.put(stand.getUniqueId(), ps);
    }

    /**
     * Elimina el placeholder y cancela sus tareas.
     */
    private void removePlaceholder(UUID standId) {
        PendingSpawn ps = pendingSpawns.remove(standId);
        if (ps != null) {
            if (ps.stand != null && ps.stand.isValid()) {
                ps.stand.remove();
            }
            if (ps.particleTask != null) {
                ps.particleTask.cancel();
            }
            if (ps.timeoutTask != null) {
                ps.timeoutTask.cancel();
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location playerLoc = player.getLocation();

        // Iterar sobre una copia para evitar ConcurrentModification
        new ArrayList<>(pendingSpawns.entrySet()).forEach(entry -> {
            UUID standId = entry.getKey();
            PendingSpawn ps = entry.getValue();
            if (!ps.stand.isValid() || ps.stand.isDead()) {
                // Si el armor stand ya no es válido, lo limpiamos
                removePlaceholder(standId);
                return;
            }
            if (playerLoc.distanceSquared(ps.location) < 20 * 20) {
                // Jugador dentro del radio, spawnear boss
                spawnBoss(ps.location, ps.bossId);
                removePlaceholder(standId);
            }
        });
    }

    /**
     * Devuelve una lista con los IDs de los bosses habilitados en la configuración.
     */
    public List<String> getAvailableBossIds() {
        List<String> available = new ArrayList<>();
        for (String bossId : bossConstructors.keySet()) {
            if (plugin.getConfig().getBoolean("bosses." + bossId + ".enabled", true)) {
                available.add(bossId);
            }
        }
        return available;
    }

    /**
     * Spawnea un boss directamente en la ubicación indicada (para comandos).
     * @param location Ubicación donde aparecerá
     * @param bossId    Identificador del boss
     * @return true si el boss existe y se generó correctamente
     */
    public boolean spawnBoss(Location location, String bossId) {
        Function<JavaPlugin, Boss> constructor = bossConstructors.get(bossId);
        if (constructor == null) return false;
        
        Boss boss = constructor.apply(plugin);
        boss.spawn(location);
        activeBosses.put(boss.getEntityId(), boss);
        return true;
    }

    private String selectRandomBossId() {
        List<String> available = getAvailableBossIds();
        if (available.isEmpty()) return null;
        return available.get(random.nextInt(available.size()));
    }

    private Location findSurfaceLocationNear(Location center) {
        int minDist = plugin.getConfig().getInt("boss-spawn.distance-min", 100);
        int maxDist = plugin.getConfig().getInt("boss-spawn.distance-max", 200);
        World world = center.getWorld();

        for (int i = 0; i < 30; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            int dist = minDist + random.nextInt(maxDist - minDist + 1);
            int dx = (int) (Math.cos(angle) * dist);
            int dz = (int) (Math.sin(angle) * dist);
            int x = center.getBlockX() + dx;
            int z = center.getBlockZ() + dz;
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            if (isSafeSurface(loc)) return loc;
        }
        return null;
    }

    private boolean isSafeSurface(Location loc) {
        if (!loc.getBlock().isPassable()) return false;
        if (!loc.clone().add(0, -1, 0).getBlock().getType().isSolid()) return false;
        Material ground = loc.clone().add(0, -1, 0).getBlock().getType();
        return ground != Material.WATER && ground != Material.LAVA && ground != Material.MAGMA_BLOCK;
    }

    // Clase interna para almacenar datos del placeholder
    private static class PendingSpawn {
        Location location;
        String bossId;
        ArmorStand stand;
        BukkitTask particleTask;
        BukkitTask timeoutTask;

        PendingSpawn(Location loc, String bossId, ArmorStand stand, BukkitTask particleTask, BukkitTask timeoutTask) {
            this.location = loc;
            this.bossId = bossId;
            this.stand = stand;
            this.particleTask = particleTask;
            this.timeoutTask = timeoutTask;
        }
    }

    
    // Fuerza la creación de un placeholder cerca del jugador indicado,
    public boolean forceSpawnEvent(Player player, String bossId) {
        // Seleccionar boss
        String selectedId = (bossId != null) ? bossId : selectRandomBossId();
        if (selectedId == null || !bossConstructors.containsKey(selectedId)) {
            player.sendMessage("§cBoss ID no válido: " + bossId);
            return false;
        }

        // Buscar ubicación cerca del jugador, con distancia reducida para testing
        Location spawnLoc = findSurfaceLocationNearForTest(player.getLocation());
        if (spawnLoc == null) {
            player.sendMessage("§cNo se encontró una ubicación válida cercana.");
            return false;
        }

        // Broadcast igual que en el spawn automático
        String message = plugin.getConfig().getString("boss-spawn.broadcast-message",
                "&cA abomination is about to appear at X: %x Y: %y Z: %z!");
        message = ChatColor.translateAlternateColorCodes('&',
                message.replace("%x", String.valueOf(spawnLoc.getBlockX()))
                    .replace("%y", String.valueOf(spawnLoc.getBlockY()))
                    .replace("%z", String.valueOf(spawnLoc.getBlockZ())));
        Bukkit.broadcastMessage(message);

        createPlaceholder(spawnLoc, selectedId);

        player.sendMessage("§aPlaceholder creado en §e" +
                spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ() +
                " §apara boss §e" + selectedId);

        return true;
    }

    /**
     * Versión de findSurfaceLocationNear con distancia reducida para testing (10-30 bloques).
     */
    private Location findSurfaceLocationNearForTest(Location center) {
        World world = center.getWorld();
        for (int i = 0; i < 30; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            int dist = 10 + random.nextInt(20); // 10-30 bloques, visible pero no encima del jugador
            int dx = (int) (Math.cos(angle) * dist);
            int dz = (int) (Math.sin(angle) * dist);
            int x = center.getBlockX() + dx;
            int z = center.getBlockZ() + dz;
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            if (isSafeSurface(loc)) return loc;
        }
        return null;
    }

}