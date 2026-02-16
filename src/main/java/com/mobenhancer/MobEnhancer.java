package com.mobenhancer;

import com.mobenhancer.boss.BossSpawnManager;
import com.mobenhancer.cmd.MobSpawn;
import com.mobenhancer.cmd.Reload;
import com.mobenhancer.type.skeleton.Dasher;
import com.mobenhancer.type.skeleton.Grenadier;
import com.mobenhancer.type.skeleton.Invader;
import com.mobenhancer.type.skeleton.Skeleton_default;
import com.mobenhancer.type.skeleton.SpiderJockey;
import com.mobenhancer.type.zombie.Breaker;
import com.mobenhancer.type.zombie.Default;
import com.mobenhancer.type.zombie.Hopper;
import com.mobenhancer.type.zombie.Hydra;
import com.mobenhancer.type.zombie.Infected;
import com.mobenhancer.type.zombie.Latcher;
import com.mobenhancer.type.zombie.Pillar;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class MobEnhancer extends JavaPlugin {
    public static NamespacedKey zombieKey;
    public static NamespacedKey skeletonKey;
    public BossSpawnManager getBossSpawnManager() { return bossSpawnManager; }

    private BossSpawnManager bossSpawnManager;
    private List<ZombieCustomType> zombieTypes = List.of();
    private List<SkeletonCustomType> skeletonTypes = List.of();
    private final Random rng = new Random();

    @Override
    public void onEnable() {
        setupConfig();

        getCommand("MobEnhancer").setExecutor(new Reload());
        getCommand("mobspawn").setExecutor(new MobSpawn(this));

        zombieKey = new NamespacedKey(this, "zombieType");
        skeletonKey = new NamespacedKey(this, "skeletonType");

        zombieTypes = new ArrayList<>();
        skeletonTypes = new ArrayList<>();

        getServer().getPluginManager().registerEvents(new Listen(rng, this), this);
        getServer().getPluginManager().registerEvents(new CreeperControl(this), this);
        getServer().getPluginManager().registerEvents(new EndermanControl(this), this);
        getServer().getPluginManager().registerEvents(new SpiderControl(this), this);
        getServer().getPluginManager().registerEvents(new HordeControl(this), this);
        getServer().getPluginManager().registerEvents(new BabyZombieControl(), this);
        getServer().getPluginManager().registerEvents(new DespawnControl(this), this);

        // ZOMBIE TYPES

        registerType(new Default());
        registerType(new Breaker());
        registerType(new Hopper());
        registerType(new Infected());
        registerType(new Hydra());
        registerType(new Pillar());
        registerType(new Latcher());

        // SKELETON TYPES
        registerSkeletonType(new Skeleton_default());
        registerSkeletonType(new Invader());
        registerSkeletonType(new Dasher());
        registerSkeletonType(new Grenadier());
        registerSkeletonType(new SpiderJockey());

        bossSpawnManager = new BossSpawnManager(this);
    }

    public static MobEnhancer getInstance() {
        return JavaPlugin.getPlugin(MobEnhancer.class);
    }

    // Config
    public void setupConfig() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        if (!getConfig().contains("boss-spawn")) {
            getConfig().set("boss-spawn.enabled", true);
            getConfig().set("boss-spawn.min-players", 2);
            getConfig().set("boss-spawn.chance-per-second", 0.001);
            getConfig().set("boss-spawn.max-concurrent", 1);
            getConfig().set("boss-spawn.distance-min", 100);
            getConfig().set("boss-spawn.distance-max", 200);
            getConfig().set("boss-spawn.broadcast-message", "&cA abomination is about to appear at X: %x Y: %y Z: %z!");
        }
        saveConfig();
    }

    // ZOMBIE API
    public void registerType(ZombieCustomType type) {
        zombieTypes.add(type);
    }

    public List<ZombieCustomType> getZombieTypes() {
        return Collections.unmodifiableList(zombieTypes);
    }

    public void setZombieType(Zombie zombie, ZombieCustomType type) {
        zombie.getPersistentDataContainer().set(MobEnhancer.zombieKey, PersistentDataType.STRING, type.getId());
    }

    public ZombieCustomType getZombieType(Zombie zombie) {
        String typeKey = zombie.getPersistentDataContainer().get(MobEnhancer.zombieKey, PersistentDataType.STRING);
        return typeKey != null ? getZombieType(typeKey) : null;
    }

    public ZombieCustomType getZombieType(String id) {
        return zombieTypes.stream().filter(it -> it.getId().equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    public ZombieCustomType getRandomZombieType() {
        return zombieTypes.get(rng.nextInt(zombieTypes.size()));
    }

    // ===== SKELETON API =====
    public void registerSkeletonType(SkeletonCustomType type) {
        skeletonTypes.add(type);
    }

    public List<SkeletonCustomType> getSkeletonTypes() {
        return Collections.unmodifiableList(skeletonTypes);
    }

    public void setSkeletonType(Skeleton skeleton, SkeletonCustomType type) {
        skeleton.getPersistentDataContainer().set(skeletonKey, PersistentDataType.STRING, type.getId());
    }

    public SkeletonCustomType getSkeletonType(Skeleton skeleton) {
        String typeKey = skeleton.getPersistentDataContainer().get(skeletonKey, PersistentDataType.STRING);
        return typeKey != null ? getSkeletonType(typeKey) : null;
    }

    public SkeletonCustomType getSkeletonType(String id) {
        return skeletonTypes.stream().filter(it -> it.getId().equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    public SkeletonCustomType getRandomSkeletonType() {
        return skeletonTypes.get(rng.nextInt(skeletonTypes.size()));
    }

}