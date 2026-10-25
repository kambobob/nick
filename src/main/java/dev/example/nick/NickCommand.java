package dev.example.nick;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class NickCommand implements CommandExecutor, TabCompleter {

    private final NickPlugin plugin;
    private final NickManager manager;

    public NickCommand(NickPlugin plugin, NickManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reset" -> reset(sender);
            case "whois" -> whois(sender, args);
            case "admin" -> admin(sender, args);
            default -> setOwn(sender, args);
        }
        return true;
    }

    // ---------- subcommands ----------

    private void setOwn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, NamedTextColor.RED, "Only players can set a nickname.");
            return;
        }
        if (!player.hasPermission("nick.use")) {
            send(sender, NamedTextColor.RED, "You don't have permission to do that.");
            return;
        }
        if (args.length != 1) {
            send(sender, NamedTextColor.RED, "Nicknames can't contain spaces. Usage: /nick <name>");
            return;
        }

        String nick = args[0];
        String error = manager.validate(nick, player.getUniqueId());
        if (error != null) {
            send(sender, NamedTextColor.RED, error);
            return;
        }
        manager.setNick(player, nick);
        send(sender, NamedTextColor.GREEN, "Your nickname is now " + nick + ".");
    }

    private void reset(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, NamedTextColor.RED, "Only players can reset a nickname.");
            return;
        }
        if (!manager.hasNick(player.getUniqueId())) {
            send(sender, NamedTextColor.YELLOW, "You don't have a nickname set.");
            return;
        }
        manager.clearNick(player);
        send(sender, NamedTextColor.GREEN, "Your nickname has been reset.");
    }

    private void whois(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nick.admin")) {
            send(sender, NamedTextColor.RED, "You don't have permission to do that.");
            return;
        }
        if (args.length != 2) {
            send(sender, NamedTextColor.RED, "Usage: /nick whois <nick>");
            return;
        }

        Optional<UUID> owner = manager.findByNick(args[1]);
        if (owner.isEmpty()) {
            send(sender, NamedTextColor.YELLOW, "No one is using the nick " + args[1] + ".");
            return;
        }
        OfflinePlayer real = Bukkit.getOfflinePlayer(owner.get());
        String realName = real.getName() != null ? real.getName() : "unknown";
        send(sender, NamedTextColor.GREEN, args[1] + " is " + realName + ".");
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nick.admin")) {
            send(sender, NamedTextColor.RED, "You don't have permission to do that.");
            return;
        }
        if (args.length < 3) {
            send(sender, NamedTextColor.RED, "Usage: /nick admin <set <player> <name> | clear <player>>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            send(sender, NamedTextColor.RED, "That player is not online.");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                if (args.length != 4) {
                    send(sender, NamedTextColor.RED, "Usage: /nick admin set <player> <name>");
                    return;
                }
                String nick = args[3];
                String error = manager.validate(nick, target.getUniqueId());
                if (error != null) {
                    send(sender, NamedTextColor.RED, error);
                    return;
                }
                manager.setNick(target, nick);
                send(sender, NamedTextColor.GREEN, "Set " + target.getName() + "'s nickname to " + nick + ".");
            }
            case "clear" -> {
                manager.clearNick(target);
                send(sender, NamedTextColor.GREEN, "Cleared " + target.getName() + "'s nickname.");
            }
            default -> send(sender, NamedTextColor.RED, "Usage: /nick admin <set|clear> <player> ...");
        }
    }

    // ---------- helpers ----------

    private void sendUsage(CommandSender sender) {
        send(sender, NamedTextColor.YELLOW, "/nick <name> | reset | whois <nick> | admin <set|clear> <player> [name]");
    }

    private void send(CommandSender sender, NamedTextColor color, String text) {
        sender.sendMessage(Component.text(text, color));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("reset", "whois", "admin"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return filter(List.of("set", "clear"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return filter(names, args[2]);
        }
        return List.of();
    }

    private static List<String> filter(Collection<String> options, String start) {
        String lower = start.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}
