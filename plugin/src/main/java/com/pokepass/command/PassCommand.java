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
        if ("addvip".equals(sub) || "vip".equals(sub)) {
            return adminVip(sender, args);
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
            // 周任务页：告诉玩家「现在能看几页 / 还能靠资历白拿几页」，
            // 不然点券解锁按钮旁边没有任何信息，玩家会以为只能花钱
            if (plugin.getPassConfig().weeklyPagesEnabled()) {
                int total = plugin.getQuestBook().pages(Quest.Period.WEEKLY);
                int unlocked = plugin.getQuestService().unlockedPages(progress).size();
                int daysLeft = plugin.getQuestService().daysToNextFreePage(progress);
                player.sendMessage(plugin.getLang().msg("info-pages",
                                "&7周任务 &f%unlocked%&7/&f%total% &7页 &8| &7再等 &f%days% &7天自动开一页")
                        .replace("%unlocked%", String.valueOf(unlocked))
                        .replace("%total%", String.valueOf(total))
                        .replace("%days%", daysLeft < 0 ? "—" : String.valueOf(daysLeft)));
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

    /**
     * {@code /zl addvip <玩家> <true|false>}——原版战令的同名命令。
     *
     * <p>写的是玩家数据里的标记，和权限 {@code pokepass.premium} 是「或」的关系：
     * 接权限组的服照旧用权限卖，这里给不想动权限组的服留一个手动开关。
     * 支持未在线玩家（改完下次上线生效）。</p>
     */
    private boolean adminVip(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pokepass.admin")) {
            sender.sendMessage(plugin.getLang().msg("no-permission", "&c你没有权限"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.getLang().msg("usage-addvip", "&c用法: /zl addvip <玩家> <true|false>"));
            return true;
        }
        Boolean value = parseBoolean(args[2]);
        if (value == null) {
            sender.sendMessage(plugin.getLang().msg("fail-boolean", "&c第三个参数只能写 true 或 false"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        PlayerProgress progress;
        String name = args[1];
        if (target != null) {
            progress = plugin.getProgressService().get(target);
            name = target.getName();
        } else {
            // 只用本地 usercache 找，不走 Mojang 接口：这条命令会卡在主线程，
            // 为了改一个 flag 去发一次 HTTP 不值得
            java.util.UUID uuid = findOfflineUuid(args[1]);
            if (uuid == null) {
                sender.sendMessage(plugin.getLang().msg("no-player", "&c找不到这个玩家"));
                return true;
            }
            progress = plugin.getProgressService().store().load(uuid);
            progress.setLastName(name);
        }
        progress.setPremiumFlag(value);
        progress.touch();
        plugin.getProgressService().store().save(progress);
        sender.sendMessage(plugin.getLang().msg(value ? "vip.granted"
                        : "vip.revoked",
                        value ? "&a已把 &f%player% &a设为高级会员" : "&7已取消 &f%player% &7的高级会员标记")
                .replace("%player%", name));
        if (target != null) {
            target.sendMessage(plugin.getLang().msg(value ? "vip.self-on" : "vip.self-off",
                    value ? "&a你已成为高级会员，任务经验与高级轨奖励已解锁"
                            : "&7你的高级会员标记已被取消"));
        }
        return true;
    }

    private Boolean parseBoolean(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text) || "是".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text) || "否".equals(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** 在本地玩家缓存里按名字找 UUID；找不到返回 null */
    private java.util.UUID findOfflineUuid(String name) {
        for (org.bukkit.OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) {
                return offline.getUniqueId();
            }
        }
        return findInUserCache(name);
    }

    /**
     * 兜底：读服务端根目录的 {@code usercache.json}。
     *
     * <p>{@code Bukkit.getOfflinePlayers()} 只认识「有 playerdata 的玩家」，玩家删过档、
     * 换过世界名、或者只在别的子服玩过，这里就查不到。usercache 是 Mojang 名字 → UUID
     * 的本地副本，不联网、格式稳定，正好补这个缺口。</p>
     */
    private java.util.UUID findInUserCache(String name) {
        java.io.File file = new java.io.File("usercache.json");
        if (!file.isFile()) {
            return null;
        }
        try {
            String json = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.Charset.forName("UTF-8"));
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                            "\"name\"\\s*:\\s*\"" + java.util.regex.Pattern.quote(name)
                                    + "\"\\s*,\\s*\"uuid\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"",
                            java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(json);
            if (matcher.find()) {
                return java.util.UUID.fromString(matcher.group(1));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 自检：事件总线是否真的注册上了、任务表、存储读写 */    /**
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
                + " 个（每周 " + plugin.getQuestBook().pages(Quest.Period.WEEKLY) + " 页）");

        // 周任务分页：检查有没有「空页」——page 跳号会让玩家花钱解锁到一页空气
        int totalPages = plugin.getQuestBook().pages(Quest.Period.WEEKLY);
        if (!plugin.getPassConfig().weeklyPagesEnabled()) {
            sender.sendMessage("§e[--] §f周任务分页未启用（" + totalPages + " 页全部免费）");
        } else {
            StringBuilder empty = new StringBuilder();
            for (int page = 1; page <= totalPages; page++) {
                if (plugin.getQuestService().pageSize(page) == 0) {
                    if (empty.length() > 0) {
                        empty.append(',');
                    }
                    empty.append(page);
                }
            }
            int cost = plugin.getPassConfig().weeklyPageCost();
            int unlockDays = plugin.getPassConfig().weeklyUnlockDays();
            sender.sendMessage((empty.length() == 0 ? "§a[OK] " : "§c[FAIL] ")
                    + "§f周任务分页: 共 " + totalPages + " 页，免费 "
                    + plugin.getPassConfig().weeklyFreePages() + " 页，解锁一页 "
                    + (cost > 0 ? cost + " 点券" : "免费（0 点券）")
                    + "，天数解锁 " + (unlockDays > 0 ? "每 " + unlockDays + " 天 +1 页" : "关闭")
                    + (empty.length() == 0 ? "" : "；空页: " + empty + "（玩家解锁后看不到任务）"));
        }

        // 按天数自动解锁：用合成档案跑一遍算法，不依赖有人在线。
        // 首登时间往前推满一个周期，应当比现在多开一页；全新档案应当一页都不多开。
        int unlockDays = plugin.getPassConfig().weeklyUnlockDays();
        if (unlockDays > 0 && plugin.getPassConfig().weeklyPagesEnabled()) {
            int free = plugin.getPassConfig().weeklyFreePages();
            int total = plugin.getQuestBook().pages(Quest.Period.WEEKLY);
            PlayerProgress old = new PlayerProgress(new java.util.UUID(0L, 0L));
            old.setFirstJoin(System.currentTimeMillis() - unlockDays * 86400000L - 60000L);
            int bonus = plugin.getQuestService().pagesByDays(old);
            int expected = Math.min(total, free + bonus);
            int actual = plugin.getQuestService().unlockedPages(old).size();
            boolean freshOk = plugin.getQuestService().pagesByDays(
                    new PlayerProgress(new java.util.UUID(0L, 1L))) == 0;
            boolean ok = bonus >= 1 && actual == expected && freshOk;
            sender.sendMessage((ok ? "§a[OK] " : "§c[FAIL] ")
                    + "§f按天解锁: 首登满 " + unlockDays + " 天 -> 多开 " + bonus
                    + " 页，实际解锁 " + actual + "/" + total + " 页（应为 " + expected
                    + "），新档案不多开=" + freshOk);
        } else {
            sender.sendMessage("§e[--] §f按天解锁: 未启用（unlock-days 为 0 或分页关闭）");
        }

        // 高级会员：倍率和「权限 / 数据标记」两条判定路径都在这条里报出来，
        // 免得线上出现「买了 VIP 但经验没翻倍」时还要去翻配置确认哪一路没生效
        double multiplier = plugin.getPassConfig().premiumExpMultiplier();
        String multiplierText = multiplier == Math.floor(multiplier)
                ? String.valueOf((int) multiplier) : String.valueOf(multiplier);
        sender.sendMessage((plugin.getPassConfig().premiumEnabled() ? "§a[OK] " : "§e[--] ")
                + "§f高级会员: " + (plugin.getPassConfig().premiumEnabled()
                ? "任务经验 x" + multiplierText + "，凭权限 "
                + plugin.getPassConfig().premiumPermission() + " 或 /zl addvip 标记"
                : "未启用（所有人按免费轨，经验不翻倍）"));

        // 奖励覆盖度：奖励表没配到的等级，玩家点开奖励页只会看到「无奖励」
        int maxLevel = plugin.getPassConfig().maxLevel();
        int covered = plugin.getRewardBook().coveredLevels();
        int rewardWarnings = plugin.getRewardBook().warnings().size();
        sender.sendMessage((covered >= maxLevel && rewardWarnings == 0 ? "§a[OK] " : "§e[--] ")
                + "§f等级奖励: 已配 " + covered + "/" + maxLevel + " 级"
                + (rewardWarnings == 0 ? "，无配置告警"
                : "，有 " + rewardWarnings + " 条告警，第一条: "
                + plugin.getRewardBook().warnings().get(0)));

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

        // 精灵图标：gui/shop/任务里的 sprite 都走 PixelmonSprites 反射创建，
        // 在这里实测一次，Pixelmon 版本变了也能第一时间在日志里看出来
        try {
            java.util.List<String> spriteWarn = new java.util.ArrayList<String>();
            org.bukkit.inventory.ItemStack sprite =
                    com.pokepass.util.PixelmonSprites.create("Pikachu", 0, false, spriteWarn, "selftest");
            if (sprite != null) {
                sender.sendMessage("§a[OK] §f精灵图标: pixelmon_sprite 创建成功（皮卡丘，"
                        + "gui/shop/任务的 sprite 键可用）");
            } else {
                sender.sendMessage("§c[FAIL] §f精灵图标: 创建失败（"
                        + (spriteWarn.isEmpty() ? "未知原因" : spriteWarn.get(0)) + "）");
            }
        } catch (Throwable t) {
            sender.sendMessage("§c[FAIL] §f精灵图标: 异常 " + t);
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
                "&8管理员: &e/zl reload&7, &e/zl give <玩家> <经验>&7, &e/zl reset <玩家>&7, &e/zl addvip <玩家> <true|false>"))) {
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
                subs.addAll(Arrays.asList("reload", "give", "reset", "addvip", "selftest", "progress"));
            }
            for (String sub : subs) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give")
                || args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("progress")
                || args[0].equalsIgnoreCase("addvip"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    out.add(player.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("addvip")) {
            for (String value : new String[]{"true", "false"}) {
                if (value.startsWith(args[2].toLowerCase())) {
                    out.add(value);
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
