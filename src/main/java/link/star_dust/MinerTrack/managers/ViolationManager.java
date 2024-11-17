package link.star_dust.MinerTrack.managers;

import link.star_dust.MinerTrack.MinerTrack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.scheduler.BukkitRunnable;

public class ViolationManager {
    private final MinerTrack plugin;
    private final Map<UUID, Integer> violationLevels = new HashMap<>();
    private final Map<UUID, BukkitRunnable> vlDecayTasks = new HashMap<>();

    private String currentLogFileName;

    public ViolationManager(MinerTrack plugin) {
        this.plugin = plugin;
        this.currentLogFileName = generateLogFileName();

        startViolationDecayTask();
    }

    private void startViolationDecayTask() {
    	Bukkit.getOnlinePlayers().forEach(this::scheduleVLDecayTask);
    }

    private String generateLogFileName() {
        LocalDate date = LocalDate.now();
        String formattedDate = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        File logDir = new File(plugin.getDataFolder(), "logs");
        if (!logDir.exists() && !logDir.mkdirs()) {
            Bukkit.getLogger().warning("Could not create logs directory for MinerTrack.");
        }

        int index = 1;
        File logFile;

        do {
            logFile = new File(logDir, String.format("%s-%d%s.log", formattedDate, index, getOrdinalSuffix(index)));
            index++;
        } while (logFile.exists());

        return logFile.getName();
    }

    private String getLogFileName() {
        if (currentLogFileName == null) {
            currentLogFileName = generateLogFileName();
        }
        return currentLogFileName;
    }

    private String getOrdinalSuffix(int index) {
        if (index >= 11 && index <= 13) return "th";
        switch (index % 10) {
            case 1: return "st";
            case 2: return "nd";
            case 3: return "rd";
            default: return "th";
        }
    }

    private void logViolation(Player player, int vl, int addVl, String blockType, int count, Location location) {
        if (!plugin.getConfig().getBoolean("log_file")) return;

        String logFormat = plugin.getLanguageManager().getLogFormat();
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "unknown";

        LocalDateTime now = LocalDateTime.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        String day = String.format("%02d", now.getDayOfMonth());
        String hour = String.format("%02d", now.getHour());
        String minute = String.format("%02d", now.getMinute());
        String second = String.format("%02d", now.getSecond());

        String formattedMessage = logFormat
            .replace("%year%", year)
            .replace("%month%", month)
            .replace("%day%", day)
            .replace("%hour%", hour)
            .replace("%minute%", minute)
            .replace("%second%", second)
            .replace("%player%", player.getName())
            .replace("%vl%", String.valueOf(vl))
            .replace("%add_vl%", String.valueOf(addVl))
            .replace("%block_type%", blockType)
            .replace("%count%", String.valueOf(count))
            .replace("%world%", worldName)
            .replace("%pos_x%", String.valueOf(location.getBlockX()))
            .replace("%pos_y%", String.valueOf(location.getBlockY()))
            .replace("%pos_z%", String.valueOf(location.getBlockZ()));

        String fileName = getLogFileName();
        File logDir = new File(plugin.getDataFolder(), "logs");
        File logFile = new File(logDir, fileName);

        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(formattedMessage + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public int getViolationLevel(Player player) {
        return violationLevels.getOrDefault(player.getUniqueId(), 0);
    }

    public void increaseViolationLevel(Player player, int amount, String blockType, int count, Location location) {
    	UUID playerId = player.getUniqueId();
        int newLevel = getViolationLevel(player) + amount;
        violationLevels.put(playerId, newLevel);

        if (vlDecayTasks.containsKey(playerId)) {
            vlDecayTasks.get(playerId).cancel();
            vlDecayTasks.remove(playerId);
        }

        scheduleVLDecayTask(player);

        for (String key : plugin.getConfig().getConfigurationSection("xray.commands").getKeys(false)) {
            int threshold = Integer.parseInt(key);
            if (newLevel == threshold) {
                String command = plugin.getConfig().getString("xray.commands." + key)
                    .replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }

        if (newLevel >= 1) {
            String verboseFormat = plugin.getLanguageManager().getPrefixedMessage("verbose-format");
            String worldName = location.getWorld() != null ? location.getWorld().getName() : "unknown";

            String formattedMessage = verboseFormat
                .replace("%player%", player.getName())
                .replace("%vl%", String.valueOf(newLevel))
                .replace("%add_vl%", String.valueOf(amount))
                .replace("%block_type%", blockType)
                .replace("%count%", String.valueOf(count))
                .replace("%world%", worldName)
                .replace("%pos_x%", String.valueOf(location.getBlockX()))
                .replace("%pos_y%", String.valueOf(location.getBlockY()))
                .replace("%pos_z%", String.valueOf(location.getBlockZ()));

            for (UUID uuid : plugin.getVerbosePlayers()) {
                Player verbosePlayer = Bukkit.getPlayer(uuid);
                if (verbosePlayer != null && verbosePlayer.hasPermission("minertrack.verbose")) {
                    verbosePlayer.sendMessage(formattedMessage);
                }
            }

            if (plugin.isVerboseConsoleEnabled()) {
                Bukkit.getConsoleSender().sendMessage(formattedMessage);
            }

            logViolation(player, newLevel, amount, blockType, count, location);
        }
    }
    
    private void scheduleVLDecayTask(Player player) {
        UUID playerId = player.getUniqueId();
        int decayInterval = plugin.getConfig().getInt("xray.decay.interval", 2);
        int decayAmount = plugin.getConfig().getInt("xray.decay.amount", 1);

        if (vlDecayTasks.containsKey(playerId)) {
            return;
        }

        BukkitRunnable decayTask = new BukkitRunnable() {
            @Override
            public void run() {
                int currentVL = violationLevels.getOrDefault(playerId, 0);

                if (currentVL > 0) {
                    int newVL = Math.max(0, currentVL - decayAmount);
                    violationLevels.put(playerId, newVL);

                    if (newVL == 0) {
                        cancel();
                        vlDecayTasks.remove(playerId);
                    }
                } else {
                    violationLevels.put(playerId, 0);
                    cancel();
                    vlDecayTasks.remove(playerId);
                }
            }
        };

        decayTask.runTaskTimer(plugin, 0L, decayInterval * 60L * 20L);
        vlDecayTasks.put(playerId, decayTask);
    }

    public void resetViolationLevel(Player player) {
        violationLevels.remove(player.getUniqueId());
    }
}
