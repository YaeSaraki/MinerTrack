package link.star_dust.MinerTrack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import link.star_dust.MinerTrack.managers.ConfigManager;
import link.star_dust.MinerTrack.managers.LanguageManager;
import link.star_dust.MinerTrack.managers.ViolationManager;
import link.star_dust.MinerTrack.listeners.MiningListener;
import link.star_dust.MinerTrack.commands.*;

public class MinerTrack extends JavaPlugin {
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private ViolationManager violationManager;
    private Notifier notifier;
    private final Set<UUID> verbosePlayers = new HashSet<>();
    private boolean verboseConsole = false;

    @Override
    public void onEnable() {
    	saveDefaultConfig();
        configManager = new ConfigManager(this);
        languageManager = new LanguageManager(this);
        violationManager = new ViolationManager(this);
        notifier = new Notifier(this);
        int traceBackLength = getConfig().getInt("traceBackLength", 10); // 默认值为 10
        
        int pluginId = 23790;
        new Metrics(this, pluginId);
        
        registerCommands();
        registerListeners();
        getCommand("minertrack").setExecutor(new MinerTrackCommand(this));

        getServer().getConsoleSender().sendMessage(applyColors("&8----[&9&lMiner&c&lTrack &bv" + getDescription().getVersion() + " &8]-----------"));
        getServer().getConsoleSender().sendMessage(applyColors("&9&lMiner&c&lTrack &4&oAnti-XRay &aEnabled!"));
        getServer().getConsoleSender().sendMessage(applyColors(""));
        getServer().getConsoleSender().sendMessage(applyColors("&a&oThanks for your trust!"));
        getServer().getConsoleSender().sendMessage(applyColors("&8-----------------------------------------"));
        
        checkForUpdates();
    }
    
    public String applyColors(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private void registerCommands() {
        MinerTrackCommand commandExecutor = new MinerTrackCommand(this);
        getCommand("mtrack").setExecutor(commandExecutor);
        getCommand("mtrack").setTabCompleter(commandExecutor);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new MiningListener(this), this);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }
    
    public Notifier getNotifier() {
        return notifier;
    }

    public ViolationManager getViolationManager() {
        return violationManager;
    }
    
    public Set<UUID> getVerbosePlayers() {
        return verbosePlayers;
    }
    
    public boolean isVerboseConsoleEnabled() {
        return verboseConsole;
    }

    public void toggleVerboseMode(CommandSender sender) {
        String enableMessage = getLanguageManager().getPrefixedMessage("verbose-enable");
        String disableMessage = getLanguageManager().getPrefixedMessage("verbose-disable");

        if (sender instanceof Player) {
            Player player = (Player) sender;
            UUID playerId = player.getUniqueId();
            
            if (verbosePlayers.contains(playerId)) {
                verbosePlayers.remove(playerId);
                player.sendMessage(disableMessage);
            } else {
                verbosePlayers.add(playerId);
                player.sendMessage(enableMessage);
            }
        } else if (sender instanceof ConsoleCommandSender) {
            verboseConsole = !verboseConsole;
            
            if (verboseConsole) {
                sender.sendMessage(enableMessage);
            } else {
                sender.sendMessage(disableMessage);
            }
        }
    }

    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // 仅当玩家拥有更新检查权限时执行
        if (player.hasPermission("minertrack.checkupdate")) {
            String latestVersion = getLatestVersionFromSpigot();
            String currentVersion = getDescription().getVersion();

            // 如果存在新版本，向玩家发送更新消息
            if (latestVersion != null && !currentVersion.equals(latestVersion)) {
                String updateMessage = languageManager.getPrefixedMessage("update-available").replace("{version}", latestVersion);
                player.sendMessage(languageManager.applyColors(updateMessage));
            }
        }
    }

    public void checkForUpdates() {
        // 检查配置文件中是否启用更新检查
        if (!getConfig().getBoolean("update-checker", true)) {
            return;
        }

        String currentVersion = getDescription().getVersion();
        String latestVersion = getLatestVersionFromSpigot();

        // 如果未获取到最新版本或当前版本已是最新，不执行任何操作
        if (latestVersion == null || currentVersion == latestVersion) {
            return;
        }
        
        String updateMessage = "&8[&9&lMiner&c&lTrack&8]&r &aNew version " + latestVersion + " now available!";

        // 向控制台发送更新可用消息
        getServer().getConsoleSender().sendMessage(languageManager.applyColors(updateMessage));

        // 通知所有具有更新检查权限的在线玩家
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("playerdata.checkupdate")) {
                player.sendMessage(languageManager.applyColors(updateMessage));
            }
        }
    }

    private String getLatestVersionFromSpigot() {
        try {
            URL url = new URL("https://api.spigotmc.org/simple/0.2/index.php?action=getResource&id=120562");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            // 读取响应内容
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // 从JSON响应中提取"current_version"
            JSONObject jsonResponse = new JSONObject(response.toString());
            return jsonResponse.getString("current_version");

        } catch (IOException | org.json.JSONException e) {
            getLogger().warning("Failed to check for updates: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void onDisable() {
    	getServer().getConsoleSender().sendMessage(applyColors("&8----[&9&lMiner&c&lTrack &bv" + getDescription().getVersion() + " &8]-----------"));
    	getServer().getConsoleSender().sendMessage(applyColors("&9&lMiner&c&lTrack &4&oAnti-XRay &cDisabled!"));
    	getServer().getConsoleSender().sendMessage(applyColors(""));
    	getServer().getConsoleSender().sendMessage(applyColors("&a&oGood bye!"));
    	getServer().getConsoleSender().sendMessage(applyColors("&8-----------------------------------------"));
    }
}
