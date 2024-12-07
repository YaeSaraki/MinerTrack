/**
 * DON'T REMOVE THIS
 * 
 * /MinerTrack/src/main/java/link/star_dust/MinerTrack/managers/LanguageManager.java
 * 
 * MinerTrack Source Code - Public under GPLv3 license
 * Original Author: Author87668
 * Contributors: Author87668
 * 
 * DON'T REMOVE THIS
**/
package link.star_dust.MinerTrack.managers;

import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import link.star_dust.MinerTrack.MinerTrack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

public class LanguageManager {
    private final MinerTrack plugin;
    private YamlConfiguration languageConfig;
    private final File languageFile;

    public LanguageManager(MinerTrack plugin) {
        this.plugin = plugin;
        this.languageFile = new File(plugin.getDataFolder(), "language.yml");
        loadLanguageFile();
    }

    private void loadLanguageFile() {
        // Save default language file if it doesn't exist
        if (!languageFile.exists()) {
            plugin.saveResource("language.yml", false);
        }

        // Load the language file
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);

        // Load defaults from the resource file using InputStreamReader
        try (InputStream defaultStream = plugin.getResource("language.yml")) {
            if (defaultStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
                languageConfig.setDefaults(defaultConfig);
                languageConfig.options().copyDefaults(true);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not load default language configuration: " + e.getMessage());
        }

        // Save only the custom values back to file
        saveCustomLanguageFile();
    }

    public String getKickMessage(String playerName) {
        return applyColors(getMessage("kick-format").replace("%player%", playerName));
    }

    public String getPrefix() {
        return applyColors(getMessage("prefix"));
    }

    public List<String> getHelpMessages() {
        List<String> helpMessages = languageConfig.getStringList("help");
        return helpMessages.stream().map(this::applyColors).collect(Collectors.toList());
    }

    public String getPrefixedMessage(String key) {
        return getPrefix() + " " + applyColors(getMessage(key));
    }

    private String getMessage(String path) {
        return languageConfig.getString(path, "&7[&cMinerTrack&7]");
    }

    public String applyColors(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    public String getLogFormat() {
        return getMessage("log-format");
    }
    
    public boolean isKickBroadcastEnabled() {
        return languageConfig.getBoolean("kick-broadcast", true);
    }

    // Save only custom values
    private void saveCustomLanguageFile() {
        try {
            languageConfig.save(languageFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save custom language configuration to " + languageFile.getName() + ": " + e.getMessage());
        }
    }

    // Reload the language file
    public void reloadLanguageFile() {
        try {
            languageConfig.load(languageFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("Could not reload language configuration: " + e.getMessage());
        }
    }
}
