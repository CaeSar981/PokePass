package com.pokepass.gui;

import com.pokepass.PokePassPlugin;
import com.pokepass.data.PlayerProgress;
import com.pokepass.quest.Quest;
import com.pokepass.reward.RewardEntry;
import com.pokepass.reward.RewardService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 三个界面：主面板 / 任务页（每日与每周共用）/ 奖励页。
 *
 * <p>全部实现 {@link InventoryHolder}，事件按 holder 类型分派，
 * 不靠标题字符串匹配（标题里有色码和变量，匹配容易翻车）。</p>
 */
public final class PassMenus {

    private PassMenus() {
    }

    /** 三个界面的公共部分 */
    public abstract static class BaseMenu implements InventoryHolder {

        protected final PokePassPlugin plugin;
        protected final Player player;
        protected Inventory inventory;

        protected BaseMenu(PokePassPlugin plugin, Player player) {
            this.plugin = plugin;
            this.player = player;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        protected abstract String title();

        protected abstract int size();

        public abstract void render();

        /** 子类按需覆盖；默认什么都不做 */
        public void handleClick(int slot, ClickType type) {
        }

        public void open() {
            inventory = Bukkit.createInventory(this, size(), GuiKit.color(title()));
            render();
            player.openInventory(inventory);
        }

        public void redraw() {
            if (inventory != null) {
                render();
            }
        }

        /** 铺一层灰色玻璃板做底 */
        protected void fillFrame(Inventory inv, int... skip) {
            ItemStack pane = GuiKit.item(Material.STAINED_GLASS_PANE, 1, (short) 7, "&f", null);
            outer:
            for (int i = 0; i < inv.getSize(); i++) {
                for (int s : skip) {
                    if (s == i) {
                        continue outer;
                    }
                }
                inv.setItem(i, pane);
            }
        }
    }

    // ================= 主面板 =================

    public static class MainMenu extends BaseMenu {

        public MainMenu(PokePassPlugin plugin, Player player) {
            super(plugin, player);
        }

        @Override
        protected String title() {
            return plugin.getGuiConfig().title("main", "&6&l战令 &8| &f主面板");
        }

        @Override
        protected int size() {
            return plugin.getGuiConfig().size("main", 27);
        }

