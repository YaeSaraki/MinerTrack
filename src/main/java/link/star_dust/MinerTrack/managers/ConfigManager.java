package link.star_dust.MinerTrack.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import link.star_dust.MinerTrack.MinerTrack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class ConfigManager {
    private final MinerTrack plugin;
    private final FileConfiguration config;
    private final File configFile;

    public ConfigManager(MinerTrack plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");

        // Load the default configuration first
        plugin.saveDefaultConfig();

        // Load the custom configuration from file, merging it with defaults
        config = YamlConfiguration.loadConfiguration(configFile);

        // Load defaults from internal resource file using InputStreamReader
        try (InputStream defaultStream = plugin.getResource("config.yml")) {
            if (defaultStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
                config.setDefaults(defaultConfig);
                config.options().copyDefaults(true);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not load default configuration: " + e.getMessage());
        }

        // Save only custom values back to file
        saveCustomConfig();
    }

    public boolean isKickStrikeLightning() {
        return config.getBoolean("xray.kick-strike-lightning", false);
    }

    public List<String> getRareOres() {
        return config.getStringList("xray.rare-ores");
    }

    public int getViolationThreshold() {
        return config.getInt("xray.violation-threshold", 10);
    }

    public int getVeinCountThreshold() {
        return config.getInt("xray.ViolationThreshold.veinCountThreshold", 3);
    }

    public int getTurnCountThreshold() {
        return config.getInt("xray.ViolationThreshold.turnCountThreshold", 10);
    }

    public int getCaveBypassAirCount() {
        return config.getInt("xray.cave-detection.air-threshold", 4);
    }
    
    public int getCaveCheckDetection() {
        return config.getInt("xray.cave-detection.detection", 3);
    }

    public int getWorldMaxHeight(String worldName) {
        ConfigurationSection xraySection = config.getConfigurationSection("xray.worlds");
        if (xraySection == null || !xraySection.isConfigurationSection(worldName)) {
            plugin.getLogger().warning("Max height configuration for world " + worldName + " not found. Defaulting to no height limit.");
            return -1;
        }
        return xraySection.getInt(worldName + ".max-height", -1);
    }

    public boolean isWorldDetectionEnabled(String worldName) {
        ConfigurationSection worldsSection = config.getConfigurationSection("xray.worlds");
        if (worldsSection == null || !worldsSection.isConfigurationSection(worldName)) {
            return config.getBoolean("xray.worlds.all_unnamed_world.enable", false);
        }
        return worldsSection.getBoolean(worldName + ".enable", false);
    }
    
    public boolean DisableBypass() {
        return config.getBoolean("disable_bypass_permission", false);
    }

    // Save only custom (non-default) values to config file
    public void saveCustomConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save custom configuration to " + configFile.getName() + ": " + e.getMessage());
        }
    }

    // Reload the configuration
    public void reloadConfig() {
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("Could not reload configuration: " + e.getMessage());
        }
    }

    public String getCommandForThreshold(int threshold) {
        ConfigurationSection commandsSection = config.getConfigurationSection("xray.commands");
        if (commandsSection != null && commandsSection.contains(String.valueOf(threshold))) {
            return commandsSection.getString(String.valueOf(threshold));
        }
        return null;
    }

    public int getMaxVeinDistance() {
        return config.getInt("xray.cave-detection.max_vein_distance", 10);
    }
    
    public int traceBackLength() {
        return config.getInt("xray.trace_back_length", 10);
    }
    
    public boolean caveSkipVL() {
        return config.getBoolean("xray.cave-detection.cave_check_skip_vl", true);
    }
}

