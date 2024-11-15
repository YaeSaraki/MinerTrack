package link.star_dust.MinerTrack.listeners;

import link.star_dust.MinerTrack.MinerTrack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
    private final Set<Location> explosionExposedOres = new HashSet<>();
    private final Map<UUID, Long> vlZeroTimestamp = new HashMap<>();

    public MiningListener(MinerTrack plugin) {
        this.plugin = plugin;
        
        int interval = 20 * 60;
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkAndResetPaths, interval, interval);
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
    public void onBlockBreak(BlockBreakEvent event) {
    	if (!plugin.getConfig().getBoolean("xray.enable", true)) {
            return;
        }
    	
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Material blockType = event.getBlock().getType();
        Location blockLocation = event.getBlock().getLocation();
        List<String> rareOres = plugin.getConfig().getStringList("xray.rare-ores");
        
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

        // Ignore ores exposed by explosions
        if (explosionExposedOres.contains(blockLocation)) {
            explosionExposedOres.remove(blockLocation); // Remove after ignoring to avoid reusing
            return;
        }

        // Proceed with X-Ray detection if the broken block is a rare ore
        if (rareOres.contains(blockType.name())) {
            long currentTime = System.currentTimeMillis();
            int traceBackLength = plugin.getConfig().getInt("xray.trace_back_length", 10) * 60000;
            int maxPathLength = plugin.getConfig().getInt("xray.max_path_length", 500);

            // Initialize the mining path for each player if absent
            miningPath.putIfAbsent(playerId, new HashMap<String, List<Location>>()); // Explicitly define the generic type here
            Map<String, List<Location>> worldPaths = miningPath.get(playerId);

            // Initialize the path for the player's current world if absent
            worldPaths.putIfAbsent(worldName, new ArrayList<>());
            List<Location> path = worldPaths.get(worldName);

            if (lastMiningTime.containsKey(playerId) && (currentTime - lastMiningTime.get(playerId)) > traceBackLength) {
                path.clear();
                minedVeinCount.put(playerId, 0);
            }

            path.add(blockLocation);
            lastMiningTime.put(playerId, currentTime);

            if (path.size() > maxPathLength) {
                path.remove(0);
            }

            if (isNewVein(playerId, worldName, blockLocation, blockType)) {
                minedVeinCount.put(playerId, minedVeinCount.getOrDefault(playerId, 0) + 1);
                lastVeinLocation.putIfAbsent(playerId, new HashMap<String, Location>());
                lastVeinLocation.get(playerId).put(worldName, blockLocation);
            }

            if (!isInCaveWithAir(blockLocation) && !isSmoothPath(path)) {
            	if (!player.hasPermission("minertrack.bypass")) {
                  analyzeMiningPath(player, path, blockType, path.size(), blockLocation);
            	} else if (plugin.getConfigManager().DisableBypass()) {
            		analyzeMiningPath(player, path, blockType, path.size(), blockLocation);
            	} else {
            		// pass
            	}
            }
        }
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
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Location nearby = location.clone().add(x, y, z);
                    if (nearby.getBlock().getType() == Material.CAVE_AIR) {
                        airCount++;
                    }
                }
            }
        }
        return airCount > threshold;
    }

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