        @Override
        public void render() {
            inventory.clear();
            fillFrame(inventory, 11, 13, 15, 20, 22, 24, 26);

            PlayerProgress progress = plugin.getProgressService().get(player);
            int level = progress.getLevel(plugin.getPassConfig());
            int inLevel = progress.expWithinLevel(plugin.getPassConfig());
            int need = plugin.getPassConfig().expForLevel(level);
            boolean maxed = level >= plugin.getPassConfig().maxLevel();

            List<String> levelLore = new ArrayList<String>();
            levelLore.add("&8&m                        ");
            levelLore.add("&7当前等级 &f" + level + "&7/&f" + plugin.getPassConfig().maxLevel());
            if (maxed) {
                levelLore.add("&a已满级");
            } else {
                levelLore.add("&7经验 &f" + inLevel + "&7/&f" + need);
                levelLore.add(GuiKit.bar(inLevel, need, 20, "&a", "&8"));
                levelLore.add("&7还需 &f" + Math.max(0, need - inLevel) + " &7经验升级");
            }
            levelLore.add("&7总经验 &f" + progress.getTotalExp());
            levelLore.add("&8&m                        ");
            levelLore.add("&7做完任务自动涨经验");
            inventory.setItem(13, GuiKit.glow(GuiKit.item(Material.NETHER_STAR, 1, "&6&l战令等级 &f" + level, levelLore)));

            // 赛季卡片：换季会清等级，得让玩家提前知道还剩几天
            long now = System.currentTimeMillis();
            boolean seasonOn = plugin.getPassConfig().seasonEnabled();
            int seasonId = plugin.getPassConfig().seasonId(now);
            int daysLeft = plugin.getPassConfig().seasonDaysLeft(now);
            List<String> seasonLore = new ArrayList<String>();
            seasonLore.add("&8&m                        ");
            if (seasonOn) {
                seasonLore.add("&7当前赛季 &f第 " + seasonId + " 赛季");
                seasonLore.add("&7赛季长度 &f" + plugin.getPassConfig().seasonLengthDays() + " &7天");
                seasonLore.add("&7还剩 &f" + daysLeft + " &7天换季");
                seasonLore.add("&8&m                        ");
                seasonLore.add("&7换季后等级、领取记录、");
                seasonLore.add("&7任务进度与商店限购都会重置");
            } else {
                seasonLore.add("&7赛季未启用");
                seasonLore.add("&7等级会一直累积");
            }
            inventory.setItem(20, GuiKit.item(Material.WATCH, 1, "&e&l赛季 &f#" + seasonId, seasonLore));

            int dailyDone = doneCount(progress, Quest.Period.DAILY);
            int weeklyDone = doneCount(progress, Quest.Period.WEEKLY);

            inventory.setItem(11, GuiKit.item(Material.BOOK, 1, "&b&l每日任务", java.util.Arrays.asList(
                    "&8&m                        ",
                    "&7进度 &f" + dailyDone + "&7/&f" + visibleCount(Quest.Period.DAILY),
                    "&7每天 &f" + plugin.getPassConfig().dailyResetHour() + " &7点刷新",
                    "&8&m                        ",
                    "&e点击查看")));

            inventory.setItem(15, GuiKit.item(Material.ENCHANTED_BOOK, 1, "&d&l每周任务", java.util.Arrays.asList(
                    "&8&m                        ",
                    "&7进度 &f" + weeklyDone + "&7/&f" + visibleCount(Quest.Period.WEEKLY),
                    "&7每周一刷新",
                    "&8&m                        ",
                    "&e点击查看")));

            int claimable = plugin.getRewardService().claimable(player);
            List<String> rewardLore = new ArrayList<String>();
            rewardLore.add("&8&m                        ");
            rewardLore.add("&7免费轨：等级到了就能领");
            rewardLore.add("&7高级轨：" + (plugin.getRewardService().hasPremium(player) ? "&a已解锁" : "&c未解锁"));
            rewardLore.add("&7可领取 &f" + claimable + " &7份");
            rewardLore.add("&8&m                        ");
            rewardLore.add("&e点击查看");
            ItemStack rewardItem = GuiKit.item(Material.CHEST, 1, "&6&l等级奖励", rewardLore);
            if (claimable > 0) {
                GuiKit.glow(rewardItem);
            }
            inventory.setItem(22, rewardItem);

            // 商店：点券的消耗口。没配商品时不显示，免得玩家点进去看到空界面
            if (!plugin.getShopBook().items().isEmpty()) {
                List<String> shopLore = new ArrayList<String>();
                shopLore.add("&8&m                        ");
                shopLore.add("&7用点券 / 金币兑换道具");
                shopLore.add("&7点券余额 &e" + plugin.getEconomy().lookPoints(player));
                shopLore.add("&8&m                        ");
                shopLore.add("&e点击进入");
                inventory.setItem(24, GuiKit.item(Material.EMERALD, 1, "&a&l兑换商店", shopLore));
            }

            inventory.setItem(26, GuiKit.item(Material.BARRIER, 1, "&c关闭", null));
        }

        private int doneCount(PlayerProgress progress, Quest.Period period) {
            int count = 0;
            for (Quest quest : plugin.getQuestService().visible(player, progress)) {
                if (quest.getPeriod() == period && progress.isComplete(quest)) {
                    count++;
                }
            }
            return count;
        }

