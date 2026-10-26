package com.pokepass.command;

import com.pokepass.PokePassPlugin;
import com.pokepass.data.PlayerProgress;
import com.pokepass.gui.PassMenus;
import com.pokepass.quest.Quest;
import com.pokepass.quest.QuestType;
import com.pokepass.reward.RewardEntry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /zl 命令。 */
public class PassCommand implements CommandExecutor, TabCompleter {

    private final PokePassPlugin plugin;

    public PassCommand(PokePassPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getLang().msg("player-only", "&c这个命令只能玩家用"));
                return true;
            }
            new PassMenus.MainMenu(plugin, (Player) sender).open();
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("help".equals(sub)) {
            help(sender);
            return true;
        }
        if ("reload".equals(sub)) {
            if (!sender.hasPermission("pokepass.admin")) {
                sender.sendMessage(plugin.getLang().msg("no-permission", "&c你没有权限"));
                return true;
            }
            plugin.reloadAll();
            sender.sendMessage(plugin.getLang().msg("reload", "&a配置已重载"));
            return true;
        }
        if ("give".equals(sub)) {
            return adminGive(sender, args);
        }
        if ("reset".equals(sub)) {
            return adminReset(sender, args);
        }
        if ("selftest".equals(sub)) {
            if (!sender.hasPermission("pokepass.admin")) {
                sender.sendMessage(plugin.getLang().msg("no-permission", "&c你没有权限"));
                return true;
            }
            selftest(sender);
            return true;
        }
        if ("progress".equals(sub)) {
            return addProgress(sender, args);
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLang().msg("player-only", "&c这个命令只能玩家用"));
            return true;
        }
        Player player = (Player) sender;
        if ("daily".equals(sub)) {
            new PassMenus.QuestMenu(plugin, player, Quest.Period.DAILY).open();
        } else if ("weekly".equals(sub)) {
            new PassMenus.QuestMenu(plugin, player, Quest.Period.WEEKLY).open();
        } else if ("reward".equals(sub) || "rewards".equals(sub)) {
            new PassMenus.RewardMenu(plugin, player).open();
        } else if ("shop".equals(sub) || "store".equals(sub)) {
            if (plugin.getShopBook().items().isEmpty()) {
                player.sendMessage(plugin.getLang().msg("shop.empty", "&7商店还没有配置商品"));
                return true;
            }
            new PassMenus.ShopMenu(plugin, player).open();
        } else if ("claim".equals(sub)) {
            int count = plugin.getRewardService().claimAll(player);
            player.sendMessage(count > 0
                    ? plugin.getLang().msg("reward.claim-all", "&a已领取 &f%count% &a份奖励")
                    .replace("%count%", String.valueOf(count))
                    : plugin.getLang().msg("reward.nothing", "&7没有可以领取的奖励"));
        } else if ("info".equals(sub) || "me".equals(sub)) {
            PlayerProgress progress = plugin.getProgressService().get(player);
            int level = progress.getLevel(plugin.getPassConfig());
            player.sendMessage(plugin.getLang().msg("info",
                    "&7战令等级 &f%level% &7| 总经验 &f%exp% &7| 可领奖励 &f%claim%")
                    .replace("%level%", String.valueOf(level))
                    .replace("%exp%", String.valueOf(progress.getTotalExp()))
                    .replace("%claim%", String.valueOf(plugin.getRewardService().claimable(player))));
            if (plugin.getPassConfig().seasonEnabled()) {
                long now = System.currentTimeMillis();
                player.sendMessage(plugin.getLang().msg("info-season",
                                "&7第 &f%season% &7赛季 &8| &7还剩 &f%days% &7天 &8| &7点券 &e%points%")
                        .replace("%season%", String.valueOf(plugin.getPassConfig().seasonId(now)))
                        .replace("%days%", String.valueOf(plugin.getPassConfig().seasonDaysLeft(now)))
                        .replace("%points%", String.valueOf(plugin.getEconomy().lookPoints(player))));
            }
        } else {
            help(sender);
        }
        return true;
    }

    private boolean adminGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pokepass.admin")) {
            sender.sendMessage(plugin.getLang().msg("no-permission", "&c你没有权限"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.getLang().msg("usage-give", "&c用法: /zl give <玩家> <经验>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLang().msg("no-player", "&c找不到这个玩家"));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getLang().msg("bad-number", "&c经验必须是数字"));
            return true;
        }
        PlayerProgress progress = plugin.getProgressService().get(target);
        progress.addExp(amount, plugin.getPassConfig());
        plugin.getProgressService().store().save(progress);
        sender.sendMessage(plugin.getLang().msg("admin-give", "&a已给 &f%player% &a加了 &f%amount% &a经验")
                .replace("%player%", target.getName())
                .replace("%amount%", String.valueOf(amount)));
        target.sendMessage(plugin.getLang().msg("level-up", "&e战令等级提升! 当前 &f%level% &e级")
                .replace("%level%", String.valueOf(progress.getLevel(plugin.getPassConfig()))));
        return true;
    }

    private boolean adminReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pokepass.admin")) {
            sender.sendMessage(plugin.getLang().msg("no-permission", "&c你没有权限"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().msg("usage-reset", "&c用法: /zl reset <玩家>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLang().msg("no-player", "&c找不到这个玩家"));
            return true;
        }
        PlayerProgress progress = plugin.getProgressService().get(target);
        progress.setTotalExp(0);
        progress.dailyMap().clear();
        progress.weeklyMap().clear();
        progress.getClaimedFree().clear();
        progress.getClaimedPremium().clear();
        progress.touch();
        plugin.getProgressService().store().save(progress);
        sender.sendMessage(plugin.getLang().msg("admin-reset", "&a已重置 &f%player% &a的战令进度")
                .replace("%player%", target.getName()));
        return true;
    }

    /** 自检：事件总线是否真的注册上了、任务表、存储读写 */
    /**
     * 启动自检入口：插件 enable 后延迟调一次，用控制台身份跑同一套检查。
     * 这样每次重启就能在日志里看到配置有没有写坏，不必等人上号敲命令。
     */
    public void runSelftest(CommandSender sender) {
        selftest(sender);
    }

    /**
     * {@code /zl progress <玩家> <类型> [数量]}
     *
     * <p>给其它插件用的进度入口：特训 / 打工插件在精灵领取成功时，用控制台身份调这个，
     * 把「完成一次特训 / 打工」推进到战令任务上。玩家自己调一律拒绝。</p>
     */
    private boolean addProgress(CommandSender sender, String[] args) {
        boolean console = sender instanceof org.bukkit.command.ConsoleCommandSender;
        if (!console && !sender.hasPermission("pokepass.admin")) {
            sender.sendMessage(plugin.getLang().msg("no-permission", "&c你没有权限"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§c用法: /zl progress <玩家> <类型> [数量]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLang().msg("no-player", "&c找不到这个玩家"));
            return true;
        }
        QuestType type = QuestType.of(args[2]);
        if (type == null) {
            sender.sendMessage("§c未知任务类型: " + args[2] + "（可用 training / working）");
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {
                // 写错就按 1 算，不值得为一条内部调用把整次领取打成失败
            }
        }
        boolean changed = plugin.getQuestService().advance(target, type, "*", amount);
        sender.sendMessage("§7战令进度: " + target.getName() + " " + type.configName()
                + " +" + amount + (changed ? " §a已推进" : " §7无匹配任务"));
        return true;
    }

    private void selftest(CommandSender sender) {
        sender.sendMessage("§6§lPixelmonTXZ 战令自检");

        boolean busOk = false;
        int listenerCount = -1;
        try {
            java.lang.reflect.Field field = com.pixelmonmod.pixelmon.Pixelmon.EVENT_BUS.getClass()
                    .getDeclaredField("listeners");
            field.setAccessible(true);
            Object value = field.get(com.pixelmonmod.pixelmon.Pixelmon.EVENT_BUS);
            if (value instanceof java.util.Map) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) value;
                listenerCount = map.size();
                busOk = map.containsKey(plugin.getPixelmonListener());
            }
        } catch (Throwable t) {
            sender.sendMessage("§c总线反射异常: " + t.getClass().getSimpleName());
        }
        sender.sendMessage((busOk ? "§a[OK] " : "§c[FAIL] ")
                + "§fPixelmon 事件总线已注册（总线内共 " + listenerCount + " 个监听器）");

        sender.sendMessage("§a[OK] §f任务表: 每日 " + plugin.getQuestBook().size(Quest.Period.DAILY)
                + " 个 / 每周 " + plugin.getQuestBook().size(Quest.Period.WEEKLY)
                + " 个，奖励等级 " + plugin.getRewardBook().maxLevelWithReward());

        // 奖励预检自检：点券/经济没装时，含这类条目的奖励必须被判定成「发不出去」，
        // 而不是照发一遍再标记已领——那样玩家就永久领不到了。
        boolean pointsReady = plugin.getEconomy().pointsAvailable();
        String pointsReason = plugin.getRewardService().whyUnavailable(
                Arrays.asList(RewardEntry.parse("points:100")));
        boolean pointsBlocked = pointsReason != null;
        boolean pointsAsExpected = pointsReady != pointsBlocked;
        sender.sendMessage((pointsAsExpected ? "§a[OK] " : "§c[FAIL] ")
                + "§f点券奖励预检: 点券" + (pointsReady ? "已就绪" : "未就绪")
                + " -> " + (pointsBlocked ? "拦截(" + pointsReason + ")" : "放行"));

        boolean vaultReady = plugin.getEconomy().vaultAvailable();
        String moneyReason = plugin.getRewardService().whyUnavailable(
                Arrays.asList(RewardEntry.parse("money:500")));
        boolean moneyBlocked = moneyReason != null;
        sender.sendMessage((vaultReady != moneyBlocked ? "§a[OK] " : "§c[FAIL] ")
                + "§f金币奖励预检: 经济" + (vaultReady ? "已就绪" : "未就绪")
                + " -> " + (moneyBlocked ? "拦截(" + moneyReason + ")" : "放行"));

        String itemReason = plugin.getRewardService().whyUnavailable(
                Arrays.asList(RewardEntry.parse("item:NOT_A_REAL_ITEM:1")));
        sender.sendMessage((itemReason != null ? "§a[OK] " : "§c[FAIL] ")
                + "§f坏物品预检: " + (itemReason != null ? "已拦截(" + itemReason + ")" : "未拦截，会静默丢奖励"));

        long dedupe = plugin.getQuestService().dedupeWindowMs();
        sender.sendMessage((dedupe > 0 ? "§a[OK] " : "§e[--] ")
                + "§f防刷: 稀有事件 " + (dedupe > 0 ? dedupe + "ms 内重复触发只记一次" : "未开启（窗口为 0）"));

        long now = System.currentTimeMillis();
        if (plugin.getPassConfig().seasonEnabled()) {
            sender.sendMessage("§a[OK] §f赛季: 第 " + plugin.getPassConfig().seasonId(now)
                    + " 赛季, 每季 " + plugin.getPassConfig().seasonLengthDays()
                    + " 天, 还剩 " + plugin.getPassConfig().seasonDaysLeft(now) + " 天");
        } else {
            sender.sendMessage("§e[--] §f赛季未启用（等级、领取记录不会被重置）");
        }

        // 商店：商品条目里写错物品名 / 换了前置没装的货币，都在这条里暴露出来，
        // 免得玩家点了兑换才发现点券系统根本不在
        int shopCount = plugin.getShopBook().items().size();
        if (shopCount == 0) {
            sender.sendMessage("§e[--] §f商店: 未配置商品（shop.yml 为空）");
        } else {
            int broken = 0;
            String firstReason = null;
            for (com.pokepass.shop.ShopItem item : plugin.getShopBook().items()) {
                String reason = plugin.getRewardService().whyUnavailable(item.getRewards());
                if (reason != null) {
                    broken++;
                    if (firstReason == null) {
                        firstReason = item.getId() + ": " + reason;
                    }
                }
            }
            sender.sendMessage((broken == 0 ? "§a[OK] " : "§c[FAIL] ")
                    + "§f商店: " + shopCount + " 件商品"
                    + (broken == 0 ? "" : "，其中 " + broken + " 件当前发不出去 (" + firstReason + ")"));
        }

        // 界面配置：gui.yml 里写错的材质 / 越界的槽位 / 撞掉的按钮，启动时就报出来，
        // 免得玩家打开界面才发现某个按钮凭空消失
        java.util.List<String> guiWarnings = plugin.getGuiConfig().warnings();
        if (guiWarnings.isEmpty()) {
            sender.sendMessage("§a[OK] §f界面配置: gui.yml 全部合法（5 个界面，按钮/装饰/内容区无冲突）");
        } else {
            sender.sendMessage("§c[FAIL] §f界面配置有 " + guiWarnings.size() + " 处问题，第一条: "
                    + guiWarnings.get(0));
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            PlayerProgress progress = plugin.getProgressService().get(player);
            long before = progress.getTotalExp();
            plugin.getProgressService().store().save(progress);
            PlayerProgress reloaded = plugin.getProgressService().store().load(progress.getUuid());
            boolean ok = reloaded != null && reloaded.getTotalExp() == before;
            sender.sendMessage((ok ? "§a[OK] " : "§c[FAIL] ") + "§f存储读写一致（总经验 " + before
                    + "，等级 " + progress.getLevel(plugin.getPassConfig()) + "）");

            int visible = plugin.getQuestService().visible(player, progress).size();
            sender.sendMessage("§a[OK] §f当前可见任务 " + visible + " 个，可领奖励 "
                    + plugin.getRewardService().claimable(player) + " 份");
        }
    }

    private void help(CommandSender sender) {
        for (String line : plugin.getLang().lines("help", Arrays.asList(
                "&6&l战令 &8| &f帮助",
                "&e/zl &7打开主面板",
                "&e/zl daily &7每日任务",
                "&e/zl weekly &7每周任务",
                "&e/zl reward &7等级奖励",
                "&e/zl shop &7兑换商店（点券/金币）",
                "&e/zl claim &7一键领取",
                "&e/zl info &7查看自己的进度",
                "&8管理员: &e/zl reload&7, &e/zl give <玩家> <经验>&7, &e/zl reset <玩家>"))) {
            sender.sendMessage(line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<String>(Arrays.asList(
                    "daily", "weekly", "reward", "shop", "claim", "info", "help"));
            if (sender.hasPermission("pokepass.admin")) {
                subs.addAll(Arrays.asList("reload", "give", "reset", "selftest", "progress"));
            }
            for (String sub : subs) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give")
                || args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("progress"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    out.add(player.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("progress")) {
            for (String type : new String[]{"training", "working"}) {
                if (type.startsWith(args[2].toLowerCase())) {
                    out.add(type);
                }
            }
        }
        return out;
    }
}
