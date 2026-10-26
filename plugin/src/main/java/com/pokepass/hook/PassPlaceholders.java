package com.pokepass.hook;

import com.pokepass.PokePassPlugin;
import com.pokepass.data.PlayerProgress;
import com.pokepass.quest.Quest;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * PlaceholderAPI 变量。只有在服务端装了 PlaceholderAPI 时才会被实例化，
 * 所以本插件对 PAPI 是软依赖（plugin.yml 里只写 softdepend）。
 *
 * <pre>
 * %pixelmontxz_level%        战令等级
 * %pixelmontxz_max_level%    等级上限
 * %pixelmontxz_exp%          当前等级内经验
 * %pixelmontxz_exp_needed%   升到下一级需要多少
 * %pixelmontxz_exp_total%    累计总经验
 * %pixelmontxz_percent%      当前等级进度 0-100
 * %pixelmontxz_daily_done%   已完成每日任务数
 * %pixelmontxz_daily_total%  每日任务总数
 * %pixelmontxz_weekly_done%  已完成每周任务数
 * %pixelmontxz_weekly_total% 每周任务总数
 * %pixelmontxz_claimable%    可领取的奖励份数
 * %pixelmontxz_premium%      yes / no（有没有高级轨权限）
 * %pixelmontxz_season%       当前赛季编号
 * %pixelmontxz_season_days%  距换季还剩几天
 * %pixelmontxz_caught%       已捕捉过的物种数
 * </pre>
 *
 * 另外注册一个 {@code zhanling} 标识符作别名，%zhanling_level% 同样可用。
 */
public class PassPlaceholders extends PlaceholderExpansion {

    private final PokePassPlugin plugin;
    private final String identifier;

    public PassPlaceholders(PokePassPlugin plugin) {
        this(plugin, "pixelmontxz");
    }

    public PassPlaceholders(PokePassPlugin plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getAuthor() {
        return "PixelmonTXZ";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        // PAPI 重载后不需要重新注册
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || params == null) {
            return null;
        }
        String key = params.toLowerCase();

        // 只查不建：计分板/HUD 会对离线玩家也拉变量，用 get() 会凭空写一行 0 分档案
        PlayerProgress progress = plugin.getProgressService().peek(player.getUniqueId());
        if (progress == null) {
            return offlineDefault(key);
        }

        long now = System.currentTimeMillis();
        int level = progress.getLevel(plugin.getPassConfig());
        int maxLevel = plugin.getPassConfig().maxLevel();
        int inLevel = progress.expWithinLevel(plugin.getPassConfig());
        int need = plugin.getPassConfig().expForLevel(level);

        if ("level".equals(key)) {
            return String.valueOf(level);
        }
        if ("max_level".equals(key)) {
            return String.valueOf(maxLevel);
        }
        if ("exp".equals(key)) {
            return String.valueOf(inLevel);
        }
        if ("exp_needed".equals(key)) {
            return need == Integer.MAX_VALUE ? "0" : String.valueOf(need);
        }
        if ("exp_total".equals(key)) {
            return String.valueOf(progress.getTotalExp());
        }
        if ("percent".equals(key)) {
            if (need == Integer.MAX_VALUE || need <= 0) {
                return "100";
            }
            return String.valueOf(Math.min(100, (int) Math.round(inLevel * 100.0D / need)));
        }
        if ("daily_done".equals(key) || "daily_total".equals(key)) {
            List<Quest> quests = plugin.getQuestBook().forPlayer(Quest.Period.DAILY,
                    player.getUniqueId(), progress.getDailyKey(), plugin.getPassConfig().dailyCount());
            return "daily_total".equals(key)
                    ? String.valueOf(quests.size())
                    : String.valueOf(doneCount(quests, progress));
        }
        if ("weekly_done".equals(key) || "weekly_total".equals(key)) {
            List<Quest> quests = plugin.getQuestBook().forPlayer(Quest.Period.WEEKLY,
                    player.getUniqueId(), progress.getWeeklyKey(), plugin.getPassConfig().weeklyCount());
            return "weekly_total".equals(key)
                    ? String.valueOf(quests.size())
                    : String.valueOf(doneCount(quests, progress));
        }
        if ("claimable".equals(key)) {
            // 在线时有 Player 就用原方法（能算上高级轨）；离线只能算免费轨
            Player online = player.getPlayer();
            if (online != null) {
                return String.valueOf(plugin.getRewardService().claimable(online));
            }
            int limit = Math.min(progress.getLevel(plugin.getPassConfig()),
                    plugin.getRewardBook().maxLevelWithReward());
            int count = 0;
            for (int l = 1; l <= limit; l++) {
                if (!progress.isClaimed(l, false) && !plugin.getRewardBook().free(l).isEmpty()) {
                    count++;
                }
            }
            return String.valueOf(count);
        }
        if ("premium".equals(key)) {
            Player online = player.getPlayer();
            if (online == null) {
                return "no";
            }
            return plugin.getRewardService().hasPremium(online) ? "yes" : "no";
        }
        if ("season".equals(key)) {
            return String.valueOf(plugin.getPassConfig().seasonId(now));
        }
        if ("season_days".equals(key)) {
            int days = plugin.getPassConfig().seasonDaysLeft(now);
            return days < 0 ? "-1" : String.valueOf(days);
        }
        if ("caught".equals(key)) {
            return String.valueOf(progress.getCaughtSpecies().size());
        }
        return null;
    }

    private int doneCount(List<Quest> quests, PlayerProgress progress) {
        int count = 0;
        for (Quest quest : quests) {
            if (progress.isComplete(quest)) {
                count++;
            }
        }
        return count;
    }

    /** 没有缓存档案时的返回值：数值给 0，布尔类给 no，避免计分板上出现 null */
    private String offlineDefault(String key) {
        if ("premium".equals(key)) {
            return "no";
        }
        if ("max_level".equals(key)) {
            return String.valueOf(plugin.getPassConfig().maxLevel());
        }
        if ("season".equals(key)) {
            return String.valueOf(plugin.getPassConfig().seasonId(System.currentTimeMillis()));
        }
        if ("season_days".equals(key)) {
            return String.valueOf(plugin.getPassConfig().seasonDaysLeft(System.currentTimeMillis()));
        }
        return "0";
    }
}
