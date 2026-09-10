package com.example.residencewhitelist;

import org.bukkit.plugin.java.JavaPlugin;

public class ResidencePlaceWhitelist extends JavaPlugin {

    private BlockPlaceListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        listener = new BlockPlaceListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        getLogger().info("ResidencePlaceWhitelist 已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().info("ResidencePlaceWhitelist 已禁用。");
    }

    public BlockPlaceListener getListener() {
        return listener;
    }
}
