package com.mobenhancer.boss;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.mobenhancer.MobEnhancer;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class NecromancerSkeleton extends Boss {

    // Constantes
    private static final double MAX_HEALTH = 600.0;
    private static final double SCALE = 1.5;
    private static final double PROJECTILE_DAMAGE = 12.0; // máximo
    private static final int PROJECTILE_COOLDOWN_SECONDS = 2;
    private static final double PROJECTILE_CHANCE = 0.6;
    private static final int SUMMON_COOLDOWN_SECONDS = 20;
    private static final double SUMMON_CHANCE = 0.4;
    private static final int SUMMON_COUNT = 4;
    private static final double SUMMON_RADIUS = 8.0;
    private static final double LIFESTEAL_AURA_CHANCE = 0.4;
    private static final double LIFESTEAL_AURA_TRIGGER_HEALTH_PERCENT = 0.75;
    private static final double LIFESTEAL_AURA_RADIUS = 8.0;
    private static final int LIFESTEAL_AURA_DURATION = 3; // segundos
    private static final double LIFESTEAL_AURA_DAMAGE_PER_SECOND = 25.0;

    // ── Fase 2 ───────────────────────────────────────────────────────
    private static final double PHASE2_HEALTH_PERCENT  = 0.30;
    private static final double PHASE2_BLIND_RADIUS    = 6.0;
    private static final int    PHASE2_BLIND_DURATION  = 60;  // 3 segundos (ticks)
    private static final int    PHASE2_BLIND_AMPLIFIER = 0;   // nivel 1
    private boolean phase2Active = false;

    // Textura para la cabeza personalizada
    private static final String SKULL_TEXTURE = "c34f534f6cb88d5272dfe8601c261651b4990e6605d718d09072a42c2a41843d";

    // Estado
    private SkeletonHorse horse;
    private final List<LivingEntity> familiars = new ArrayList<>();
    private final Random random = new Random();

    private boolean auraActive = false;
    private boolean auraWarning = false;

    private long lastProjectileTime = 0;
    private long lastSummonTime = 0;

    public NecromancerSkeleton(JavaPlugin plugin) {
        super(plugin, "necromancer_skeleton", "Lich Skeleton", MAX_HEALTH);
    }

    @SuppressWarnings("deprecation")
    private ItemStack createCustomHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "Necromancer");
        PlayerTextures textures = profile.getTextures();

        try {
            URL skinUrl = new URL("https://textures.minecraft.net/texture/" + SKULL_TEXTURE);
            textures.setSkin(skinUrl);
        } catch (MalformedURLException ex) {
            throw new RuntimeException("URL de textura inválida", ex);
        }

        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
        head.setItemMeta(meta);

        return head;
    }

    @Override
    public void spawn(Location location) {
        World world = location.getWorld();

        // 1. Spawn del caballo esqueleto (con IA activada)
        horse = (SkeletonHorse) world.spawnEntity(location, EntityType.SKELETON_HORSE);
        configureHorse(horse);

        // 2. Spawn del stray (necromancer)
        Stray necromancer = (Stray) world.spawnEntity(location, EntityType.STRAY);
        configureNecromancer(necromancer);

        // 3. Montar el necromancer en el caballo
        horse.addPassenger(necromancer);

        // 4. Marcar ambos como bosses
        markAsBoss(necromancer);
        markAsBoss(horse);

        this.entity = necromancer;
        this.entityId = necromancer.getUniqueId();
        this.active = true;

        // 5. Inicializar bossbar (solo para el necromancer)
        initBossBar();
        holdChunk();

        // 6. Iniciar tareas
        startTicking();
        startConstantParticles();
    }

    private void configureHorse(SkeletonHorse horse) {
        horse.getAttribute(Attribute.SCALE).setBaseValue(SCALE);
        horse.setInvulnerable(true);
        horse.setTamed(true);
        horse.setRemoveWhenFarAway(false);
        horse.setCustomName("Necromancer's Steed");
        horse.setCustomNameVisible(false);
        horse.setAI(true);  // IA activada para que se mueva solo
        // Ajustar velocidad para que sea más ágil
        horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.3);
    }

    private void configureNecromancer(Stray necromancer) {
        necromancer.getAttribute(Attribute.SCALE).setBaseValue(SCALE);
        necromancer.getAttribute(Attribute.MAX_HEALTH).setBaseValue(MAX_HEALTH);
        necromancer.setHealth(MAX_HEALTH);
        necromancer.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.25);
        necromancer.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(24.0);
        // Le damos un arco para que la IA lo considere un atacante a distancia
        necromancer.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
        necromancer.getEquipment().setItemInMainHandDropChance(0);
        ItemStack head = createCustomHead();
        necromancer.getEquipment().setHelmet(head);
        necromancer.getEquipment().setHelmetDropChance(0);

        // Eliminar tipo skeleton previo
        necromancer.getPersistentDataContainer().remove(MobEnhancer.skeletonKey);
        necromancer.getPersistentDataContainer().set(MobEnhancer.skeletonKey, PersistentDataType.STRING, "default");
    }

    private void startTicking() {
        new BukkitRunnable() {
            private int deadCheckCounter = 0;

            @Override
            public void run() {
                // Si el boss no está activo, cancelar sin dudas
                if (!active) {
                    cancel();
                    return;
                }

                // Si la entidad es null o inválida, dar 3 segundos de gracia
                // antes de cancelar, por si es un estado transitorio del servidor
                if (entity == null || !entity.isValid()) {
                    deadCheckCounter++;
                    if (deadCheckCounter >= 3) {
                        cancel();
                    }
                    return;
                }

                // Si la entidad está genuinamente muerta (ya procesó su EntityDeathEvent),
                // cancelar definitivamente
                if (entity.isDead()) {
                    cancel();
                    return;
                }

                // Todo bien: resetear contador y ejecutar lógica
                deadCheckCounter = 0;
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L); // delay inicial de 1 segundo para que spawn() termine
    }

    @Override
    public void tick() {
        if (!active || entity == null || entity.isDead()) return;

        if (entity.getHealth() <= 0) {
            plugin.getLogger().warning(
                    "[NecromancerSkeleton] Salud en 0 pero entidad no muerta. Forzando muerte.");
            forceCleanup();
            return;
        }

        updateBossBar();
        updateBossBarViewers(50.0);

        // Comprobar transición a fase 2
        if (!phase2Active && entity.getHealth() <= MAX_HEALTH * PHASE2_HEALTH_PERCENT) {
            activatePhase2();
        }

        // Verificar que el necromancer sigue montado en el caballo
        if (horse != null && horse.isValid() && !horse.isDead()) {
            if (entity.getVehicle() == null || !entity.getVehicle().equals(horse)) {
                horse.addPassenger(entity);
            }
            LivingEntity target = ((Mob) entity).getTarget();
            horse.setTarget(target);
        }

        long now = System.currentTimeMillis();
        LivingEntity target = ((Mob) entity).getTarget();

        if (target instanceof Player player && !isValidTarget(player)) {
            ((Mob) entity).setTarget(null);
            if (horse != null && horse.isValid()) horse.setTarget(null);
            target = null;
        }

        if (target != null && target.isValid() && !target.isDead()) {
            lastTargetTime = now;
        }

        // Proyectil — en fase 2 se permite el disparo nativo (onShootBow no cancela)
        if (target != null && target.isValid() && !target.isDead()) {
            if (!phase2Active) {
                // Fase 1: proyectil personalizado (snowball robavidas)
                if ((now - lastProjectileTime) >= PROJECTILE_COOLDOWN_SECONDS * 1000L) {
                    if (random.nextDouble() < PROJECTILE_CHANCE) {
                        shootProjectile(target);
                        lastProjectileTime = now;
                    }
                }
            }
            // En fase 2, la IA nativa del Stray dispara flechas libremente
            // onShootBow ya no cancela el evento cuando phase2Active == true

            // Invocación
            if ((now - lastSummonTime) >= SUMMON_COOLDOWN_SECONDS * 1000L) {
                if (random.nextDouble() < SUMMON_CHANCE) {
                    summonFamiliars(target);
                    lastSummonTime = now;
                }
            }
        }

        // Ceguera en fase 2 — aplicar a jugadores survival cercanos
        if (phase2Active) {
            applyPhase2Blindness();
        }

        familiars.removeIf(fam -> fam == null || fam.isDead() || !fam.isValid());
    }

    private void startConstantParticles() {
        new BukkitRunnable() {
            private int deadCheckCounter = 0;

            @Override
            public void run() {
                if (!active) {
                    cancel();
                    return;
                }

                if (entity == null || !entity.isValid()) {
                    deadCheckCounter++;
                    if (deadCheckCounter >= 3) cancel();
                    return;
                }

                if (entity.isDead()) {
                    cancel();
                    return;
                }

                deadCheckCounter = 0;
                entity.getWorld().spawnParticle(
                        Particle.CRIMSON_SPORE,
                        entity.getLocation().add(0, 2, 0),
                        8, 0.5, 0.5, 0.5, 0
                );
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    private void applyPhase2Blindness() {
        if (entity == null || entity.isDead()) return;

        Location bossLoc = entity.getLocation();

        for (Entity e : entity.getWorld().getNearbyEntities(
                bossLoc, PHASE2_BLIND_RADIUS, PHASE2_BLIND_RADIUS, PHASE2_BLIND_RADIUS)) {

            if (!(e instanceof Player p)) continue;
            if (p.getGameMode() != GameMode.SURVIVAL) continue;
            if (!p.isValid() || p.isDead()) continue;

            // Re-aplicar ceguera cada tick para que nunca expire mientras está en rango
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.BLINDNESS,
                    PHASE2_BLIND_DURATION,
                    PHASE2_BLIND_AMPLIFIER,
                    false,  // ambient: false — partículas visibles
                    false,  // particles: false — la ceguera no necesita partículas propias
                    true    // icon: mostrar ícono en HUD
            ));
        }
    }
    // ================== PROYECTIL ==================

    @Override
    public void onShootBow(EntityShootBowEvent event) {
        if (!phase2Active) {
            // Fase 1: cancelar siempre — usamos el proyectil personalizado
            event.setCancelled(true);
            return;
        }

        // Fase 2: permitir el disparo nativo pero modificar la flecha
        if (!(event.getProjectile() instanceof Arrow arrow)) return;

        // Aplicar efecto wither a la flecha
        arrow.addCustomEffect(
                new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.WITHER,
                        60,  // 3 segundos
                        1,   // amplifier: nivel 2
                        false,
                        true
                ),
                true
        );

        // Estela de partículas en la flecha
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isDead() || arrow.isOnGround()) {
                    cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(
                        Particle.DUST,
                        arrow.getLocation(),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(30, 0, 30), 1.0f)
                );
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void shootProjectile(LivingEntity target) {
        if (!(entity instanceof Stray necromancer)) return;

        // Animación de levantar la mano
        necromancer.swingMainHand();

        Location eyeLoc = necromancer.getEyeLocation();
        Vector direction = target.getLocation().add(0, 1, 0).toVector().subtract(eyeLoc.toVector()).normalize();

        Snowball snowball = necromancer.launchProjectile(Snowball.class, direction.multiply(1.5));
        snowball.setShooter(necromancer);

        // Marcar el proyectil como del necromancer
        NamespacedKey key = new NamespacedKey(plugin, "necromancer_projectile");
        snowball.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

        // Efectos visuales
        snowball.setGlowing(true);
        snowball.setVisualFire(true);
        startProjectileTrail(snowball);

        necromancer.getWorld().playSound(necromancer.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.5f);
    }

    private void startProjectileTrail(Snowball snowball) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!snowball.isValid() || snowball.isOnGround() || snowball.isDead()) {
                    cancel();
                    return;
                }
                snowball.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, snowball.getLocation(), 3, 0.1, 0.1, 0.1, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @Override
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        NamespacedKey key = new NamespacedKey(plugin, "necromancer_projectile");
        if (!snowball.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;

        if (event.getHitEntity() instanceof LivingEntity target) {
            double damage = PROJECTILE_DAMAGE * (0.6 + 0.4 * random.nextDouble());
            target.damage(damage, entity);

            double newHealth = Math.min(entity.getHealth() + damage, MAX_HEALTH);
            entity.setHealth(newHealth);
            updateBossBar();

            // Efectos existentes
            target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.2f);

            // Haz de robo de vida
            drawLifestealBeam(target, 12); // ← más corto porque el proyectil es puntual
        }

        snowball.remove();
    }

    // ================== INVOCACIÓN ==================

    private void summonFamiliars(LivingEntity target) {

        Location bossLoc = entity.getLocation();
        World world = bossLoc.getWorld();

        // --- Efectos de invocación ---
        world.playSound(bossLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);

        // CORREGIDO: DUST requiere DustOptions
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(80, 0, 180), 2.0f);
        world.spawnParticle(Particle.DUST, bossLoc.clone().add(0, 2, 0),
                50, 3, 2, 3, 0.1, dustOptions);

        int count = random.nextInt(SUMMON_COUNT) + 1;
        for (int i = 0; i < count; i++) {
            Location spawnLoc = findGroundLocationRobust(bossLoc);
            if (spawnLoc == null) {
                // plugin.getLogger().warning("[NecromancerSkeleton] No se encontró ubicación de suelo para familiar #" + i);
                continue;
            }

            Skeleton familiar = spawnFamiliar(spawnLoc, target);
            familiars.add(familiar);
            // Efecto de aparición por familiar
            world.spawnParticle(Particle.WITCH, spawnLoc.clone().add(0, 1, 0),
                    20, 0.4, 0.6, 0.4, 0.05);
            world.playSound(spawnLoc, Sound.ENTITY_SKELETON_AMBIENT, 1.0f, 0.8f);
        }

        // plugin.getLogger().info("[NecromancerSkeleton] Familiares invocados: " + spawned + "/" + count);
    }

    private static final String FAMILIAR_SKULL_TEXTURE = "54e5a2321e639fdc9d42434aff3d7c674b4a88b2e45ed9f03723befecc9a3e7c";

    @SuppressWarnings("deprecation")
    private ItemStack createFamiliarHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "Familiar");
        PlayerTextures textures = profile.getTextures();

        try {
            URL skinUrl = new URL("https://textures.minecraft.net/texture/" + FAMILIAR_SKULL_TEXTURE);
            textures.setSkin(skinUrl);
        } catch (MalformedURLException ex) {
            throw new RuntimeException("URL de textura inválida para familiar", ex);
        }

        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
        head.setItemMeta(meta);

        return head;
    }

    private Skeleton spawnFamiliar(Location spawnLoc, LivingEntity target) {
        Skeleton familiar = (Skeleton) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.SKELETON);
        familiar.setCustomName("§5Familiar");
        familiar.setCustomNameVisible(false);
        familiar.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
        familiar.setHealth(20.0);
        familiar.getAttribute(Attribute.SCALE).setBaseValue(0.75); // Tamaño reducido
        familiar.setRemoveWhenFarAway(false);
        familiar.setTarget(target);

        // Equipar cabeza personalizada
        familiar.getEquipment().setHelmet(createFamiliarHead());
        familiar.getEquipment().setHelmetDropChance(0);

        familiar.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "familiar"),
                PersistentDataType.BYTE, (byte) 1);

        return familiar;
    }

    /**
     * Versión robusta: busca suelo en un radio, con fallback a la posición del boss.
     */
    private Location findGroundLocationRobust(Location origin) {
        World world = origin.getWorld();

        // Intentar posición aleatoria dentro del radio
        for (int attempt = 0; attempt < 8; attempt++) {
            double offsetX = random.nextDouble() * SUMMON_RADIUS * 2 - SUMMON_RADIUS;
            double offsetZ = random.nextDouble() * SUMMON_RADIUS * 2 - SUMMON_RADIUS;

            int x = (int) (origin.getX() + offsetX);
            int z = (int) (origin.getZ() + offsetZ);

            Location candidate = findSafeGroundAt(world, x, z, (int) origin.getY());
            if (candidate != null) return candidate;
        }

        // Fallback: justo al lado del boss
        return findSafeGroundAt(world, origin.getBlockX() + 1, origin.getBlockZ() + 1, (int) origin.getY());
    }

    /**
     * Busca una posición segura en X/Z, escaneando desde Y de referencia
     * hacia arriba y hacia abajo, en lugar de depender solo de getHighestBlockYAt.
     * Esto funciona correctamente en interiores y cuevas.
     */
    private Location findSafeGroundAt(World world, int x, int z, int referenceY) {
        // Buscar hacia abajo desde referenceY+5 hasta referenceY-10
        for (int y = referenceY + 5; y >= referenceY - 10; y--) {
            Location feet = new Location(world, x + 0.5, y, z + 0.5);
            Location head = new Location(world, x + 0.5, y + 1, z + 0.5);
            Location floor = new Location(world, x + 0.5, y - 1, z + 0.5);

            boolean feetClear = feet.getBlock().getType().isAir() || !feet.getBlock().getType().isSolid();
            boolean headClear = head.getBlock().getType().isAir() || !head.getBlock().getType().isSolid();
            boolean hasFloor = floor.getBlock().getType().isSolid();

            if (feetClear && headClear && hasFloor) {
                return feet;
            }
        }
        return null;
    }

    // ================== AURA DE ROBO DE VIDA ==================

    private void startLifestealAura() {
        // auraWarning ya fue puesto a true en onDamage, no repetir aquí

        if (!active || entity == null || entity.isDead()) {
            auraWarning = false;
            return;
        }

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.7f);

        new BukkitRunnable() {
            int warningTicks = 0;

            @Override
            public void run() {
                if (!active || entity == null || entity.isDead()) {
                    auraWarning = false;
                    cancel();
                    return;
                }

                if (warningTicks >= 4) {
                    auraWarning = false;
                    activateAura();
                    cancel();
                    return;
                }

                // Partículas de advertencia: espiral roja alrededor del necromancer
                Location loc = entity.getLocation().add(0, 1, 0);
                double radius = 2.0;
                for (int i = 0; i < 8; i++) {
                    double angle = (warningTicks * 20 + i * 45) * Math.PI / 180;
                    double x = loc.getX() + radius * Math.cos(angle);
                    double z = loc.getZ() + radius * Math.sin(angle);
                    Location particleLoc = new Location(loc.getWorld(), x, loc.getY(), z);
                    loc.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.5f));
                }
                loc.getWorld().spawnParticle(Particle.WITCH, loc, 10, 1, 1, 1, 0);

                warningTicks++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void activateAura() {
        auraActive = true;
        Location center = entity.getLocation();
        World world = center.getWorld();

        // Sonido de activación
        world.playSound(center, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.5f);
        world.playSound(center, Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.2f);

        // Partícula grande de inicio
        world.spawnParticle(Particle.EXPLOSION, center, 1);
        drawRedCircle(center, LIFESTEAL_AURA_RADIUS);

        new BukkitRunnable() {
            int seconds = 0;

            @Override
            public void run() {
                if (!active || entity.isDead() || seconds >= LIFESTEAL_AURA_DURATION) {
                    auraActive = false;
                    cancel();
                    return;
                }

                double totalDamage = 0;
                for (Entity e : world.getNearbyEntities(center, LIFESTEAL_AURA_RADIUS, 4, LIFESTEAL_AURA_RADIUS)) {
                    if (e instanceof Player p) {
                        double damage = Math.min(p.getHealth(), LIFESTEAL_AURA_DAMAGE_PER_SECOND);
                        p.damage(damage, entity);
                        totalDamage += damage;
                        drawLifestealBeam(p, 15); // ← haz de ~0.75 segundos por jugador dañado

                    } else if (e instanceof Mob && !familiars.contains(e) && !e.equals(horse)) {
                        LivingEntity mob = (LivingEntity) e;
                        double damage = Math.min(mob.getHealth(), LIFESTEAL_AURA_DAMAGE_PER_SECOND);
                        mob.damage(damage, entity);
                        totalDamage += damage;
                        drawLifestealBeam(mob, 15); // ← haz de ~0.75 segundos por mob dañado
                    }
                }

                if (totalDamage > 0) {
                    double newHealth = Math.min(entity.getHealth() + totalDamage, MAX_HEALTH);
                    entity.setHealth(newHealth);
                    updateBossBar();
                }

                drawRedCircle(center, LIFESTEAL_AURA_RADIUS);
                seconds++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void drawRedCircle(Location center, double radius) {
        World world = center.getWorld();
        int points = 36;
        double angleStep = 2 * Math.PI / points;

        for (int i = 0; i < points; i++) {
            double angle = i * angleStep;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
            Location loc = new Location(world, x, y, z);
            world.spawnParticle(Particle.DUST, loc, 12, 0, 0, 0, 12,
                    new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1));
        }
    }

    /**
     * Dibuja un haz animado de partículas desde la víctima hacia el Necromancer,
     * simbolizando la transferencia de vida.
     *
     * @param victim   La entidad a la que se le está robando vida
     * @param duration Duración del haz en ticks (20 = 1 segundo)
     */
    private void drawLifestealBeam(LivingEntity victim, int duration) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // Cancelar si alguna de las dos entidades ya no es válida
                if (ticks >= duration || !active
                        || entity == null || entity.isDead()
                        || victim == null || victim.isDead()) {
                    cancel();
                    return;
                }

                Location from = victim.getLocation().add(0, 1, 0);   // pecho de la víctima
                Location to   = entity.getLocation().add(0, 1.5, 0); // pecho del necromancer

                double distance = from.distance(to);
                if (distance < 0.5) {
                    cancel();
                    return;
                }

                // Vector unitario de víctima → necromancer
                Vector direction = to.toVector().subtract(from.toVector()).normalize();

                // Número de puntos proporcional a la distancia (1 punto por bloque aprox.)
                int points = (int) (distance * 4);

                for (int i = 0; i < points; i++) {
                    double t = (double) i / points; // 0.0 → 1.0

                    // Posición base interpolada
                    Location particleLoc = from.clone().add(direction.clone().multiply(t * distance));

                    // Ondulación sinusoidal perpendicular para que el haz tenga "vida"
                    double wave = Math.sin(t * Math.PI * 4 + ticks * 0.6) * 0.15;
                    Vector perp = getPerpendicular(direction).multiply(wave);
                    particleLoc.add(perp);

                    // Color interpolado: rojo intenso en la víctima → violeta en el necromancer
                    int red   = (int) (255 * (1 - t) + 120 * t);
                    int green = 0;
                    int blue  = (int) (0   * (1 - t) + 220 * t);

                    World world = particleLoc.getWorld();
                    world.spawnParticle(
                            Particle.DUST,
                            particleLoc,
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(red, green, blue), 1.2f)
                    );

                    // Cada 4 puntos, añadir una partícula SOUL más tenue para profundidad
                    if (i % 4 == 0) {
                        world.spawnParticle(Particle.SOUL, particleLoc, 1, 0.05, 0.05, 0.05, 0.001);
                    }
                }

                // Destello en el necromancer al recibir la vida
                entity.getWorld().spawnParticle(
                        Particle.DUST,
                        entity.getLocation().add(0, 1.5, 0),
                        3, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(120, 0, 220), 1.5f)
                );

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L); // cada tick para máxima fluidez
    }

    /**
     * Calcula un vector perpendicular al dado para la ondulación del haz.
     * Evita el caso degenerado cuando la dirección es paralela al eje Y.
     */
    private Vector getPerpendicular(Vector direction) {
        Vector ref = (Math.abs(direction.getY()) < 0.9)
                ? new Vector(0, 1, 0)   // caso normal: usar eje Y como referencia
                : new Vector(1, 0, 0);  // caso vertical: usar eje X
        return direction.clone().crossProduct(ref).normalize();
    }
    // ================== EVENTOS DE DAÑO ==================

    @Override
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!event.getEntity().equals(entity)) return;

        // Calcular salud resultante, clampeada a 0 para evitar negativos
        double healthAfterDamage = Math.max(0, entity.getHealth() - event.getFinalDamage());
        double triggerThreshold = MAX_HEALTH * LIFESTEAL_AURA_TRIGGER_HEALTH_PERCENT;

        // Guardia: si ya está activa o en advertencia, no hacer nada
        if (auraActive || auraWarning) return;

        // Comprobar si la salud resultante está por debajo del umbral
        if (healthAfterDamage <= 0) return; // Va a morir, no tiene sentido activar el aura
        if (healthAfterDamage > triggerThreshold) return;

        // Aplicar chance
        if (random.nextDouble() >= LIFESTEAL_AURA_CHANCE) return;

        // Marcar inmediatamente para evitar activaciones múltiples en el mismo tick
        auraWarning = true;

        Bukkit.getScheduler().runTask(plugin, this::startLifestealAura);
    }

    /**
     * Activa la fase 2 del Necromancer Skeleton.
     * Añade efectos de resistencia y muestra partículas de transición.
     */
    private void activatePhase2() {
        phase2Active = true;

        // Resistencia 2
        entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.RESISTANCE,
                Integer.MAX_VALUE, 1, false, true, true));

        // Resistencia al fuego 2
        entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE,
                Integer.MAX_VALUE, 1, false, true, true));

        Location loc = entity.getLocation().clone();
        World world = loc.getWorld();

        // Sonidos de transición
        world.playSound(loc, Sound.ENTITY_WITHER_SPAWN,   1.0f, 0.6f);
        world.playSound(loc, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.5f);

        // Explosión de partículas de transición
        world.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 2);
        world.spawnParticle(Particle.SOUL,
                loc.clone().add(0, 2, 0), 60, 2, 1, 2, 0.15);
        world.spawnParticle(Particle.DUST,
                loc.clone().add(0, 2, 0), 80, 2, 1, 2, 0.1,
                new Particle.DustOptions(Color.fromRGB(180, 0, 255), 2.0f));

        // Mensaje a jugadores cercanos
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= 80 * 80) {
                p.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD
                        + "The Lich Skeleton grows desperate!");
            }
        }

        // Animación de pulso durante 2 segundos
        new BukkitRunnable() {
            double angle = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 40 || !active || entity == null || entity.isDead()) {
                    cancel();
                    return;
                }
                double progress = ticks / 40.0;
                double radius = 4.0 * Math.sin(progress * Math.PI);

                for (int i = 0; i < 6; i++) {
                    double a = angle + i * (Math.PI / 3);
                    double x = loc.getX() + radius * Math.cos(a);
                    double z = loc.getZ() + radius * Math.sin(a);
                    double y = loc.getY() + 1.5 + Math.sin(ticks * 0.3 + i) * 1.0;

                    world.spawnParticle(Particle.DUST,
                            new Location(world, x, y, z),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(
                                    Color.fromRGB(220, 0, 100), 1.8f));
                }
                angle += 0.35;
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        plugin.getLogger().info("[NecromancerSkeleton] Fase 2 activada. HP: "
                + String.format("%.1f", entity.getHealth()) + "/" + MAX_HEALTH);
    }

    // ================== MUERTE ==================
    /**
     * Forzar la limpieza completa del boss cuando el flujo de muerte normal falla.
     * Esto ocurre cuando el Stray está montado y el EntityDeathEvent no se dispara.
     */
    private void forceCleanup() {
        active = false; // idempotente, no importa si ya era false

        if (horse != null && horse.isValid()) {
            horse.removePassenger(entity);
        }

        familiars.removeIf(fam -> {
            if (fam != null && fam.isValid() && !fam.isDead()) fam.remove();
            return true;
        });

        // Solo hacer drops y efectos si la entidad todavía existe
        // (si onDeath ya la procesó, isValid() devolverá false)
        if (entity != null && entity.isValid() && !entity.isDead()) {
            Location deathLoc = entity.getLocation();
            deathLoc.getWorld().dropItemNaturally(deathLoc,
                    new ItemStack(Material.BONE, 10));
            deathLoc.getWorld().dropItemNaturally(deathLoc,
                    new ItemStack(Material.EXPERIENCE_BOTTLE, 4));
            deathLoc.getWorld().spawnParticle(
                    Particle.EXPLOSION, deathLoc.clone().add(0, 1, 0), 3,
                    0.5, 0.5, 0.5, 0.1);
            deathLoc.getWorld().playSound(deathLoc,
                    Sound.ENTITY_SKELETON_DEATH, 1.0f, 0.8f);
            entity.remove();
        }

        if (horse != null && horse.isValid() && !horse.isDead()) {
            horse.setInvulnerable(false);
            horse.remove();
        }

        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    @Override
    public void onDeath(EntityDeathEvent event) {
        // Limpiar drops por defecto y añadir los nuestros
        event.getDrops().clear();
        // Delegar toda la limpieza a forceCleanup para evitar duplicación
        // Marcar active = false primero para detener el tick inmediatamente
        active = false;
        forceCleanup();
    }
}