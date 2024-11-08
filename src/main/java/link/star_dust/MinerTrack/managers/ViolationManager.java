package link.star_dust.MinerTrack.managers;

import link.star_dust.MinerTrack.MinerTrack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ViolationManager {
    private final MinerTrack plugin;
    private final Map<UUID, Integer> violationLevels = new HashMap<>();

    public ViolationManager(MinerTrack plugin) {
        this.plugin = plugin;
    }

    /**
     * 获取玩家的当前违规等级
     */
    public int getViolationLevel(Player player) {
        return violationLevels.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * 增加玩家的违规等级
     * 
     * @param player 违规玩家
     * @param amount 增加的违规等级
     * @param blockType 违规的矿石类型
     * @param count 矿石数量
     * @param location 挖掘位置
     */
    public void increaseViolationLevel(Player player, int amount, String blockType, int count, Location location) {
        UUID playerId = player.getUniqueId();
        int newLevel = getViolationLevel(player) + amount;
        violationLevels.put(playerId, newLevel);

        // 检查是否达到配置文件中指定的命令阈值
        for (String key : plugin.getConfig().getConfigurationSection("xray.commands").getKeys(false)) {
            int threshold = Integer.parseInt(key);
            if (newLevel == threshold) {
                String command = plugin.getConfig().getString("xray.commands." + key)
                    .replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }

        // 使用配置文件中的详细模式消息格式
        if (newLevel >= 1) {
            String verboseFormat = plugin.getLanguageManager().getPrefixedMessage("verbose-format");
            String formattedMessage = verboseFormat
                .replace("%player%", player.getName())
                .replace("%vl%", String.valueOf(newLevel))
                .replace("%add_vl%", String.valueOf(amount))
                .replace("%block_type%", blockType)
                .replace("%count%", String.valueOf(count))
                .replace("%pos_x%", String.valueOf(location.getBlockX()))
                .replace("%pos_y%", String.valueOf(location.getBlockY()))
                .replace("%pos_z%", String.valueOf(location.getBlockZ()));

            // 向开启详细模式的玩家发送消息
            for (UUID uuid : plugin.getVerbosePlayers()) {
                Player verbosePlayer = Bukkit.getPlayer(uuid);
                if (verbosePlayer != null && verbosePlayer.hasPermission("minertrack.verbose")) {
                    verbosePlayer.sendMessage(formattedMessage);
                }
            }

            // 向控制台发送详细信息（如果启用了详细模式）
            if (plugin.isVerboseConsoleEnabled()) {
                Bukkit.getConsoleSender().sendMessage(formattedMessage);
            }
        }
    }

    /**
     * 重置玩家的违规等级
     */
    public void resetViolationLevel(Player player) {
        violationLevels.remove(player.getUniqueId());
    }
}
