package dev.cayadev.redstonestatesaver;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Deque;
import java.time.Instant;

public class RedstoneListener implements Listener {
    private final RedstoneStateSaver plugin;
    private final Storage storage;
    private final Map<String, AtomicInteger> eventCounts = new ConcurrentHashMap<>();
    private final Set<String> allowedSystems = ConcurrentHashMap.newKeySet();
    // sliding window per block key -> deque of timestamps (epoch seconds)
    private final Map<String, Deque<Long>> eventTimes = new ConcurrentHashMap<>();

    public RedstoneListener(RedstoneStateSaver plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public String getPluginName() { return plugin.getName(); }

    @EventHandler
    public void onRedstoneChange(BlockRedstoneEvent e) {
        Location loc = e.getBlock().getLocation();
        // save state
        if (plugin.getConfig().getBoolean("save-on-change", true)) {
            storage.saveBlock(e.getBlock());
        }

        // warning system
    String worldName = loc.getWorld().getName();
    int chunkX = loc.getBlockX() >> 4;
    int chunkZ = loc.getBlockZ() >> 4;
    // if the whole chunk is allowed, skip warning and saving
    if (storage.isChunkAllowed(worldName, chunkX, chunkZ)) return;

    String key = worldName + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    int threshold = plugin.getConfig().getInt("warn.threshold", 200);
    int window = plugin.getConfig().getInt("warn.window-seconds", 10);

        Deque<Long> dq = eventTimes.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        long now = Instant.now().getEpochSecond();
        dq.addLast(now);
        // remove old timestamps
        while (!dq.isEmpty() && dq.peekFirst() < now - window) dq.pollFirst();
        int count = dq.size();

        if (count >= threshold && !allowedSystems.contains(key)) {
            String msg = plugin.getConfig().getString("messages.warning", "§c[RSS] Yoğun redstone sistemi tespit edildi: %x% %y% %z% ");
            msg = msg.replace("%x%", String.valueOf(loc.getBlockX())).replace("%y%", String.valueOf(loc.getBlockY())).replace("%z%", String.valueOf(loc.getBlockZ()));

            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.hasPermission(plugin.getConfig().getString("permissions.notify", "rss.notify"))) {
                    TextComponent text = new TextComponent(msg + " §7(Tıklayın ışınlanmak için)");
                    text.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
                    p.spigot().sendMessage(text);

                    // clickable allow chunk message
                    int cX = loc.getBlockX() >> 4;
                    int cZ = loc.getBlockZ() >> 4;
                    int rad = plugin.getConfig().getInt("warn.allow-radius-default", 1);
                    TextComponent allow = new TextComponent(" §a[Chunk'a izin ver]");
                    allow.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rss izinchunk " + cX + " " + cZ + " " + rad));
                    p.spigot().sendMessage(allow);
                }
            }
            dq.clear();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!plugin.getConfig().getBoolean("protection.enabled", true)) return;
        if (e.getPlayer().hasPermission(plugin.getConfig().getString("permissions.bypass", "rss.bypass"))) return;
    int cx = e.getBlock().getX() >> 4;
    int cz = e.getBlock().getZ() >> 4;
    String world = e.getBlock().getWorld().getName();
    if (storage.isChunkAllowed(world, cx, cz)) return;
    if (storage.hasSavedAt(e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.getConfig().getString("messages.protected", "§cBu sistem korumalıdır."));
            // suggestion to allow
            TextComponent allow = new TextComponent("§a[Sistem'e izin ver]");
            Location loc = e.getBlock().getLocation();
            allow.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rss izin " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
            e.getPlayer().spigot().sendMessage(allow);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!plugin.getConfig().getBoolean("protection.enabled", true)) return;
        if (e.getPlayer().hasPermission(plugin.getConfig().getString("permissions.bypass", "rss.bypass"))) return;
    int cx = e.getBlock().getX() >> 4;
    int cz = e.getBlock().getZ() >> 4;
    String world = e.getBlock().getWorld().getName();
    if (storage.isChunkAllowed(world, cx, cz)) return;
    // if placing into a saved system location, cancel
    if (storage.hasSavedAt(e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.getConfig().getString("messages.protected", "§cBu sistem korumalıdır."));
        }
    }

    // allow a system (called from command)
    public boolean allowSystem(int x, int y, int z, String world) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        int radius = plugin.getConfig().getInt("warn.allow-radius-default", 1);
        storage.allowChunk(world, chunkX, chunkZ, radius);
        return true;
    }
}
