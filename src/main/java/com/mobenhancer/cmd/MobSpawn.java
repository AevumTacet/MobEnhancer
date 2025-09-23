package com.mobenhancer.cmd;

import com.mobenhancer.MobEnhancer;
import com.mobenhancer.SkeletonCustomType;
import com.mobenhancer.ZombieCustomType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class MobSpawn implements CommandExecutor, TabExecutor {
    private final MobEnhancer instance;

    public MobSpawn(MobEnhancer instance) {
        this.instance = instance;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (!(commandSender instanceof Player p)) {
            commandSender.sendMessage("§cOnly in-game players can use this command!");
            return true;
        }

        if (strings.length == 0) {
            showHelp(commandSender);
            return true;
        }

        String mobType = strings[0].toLowerCase();

        if (strings.length == 1) {
            // Mostrar tipos disponibles para el mob especificado
            showAvailableTypes(commandSender, mobType);
            return true;
        }

        // Spawnear el mob con el tipo específico
        return spawnCustomMob(commandSender, p, mobType, strings[1].toLowerCase());
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§eUsage: /mobspawn <zombie|skeleton> [type]");
        sender.sendMessage("§eAvailable mobs: zombie, skeleton");
        sender.sendMessage("§eUse '/mobspawn <mob>' to see available types for that mob.");
    }

    private void showAvailableTypes(CommandSender sender, String mobType) {
        switch (mobType) {
            case "zombie":
                List<String> zombieTypes = instance.getZombieTypes().stream()
                        .map(ZombieCustomType::getId)
                        .collect(Collectors.toList());
                sender.sendMessage("§eAvailable zombie types: " + String.join(", ", zombieTypes));
                break;

            case "skeleton":
                List<String> skeletonTypes = instance.getSkeletonTypes().stream()
                        .map(SkeletonCustomType::getId)
                        .collect(Collectors.toList());
                sender.sendMessage("§eAvailable skeleton types: " + String.join(", ", skeletonTypes));
                break;

            default:
                sender.sendMessage("§cUnknown mob type: " + mobType);
                sender.sendMessage("§cAvailable mobs: zombie, skeleton");
                break;
        }
    }

    private boolean spawnCustomMob(CommandSender sender, Player player, String mobType, String typeId) {
        switch (mobType) {
            case "zombie":
                ZombieCustomType zombieType = instance.getZombieType(typeId);
                if (zombieType == null) {
                    sender.sendMessage("§cZombie type not found: " + typeId);
                    return true;
                }

                sender.sendMessage("§aSpawned " + zombieType.getName() + " (" + zombieType.getId() + ") zombie.");
                player.getWorld().spawn(player.getLocation(), Zombie.class,
                        zombie -> zombie.getPersistentDataContainer().set(
                                MobEnhancer.zombieKey, PersistentDataType.STRING, zombieType.getId()));
                return true;

            case "skeleton":
                SkeletonCustomType skeletonType = instance.getSkeletonType(typeId);
                if (skeletonType == null) {
                    sender.sendMessage("§cSkeleton type not found: " + typeId);
                    return true;
                }

                sender.sendMessage("§aSpawned " + skeletonType.getName() + " (" + skeletonType.getId() + ") skeleton.");
                player.getWorld().spawn(player.getLocation(), Skeleton.class,
                        skeleton -> skeleton.getPersistentDataContainer().set(
                                MobEnhancer.skeletonKey, PersistentDataType.STRING, skeletonType.getId()));
                return true;

            default:
                sender.sendMessage("§cUnknown mob type: " + mobType);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        List<String> completions = new ArrayList<>();

        if (strings.length == 1) {
            // Sugerir "zombie" o "skeleton" para el primer argumento
            completions.add("zombie");
            completions.add("skeleton");
        } else if (strings.length == 2) {
            // Sugerir tipos específicos según el mob seleccionado
            String mobType = strings[0].toLowerCase();

            switch (mobType) {
                case "zombie":
                    completions.addAll(instance.getZombieTypes().stream()
                            .map(ZombieCustomType::getId)
                            .collect(Collectors.toList()));
                    break;

                case "skeleton":
                    completions.addAll(instance.getSkeletonTypes().stream()
                            .map(SkeletonCustomType::getId)
                            .collect(Collectors.toList()));
                    break;
            }
        }

        return completions;
    }
}