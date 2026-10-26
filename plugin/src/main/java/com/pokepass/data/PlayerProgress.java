package com.pokepass.data;

import com.pokepass.config.PassConfig;
import com.pokepass.quest.Quest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 一个玩家的战令进度。
 *
 * <p>周期切换是「懒重置」：不跑定时任务扫全服，而是每次访问时比对键值，
 * 对不上就把上一周期的进度清空。玩家不登录就不占资源，只有真的进来了才算。</p>
 */
public class PlayerProgress {

    private final UUID uuid;
    private String lastName = "";

    private long totalExp;
    private String dailyKey = "";
    private String weeklyKey = "";
    /** 这份进度属于第几赛季；0 表示老数据还没打过标，首次访问时补上 */
    private int seasonId;

    private final Map<String, Integer> dailyProgress = new HashMap<String, Integer>();
    private final Map<String, Integer> weeklyProgress = new HashMap<String, Integer>();

    /** 商店条目 id -> 本赛季已购买次数 */
    private final Map<String, Integer> shopPurchases = new HashMap<String, Integer>();

    private final Set<Integer> claimedFree = new HashSet<Integer>();
    private final Set<Integer> claimedPremium = new HashSet<Integer>();

    /** 自己统计的已捕捉物种（图鉴任务用它，不依赖 Pixelmon 的图鉴存储） */
    private final Set<String> caughtSpecies = new LinkedHashSet<String>();

    private boolean dirty;

    public PlayerProgress(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String name) {
        this.lastName = name == null ? "" : name;
    }

    public long getTotalExp() {
        return totalExp;
    }

    public void setTotalExp(long totalExp) {
        this.totalExp = Math.max(0, totalExp);
        this.dirty = true;
    }

    public int getLevel(PassConfig config) {
        return config.levelOf(totalExp);
    }

    public int expWithinLevel(PassConfig config) {
        return config.expWithinLevel(totalExp);
    }

    /** 加经验，返回是否升级了 */
    public boolean addExp(int amount, PassConfig config) {
        if (amount <= 0) {
            return false;
        }
        int before = config.levelOf(totalExp);
        totalExp += amount;
        dirty = true;
        return config.levelOf(totalExp) > before;
    }

    // ---------- 周期 ----------

    /** 返回 true 表示发生了重置（调用方可以顺手存库） */
    public boolean ensurePeriod(String newDailyKey, String newWeeklyKey) {
        boolean reset = false;
        if (!newDailyKey.equals(dailyKey)) {
            dailyKey = newDailyKey;
            dailyProgress.clear();
            reset = true;
        }
        if (!newWeeklyKey.equals(weeklyKey)) {
            weeklyKey = newWeeklyKey;
            weeklyProgress.clear();
            reset = true;
        }
        if (reset) {
            dirty = true;
        }
        return reset;
    }

    public String getDailyKey() {
        return dailyKey;
    }

    public String getWeeklyKey() {
        return weeklyKey;
    }

    public void setPeriodKeys(String daily, String weekly) {
        this.dailyKey = daily == null ? "" : daily;
        this.weeklyKey = weekly == null ? "" : weekly;
    }

    // ---------- 任务进度 ----------

    private Map<String, Integer> map(Quest.Period period) {
        return period == Quest.Period.DAILY ? dailyProgress : weeklyProgress;
    }

    public int progressOf(Quest quest) {
        Integer value = map(quest.getPeriod()).get(quest.getId());
        return value == null ? 0 : value;
    }

    /** 推进进度，返回推进后的值（不超过目标） */
    public int advance(Quest quest, int delta) {
        if (delta <= 0) {
            return progressOf(quest);
        }
        int current = progressOf(quest);
        if (current >= quest.getAmount()) {
            return current;
        }
        int next = Math.min(quest.getAmount(), current + delta);
        map(quest.getPeriod()).put(quest.getId(), next);
        dirty = true;
        return next;
    }

