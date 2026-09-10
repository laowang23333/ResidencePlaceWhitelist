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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockPlaceListener implements Listener {

    private final ResidencePlaceWhitelist plugin;
    private final Set<String> whitelistPlace = new HashSet<>();
    private final Set<String> whitelistBreak = new HashSet<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyy-MM-dd");

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
            String friendlyName = getFriendlyName(block);
            player.sendMessage(ChatColor.GREEN + "[RPW] 你放置了白名单物品: " + friendlyName);
            // 【新增】写入日志
            writeLog("[放置] 玩家: " + player.getName()
                    + " | 方块: " + friendlyName
                    + " | Bukkit: " + bukkitName
                    + " | NMS: " + (nmsId != null ? nmsId : "无")
                    + " | 世界: " + block.getWorld().getName()
                    + " | 坐标: " + block.getX() + "," + block.getY() + "," + block.getZ());
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
            // 【新增】写入日志（挖掘没提示，但有日志）
            String friendlyName = getFriendlyName(block);
            writeLog("[挖掘] 玩家: " + player.getName()
                    + " | 方块: " + friendlyName
                    + " | Bukkit: " + bukkitName
                    + " | NMS: " + (nmsId != null ? nmsId : "无")
                    + " | 世界: " + block.getWorld().getName()
                    + " | 坐标: " + block.getX() + "," + block.getY() + "," + block.getZ());
        }
    }

    // ==================== 白名单匹配 ====================
    private boolean isWhitelisted(Set<String> list, String bukkitName, String nmsId) {
        return list.contains(bukkitName)
                || (nmsId != null && list.contains(nmsId.toUpperCase()))
                || list.contains("MODDED");
    }

    // ==================== 写日志 ====================
    /**
     * 把日志写到 plugins/ResidencePlaceWhitelist/logs/日期.log
     * 每天一个文件，方便查
     */
    private void writeLog(String message) {
        try {
            File logDir = new File(plugin.getDataFolder(), "logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            String fileName = fileDateFormat.format(new Date()) + ".log";
            File logFile = new File(logDir, fileName);

            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write("[" + dateFormat.format(new Date()) + "] " + message + "\n");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("日志写入失败: " + e.getMessage());
        }
    }

    // ==================== 获取物品中文名 ====================
    private String getFriendlyName(Block block) {
        try {
            ItemStack item = new ItemStack(block.getType());
            if (item.getType().isAir()) {
                String nms = getNMSBlockId(block);
                return nms != null ? nms : block.getType().name();
            }

            try {
                java.lang.reflect.Method method = ItemStack.class.getMethod("getI18NDisplayName");
                Object result = method.invoke(item);
                if (result instanceof String && !((String) result).isEmpty()) {
                    return (String) result;
                }
            } catch (Throwable ignored) {}

            try {
                if (item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.hasDisplayName()) {
                        return meta.getDisplayName();
                    }
                }
            } catch (Throwable ignored) {}

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