        private int visibleCount(Quest.Period period) {
            int count = 0;
            for (Quest quest : plugin.getQuestService().visible(player,
                    plugin.getProgressService().get(player))) {
                if (quest.getPeriod() == period) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public void handleClick(int slot, ClickType type) {
            if (slot == 11) {
                new QuestMenu(plugin, player, Quest.Period.DAILY).open();
            } else if (slot == 15) {
                new QuestMenu(plugin, player, Quest.Period.WEEKLY).open();
            } else if (slot == 22) {
                new RewardMenu(plugin, player).open();
            } else if (slot == 24) {
                if (!plugin.getShopBook().items().isEmpty()) {
                    new ShopMenu(plugin, player).open();
                }
            } else if (slot == 26) {
                player.closeInventory();
            }
        }
    }

    // ================= 任务页 =================

    public static class QuestMenu extends BaseMenu {

        private static final int[] QUEST_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        private final Quest.Period period;
        private int page;

        public QuestMenu(PokePassPlugin plugin, Player player, Quest.Period period) {
            super(plugin, player);
            this.period = period;
        }

        @Override
        protected String title() {
            return plugin.getGuiConfig().title(period == Quest.Period.DAILY ? "daily" : "weekly",
                    period == Quest.Period.DAILY ? "&6&l战令 &8| &f每日任务" : "&6&l战令 &8| &f每周任务");
        }

        @Override
        protected int size() {
            return plugin.getGuiConfig().size(period == Quest.Period.DAILY ? "daily" : "weekly", 54);
        }

        @Override
        public void render() {
            inventory.clear();
            fillFrame(inventory, QUEST_SLOTS);

            PlayerProgress progress = plugin.getProgressService().get(player);
            List<Quest> quests = new ArrayList<Quest>();
            for (Quest quest : plugin.getQuestService().visible(player, progress)) {
                if (quest.getPeriod() == period) {
                    quests.add(quest);
                }
            }
            int perPage = QUEST_SLOTS.length;
            int maxPage = Math.max(0, (quests.size() - 1) / perPage);
            if (page > maxPage) {
                page = maxPage;
            }
            for (int i = 0; i < perPage; i++) {
                int index = page * perPage + i;
                if (index >= quests.size()) {
                    break;
                }
                Quest quest = quests.get(index);
                inventory.setItem(QUEST_SLOTS[i], renderQuest(quest, progress));
            }

            inventory.setItem(45, GuiKit.item(Material.ARROW, 1, "&f返回", null));
            if (page > 0) {
                inventory.setItem(48, GuiKit.item(Material.SPECTRAL_ARROW, 1, "&a上一页", null));
            }
            if (page < maxPage) {
                inventory.setItem(50, GuiKit.item(Material.SPECTRAL_ARROW, 1, "&a下一页", null));
            }
            inventory.setItem(53, GuiKit.item(Material.BARRIER, 1, "&c关闭", null));
        }

        private ItemStack renderQuest(Quest quest, PlayerProgress progress) {
            int current = progress.progressOf(quest);
            boolean complete = progress.isComplete(quest);
            List<String> lore = new ArrayList<String>(quest.getLore());
            lore.add("&8&m                        ");
            if (complete) {
                lore.add(plugin.getLang().plain("quest.status-done", "&a✔ 已完成"));
            } else {
                lore.add(plugin.getLang().plain("quest.status-progress", "&7进度 &f%now%&7/&f%max%")
                        .replace("%now%", String.valueOf(current))
                        .replace("%max%", String.valueOf(quest.getAmount())));
                lore.add(GuiKit.bar(current, quest.getAmount(), 16, "&b", "&8"));
            }
            lore.add("&7奖励经验 &f" + quest.getExp());
            ItemStack item = GuiKit.item(quest.getIcon(), 1, quest.getName(), lore);
            if (complete) {
                GuiKit.glow(item);
            }
            return item;
        }

        @Override
        public void handleClick(int slot, ClickType type) {
            if (slot == 45) {
                new MainMenu(plugin, player).open();
            } else if (slot == 48 && page > 0) {
                page--;
                render();
            } else if (slot == 50) {
                page++;
                render();
            } else if (slot == 53) {
                player.closeInventory();
            }
        }
    }

    // ================= 奖励页 =================

    public static class RewardMenu extends BaseMenu {

        private static final int[] LEVEL_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        private int page;

        public RewardMenu(PokePassPlugin plugin, Player player) {
            super(plugin, player);
        }

        @Override
        protected String title() {
            return plugin.getGuiConfig().title("reward", "&6&l战令 &8| &f等级奖励");
        }

        @Override
        protected int size() {
            return plugin.getGuiConfig().size("reward", 54);
        }

        @Override
        public void render() {
            inventory.clear();
            fillFrame(inventory, LEVEL_SLOTS);

            PlayerProgress progress = plugin.getProgressService().get(player);
            int currentLevel = progress.getLevel(plugin.getPassConfig());
            boolean premium = plugin.getRewardService().hasPremium(player);
            int max = plugin.getRewardBook().maxLevelWithReward();
            int perPage = LEVEL_SLOTS.length;
            int maxPage = Math.max(0, (max - 1) / perPage);
            if (page > maxPage) {
                page = maxPage;
            }
            for (int i = 0; i < perPage; i++) {
                int level = page * perPage + i + 1;
                if (level > max) {
                    break;
                }
                inventory.setItem(LEVEL_SLOTS[i], renderLevel(level, progress, currentLevel, premium));
            }

            inventory.setItem(45, GuiKit.item(Material.ARROW, 1, "&f返回", null));
            if (page > 0) {
                inventory.setItem(48, GuiKit.item(Material.SPECTRAL_ARROW, 1, "&a上一页", null));
            }
            if (page < maxPage) {
                inventory.setItem(50, GuiKit.item(Material.SPECTRAL_ARROW, 1, "&a下一页", null));
            }
            int claimable = plugin.getRewardService().claimable(player);
            if (claimable > 0) {
                inventory.setItem(49, GuiKit.glow(GuiKit.item(Material.ENDER_CHEST, 1, "&a&l一键领取 &7(" + claimable + ")",
                        java.util.Arrays.asList("&7把所有够等级的奖励一次领完"))));
            }
            inventory.setItem(53, GuiKit.item(Material.BARRIER, 1, "&c关闭", null));
        }

        private ItemStack renderLevel(int level, PlayerProgress progress, int currentLevel, boolean premium) {
            List<RewardEntry> free = plugin.getRewardBook().free(level);
            List<RewardEntry> premiumList = plugin.getRewardBook().premium(level);
            List<String> lore = new ArrayList<String>();
            lore.add("&8&m                        ");
            lore.add("&f免费轨:");
            appendRewards(lore, free, progress.isClaimed(level, false));
            lore.add("&f高级轨 " + (premium ? "&a(已解锁)" : "&c(未解锁)"));
            appendRewards(lore, premiumList, progress.isClaimed(level, true));
            lore.add("&8&m                        ");

            boolean locked = level > currentLevel;
            if (locked) {
                lore.add("&c需要战令 " + level + " 级");
            } else {
                lore.add("&e左键领免费 &8| &e右键领高级");
            }
            Material material = locked ? Material.STAINED_GLASS_PANE : Material.GOLD_INGOT;
            short data = (short) (locked ? 14 : 0);
            ItemStack item = GuiKit.item(material, 1, "&6" + level + " 级奖励", lore);
            if (locked) {
                item = GuiKit.item(material, 1, data, "&6" + level + " 级奖励", lore);
            } else if (!progress.isClaimed(level, false) || (premium && !progress.isClaimed(level, true))) {
                GuiKit.glow(item);
            }
            return item;
        }

        private void appendRewards(List<String> lore, List<RewardEntry> entries, boolean claimed) {
            if (entries.isEmpty()) {
                lore.add("  &8—");
                return;
            }
            for (String line : plugin.getRewardService().describeAll(entries)) {
                lore.add("  " + (claimed ? "&8✔ " : "&7• ") + (claimed ? "&8" + org.bukkit.ChatColor.stripColor(line) : line));
            }
        }

        @Override
        public void handleClick(int slot, ClickType type) {
            if (slot == 45) {
                new MainMenu(plugin, player).open();
                return;
            }
            if (slot == 48 && page > 0) {
                page--;
                render();
                return;
            }
            if (slot == 50) {
                page++;
                render();
                return;
            }
            if (slot == 53) {
                player.closeInventory();
                return;
            }
            if (slot == 49) {
                int count = plugin.getRewardService().claimAll(player);
                if (count > 0) {
                    player.sendMessage(plugin.getLang().msg("reward.claim-all", "&a已领取 &f%count% &a份奖励")
                            .replace("%count%", String.valueOf(count)));
                } else {
                    player.sendMessage(plugin.getLang().msg("reward.nothing", "&7没有可以领取的奖励"));
                }
                render();
                return;
            }
            int index = indexOf(LEVEL_SLOTS, slot);
            if (index < 0) {
                return;
            }
            int level = page * LEVEL_SLOTS.length + index + 1;
            boolean premiumTrack = type.isRightClick();
            RewardService.Result result = plugin.getRewardService().claim(player, level, premiumTrack);
            switch (result) {
                case OK:
                    player.sendMessage(plugin.getLang().msg("reward.claimed", "&a已领取 &f%level% &a级%track%奖励")
                            .replace("%level%", String.valueOf(level))
                            .replace("%track%", premiumTrack ? "高级轨" : "免费轨"));
                    break;
                case NOT_ENOUGH_LEVEL:
                    player.sendMessage(plugin.getLang().msg("reward.not-enough-level", "&c战令等级不够"));
                    break;
                case NO_PERMISSION:
                    player.sendMessage(plugin.getLang().msg("reward.no-permission", "&c你没有解锁高级轨"));
                    break;
                case ALREADY_CLAIMED:
                    player.sendMessage(plugin.getLang().msg("reward.already", "&7这份奖励已经领过了"));
                    break;
                case NO_REWARD:
                    player.sendMessage(plugin.getLang().msg("reward.nothing", "&7这一级没有奖励"));
                    break;
                case UNAVAILABLE:
                    // 具体原因已由 RewardService 发出（点券/经济未就绪、物品名写错等）
                    break;
                default:
                    break;
            }
            render();
        }

        private int indexOf(int[] slots, int slot) {
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == slot) {
                    return i;
                }
            }
            return -1;
        }
    }

