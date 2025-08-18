package dev.cayadev.redstonestatesaver;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class RedstoneStateSaver extends JavaPlugin {
    private Storage storage;
    private RedstoneListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        storage = new Storage(this);

        listener = new RedstoneListener(this, storage);
        getServer().getPluginManager().registerEvents(listener, this);
        if (getCommand("rss") != null) getCommand("rss").setExecutor(new CommandRss(this, storage, listener));

        // Load saved states on startup async
        Bukkit.getScheduler().runTaskAsynchronously(this, storage::loadAll);

        int interval = getConfig().getInt("save-interval-seconds", 60);
        if (interval > 0) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, storage::saveAll, interval * 20L, interval * 20L);
        }

        getLogger().info("RedstoneStateSaver by CaYaDev etkinleştirildi.");
    }

    @Override
    public void onDisable() {
        getLogger().info("RedstoneStateSaver: Redstone durumları kaydediliyor...");
        storage.saveAll();
    }

    public Storage getStorage() { return storage; }
    public RedstoneListener getListener() { return listener; }
}
