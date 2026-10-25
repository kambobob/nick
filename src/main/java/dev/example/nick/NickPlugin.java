package dev.example.nick;

import org.bukkit.plugin.java.JavaPlugin;

public final class NickPlugin extends JavaPlugin {

    private NickManager nickManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        nickManager = new NickManager(this);
        nickManager.load();

        getServer().getPluginManager().registerEvents(new JoinListener(nickManager), this);

        NickCommand command = new NickCommand(this, nickManager);
        getCommand("nick").setExecutor(command);
        getCommand("nick").setTabCompleter(command);

        getLogger().info("Nick enabled.");
    }

    @Override
    public void onDisable() {
        if (nickManager != null) {
            nickManager.saveNow();
        }
    }

    public NickManager getNickManager() {
        return nickManager;
    }
}
