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
import java.util.Map;
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

        // 1. 主动遍历检查玩家是否在领地内且没有权限
        if (!isPlayerInResidenceWithoutPermission(player, loc)) {
            return; // 不在领地内，或者有权限，不需要我们管
        }

        Material placedType = block.getType();

        // 2. 检查物品是否在白名单中
        if (whitelist.contains(placedType)) {
            // 在白名单里，强行允许放置
            if (event.isCancelled()) {
                event.setCancelled(false);
                player.sendMessage(ChatColor.GREEN + "[RPW] 你放置了白名单物品: " + placedType.name());
            }
        } else {
            // 不在白名单，强行阻止放置（双保险，防止 Residence 罢工时保护失效）
            if (!event.isCancelled()) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You don't have place permissions here.");
            }
        }
    }

    /**
     * 通过遍历领地列表，主动检查玩家是否在领地内且没有 build 权限
     * 这种方式绕开了 getByLoc，彻底避免因缺少 WorldGuard 导致的崩溃
     */
    private boolean isPlayerInResidenceWithoutPermission(Player player, Location loc) {
        try {
            Plugin resPlugin = Bukkit.getPluginManager().getPlugin("Residence");
            if (resPlugin == null || !resPlugin.isEnabled()) return false;

            Object resManager = resPlugin.getClass()
                    .getMethod("getResidenceManager")
                    .invoke(resPlugin);

            // 获取所有领地
            Object residencesObj = resManager.getClass()
                    .getMethod("getResidences")
                    .invoke(resManager);

            if (residencesObj instanceof Map) {
                Map<?, ?> residences = (Map<?, ?>) residencesObj;
                for (Object resObj : residences.values()) {
                    // 检查这个位置是否在这个领地内
                    Boolean contains = (Boolean) resObj.getClass()
                            .getMethod("containsLoc", Location.class)
                            .invoke(resObj, loc);

                    if (contains != null && contains) {
                        // 玩家在这个领地内，检查 build 权限
                        Object perms = resObj.getClass()
                                .getMethod("getPermissions")
                                .invoke(resObj);
                        
                        Boolean hasPerm = (Boolean) perms.getClass()
                                .getMethod("playerHas", String.class, String.class, boolean.class)
                                .invoke(perms, player.getName(), "build", true);
                        
                        return !(hasPerm != null && hasPerm); // 如果没有权限则返回 true
                    }
                }
            }
        } catch (Throwable t) {
            // 如果遍历出错（极少情况），保守返回 false，不干预
        }
        return false; // 默认不在领地内
    }
}
