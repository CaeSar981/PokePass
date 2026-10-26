package com.pokepass.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/** YAML 读写小工具：文件不存在就从 jar 里释放一份默认的。 */
public final class Configs {

    private Configs() {
    }

    public static YamlConfiguration load(JavaPlugin plugin, String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            try {
                plugin.saveResource(path, false);
            } catch (IllegalArgumentException ignored) {
                // jar 里没有这个资源，留空文件
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public static boolean save(YamlConfiguration config, File file) {
        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
