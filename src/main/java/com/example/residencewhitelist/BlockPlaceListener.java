package com.example.residencewhitelist;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockPlaceListener implements Listener {

    private final ResidencePlaceWhitelist plugin;
    private final Set<String> whitelistPlace = new HashSet<>();
    private final Set<String> whitelistBreak = new HashSet<>();

    public BlockPlaceListener(ResidencePlaceWhitelist plugin) {
        this.plugin = plugin;
        loadWhitelist();
    }

    private void loadWhitelist() {
        whitelistPlace.clear();
        whitelistBreak.clear();

        List<String> placeList = plugin.getConfig().getStringList("whitelist-place");
        for (String entry : placeList) {
            whitelistPlace.add(entry.toUpperCase());
        }

        List<String> breakList = plugin.getConfig().getStringList("whitelist-break");
        for (String entry : breakList) {
            whitelistBreak.add(entry.toUpperCase());
        }

        plugin.getLogger().info("已加载 " + whitelistPlace.size() + " 个放置白名单物品，"
                + whitelistBreak.size() + " 个挖掘白名单物品。");
    }

    public void reloadWhitelist() {
        plugin.reloadConfig();
        loadWhitelist();
    }

    // ==================== 监听方块放置 ====================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.isCancelled()) return;

        Player player = event.getPlayer();
        if (player == null) return;
        Block block = event.getBlockPlaced();
        if (block == null) return;

        String bukkitName = block.getType().name();
        String nmsId = getNMSBlockId(block);

        if (isWhitelisted(whitelistPlace, bukkitName, nmsId)) {
            event.setCancelled(false);
            // 【修改】显示物品中文名（拿不到就回退到 NMS ID / Bukkit 名）
            String friendlyName = getFriendlyName(block);
            player.sendMessage(ChatColor.GREEN + "[RPW] 你放置了白名单物品: " + friendlyName);
        }
    }

    // ==================== 监听方块挖掘 ====================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.isCancelled()) return;

        Player player = event.getPlayer();
        if (player == null) return;
        Block block = event.getBlock();
        if (block == null) return;

        String bukkitName = block.getType().name();
        String nmsId = getNMSBlockId(block);

        if (isWhitelisted(whitelistBreak, bukkitName, nmsId)) {
            event.setCancelled(false);
            // 【修改】挖掘不再发任何提示
        }
    }

    // ==================== 白名单匹配 ====================
    private boolean isWhitelisted(Set<String> list, String bukkitName, String nmsId) {
        return list.contains(bukkitName)
                || (nmsId != null && list.contains(nmsId.toUpperCase()))
                || list.contains("MODDED");
    }

    // ==================== 获取物品中文名 ====================
    /**
     * 尝试获取方块对应的物品中文名。
     * 优先顺序：ItemStack 的 I18N 名称 -> ItemMeta 的显示名 -> NMS ID -> Bukkit 名称
     */
    private String getFriendlyName(Block block) {
        try {
            ItemStack item = new ItemStack(block.getType());
            if (item.getType().isAir()) {
                // ItemStack 构造失败（模组方块在混合端常见），回退
                String nms = getNMSBlockId(block);
                return nms != null ? nms : block.getType().name();
            }

            // 尝试 Paper/Spigot 的 I18N 名称
            try {
                java.lang.reflect.Method method = ItemStack.class.getMethod("getI18NDisplayName");
                Object result = method.invoke(item);
                if (result instanceof String && !((String) result).isEmpty()) {
                    return (String) result;
                }
            } catch (Throwable ignored) {}

            // 尝试 ItemMeta 的 display name
            try {
                if (item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.hasDisplayName()) {
                        return meta.getDisplayName();
                    }
                }
            } catch (Throwable ignored) {}

            // 最后回退
            String nms = getNMSBlockId(block);
            return nms != null ? nms : block.getType().name();
        } catch (Throwable t) {
            return block.getType().name();
        }
    }

    // ==================== Mod 方块 NMS ID ====================
    private String getNMSBlockId(Block block) {
        try {
            Object nmsBlock = block.getClass().getMethod("getNMS").invoke(block);
            Class<?> forgeRegistryClass = Class.forName("net.minecraftforge.registries.ForgeRegistries");
            Object blockRegistry = forgeRegistryClass.getField("BLOCKS").get(null);
            Object key = blockRegistry.getClass().getMethod("getKey", Object.class).invoke(blockRegistry, nmsBlock);
            return key != null ? key.toString() : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
