package com.example.residencewhitelist;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

        // 加载放置白名单
        List<String> placeList = plugin.getConfig().getStringList("whitelist-place");
        for (String entry : placeList) {
            whitelistPlace.add(entry.toUpperCase());
        }

        // 加载挖掘白名单
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
        Player player = event.getPlayer();
        if (player == null) return;

        Block block = event.getBlockPlaced();
        if (block == null) return;

        Location loc = block.getLocation();

        if (!isPlayerInResidenceWithoutPermission(player, loc, "build")) {
            return;
        }

        String bukkitName = block.getType().name();
        String nmsId = getNMSBlockId(block);

        // 【新增】调试日志：打印方块的真实 Bukkit 名称和 NMS ID
        plugin.getLogger().info("玩家放置方块，Bukkit名称: " + bukkitName + " | NMS_ID: " + nmsId);

        boolean isWhitelisted = whitelistPlace.contains(bukkitName)
                || (nmsId != null && whitelistPlace.contains(nmsId.toUpperCase()))
                || whitelistPlace.contains("MODDED");

        if (isWhitelisted) {
            if (event.isCancelled()) {
                event.setCancelled(false);
                player.sendMessage(ChatColor.GREEN + "[RPW] 你放置了白名单物品: " + (nmsId != null ? nmsId : bukkitName));
            }
        } else {
            if (!event.isCancelled()) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You don't have place permissions here.");
            }
        }
    }

    // ==================== 监听方块挖掘 ====================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Block block = event.getBlock();
        if (block == null) return;

        Location loc = block.getLocation();

        if (!isPlayerInResidenceWithoutPermission(player, loc, "destroy")) {
            return;
        }

        String bukkitName = block.getType().name();
        String nmsId = getNMSBlockId(block);

        // 【新增】调试日志：打印方块的真实 Bukkit 名称和 NMS ID
        plugin.getLogger().info("玩家挖掘方块，Bukkit名称: " + bukkitName + " | NMS_ID: " + nmsId);

        boolean isWhitelisted = whitelistBreak.contains(bukkitName)
                || (nmsId != null && whitelistBreak.contains(nmsId.toUpperCase()))
                || whitelistBreak.contains("MODDED");

        if (isWhitelisted) {
            if (event.isCancelled()) {
                event.setCancelled(false);
                player.sendMessage(ChatColor.GREEN + "[RPW] 你挖掘了白名单物品: " + (nmsId != null ? nmsId : bukkitName));
            }
        } else {
            if (!event.isCancelled()) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You don't have destroy permissions here.");
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

        if (!isPlayerInResidenceWithoutPermission(player, loc, "container")) {
            return;
        }

        if (!event.isCancelled()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You don't have container permissions here.");
        }
    }

    /**
     * 反射获取 Mod 方块的 NMS 注册名（例如 netcraft:block_wood_t3_crystal）
     */
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

    private boolean isPlayerInResidenceWithoutPermission(Player player, Location loc, String permission) {
        try {
            Plugin resPlugin = Bukkit.getPluginManager().getPlugin("Residence");
            if (resPlugin == null || !resPlugin.isEnabled()) return false;

            Object resManager = resPlugin.getClass().getMethod("getResidenceManager").invoke(resPlugin);
            Object residencesObj = resManager.getClass().getMethod("getResidences").invoke(resManager);

            if (residencesObj instanceof Map) {
                Map<?, ?> residences = (Map<?, ?>) residencesObj;
                for (Object resObj : residences.values()) {
                    Boolean contains = (Boolean) resObj.getClass().getMethod("containsLoc", Location.class).invoke(resObj, loc);
                    if (contains != null && contains) {
                        Object perms = resObj.getClass().getMethod("getPermissions").invoke(resObj);
                        Boolean hasPerm = (Boolean) perms.getClass().getMethod("playerHas", String.class, String.class, boolean.class).invoke(perms, player.getName(), permission, false);
                        return !(hasPerm != null && hasPerm);
                    }
                }
            }
        } catch (Throwable t) {
            return true;
        }
        return false;
    }
}
