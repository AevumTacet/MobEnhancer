package com.mobenhancer.cmd;

import com.mobenhancer.MobEnhancer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class Reload implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        commandSender.sendMessage("§aMobEnhancer Config reloaded!");
        MobEnhancer.getInstance().reloadConfig();
        return true;
    }
}
