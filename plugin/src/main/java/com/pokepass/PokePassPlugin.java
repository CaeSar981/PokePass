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
import com.pokepass.shop.ShopBook;
import com.pokepass.shop.ShopService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

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
    private ShopBook shopBook;
    private ShopService shopService;

    private PixelmonListener pixelmonListener;
    private int snapshotTaskId = -1;
    private int saveTaskId = -1;

    @Override
    public void onEnable() {
        prepareDataFolder();
        saveDefaultConfig();
        for (String resource : new String[]{"message.yml", "gui.yml", "reward.yml", "shop.yml",
                "quest/daily.yml", "quest/weekly.yml"}) {
            saveResourceIfMissing(resource);
        }

        passConfig = new PassConfig(this);
        lang = new Lang(this);
        questBook = new QuestBook(this);
        rewardBook = new RewardBook(this);
        guiConfig = new GuiConfig(this);
        shopBook = new ShopBook(this);
        economy = new Economy(getLogger());

        if (!setupStorage()) {
            getLogger().severe("存储初始化失败，战令插件将被禁用");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        progressService = new ProgressService(this, store);
        questService = new QuestService(this);
        rewardService = new RewardService(this);
        shopService = new ShopService(this);

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
        hookPlaceholders();
        report();
        scheduleSelfTest(command);
    }

    /**
     * 注册 PlaceholderAPI 变量。PAPI 是软依赖：没装就跳过，
     * 装了但版本不对也只是记一条日志，绝不能因此让整个插件起不来。
     */
    private void hookPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("未安装 PlaceholderAPI，跳过变量注册");
            return;
        }
        try {
            new com.pokepass.hook.PassPlaceholders(this).register();
            // 别名：有些服主习惯按插件中文名写变量
            new com.pokepass.hook.PassPlaceholders(this, "zhanling").register();
            getLogger().info("已注册 PlaceholderAPI 变量: %pixelmontxz_*% / %zhanling_*%");
        } catch (Throwable t) {
            getLogger().warning("注册 PlaceholderAPI 变量失败: " + t);
        }
    }

    /**
     * 数据目录准备。两件事：
     *
     * <p>1. 上一版的插件名是 PokePass，改成 PixelmonTXZ 之后数据目录也跟着变了。
     * 老目录存在而新目录还没有配置，就把旧文件整体搬过来，玩家的 pass.db 不会丢。</p>
     *
     * <p>2. 原版战令也叫 PixelmonTXZ，装的还是同一个 plugins/PixelmonTXZ 目录。
     * 如果里面躺着一份不认识格式的 config.yml，先备份再让 saveDefaultConfig 生成新的，
     * 免得本插件拿着一份别人的配置一路用默认值跑，腐竹却以为配置生效了。</p>
     */
    private void prepareDataFolder() {
        File current = getDataFolder();
        File parent = current.getParentFile();
        File configFile = new File(current, "config.yml");

        File legacy = new File(parent, "PokePass");
        if (legacy.isDirectory() && !configFile.exists()) {
            int copied = copyTree(legacy, current);
            if (copied > 0) {
                getLogger().info("已从旧数据目录 plugins/PokePass 迁移 " + copied + " 个文件");
            }
        }

        if (configFile.isFile() && !looksLikeOwnConfig(configFile)) {
            File backup = new File(current, "config.original-" + System.currentTimeMillis() + ".yml");
            if (configFile.renameTo(backup)) {
                getLogger().warning("检测到非本插件的 config.yml（疑似原版战令遗留），"
                        + "已备份为 " + backup.getName() + "，接下来会生成新的默认配置");
            } else {
                getLogger().warning("发现非本插件的 config.yml，但备份失败，请手动检查 "
                        + configFile.getPath());
            }
        }
    }

    /** 认得出自己写的配置：本插件的 config.yml 头部带「精灵战令」字样 */
    private boolean looksLikeOwnConfig(File file) {
        try {
            byte[] raw = java.nio.file.Files.readAllBytes(file.toPath());
            return new String(raw, java.nio.charset.Charset.forName("UTF-8")).contains("精灵战令");
        } catch (Exception e) {
            return true;
        }
    }

    /** 递归复制，已存在的文件不覆盖（迁移只补缺） */
    private int copyTree(File from, File to) {
        File[] children = from.listFiles();
        if (children == null) {
            return 0;
        }
        if (!to.isDirectory() && !to.mkdirs()) {
            return 0;
        }
        int copied = 0;
        for (File child : children) {
            File target = new File(to, child.getName());
            if (child.isDirectory()) {
                copied += copyTree(child, target);
            } else if (!target.exists()) {
                try {
                    java.nio.file.Files.copy(child.toPath(), target.toPath());
                    copied++;
                } catch (Exception e) {
                    getLogger().warning("迁移 " + child.getName() + " 失败: " + e.getMessage());
                }
            }
        }
        return copied;
    }

    /**
     * 启动自检。必须延迟：Vault / PlayerPoints 的 hook 不是 enable 当下就完成的，
     * 立刻跑会把「还没 hook 上」误判成「没装」，白白刷一堆 FAIL。
     */
    private void scheduleSelfTest(final PassCommand command) {
        if (!getConfig().getBoolean("self-test.enabled", true)) {
            getLogger().info("启动自检已关闭（self-test.enabled: false）");
            return;
        }
        long delay = Math.max(1L, getConfig().getLong("self-test.delay-seconds", 5L) * 20L);
        getServer().getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                try {
                    command.runSelftest(getServer().getConsoleSender());
                } catch (Throwable t) {
                    getLogger().warning("启动自检执行异常: " + t);
                }
            }
        }, delay);
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
        shopBook.reload();
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
        for (String warning : shopBook.warnings()) {
            warnings.append("\n  - ").append(warning);
        }
        if (warnings.length() > 0) {
            getLogger().warning("配置有几处问题，已跳过对应条目:" + warnings);
        }
        getLogger().info("已加载任务: 每日 " + questBook.size(com.pokepass.quest.Quest.Period.DAILY)
                + " 个 / 每周 " + questBook.size(com.pokepass.quest.Quest.Period.WEEKLY)
                + " 个, 奖励等级 " + rewardBook.maxLevelWithReward()
                + ", 商店商品 " + shopBook.items().size() + " 个"
                + ", 存储 " + store.describe()
                + ", 点券 " + (economy.pointsAvailable() ? "可用" : "未装")
                + ", 经济 " + (economy.vaultAvailable() ? "可用" : "未装")
                + ", 赛季 " + (passConfig.seasonEnabled()
                        ? "第 " + passConfig.seasonId(System.currentTimeMillis())
                        + " 赛季剩 " + passConfig.seasonDaysLeft(System.currentTimeMillis()) + " 天"
                        : "关闭")
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

    public ShopBook getShopBook() {
        return shopBook;
    }

    public ShopService getShopService() {
        return shopService;
    }

    public PixelmonListener getPixelmonListener() {
        return pixelmonListener;
    }
}
