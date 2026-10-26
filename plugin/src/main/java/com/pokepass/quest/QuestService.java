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
import java.util.List;

/**
 * 任务推进与结算。
 *
 * <p>所有事件监听器最终都只调 {@link #advance}：传进来「什么类型、对象是谁、加多少」，
 * 它负责找出这个玩家当前可见的任务、过滤条件、推进进度、判完成、发经验。
 * 这样新增一种任务只需要加个监听器，不用改这里。</p>
 */
public class QuestService {

    private final PokePassPlugin plugin;

    public QuestService(PokePassPlugin plugin) {
        this.plugin = plugin;
    }

    /** 当前玩家本周期可见的全部任务（每日 + 每周） */
    public List<Quest> visible(Player player, PlayerProgress progress) {
        List<Quest> out = new ArrayList<Quest>();
        out.addAll(plugin.getQuestBook().forPlayer(Quest.Period.DAILY, player.getUniqueId(),
                progress.getDailyKey(), plugin.getPassConfig().dailyCount()));
        out.addAll(plugin.getQuestBook().forPlayer(Quest.Period.WEEKLY, player.getUniqueId(),
                progress.getWeeklyKey(), plugin.getPassConfig().weeklyCount()));
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
        boolean levelUp = progress.addExp(quest.getExp(), plugin.getPassConfig());
        player.sendMessage(plugin.getLang().msg("quest.complete", "&a任务完成: &f%quest% &7(+%exp% 经验)")
                .replace("%quest%", quest.getName())
                .replace("%exp%", String.valueOf(quest.getExp())));
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
