package com.example.residencewhitelist;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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

    // ==================== 监听方块放置 ====================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Block block = event.getBlockPlaced();
        if (block == null) return;

        Location loc = block.getLocation();

        // 主动检查：玩家在领地内且没有 build 权限
        if (!isPlayerInResidenceWithoutPermission(player, loc, "build")) {
            return; // 不在领地内，或者有权限，不需要我们管
        }

        Material placedType = block.getType();

        // 白名单物品，强行允许放置
        if (whitelist.contains(placedType)) {
            if (event.isCancelled()) {
                event.setCancelled(false);
                player.sendMessage(ChatColor.GREEN + "[RPW] 你放置了白名单物品: " + placedType.name());
            }
        } else {
            // 非白名单物品，强行阻止（双保险）
            if (!event.isCancelled()) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You don't have place permissions here.");
            }
        }
    }

    // ==================== 监听打开容器 ====================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        if (!(block.getState() instanceof Container)) return;

        Location loc = block.getLocation();

        // 主动检查：玩家在领地内且没有 container 权限
        if (!isPlayerInResidenceWithoutPermission(player, loc, "container")) {
            return; // 有权限，正常打开
        }

        // 如果没有权限，强行拦截
        if (!event.isCancelled()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You don't have container permissions here.");
        }
    }

    /**
     * 通过遍历领地列表，主动检查玩家是否在领地内且没有指定权限
     */
    private boolean isPlayerInResidenceWithoutPermission(Player player, Location loc, String permission) {
        try {
            Plugin resPlugin = Bukkit.getPluginManager().getPlugin("Residence");
            if (resPlugin == null || !resPlugin.isEnabled()) return false;

            Object resManager = resPlugin.getClass()
                    .getMethod("getResidenceManager")
                    .invoke(resPlugin);

            Object residencesObj = resManager.getClass()
                    .getMethod("getResidences")
                    .invoke(resManager);

            if (residencesObj instanceof Map) {
                Map<?, ?> residences = (Map<?, ?>) residencesObj;
                for (Object resObj : residences.values()) {
                    Boolean contains = (Boolean) resObj.getClass()
                            .getMethod("containsLoc", Location.class)
                            .invoke(resObj, loc);

                    if (contains != null && contains) {
                        Object perms = resObj.getClass()
                                .getMethod("getPermissions")
                                .invoke(resObj);
                        
                        // 【关键修改】最后一个参数改为 false，表示玩家如果没明确设置权限，就默认是没权限
                        Boolean hasPerm = (Boolean) perms.getClass()
                                .getMethod("playerHas", String.class, String.class, boolean.class)
                                .invoke(perms, player.getName(), permission, false);
                        
                        return !(hasPerm != null && hasPerm); // 如果没有权限则返回 true
                    }
                }
            }
        } catch (Throwable t) {
            // 如果遍历出错，强制认定为无权限，触发拦截保护
            return true; 
        }
        return false; 
    }
}
