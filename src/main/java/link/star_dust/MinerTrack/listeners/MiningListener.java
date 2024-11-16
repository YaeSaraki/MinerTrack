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

    // Only for test
    /*@EventHandler
    public void onPlayerMine(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        
        if (isInAdvancedCave(location)) {
        	player.sendMessage(ChatColor.GOLD + "You are in an advanced cave!");
        	if (hasHighConnectivity(location)) {
                player.sendMessage(ChatColor.BLUE + "This cave is highly connected!");
            }
        } else {
        	player.sendMessage(ChatColor.RED + "You are not in a cave!");
        }
    }*/

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

        // 洞穴检测
        /*
        boolean isDynamicCave = isInCaveDynamic(blockLocation);
        boolean isConnectedCave = hasHighConnectivity(blockLocation);
        boolean isAdvancedCave = isInAdvancedCave(blockLocation);
        */

        // 判断是否需要进一步分析挖矿路径
        /*
        if (!(isDynamicCave && isConnectedCave && isAdvancedCave) || !isSmoothPath(path)) {
            analyzeMiningPath(player, path, blockType, path.size(), blockLocation);
        }
        */
        if (!isInCaveWithAir(blockLocation) || !isSmoothPath(path)) {
            analyzeMiningPath(player, path, blockType, path.size(), blockLocation);
        }
    }

    private void cleanupExplosionExposedOres() {
        long currentTime = System.currentTimeMillis();
        explosionExposedOres.entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }

    private boolean isNewVein(UUID playerId, String worldName, Location location, Material oreType) {
        Map<String, Location> lastLocations = lastVeinLocation.getOrDefault(playerId, new HashMap<String, Location>());
        Location lastLocation = lastLocations.get(worldName);

        // Check if the player is in the same world before calculating distance
        if (lastLocation == null || !lastLocation.getWorld().equals(location.getWorld()) || lastLocation.distance(location) > 5 || !lastLocation.getBlock().getType().equals(oreType)) {
            // Update last locations for each world
            lastLocations.put(worldName, location);
            lastVeinLocation.put(playerId, lastLocations);
            return true;
        }
        return false;
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
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }


    /*
    private boolean isInCave(Location location) {
        int airCount = 0;
        int maxDepth = 30; // 检测范围
        int thresholdAir = 50; // 空气阈值

        Set<Material> uniqueBlocks = new HashSet<>();
        Queue<Location> toVisit = new LinkedList<>();
        Set<Location> visited = new HashSet<>();

        toVisit.add(location);

        while (!toVisit.isEmpty() && airCount <= thresholdAir && maxDepth > 0) {
            Location current = toVisit.poll();
            maxDepth--;

            if (visited.contains(current)) continue;
            visited.add(current);

            Material blockType = current.getBlock().getType();

            // 统计空气块数量
            if (blockType == Material.CAVE_AIR || blockType == Material.AIR) {
                airCount++;
            }

            // 统计不同类型的方块
            uniqueBlocks.add(blockType);

            // 将周围方块加入待检测队列
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        Location adjacent = current.clone().add(x, y, z);
                        if (!visited.contains(adjacent)) {
                            toVisit.add(adjacent);
                        }
                    }
                }
            }
        }

        // 判定为洞穴条件：空气块多
        return airCount > thresholdAir;
    }
    
    private boolean isInCaveDynamic(Location location) {
        int yLevel = location.getBlockY(); // 获取玩家 Y 坐标
        int thresholdAir = yLevel < 0 ? 70 : 50; // Y < 0 时空气阈值更高
        int maxDepth = yLevel < 0 ? 40 : 30; // Y < 0 时检测深度更大

        // 调用核心检测逻辑
        return isInCaveWithParams(location, maxDepth, thresholdAir);
    }

    private boolean isInCaveWithParams(Location location, int maxDepth, int thresholdAir) {
        int airCount = 0;

        Set<Material> uniqueBlocks = new HashSet<>();
        Queue<Location> toVisit = new LinkedList<>();
        Set<Location> visited = new HashSet<>();

        toVisit.add(location);

        while (!toVisit.isEmpty() && airCount <= thresholdAir && maxDepth > 0) {
            Location current = toVisit.poll();
            maxDepth--;

            if (visited.contains(current)) continue;
            visited.add(current);

            Material blockType = current.getBlock().getType();

            // 统计空气块数量
            if (blockType == Material.CAVE_AIR || blockType == Material.AIR) {
                airCount++;
            }

            // 统计不同类型的方块
            uniqueBlocks.add(blockType);

            // 将周围方块加入待检测队列
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        Location adjacent = current.clone().add(x, y, z);
                        if (!visited.contains(adjacent)) {
                            toVisit.add(adjacent);
                        }
                    }
                }
            }
        }

        // 判定为洞穴条件：空气块多
        return airCount > thresholdAir;
    }

    
    private boolean hasHighConnectivity(Location location) {
        int connectedCount = 0;
        int threshold = 10; // 连通空气块阈值
        int maxDepth = 20; // 检测深度限制

        Queue<Location> toVisit = new LinkedList<>();
        Set<Location> visited = new HashSet<>();
        toVisit.add(location);

        while (!toVisit.isEmpty() && connectedCount <= threshold && maxDepth > 0) {
            Location current = toVisit.poll();
            maxDepth--;

            if (visited.contains(current)) continue;
            visited.add(current);

            Material blockType = current.getBlock().getType();
            if (blockType == Material.CAVE_AIR || blockType == Material.AIR) {
                connectedCount++;

                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 && y == 0 && z == 0) continue;
                            Location adjacent = current.clone().add(x, y, z);
                            if (!visited.contains(adjacent)) {
                                toVisit.add(adjacent);
                            }
                        }
                    }
                }
            }
        }

        return connectedCount > threshold;
    }
    
    private boolean isInAdvancedCave(Location location) {
        boolean isBasicCave = isInCave(location);
        boolean isHighlyConnected = hasHighConnectivity(location);

        return isBasicCave && isHighlyConnected;
    }
    */

    private boolean isSmoothPath(List<Location> path) {
        Location lastLocation = null;
        int turns = 0;
        int turnThreshold = plugin.getConfigManager().getTurnCountThreshold();
        for (Location currentLocation : path) {
            if (lastLocation != null) {
                Vector direction = currentLocation.toVector().subtract(lastLocation.toVector()).normalize();
                if (Math.abs(direction.getX()) > 0.7 || Math.abs(direction.getZ()) > 0.7) {
                    turns++;
                }
            }
            lastLocation = currentLocation;
        }
        return turns < turnThreshold;
    }

    private void analyzeMiningPath(Player player, List<Location> path, Material blockType, int count, Location blockLocation) {
        UUID playerId = player.getUniqueId();
        int disconnectedSegments = 0;
        @SuppressWarnings("unused")
		double totalDistance = 0.0;
        Location lastLocation = null;

        for (int i = 0; i < path.size(); i++) {
            Location currentLocation = path.get(i);
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
    
    private void checkAndResetPaths() {
        long now = System.currentTimeMillis();
        long traceRemoveMillis = plugin.getConfig().getInt("xray.trace_remove", 5) * 60 * 1000L; // The configured minutes are converted to milliseconds

        for (UUID playerId : new HashSet<>(vlZeroTimestamp.keySet())) {
            Long lastZeroTime = vlZeroTimestamp.get(playerId);
            if (lastZeroTime != null && now - lastZeroTime > traceRemoveMillis) {
                miningPath.remove(playerId); // Clear the path record
                minedVeinCount.remove(playerId); // Reset the vein count
                vlZeroTimestamp.remove(playerId); // Remove the timestamp that has been processed
            }
        }
    }

    private void increaseViolationLevel(Player player, int amount, String blockType, int count, Location location) {
        UUID playerId = player.getUniqueId();
        violationLevel.put(playerId, violationLevel.getOrDefault(playerId, 0) + amount);
        vlZeroTimestamp.remove(playerId); // When the violation level increases, remove the timestamp with VL of 0
        plugin.getViolationManager().increaseViolationLevel(player, amount, blockType, count, location);
    }
}
