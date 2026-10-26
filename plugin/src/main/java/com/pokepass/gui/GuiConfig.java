package com.pokepass.gui;

import com.pokepass.config.Configs;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** gui.yml：三个界面的标题和尺寸。内容布局写在代码里，避免为每个格子写一堆配置。 */
public class GuiConfig {

    private final JavaPlugin plugin;
    private YamlConfiguration config;

    public GuiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.config = Configs.load(plugin, "gui.yml");
    }

    public String title(String key, String fallback) {
        return config.getString(key + ".title", fallback);
    }

    public int size(String key, int fallback) {
        int size = config.getInt(key + ".size", fallback);
        if (size < 9 || size > 54 || size % 9 != 0) {
            return fallback;
        }
        return size;
    }
}
