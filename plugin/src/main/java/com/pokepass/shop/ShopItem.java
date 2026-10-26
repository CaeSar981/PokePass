package com.pokepass.shop;

import com.pokepass.reward.RewardEntry;
import org.bukkit.Material;

import java.util.List;

/**
 * 商店里的一条兑换项。
 *
 * <p>价格只有两种：点券（PlayerPoints）或金币（Vault）。发货复用战令奖励那套
 * {@link RewardEntry}，所以商店能卖的东西和奖励能发的东西完全一致，
 * 不需要再维护第二套发货逻辑。</p>
 */
public class ShopItem {

    public enum Cost {
        POINTS, MONEY, NONE
    }

    private final String id;
    private final Material icon;
    private final short data;
    private final String name;
    private final List<String> lore;
    private final Cost cost;
    private final int price;
    private final String permission;
    private final int limit;
    private final int slot;
    private final List<RewardEntry> rewards;

    public ShopItem(String id, Material icon, short data, String name, List<String> lore,
                    Cost cost, int price, String permission, int limit, int slot,
                    List<RewardEntry> rewards) {
        this.id = id;
        this.icon = icon;
        this.data = data;
        this.name = name;
        this.lore = lore;
        this.cost = cost;
        this.price = price;
        this.permission = permission;
        this.limit = limit;
        this.slot = slot;
        this.rewards = rewards;
    }

    public String getId() {
        return id;
    }

    public Material getIcon() {
        return icon;
    }

    public short getData() {
        return data;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public Cost getCost() {
        return cost;
    }

    public int getPrice() {
        return price;
    }

    /** 空字符串表示不需要权限 */
    public String getPermission() {
        return permission;
    }

    public boolean hasPermissionLimit() {
        return permission != null && !permission.trim().isEmpty();
    }

    /** 每赛季限购次数；0 表示不限 */
    public int getLimit() {
        return limit;
    }

    public int getSlot() {
        return slot;
    }

    public List<RewardEntry> getRewards() {
        return rewards;
    }
}
