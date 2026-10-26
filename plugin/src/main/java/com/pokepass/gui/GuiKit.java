package com.pokepass.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** 造物品的小工具。 */
public final class GuiKit {

    private GuiKit() {
    }

    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    public static ItemStack item(Material material, int amount, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<String>();
                for (String line : lore) {
                    colored.add(color(line));
                }
                meta.setLore(colored);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack item(Material material, int amount, short data, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount), data);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<String>();
                for (String line : lore) {
                    colored.add(color(line));
                }
                meta.setLore(colored);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack glow(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 进度条：▮▮▮▯▯▯ */
    public static String bar(int current, int max, int length, String doneColor, String leftColor) {
        if (max <= 0) {
            return color("&7—");
        }
        int filled = (int) Math.round((double) Math.min(current, max) / max * length);
        StringBuilder sb = new StringBuilder();
        sb.append(color(doneColor));
        for (int i = 0; i < filled; i++) {
            sb.append('▮');
        }
        sb.append(color(leftColor));
        for (int i = filled; i < length; i++) {
            sb.append('▯');
        }
        return sb.toString();
    }

    /** "0,1,2" 或 "0-8" 解析成槽位列表 */
    public static List<Integer> slots(String raw) {
        List<Integer> out = new ArrayList<Integer>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        for (String part : raw.split(",")) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            int dash = text.indexOf('-');
            if (dash > 0) {
                try {
                    int from = Integer.parseInt(text.substring(0, dash).trim());
                    int to = Integer.parseInt(text.substring(dash + 1).trim());
                    for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                        out.add(i);
                    }
                } catch (NumberFormatException ignored) {
                }
            } else {
                try {
                    out.add(Integer.parseInt(text));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }
}
