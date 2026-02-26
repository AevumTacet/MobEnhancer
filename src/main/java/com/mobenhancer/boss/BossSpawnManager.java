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
    private static final long BOSS_NO_TARGET_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutos
    private long lastNaturalSpawnTime = 0L;

    // Clave para marcar el armor stand como placeholder
    private final NamespacedKey placeholderKey;

    public BossSpawnManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.placeholderKey = new NamespacedKey(plugin, "boss_placeholder");
        registerBosses();
        startSpawningTask();
        startBossTimeoutTask();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(new BossListener(activeBosses, plugin), plugin);
    }

    private void registerBosses() {
        bossConstructors.put("putrid_colossus", PutridColossus::new);
        bossConstructors.put("necromancer_skeleton", NecromancerSkeleton::new);
        bossConstructors.put("enderman_overseer", EndermanOverseer::new);
        //bossConstructors.put("wandering_warden", WanderingWarden::new);
        //bossConstructors.put("nether_raid", NetherRaid::new);
    }

    private void startSpawningTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // ── Guardias básicas ────────────────────────────────────
                if (!plugin.getConfig().getBoolean("boss-spawn.enabled", true)) return;

                int minPlayers = plugin.getConfig().getInt("boss-spawn.min-players", 1);
                if (Bukkit.getOnlinePlayers().size() < minPlayers) return;

                int maxConcurrent = plugin.getConfig().getInt("boss-spawn.max-concurrent", 1);
                // Contar solo bosses activos, no placeholders pendientes
                if (activeBosses.size() >= maxConcurrent) return;

                // ── Cooldown explícito ──────────────────────────────────
                long cooldownMs = plugin.getConfig()
                        .getLong("boss-spawn.cooldown-minutes", 60) * 60_000L;
                long now = System.currentTimeMillis();

                if (now - lastNaturalSpawnTime < cooldownMs) return;

                // ── Chance de spawn (ahora es una probabilidad de "activar"
                //    la ventana, no de spawn por segundo) ─────────────────
                double chance = plugin.getConfig()
                        .getDouble("boss-spawn.chance", 0.25); // 25% cuando el cooldown expira
                if (random.nextDouble() > chance) return;

                // ── Selección de jugador y ubicación ───────────────────
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                if (players.isEmpty()) return;
                Player target = players.get(random.nextInt(players.size()));

                Location spawnLoc = findSurfaceLocationNear(target.getLocation());
                if (spawnLoc == null) return;

                String bossId = selectRandomBossId();
                if (bossId == null) return;

                // ── Registrar el timestamp ANTES del spawn ──────────────
                // para que incluso si algo falla, el cooldown se respete
                lastNaturalSpawnTime = now;

                // ── Broadcast ──────────────────────────────────────────
                String message = plugin.getConfig().getString(
                        "boss-spawn.broadcast-message",
                        "&cA abomination is about to appear at X: %x Y: %y Z: %z!");
                message = ChatColor.translateAlternateColorCodes('&',
                        message.replace("%x", String.valueOf(spawnLoc.getBlockX()))
                            .replace("%y", String.valueOf(spawnLoc.getBlockY()))
                            .replace("%z", String.valueOf(spawnLoc.getBlockZ())));
                Bukkit.broadcastMessage(message);

                plugin.getLogger().info("[BossSpawnManager] Spawn natural activado. "
                        + "Próximo spawn disponible en "
                        + (cooldownMs / 60_000L) + " minutos.");

                createPlaceholder(spawnLoc, bossId);
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60); // comprobar una vez por minuto
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
        private double angle = 0;
        private final Location base = location.clone(); // posición fija, no depende del stand

        @Override
        public void run() {
            if (!stand.isValid() || stand.isDead()) {
                cancel();
                return;
            }

            World world = base.getWorld();

            // --- Espiral ascendente ---
            // Dos brazos de la espiral desfasados 180° para más volumen visual
            for (int arm = 0; arm < 2; arm++) {
                double armOffset = arm * Math.PI;

                for (int step = 0; step < 12; step++) {
                    double t = step / 12.0; // 0.0 → 1.0, progreso vertical

                    double currentAngle = angle + armOffset + (t * Math.PI * 3); // 1.5 vueltas por brazo
                    double radius = 0.6 + t * 0.4; // radio crece ligeramente hacia arriba
                    double height = t * 3.5; // columna de 3.5 bloques de alto

                    double x = base.getX() + radius * Math.cos(currentAngle);
                    double z = base.getZ() + radius * Math.sin(currentAngle);
                    double y = base.getY() + height;

                    Location particleLoc = new Location(world, x, y, z);

                    // Color: degradado de morado oscuro (base) a azul claro (cima)
                    int red   = (int) (80  * (1 - t) + 40  * t);
                    int green = (int) (0   * (1 - t) + 10  * t);
                    int blue  = (int) (180 * (1 - t) + 255 * t);

                    world.spawnParticle(
                            Particle.DUST,
                            particleLoc,
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(red, green, blue), 1.3f)
                    );
                }
            }

            // --- Partículas de ambiente en la base ---
            // PORTAL gira por sí solo visualmente y da sensación de succión
            world.spawnParticle(
                    Particle.PORTAL,
                    base.clone().add(0, 0.5, 0),
                    6, 0.3, 0.1, 0.3, 0.4
            );

            // --- Destellos ocasionales en la cima de la columna ---
            // Solo cada ~1 segundo (el runnable corre cada 3 ticks = 15 veces/seg)
            if ((int)(angle * 10) % 15 == 0) {
                world.spawnParticle(
                        Particle.END_ROD,
                        base.clone().add(0, 3.8, 0),
                        3, 0.2, 0.1, 0.2, 0.02
                );
            }

            // Avanzar el ángulo base: velocidad de rotación de la espiral
            angle += 0.25;
            if (angle > Math.PI * 2) angle -= Math.PI * 2; // mantener en rango para evitar overflow
        }
    }.runTaskTimer(plugin, 0L, 3L); // cada 3 ticks = fluido sin ser costoso

        // Programar timeout (3 minutos)
        BukkitTask timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                removePlaceholder(stand.getUniqueId());
            }
        }.runTaskLater(plugin, 20L * 60 * 8); // 8 minutos en ticks

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
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Location playerLoc = player.getLocation();

        new ArrayList<>(pendingSpawns.entrySet()).forEach(entry -> {
            UUID standId = entry.getKey();
            PendingSpawn ps = entry.getValue();

            if (!ps.stand.isValid() || ps.stand.isDead()) {
                removePlaceholder(standId);
                return;
            }

            // Distancia horizontal solamente (ignorar diferencia de altura Y)
            double dx = playerLoc.getX() - ps.location.getX();
            double dz = playerLoc.getZ() - ps.location.getZ();
            double horizontalDistanceSquared = dx * dx + dz * dz;

            if (horizontalDistanceSquared <= 8 * 8) {
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

        player.sendMessage("§aPlaceholder created at §e" +
                spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ() +
                " §afor boss §e" + selectedId);

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

    private void startBossTimeoutTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeBosses.isEmpty()) return;

                long now = System.currentTimeMillis();

                // Iterar sobre copia para poder modificar el mapa durante el bucle
                new ArrayList<>(activeBosses.entrySet()).forEach(entry -> {
                    Boss boss = entry.getValue();

                    // Saltar bosses ya muertos o inactivos (serán limpiados por onBossDeath)
                    if (!boss.isActive() || boss.getEntity() == null || boss.getEntity().isDead()) return;

                    long idleTime = now - boss.getLastTargetTime();

                    if (idleTime >= BOSS_NO_TARGET_TIMEOUT_MS) {
                        plugin.getLogger().info("[BossSpawnManager] Boss '" + boss.getId()
                                + "' eliminado por inactividad ("
                                + (idleTime / 1000) + "s sin target).");

                        // Efectos de despawn para que no desaparezca abruptamente
                        Location loc = boss.getEntity().getLocation();
                        loc.getWorld().spawnParticle(
                                Particle.EXPLOSION, loc.clone().add(0, 1, 0), 3,
                                0.5, 0.5, 0.5, 0.1
                        );
                        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_DEATH, 1.0f, 1.2f);

                        // Eliminar de activeBosses todas las entradas que apunten a este boss
                        // (entidad principal + caballo en el caso del Necromancer)
                        activeBosses.entrySet().removeIf(e -> e.getValue().equals(boss));

                        // Despawnear el boss y limpiar sus recursos
                        boss.despawn();
                    }
                });
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 30); // comprobar cada 30 segundos
    }

}