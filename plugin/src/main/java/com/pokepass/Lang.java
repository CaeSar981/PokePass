package com.pokepass;

import com.pokepass.config.Configs;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** message.yml 的读取器，所有对外文案都从这里出。 */
public class Lang {

    private final JavaPlugin plugin;
    private YamlConfiguration config;
    private String prefix;

    public Lang(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.config = Configs.load(plugin, "message.yml");
        this.prefix = color(config.getString("prefix", "&6&l战令 &8» &r"));
    }

    public String prefix() {
        return prefix;
    }

    /** 取一条消息（已上色），带前缀 */
    public String msg(String key, String fallback) {
        String raw = config.getString(key);
        return prefix + color(raw == null ? fallback : raw);
    }

    /** 取一条消息，不带前缀 */
    public String plain(String key, String fallback) {
        String raw = config.getString(key);
        return color(raw == null ? fallback : raw);
    }

    public String list(String key, String fallback) {
        return plain(key, fallback);
    }

    /** 取一组多行文本（已上色）；配置缺失时用兜底列表 */
    public java.util.List<String> lines(String key, java.util.List<String> fallback) {
        java.util.List<String> raw = config.getStringList(key);
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (raw == null || raw.isEmpty()) {
            for (String line : fallback) {
                out.add(color(line));
            }
            return out;
        }
        for (String line : raw) {
            out.add(color(line));
        }
        return out;
    }

    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
}
