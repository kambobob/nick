package dev.example.nick;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Stores nicknames and applies them to the chat name and tab list.
 *
 * Folia threading rules used here:
 *  - Per-player data (displayName, playerListName) is changed on the player's entity scheduler.
 *  - File I/O runs on the async scheduler.
 */
public final class NickManager {

    private static final Pattern VALID_NICK = Pattern.compile("[A-Za-z0-9_]+");
    /** Words that would clash with /nick subcommands. */
    private static final Set<String> RESERVED = Set.of("reset", "whois", "admin");

    private final NickPlugin plugin;
    private final Map<UUID, String> nicks = new ConcurrentHashMap<>();
    private final File dataFile;

    public NickManager(NickPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "nicks.yml");
    }

    // ---------- persistence ----------

    public void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : yaml.getKeys(false)) {
            try {
                nicks.put(UUID.fromString(key), yaml.getString(key));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping invalid UUID in nicks.yml: " + key);
            }
        }
    }

    /** Blocking save, used on shutdown. */
    public void saveNow() {
        writeToDisk(serialize());
    }

    /** Non-blocking save, used during normal play. */
    public void saveAsync() {
        String content = serialize();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> writeToDisk(content));
    }

    private String serialize() {
        YamlConfiguration yaml = new YamlConfiguration();
        nicks.forEach((uuid, nick) -> yaml.set(uuid.toString(), nick));
        return yaml.saveToString();
    }

    private void writeToDisk(String content) {
        try {
            plugin.getDataFolder().mkdirs();
            Files.writeString(dataFile.toPath(), content);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save nicks.yml: " + e.getMessage());
        }
    }

    // ---------- lookups ----------

    public boolean hasNick(UUID id) {
        return nicks.containsKey(id);
    }

    public String getNick(UUID id) {
        return nicks.get(id);
    }

    /** Finds the player who uses this nick, ignoring case. */
    public Optional<UUID> findByNick(String nick) {
        for (Map.Entry<UUID, String> entry : nicks.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(nick)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    // ---------- validation ----------

    /** Returns an error message, or null if the nick can be used by the given player. */
    public String validate(String nick, UUID self) {
        FileConfiguration cfg = plugin.getConfig();
        int min = cfg.getInt("min-length", 3);
        int max = cfg.getInt("max-length", 16);

        if (nick.length() < min || nick.length() > max) {
            return "Nicks must be " + min + " to " + max + " characters long.";
        }
        if (!VALID_NICK.matcher(nick).matches()) {
            return "Nicks can only use letters, numbers, and underscores.";
        }

        String lower = nick.toLowerCase(Locale.ROOT);
        if (RESERVED.contains(lower)) {
            return "That name is reserved.";
        }
        for (String blocked : cfg.getStringList("blocked-words")) {
            if (lower.contains(blocked.toLowerCase(Locale.ROOT))) {
                return "That nick contains a blocked word.";
            }
        }

        // Stops people from impersonating a player who is online right now
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(self) && online.getName().equalsIgnoreCase(nick)) {
                return "That name belongs to a player who is online right now.";
            }
        }

        for (Map.Entry<UUID, String> entry : nicks.entrySet()) {
            if (!entry.getKey().equals(self) && entry.getValue().equalsIgnoreCase(nick)) {
                return "That nick is already in use.";
            }
        }
        return null;
    }

    // ---------- set / clear ----------

    public void setNick(Player player, String nick) {
        nicks.put(player.getUniqueId(), nick);
        saveAsync();
        apply(player);
    }

    public void clearNick(Player player) {
        nicks.remove(player.getUniqueId());
        saveAsync();
        apply(player);
    }

    // ---------- display ----------

    /** Applies the nick, or the real name if there isn't one, to chat and the tab list. */
    public void apply(Player player) {
        final String nick = nicks.get(player.getUniqueId());
        final String shown = nick != null ? nick : player.getName();

        // Per-player data -> the player's own region/scheduler
        player.getScheduler().run(plugin, task -> {
            Component name = Component.text(shown);
            player.displayName(name);
            player.playerListName(name);
        }, null);
    }
}
