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
    private final Map<UUID, List<Location>> miningPath = new HashMap<>();
    private final Map<UUID, Long> lastMiningTime = new HashMap<>();
    private final Map<UUID, Integer> violationLevel = new HashMap<>();
    private final Map<UUID, Integer> minedVeinCount = new HashMap<>();
    private final Map<UUID, Location> lastVeinLocation = new HashMap<>();
    private final Map<UUID, Set<Location>> placedOres = new HashMap<>();

    public MiningListener(MinerTrack plugin) {
        this.plugin = plugin;
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
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Material blockType = event.getBlock().getType();
        Location blockLocation = event.getBlock().getLocation();
        List<String> rareOres = plugin.getConfig().getStringList("xray.rare-ores");

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

        // Proceed with X-Ray detection if the broken block is a rare ore
        if (rareOres.contains(blockType.name())) {
            long currentTime = System.currentTimeMillis();
            int traceBackLength = plugin.getConfig().getInt("xray.trace_back_length", 10) * 60000;
            int maxPathLength = plugin.getConfig().getInt("xray.max_path_length", 500);

            miningPath.putIfAbsent(playerId, new ArrayList<>());
            List<Location> path = miningPath.get(playerId);

            if (lastMiningTime.containsKey(playerId) && (currentTime - lastMiningTime.get(playerId)) > traceBackLength) {
                path.clear();
                minedVeinCount.put(playerId, 0);
            }

            path.add(blockLocation);
            lastMiningTime.put(playerId, currentTime);

            if (path.size() > maxPathLength) {
                path.remove(0);
            }

            if (isNewVein(playerId, blockLocation, blockType)) {
                minedVeinCount.put(playerId, minedVeinCount.getOrDefault(playerId, 0) + 1);
                lastVeinLocation.put(playerId, blockLocation);
            }

            if (!isInCaveWithAir(blockLocation) && !isSmoothPath(path) && !player.hasPermission("minertrack.bypass")) {
                analyzeMiningPath(player, path, blockType, path.size(), blockLocation);
            }
        }
    }

    private boolean isNewVein(UUID playerId, Location location, Material oreType) {
        Location lastLocation = lastVeinLocation.get(playerId);

        return lastLocation == null || lastLocation.distance(location) > 5 || !lastLocation.getBlock().getType().equals(oreType);
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
        double totalDistance = 0.0;
        Location lastLocation = null;

        for (int i = 0; i < path.size(); i++) {
            Location currentLocation = path.get(i);
            if (lastLocation != null) {
                double distance = currentLocation.distance(lastLocation);
                totalDistance += distance;

                if (distance > 5) {
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

    private void increaseViolationLevel(Player player, int amount, String blockType, int count, Location location) {
        UUID playerId = player.getUniqueId();
        violationLevel.put(playerId, violationLevel.getOrDefault(playerId, 0) + amount);
        plugin.getViolationManager().increaseViolationLevel(player, amount, blockType, count, location);
    }
}



