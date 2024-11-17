package link.star_dust.MinerTrack.listeners;

import link.star_dust.MinerTrack.FoliaCheck;
import link.star_dust.MinerTrack.MinerTrack;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.*;

public class MiningListener implements Listener {
    private final MinerTrack plugin;
    private final Map<UUID, Map<String, List<Location>>> miningPath = new HashMap<>();
    private final Map<UUID, Long> lastMiningTime = new HashMap<>();
    private final Map<UUID, Integer> violationLevel = new HashMap<>();
    private final Map<UUID, Integer> minedVeinCount = new HashMap<>();
    private final Map<UUID, Map<String, Location>> lastVeinLocation = new HashMap<>();
    private final Map<UUID, Set<Location>> placedOres = new HashMap<>();
    private final Map<Location, Long> explosionExposedOres = new HashMap<>();
    private final Map<UUID, Long> vlZeroTimestamp = new HashMap<>();

    public MiningListener(MinerTrack plugin) {
        this.plugin = plugin;
        int interval = 20 * 60; // Scheduling interval (unit: tick)

        if (FoliaCheck.isFolia()) {
            // Folia scheduling logic
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
                if (!plugin.isEnabled()) {
                    task.cancel();
                    return;
                }
                checkAndResetPaths();
                cleanupExplosionExposedOres(); // Periodically clean up explosion-exposed ores
                cleanupExpiredPaths();
            }, interval, interval);
        } else {
            // Use reflection to call the Spigot scheduling logic
            try {
                Class<?> schedulerClass = Bukkit.getScheduler().getClass();
                java.lang.reflect.Method runTaskTimer = schedulerClass.getMethod(
                    "runTaskTimer",
                    Plugin.class,
                    Runnable.class,
                    long.class,
                    long.class
                );

                Object[] params = {
                    plugin,
                    (Runnable) () -> {
                        checkAndResetPaths();
                        cleanupExplosionExposedOres();
                        cleanupExpiredPaths();
                    },
                    (long) interval,
                    (long) interval
                };

                runTaskTimer.invoke(
                    Bukkit.getScheduler(),
                    params
                );
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to schedule task on Spigot: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        plugin.getVerbosePlayers().remove(playerUUID);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Material blockType = event.getBlock().getType();
        List<String> rareOres = plugin.getConfig().getStringList("xray.rare-ores");

        if (rareOres.contains(blockType.name())) {
            placedOres.putIfAbsent(playerId, new HashSet<>());
            placedOres.get(playerId).add(event.getBlock().getLocation());
        }
    }
    
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        // Record all ore locations exposed by the explosion
        List<String> rareOres = plugin.getConfig().getStringList("xray.rare-ores");
        long currentTime = System.currentTimeMillis();
        int retentionTime = plugin.getConfig().getInt("xray.explosion_retention_time", 600) * 1000; // Default 10 minutes

        for (var block : event.blockList()) {
            if (rareOres.contains(block.getType().name())) {
                explosionExposedOres.put(block.getLocation(), currentTime + retentionTime);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("xray.enable", true)) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Material blockType = event.getBlock().getType();
        Location blockLocation = event.getBlock().getLocation();
        List<String> rareOres = plugin.getConfig().getStringList("xray.rare-ores");

        if (!player.hasPermission("minertrack.bypass") || player.hasPermission("minertrack.bypass") && plugin.getConfigManager().DisableBypass()) {
        	if (violationLevel.getOrDefault(playerId, 0) == 0) {
        		vlZeroTimestamp.put(playerId, System.currentTimeMillis());
        	}

        	// Check if detection is enabled for the player's world
        	String worldName = player.getWorld().getName();
        	if (!plugin.getConfigManager().isWorldDetectionEnabled(worldName)) {
        		return; // Detection is disabled for this world
        	}

        	// Check if the block height exceeds max height for detection
        	int maxHeight = plugin.getConfigManager().getWorldMaxHeight(worldName);
        	if (maxHeight != -1 && blockLocation.getY() > maxHeight) {
        		return;
        	}

        	// Ignore ores exposed by explosions within the retention time
        	if (explosionExposedOres.containsKey(blockLocation)) {
        		long expirationTime = explosionExposedOres.get(blockLocation);
        		if (System.currentTimeMillis() < expirationTime) {
        			return;
        		} else {
        			explosionExposedOres.remove(blockLocation); // Remove expired entry
        		}
        	}

        	// Proceed with X-Ray detection if the broken block is a rare ore
        	if (rareOres.contains(blockType.name())) {
        		handleXRayDetection(player, blockType, blockLocation);
        	}
        }
    }

    private void handleXRayDetection(Player player, Material blockType, Location blockLocation) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        int traceBackLength = plugin.getConfig().getInt("xray.trace_back_length", 10) * 60000;
        int maxPathLength = plugin.getConfig().getInt("xray.max_path_length", 500);

        // 初始化玩家挖矿路径信息
        miningPath.putIfAbsent(playerId, new HashMap<>());
        Map<String, List<Location>> worldPaths = miningPath.get(playerId);
        String worldName = blockLocation.getWorld().getName();

        worldPaths.putIfAbsent(worldName, new ArrayList<>());
        List<Location> path = worldPaths.get(worldName);

        // 检查上次挖掘时间并清除过期路径
        if (lastMiningTime.containsKey(playerId) && (currentTime - lastMiningTime.get(playerId)) > traceBackLength) {
            path.clear();
            minedVeinCount.put(playerId, 0);
        }

        // 更新挖矿路径和时间
        path.add(blockLocation);
        lastMiningTime.put(playerId, currentTime);

        if (path.size() > maxPathLength) {
            path.remove(0);
        }

        // 检测新矿脉
        if (isNewVein(playerId, worldName, blockLocation, blockType)) {
            minedVeinCount.put(playerId, minedVeinCount.getOrDefault(playerId, 0) + 1);
            lastVeinLocation.putIfAbsent(playerId, new HashMap<>());
            lastVeinLocation.get(playerId).put(worldName, blockLocation);
        }

        // 判断是否需要进一步分析挖矿路径
        if (!isInCaveWithAir(blockLocation) && !isSmoothPath(path)) {
            analyzeMiningPath(player, path, blockType, path.size(), blockLocation);
        }
    }

    private void cleanupExpiredPaths() {
        long now = System.currentTimeMillis();
        miningPath.forEach((playerId, paths) -> {
            paths.values().forEach(path -> path.removeIf(loc -> now - loc.getWorld().getTime() > plugin.getConfigManager().traceBackLength()));
        });
    }
    
    private void cleanupExplosionExposedOres() {
        long currentTime = System.currentTimeMillis();
        explosionExposedOres.entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }

    private boolean isNewVein(UUID playerId, String worldName, Location location, Material oreType) {
        // 获取玩家在当前世界的最后矿脉位置记录
        Map<String, Location> lastLocations = lastVeinLocation.getOrDefault(playerId, new HashMap<>());
        Location lastLocation = lastLocations.get(worldName);

        // 使用 isSameVein 判断是否为同一矿脉
        if (lastLocation == null || !isSameVein(lastLocation, location, oreType)) {
            // 更新该玩家在该世界的最后矿脉位置
            lastLocations.put(worldName, location);
            lastVeinLocation.put(playerId, lastLocations);
            return true; // 是新矿脉
        }

        return false; // 属于同一矿脉
    }

    
    private boolean isSameVein(Location loc1, Location loc2, Material type) {
        if (!loc1.getWorld().equals(loc2.getWorld())) return false;
        if (!loc2.getBlock().getType().equals(type)) return false;

        // 检查两点之间的矿石连通性，包括相邻方块以及八个角
        Set<Location> visited = new HashSet<>();
        Queue<Location> toVisit = new LinkedList<>();
        toVisit.add(loc1);

        while (!toVisit.isEmpty()) {
            Location current = toVisit.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            // 如果到达目标点，则认为是同一矿脉
            if (current.equals(loc2)) {
                return true;
            }

            // 遍历相邻的六个面和八个角的方块
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 2) continue; // 排除非直接邻接方块

                        Location neighbor = current.clone().add(dx, dy, dz);
                        if (neighbor.getBlock().getType().equals(type)) {
                            toVisit.add(neighbor);
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isSmoothPath(List<Location> path) {
        if (path.size() < 2) return true;

        int totalTurns = 0;
        int turnThreshold = plugin.getConfigManager().getTurnCountThreshold();
        Location lastLocation = null;
        Vector lastDirection = null;

        for (Location currentLocation : path) {
            if (lastLocation != null) {
                // 当前方向向量
                Vector currentDirection = currentLocation.toVector().subtract(lastLocation.toVector()).normalize();

                if (lastDirection != null) {
                    // 计算方向变化的角度（转向幅度）
                    double dotProduct = lastDirection.dot(currentDirection);
                    if (dotProduct < Math.cos(Math.toRadians(45))) { // 夹角大于45度，记为一次转向
                        totalTurns++;
                    }
                }
                lastDirection = currentDirection;
            }
            lastLocation = currentLocation;
        }
        // 如果转向次数超过阈值，路径视为不平滑
        return totalTurns < turnThreshold;
    }
    
    private boolean isInCaveWithAir(Location location) {
        int airCount = 0;
        int threshold = plugin.getConfigManager().getCaveBypassAirCount();
        int range = plugin.getConfigManager().getCaveCheckDetection();

        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    Material type = location.getWorld().getBlockAt(baseX + x, baseY + y, baseZ + z).getType();
                    if (type == Material.CAVE_AIR) {
                        airCount++;
                        if (airCount > threshold) {
                        	if (plugin.getConfigManager().caveSkipVL()) {
                                return false;
                        	} else {
                                return true;
                        	}
                        }
                    }
                }
            }
        }
        
        return false;
    }

    private void analyzeMiningPath(Player player, List<Location> path, Material blockType, int count, Location blockLocation) {
        UUID playerId = player.getUniqueId();
        Map<String, Location> lastVeins = lastVeinLocation.getOrDefault(playerId, new HashMap<>());
        String worldName = blockLocation.getWorld().getName();
        Location lastVeinLocation = lastVeins.get(worldName);

        // 如果有上一个矿脉记录，检查路径联通性
        if (lastVeinLocation != null) {
            double veinDistance = lastVeinLocation.distance(blockLocation);

            // 如果路径不联通，认为是在洞穴中挖矿
            if (!isPathConnected(lastVeinLocation, blockLocation, path)) {
                if (plugin.getConfigManager().caveSkipVL()) {
                    return;
                }
            }
        }

        // 如果路径分析通过，继续处理违规逻辑
        int disconnectedSegments = 0;
        double totalDistance = 0.0;
        Location lastLocation = null;

        for (Location currentLocation : path) {
            if (lastLocation != null) {
                double distance = currentLocation.distance(lastLocation);
                totalDistance += distance;

                if (distance > 3) {
                    disconnectedSegments++;
                }
            }
            lastLocation = currentLocation;
        }

        int veinCount = minedVeinCount.getOrDefault(playerId, 0);
        if (veinCount > plugin.getConfigManager().getVeinCountThreshold() && disconnectedSegments > 2) {
            increaseViolationLevel(player, 1, blockType.name(), count, blockLocation);
            minedVeinCount.put(playerId, 0);
        }
    }
    
    private boolean isPathConnected(Location start, Location end, List<Location> path) {
        // 检查路径中是否存在从 start 到 end 的合理联通
        for (Location point : path) {
            double startDistance = start.distance(point);
            double endDistance = end.distance(point);

            // 如果某个路径点与 start 和 end 距离都较近，认为路径联通
            if (startDistance <= plugin.getConfigManager().getMaxVeinDistance() && endDistance <= plugin.getConfigManager().getMaxVeinDistance()) {
                return true;
            }
        }
        return false;
    }
    
    private void checkAndResetPaths() {
    	/*
        long now = System.currentTimeMillis();
        long traceRemoveMillis = plugin.getConfig().getInt("xray.trace_remove", 15) * 60 * 1000L; // The configured minutes are converted to milliseconds

        for (UUID playerId : new HashSet<>(vlZeroTimestamp.keySet())) {
            Long lastZeroTime = vlZeroTimestamp.get(playerId);
            if (lastZeroTime != null && now - lastZeroTime > traceRemoveMillis) {
                miningPath.remove(playerId); // Clear the path record
                minedVeinCount.remove(playerId); // Reset the vein count
                vlZeroTimestamp.remove(playerId); // Remove the timestamp that has been processed
            }
        }
        */
    	// BUGGED
    	return;
    }

    private void increaseViolationLevel(Player player, int amount, String blockType, int count, Location location) {
        UUID playerId = player.getUniqueId();
        violationLevel.put(playerId, violationLevel.getOrDefault(playerId, 0) + amount);
        vlZeroTimestamp.remove(playerId); // When the violation level increases, remove the timestamp with VL of 0
        plugin.getViolationManager().increaseViolationLevel(player, amount, blockType, count, location);
    }
}
