package com.example.residencewhitelist;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class ResidencePlaceWhitelist extends JavaPlugin implements CommandExecutor {

    private BlockPlaceListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        listener = new BlockPlaceListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        
        // 注册指令
        getCommand("rpw").setExecutor(this);
        
        getLogger().info("ResidencePlaceWhitelist 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().info("ResidencePlaceWhitelist 已禁用。");
    }

    public BlockPlaceListener getListener() {
        return listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("rpw")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                // 重载配置
                listener.reloadWhitelist();
                sender.sendMessage(ChatColor.GREEN + "[RPW] 白名单配置已成功重载！");
                return true;
            }
            sender.sendMessage(ChatColor.RED + "用法: /rpw reload");
            return true;
        }
        return false;
    }
}
