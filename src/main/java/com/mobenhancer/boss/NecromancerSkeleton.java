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
import com.mobenhancer.integration.CraftEngineHook;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class NecromancerSkeleton extends Boss {

    private static final double MAX_HEALTH                       = 600.0;
    private static final double SCALE                            = 1.5;
    private static final double PROJECTILE_DAMAGE                = 8.0;
    private static final int    PROJECTILE_COOLDOWN_SECONDS      = 3;
    private static final double PROJECTILE_CHANCE                = 0.6;
    private static final int    SUMMON_COOLDOWN_SECONDS          = 20;
    private static final double SUMMON_CHANCE                    = 0.4;
    private static final int    SUMMON_COUNT                     = 4;
    private static final double SUMMON_RADIUS                    = 8.0;
    private static final double LIFESTEAL_AURA_CHANCE            = 0.3;
    private static final double LIFESTEAL_AURA_TRIGGER_HEALTH_PERCENT = 0.75;
    private static final double LIFESTEAL_AURA_RADIUS            = 5.0;
    private static final int    LIFESTEAL_AURA_DURATION          = 3;
    private static final double LIFESTEAL_AURA_DAMAGE_PER_SECOND = 25.0;

    private static final double PHASE2_HEALTH_PERCENT  = 0.30;
    private static final double PHASE2_BLIND_RADIUS    = 5.0;
    private static final int    PHASE2_BLIND_DURATION  = 60;
    private static final int    PHASE2_BLIND_AMPLIFIER = 0;
    private boolean phase2Active = false;

    private static final String SKULL_TEXTURE =
            "c34f534f6cb88d5272dfe8601c261651b4990e6605d718d09072a42c2a41843d";

    private SkeletonHorse horse;
    private final List<LivingEntity> familiars = new ArrayList<>();
    private final Random random = new Random();

    private boolean auraActive  = false;
    private boolean auraWarning = false;

    private long lastProjectileTime = 0;
    private long lastSummonTime     = 0;

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
            textures.setSkin(new URL("https://textures.minecraft.net/texture/" + SKULL_TEXTURE));
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

        horse = (SkeletonHorse) world.spawnEntity(location, EntityType.SKELETON_HORSE);
        configureHorse(horse);

        Stray necromancer = (Stray) world.spawnEntity(location, EntityType.STRAY);
        configureNecromancer(necromancer);

        horse.addPassenger(necromancer);

        markAsBoss(necromancer);
        markAsBoss(horse);

        this.entity   = necromancer;
        this.entityId = necromancer.getUniqueId();
        this.active   = true;

        initBossBar();
        holdChunk();
        startTicking();
        startConstantParticles();
    }

    private void configureHorse(SkeletonHorse horse) {
        horse.getAttribute(Attribute.SCALE).setBaseValue(SCALE);
        horse.setInvulnerable(true);
        horse.setTamed(true);
        horse.setRemoveWhenFarAway(false);
        horse.setCustomName(ChatColor.DARK_PURPLE + "Necromancer's Steed");
        horse.setCustomNameVisible(false);  // Feature B: sin nametag visible
        horse.setAI(true);
        horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.3);
    }

    private void configureNecromancer(Stray necromancer) {
        necromancer.getAttribute(Attribute.SCALE).setBaseValue(SCALE);
        necromancer.getAttribute(Attribute.MAX_HEALTH).setBaseValue(MAX_HEALTH);
        necromancer.setHealth(MAX_HEALTH);
        necromancer.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.25);
        necromancer.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(24.0);

        // Arma: necrostaff de CE, BOW como fallback
        ItemStack weapon = CraftEngineHook.resolveItem("regnum:necrostaff");
        if (weapon == null) weapon = new ItemStack(Material.BOW);
        necromancer.getEquipment().setItemInMainHand(weapon);
        necromancer.getEquipment().setItemInMainHandDropChance(0);

        necromancer.getEquipment().setHelmet(resolveHelmet(createCustomHead()));
        necromancer.getEquipment().setHelmetDropChance(0);

        // Feature B: nombre invisible para kill message
        necromancer.setCustomName(ChatColor.DARK_PURPLE + "Lich Skeleton");
        necromancer.setCustomNameVisible(false);

        necromancer.getPersistentDataContainer().remove(MobEnhancer.skeletonKey);
        necromancer.getPersistentDataContainer().set(
                MobEnhancer.skeletonKey, PersistentDataType.STRING, "default");
    }

    // ══════════════════════════════════════════════════════════════════
    // Bug 3 fix: sobrescribir despawn() para eliminar también el caballo
    // El despawn() de Boss.java solo elimina this.entity (el Stray).
    // Cuando el BossSpawnManager llama boss.despawn() por timeout, el
    // caballo quedaba huérfano en el mundo.
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void despawn() {
        // Primero limpiar familiares
        familiars.removeIf(fam -> {
            if (fam != null && fam.isValid() && !fam.isDead()) fam.remove();
            return true;
        });

        // Eliminar el caballo si sigue vivo
        if (horse != null && horse.isValid() && !horse.isDead()) {
            horse.setInvulnerable(false); // quitar invulnerabilidad antes de remover
            horse.remove();
        }

        // Delegar el resto al padre (elimina this.entity = Stray, bossbar, chunks)
        super.despawn();
    }

    private void startTicking() {
        new BukkitRunnable() {
            private int deadCheckCounter = 0;

            @Override
            public void run() {
                if (!active) { cancel(); return; }
                if (entity == null || !entity.isValid()) {
                    if (++deadCheckCounter >= 3) { cancel(); return; }
                    return;
                }
                if (entity.isDead()) { cancel(); return; }
                deadCheckCounter = 0;
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
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

        if (!phase2Active && entity.getHealth() <= MAX_HEALTH * PHASE2_HEALTH_PERCENT) {
            activatePhase2();
        }

        // Verificar que el necromancer sigue montado
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

        // Bug 1-style fix: actualizar lastTargetTime mientras haya target válido
        if (target != null && target.isValid() && !target.isDead()) {
            lastTargetTime = now;
        }

        if (target != null && target.isValid() && !target.isDead()) {
            if (!phase2Active) {
                if ((now - lastProjectileTime) >= PROJECTILE_COOLDOWN_SECONDS * 1000L) {
                    if (random.nextDouble() < PROJECTILE_CHANCE) {
                        shootProjectile(target);
                        lastProjectileTime = now;
                    }
                }
            }

            if ((now - lastSummonTime) >= SUMMON_COOLDOWN_SECONDS * 1000L) {
                if (random.nextDouble() < SUMMON_CHANCE) {
                    summonFamiliars(target);
                    lastSummonTime = now;
                }
            }
        }

        if (phase2Active) applyPhase2Blindness();

        familiars.removeIf(fam -> fam == null || fam.isDead() || !fam.isValid());
    }

    private void startConstantParticles() {
        new BukkitRunnable() {
            private int deadCheckCounter = 0;

            @Override
            public void run() {
                if (!active) { cancel(); return; }
                if (entity == null || !entity.isValid()) {
                    if (++deadCheckCounter >= 3) { cancel(); return; }
                    return;
                }
                if (entity.isDead()) { cancel(); return; }
                deadCheckCounter = 0;
                entity.getWorld().spawnParticle(Particle.CRIMSON_SPORE,
                        entity.getLocation().add(0, 2, 0), 8, 0.5, 0.5, 0.5, 0);
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
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.BLINDNESS,
                    PHASE2_BLIND_DURATION, PHASE2_BLIND_AMPLIFIER,
                    false, false, true));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // PROYECTIL
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void onShootBow(EntityShootBowEvent event) {
        if (!phase2Active) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getProjectile() instanceof Arrow arrow)) return;
        arrow.addCustomEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.WITHER, 60, 1, false, true), true);

        new BukkitRunnable() {
            @Override public void run() {
                if (!arrow.isValid() || arrow.isDead() || arrow.isOnGround()) {
                    cancel(); return;
                }
                arrow.getWorld().spawnParticle(Particle.DUST, arrow.getLocation(),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(30, 0, 30), 1.0f));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void shootProjectile(LivingEntity target) {
        if (!(entity instanceof Stray necromancer)) return;
        necromancer.swingMainHand();
        Location eyeLoc = necromancer.getEyeLocation();
        Vector direction = target.getLocation().add(0, 1, 0)
                .toVector().subtract(eyeLoc.toVector()).normalize();
        Snowball snowball = necromancer.launchProjectile(Snowball.class, direction.multiply(1.5));
        snowball.setShooter(necromancer);
        snowball.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "necromancer_projectile"),
                PersistentDataType.BYTE, (byte) 1);
        snowball.setGlowing(true);
        snowball.setVisualFire(true);
        startProjectileTrail(snowball);
        necromancer.getWorld().playSound(necromancer.getLocation(),
                Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.5f);
    }

    private void startProjectileTrail(Snowball snowball) {
        new BukkitRunnable() {
            @Override public void run() {
                if (!snowball.isValid() || snowball.isOnGround() || snowball.isDead()) {
                    cancel(); return;
                }
                snowball.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                        snowball.getLocation(), 3, 0.1, 0.1, 0.1, 0.01);
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
            target.getWorld().spawnParticle(Particle.SOUL,
                    target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
            entity.getWorld().playSound(entity.getLocation(),
                    Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.2f);
            drawLifestealBeam(target, 12);
        }
        snowball.remove();
    }

    // ══════════════════════════════════════════════════════════════════
    // INVOCACIÓN
    // ══════════════════════════════════════════════════════════════════

    private void summonFamiliars(LivingEntity target) {
        Location bossLoc = entity.getLocation();
        World world = bossLoc.getWorld();

        world.playSound(bossLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        world.spawnParticle(Particle.DUST, bossLoc.clone().add(0, 2, 0),
                50, 3, 2, 3, 0.1,
                new Particle.DustOptions(Color.fromRGB(80, 0, 180), 2.0f));

        int count = random.nextInt(SUMMON_COUNT) + 1;
        for (int i = 0; i < count; i++) {
            Location spawnLoc = findGroundLocationRobust(bossLoc);
            if (spawnLoc == null) continue;
            Skeleton familiar = spawnFamiliar(spawnLoc, target);
            familiars.add(familiar);
            world.spawnParticle(Particle.WITCH, spawnLoc.clone().add(0, 1, 0),
                    20, 0.4, 0.6, 0.4, 0.05);
            world.playSound(spawnLoc, Sound.ENTITY_SKELETON_AMBIENT, 1.0f, 0.8f);
        }
    }

    private static final String FAMILIAR_SKULL_TEXTURE =
            "54e5a2321e639fdc9d42434aff3d7c674b4a88b2e45ed9f03723befecc9a3e7c";

    @SuppressWarnings("deprecation")
    private ItemStack createFamiliarHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "Familiar");
        PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(new URL(
                    "https://textures.minecraft.net/texture/" + FAMILIAR_SKULL_TEXTURE));
        } catch (MalformedURLException ex) {
            throw new RuntimeException("URL de textura inválida para familiar", ex);
        }
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    private Skeleton spawnFamiliar(Location spawnLoc, LivingEntity target) {
        Skeleton familiar = (Skeleton) spawnLoc.getWorld().spawnEntity(
                spawnLoc, EntityType.SKELETON);
        // Feature B: familiar sin nametag visible
        familiar.setCustomNameVisible(false);
        familiar.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
        familiar.setHealth(20.0);
        familiar.getAttribute(Attribute.SCALE).setBaseValue(0.75);
        familiar.setRemoveWhenFarAway(false);
        familiar.setTarget(target);
        familiar.getEquipment().setHelmet(createFamiliarHead());
        familiar.getEquipment().setHelmetDropChance(0);
        familiar.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "familiar"),
                PersistentDataType.BYTE, (byte) 1);
        return familiar;
    }

    private Location findGroundLocationRobust(Location origin) {
        World world = origin.getWorld();
        for (int attempt = 0; attempt < 8; attempt++) {
            double offsetX = random.nextDouble() * SUMMON_RADIUS * 2 - SUMMON_RADIUS;
            double offsetZ = random.nextDouble() * SUMMON_RADIUS * 2 - SUMMON_RADIUS;
            Location candidate = findSafeGroundAt(world,
                    (int)(origin.getX() + offsetX),
                    (int)(origin.getZ() + offsetZ),
                    (int) origin.getY());
            if (candidate != null) return candidate;
        }
        return findSafeGroundAt(world,
                origin.getBlockX() + 1, origin.getBlockZ() + 1, (int) origin.getY());
    }

    private Location findSafeGroundAt(World world, int x, int z, int referenceY) {
        for (int y = referenceY + 5; y >= referenceY - 10; y--) {
            Location feet  = new Location(world, x + 0.5, y,     z + 0.5);
            Location head  = new Location(world, x + 0.5, y + 1, z + 0.5);
            Location floor = new Location(world, x + 0.5, y - 1, z + 0.5);
            boolean feetClear = feet.getBlock().getType().isAir()
                    || !feet.getBlock().getType().isSolid();
            boolean headClear = head.getBlock().getType().isAir()
                    || !head.getBlock().getType().isSolid();
            boolean hasFloor  = floor.getBlock().getType().isSolid();
            if (feetClear && headClear && hasFloor) return feet;
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════════
    // AURA DE ROBO DE VIDA
    // ══════════════════════════════════════════════════════════════════

    private void startLifestealAura() {
        if (!active || entity == null || entity.isDead()) {
            auraWarning = false; return;
        }
        entity.getWorld().playSound(entity.getLocation(),
                Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.7f);

        new BukkitRunnable() {
            int warningTicks = 0;

            @Override
            public void run() {
                if (!active || entity == null || entity.isDead()) {
                    auraWarning = false; cancel(); return;
                }
                if (warningTicks >= 4) {
                    auraWarning = false; activateAura(); cancel(); return;
                }
                Location loc = entity.getLocation().add(0, 1, 0);
                for (int i = 0; i < 8; i++) {
                    double angle = (warningTicks * 20 + i * 45) * Math.PI / 180;
                    double x = loc.getX() + 2.0 * Math.cos(angle);
                    double z = loc.getZ() + 2.0 * Math.sin(angle);
                    loc.getWorld().spawnParticle(Particle.DUST,
                            new Location(loc.getWorld(), x, loc.getY(), z),
                            1, 0, 0, 0, 0,
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

        world.playSound(center, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.5f);
        world.playSound(center, Sound.ENTITY_WITHER_SHOOT,       1.0f, 1.2f);
        world.spawnParticle(Particle.EXPLOSION, center, 1);
        drawRedCircle(center, LIFESTEAL_AURA_RADIUS);

        new BukkitRunnable() {
            int seconds = 0;

            @Override
            public void run() {
                if (!active || entity.isDead() || seconds >= LIFESTEAL_AURA_DURATION) {
                    auraActive = false; cancel(); return;
                }
                double totalDamage = 0;
                for (Entity e : world.getNearbyEntities(
                        center, LIFESTEAL_AURA_RADIUS, 4, LIFESTEAL_AURA_RADIUS)) {
                    if (e instanceof Player p) {
                        double dmg = Math.min(p.getHealth(), LIFESTEAL_AURA_DAMAGE_PER_SECOND);
                        p.damage(dmg, entity);
                        totalDamage += dmg;
                        drawLifestealBeam(p, 15);
                    } else if (e instanceof Mob && !familiars.contains(e) && !e.equals(horse)) {
                        LivingEntity mob = (LivingEntity) e;
                        double dmg = Math.min(mob.getHealth(), LIFESTEAL_AURA_DAMAGE_PER_SECOND);
                        mob.damage(dmg, entity);
                        totalDamage += dmg;
                        drawLifestealBeam(mob, 15);
                    }
                }
                if (totalDamage > 0) {
                    entity.setHealth(Math.min(entity.getHealth() + totalDamage, MAX_HEALTH));
                    updateBossBar();
                }
                drawRedCircle(center, LIFESTEAL_AURA_RADIUS);
                seconds++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void drawRedCircle(Location center, double radius) {
        World world = center.getWorld();
        double angleStep = 2 * Math.PI / 36;
        for (int i = 0; i < 36; i++) {
            double angle = i * angleStep;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
            world.spawnParticle(Particle.DUST, new Location(world, x, y, z),
                    12, 0, 0, 0, 12,
                    new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1));
        }
    }

    private void drawLifestealBeam(LivingEntity victim, int duration) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration || !active
                        || entity == null || entity.isDead()
                        || victim == null || victim.isDead()) {
                    cancel(); return;
                }
                Location from = victim.getLocation().add(0, 1, 0);
                Location to   = entity.getLocation().add(0, 1.5, 0);
                double distance = from.distance(to);
                if (distance < 0.5) { cancel(); return; }

                Vector direction = to.toVector().subtract(from.toVector()).normalize();
                int points = (int) (distance * 4);

                for (int i = 0; i < points; i++) {
                    double t = (double) i / points;
                    Location particleLoc = from.clone().add(direction.clone().multiply(t * distance));
                    double wave = Math.sin(t * Math.PI * 4 + ticks * 0.6) * 0.15;
                    particleLoc.add(getPerpendicular(direction).multiply(wave));
                    int red  = (int)(255*(1-t) + 120*t);
                    int blue = (int)(0  *(1-t) + 220*t);
                    particleLoc.getWorld().spawnParticle(Particle.DUST, particleLoc,
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(red, 0, blue), 1.2f));
                    if (i % 4 == 0) {
                        particleLoc.getWorld().spawnParticle(Particle.SOUL, particleLoc,
                                1, 0.05, 0.05, 0.05, 0.001);
                    }
                }
                entity.getWorld().spawnParticle(Particle.DUST,
                        entity.getLocation().add(0, 1.5, 0), 3, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(120, 0, 220), 1.5f));
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Vector getPerpendicular(Vector direction) {
        Vector ref = (Math.abs(direction.getY()) < 0.9)
                ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        return direction.clone().crossProduct(ref).normalize();
    }

    // ══════════════════════════════════════════════════════════════════
    // DAÑO
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!event.getEntity().equals(entity)) return;
        double healthAfterDamage = Math.max(0, entity.getHealth() - event.getFinalDamage());
        if (auraActive || auraWarning) return;
        if (healthAfterDamage <= 0) return;
        if (healthAfterDamage > MAX_HEALTH * LIFESTEAL_AURA_TRIGGER_HEALTH_PERCENT) return;
        if (random.nextDouble() >= LIFESTEAL_AURA_CHANCE) return;
        auraWarning = true;
        Bukkit.getScheduler().runTask(plugin, this::startLifestealAura);
    }

    // ══════════════════════════════════════════════════════════════════
    // FASE 2
    // ══════════════════════════════════════════════════════════════════

    private void activatePhase2() {
        phase2Active = true;

        entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.RESISTANCE,
                Integer.MAX_VALUE, 1, false, true, true));
        entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE,
                Integer.MAX_VALUE, 1, false, true, true));

        Location loc   = entity.getLocation().clone();
        World    world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_WITHER_SPAWN,      1.0f, 0.6f);
        world.playSound(loc, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 2);
        world.spawnParticle(Particle.SOUL, loc.clone().add(0, 2, 0), 60, 2, 1, 2, 0.15);
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 80, 2, 1, 2, 0.1,
                new Particle.DustOptions(Color.fromRGB(180, 0, 255), 2.0f));

        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= 80 * 80) {
                p.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD
                        + "The Lich Skeleton grows desperate!");
            }
        }

        new BukkitRunnable() {
            double angle = 0; int ticks = 0;
            @Override public void run() {
                if (ticks >= 40 || !active || entity == null || entity.isDead()) {
                    cancel(); return;
                }
                double radius = 4.0 * Math.sin((ticks / 40.0) * Math.PI);
                for (int i = 0; i < 6; i++) {
                    double a = angle + i * (Math.PI / 3);
                    world.spawnParticle(Particle.DUST,
                            new Location(world,
                                    loc.getX() + radius * Math.cos(a),
                                    loc.getY() + 1.5 + Math.sin(ticks * 0.3 + i),
                                    loc.getZ() + radius * Math.sin(a)),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(220, 0, 100), 1.8f));
                }
                angle += 0.35; ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        plugin.getLogger().info("[NecromancerSkeleton] Fase 2 activada. HP: "
                + String.format("%.1f", entity.getHealth()) + "/" + MAX_HEALTH);
    }

    // ══════════════════════════════════════════════════════════════════
    // MUERTE
    // ══════════════════════════════════════════════════════════════════

    /**
     * Limpieza forzada cuando el flujo de muerte normal falla
     * (Stray montado — EntityDeathEvent puede no dispararse).
     */
    private void forceCleanup() {
        active = false;

        if (horse != null && horse.isValid()) horse.removePassenger(entity);

        familiars.removeIf(fam -> {
            if (fam != null && fam.isValid() && !fam.isDead()) fam.remove();
            return true;
        });

        if (entity != null && entity.isValid() && !entity.isDead()) {
            Location deathLoc = entity.getLocation();
            deathLoc.getWorld().spawnParticle(Particle.EXPLOSION,
                    deathLoc.clone().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0.1);
            deathLoc.getWorld().playSound(deathLoc,
                    Sound.ENTITY_SKELETON_DEATH, 1.0f, 0.8f);
            entity.remove();
        }

        if (horse != null && horse.isValid() && !horse.isDead()) {
            horse.setInvulnerable(false);
            horse.remove();
        }

        if (bossBar != null) bossBar.removeAll();
        releaseChunk();
    }

    @Override
    public void onDeath(EntityDeathEvent event) {
        active = false;
        rollDrops(event);
        forceCleanup();
    }
}