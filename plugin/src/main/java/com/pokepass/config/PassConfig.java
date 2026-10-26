package com.pokepass.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** config.yml 的强类型视图。所有数值都在这里兜底，业务代码不直接读 config。 */
public class PassConfig {

    private final String storageType;
    private final String storageFile;

    private final int maxLevel;
    private final int expBase;
    private final int expStep;

    private final boolean premiumEnabled;
    private final String premiumPermission;

    private final int dailyResetHour;
    private final int dailyCount;
    private final int weeklyCount;
    private final String weeklyResetDay;

    private final boolean retroClaim;
    private final int checkIntervalTicks;

    public PassConfig(JavaPlugin plugin) {
        FileConfiguration c = plugin.getConfig();
        this.storageType = c.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
        this.storageFile = c.getString("storage.file", "pass.db");

        this.maxLevel = Math.max(1, c.getInt("pass.max-level", 50));
        this.expBase = Math.max(1, c.getInt("pass.exp.base", 1000));
        this.expStep = Math.max(0, c.getInt("pass.exp.step", 250));

        this.premiumEnabled = c.getBoolean("pass.premium.enabled", true);
        this.premiumPermission = c.getString("pass.premium.permission", "pokepass.premium");

        this.dailyResetHour = clamp(c.getInt("daily.reset-hour", 0), 0, 23);
        this.dailyCount = Math.max(0, c.getInt("daily.count", 3));

        this.weeklyResetDay = c.getString("weekly.reset-day", "MONDAY").toUpperCase(Locale.ROOT);
        this.weeklyCount = Math.max(0, c.getInt("weekly.count", 0));

        this.retroClaim = c.getBoolean("reward.retro-claim", true);
        this.checkIntervalTicks = Math.max(20, c.getInt("quest.check-interval-ticks", 100));
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public String storageType() {
        return storageType;
    }

    public String storageFile() {
        return storageFile;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public boolean premiumEnabled() {
        return premiumEnabled;
    }

    public String premiumPermission() {
        return premiumPermission;
    }

    public int dailyCount() {
        return dailyCount;
    }

    public int weeklyCount() {
        return weeklyCount;
    }

    public int dailyResetHour() {
        return dailyResetHour;
    }

    public String weeklyResetDay() {
        return weeklyResetDay;
    }

    public boolean retroClaim() {
        return retroClaim;
    }

    public int checkIntervalTicks() {
        return checkIntervalTicks;
    }

    /** 从 level 升到 level+1 需要多少经验 */
    public int expForLevel(int level) {
        if (level >= maxLevel) {
            return Integer.MAX_VALUE;
        }
        return expBase + Math.max(0, level - 1) * expStep;
    }

    /** 当前累计经验能到达的等级（1 起） */
    public int levelOf(long totalExp) {
        int level = 1;
        long remain = totalExp;
        while (level < maxLevel) {
            int need = expForLevel(level);
            if (remain < need) {
                break;
            }
            remain -= need;
            level++;
        }
        return level;
    }

    /** 当前等级内已累积的经验 */
    public int expWithinLevel(long totalExp) {
        long remain = totalExp;
        int level = 1;
        while (level < maxLevel) {
            int need = expForLevel(level);
            if (remain < need) {
                return (int) remain;
            }
            remain -= need;
            level++;
        }
        return 0;
    }

    // ---------- 周期键 ----------

    /** 每日键：按 reset-hour 切日，所以 0 点重置和 5 点重置都能表达 */
    public String dailyKey(long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        if (cal.get(Calendar.HOUR_OF_DAY) < dailyResetHour) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(cal.getTimeInMillis()));
    }

    /** 每周键：yyyy-Www，周起点按 reset-day 之前那天算 */
    public String weeklyKey(long now) {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.setMinimalDaysInFirstWeek(4);
        cal.setTimeInMillis(now);
        return new SimpleDateFormat("yyyy-'W'ww", Locale.ROOT).format(cal.getTime());
    }
}
