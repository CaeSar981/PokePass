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
 *
 * <p>发放顺序是「先预检、再发放、最后落库」。预检这一步不能省：
 * 以前是直接发、发完无条件标记已领，一旦某条发不出去（点券系统没装、
 * 物品名写错），玩家就被标记成已领取，奖励永久丢失且无法重领，
 * 只能让管理员去改数据库。</p>
 */
public class RewardService {

    public enum Result {
        OK,
        NOT_ENOUGH_LEVEL,
        NO_PERMISSION,
        ALREADY_CLAIMED,
        NO_REWARD,
        /** 这级奖励现在发不出去（前置没装 / 配置写错）。<b>没有标记已领</b>，修好后可以再领。 */
        UNAVAILABLE
    }

    /** 一次领取的内部结果，带失败原因，供批量领取汇总用（不直接给玩家发消息） */
    private static final class Outcome {
        private final Result result;
        private final String reason;
        private final int failedCount;

        Outcome(Result result, String reason, int failedCount) {
            this.result = result;
            this.reason = reason;
            this.failedCount = failedCount;
        }

        static Outcome of(Result result) {
            return new Outcome(result, null, 0);
        }
    }

    private final PokePassPlugin plugin;

    public RewardService(PokePassPlugin plugin) {
        this.plugin = plugin;
    }

    /** 单个等级领取，失败会直接给玩家提示 */
    public Result claim(Player player, int level, boolean premiumTrack) {
        Outcome outcome = claimInternal(player, level, premiumTrack, true);
        if (outcome.result == Result.UNAVAILABLE) {
            player.sendMessage(plugin.getLang().msg("reward.unavailable", "&c这级奖励暂时领不了：%reason%")
                    .replace("%reason%", outcome.reason == null ? "前置未就绪" : outcome.reason));
        } else if (outcome.failedCount > 0) {
            player.sendMessage(plugin.getLang().msg("reward.partial", "&e有 %count% 项奖励没发成功，请联系管理员补发")
                    .replace("%count%", String.valueOf(outcome.failedCount)));
        }
        return outcome.result;
    }

    /**
     * 真正的领取逻辑。
     *
     * @param persist 是否立刻落库。批量领取传 false，由调用方最后统一存一次，
     *                避免「50 级 × 2 轨 = 100 次同步 SQLite 写入」卡在主线程上。
     */
    private Outcome claimInternal(Player player, int level, boolean premiumTrack, boolean persist) {
        PlayerProgress progress = plugin.getProgressService().get(player);
        int currentLevel = progress.getLevel(plugin.getPassConfig());
        if (level > currentLevel) {
            return Outcome.of(Result.NOT_ENOUGH_LEVEL);
        }
        List<RewardEntry> entries = premiumTrack
                ? plugin.getRewardBook().premium(level)
                : plugin.getRewardBook().free(level);
        if (entries.isEmpty()) {
            return Outcome.of(Result.NO_REWARD);
        }
        if (premiumTrack && !hasPremium(player)) {
            return Outcome.of(Result.NO_PERMISSION);
        }
        if (progress.isClaimed(level, premiumTrack)) {
            return Outcome.of(Result.ALREADY_CLAIMED);
        }

        // 预检：这一级只要有一条发不出去，就整级不发也不标记。
        // 保证「领了就是拿到了」，不会出现标记已领却没到账的死账。
        String blocked = whyUnavailable(entries);
        if (blocked != null) {
            return new Outcome(Result.UNAVAILABLE, blocked, 0);
        }

        List<RewardEntry> failed = give(player, entries);
        progress.markClaimed(level, premiumTrack);
        if (persist) {
            plugin.getProgressService().store().save(progress);
        }
        if (!failed.isEmpty()) {
            // 预检放行了却仍然失败，属于运行时意外（命令抛错等）。
            // 这种情况仍然标记已领，否则玩家反复重领会把前面发成功的条目刷成多份；
            // 代价是必须把缺了哪几条讲清楚，并留下日志给管理员补发。
            plugin.getLogger().warning("玩家 " + player.getName() + " 领取 " + level + " 级"
                    + (premiumTrack ? "高级轨" : "免费轨") + " 奖励时，以下条目发放失败，需人工补发：");
            for (RewardEntry entry : failed) {
                plugin.getLogger().warning("  - " + entry.getRaw());
            }
        }
        return new Outcome(Result.OK, null, failed.size());
    }

