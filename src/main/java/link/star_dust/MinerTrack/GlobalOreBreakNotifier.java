package link.star_dust.MinerTrack;

import link.star_dust.MinerTrack.managers.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * @author YaeSaraki
 * @email ikaraswork@iCloud.com
 * @date 2025/7/29 21:49
 * @description:
 */
public class GlobalOreBreakNotifier {
    private final MinerTrack plugin;

    public GlobalOreBreakNotifier(MinerTrack plugin) {
        this.plugin = plugin;
    }

    public void buildAndSendNotifierMessageWith(Player player, Material blockType, int veinBlocks) {
        LanguageManager languageManager = LanguageManager.getInstance(plugin);
        String formattedMessage = languageManager.getPrefixedMessage("ore-break-notification")
                .replace("%player%", player.getName())
                .replace("%ore%", blockType.name())
                .replace("%veinblocks%", String.valueOf(veinBlocks));

//        Bukkit.broadcastMessage(formattedMessage);
//         向拥有权限的玩家发送消息
        for (Player player_ : Bukkit.getOnlinePlayers()) {
            if (true) {
                player_.sendMessage(formattedMessage);
            }
        }

    }
}
