package com.pokepass.shop;

import com.pokepass.config.Configs;
import com.pokepass.reward.RewardEntry;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 兑换商店配置（shop.yml）。
 *
 * <p>价格只有 points / money 两种，发货复用战令奖励的写法，
 * 所以「商店卖什么」和「升级奖励发什么」是同一套语法，服主学一次就够。</p>
 */
public class ShopBook {

    /** 没写 slot 时按顺序填这些格子（界面内两排） */
    private static final int[] DEFAULT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final JavaPlugin plugin;
    private final List<ShopItem> items = new ArrayList<ShopItem>();
    private final List<String> warnings = new ArrayList<String>();
    private String title = "&8宝可梦通行证 | 兑换商店";
    private int size = 27;

    public ShopBook(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        items.clear();
        warnings.clear();
        YamlConfiguration cfg = Configs.load(plugin, "shop.yml");
        title = cfg.getString("title", title);
        size = normalizeSize(cfg.getInt("size", 27));

        ConfigurationSection root = cfg.getConfigurationSection("items");
        if (root == null) {
            warnings.add("shop.yml 里没有 items 段");
            return;
        }

        Set<Integer> usedSlots = new HashSet<Integer>();
        int autoIndex = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<RewardEntry> rewards = new ArrayList<RewardEntry>();
            for (String raw : section.getStringList("rewards")) {
                RewardEntry entry = RewardEntry.parse(raw);
                if (entry == null) {
                    warnings.add("商店 " + id + " 的奖励写法无法识别: " + raw);
                    continue;
                }
                rewards.add(entry);
            }
            if (rewards.isEmpty()) {
                warnings.add("商店 " + id + " 没有任何可用奖励，已跳过");
                continue;
            }

            ShopItem.Cost cost = parseCost(section.getString("cost.type", "points"));
            int price = section.getInt("cost.amount", 0);
            if (cost == ShopItem.Cost.NONE || price <= 0) {
                warnings.add("商店 " + id + " 的价格无效（cost.type=" + section.getString("cost.type")
                        + ", cost.amount=" + price + "），已跳过");
                continue;
            }

            Material icon;
            try {
                icon = Material.valueOf(section.getString("icon", "PAPER").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                icon = Material.PAPER;
                warnings.add("商店 " + id + " 的 icon 无效，已退化为 PAPER");
            }
            // sprite：精灵头像（Pixelmon 的 pixelmon_sprite），失败退回普通材质
            org.bukkit.inventory.ItemStack spriteIcon = null;
            String sprite = section.getString("sprite", "");
            if (!sprite.trim().isEmpty()) {
                spriteIcon = com.pokepass.util.PixelmonSprites.create(sprite,
                        section.getInt("form", 0),
                        section.getBoolean("shiny", false),
                        warnings, "shop.yml: " + id);
            }

            List<String> lore = new ArrayList<String>();
            for (String line : section.getStringList("lore")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            int slot = section.getInt("slot", -1);
            if (slot < 0 || slot >= size || usedSlots.contains(slot)) {
                slot = nextFreeSlot(usedSlots, autoIndex);
                autoIndex++;
                if (slot < 0) {
                    warnings.add("商店 " + id + " 没有可用槽位了（界面放满），已跳过");
                    continue;
                }
            }
            usedSlots.add(slot);

            items.add(new ShopItem(id,
                    icon,
                    (short) section.getInt("data", 0),
                    ChatColor.translateAlternateColorCodes('&', section.getString("name", id)),
                    lore,
                    cost,
                    price,
                    section.getString("permission", ""),
                    Math.max(0, section.getInt("limit", 0)),
                    slot,
                    rewards,
                    spriteIcon));
        }
    }

    /** 自动排位：优先用标准格子，都被占了就往后找空格 */
    private int nextFreeSlot(Set<Integer> used, int autoIndex) {
        for (int i = autoIndex; i < DEFAULT_SLOTS.length; i++) {
            if (!used.contains(DEFAULT_SLOTS[i])) {
                return DEFAULT_SLOTS[i];
            }
        }
        for (int i = 0; i < size; i++) {
            if (!used.contains(i)) {
                return i;
            }
        }
        return -1;
    }

    private int normalizeSize(int raw) {
        int clamped = Math.max(9, Math.min(54, raw));
        // Bukkit 只接受 9 的倍数，写 27 是标准，写 30 会被服务端拒绝
        return clamped - clamped % 9;
    }

    private ShopItem.Cost parseCost(String raw) {
        if (raw == null) {
            return ShopItem.Cost.NONE;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if ("points".equals(text) || "point".equals(text) || "pp".equals(text)) {
            return ShopItem.Cost.POINTS;
        }
        if ("money".equals(text) || "vault".equals(text)) {
            return ShopItem.Cost.MONEY;
        }
        return ShopItem.Cost.NONE;
    }

    public List<ShopItem> items() {
        return Collections.unmodifiableList(items);
    }

    public ShopItem find(String id) {
        for (ShopItem item : items) {
            if (item.getId().equalsIgnoreCase(id)) {
                return item;
            }
        }
        return null;
    }

    public String title() {
        return title;
    }

    public int size() {
        return size;
    }

    public List<String> warnings() {
        return warnings;
    }
}
