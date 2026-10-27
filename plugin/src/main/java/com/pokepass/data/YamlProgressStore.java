package com.pokepass.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * YAML 存储，每个玩家一个文件（players/&lt;uuid&gt;.yml）。
 *
 * <p>只适合单服小规模：没有文件锁，两台服写同一份目录会互相覆盖。
 * SQLite 起不来的时候可以拿它顶一下。</p>
 */
public class YamlProgressStore implements ProgressStore {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final File dir;

    public YamlProgressStore(JavaPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.dir = new File(plugin.getDataFolder(), "players");
        if (!dir.exists() && !dir.mkdirs()) {
            logger.warning("无法创建 players 目录，YAML 存储可能失败");
        }
    }

    private File fileOf(UUID uuid) {
        return new File(dir, uuid.toString() + ".yml");
    }

    @Override
    public PlayerProgress load(UUID uuid) {
        PlayerProgress progress = new PlayerProgress(uuid);
        File file = fileOf(uuid);
        if (!file.exists()) {
            return progress;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        progress.setLastName(cfg.getString("name", ""));
        progress.setTotalExp(cfg.getLong("total-exp", 0));
        progress.setPeriodKeys(cfg.getString("daily-key", ""), cfg.getString("weekly-key", ""));
        PlayerProgress.decodeProgress(cfg.getString("daily-progress", ""), progress.dailyMap());
        PlayerProgress.decodeProgress(cfg.getString("weekly-progress", ""), progress.weeklyMap());
        PlayerProgress.decodeInts(cfg.getString("claimed-free", ""), progress.getClaimedFree());
        PlayerProgress.decodeInts(cfg.getString("claimed-premium", ""), progress.getClaimedPremium());
        PlayerProgress.decodeStrings(cfg.getString("caught", ""), progress.getCaughtSpecies());
        progress.setSeasonId(cfg.getInt("season", 0));
        PlayerProgress.decodeProgress(cfg.getString("shop", ""), progress.shopMap());
        PlayerProgress.decodeInts(cfg.getString("pages", ""), progress.getWeeklyPages());
        progress.setFirstJoin(cfg.getLong("first-join", 0));
        progress.setPremiumFlag(cfg.getBoolean("premium", false));
        progress.clearDirty();
        return progress;
    }

    @Override
    public void save(PlayerProgress progress) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("name", progress.getLastName());
        cfg.set("total-exp", progress.getTotalExp());
        cfg.set("daily-key", progress.getDailyKey());
        cfg.set("weekly-key", progress.getWeeklyKey());
        cfg.set("daily-progress", PlayerProgress.encodeProgress(progress.dailyMap()));
        cfg.set("weekly-progress", PlayerProgress.encodeProgress(progress.weeklyMap()));
        cfg.set("claimed-free", PlayerProgress.encodeInts(progress.getClaimedFree()));
        cfg.set("claimed-premium", PlayerProgress.encodeInts(progress.getClaimedPremium()));
        cfg.set("caught", PlayerProgress.encodeStrings(progress.getCaughtSpecies()));
        cfg.set("season", progress.getSeasonId());
        cfg.set("shop", PlayerProgress.encodeProgress(progress.shopMap()));
        cfg.set("pages", PlayerProgress.encodeInts(progress.getWeeklyPages()));
        cfg.set("first-join", progress.getFirstJoin());
        cfg.set("premium", progress.isPremiumFlag());
        try {
            cfg.save(fileOf(progress.getUuid()));
            progress.clearDirty();
        } catch (Exception e) {
            logger.warning("保存战令进度失败(" + progress.getUuid() + "): " + e.getMessage());
        }
    }

    @Override
    public void saveAll(Collection<PlayerProgress> progresses) {
        for (PlayerProgress progress : progresses) {
            save(progress);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public String describe() {
        return "YAML (players/*.yml)";
    }
}
