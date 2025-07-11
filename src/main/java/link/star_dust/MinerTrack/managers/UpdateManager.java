/**
 * DON'T REMOVE THIS
 * 
 * /MinerTrack/src/main/java/link/star_dust/MinerTrack/managers/UpdateManager.java
 * 
 * MinerTrack Source Code - Public under GPLv3 license
 * Original Author: Author87668
 * Contributors: Author87668
 * 
 * DON'T REMOVE THIS
**/
package link.star_dust.MinerTrack.managers;

import link.star_dust.MinerTrack.MinerTrack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateManager {
    private final MinerTrack plugin;
	private boolean isHasNewerVersion;
	private String latestVersion;
	private String currentVersion;

    public UpdateManager(MinerTrack plugin) {
        this.plugin = plugin;
        
        latestVersion = getLatestVersionFromSpigot();
        currentVersion = plugin.getDescription().getVersion();
        
        if(isNewerVersion(latestVersion, currentVersion)) {
        	isHasNewerVersion = true;
        } else {
        	isHasNewerVersion = false;
        }
    }

    public void checkForUpdates(CommandSender sender) {
        latestVersion = getLatestVersionFromSpigot();

        if (latestVersion == null) {
            String errorMessage = plugin.getLanguageManager().getPrefixedMessageWithDefault(
                "update.check-failed",
                "&cFailed to check for updates."
            );
            sendMessage(sender, errorMessage);
            return;
        }

        if (isHasNewerVersion()) {
            sendUpdateMessage(sender, latestVersion);
        } else {
            String upToDateMessage = plugin.getLanguageManager().getPrefixedMessageWithDefault(
                "update.using-latest",
                "&2You are using the latest version."
            );
            sendMessage(sender, upToDateMessage);
        }
    }

    private boolean isNewerVersion(String latestVersion, String currentVersion) {
    	latestVersion = getLatestVersionFromSpigot();
    	if (latestVersion == null) {
    		return false;
    	} else {
    		String latest = latestVersion.split("-")[0];
    		String current = currentVersion.split("-")[0];

    		String[] latestParts = latest.split("\\.");
    		String[] currentParts = current.split("\\.");

    		for (int i = 0; i < Math.max(latestParts.length, currentParts.length); i++) {
    			int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
    			int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
    			if (latestPart > currentPart) {
    				return true;
    			} else if (latestPart < currentPart) {
    				return false;
    			}
    		}
    	}

    	return false;
    }
    
    public boolean isHasNewerVersion() {
        return isHasNewerVersion;
    }
    
    private String updateMessage;

    private void sendUpdateMessage(CommandSender sender, String latestVersion) {
        String messageKey;
        if (latestVersion.contains("-beta")) {
            messageKey = "update.beta-available";
        } else if (latestVersion.contains("-alpha")) {
            messageKey = "update.alpha-available";
        } else {
            messageKey = "update.stable-available";
        }

        String defaultMessage;
        if (latestVersion.contains("-beta")) {
            defaultMessage = "&eNew beta version %latest_version% now available!";
        } else if (latestVersion.contains("-alpha")) {
            defaultMessage = "&cNew alpha version %latest_version% now available!";
        } else {
            defaultMessage = "&aNew stable version %latest_version% now available!";
        }

        String updateMessage = plugin.getLanguageManager().getPrefixedMessageWithDefault(messageKey, defaultMessage)
                .replace("%latest_version%", latestVersion);

        sendMessage(sender, updateMessage);
    }
    
    public String updateMessageOnPlayerJoin() {
    	return updateMessage;
    }

    private void sendMessage(CommandSender sender, String message) {
        String coloredMessage = plugin.getLanguageManager().applyColors(message);
        if (sender != null) {
            sender.sendMessage(coloredMessage);
        } else {
            // Send to console if sender is null
            Bukkit.getConsoleSender().sendMessage(coloredMessage);
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

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JSONObject jsonResponse = new JSONObject(response.toString());
            return jsonResponse.getString("current_version");

        } catch (IOException | org.json.JSONException e) {
            plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
            return null;
        }
    }
}



