package com.pokepass.quest;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import com.pokepass.PokePassPlugin;
import com.pokepass.data.PlayerProgress;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 任务推进与结算。
 *
 * <p>所有事件监听器最终都只调 {@link #advance}：传进来「什么类型、对象是谁、加多少」，
 * 它负责找出这个玩家当前可见的任务、过滤条件、推进进度、判完成、发经验。
 * 这样新增一种任务只需要加个监听器，不用改这里。</p>
 */
public class QuestService {

    /**
     * 「稀有事件」白名单：一次动作耗时很长、单次价值高，同一次动作被重复投递等于白送进度。
     *
     * <p>刻意不含 BLOCK_BREAK / CHAT / CRAFT 这类高频操作 —— 玩家连挖连点本来就该连算，
     * 给它们加时间窗只会吞掉正常进度。</p>
     */
    private static final Set<QuestType> DEDUPE = EnumSet.of(
            QuestType.CAPTURE, QuestType.BATTLE_WIN, QuestType.EVOLVE,
            QuestType.EGG_HATCH, QuestType.TRADE, QuestType.RECEIVED_FOSSIL);

    private final PokePassPlugin plugin;
    /** 玩家 -> 各类型最后一次记账的时间戳（毫秒） */
    private final Map<UUID, Map<QuestType, Long>> lastAdvance =
            new HashMap<UUID, Map<QuestType, Long>>();
    /** 去重窗口；0 表示关闭 */
    private long dedupeWindowMs;
    /** 全图鉴物种数缓存（0 = 还没算过） */
    private int totalSpecies;

    public QuestService(PokePassPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** 重载配置时更新去重窗口（/zl reload 会调） */
    public void reload() {
        this.dedupeWindowMs = Math.max(0L,
                plugin.getConfig().getLong("anti-abuse.dedupe-window-ms", 250L));
    }

    /** 去重窗口毫秒数，0 表示关闭 */
    public long dedupeWindowMs() {
        return dedupeWindowMs;
    }

    /** 玩家退出时调，避免内存里攒着一堆离线 UUID */
    public void forget(UUID uuid) {
        lastAdvance.remove(uuid);
    }

    /**
     * 同一玩家在窗口内重复触发同一稀有事件 -> 判定为重复投递，丢弃。
     * 被丢弃的那次不刷新时间戳，否则重复投递能一直把窗口往后推。
     */
    private boolean isDuplicate(UUID uuid, QuestType type) {
        long now = System.currentTimeMillis();
        Map<QuestType, Long> map = lastAdvance.get(uuid);
        if (map == null) {
            map = new HashMap<QuestType, Long>();
            lastAdvance.put(uuid, map);
        }
        Long last = map.get(type);
        if (last != null && now - last < dedupeWindowMs) {
            return true;
        }
        map.put(type, now);
        return false;
    }

    /**
     * 当前玩家能看到的周任务页：免费页 + 自己用点券买过的页。
     *
     * <p>页面集合只在这里算一次，任务抽样、界面渲染、进度推进都用同一份口径，
     * 免得出现「界面上看到但事件推不动」这类不一致。</p>
     */
    public Set<Integer> unlockedPages(PlayerProgress progress) {
        int total = plugin.getQuestBook().pages(Quest.Period.WEEKLY);
        // 「免费页」有两个来源：配置里白送的 free-pages，以及按在服天数攒出来的页。
        // 两者取并集，玩家买过的页再叠上去，这样口径只有一处，界面和事件推进不会打架。
        int freeThrough = plugin.getPassConfig().weeklyFreePages() + pagesByDays(progress);
        Set<Integer> unlocked = new HashSet<Integer>();
        for (int page = 1; page <= total; page++) {
            if (plugin.getPassConfig().isPageFree(page)
                    || page <= freeThrough
                    || progress.isPageUnlocked(page)) {
                unlocked.add(page);
            }
        }
        return unlocked;
    }

    /**
     * 靠「在服资历」白送的页数（原版 {@code Day: 7} 的语义：每满 N 天自动多开一页）。
     *
     * <p>算的是首次登录到现在的累计天数，不随赛季重置；{@code unlock-days: 0} 关闭。</p>
     */
    public int pagesByDays(PlayerProgress progress) {
        if (!plugin.getPassConfig().weeklyPagesEnabled()) {
            return 0;
        }
        int days = plugin.getPassConfig().weeklyUnlockDays();
        if (days <= 0) {
            return 0;
        }
        long first = progress.getFirstJoin();
        if (first <= 0) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - first;
        if (elapsed <= 0) {
            return 0;
        }
        return (int) (elapsed / (days * 86400000L));
    }

    /**
     * 还有几天会靠资历自动开出下一页；-1 表示不会（功能关闭、已无待解锁页、或首登时间未知）。
     *
     * <p>只给玩家提示用，不参与解锁判定。</p>
     */
    public int daysToNextFreePage(PlayerProgress progress) {
        if (!plugin.getPassConfig().weeklyPagesEnabled() || plugin.getPassConfig().weeklyUnlockDays() <= 0) {
            return -1;
        }
        int total = plugin.getQuestBook().pages(Quest.Period.WEEKLY);
        int unlockDays = plugin.getPassConfig().weeklyUnlockDays();
        int granted = plugin.getPassConfig().weeklyFreePages() + pagesByDays(progress);
        if (granted >= total || progress.getFirstJoin() <= 0) {
            return -1;
        }
        long nextAt = progress.getFirstJoin() + (long) (granted - plugin.getPassConfig().weeklyFreePages() + 1)
                * unlockDays * 86400000L;
        long remain = nextAt - System.currentTimeMillis();
        if (remain <= 0) {
            return 0;
        }
        return (int) Math.ceil(remain / 86400000.0D);
    }

    /**
     * 下一张待解锁的页；没有可解锁的就返回 -1。
     *
     * <p>只返回「紧挨着的下一页」——跳过中间页会让「解锁了第 3 页但第 2 页是空的」
     * 这种配置错误变成玩家投诉。</p>
     */
    public int nextLockedPage(PlayerProgress progress) {
        int total = plugin.getQuestBook().pages(Quest.Period.WEEKLY);
        if (!plugin.getPassConfig().weeklyPagesEnabled()) {
            return -1;
        }
        Set<Integer> unlocked = unlockedPages(progress);
        for (int page = 1; page <= total; page++) {
            if (!unlocked.contains(page)) {
                return page;
            }
        }
        return -1;
    }

    /** 某一页里有多少任务（界面提示用） */
    public int pageSize(int page) {
        int count = 0;
        for (Quest quest : plugin.getQuestBook().all(Quest.Period.WEEKLY)) {
            if (quest.getPage() == page) {
                count++;
            }
        }
        return count;
    }

    /** 当前玩家本周期可见的全部任务（每日 + 每周） */
    public List<Quest> visible(Player player, PlayerProgress progress) {
        List<Quest> out = new ArrayList<Quest>();
        out.addAll(plugin.getQuestBook().forPlayer(Quest.Period.DAILY, player.getUniqueId(),
                progress.getDailyKey(), plugin.getPassConfig().dailyCount()));
        out.addAll(plugin.getQuestBook().forPlayer(Quest.Period.WEEKLY, player.getUniqueId(),
                progress.getWeeklyKey(), plugin.getPassConfig().weeklyCount(), unlockedPages(progress)));
        return out;
    }

    /** 已解锁但一条任务都没有的页（配置写漏时给自检用） */
    public List<Integer> emptyUnlockedPages(PlayerProgress progress) {
        List<Integer> out = new ArrayList<Integer>();
        for (int page : unlockedPages(progress)) {
            if (pageSize(page) == 0) {
                out.add(page);
            }
        }
        Collections.sort(out);
        return out;
    }

    /**
     * 推进某类任务。
     *
     * @param target 事件对象（物种名、物品名、NPC 名、聊天内容……），条件为 * 时无所谓
     * @param delta  推进量；次数类任务传 1，累计类任务传实际数值
     * @return 是否有任务因此推进
     */
    public boolean advance(Player player, QuestType type, String target, int delta) {
        if (player == null || delta <= 0) {
            return false;
        }
        if (dedupeWindowMs > 0 && DEDUPE.contains(type)
                && isDuplicate(player.getUniqueId(), type)) {
            return false;
        }
        PlayerProgress progress = plugin.getProgressService().get(player);
        boolean changed = false;
        for (Quest quest : visible(player, progress)) {
            if (quest.getType() != type || quest.getType().isCheck()) {
                continue;
            }
            if (progress.isComplete(quest)) {
                continue;
            }
            if (!quest.matches(target)) {
                continue;
            }
            int before = progress.progressOf(quest);
            int after = progress.advance(quest, delta);
            if (after == before) {
                continue;
            }
            changed = true;
            if (after >= quest.getAmount()) {
                complete(player, progress, quest);
            }
        }
        return changed;
    }

    private void complete(Player player, PlayerProgress progress, Quest quest) {
        int beforeLevel = progress.getLevel(plugin.getPassConfig());
        // 高级会员经验倍率（原版 Double）。倍率只影响「做任务拿到的经验」，
        // /zl give 是管理员调数值，不乘，否则补一次经验还要先算半天。
        double multiplier = plugin.getRewardService().hasPremium(player, progress)
                ? plugin.getPassConfig().premiumExpMultiplier() : 1.0D;
        int gained = (int) Math.round(quest.getExp() * multiplier);
        boolean levelUp = progress.addExp(gained, plugin.getPassConfig());
        player.sendMessage(plugin.getLang().msg("quest.complete", "&a任务完成: &f%quest% &7(+%exp% 经验)")
                .replace("%quest%", quest.getName())
                .replace("%exp%", String.valueOf(gained)));
        if (levelUp) {
            int level = progress.getLevel(plugin.getPassConfig());
            player.sendMessage(plugin.getLang().msg("level-up", "&e战令等级提升! 当前 &f%level% &e级")
                    .replace("%level%", String.valueOf(level)));
        }
        int afterLevel = progress.getLevel(plugin.getPassConfig());
        if (afterLevel > beforeLevel) {
            plugin.getRewardService().notifyUnclaimed(player, progress);
        }
        progress.touch();
    }

    /**
     * 快照型任务：直接从玩家当前状态算出数值并写入。
     * 由定时任务每隔 {@code quest.check-interval-ticks} 调一次。
     */
    public void refreshSnapshots(Player player) {
        PlayerProgress progress = plugin.getProgressService().get(player);
        for (Quest quest : visible(player, progress)) {
            if (!quest.getType().isCheck()) {
                continue;
            }
            int value = snapshotValue(player, quest);
            if (value < 0) {
                continue;
            }
            boolean wasComplete = progress.isComplete(quest);
            progress.setProgress(quest, value);
            if (!wasComplete && progress.isComplete(quest)) {
                complete(player, progress, quest);
            }
        }
    }

    /** 算快照值；返回 -1 表示这条任务暂时算不出来（跳过） */
    private int snapshotValue(Player player, Quest quest) {
        switch (quest.getType()) {
            case MAX_LEVEL_POKEMON:
                return countMaxLevel(player, quest);
            case POKEDEX:
                return progressCaught(player);
            case CHECK_ITEM:
                return countItem(player, quest);
            case PLAYER_LEVEL:
                // 人物等级直接读，不做任何加成
                return Math.max(0, player.getLevel());
            case POKEDEX_PERCENT:
                return pokedexPercent(player);
            default:
                return -1;
        }
    }

    private int countMaxLevel(Player player, Quest quest) {
        int target = 100;
        if (!quest.isWildcard()) {
            try {
                target = Integer.parseInt(quest.getConditions().get(0).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        int count = 0;
        try {
            PlayerPartyStorage party = Pixelmon.storageManager.getParty(player.getUniqueId());
            if (party == null) {
                return 0;
            }
            for (int i = 0; i < 6; i++) {
                Pokemon pokemon = party.get(i);
                if (pokemon != null && pokemon.getLevel() >= target) {
                    count++;
                }
            }
        } catch (Throwable ignored) {
        }
        return count;
    }

    private int progressCaught(Player player) {
        PlayerProgress progress = plugin.getProgressService().get(player);
        return progress.getCaughtSpecies().size();
    }

    /** 图鉴完成度百分比（0~100），分母只算图鉴号 > 0 的正常物种 */
    private int pokedexPercent(Player player) {
        int total = totalSpecies();
        if (total <= 0) {
            return 0;
        }
        int caught = Math.min(progressCaught(player), total);
        return caught * 100 / total;
    }

    /**
     * 全图鉴物种数。
     *
     * <p>EnumSpecies 里除了正常物种还有 MissingNo / Egg 这些占位项，
     * 直接用 values().length 会让分母虚高、百分比永远到不了 100，
     * 所以只数图鉴号大于 0 的。第一次算完缓存，枚举不会变。</p>
     */
    private int totalSpecies() {
        if (totalSpecies > 0) {
            return totalSpecies;
        }
        try {
            int count = 0;
            for (com.pixelmonmod.pixelmon.enums.EnumSpecies species
                    : com.pixelmonmod.pixelmon.enums.EnumSpecies.values()) {
                if (species.getNationalPokedexInteger() > 0) {
                    count++;
                }
            }
            totalSpecies = count;
        } catch (Throwable ignored) {
            totalSpecies = 0;
        }
        return totalSpecies;
    }

    private int countItem(Player player, Quest quest) {
        if (quest.isWildcard()) {
            return 0;
        }
        String name = quest.getConditions().get(0).trim();
        Material material;
        try {
            material = Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }
}
