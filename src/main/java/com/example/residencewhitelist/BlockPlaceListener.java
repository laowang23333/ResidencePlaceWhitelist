package com.example.residencewhitelist;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockPlaceListener implements Listener {

    private final ResidencePlaceWhitelist plugin;
    private final Set<Material> whitelist = new HashSet<>();

    public BlockPlaceListener(ResidencePlaceWhitelist plugin) {
        this.plugin = plugin;
        loadWhitelist();
    }

    private void loadWhitelist() {
        whitelist.clear();
        List<String> list = plugin.getConfig().getStringList("whitelist");
        for (String entry : list) {
            Material mat = Material.matchMaterial(entry);
            if (mat != null) {
                whitelist.add(mat);
            } else {
                plugin.getLogger().warning("未知物品: " + entry);
            }
        }
        plugin.getLogger().info("已加载 " + whitelist.size() + " 个白名单物品。");
    }

    public void reloadWhitelist() {
        plugin.reloadConfig();
        loadWhitelist();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Block block = event.getBlockPlaced();
        if (block == null) return;

        Location loc = block.getLocation();

        // 1. 通过反射获取 Residence 领地
        Object residence = getResidenceAt(loc);
        if (residence == null) return; // 不在领地内

        // 2. 检查玩家是否有 build 权限
        boolean hasPermission = playerHasBuildPermission(residence, player.getName());
        if (hasPermission) return;

        // 3. 检查物品是否在白名单
        Material placedType = block.getType();
        if (!whitelist.contains(placedType)) return;

        // 4. 白名单物品，覆盖 Residence 的取消
        if (event.isCancelled()) {
            event.setCancelled(false);
            player.sendMessage(ChatColor.GREEN + "[RPW] 你放置了白名单物品: " + placedType.name());
        }
    }

    /**
     * 通过反射调用 Residence API 获取位置所在领地
     */
    private Object getResidenceAt(Location loc) {
        try {
            Plugin resPlugin = Bukkit.getPluginManager().getPlugin("Residence");
            if (resPlugin == null || !resPlugin.isEnabled()) return null;

            Object resManager = resPlugin.getClass()
                    .getMethod("getResidenceManager")
                    .invoke(resPlugin);

            return resManager.getClass()
                    .getMethod("getByLoc", Location.class)
                    .invoke(resManager, loc);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 通过反射调用 playerHas(name, "build", true)
     */
    private boolean playerHasBuildPermission(Object residence, String playerName) {
        try {
            Object permissions = residence.getClass()
                    .getMethod("getPermissions")
                    .invoke(residence);

            Object result = permissions.getClass()
                    .getMethod("playerHas", String.class, String.class, boolean.class)
                    .invoke(permissions, playerName, "build", true);

            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            // 反射失败时保守返回 true，不干预原有拦截
            return true;
        }
    }
}
