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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 五个界面：主面板 / 每日任务 / 每周任务 / 奖励页 / 兑换商店。
 *
 * <p>全部实现 {@link InventoryHolder}，事件按 holder 类型分派，
 * 不靠标题字符串匹配（标题里有色码和变量，匹配容易翻车）。</p>
 *
 * <p>界面长相来自 gui.yml：按钮的槽位、材质、名字，底色填充物，
 * 装饰物，任务 / 奖励的内容槽位区都可改（见 {@link GuiConfig}）。
 * 代码只负责往配置给的格子里填动态内容。</p>
 */
public final class PassMenus {

    private PassMenus() {
    }

    /** 各界面的公共部分 */
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

        /** gui.yml 里的菜单键：main / daily / weekly / reward / shop */
        protected abstract String menuKey();

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

        /** 铺一层底色（gui.yml 的 filler 可换材质） */
        protected void fillFrame(Inventory inv, int... skip) {
            ItemStack pane = plugin.getGuiConfig().filler(menuKey());
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

        /** 把 gui.yml 的装饰物摆上去（名字 / lore 支持占位符）。
         *  extraTaken：调用方自己占用的槽位（如商店的商品位），装饰物不与它们重叠 */
        protected void renderDecor(int... extraTaken) {
            outer:
            for (GuiConfig.DecorItem decor : plugin.getGuiConfig().decor(menuKey())) {
                // 商店的界面尺寸来自 shop.yml，可能比 gui.yml 校验时用的默认值小
                if (decor.slot() < 0 || decor.slot() >= inventory.getSize()) {
                    continue;
                }
                for (int taken : extraTaken) {
                    if (taken == decor.slot()) {
                        continue outer;
                    }
                }
                ItemStack item = decor.template().clone();
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(GuiKit.color(applyVars(decor.name())));
                    if (!decor.lore().isEmpty()) {
                        List<String> colored = new ArrayList<String>();
                        for (String line : decor.lore()) {
                            colored.add(GuiKit.color(applyVars(line)));
                        }
                        meta.setLore(colored);
                    }
                    item.setItemMeta(meta);
                }
                if (decor.glow()) {
                    GuiKit.glow(item);
                }
                inventory.setItem(decor.slot(), item);
            }
        }

        /** 某个按钮要不要显示、放在哪、长什么样 */
        protected ButtonSpec button(String id) {
            return plugin.getGuiConfig().button(menuKey(), id);
        }

        /**
         * 按钮名字与装饰物上的占位符。全部基于打开界面的这个玩家实时取值，
         * 每次 render 都会重算，所以开着的界面 redraw 后数字是新的。
         */
        protected String applyVars(String raw) {
            if (raw == null || raw.indexOf('%') < 0) {
                return raw;
            }
            PlayerProgress progress = plugin.getProgressService().get(player);
            int level = progress.getLevel(plugin.getPassConfig());
            long now = System.currentTimeMillis();
            return raw
                    .replace("%player%", player.getName())
                    .replace("%level%", String.valueOf(level))
                    .replace("%max_level%", String.valueOf(plugin.getPassConfig().maxLevel()))
                    .replace("%exp_now%", String.valueOf(progress.expWithinLevel(plugin.getPassConfig())))
                    .replace("%exp_need%", String.valueOf(plugin.getPassConfig().expForLevel(level)))
                    .replace("%total_exp%", String.valueOf(progress.getTotalExp()))
                    .replace("%season%", String.valueOf(plugin.getPassConfig().seasonId(now)))
                    .replace("%days_left%", String.valueOf(plugin.getPassConfig().seasonDaysLeft(now)))
                    .replace("%claimable%", String.valueOf(plugin.getRewardService().claimable(player)))
                    .replace("%daily_done%", String.valueOf(doneCount(Quest.Period.DAILY)))
                    .replace("%daily_max%", String.valueOf(visibleCount(Quest.Period.DAILY)))
                    .replace("%weekly_done%", String.valueOf(doneCount(Quest.Period.WEEKLY)))
                    .replace("%weekly_max%", String.valueOf(visibleCount(Quest.Period.WEEKLY)))
                    .replace("%points%", String.valueOf(plugin.getEconomy().lookPoints(player)))
                    .replace("%next_page%", String.valueOf(
                            plugin.getQuestService().nextLockedPage(progress)))
                    .replace("%page_cost%", String.valueOf(
                            plugin.getPassConfig().weeklyPageCost()));
        }