    // ================= 兑换商店 =================

    /**
     * 商店界面。商品槽位由 shop.yml 决定，界面只负责渲染和把点击转给 ShopService。
     *
     * <p>余额、限购次数每次 render 都重新查：玩家可能在这个界面开着的时候
     * 从别处领到了点券，或者刚好被别人买走限购最后一份。</p>
     */
    public static class ShopMenu extends BaseMenu {

        public ShopMenu(PokePassPlugin plugin, Player player) {
            super(plugin, player);
        }

        @Override
        protected String title() {
            return plugin.getShopBook().title();
        }

        @Override
        protected int size() {
            return plugin.getShopBook().size();
        }

        @Override
        public void render() {
            inventory.clear();
            List<Integer> itemSlots = new ArrayList<Integer>();
            for (com.pokepass.shop.ShopItem item : plugin.getShopBook().items()) {
                itemSlots.add(item.getSlot());
            }
            int[] skip = new int[itemSlots.size() + 2];
            for (int i = 0; i < itemSlots.size(); i++) {
                skip[i] = itemSlots.get(i);
            }
            skip[skip.length - 2] = 18;
            skip[skip.length - 1] = 26;
            fillFrame(inventory, skip);

            for (com.pokepass.shop.ShopItem item : plugin.getShopBook().items()) {
                inventory.setItem(item.getSlot(), renderItem(item));
            }

            inventory.setItem(18, GuiKit.item(Material.ARROW, 1, "&f返回", null));
            inventory.setItem(26, GuiKit.item(Material.BARRIER, 1, "&c关闭", null));
        }

