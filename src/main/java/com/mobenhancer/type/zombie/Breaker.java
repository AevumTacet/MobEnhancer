package com.mobenhancer.type.zombie;

import com.mobenhancer.ZombieCustomType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
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

public class Breaker implements ZombieCustomType {
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
            Material.DRIPSTONE_BLOCK,
            Material.POINTED_DRIPSTONE,
            Material.DEEPSLATE,
            Material.CALCITE,
            Material.TUFF,
            Material.COBBLED_DEEPSLATE,
            Material.COBBLESTONE,
            // PLANKS
            Material.OAK_PLANKS,
            Material.SPRUCE_PLANKS,
            Material.BIRCH_PLANKS,
            Material.JUNGLE_PLANKS,
            Material.ACACIA_PLANKS,
            Material.DARK_OAK_PLANKS,
            Material.PALE_OAK_PLANKS,
            Material.CHERRY_PLANKS,
            Material.MANGROVE_PLANKS,
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
            Material.BLACK_WOOL));

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
        zombie.setAdult();

        ItemStack breakerHead = createBreakerHead();
        // Apariencia identificativa
        zombie.getEquipment().setHelmet(breakerHead);
        zombie.getEquipment().setHelmetDropChance(0);
        /*
         * zombie.getEquipment().setItemInOffHand(new ItemStack(Material.IRON_AXE));
         * zombie.getEquipment().setItemInOffHandDropChance(0);
         * zombie.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SHOVEL));
         * zombie.getEquipment().setItemInMainHandDropChance(0);
         */

        zombie.addPotionEffect(
                new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 2, false, false));

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

                if (zombie.getTarget() instanceof Player
                        && zombie.getLocation().distance(zombie.getTarget().getLocation()) < 12) {
                    Player target = (Player) zombie.getTarget();
                    createPath(zombie, target.getLocation());
                }
            }
        }.runTaskTimer(com.mobenhancer.MobEnhancer.getInstance(), 0L, 40L); // 2 segundos
    }

    private void createPath(Zombie breaker, Location targetLoc) {
        Vector direction = targetLoc.toVector().subtract(breaker.getLocation().toVector()).normalize();

        double heightDifference = targetLoc.getY() - breaker.getLocation().getY();

        if (heightDifference >= 2.0) {
            createUpwardPath(breaker, direction);
        } else if (heightDifference <= -2.0) {
            createDownwardPath(breaker, direction);
        } else {
            createNormalPath(breaker, direction);
        }
    }

    private void createNormalPath(Zombie breaker, Vector direction) {
        Location checkLoc = breaker.getLocation().add(direction);
        Block mainBlock = checkLoc.clone().add(0, 0.5, 0).getBlock();
        Block upperBlock = checkLoc.clone().add(0, 1, 0).getBlock();

        if (shouldBreak(mainBlock)) {
            startBreakingAnimation(breaker, mainBlock, upperBlock);
        }
    }

    private void createUpwardPath(Zombie breaker, Vector direction) {
        Location eyeLevel = breaker.getLocation().add(0, 1.1, 0);
        Location eyeCheckLoc = eyeLevel.add(direction);
        Block eyeLevelBlock = eyeCheckLoc.getBlock();
        Block aboveEyeBlock = eyeCheckLoc.clone().add(0, 1, 0).getBlock();

        Location feetCheckLoc = breaker.getLocation().add(direction);
        Block feetLevelBlock = feetCheckLoc.getBlock();
        Block aboveFeetBlock = feetCheckLoc.clone().add(0, 1, 0).getBlock();

        if (shouldBreak(eyeLevelBlock)) {
            startBreakingAnimation(breaker, eyeLevelBlock, aboveEyeBlock);
        } else if (shouldBreak(feetLevelBlock)) {
            startBreakingAnimation(breaker, feetLevelBlock, aboveFeetBlock);
        }
    }

    private void createDownwardPath(Zombie breaker, Vector direction) {
        Location forwardLoc = breaker.getLocation().add(direction);
        Block forwardBlock = forwardLoc.getBlock();

        Location belowForwardLoc = forwardLoc.clone().add(0, -1, 0);
        Block belowForwardBlock = belowForwardLoc.getBlock();

        Location belowFeetLoc = breaker.getLocation().add(0, -1, 0);
        Block belowFeetBlock = belowFeetLoc.getBlock();

        if (shouldBreak(belowForwardBlock)) {
            startBreakingAnimation(breaker, belowForwardBlock, forwardBlock);
        } else if (shouldBreak(forwardBlock)) {
            startBreakingAnimation(breaker, forwardBlock, null);
        } else if (shouldBreak(belowFeetBlock)) {
            Location deepCheck = belowFeetLoc.clone().add(0, -2, 0);
            if (!deepCheck.getBlock().isEmpty()) {
                startBreakingAnimation(breaker, belowFeetBlock, null);
            }
        }
    }

    private void startBreakingAnimation(Zombie breaker, Block mainBlock, Block upperBlock) {
        // 1. Animación de brazos - el zombie golpea con sus herramientas
        breaker.swingMainHand();
        breaker.swingOffHand();

        // 2. Partículas de destrucción en los bloques objetivo
        spawnBreakParticles(mainBlock);
        if (shouldBreak(upperBlock)) {
            spawnBreakParticles(upperBlock);
        }

        // 3. Sonido de "carga" o preparación
        breaker.getWorld().playSound(
                breaker.getLocation(),
                Sound.BLOCK_STONE_HIT,
                1.0f,
                0.8f);

        // 4. Programar la destrucción real después de 20 ticks
            new BukkitRunnable() {
            @Override
            public void run() {
                if (breaker.isValid() &&
                        (shouldBreak(mainBlock) || (upperBlock != null && shouldBreak(upperBlock)))) {  // ← AÑADIDO null check
                    breakBlocks(breaker, mainBlock, upperBlock);
                }
            }
        }.runTaskLater(com.mobenhancer.MobEnhancer.getInstance(), 20L);
    }

    private void spawnBreakParticles(Block block) {
        Location particleLoc = block.getLocation().add(0.5, 0.5, 0.5); // Centro del bloque
        block.getWorld().spawnParticle(
                Particle.BLOCK_CRUMBLE,
                particleLoc,
                8, // Cantidad de partículas
                0.3, 0.3, 0.3, // Offset
                0.1, // Velocidad
                block.getBlockData() // Datos del bloque para partículas precisas
        );

        // Partículas adicionales de efecto
        block.getWorld().spawnParticle(
                Particle.SMOKE,
                particleLoc,
                3,
                0.2, 0.2, 0.2,
                0.05);
    }

    private boolean shouldBreak(Block block) {
        if (block == null) return false;
            return BREAKABLE.contains(block.getType()) &&
                block.getType().isSolid() &&
                !block.isEmpty();
    }

    private void breakBlocks(Zombie breaker, Block... blocks) {
        for (Block block : blocks) {
            if (block != null && shouldBreak(block)) {
                // Efecto visual de destrucción
                breaker.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());

                // Sonido de destrucción específico del material
                playBreakSound(breaker, block);

                // Destruir el bloque
                block.setType(Material.AIR);

                // Partículas finales de destrucción
                block.getWorld().spawnParticle(
                        Particle.BLOCK_CRUMBLE,
                        block.getLocation().add(0.5, 0.5, 0.5),
                        12,
                        0.3, 0.3, 0.3,
                        0.1,
                        block.getType().createBlockData());
            }
        }
    }

    private void playBreakSound(Zombie breaker, Block block) {
        Sound breakSound = Sound.BLOCK_STONE_BREAK;
        Material blockType = block.getType();

        // Determinar el sonido apropiado según el tipo de bloque
        if (blockType.name().contains("GLASS")) {
            breakSound = Sound.BLOCK_GLASS_BREAK;
        } else if (blockType.name().contains("WOOD") ||
                blockType.name().contains("FENCE") ||
                blockType.name().contains("GATE") ||
                blockType.name().contains("DOOR") ||
                blockType.name().contains("PLANKS")) {
            breakSound = Sound.BLOCK_WOOD_BREAK;
        } else if (blockType.name().contains("WOOL")) {
            breakSound = Sound.BLOCK_WOOL_BREAK;
        } else if (blockType.name().contains("STONE") ||
                blockType.name().contains("CALCITE") ||
                blockType.name().contains("DEEPSLATE") ||
                blockType.name().contains("TUFF")) {
            breakSound = Sound.BLOCK_STONE_BREAK;
        } else {
            breakSound = Sound.BLOCK_GRASS_BREAK;
        }

        breaker.getWorld().playSound(
                block.getLocation(),
                breakSound,
                1.5f,
                0.7f);
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