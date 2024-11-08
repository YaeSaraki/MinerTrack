package link.star_dust.MinerTrack.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import link.star_dust.MinerTrack.MinerTrack;

import java.util.List;

public class ConfigManager {
    private final MinerTrack plugin; // Add this line to store plugin reference
    private final FileConfiguration config;

    public ConfigManager(MinerTrack plugin) {
        this.plugin = plugin; // Initialize plugin reference
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
    }

    public boolean isKickStrikeLightning() {
        return config.getBoolean("kick-strike-lightning", false);
    }

    public List<String> getRareOres() {
        return config.getStringList("rare-ores");
    }

    public int getViolationThreshold() {
        return config.getInt("violation-threshold", 10);
    }

    public boolean isKickBroadcastEnabled() {
        return config.getBoolean("kick-broadcast", true);
    }

    public int getVeinCountThreshold() {
        return config.getInt("veinCountThreshold", 3);
    }

    public int getTurnCountThreshold() {
        return config.getInt("turnCountThreshold", 10);
    }

    public int getCaveBypassAirCount() {
        return config.getInt("caveBypassAirCount", 10);
    }

    public int getMaxHeight(String worldName) {
        // 定位到 xray.worlds 配置部分
        ConfigurationSection xraySection = config.getConfigurationSection("xray.worlds");
        if (xraySection == null || !xraySection.contains(worldName)) {
            plugin.getLogger().warning("Max height configuration for world " + worldName + " not found. Defaulting to no height limit.");
            return -1; // 如果找不到配置则返回默认值 -1
        }

        // 获取指定世界的最大高度配置
        return xraySection.getInt(worldName + ".max-height", -1); // 如果未指定则默认值为 -1
    }

}


