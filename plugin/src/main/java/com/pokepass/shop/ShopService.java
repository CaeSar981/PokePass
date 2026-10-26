package com.pokepass.shop;

import com.pokepass.PokePassPlugin;
import com.pokepass.data.PlayerProgress;
import com.pokepass.reward.RewardEntry;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 兑换商店的购买逻辑。
 *
 * <p>这是点券的<b>消耗口</b>：战令发点券、商店收点券，经济才闭环。以前点券只进不出，
 * 玩家攒满就没动力了。</p>
 *
 * <p>顺序是「资格 -> 预检 -> 扣款 -> 发货 -> 记次」，扣款成功但发货失败时不会退款
 * （退款比丢奖励更难写对：得考虑库存满、命令失败），改为写日志留痕给管理员补发。
 * 所以预检必须严：能静态判断的失败原因全部前置拦住。</p>
 */
public class ShopService {

    public enum Result {
        OK,
        NO_PERMISSION,
        LIMIT_REACHED,
        NOT_ENOUGH_POINTS,
        NOT_ENOUGH_MONEY,
        /** 点券/经济前置没装，或奖励本身发不出去 */
        UNAVAILABLE,
        NOTHING_TO_BUY
    }

    private final PokePassPlugin plugin;

    public ShopService(PokePassPlugin plugin) {
        this.plugin = plugin;
    }

    /** 本赛季还能买几次；-1 表示不限 */
    public int remaining(Player player, ShopItem item) {
        if (item.getLimit() <= 0) {
            return -1;
        }
        PlayerProgress progress = plugin.getProgressService().get(player);
        return Math.max(0, item.getLimit() - progress.purchasesOf(item.getId()));
    }

    public Result purchase(Player player, ShopItem item) {
        if (item == null) {
            return Result.NOTHING_TO_BUY;
        }
        if (item.hasPermissionLimit() && !player.hasPermission(item.getPermission())) {
            player.sendMessage(plugin.getLang().msg("shop.no-permission", "&c你没有兑换这个的权限"));
            return Result.NO_PERMISSION;
        }
        int remaining = remaining(player, item);
        if (remaining == 0) {
            player.sendMessage(plugin.getLang().msg("shop.limit", "&c本赛季已经兑换满了 (&f%limit%&c 次)")
                    .replace("%limit%", String.valueOf(item.getLimit())));
            return Result.LIMIT_REACHED;
        }

        List<RewardEntry> rewards = item.getRewards();
        String blocked = plugin.getRewardService().whyUnavailable(rewards);
        if (blocked != null) {
            player.sendMessage(plugin.getLang().msg("shop.unavailable", "&c现在兑换不了: %reason%")
                    .replace("%reason%", blocked));
            return Result.UNAVAILABLE;
        }

        if (!charge(player, item)) {
            return item.getCost() == ShopItem.Cost.POINTS
                    ? Result.NOT_ENOUGH_POINTS : Result.NOT_ENOUGH_MONEY;
        }

        List<RewardEntry> failed = plugin.getRewardService().give(player, rewards);
        PlayerProgress progress = plugin.getProgressService().get(player);
        progress.recordPurchase(item.getId());
        plugin.getProgressService().store().save(progress);

        if (!failed.isEmpty()) {
            plugin.getLogger().warning("玩家 " + player.getName() + " 在商店兑换 " + item.getId()
                    + " 时，以下条目发放失败，需人工补发（点券已扣）：");
            for (RewardEntry entry : failed) {
                plugin.getLogger().warning("  - " + entry.getRaw());
            }
            player.sendMessage(plugin.getLang().msg("shop.partial",
                    "&e有 %count% 项没发成功，已记录，请联系管理员补发")
                    .replace("%count%", String.valueOf(failed.size())));
        }
        return Result.OK;
    }

    /** 扣款。余额不足时给玩家提示，返回 false 让调用方别发货 */
    private boolean charge(Player player, ShopItem item) {
        if (item.getCost() == ShopItem.Cost.POINTS) {
            if (!plugin.getEconomy().pointsAvailable()) {
                player.sendMessage(plugin.getLang().msg("shop.no-points-system",
                        "&c本服没有点券系统，暂时无法兑换"));
                return false;
            }
            int balance = plugin.getEconomy().lookPoints(player);
            if (balance < item.getPrice()) {
                player.sendMessage(plugin.getLang().msg("shop.not-enough-points",
                                "&c点券不够，需要 &f%need%&c，你有 &f%have%")
                        .replace("%need%", String.valueOf(item.getPrice()))
                        .replace("%have%", String.valueOf(balance)));
                return false;
            }
            if (!plugin.getEconomy().takePoints(player, item.getPrice())) {
                player.sendMessage(plugin.getLang().msg("shop.not-enough-points",
                                "&c点券不够，需要 &f%need%&c，你有 &f%have%")
                        .replace("%need%", String.valueOf(item.getPrice()))
                        .replace("%have%", String.valueOf(balance)));
                return false;
            }
            return true;
        }

        if (item.getCost() == ShopItem.Cost.MONEY) {
            if (!plugin.getEconomy().vaultAvailable()) {
                player.sendMessage(plugin.getLang().msg("shop.no-money-system",
                        "&c本服没有经济系统，暂时无法兑换"));
                return false;
            }
            double balance = plugin.getEconomy().moneyBalance(player);
            if (balance < item.getPrice()) {
                player.sendMessage(plugin.getLang().msg("shop.not-enough-money",
                                "&c金币不够，需要 &f%need%&c，你有 &f%have%")
                        .replace("%need%", String.valueOf(item.getPrice()))
                        .replace("%have%", String.valueOf((long) balance)));
                return false;
            }
            if (!plugin.getEconomy().takeMoney(player, item.getPrice())) {
                player.sendMessage(plugin.getLang().msg("shop.not-enough-money",
                                "&c金币不够，需要 &f%need%&c，你有 &f%have%")
                        .replace("%need%", String.valueOf(item.getPrice()))
                        .replace("%have%", String.valueOf((long) balance)));
                return false;
            }
            return true;
        }

        player.sendMessage(plugin.getLang().msg("shop.unavailable", "&c现在兑换不了: %reason%")
                .replace("%reason%", "价格配置无效"));
        return false;
    }
}
