package com.pokepass.config;

import com.pokepass.quest.Quest;
import com.pokepass.quest.QuestType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 任务表：从 quest/daily.yml 与 quest/weekly.yml 读出来。
 *
 * <p>每日任务支持「每周期只抽 N 门」（config 的 daily.count）。抽样用的是
 * 「玩家 UUID + 日期」做种子，所以同一个人当天重登看到的是同一批任务，
 * 不会靠反复重登刷题；不同玩家各抽各的。</p>
 */
public class QuestBook {

    private final JavaPlugin plugin;
    private final List<Quest> daily = new ArrayList<Quest>();
    private final List<Quest> weekly = new ArrayList<Quest>();
    private final List<String> warnings = new ArrayList<String>();

    public QuestBook(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        daily.clear();
        weekly.clear();
        warnings.clear();
        read("quest/daily.yml", Quest.Period.DAILY, daily);
        read("quest/weekly.yml", Quest.Period.WEEKLY, weekly);
    }

    private void read(String path, Quest.Period period, List<Quest> target) {
        YamlConfiguration cfg = Configs.load(plugin, path);
        ConfigurationSection root = cfg.getConfigurationSection("quests");
        if (root == null) {
            warnings.add(path + " 里没有 quests 段");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            QuestType type = QuestType.of(section.getString("type"));
            if (type == null) {
                warnings.add(period + " 任务 " + id + " 的 type 无法识别: " + section.getString("type"));
                continue;
            }
            Material icon;
            try {
                icon = Material.valueOf(section.getString("icon", "PAPER").toUpperCase());
            } catch (IllegalArgumentException e) {
                icon = Material.PAPER;
                warnings.add(period + " 任务 " + id + " 的 icon 无效，已退化为 PAPER");
            }
            // sprite：精灵头像（Pixelmon 的 pixelmon_sprite），失败退回普通材质
            org.bukkit.inventory.ItemStack spriteIcon = null;
            String sprite = section.getString("sprite", "");
            if (!sprite.trim().isEmpty()) {
                spriteIcon = com.pokepass.util.PixelmonSprites.create(sprite,
                        section.getInt("form", 0),
                        section.getBoolean("shiny", false),
                        warnings, path + " 任务 " + id);
            }
            List<String> lore = new ArrayList<String>();
            for (String line : section.getStringList("lore")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            target.add(new Quest(
                    id,
                    period,
                    ChatColor.translateAlternateColorCodes('&', section.getString("name", id)),
                    type,
                    section.getInt("amount", 1),
                    Quest.parseConditions(section.getString("condition", "*")),
                    section.getInt("exp", 100),
                    icon,
                    (short) section.getInt("data", 0),
                    lore,
                    spriteIcon,
                    section.getInt("page", 1)));
        }
    }

    public List<Quest> all(Quest.Period period) {
        return Collections.unmodifiableList(period == Quest.Period.DAILY ? daily : weekly);
    }

    /** 该玩家本周期可见的任务（含抽样），不按页过滤 */
    public List<Quest> forPlayer(Quest.Period period, UUID uuid, String periodKey, int limit) {
        return forPlayer(period, uuid, periodKey, limit, null);
    }

    /**
     * 该玩家本周期可见的任务。
     *
     * @param unlockedPages 允许出现的页；null 表示不按页过滤（每日任务用 null）。
     *                      过滤发生在抽样之前，这样「抽 3 个」抽的是他能做的那几页。
     */
    public List<Quest> forPlayer(Quest.Period period, UUID uuid, String periodKey, int limit,
                                 java.util.Set<Integer> unlockedPages) {
        List<Quest> source = new ArrayList<Quest>();
        for (Quest quest : all(period)) {
            if (unlockedPages == null || unlockedPages.contains(quest.getPage())) {
                source.add(quest);
            }
        }
        if (source.isEmpty()) {
            return source;
        }
        if (limit <= 0 || limit >= source.size()) {
            return source;
        }
        Collections.shuffle(source, new Random(("pokepass:" + period + ":" + periodKey + ":" + uuid).hashCode()));
        return new ArrayList<Quest>(source.subList(0, limit));
    }

    /** 这个周期一共有几页（按配置里最大的 page 算，至少 1） */
    public int pages(Quest.Period period) {
        int max = 1;
        for (Quest quest : all(period)) {
            max = Math.max(max, quest.getPage());
        }
        return max;
    }

    public Quest find(Quest.Period period, String id) {
        for (Quest quest : all(period)) {
            if (quest.getId().equalsIgnoreCase(id)) {
                return quest;
            }
        }
        return null;
    }

    public int size(Quest.Period period) {
        return period == Quest.Period.DAILY ? daily.size() : weekly.size();
    }

    public List<String> warnings() {
        return warnings;
    }
}
