package com.example.residencewhitelist;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

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
        // 1. 如果事件没被取消，说明正常放置，不需要我们管
        if (!event.isCancelled()) {
            return;
        }

        // 2. 事件被取消，说明玩家可能没有领地权限（或其他限制）
        Player player = event.getPlayer();
        if (player == null) return;

        Material placedType = event.getBlockPlaced().getType();

        // 3. 检查物品是否在白名单中
        if (!whitelist.contains(placedType)) {
            return; // 不在白名单，保持原有的拦截
        }

        // 4. 在白名单里，强行取消拦截，允许放置
        event.setCancelled(false);
        player.sendMessage(ChatColor.GREEN + "[RPW] 你放置了白名单物品: " + placedType.name());
    }
}
