package dev.cayadev.redstonestatesaver;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.Repeater;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Storage {
    private final JavaPlugin plugin;
    private final File dataFile;
    private FileConfiguration cfg;

    // key = world:x:y:z
    private final Map<String, SavedBlock> blocks = new ConcurrentHashMap<>();
    // allowed chunks keys: world:chunkX:chunkZ
    private final Set<String> allowedChunks = ConcurrentHashMap.newKeySet();

    public Storage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "states.yml");
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        loadConfigFile();
        loadAll();
    }

    private void loadConfigFile() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("states.yml oluşturulamadı: " + e.getMessage());
            }
        }
        cfg = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveBlock(Block block) {
        if (block == null) return;
        Location loc = block.getLocation();
        String key = keyOf(loc);
        BlockData bd = block.getBlockData();
        SavedBlock sb = new SavedBlock(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), block.getType().name(), bd.getAsString());
        // capture redstone-specific properties
        if (bd instanceof RedstoneWire) {
            RedstoneWire rw = (RedstoneWire) bd;
            sb.props.put("wirePower", rw.getPower());
        } else if (bd instanceof Repeater) {
            Repeater r = (Repeater) bd;
            sb.props.put("repeaterPowered", r.isPowered());
            sb.props.put("repeaterDelay", r.getDelay());
        }
        blocks.put(key, sb);
        // keep in-memory; periodic or explicit save will persist
    }

    public boolean hasSavedAt(Location loc) {
        return blocks.containsKey(keyOf(loc));
    }

    public void saveAll() {
        // Before writing, snapshot current live state for entries to make shutdown save accurate
        for (Map.Entry<String, SavedBlock> e : blocks.entrySet()) {
            SavedBlock sb = e.getValue();
            try {
                if (Bukkit.getWorld(sb.world) == null) continue;
                Block b = Bukkit.getWorld(sb.world).getBlockAt(sb.x, sb.y, sb.z);
                sb.material = b.getType().name();
                sb.blockData = b.getBlockData().getAsString();
                BlockData bd = b.getBlockData();
                sb.props.clear();
                if (bd instanceof RedstoneWire) {
                    sb.props.put("wirePower", ((RedstoneWire) bd).getPower());
                } else if (bd instanceof Repeater) {
                    Repeater r = (Repeater) bd;
                    sb.props.put("repeaterPowered", r.isPowered());
                    sb.props.put("repeaterDelay", r.getDelay());
                }
            } catch (Exception ignored) {}
        }

        // atomic save: write to temp file and rename
        try {
            File tmp = new File(plugin.getDataFolder(), "states.yml.tmp");
            FileConfiguration out = YamlConfiguration.loadConfiguration(tmp);
            out.set("blocks", null);
            for (Map.Entry<String, SavedBlock> e : blocks.entrySet()) {
                out.set("blocks." + e.getKey(), e.getValue().toMap());
            }
            out.set("allowedChunks", null);
            int i = 0;
            for (String c : allowedChunks) {
                out.set("allowedChunks." + i++, c);
            }
            out.save(tmp);
            File bak = new File(plugin.getDataFolder(), "states.yml.bak");
            if (dataFile.exists()) {
                if (bak.exists()) bak.delete();
                dataFile.renameTo(bak);
            }
            tmp.renameTo(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("states.yml kaydedilemedi: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public void loadAll() {
        loadConfigFile();
        if (cfg.contains("blocks")) {
            for (String key : cfg.getConfigurationSection("blocks").getKeys(false)) {
                try {
                    Map<String, Object> m = (Map<String, Object>) cfg.getConfigurationSection("blocks." + key).getValues(false);
                    SavedBlock sb = SavedBlock.fromMap(m);
                    blocks.put(key, sb);
                } catch (Exception e) {
                    plugin.getLogger().warning("states.yml yüklenirken hata: " + e.getMessage());
                }
            }
        }
        // load allowed chunks
        if (cfg.contains("allowedChunks")) {
            for (String k : cfg.getConfigurationSection("allowedChunks").getKeys(false)) {
                String val = cfg.getString("allowedChunks." + k);
                if (val != null) allowedChunks.add(val);
            }
        }

        // Apply states on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            int applied = 0;
            for (SavedBlock sb : blocks.values()) {
                try {
                    if (sb == null) continue;
                    if (Bukkit.getWorld(sb.world) == null) continue;
                    Block b = Bukkit.getWorld(sb.world).getBlockAt(sb.x, sb.y, sb.z);
                    Material mat = Material.getMaterial(sb.material);
                    if (mat == null) continue;
                    if (b.getType() != mat) b.setType(mat, false);
                    try {
                        BlockData bd = plugin.getServer().createBlockData(sb.blockData);
                        b.setBlockData(bd, false);
                        // apply redstone-specific properties after setting block data
                        if (!sb.props.isEmpty()) {
                            if (bd instanceof RedstoneWire && sb.props.containsKey("wirePower")) {
                                try {
                                    RedstoneWire rw = (RedstoneWire) bd;
                                    Object pw = sb.props.get("wirePower");
                                    if (pw instanceof Number) rw.setPower(((Number) pw).intValue());
                                    b.setBlockData(rw, false);
                                } catch (Exception ignored) {}
                            } else if (bd instanceof Repeater) {
                                try {
                                    Repeater rp = (Repeater) bd;
                                    if (sb.props.containsKey("repeaterDelay")) {
                                        Object d = sb.props.get("repeaterDelay");
                                        if (d instanceof Number) rp.setDelay(((Number) d).intValue());
                                    }
                                    if (sb.props.containsKey("repeaterPowered")) {
                                        Object pw = sb.props.get("repeaterPowered");
                                        if (pw instanceof Boolean) rp.setPowered((Boolean) pw);
                                    }
                                    b.setBlockData(rp, false);
                                } catch (Exception ignored) {}
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                        // fallback: ignore if blockdata string not valid on this server version
                    }
                    applied++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Bir blok uygulanamadı: " + ex.getMessage());
                }
            }
            plugin.getLogger().info("RedstoneStateSaver: " + applied + " blok yüklendi.");
            // schedule a follow-up task to apply physics for redstone components so circuits resume
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                int updated = 0;
                for (SavedBlock sb : blocks.values()) {
                    try {
                        if (sb == null) continue;
                        if (Bukkit.getWorld(sb.world) == null) continue;
                        Block b = Bukkit.getWorld(sb.world).getBlockAt(sb.x, sb.y, sb.z);
                        BlockData bd = b.getBlockData();
                        if (bd instanceof RedstoneWire || bd instanceof Repeater) {
                            // reapply with physics
                            b.setBlockData(bd, true);
                            updated++;
                        }
                    } catch (Exception ignored) {}
                }
                plugin.getLogger().info("RedstoneStateSaver: " + updated + " redstone blok için fizik uygulandı.");
            }, 2L);
        });
    }

    private String keyOf(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public static class SavedBlock {
        public String world;
        public int x, y, z;
        public String material;
        public String blockData;
    public final Map<String, Object> props = new java.util.HashMap<>();

        public SavedBlock(String world, int x, int y, int z, String material, String blockData) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
            this.blockData = blockData;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> base = new java.util.HashMap<>();
            base.put("world", world);
            base.put("x", x);
            base.put("y", y);
            base.put("z", z);
            base.put("material", material);
            base.put("blockData", blockData);
            if (!props.isEmpty()) base.put("props", props);
            return base;
        }

        @SuppressWarnings("unchecked")
        public static SavedBlock fromMap(Map<String, Object> m) {
            String world = (String) m.get("world");
            int x = ((Number) m.get("x")).intValue();
            int y = ((Number) m.get("y")).intValue();
            int z = ((Number) m.get("z")).intValue();
            String material = (String) m.get("material");
            String blockData = (String) m.getOrDefault("blockData", "");
            SavedBlock sb = new SavedBlock(world, x, y, z, material, blockData);
            if (m.containsKey("props") && m.get("props") instanceof Map) {
                Map<String, Object> p = (Map<String, Object>) m.get("props");
                sb.props.putAll(p);
            }
            return sb;
        }
    }


    public void allowChunk(String world, int chunkX, int chunkZ, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                allowedChunks.add(world + ":" + (chunkX + dx) + ":" + (chunkZ + dz));
            }
        }
    }

    public boolean isChunkAllowed(String world, int chunkX, int chunkZ) {
        return allowedChunks.contains(world + ":" + chunkX + ":" + chunkZ);
    }
}
