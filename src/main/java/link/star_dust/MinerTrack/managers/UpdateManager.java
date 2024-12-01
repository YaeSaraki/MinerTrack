package link.star_dust.MinerTrack.managers;

import link.star_dust.MinerTrack.MinerTrack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateManager {
    private final MinerTrack plugin;

    public UpdateManager(MinerTrack plugin) {
        this.plugin = plugin;
    }

    public void checkForUpdates(CommandSender sender) {
        String latestVersion = getLatestVersionFromSpigot();
        String currentVersion = plugin.getDescription().getVersion();

        if (latestVersion == null) {
            sendMessage(sender, "&8[&9&lMiner&c&lTrack&8]&r &cFailed to check for updates.");
            return;
        }

        if (isNewerVersion(latestVersion, currentVersion)) {
            sendUpdateMessage(sender, latestVersion);
        } else {
            sendMessage(sender, "&8[&9&lMiner&c&lTrack&8]&r &2You are using the latest version.");
        }
    }

    private boolean isNewerVersion(String latestVersion, String currentVersion) {
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
        return false;
    }

    private void sendUpdateMessage(CommandSender sender, String latestVersion) {
        String updateMessage;
        if (latestVersion.contains("-beta") || latestVersion.contains("-snapshot")) {
            updateMessage = "&8[&9&lMiner&c&lTrack&8]&r &eNew beta version " + latestVersion + " now available!";
        } else {
            updateMessage = "&8[&9&lMiner&c&lTrack&8]&r &aNew stable version " + latestVersion + " now available!";
        }
        sendMessage(sender, updateMessage);
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

