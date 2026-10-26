package com.pokepass.config;

import com.pokepass.reward.RewardEntry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 等级奖励表（reward.yml）。
 *
 * <p>每一级分两条轨：{@code free} 人人可领，{@code premium} 需要
 * {@code pass.premium.permission} 权限。两条轨各自独立领取。</p>
 */
public class RewardBook {

    private final JavaPlugin plugin;
    private final Map<Integer, List<RewardEntry>> free = new LinkedHashMap<Integer, List<RewardEntry>>();
    private final Map<Integer, List<RewardEntry>> premium = new LinkedHashMap<Integer, List<RewardEntry>>();
    private final List<String> warnings = new ArrayList<String>();

    public RewardBook(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        free.clear();
        premium.clear();
        warnings.clear();
        YamlConfiguration cfg = Configs.load(plugin, "reward.yml");
        ConfigurationSection root = cfg.getConfigurationSection("levels");
        if (root == null) {
            warnings.add("reward.yml 里没有 levels 段");
            return;
        }
        for (String key : root.getKeys(false)) {
            Integer level = parseInt(key);
            if (level == null) {
                warnings.add("reward.yml 的等级键不是数字: " + key);
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            free.put(level, readList(section.getStringList("free"), level, "free"));
            premium.put(level, readList(section.getStringList("premium"), level, "premium"));
        }
    }

    private List<RewardEntry> readList(List<String> raw, int level, String track) {
        List<RewardEntry> out = new ArrayList<RewardEntry>();
        for (String line : raw) {
            RewardEntry entry = RewardEntry.parse(line);
            if (entry == null) {
                warnings.add("reward.yml 第 " + level + " 级 " + track + " 有无法解析的奖励: " + line);
                continue;
            }
            out.add(entry);
        }
        return out;
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<RewardEntry> free(int level) {
        List<RewardEntry> list = free.get(level);
        return list == null ? Collections.<RewardEntry>emptyList() : list;
    }

    public List<RewardEntry> premium(int level) {
        List<RewardEntry> list = premium.get(level);
        return list == null ? Collections.<RewardEntry>emptyList() : list;
    }

    /** 有奖励的最大等级（用于界面翻页上界） */
    public int maxLevelWithReward() {
        int max = 0;
        for (Integer level : free.keySet()) {
            max = Math.max(max, level);
        }
        for (Integer level : premium.keySet()) {
            max = Math.max(max, level);
        }
        return max;
    }

    public List<String> warnings() {
        return warnings;
    }
}
