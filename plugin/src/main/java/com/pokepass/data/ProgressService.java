package com.pokepass.data;

import com.pokepass.PokePassPlugin;
import com.pokepass.config.PassConfig;
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
        refreshPeriod(progress);
        return progress;
    }

    public PlayerProgress peek(UUID uuid) {
        return cache.get(uuid);
    }

    /** 按键值判断是否跨周期；跨了就清进度 */
    public void refreshPeriod(PlayerProgress progress) {
        PassConfig config = plugin.getPassConfig();
        long now = System.currentTimeMillis();
        String daily = config.dailyKey(now);
        String weekly = config.weeklyKey(now);
        if (progress.ensurePeriod(daily, weekly)) {
            progress.touch();
        }
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
