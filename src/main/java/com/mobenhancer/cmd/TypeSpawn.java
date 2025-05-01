package com.mobenhancer.cmd;

import com.mobenhancer.CustomType;
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
            commandSender.sendMessage("§eRegistered types: " + instance.getTypes().stream().map(CustomType::getId).collect(Collectors.joining(", ")));
            return true;
        }

        CustomType type = MobEnhancer.getInstance().getType(strings[0].toLowerCase());
        if (type == null) {
            commandSender.sendMessage("§cZombie type not found.");
            return true;
        }

        commandSender.sendMessage("§aSpawned " + type.getName() + " (" + type.getId() + ") type zombie.");
        p.getWorld().spawn(p.getLocation(), Zombie.class, it -> it.getPersistentDataContainer().set(MobEnhancer.key, PersistentDataType.STRING, type.getId()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        return instance.getTypes().stream().map(CustomType::getId).toList();
    }
}
