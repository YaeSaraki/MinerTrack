/**
 * DON'T REMOVE THIS
 * 
 * /MinerTrack/src/main/java/link/star_dust/MinerTrack/commands/MinerTrackCommand.java
 * 
 * MinerTrack Source Code - Public under GPLv3 license
 * Original Author: Author87668
 * Contributors: Author87668
 * 
 * DON'T REMOVE THIS
**/
package link.star_dust.MinerTrack.commands;

import link.star_dust.MinerTrack.MinerTrack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MinerTrackCommand implements CommandExecutor, TabCompleter {
    private final MinerTrack plugin;

    public MinerTrackCommand(MinerTrack plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
	@Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) || sender instanceof ConsoleCommandSender) {
            // pass
        } else if (!sender.hasPermission("minertrack.use")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (!sender.hasPermission("minertrack.help")) {
                sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("no-permission"));
                return true;
            }
            List<String> helpMessages = plugin.getLanguageManager().getHelpMessages();
            for (String message : helpMessages) {
                sender.sendMessage(plugin.getLanguageManager().applyColors(message));
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "notify":
                if (!sender.hasPermission("minertrack.sendnotify")) {
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("usage-notify"));
                    return true;
                }
                String messageContent = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                plugin.getNotifier().sendNotifyMessage(messageContent);
                break;

            case "verbose":
                if (!sender.hasPermission("minertrack.verbose")) {
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("no-permission"));
                    return true;
                }
                plugin.toggleVerboseMode(sender);
                break;

            case "check":
                if (!sender.hasPermission("minertrack.check")) {
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("usage-check"));
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target != null) {
                    int violationLevel = plugin.getViolationManager().getViolationLevel(target);
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("violation-level")
                            .replace("{player}", target.getName())
                            .replace("{level}", String.valueOf(violationLevel)));
                } else {
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("player-not-found")
                            .replace("{player}", args[1]));
                }
                break;

            case "reset":
                if (!sender.hasPermission("minertrack.reset")) {
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("usage-reset"));
                    return true;
                }
                Player targetToReset = plugin.getServer().getPlayer(args[1]);
                if (targetToReset != null) {
                    plugin.getViolationManager().resetViolationLevel(targetToReset);
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("reset-success")
                            .replace("{player}", args[1]));
                } else {
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("player-not-found")
                            .replace("{player}", args[1]));
                }
                break;

            case "kick":
                if (!sender.hasPermission("minertrack.kick")) {
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("no-permission"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("usage-kick"));
                    return true;
                }

                Player playerToKick = plugin.getServer().getPlayer(args[1]);
                if (playerToKick != null) {
                    String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

                    if (plugin.getConfigManager().isKickStrikeLightning()) {
                        playerToKick.getWorld().strikeLightningEffect(playerToKick.getLocation());
                    }

                    if (plugin.getLanguageManager().isKickBroadcastEnabled()) {
                        String kickMessage = plugin.getLanguageManager().getPrefixedMessage("kick-format")
                            .replace("%player%", playerToKick.getName())
                            .replace("%reason%", reason);
                        plugin.getServer().broadcastMessage(kickMessage);
                    }

                    plugin.getNotifier().kickPlayer(playerToKick, reason);

                } else {
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("player-not-found")
                            .replace("{player}", args[1]));
                }
                break;


            case "reload":
                if (!sender.hasPermission("minertrack.reload")) {
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("no-permission"));
                    return true;
                }
                plugin.reloadConfig();
                sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("config-reloaded"));
                break;

            case "update":
                if (!sender.hasPermission("minertrack.checkupdate")) {
                    sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("no-permission"));
                    return true;
                }
                plugin.checkForUpdates(sender);
                break;

            default:
                sender.sendMessage(plugin.getLanguageManager().getPrefixedMessage("unknown-command"));
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("help", "notify", "verbose", "check", "reset", "kick", "reload", "update"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("kick")) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
        }
        return completions;
    }
}




