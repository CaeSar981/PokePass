package com.pokepass;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pokepass.command.PassCommand;
import com.pokepass.config.PassConfig;
import com.pokepass.config.QuestBook;
import com.pokepass.config.RewardBook;
import com.pokepass.data.PlayerProgress;
import com.pokepass.data.ProgressService;
import com.pokepass.data.ProgressStore;
import com.pokepass.data.SqliteProgressStore;
import com.pokepass.data.YamlProgressStore;
import com.pokepass.gui.GuiConfig;
import com.pokepass.gui.MenuListener;
import com.pokepass.listener.BukkitListener;
import com.pokepass.listener.PixelmonListener;
import com.pokepass.quest.QuestService;
import com.pokepass.reward.Economy;
import com.pokepass.reward.RewardService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PokePass —— 精灵战令。
 *
 * <p>每日/每周任务 + 战令等级 + 免费/高级双轨奖励。任务类型覆盖 Pixelmon
 * （捕捉、对战、进化、孵化、经验、努力值、交易）与原版（挖矿、合成、钓鱼、
 * 拾取、附魔、聊天、NPC 交互、持有物品、图鉴、满级精灵数）。</p>
 */
public class PokePassPlugin extends JavaPlugin {

    private PassConfig passConfig;
    private Lang lang;
    private QuestBook questBook;
    private RewardBook rewardBook;
    private GuiConfig guiConfig;
    private Economy economy;

    private ProgressStore store;
    private ProgressService progressService;
    private QuestService questService;
    private RewardService rewardService;

    private PixelmonListener pixelmonListener;
    private int snapshotTaskId = -1;
    private int saveTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String resource : new String[]{"message.yml", "gui.yml", "reward.yml",
                "quest/daily.yml", "quest/weekly.yml"}) {
            saveResourceIfMissing(resource);
        }

        passConfig = new PassConfig(this);
        lang = new Lang(this);
        questBook = new QuestBook(this);
        rewardBook = new RewardBook(this);
        guiConfig = new GuiConfig(this);
        economy = new Economy(getLogger());

        if (!setupStorage()) {
            getLogger().severe("存储初始化失败，战令插件将被禁用");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        progressService = new ProgressService(this, store);
        questService = new QuestService(this);
        rewardService = new RewardService(this);

        pixelmonListener = new PixelmonListener(this);
        try {
            Pixelmon.EVENT_BUS.register(pixelmonListener);
        } catch (Throwable t) {
            getLogger().severe("注册 Pixelmon 事件失败，精灵相关任务将不可用: " + t);
        }
        getServer().getPluginManager().registerEvents(new BukkitListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        PassCommand command = new PassCommand(this);
        if (getCommand("zl") != null) {
            getCommand("zl").setExecutor(command);
            getCommand("zl").setTabCompleter(command);
        }

        startTasks();
        report();
    }

    @Override
    public void onDisable() {
        if (getServer().getScheduler() != null) {
            if (snapshotTaskId != -1) {
                getServer().getScheduler().cancelTask(snapshotTaskId);
            }
            if (saveTaskId != -1) {
                getServer().getScheduler().cancelTask(saveTaskId);
            }
        }
        if (pixelmonListener != null) {
            try {
                Pixelmon.EVENT_BUS.unregister(pixelmonListener);
            } catch (Throwable ignored) {
            }
        }
        if (progressService != null && store != null) {
            progressService.saveAll();
        }
        if (store != null) {
            store.close();
        }
        getLogger().info("PokePass 已关闭");
    }

    /** 重载全部配置（不动玩家数据） */
    public void reloadAll() {
        reloadConfig();
        passConfig = new PassConfig(this);
        lang.reload();
        questBook.reload();
        rewardBook.reload();
        guiConfig.reload();
        questService.reload();
        economy = new Economy(getLogger());
        report();
    }

    private void saveResourceIfMissing(String path) {
        try {
            if (!new java.io.File(getDataFolder(), path).exists()) {
                saveResource(path, false);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private boolean setupStorage() {
        try {
            if ("yaml".equals(passConfig.storageType())) {
                store = new YamlProgressStore(this, getLogger());
                return true;
            }
            store = new SqliteProgressStore(getDataFolder(), passConfig.storageFile(), getLogger());
            return true;
        } catch (Throwable t) {
            getLogger().severe("SQLite 初始化失败(" + t + ")，尝试退回 YAML 存储");
            try {
                store = new YamlProgressStore(this, getLogger());
                return true;
            } catch (Throwable inner) {
                getLogger().severe("YAML 存储也失败了: " + inner);
                return false;
            }
        }
    }

    private void startTasks() {
        snapshotTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        questService.refreshSnapshots(player);
                    } catch (Throwable t) {
                        getLogger().warning("刷新快照任务失败(" + player.getName() + "): " + t.getMessage());
                    }
                }
            }
        }, 100L, passConfig.checkIntervalTicks());

        saveTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                for (PlayerProgress progress : progressService.cached()) {
                    if (progress.isDirty()) {
                        store.save(progress);
                    }
                }
            }
        }, 1200L, 6000L);
    }

    private void report() {
        StringBuilder warnings = new StringBuilder();
        for (String warning : questBook.warnings()) {
            warnings.append("\n  - ").append(warning);
        }
        for (String warning : rewardBook.warnings()) {
            warnings.append("\n  - ").append(warning);
        }
        if (warnings.length() > 0) {
            getLogger().warning("配置有几处问题，已跳过对应条目:" + warnings);
        }
        getLogger().info("已加载任务: 每日 " + questBook.size(com.pokepass.quest.Quest.Period.DAILY)
                + " 个 / 每周 " + questBook.size(com.pokepass.quest.Quest.Period.WEEKLY)
                + " 个, 奖励等级 " + rewardBook.maxLevelWithReward()
                + ", 存储 " + store.describe()
                + ", 点券 " + (economy.pointsAvailable() ? "可用" : "未装")
                + ", 经济 " + (economy.vaultAvailable() ? "可用" : "未装")
                + ", 防刷 " + (questService.dedupeWindowMs() > 0
                        ? questService.dedupeWindowMs() + "ms" : "关闭"));
    }

    public PassConfig getPassConfig() {
        return passConfig;
    }

    public Lang getLang() {
        return lang;
    }

    public QuestBook getQuestBook() {
        return questBook;
    }

    public RewardBook getRewardBook() {
        return rewardBook;
    }

    public GuiConfig getGuiConfig() {
        return guiConfig;
    }

    public Economy getEconomy() {
        return economy;
    }

    public ProgressService getProgressService() {
        return progressService;
    }

    public QuestService getQuestService() {
        return questService;
    }

    public RewardService getRewardService() {
        return rewardService;
    }

    public PixelmonListener getPixelmonListener() {
        return pixelmonListener;
    }
}