    /** 快照型任务直接写入算出来的值 */
    public void setProgress(Quest quest, int value) {
        int clamped = Math.max(0, Math.min(quest.getAmount(), value));
        Integer old = map(quest.getPeriod()).get(quest.getId());
        if (old != null && old == clamped) {
            return;
        }
        map(quest.getPeriod()).put(quest.getId(), clamped);
        dirty = true;
    }

    public boolean isComplete(Quest quest) {
        return progressOf(quest) >= quest.getAmount();
    }

    // ---------- 领取 ----------

    public boolean isClaimed(int level, boolean premiumTrack) {
        return (premiumTrack ? claimedPremium : claimedFree).contains(level);
    }

    public void markClaimed(int level, boolean premiumTrack) {
        (premiumTrack ? claimedPremium : claimedFree).add(level);
        dirty = true;
    }

    public Set<Integer> getClaimedFree() {
        return claimedFree;
    }

    public Set<Integer> getClaimedPremium() {
        return claimedPremium;
    }

    // ---------- 图鉴 ----------

    public boolean recordSpecies(String species) {
        if (species == null || species.isEmpty()) {
            return false;
        }
        if (caughtSpecies.add(species.toLowerCase())) {
            dirty = true;
            return true;
        }
        return false;
    }

    public Set<String> getCaughtSpecies() {
        return caughtSpecies;
    }

    // ---------- 脏标记 ----------

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public void touch() {
        dirty = true;
    }

    // ---------- 序列化（给存储层用） ----------

    public static String encodeProgress(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return sb.toString();
    }

    public static void decodeProgress(String raw, Map<String, Integer> target) {
        target.clear();
        if (raw == null || raw.isEmpty()) {
            return;
        }
        for (String part : raw.split("\\|")) {
            int idx = part.lastIndexOf(':');
            if (idx <= 0) {
                continue;
            }
            try {
                target.put(part.substring(0, idx), Integer.parseInt(part.substring(idx + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public static String encodeInts(Set<Integer> values) {
        StringBuilder sb = new StringBuilder();
        for (Integer value : values) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(value);
        }
        return sb.toString();
    }

    public static void decodeInts(String raw, Set<Integer> target) {
        target.clear();
        if (raw == null || raw.isEmpty()) {
            return;
        }
        for (String part : raw.split(",")) {
            try {
                target.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public static String encodeStrings(Set<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(value);
        }
        return sb.toString();
    }

    public static void decodeStrings(String raw, Set<String> target) {
        target.clear();
        if (raw == null || raw.isEmpty()) {
            return;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                target.add(trimmed);
            }
        }
    }

    public Map<String, Integer> dailyMap() {
        return dailyProgress;
    }

    public Map<String, Integer> weeklyMap() {
        return weeklyProgress;
    }

    // ---------- 赛季 ----------

    public int getSeasonId() {
        return seasonId;
    }

    public void setSeasonId(int seasonId) {
        if (this.seasonId != seasonId) {
            this.seasonId = seasonId;
            this.dirty = true;
        }
    }

    /**
     * 换季：等级、领取记录、周期进度、商店限购全部归零。
     *
     * <p>图鉴（caughtSpecies）刻意保留 —— 那是「抓过哪些物种」的事实记录，
     * 不是本赛季成绩，清掉会让图鉴类任务永久做不完。</p>
     */
    public void resetForSeason(int newSeasonId) {
        this.totalExp = 0;
        this.dailyProgress.clear();
        this.weeklyProgress.clear();
        this.claimedFree.clear();
        this.claimedPremium.clear();
        this.shopPurchases.clear();
        this.dailyKey = "";
        this.weeklyKey = "";
        this.seasonId = newSeasonId;
        this.dirty = true;
    }

    // ---------- 商店 ----------

    public int purchasesOf(String itemId) {
        Integer value = shopPurchases.get(itemId);
        return value == null ? 0 : value;
    }

    public void recordPurchase(String itemId) {
        shopPurchases.put(itemId, purchasesOf(itemId) + 1);
        dirty = true;
    }

    public Map<String, Integer> shopMap() {
        return shopPurchases;
    }
}
