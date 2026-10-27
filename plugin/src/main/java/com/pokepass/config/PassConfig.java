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
    private final double premiumExpMultiplier;

    private final int dailyResetHour;
    private final int dailyCount;
    private final int weeklyCount;
    private final String weeklyResetDay;
    private final boolean weeklyPagesEnabled;
    private final int weeklyFreePages;
    private final int weeklyPageCost;
    /** 每累计多少天自动多解锁一页（原版 Day）；0 = 关闭 */
    private final int weeklyUnlockDays;

    private final boolean retroClaim;
    private final int checkIntervalTicks;

    private final boolean seasonEnabled;
    private final int seasonLengthDays;
    private final long seasonStartMillis;

    public PassConfig(JavaPlugin plugin) {
        FileConfiguration c = plugin.getConfig();
        this.storageType = c.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
        this.storageFile = c.getString("storage.file", "pass.db");

        this.maxLevel = Math.max(1, c.getInt("pass.max-level", 50));
        this.expBase = Math.max(1, c.getInt("pass.exp.base", 1000));
        this.expStep = Math.max(0, c.getInt("pass.exp.step", 250));

        this.premiumEnabled = c.getBoolean("pass.premium.enabled", true);
        this.premiumPermission = c.getString("pass.premium.permission", "pokepass.premium");
        this.premiumExpMultiplier = clampDouble(
                c.getDouble("pass.premium.exp-multiplier", 2.0D), 1.0D, 100.0D);

        this.dailyResetHour = clamp(c.getInt("daily.reset-hour", 0), 0, 23);
        this.dailyCount = Math.max(0, c.getInt("daily.count", 3));

        this.weeklyResetDay = c.getString("weekly.reset-day", "MONDAY").toUpperCase(Locale.ROOT);
        this.weeklyCount = Math.max(0, c.getInt("weekly.count", 0));

        this.weeklyPagesEnabled = c.getBoolean("weekly.pages.enabled", true);
        this.weeklyFreePages = Math.max(1, c.getInt("weekly.pages.free-pages", 1));
        this.weeklyPageCost = Math.max(0, c.getInt("weekly.pages.cost-points", 10));
        this.weeklyUnlockDays = Math.max(0, c.getInt("weekly.pages.unlock-days", 7));

        this.retroClaim = c.getBoolean("reward.retro-claim", true);
        this.checkIntervalTicks = Math.max(20, c.getInt("quest.check-interval-ticks", 100));

        this.seasonEnabled = c.getBoolean("season.enabled", true);
        this.seasonLengthDays = Math.max(1, c.getInt("season.length-days", 60));
        this.seasonStartMillis = parseDate(c.getString("season.start-date", ""), c);
    }

    /** 日期只认 yyyy-MM-dd；写错就按今天算，别因为一行配置把赛季整个算飞 */
    private long parseDate(String text, FileConfiguration c) {
        if (text != null && !text.trim().isEmpty()) {
            try {
                java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
                format.setLenient(false);
                return format.parse(text.trim()).getTime();
            } catch (java.text.ParseException e) {
                c.set("season.start-date", new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                        .format(new Date()));
            }
        }
        // 没有起始日就用今天零点作为第 1 赛季起点，并写回内存里的配置
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static double clampDouble(double value, double min, double max) {
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

    /** 周任务分页解锁是否开启；关掉等于所有页都免费 */
    public boolean weeklyPagesEnabled() {
        return weeklyPagesEnabled;
    }

    /** 免费开放的页数（默认 1，即第 1 页不用买） */
    public int weeklyFreePages() {
        return weeklyFreePages;
    }

    /** 解锁一页要多少点券 */
    public int weeklyPageCost() {
        return weeklyPageCost;
    }

    /** 每累计多少天自动多解锁一页周任务；0 = 这个功能关掉 */
    public int weeklyUnlockDays() {
        return weeklyUnlockDays;
    }

    /** 高级会员的经验倍率（原版 Double）。1 = 不翻倍 */
    public double premiumExpMultiplier() {
        return premiumExpMultiplier;
    }

    /** 这一页是否本来就免费（含 pages 整体关掉的情况） */
    public boolean isPageFree(int page) {
        return !weeklyPagesEnabled || page <= weeklyFreePages;
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

    // ---------- 赛季 ----------

    public boolean seasonEnabled() {
        return seasonEnabled;
    }

    public int seasonLengthDays() {
        return seasonLengthDays;
    }

    /** 当前是第几赛季，1 起 */
    public int seasonId(long now) {
        if (!seasonEnabled) {
            return 1;
        }
        long perSeason = seasonLengthDays * 86400000L;
        long elapsed = now - seasonStartMillis;
        if (elapsed < 0) {
            return 1;
        }
        return (int) (elapsed / perSeason) + 1;
    }

    /** 本赛季结束的时刻（毫秒） */
    public long seasonEndMillis(long now) {
        if (!seasonEnabled) {
            return Long.MAX_VALUE;
        }
        long perSeason = seasonLengthDays * 86400000L;
        return seasonStartMillis + seasonId(now) * perSeason;
    }

    /** 距换季还剩几天（向上取整，今天结束就是 0） */
    public int seasonDaysLeft(long now) {
        if (!seasonEnabled) {
            return -1;
        }
        long remain = seasonEndMillis(now) - now;
        if (remain <= 0) {
            return 0;
        }
        return (int) Math.ceil(remain / 86400000.0D);
    }
}
