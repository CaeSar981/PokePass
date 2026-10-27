package com.pokepass.data;

import com.pokepass.PokePassPlugin;
import com.pokepass.config.PassConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 进度缓存 + 周期切换。
 *
 * <p>所有人都会走 {@link #get(Player)}，它顺手做一次周期比对：
 * 日期变了就把每日/每周进度清掉。这样不需要额外的定时重置任务。</p>
 */
public class ProgressService {

    private final PokePassPlugin plugin;
    private final ProgressStore store;
    private final Map<UUID, PlayerProgress> cache = new HashMap<UUID, PlayerProgress>();

    public ProgressService(PokePassPlugin plugin, ProgressStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public ProgressStore store() {
        return store;
    }

    public PlayerProgress get(Player player) {
        PlayerProgress progress = cache.get(player.getUniqueId());
        if (progress == null) {
            progress = store.load(player.getUniqueId());
            cache.put(player.getUniqueId(), progress);
        }
        progress.setLastName(player.getName());
        // 老数据/新档案没有首次登录时间：从这次开始算资历。
        // 升级前就在服里的玩家也只能从今天起跑，没法凭空补出真实的首登时间。
        boolean stamped = false;
        if (progress.getFirstJoin() <= 0) {
            progress.setFirstJoin(System.currentTimeMillis());
            stamped = true;
        }
        // 重置（换季 / 跨天）立刻落库：这类事件很少发生，但如果只在定时任务里存，
        // 玩家重置完马上崩服就会拿着旧数据回来，等级又「复活」一次
        if (refreshPeriod(progress) || stamped) {
            store.save(progress);
        }
        return progress;
    }

    public PlayerProgress peek(UUID uuid) {
        return cache.get(uuid);
    }

    /** 按键值判断是否跨周期；跨了就清进度。返回 true 表示有东西被重置 */
    public boolean refreshPeriod(PlayerProgress progress) {
        PassConfig config = plugin.getPassConfig();
        long now = System.currentTimeMillis();
        boolean changed = false;

        // 赛季：换季要连等级和领取记录一起清，所以放在周期之前判断。
        // seasonId 为 0 的是老数据/新档案，只打标不算换季，别给玩家发一条莫名其妙的公告。
        if (config.seasonEnabled()) {
            int current = config.seasonId(now);
            int before = progress.getSeasonId();
            if (before != current) {
                progress.resetForSeason(current);
                changed = true;
                if (before > 0) {
                    announceSeason(progress, before, current);
                }
            }
        }

        String daily = config.dailyKey(now);
        String weekly = config.weeklyKey(now);
        if (progress.ensurePeriod(daily, weekly)) {
            progress.touch();
            changed = true;
        }
        return changed;
    }

    /** 换季公告 + 日志。只在真的从旧赛季跨过来时调 */
    private void announceSeason(PlayerProgress progress, int from, int to) {
        Player online = Bukkit.getPlayer(progress.getUuid());
        if (online != null) {
            online.sendMessage(plugin.getLang().msg("season.rolled",
                            "&6&l新赛季开始! &f第 %season% 赛季 &7(上赛季 %from% 级进度已归档，等级与领取记录已重置)")
                    .replace("%season%", String.valueOf(to))
                    .replace("%from%", String.valueOf(from)));
        }
        plugin.getLogger().info("换季: " + progress.getLastName() + "(" + progress.getUuid()
                + ") 从第 " + from + " 赛季进入第 " + to + " 赛季");
    }

    public void unload(Player player) {
        PlayerProgress progress = cache.remove(player.getUniqueId());
        if (progress != null && progress.isDirty()) {
            store.save(progress);
        }
    }

    public void saveAll() {
        for (PlayerProgress progress : cache.values()) {
            store.save(progress);
        }
    }

    public Collection<PlayerProgress> cached() {
        return cache.values();
    }
}
