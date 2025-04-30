package com.mobenhancer.type;

import com.mobenhancer.CustomType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Breaker implements CustomType {
    private static final Set<Material> BREAKABLE = new HashSet<>(Arrays.asList(
// SOIL
    Material.STONE,
    Material.DIRT,
    Material.GRASS_BLOCK,
    Material.COARSE_DIRT,
    Material.GRAVEL,
    Material.SAND,
    Material.RED_SAND,
    Material.CLAY,
    Material.SANDSTONE,
    Material.RED_SANDSTONE,
    Material.PODZOL,
    Material.NETHERRACK,
    Material.MYCELIUM,
// WOODS
    Material.OAK_WOOD,
    Material.SPRUCE_WOOD,
    Material.BIRCH_WOOD,
    Material.JUNGLE_WOOD,
    Material.ACACIA_WOOD,
    Material.DARK_OAK_WOOD,
    Material.PALE_OAK_WOOD,
    Material.CHERRY_WOOD,
    Material.MANGROVE_WOOD,
    Material.CRIMSON_PLANKS,
    Material.WARPED_PLANKS,
    Material.BAMBOO_PLANKS,
// FENCES
    Material.OAK_FENCE,
    Material.SPRUCE_FENCE,
    Material.BIRCH_FENCE,
    Material.JUNGLE_FENCE,
    Material.ACACIA_FENCE,
    Material.DARK_OAK_FENCE,
    Material.PALE_OAK_FENCE,
    Material.CHERRY_FENCE,
    Material.MANGROVE_FENCE,
    Material.CRIMSON_FENCE,
    Material.WARPED_FENCE,
    Material.BAMBOO_FENCE,
// GATES
    Material.OAK_FENCE_GATE,
    Material.SPRUCE_FENCE_GATE,
    Material.BIRCH_FENCE_GATE,
    Material.JUNGLE_FENCE_GATE,
    Material.ACACIA_FENCE_GATE,
    Material.DARK_OAK_FENCE_GATE,
    Material.PALE_OAK_FENCE_GATE,
    Material.CHERRY_FENCE_GATE,
    Material.MANGROVE_FENCE_GATE,
    Material.CRIMSON_FENCE_GATE,
    Material.WARPED_FENCE_GATE,
    Material.BAMBOO_FENCE_GATE,
// DOORS
    Material.OAK_DOOR,
    Material.SPRUCE_DOOR,
    Material.BIRCH_DOOR,
    Material.JUNGLE_DOOR,
    Material.ACACIA_DOOR,
    Material.DARK_OAK_DOOR,
    Material.PALE_OAK_DOOR,
    Material.CHERRY_DOOR,
    Material.MANGROVE_DOOR,
    Material.CRIMSON_DOOR,
    Material.WARPED_DOOR,
    Material.BAMBOO_DOOR,
    Material.IRON_DOOR,
// GLASS
    Material.GLASS,
    Material.GLASS_PANE,
    Material.WHITE_STAINED_GLASS,
    Material.ORANGE_STAINED_GLASS,
    Material.MAGENTA_STAINED_GLASS,
    Material.LIGHT_BLUE_STAINED_GLASS,
    Material.YELLOW_STAINED_GLASS,
    Material.LIME_STAINED_GLASS,
    Material.PINK_STAINED_GLASS,
    Material.GRAY_STAINED_GLASS,
    Material.LIGHT_GRAY_STAINED_GLASS,
    Material.CYAN_STAINED_GLASS,
    Material.PURPLE_STAINED_GLASS,
    Material.BLUE_STAINED_GLASS,
    Material.BROWN_STAINED_GLASS,
    Material.GREEN_STAINED_GLASS,
    Material.RED_STAINED_GLASS,
    Material.BLACK_STAINED_GLASS,
    Material.WHITE_STAINED_GLASS_PANE,
    Material.ORANGE_STAINED_GLASS_PANE,
    Material.MAGENTA_STAINED_GLASS_PANE,
    Material.LIGHT_BLUE_STAINED_GLASS_PANE,
    Material.YELLOW_STAINED_GLASS_PANE,
    Material.LIME_STAINED_GLASS_PANE,
    Material.PINK_STAINED_GLASS_PANE,
    Material.GRAY_STAINED_GLASS_PANE,
    Material.LIGHT_GRAY_STAINED_GLASS_PANE,
    Material.CYAN_STAINED_GLASS_PANE,
    Material.PURPLE_STAINED_GLASS_PANE,
    Material.BLUE_STAINED_GLASS_PANE,
    Material.BROWN_STAINED_GLASS_PANE,
    Material.GREEN_STAINED_GLASS_PANE,
    Material.RED_STAINED_GLASS_PANE,
    Material.BLACK_STAINED_GLASS_PANE,
// WOOLS
    Material.WHITE_WOOL,
    Material.ORANGE_WOOL,
    Material.MAGENTA_WOOL,
    Material.LIGHT_BLUE_WOOL,
    Material.YELLOW_WOOL,
    Material.LIME_WOOL,
    Material.PINK_WOOL,
    Material.GRAY_WOOL,
    Material.LIGHT_GRAY_WOOL,
    Material.CYAN_WOOL,
    Material.PURPLE_WOOL,
    Material.BLUE_WOOL,
    Material.BROWN_WOOL,
    Material.GREEN_WOOL,
    Material.RED_WOOL,
    Material.BLACK_WOOL
    ));
    
    private static final String BREAKER_TEXTURE_HASH = "6951d6c3efc5e702307e1b8990afdf88cea37b763f1a2ce574a8c69156820bfb";

    @Override
    public String getId() {
        return "breaker";
    }

    @Override
    public String getName() {
        return "Breaker";
    }

    @Override
    public void onSpawn(Zombie zombie, CreatureSpawnEvent e) {
        ItemStack breakerHead = createBreakerHead();
        // Apariencia identificativa
        zombie.getEquipment().setItemInOffHand(new ItemStack(Material.IRON_AXE));
        zombie.getEquipment().setItemInOffHandDropChance(0);
        zombie.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SHOVEL));
        zombie.getEquipment().setItemInMainHandDropChance(0);
        zombie.getEquipment().setHelmet(breakerHead);
        zombie.getEquipment().setHelmetDropChance(0);

        zombie.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 3, false, false));
        
        // Iniciar IA de destrucción
        startBreakerAI(zombie);
    }

    private void startBreakerAI(Zombie zombie) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!zombie.isValid()) {
                    cancel();
                    return;   
                }
                
                if (zombie.getTarget() instanceof Player && zombie.getLocation().distance(zombie.getTarget().getLocation()) < 10)
                {
                    Player target = (Player) zombie.getTarget();
                    createPath(zombie, target.getLocation());
                }
            }
        }.runTaskTimer(com.mobenhancer.MobEnhancer.getInstance(), 0L, 30L); // 1.5 segundos
    }

    private void createPath(Zombie breaker, Location targetLoc) {
        Vector direction = targetLoc.toVector().subtract(breaker.getLocation().toVector()).normalize();
        
        // Verificar 8 bloques hacia adelante
        for (int i = 1; i <= 8; i++) {
            Location checkLoc = breaker.getLocation().add(direction.clone().multiply(i));
            
            // Bloque principal y superior
            Block mainBlock = checkLoc.getBlock();
            Block upperBlock = checkLoc.clone().add(0, 1, 0).getBlock();
            
            if (shouldBreak(mainBlock)) {
                breakBlocks(breaker, mainBlock, upperBlock);
                break; // Priorizar primer obstáculo
            }
        }
    }

    private boolean shouldBreak(Block block) {
        return BREAKABLE.contains(block.getType()) && 
               block.getType().isSolid() && 
               !block.isEmpty();
    }

    private void breakBlocks(Zombie breaker, Block... blocks) {
        for (Block block : blocks) {
            if (shouldBreak(block)) {
                breaker.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());
                breaker.getWorld().playSound(
                    block.getLocation(), 
                    Sound.BLOCK_STONE_BREAK, 
                    1.5f, 
                    0.7f
                );
                block.setType(Material.AIR);
            }
        }
    }

    @Override
    public void onDeath(Zombie zombie, EntityDeathEvent e) {
        // Efecto de partículas al morir
        zombie.getWorld().spawnParticle(
            Particle.SMOKE, 
            zombie.getLocation().add(0, 1, 0), 
            15,
            0.3, 0.5, 0.3,
            0.1
        );
    }

    @SuppressWarnings("deprecation")
    private ItemStack createBreakerHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "Breaker");
        PlayerTextures textures = profile.getTextures();
        
        try {
            URL skinUrl = new URL("https://textures.minecraft.net/texture/" + BREAKER_TEXTURE_HASH);
            textures.setSkin(skinUrl);
        } catch (MalformedURLException ex) {
            throw new RuntimeException("URL de textura inválida", ex);
        }
        
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
        head.setItemMeta(meta);
        
        return head;
    }
}