package com.mobenhancer.boss;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.function.Function;

public class BossSpawnManager implements Listener {
    private final JavaPlugin plugin;
    private final Random random = new Random();
    private final List<PendingSpawn> pendingSpawns = new ArrayList<>();
    private final Map<UUID, Boss> activeBosses = new HashMap<>();
    private final Map<String, Function<JavaPlugin, Boss>> bossConstructors = new HashMap<>();

    public BossSpawnManager(JavaPlugin plugin) {
        this.plugin = plugin;
        registerBosses();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(new BossListener(activeBosses), plugin);

        if (plugin.getConfig().getBoolean("boss-spawn.enabled", true)) {
            startSpawningTask();
        }
    }

    private void registerBosses() {
        bossConstructors.put("putrid_colossus", PutridColossus::new);
        bossConstructors.put("bonetower", Bonetower::new);
       // bossConstructors.put("necromancer_skeleton", NecromancerSkeleton::new);
       // bossConstructors.put("enderman_overseer", EndermanOverseer::new);
       // bossConstructors.put("wandering_warden", WanderingWarden::new);
      //  bossConstructors.put("nether_raid", NetherRaid::new);
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

                // Broadcast
                String message = plugin.getConfig().getString("boss-spawn.broadcast-message",
                        "&cA abomination is about to appear at X: %x Y: %y Z: %z!");
                message = ChatColor.translateAlternateColorCodes('&',
                        message.replace("%x", String.valueOf(spawnLoc.getBlockX()))
                               .replace("%y", String.valueOf(spawnLoc.getBlockY()))
                               .replace("%z", String.valueOf(spawnLoc.getBlockZ())));
                Bukkit.broadcastMessage(message);

                // Añadir a pendientes (expira en 60 segundos)
                long expiry = System.currentTimeMillis() + 60000;
                pendingSpawns.add(new PendingSpawn(spawnLoc, expiry));
            }
        }.runTaskTimer(plugin, 0L, 20L); // cada segundo
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
        // Comprobación básica: bloque de abajo sólido, espacio para la entidad
        if (!loc.getBlock().isPassable()) return false;
        if (!loc.clone().add(0, -1, 0).getBlock().getType().isSolid()) return false;
        // Evitar agua/lava
        Material ground = loc.clone().add(0, -1, 0).getBlock().getType();
        return ground != Material.WATER && ground != Material.LAVA && ground != Material.MAGMA_BLOCK;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        Iterator<PendingSpawn> it = pendingSpawns.iterator();
        while (it.hasNext()) {
            PendingSpawn ps = it.next();
            if (ps.expiry < System.currentTimeMillis()) {
                it.remove();
                continue;
            }
            if (p.getLocation().distance(ps.location) < 30) {
                spawnBoss(ps.location);
                it.remove();
                break;
            }
        }
    }

    private void spawnBoss(Location location) {
        // Seleccionar un boss aleatorio según pesos (aquí simplificado: el primero habilitado)
        String bossId = selectRandomBoss();
        if (bossId == null) return;

        Function<JavaPlugin, Boss> constructor = bossConstructors.get(bossId);
        if (constructor == null) return;

        Boss boss = constructor.apply(plugin);
        boss.spawn(location);
        activeBosses.put(boss.getEntityId(), boss);
    }

    private String selectRandomBoss() {
        // Leer de la configuración los bosses habilitados con sus pesos
        // Por simplicidad, devolvemos "putrid_colossus" si está habilitado
        if (plugin.getConfig().getBoolean("bosses.putrid_colossus.enabled", true))
            return "putrid_colossus";
        return null;
    }

    private static class PendingSpawn {
        Location location;
        long expiry;
        PendingSpawn(Location loc, long expiry) {
            this.location = loc;
            this.expiry = expiry;
        }
    }

    public boolean spawnBoss(String bossId, Location location) {
        Function<JavaPlugin, Boss> constructor = bossConstructors.get(bossId);
        if (constructor == null) return false;
        Boss boss = constructor.apply(plugin);
        boss.spawn(location);
        activeBosses.put(boss.getEntityId(), boss);
        return true;
    }

    public List<String> getAvailableBossIds() {
        return new ArrayList<>(bossConstructors.keySet());
    }
}