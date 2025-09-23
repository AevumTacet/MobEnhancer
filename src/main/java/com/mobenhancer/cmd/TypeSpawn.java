package com.mobenhancer.cmd;

import com.mobenhancer.ZombieCustomType;
import com.mobenhancer.MobEnhancer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class TypeSpawn implements CommandExecutor, TabExecutor {
    private final MobEnhancer instance;

    public TypeSpawn(MobEnhancer instance) {
        this.instance = instance;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (!(commandSender instanceof Player p)) {
            commandSender.sendMessage("§cOnly in-game players can use this command!");
            return true;
        }

        if (strings.length == 0) {
            commandSender.sendMessage("§eRegistered types: "
                    + instance.getZombieTypes().stream().map(ZombieCustomType::getId)
                            .collect(Collectors.joining(", ")));
            return true;
        }

        ZombieCustomType type = MobEnhancer.getInstance().getZombieType(strings[0].toLowerCase());
        if (type == null) {
            commandSender.sendMessage("§cZombie type not found.");
            return true;
        }

        commandSender.sendMessage("§aSpawned " + type.getName() + " (" + type.getId() + ") type zombie.");
        p.getWorld().spawn(p.getLocation(), Zombie.class,
                it -> it.getPersistentDataContainer().set(MobEnhancer.zombieKey, PersistentDataType.STRING,
                        type.getId()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        return instance.getZombieTypes().stream().map(ZombieCustomType::getId).toList();
    }
}
