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
    // 【修改】白名单改为字符串集合，不再强依赖 Material
    private final Set<String> whitelist = new HashSet<>();

    public BlockPlaceListener(ResidencePlaceWhitelist plugin) {
        this.plugin = plugin;
        loadWhitelist();
    }

    private void loadWhitelist() {
        whitelist.clear();
        List<String> list = plugin.getConfig().getStringList("whitelist");
        for (String entry : list) {
            // 直接添加，不做 Material 转换，支持 "netcraft:block_wood_t3_crystal" 这种格式
            whitelist.add(entry.toUpperCase());
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

        if (!isPlayerInResidenceWithoutPermission(player, loc, "build")) {
            return;
        }

        // 【修改】获取方块的 Bukkit 名称（如 STONE）和 NMS 真实ID（如 netcraft:block_wood_t3_crystal）
        String bukkitName = block.getType().name();
        String nmsId = getNMSBlockId(block);

        boolean isWhitelisted = whitelist.contains(bukkitName) 
                || (nmsId != null && whitelist.contains(nmsId.toUpperCase()))
                || whitelist.contains("MODDED"); // 终极兜底：如果写了 MODDED，放行所有模组方块

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
     * 【新增】反射获取 Mod 方块的 NMS 注册名（例如 netcraft:block_wood_t3_crystal）
     */
    private String getNMSBlockId(Block block) {
        try {
            // 尝试方法1：CraftBlock 的 getNMS
            Object nmsBlock = block.getClass().getMethod("getNMS").invoke(block);
            
            // 尝试方法2：通过 Forge 注册表获取键名
            Class<?> forgeRegistryClass = Class.forName("net.minecraftforge.registries.ForgeRegistries");
            Object blockRegistry = forgeRegistryClass.getField("BLOCKS").get(null);
            Object key = blockRegistry.getClass().getMethod("getKey", Object.class).invoke(blockRegistry, nmsBlock);
            
            return key != null ? key.toString() : null;
        } catch (Throwable t) {
            return null; // 获取失败，安全返回 null
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