        protected int doneCount(Quest.Period period) {
            int count = 0;
            for (Quest quest : plugin.getQuestService().visible(player,
                    plugin.getProgressService().get(player))) {
                if (quest.getPeriod() == period && plugin.getProgressService().get(player).isComplete(quest)) {
                    count++;
                }
            }
            return count;
        }

        protected int visibleCount(Quest.Period period) {
            int count = 0;
            for (Quest quest : plugin.getQuestService().visible(player,
                    plugin.getProgressService().get(player))) {
                if (quest.getPeriod() == period) {
                    count++;
                }
            }
            return count;
        }
    }

    // ================= 主面板 =================

    public static class MainMenu extends BaseMenu {

        public MainMenu(PokePassPlugin plugin, Player player) {
            super(plugin, player);
        }

        @Override
        protected String menuKey() {
            return "main";
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

            PlayerProgress progress = plugin.getProgressService().get(player);
            int level = progress.getLevel(plugin.getPassConfig());
            int inLevel = progress.expWithinLevel(plugin.getPassConfig());
            int need = plugin.getPassConfig().expForLevel(level);
            boolean maxed = level >= plugin.getPassConfig().maxLevel();

            ButtonSpec levelSpec = button("level");
            ButtonSpec seasonSpec = button("season");
            ButtonSpec dailySpec = button("daily");
            ButtonSpec weeklySpec = button("weekly");
            ButtonSpec rewardSpec = button("reward");
            ButtonSpec shopSpec = button("shop");
            ButtonSpec closeSpec = button("close");

            List<Integer> used = new ArrayList<Integer>();
            for (ButtonSpec spec : Arrays.asList(levelSpec, seasonSpec, dailySpec,
                    weeklySpec, rewardSpec, shopSpec, closeSpec)) {
                if (spec.enabled()) {
                    used.add(spec.slot());
                }
            }
            int[] skip = new int[used.size()];
            for (int i = 0; i < skip.length; i++) {
                skip[i] = used.get(i);
            }
            fillFrame(inventory, skip);

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
            if (levelSpec.enabled()) {
                ItemStack levelItem = GuiKit.item(levelSpec,                         applyVars(levelSpec.name()), levelLore);
                inventory.setItem(levelSpec.slot(), GuiKit.glow(levelItem));
            }

            // 赛季卡片：换季会清等级，得让玩家提前知道还剩几天
            long now = System.currentTimeMillis();
            boolean seasonOn = plugin.getPassConfig().seasonEnabled();
            int seasonId = plugin.getPassConfig().seasonId(now);
            int daysLeft = plugin.getPassConfig().seasonDaysLeft(now);
            if (seasonSpec.enabled()) {
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
                inventory.setItem(seasonSpec.slot(), GuiKit.item(seasonSpec, applyVars(seasonSpec.name()), seasonLore));
            }

            if (dailySpec.enabled()) {
                inventory.setItem(dailySpec.slot(), GuiKit.item(dailySpec,                         applyVars(dailySpec.name()), Arrays.asList(
                        "&8&m                        ",
                        "&7进度 &f" + doneCount(Quest.Period.DAILY) + "&7/&f" + visibleCount(Quest.Period.DAILY),
                        "&7每天 &f" + plugin.getPassConfig().dailyResetHour() + " &7点刷新",
                        "&8&m                        ",
                        "&e点击查看")));
            }

            if (weeklySpec.enabled()) {
                inventory.setItem(weeklySpec.slot(), GuiKit.item(weeklySpec,                         applyVars(weeklySpec.name()), Arrays.asList(
                        "&8&m                        ",
                        "&7进度 &f" + doneCount(Quest.Period.WEEKLY) + "&7/&f" + visibleCount(Quest.Period.WEEKLY),
                        "&7每周一刷新",
                        "&8&m                        ",
                        "&e点击查看")));
            }

            int claimable = plugin.getRewardService().claimable(player);
            if (rewardSpec.enabled()) {
                List<String> rewardLore = new ArrayList<String>();
                rewardLore.add("&8&m                        ");
                rewardLore.add("&7免费轨：等级到了就能领");
                rewardLore.add("&7高级轨：" + (plugin.getRewardService().hasPremium(player) ? "&a已解锁" : "&c未解锁"));
                rewardLore.add("&7可领取 &f" + claimable + " &7份");
                rewardLore.add("&8&m                        ");
                rewardLore.add("&e点击查看");
                ItemStack rewardItem = GuiKit.item(rewardSpec,                         applyVars(rewardSpec.name()), rewardLore);
                if (claimable > 0) {
                    GuiKit.glow(rewardItem);
                }
                inventory.setItem(rewardSpec.slot(), rewardItem);
            }

            // 商店：点券的消耗口。没配商品时不显示，免得玩家点进去看到空界面
            boolean shopVisible = shopSpec.enabled() && !plugin.getShopBook().items().isEmpty();
            if (shopVisible) {
                List<String> shopLore = new ArrayList<String>();
                shopLore.add("&8&m                        ");
                shopLore.add("&7用点券 / 金币兑换道具");
                shopLore.add("&7点券余额 &e" + plugin.getEconomy().lookPoints(player));
                shopLore.add("&8&m                        ");
                shopLore.add("&e点击进入");
                inventory.setItem(shopSpec.slot(), GuiKit.item(shopSpec,                         applyVars(shopSpec.name()), shopLore));
            }

            if (closeSpec.enabled()) {
                inventory.setItem(closeSpec.slot(), GuiKit.item(closeSpec,                         applyVars(closeSpec.name()), null));
            }

            renderDecor();
        }

        @Override
        public void handleClick(int slot, ClickType type) {
            ButtonSpec dailySpec = button("daily");
            ButtonSpec weeklySpec = button("weekly");
            ButtonSpec rewardSpec = button("reward");
            ButtonSpec shopSpec = button("shop");
            ButtonSpec closeSpec = button("close");

            if (dailySpec.enabled() && slot == dailySpec.slot()) {
                new QuestMenu(plugin, player, Quest.Period.DAILY).open();
            } else if (weeklySpec.enabled() && slot == weeklySpec.slot()) {
                new QuestMenu(plugin, player, Quest.Period.WEEKLY).open();
            } else if (rewardSpec.enabled() && slot == rewardSpec.slot()) {
                new RewardMenu(plugin, player).open();
            } else if (shopSpec.enabled() && slot == shopSpec.slot()) {
                if (!plugin.getShopBook().items().isEmpty()) {
                    new ShopMenu(plugin, player).open();
                }
            } else if (closeSpec.enabled() && slot == closeSpec.slot()) {
                player.closeInventory();
            }
        }
    }

    // ================= 任务页 =================

    public static class QuestMenu extends BaseMenu {

        private final Quest.Period period;
        private final int[] slots;
        private int page;

        public QuestMenu(PokePassPlugin plugin, Player player, Quest.Period period) {
            super(plugin, player);
            this.period = period;
            this.slots = plugin.getGuiConfig().content(period == Quest.Period.DAILY ? "daily" : "weekly");
        }

        @Override
        protected String menuKey() {
            return period == Quest.Period.DAILY ? "daily" : "weekly";
        }

        @Override
        protected String title() {
            return plugin.getGuiConfig().title(menuKey(),
                    period == Quest.Period.DAILY ? "&6&l战令 &8| &f每日任务" : "&6&l战令 &8| &f每周任务");
        }

        @Override
        protected int size() {
            return plugin.getGuiConfig().size(menuKey(), 54);
        }

        @Override
        public void render() {
            inventory.clear();

            ButtonSpec backSpec = button("back");
            ButtonSpec prevSpec = button("prev");
            ButtonSpec nextSpec = button("next");
            ButtonSpec closeSpec = button("close");

            PlayerProgress progress = plugin.getProgressService().get(player);
            // 周任务分页：只有周任务页才有「解锁」按钮，且有下一张待解锁的页时才显示
            ButtonSpec unlockSpec = period == Quest.Period.WEEKLY ? button("unlock") : null;
            int nextPage = unlockSpec != null && unlockSpec.enabled()
                    ? plugin.getQuestService().nextLockedPage(progress) : -1;

            List<Integer> used = new ArrayList<Integer>();
            for (int slot : slots) {
                used.add(slot);
            }
            if (nextPage > 0) {
                used.add(unlockSpec.slot());
            }
            for (ButtonSpec spec : Arrays.asList(backSpec, prevSpec, nextSpec, closeSpec)) {
                if (spec.enabled()) {
                    used.add(spec.slot());
                }
            }
            int[] skip = new int[used.size()];
            for (int i = 0; i < skip.length; i++) {
                skip[i] = used.get(i);
            }
            fillFrame(inventory, skip);

            List<Quest> quests = new ArrayList<Quest>();
            for (Quest quest : plugin.getQuestService().visible(player, progress)) {
                if (quest.getPeriod() == period) {
                    quests.add(quest);
                }
            }
            int perPage = slots.length;
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
                inventory.setItem(slots[i], renderQuest(quest, progress));
            }

            if (backSpec.enabled()) {
                inventory.setItem(backSpec.slot(), GuiKit.item(backSpec, applyVars(backSpec.name()), null));
            }
            if (prevSpec.enabled() && page > 0) {
                inventory.setItem(prevSpec.slot(), GuiKit.item(prevSpec, applyVars(prevSpec.name()), null));
            }
            if (nextSpec.enabled() && page < maxPage) {
                inventory.setItem(nextSpec.slot(), GuiKit.item(nextSpec, applyVars(nextSpec.name()), null));
            }
            if (closeSpec.enabled()) {
                inventory.setItem(closeSpec.slot(), GuiKit.item(closeSpec, applyVars(closeSpec.name()), null));
            }
            if (nextPage > 0) {
                inventory.setItem(unlockSpec.slot(),
                        GuiKit.glow(GuiKit.item(unlockSpec, applyVars(unlockSpec.name()),
                                unlockLore(nextPage))));
            }

            renderDecor(nextPage > 0 ? new int[]{unlockSpec.slot()} : new int[0]);
        }

        /** 解锁按钮的说明：买的是哪一页、多几条任务、要多少点券、身上有多少 */
        private List<String> unlockLore(int page) {
            int cost = plugin.getPassConfig().weeklyPageCost();
            int have = plugin.getEconomy().lookPoints(player);
            List<String> lore = new ArrayList<String>();
            lore.add(plugin.getLang().plain("weekly.unlock-lore-page", "&7解锁第 &f%page% &7页，多出 &f%count% &7条任务")
                    .replace("%page%", String.valueOf(page))
                    .replace("%count%", String.valueOf(plugin.getQuestService().pageSize(page))));
            if (cost > 0) {
                lore.add(plugin.getLang().plain("weekly.unlock-lore-cost", "&e花费 &f%cost% &e点券 &8(你有 %have%)")
                        .replace("%cost%", String.valueOf(cost))
                        .replace("%have%", String.valueOf(have)));
            }
            lore.add(plugin.getLang().plain("weekly.unlock-lore-season", "&7解锁本赛季有效，换季后重置"));
            return lore;
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
            ItemStack item;
            if (quest.getSpriteIcon() != null) {
                item = GuiKit.finish(quest.getSpriteIcon().clone(), quest.getName(), lore);
            } else {
                item = GuiKit.item(quest.getIcon(), 1, quest.getName(), lore);
            }
            if (complete) {
                GuiKit.glow(item);
            }
            return item;
        }

        /**
         * 扣点券解锁下一页。
         *
         * <p>顺序是「先扣钱再解锁」还是「先解锁再扣钱」都怕中途失败：
         * 扣钱成功但解锁失败 = 玩家白扣；解锁成功但扣钱失败 = 白送。
         * 这里选先扣钱：扣款接口本身是原子的，扣完立刻写内存再落库，
         * 落库失败只影响重启后（最坏是重复买一次，不会凭空多给内容）。</p>
         */
        private void unlockNextPage() {
            PlayerProgress progress = plugin.getProgressService().get(player);
            int page = plugin.getQuestService().nextLockedPage(progress);
            if (page < 0) {
                player.sendMessage(plugin.getLang().msg("weekly.all-unlocked", "&7已经没有可解锁的页了"));
                return;
            }
            int cost = plugin.getPassConfig().weeklyPageCost();
            if (cost > 0) {
                if (!plugin.getEconomy().pointsAvailable()) {
                    player.sendMessage(plugin.getLang().msg("weekly.no-points-system",
                            "&c本服没有点券系统，无法解锁"));
                    return;
                }
                int have = plugin.getEconomy().lookPoints(player);
                if (have < cost) {
                    player.sendMessage(plugin.getLang().msg("weekly.not-enough-points",
                            "&c点券不够，需要 &f%need%&c，你有 &f%have%")
                            .replace("%need%", String.valueOf(cost))
                            .replace("%have%", String.valueOf(have)));
                    return;
                }
                if (!plugin.getEconomy().takePoints(player, cost)) {
                    player.sendMessage(plugin.getLang().msg("weekly.not-enough-points",
                            "&c点券不够，需要 &f%need%&c，你有 &f%have%")
                            .replace("%need%", String.valueOf(cost))
                            .replace("%have%", String.valueOf(have)));
                    return;
                }
            }
            progress.unlockPage(page);
            plugin.getProgressService().store().save(progress);
            player.sendMessage(plugin.getLang().msg("weekly.unlocked",
                    "&a已解锁第 &f%page% &a页，本周多出 &f%count% &a条任务")
                    .replace("%page%", String.valueOf(page))
                    .replace("%count%", String.valueOf(plugin.getQuestService().pageSize(page))));
            render();
        }

        @Override
        public void handleClick(int slot, ClickType type) {
            ButtonSpec backSpec = button("back");
            ButtonSpec prevSpec = button("prev");
            ButtonSpec nextSpec = button("next");
            ButtonSpec closeSpec = button("close");

            if (backSpec.enabled() && slot == backSpec.slot()) {
                new MainMenu(plugin, player).open();
                return;
            }
            if (prevSpec.enabled() && slot == prevSpec.slot() && page > 0) {
                page--;
                render();
                return;
            }
            if (nextSpec.enabled() && slot == nextSpec.slot()) {
                page++;
                render();
                return;
            }
            if (closeSpec.enabled() && slot == closeSpec.slot()) {
                player.closeInventory();
                return;
            }
            if (period == Quest.Period.WEEKLY) {
                ButtonSpec unlockSpec = button("unlock");
                if (unlockSpec.enabled() && slot == unlockSpec.slot()) {
                    unlockNextPage();
                    return;
                }
            }
            // 内容区里的点击不影响状态，吞掉即可（MenuListener 已经 cancel 了）
        }
    }

    // ================= 奖励页 =================

    public static class RewardMenu extends BaseMenu {

        private final int[] slots;
        private int page;

        public RewardMenu(PokePassPlugin plugin, Player player) {
            super(plugin, player);
            this.slots = plugin.getGuiConfig().content("reward");
        }

        @Override
        protected String menuKey() {
            return "reward";
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

            ButtonSpec backSpec = button("back");
            ButtonSpec prevSpec = button("prev");
            ButtonSpec nextSpec = button("next");
            ButtonSpec claimSpec = button("claim-all");
            ButtonSpec closeSpec = button("close");

            List<Integer> used = new ArrayList<Integer>();
            for (int slot : slots) {
                used.add(slot);
            }
            for (ButtonSpec spec : Arrays.asList(backSpec, prevSpec, nextSpec, claimSpec, closeSpec)) {
                if (spec.enabled()) {
                    used.add(spec.slot());
                }
            }
            int[] skip = new int[used.size()];
            for (int i = 0; i < skip.length; i++) {
                skip[i] = used.get(i);
            }
            fillFrame(inventory, skip);

            PlayerProgress progress = plugin.getProgressService().get(player);
            int currentLevel = progress.getLevel(plugin.getPassConfig());
            boolean premium = plugin.getRewardService().hasPremium(player);
            int max = plugin.getRewardBook().maxLevelWithReward();
            int perPage = slots.length;
            int maxPage = Math.max(0, (max - 1) / perPage);
            if (page > maxPage) {
                page = maxPage;
            }
            for (int i = 0; i < perPage; i++) {
                int level = page * perPage + i + 1;
                if (level > max) {
                    break;
                }
                inventory.setItem(slots[i], renderLevel(level, progress, currentLevel, premium));
            }

            if (backSpec.enabled()) {
                inventory.setItem(backSpec.slot(), GuiKit.item(backSpec, applyVars(backSpec.name()), null));
            }
            if (prevSpec.enabled() && page > 0) {
                inventory.setItem(prevSpec.slot(), GuiKit.item(prevSpec, applyVars(prevSpec.name()), null));
            }
            if (nextSpec.enabled() && page < maxPage) {
                inventory.setItem(nextSpec.slot(), GuiKit.item(nextSpec, applyVars(nextSpec.name()), null));
            }
            int claimable = plugin.getRewardService().claimable(player);
            if (claimSpec.enabled() && claimable > 0) {
                inventory.setItem(claimSpec.slot(), GuiKit.glow(GuiKit.item(claimSpec, applyVars(claimSpec.name()) + " &7(" + claimable + ")",
                        Arrays.asList("&7把所有够等级的奖励一次领完"))));
            }
            if (closeSpec.enabled()) {
                inventory.setItem(closeSpec.slot(), GuiKit.item(closeSpec, applyVars(closeSpec.name()), null));
            }

            renderDecor();
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
            ButtonSpec backSpec = button("back");
            ButtonSpec prevSpec = button("prev");
            ButtonSpec nextSpec = button("next");
            ButtonSpec claimSpec = button("claim-all");
            ButtonSpec closeSpec = button("close");

            if (backSpec.enabled() && slot == backSpec.slot()) {
                new MainMenu(plugin, player).open();
                return;
            }
            if (prevSpec.enabled() && slot == prevSpec.slot() && page > 0) {
                page--;
                render();
                return;
            }
            if (nextSpec.enabled() && slot == nextSpec.slot()) {
                page++;
                render();
                return;
            }
            if (closeSpec.enabled() && slot == closeSpec.slot()) {
                player.closeInventory();
                return;
            }
            if (claimSpec.enabled() && slot == claimSpec.slot()) {
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
            int index = indexOf(slots, slot);
            if (index < 0) {
                return;
            }
            int max = plugin.getRewardBook().maxLevelWithReward();
            int maxPage = Math.max(0, (max - 1) / slots.length);
            if (page > maxPage) {
                page = maxPage;
            }
            int level = page * slots.length + index + 1;
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
     * 返回 / 关闭按钮与装饰物走 gui.yml 的 shop 段（标题与尺寸仍以 shop.yml 为准）。
     *
     * <p>余额、限购次数每次 render 都重新查：玩家可能在这个界面开着的时候
     * 从别处领到了点券，或者刚好被别人买走限购最后一份。</p>
     */
    public static class ShopMenu extends BaseMenu {

        public ShopMenu(PokePassPlugin plugin, Player player) {
            super(plugin, player);
        }

        @Override
        protected String menuKey() {
            return "shop";
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

            ButtonSpec backSpec = button("back");
            ButtonSpec closeSpec = button("close");

            List<Integer> used = new ArrayList<Integer>();
            for (com.pokepass.shop.ShopItem item : plugin.getShopBook().items()) {
                used.add(item.getSlot());
            }
            for (ButtonSpec spec : Arrays.asList(backSpec, closeSpec)) {
                if (spec.enabled() && spec.slot() < size()) {
                    used.add(spec.slot());
                }
            }
            int[] skip = new int[used.size()];
            for (int i = 0; i < skip.length; i++) {
                skip[i] = used.get(i);
            }
            fillFrame(inventory, skip);

            for (com.pokepass.shop.ShopItem item : plugin.getShopBook().items()) {
                inventory.setItem(item.getSlot(), renderItem(item));
            }

            if (backSpec.enabled() && backSpec.slot() < size()) {
                inventory.setItem(backSpec.slot(), GuiKit.item(backSpec, applyVars(backSpec.name()), null));
            }
            if (closeSpec.enabled() && closeSpec.slot() < size()) {
                inventory.setItem(closeSpec.slot(), GuiKit.item(closeSpec, applyVars(closeSpec.name()), null));
            }

            // 商品位是 shop.yml 定的，装饰物别把它们盖掉
            List<Integer> itemSlots = new ArrayList<Integer>();
            for (com.pokepass.shop.ShopItem item : plugin.getShopBook().items()) {
                itemSlots.add(item.getSlot());
            }
            int[] taken = new int[itemSlots.size()];
            for (int i = 0; i < taken.length; i++) {
                taken[i] = itemSlots.get(i);
            }
            renderDecor(taken);
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

            ItemStack stack;
            if (item.getSpriteIcon() != null) {
                stack = GuiKit.finish(item.getSpriteIcon().clone(), item.getName(), lore);
            } else {
                stack = GuiKit.item(item.getIcon(), 1, item.getData(), item.getName(), lore);
            }
            if (remaining != 0 && (!item.hasPermissionLimit() || player.hasPermission(item.getPermission()))) {
                GuiKit.glow(stack);
            }
            return stack;
        }

        @Override
        public void handleClick(int slot, ClickType type) {
            ButtonSpec backSpec = button("back");
            ButtonSpec closeSpec = button("close");

            if (backSpec.enabled() && slot == backSpec.slot() && backSpec.slot() < size()) {
                new MainMenu(plugin, player).open();
                return;
            }
            if (closeSpec.enabled() && slot == closeSpec.slot() && closeSpec.slot() < size()) {
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
