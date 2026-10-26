package com.pokepass.reward;

import com.pokepass.PokePassPlugin;
import com.pokepass.data.PlayerProgress;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 奖励发放与领取记录。
 *
 * <p>双轨：免费轨人人可领，高级轨要权限。两条轨各自记录领取状态，
 * 所以「1 级免费领了、高级没领」是合法状态。</p>
 */
public class RewardService {

    public enum Result {
        OK,
        NOT_ENOUGH_LEVEL,
        NO_PERMISSION,
        ALREADY_CLAIMED,
        NO_REWARD
    }

    private final PokePassPlugin plugin;

    public RewardService(PokePassPlugin plugin) {
        this.plugin = plugin;
    }

    public Result claim(Player player, int level, boolean premiumTrack) {
        PlayerProgress progress = plugin.getProgressService().get(player);
        int currentLevel = progress.getLevel(plugin.getPassConfig());
        if (level > currentLevel) {
            return Result.NOT_ENOUGH_LEVEL;
        }
        List<RewardEntry> entries = premiumTrack
                ? plugin.getRewardBook().premium(level)
                : plugin.getRewardBook().free(level);
        if (entries.isEmpty()) {
            return Result.NO_REWARD;
        }
        if (premiumTrack && !hasPremium(player)) {
            return Result.NO_PERMISSION;
        }
        if (progress.isClaimed(level, premiumTrack)) {
            return Result.ALREADY_CLAIMED;
        }
        give(player, entries);
        progress.markClaimed(level, premiumTrack);
        plugin.getProgressService().store().save(progress);
        return Result.OK;
    }

    /** 批量领取所有「等级够、有权限、还没领」的奖励，返回领取条数 */
    public int claimAll(Player player) {
        PlayerProgress progress = plugin.getProgressService().get(player);
        int currentLevel = progress.getLevel(plugin.getPassConfig());
        int max = Math.min(currentLevel, plugin.getRewardBook().maxLevelWithReward());
        int claimed = 0;
        for (int level = 1; level <= max; level++) {
            if (claim(player, level, false) == Result.OK) {
                claimed++;
            }
            if (hasPremium(player) && claim(player, level, true) == Result.OK) {
                claimed++;
            }
        }
        return claimed;
    }

    public boolean hasPremium(Player player) {
        return plugin.getPassConfig().premiumEnabled()
                && player.hasPermission(plugin.getPassConfig().premiumPermission());
    }

    /** 有多少条奖励可以领（用于上线提示和界面角标） */
    public int claimable(Player player) {
        PlayerProgress progress = plugin.getProgressService().get(player);
        int currentLevel = progress.getLevel(plugin.getPassConfig());
        int max = Math.min(currentLevel, plugin.getRewardBook().maxLevelWithReward());
        boolean premium = hasPremium(player);
        int count = 0;
        for (int level = 1; level <= max; level++) {
            if (!progress.isClaimed(level, false) && !plugin.getRewardBook().free(level).isEmpty()) {
                count++;
            }
            if (premium && !progress.isClaimed(level, true) && !plugin.getRewardBook().premium(level).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public void notifyUnclaimed(Player player, PlayerProgress progress) {
        int count = claimable(player);
        if (count > 0) {
            player.sendMessage(plugin.getLang().msg("reward.available", "&a你有 &f%count% &a份战令奖励可以领取 &7(/zl reward)")
                    .replace("%count%", String.valueOf(count)));
        }
    }

    /** 真正发东西。任何一条失败都不影响其它条，也不会阻断领取标记。 */
    public void give(Player player, List<RewardEntry> entries) {
        for (RewardEntry entry : entries) {
            try {
                giveOne(player, entry);
            } catch (Throwable t) {
                plugin.getLogger().warning("发放奖励失败 " + entry.getRaw() + ": " + t.getMessage());
            }
        }
    }

    private void giveOne(Player player, RewardEntry entry) {
        switch (entry.getKind()) {
            case ITEM:
                giveItem(player, entry);
                break;
            case COMMAND:
                String command = entry.getPayload()
                        .replace("{player}", player.getName())
                        .replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                break;
            case POINTS:
                if (!plugin.getEconomy().givePoints(player, entry.getAmount())) {
                    player.sendMessage(plugin.getLang().msg("reward.points-unavailable",
                            "&c点券系统未就绪，这部分奖励请联系管理员补发"));
                }
                break;
            case MONEY:
                if (!plugin.getEconomy().giveMoney(player, entry.getAmount())) {
                    player.sendMessage(plugin.getLang().msg("reward.money-unavailable",
                            "&c经济系统未就绪，这部分奖励请联系管理员补发"));
                }
                break;
            default:
                break;
        }
    }

    private void giveItem(Player player, RewardEntry entry) {
        Material material;
        try {
            material = Material.valueOf(entry.getPayload().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("奖励物品名无效: " + entry.getPayload());
            return;
        }
        int remaining = entry.getAmount();
        ItemStack template = new ItemStack(material, 1, entry.getData());
        int maxStack = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            int stackSize = Math.min(maxStack, remaining);
            ItemStack stack = new ItemStack(material, stackSize, entry.getData());
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            remaining -= stackSize;
        }
    }

    /** 给界面用：把一条奖励渲染成一行可读文字 */
    public String describe(RewardEntry entry) {
        switch (entry.getKind()) {
            case ITEM:
                return "&f" + prettyMaterial(entry.getPayload()) + (entry.getAmount() > 1 ? " &7x&f" + entry.getAmount() : "");
            case COMMAND:
                return "&7命令 &f" + entry.getPayload();
            case POINTS:
                return "&e点券 &f" + entry.getAmount();
            case MONEY:
                return "&6金币 &f" + entry.getAmount();
            default:
                return "&7?";
        }
    }

    private String prettyMaterial(String raw) {
        String text = raw.replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    public List<String> describeAll(List<RewardEntry> entries) {
        List<String> out = new ArrayList<String>();
        for (RewardEntry entry : entries) {
            out.add(ChatColor.translateAlternateColorCodes('&', describe(entry)));
        }
        return out;
    }

    /** 未领取等级列表（界面标注用） */
    public Map<Integer, Boolean> claimState(Player player) {
        PlayerProgress progress = plugin.getProgressService().get(player);
        int currentLevel = progress.getLevel(plugin.getPassConfig());
        Map<Integer, Boolean> state = new HashMap<Integer, Boolean>();
        for (int level = 1; level <= Math.min(currentLevel, plugin.getRewardBook().maxLevelWithReward()); level++) {
            state.put(level, !progress.isClaimed(level, false));
        }
        return state;
    }
}
