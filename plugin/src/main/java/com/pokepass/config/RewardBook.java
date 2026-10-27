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
        checkCoverage();
    }

    /**
     * 等级上限与奖励表对不上时提前报出来。
     *
     * <p>超出 max-level 的条目玩家永远到不了（升级到顶就停了），
     * 而没配到的等级点开奖励页只会看到「无奖励」—— 两种都是配置写漏，
     * 放在加载期告警比等玩家反馈省事。</p>
     */
    private void checkCoverage() {
        int maxLevel = Math.max(1, plugin.getConfig().getInt("pass.max-level", 50));
        for (Integer level : free.keySet()) {
            if (level > maxLevel) {
                warnings.add("reward.yml 第 " + level + " 级超出 pass.max-level(" + maxLevel
                        + ")，这一级玩家永远达不到");
            }
        }
        for (int level = 1; level <= maxLevel; level++) {
            if (free(level).isEmpty() && premium(level).isEmpty()) {
                warnings.add("reward.yml 第 " + level + " 级没有任何奖励（free/premium 都是空），"
                        + "奖励页这一级会显示「无奖励」");
            }
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
            // 物品名在加载时就校验。等到玩家点领取才发现的话，这一级会被整级拦下
            // （预检机制不允许「标记已领但没到账」），结果是这一级永久领不了，很难查。
            if (entry.getKind() == RewardEntry.Kind.ITEM && !validMaterial(entry.getPayload())) {
                warnings.add("reward.yml 第 " + level + " 级 " + track + " 的物品名无效: "
                        + entry.getPayload() + "（1.12.2 没有这个 Material，这一级会领不了）");
            }
            out.add(entry);
        }
        return out;
    }

    private boolean validMaterial(String name) {
        try {
            org.bukkit.Material.valueOf(name.toUpperCase(java.util.Locale.ROOT));
            return true;
        } catch (Throwable t) {
            return false;
        }
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

    /** 真正配了奖励的等级个数（不是最高等级，中间留空不算） */
    public int coveredLevels() {
        int count = 0;
        for (Integer level : free.keySet()) {
            if (!free(level).isEmpty() || !premium(level).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public List<String> warnings() {
        return warnings;
    }
}