    /** 批量领取所有「等级够、有权限、还没领」的奖励，返回真正领到的条数 */
    public int claimAll(Player player) {
        PlayerProgress progress = plugin.getProgressService().get(player);
        int currentLevel = progress.getLevel(plugin.getPassConfig());
        int max = Math.min(currentLevel, plugin.getRewardBook().maxLevelWithReward());
        boolean premium = hasPremium(player);
        int claimed = 0;
        int blocked = 0;
        for (int level = 1; level <= max; level++) {
            Outcome free = claimInternal(player, level, false, false);
            if (free.result == Result.OK) {
                claimed++;
            } else if (free.result == Result.UNAVAILABLE) {
                blocked++;
            }
            if (premium) {
                Outcome premiumOutcome = claimInternal(player, level, true, false);
                if (premiumOutcome.result == Result.OK) {
                    claimed++;
                } else if (premiumOutcome.result == Result.UNAVAILABLE) {
                    blocked++;
                }
            }
        }
        // 整个批次只落库一次
        if (claimed > 0) {
            plugin.getProgressService().store().save(progress);
        }
        if (blocked > 0) {
            player.sendMessage(plugin.getLang().msg("reward.claim-blocked",
                            "&e有 %count% 级奖励因系统未就绪暂时领不了，修好后可以再领")
                    .replace("%count%", String.valueOf(blocked)));
        }
        return claimed;
    }

    public boolean hasPremium(Player player) {
        return hasPremium(player, plugin.getProgressService().get(player));
    }

    /**
     * 高级会员判定：权限 或 玩家数据里的标记。
     *
     * <p>两条路是有意并存的：接权限组（LuckPerms 等）的服用权限；不接权限组、
     * 只想单独补某个玩家的服用 {@code /zl addvip} 直接写标记。</p>
     */
    public boolean hasPremium(Player player, PlayerProgress progress) {
        if (!plugin.getPassConfig().premiumEnabled()) {
            return false;
        }
        if (player.hasPermission(plugin.getPassConfig().premiumPermission())) {
            return true;
        }
        return progress != null && progress.isPremiumFlag();
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

    /**
     * 预检一条奖励能不能真的发出去，返回 null 表示都能发。
     *
     * <p>覆盖的都是「必然失败」的情况：物品名拼错、命令为空、点券/经济前置没装。
     * 这些如果放进 give() 里靠异常兜，就会变成静默失败。</p>
     */
    public String whyUnavailable(List<RewardEntry> entries) {
        for (RewardEntry entry : entries) {
            switch (entry.getKind()) {
                case ITEM:
                    String invalid = invalidItem(entry);
                    if (invalid != null) {
                        return invalid;
                    }
                    break;
                case COMMAND:
                    if (entry.getPayload() == null || entry.getPayload().trim().isEmpty()) {
                        return "奖励命令为空";
                    }
                    break;
                case POINTS:
                    if (!plugin.getEconomy().pointsAvailable()) {
                        return "点券系统未就绪";
                    }
                    break;
                case MONEY:
                    if (!plugin.getEconomy().vaultAvailable()) {
                        return "经济系统未就绪";
                    }
                    break;
                default:
                    break;
            }
        }
        return null;
    }

    private String invalidItem(RewardEntry entry) {
        try {
            Material material = Material.valueOf(entry.getPayload().toUpperCase());
            // data 越界在部分材质上会抛，构造一次才能真正确认
            new ItemStack(material, 1, entry.getData());
            return null;
        } catch (Throwable t) {
            return "物品名无效 " + entry.getPayload();
        }
    }

    /** 真正发东西，返回没发成功的条目（空列表表示全成功）。任何一条失败都不影响其它条。 */
    public List<RewardEntry> give(Player player, List<RewardEntry> entries) {
        List<RewardEntry> failed = new ArrayList<RewardEntry>();
        for (RewardEntry entry : entries) {
            try {
                if (!giveOne(player, entry)) {
                    failed.add(entry);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("发放奖励失败 " + entry.getRaw() + ": " + t.getMessage());
                failed.add(entry);
            }
        }
        return failed;
    }

    private boolean giveOne(Player player, RewardEntry entry) {
        switch (entry.getKind()) {
            case ITEM:
                return giveItem(player, entry);
            case COMMAND:
                String command = entry.getPayload()
                        .replace("{player}", player.getName())
                        .replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                return true;
            case POINTS:
                return plugin.getEconomy().givePoints(player, entry.getAmount());
            case MONEY:
                return plugin.getEconomy().giveMoney(player, entry.getAmount());
            default:
                return true;
        }
    }

    private boolean giveItem(Player player, RewardEntry entry) {
        Material material;
        try {
            material = Material.valueOf(entry.getPayload().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("奖励物品名无效: " + entry.getPayload());
            return false;
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
        return true;
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
