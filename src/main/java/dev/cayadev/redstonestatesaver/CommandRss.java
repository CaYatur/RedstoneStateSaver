package dev.cayadev.redstonestatesaver;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandRss implements CommandExecutor {
    private final RedstoneStateSaver plugin;
    private final Storage storage;
    private final RedstoneListener listener;

    public CommandRss(RedstoneStateSaver plugin, Storage storage, RedstoneListener listener) {
        this.plugin = plugin;
        this.storage = storage;
        this.listener = listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6RedstoneStateSaver by CaYaDev");
            sender.sendMessage("/rss reload - yapılandırmayı yeniden yükle");
            sender.sendMessage("/rss save - kayıtları diske kaydet");
            sender.sendMessage("/rss izin <x> <y> <z> - o koordinattaki sistemi izinlendir (oyuncu için)");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("rss.admin")) { sender.sendMessage("Yetkiniz yok."); return true; }
            plugin.reloadConfig();
            storage.loadAll();
            sender.sendMessage("Yeniden yüklendi.");
            return true;
        }

        if (args[0].equalsIgnoreCase("save")) {
            if (!sender.hasPermission("rss.admin")) { sender.sendMessage("Yetkiniz yok."); return true; }
            storage.saveAll();
            sender.sendMessage("Durumlar diske kaydedildi.");
            return true;
        }

        if (args[0].equalsIgnoreCase("izin")) {
            if (!(sender instanceof Player)) { sender.sendMessage("Sadece oyuncular kullanabilir."); return true; }
            Player p = (Player) sender;
            if (!p.hasPermission(plugin.getConfig().getString("permissions.bypass", "rss.bypass"))) { p.sendMessage("Yetkiniz yok."); return true; }
            if (args.length < 4) { p.sendMessage("Kullanım: /rss izin <x> <y> <z>"); return true; }
            try {
                int x = Integer.parseInt(args[1]);
                int y = Integer.parseInt(args[2]);
                int z = Integer.parseInt(args[3]);
                String world = p.getWorld().getName();
                boolean added = listener.allowSystem(x, y, z, world);
                if (added) p.sendMessage("Koordinata izin verildi: " + x + ", " + y + ", " + z);
                else p.sendMessage("Zaten izinli veya hata oluştu.");
            } catch (NumberFormatException ex) { p.sendMessage("Koordinat hatası."); }
            return true;
        }

        if (args[0].equalsIgnoreCase("izinchunk")) {
            if (!sender.hasPermission("rss.admin") && !sender.hasPermission("rss.bypass")) { sender.sendMessage("Yetkiniz yok."); return true; }
            if (args.length < 4) { sender.sendMessage("Kullanım: /rss izinchunk <chunkX> <chunkZ> <radius>"); return true; }
            try {
                int cx = Integer.parseInt(args[1]);
                int cz = Integer.parseInt(args[2]);
                int r = Integer.parseInt(args[3]);
                String world = (sender instanceof Player) ? ((Player) sender).getWorld().getName() : plugin.getServer().getWorlds().get(0).getName();
                storage.allowChunk(world, cx, cz, r);
                storage.saveAll();
                sender.sendMessage("Chunk izinleri eklendi ve kaydedildi: " + cx + ", " + cz + " radius=" + r);
            } catch (NumberFormatException ex) { sender.sendMessage("Sayı hatası."); }
            return true;
        }

        if (args[0].equalsIgnoreCase("izinbolge")) {
            // alias: izinbolge <chunkX> <chunkZ> <radius>
            return onCommand(sender, command, label, new String[]{"izinchunk", args.length>1?args[1]:"0", args.length>2?args[2]:"0", args.length>3?args[3]:"1"});
        }

        return false;
    }
}