        private ItemStack renderItem(com.pokepass.shop.ShopItem item) {
            List<String> lore = new ArrayList<String>(item.getLore());
            lore.add("&8&m                        ");

            // 商品内容照抄奖励描述，玩家不用猜「买了到底给什么」
            for (String line : plugin.getRewardService().describeAll(item.getRewards())) {
                lore.add("&7• " + line);
            }
            lore.add("&8&m                        ");

            if (item.getCost() == com.pokepass.shop.ShopItem.Cost.POINTS) {
                int balance = plugin.getEconomy().lookPoints(player);
                lore.add("&7价格 &e" + item.getPrice() + " &7点券 &8(你有 " + balance + ")");
            } else {
                long balance = (long) plugin.getEconomy().moneyBalance(player);
                lore.add("&7价格 &6" + item.getPrice() + " &7金币 &8(你有 " + balance + ")");
            }

            int remaining = plugin.getShopService().remaining(player, item);
            if (remaining < 0) {
                lore.add("&7限购 &f不限");
            } else if (remaining == 0) {
                lore.add("&c本赛季已兑换满 &8(" + item.getLimit() + ")");
            } else {
                lore.add("&7本赛季还可兑换 &f" + remaining + " &7次");
            }

            if (item.hasPermissionLimit() && !player.hasPermission(item.getPermission())) {
                lore.add("&c缺少权限 &8" + item.getPermission());
            } else if (remaining != 0) {
                lore.add("&e点击兑换");
            }

            ItemStack stack = GuiKit.item(item.getIcon(), 1, item.getData(), item.getName(), lore);
            if (remaining != 0 && (!item.hasPermissionLimit() || player.hasPermission(item.getPermission()))) {
                GuiKit.glow(stack);
            }
            return stack;
        }

        @Override
        public void handleClick(int slot, ClickType type) {
            if (slot == 18) {
                new MainMenu(plugin, player).open();
                return;
            }
            if (slot == 26) {
                player.closeInventory();
                return;
            }
            com.pokepass.shop.ShopItem item = itemAt(slot);
            if (item == null) {
                return;
            }
            com.pokepass.shop.ShopService.Result result = plugin.getShopService().purchase(player, item);
            if (result == com.pokepass.shop.ShopService.Result.OK) {
                player.sendMessage(plugin.getLang().msg("shop.bought", "&a已兑换: &f%item%")
                        .replace("%item%", item.getName()));
            }
            render();
        }

        private com.pokepass.shop.ShopItem itemAt(int slot) {
            for (com.pokepass.shop.ShopItem item : plugin.getShopBook().items()) {
                if (item.getSlot() == slot) {
                    return item;
                }
            }
            return null;
        }
    }
}
