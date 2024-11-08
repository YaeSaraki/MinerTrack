package link.star_dust.MinerTrack.managers;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import link.star_dust.MinerTrack.MinerTrack;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class LanguageManager {
    private final MinerTrack plugin;
    private YamlConfiguration languageConfig;

    public LanguageManager(MinerTrack plugin) {
        this.plugin = plugin;
        loadLanguageFile();
    }

    private void loadLanguageFile() {
        File languageFile = new File(plugin.getDataFolder(), "language.yml");
        if (!languageFile.exists()) {
            plugin.saveResource("language.yml", false);
        }
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);
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
}
