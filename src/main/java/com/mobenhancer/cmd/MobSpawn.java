package com.mobenhancer.cmd;

import com.mobenhancer.MobEnhancer;
import com.mobenhancer.SkeletonCustomType;
import com.mobenhancer.ZombieCustomType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MobSpawn implements TabExecutor {
    private final MobEnhancer plugin;

    public MobSpawn(MobEnhancer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String mobType = args[0].toLowerCase();

        if (args.length == 1) {
            showAvailableTypes(sender, mobType);
            return true;
        }

        switch (mobType) {
            case "zombie":
                return spawnZombie(player, args[1].toLowerCase());
            case "skeleton":
                return spawnSkeleton(player, args[1].toLowerCase());
            case "boss":
                if (args[1].equalsIgnoreCase("event")) {
                    return spawnBossEvent(player, args.length >= 3 ? args[2].toLowerCase() : null);
                }
                return spawnBoss(player, args[1].toLowerCase());
            default:
                sender.sendMessage("§cUnknown mob type: " + mobType);
                return true;
        }
    }

    private boolean spawnBossEvent(Player player, String bossId) {
        if (plugin.getBossSpawnManager() == null) {
            player.sendMessage("§cBoss system is not enabled.");
            return true;
        }
        plugin.getBossSpawnManager().forceSpawnEvent(player, bossId);
        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§eUsage: /mobspawn <zombie|skeleton|boss> [type]");
        sender.sendMessage("§eUse '/mobspawn <mob>' to see available types.");
    }

    private void showAvailableTypes(CommandSender sender, String mobType) {
        switch (mobType) {
            case "zombie":
                List<String> zombieTypes = plugin.getZombieTypes().stream()
                        .map(ZombieCustomType::getId)
                        .collect(Collectors.toList());
                sender.sendMessage("§eAvailable zombie types: " + String.join(", ", zombieTypes));
                break;
            case "skeleton":
                List<String> skeletonTypes = plugin.getSkeletonTypes().stream()
                        .map(SkeletonCustomType::getId)
                        .collect(Collectors.toList());
                sender.sendMessage("§eAvailable skeleton types: " + String.join(", ", skeletonTypes));
                break;
            case "boss":
                if (plugin.getBossSpawnManager() != null) {
                    List<String> bossIds = plugin.getBossSpawnManager().getAvailableBossIds();
                    sender.sendMessage("§eAvailable bosses: " + String.join(", ", bossIds));
                } else {
                    sender.sendMessage("§cBoss system is disabled.");
                }
                break;
            default:
                sender.sendMessage("§cUnknown mob type: " + mobType);
        }
    }

    private boolean spawnZombie(Player player, String typeId) {
        var type = plugin.getZombieType(typeId);
        if (type == null) {
            player.sendMessage("§cZombie type not found: " + typeId);
            return true;
        }
        player.sendMessage("§aSpawned " + type.getName() + " (" + type.getId() + ") zombie.");
        player.getWorld().spawn(player.getLocation(), Zombie.class,
                zombie -> zombie.getPersistentDataContainer().set(
                        MobEnhancer.zombieKey, PersistentDataType.STRING, type.getId()));
        return true;
    }

    private boolean spawnSkeleton(Player player, String typeId) {
        var type = plugin.getSkeletonType(typeId);
        if (type == null) {
            player.sendMessage("§cSkeleton type not found: " + typeId);
            return true;
        }
        player.sendMessage("§aSpawned " + type.getName() + " (" + type.getId() + ") skeleton.");
        player.getWorld().spawn(player.getLocation(), Skeleton.class,
                skeleton -> skeleton.getPersistentDataContainer().set(
                        MobEnhancer.skeletonKey, PersistentDataType.STRING, type.getId()));
        return true;
    }

    private boolean spawnBoss(Player player, String bossId) {
        if (plugin.getBossSpawnManager() == null) {
            player.sendMessage("§cBoss system is not enabled.");
            return true;
        }
        // Ahora pasamos primero la ubicación y luego el ID
        boolean success = plugin.getBossSpawnManager().spawnBoss(player.getLocation(), bossId);
        if (success) {
            player.sendMessage("§aSpawned boss: " + bossId);
        } else {
            player.sendMessage("§cBoss not found: " + bossId);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("zombie");
            completions.add("skeleton");
            if (plugin.getBossSpawnManager() != null) {
                completions.add("boss");
            }
        } else if (args.length == 2) {
            String mobType = args[0].toLowerCase();
            switch (mobType) {
                case "zombie":
                    completions.addAll(plugin.getZombieTypes().stream()
                            .map(ZombieCustomType::getId)
                            .collect(Collectors.toList()));
                    break;
                case "skeleton":
                    completions.addAll(plugin.getSkeletonTypes().stream()
                            .map(SkeletonCustomType::getId)
                            .collect(Collectors.toList()));
                    break;
                case "boss":
                    completions.add("event"); // siempre disponible
                    if (plugin.getBossSpawnManager() != null) {
                        completions.addAll(plugin.getBossSpawnManager().getAvailableBossIds());
                    }
                    break;
                
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("boss") 
                                    && args[1].equalsIgnoreCase("event")) {
            if (plugin.getBossSpawnManager() != null) {
                completions.addAll(plugin.getBossSpawnManager().getAvailableBossIds());
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}