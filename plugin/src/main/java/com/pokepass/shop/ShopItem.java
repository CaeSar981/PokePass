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
    /** 精灵头像图标（shop.yml 的 sprite 键）；null 表示用普通材质 */
    private final org.bukkit.inventory.ItemStack spriteIcon;

    public ShopItem(String id, Material icon, short data, String name, List<String> lore,
                    Cost cost, int price, String permission, int limit, int slot,
                    List<RewardEntry> rewards) {
        this(id, icon, data, name, lore, cost, price, permission, limit, slot, rewards, null);
    }

    public ShopItem(String id, Material icon, short data, String name, List<String> lore,
                    Cost cost, int price, String permission, int limit, int slot,
                    List<RewardEntry> rewards, org.bukkit.inventory.ItemStack spriteIcon) {
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
        this.spriteIcon = spriteIcon;
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

    /** 精灵头像图标；null 表示用普通材质渲染 */
    public org.bukkit.inventory.ItemStack getSpriteIcon() {
        return spriteIcon;
    }

    public List<RewardEntry> getRewards() {
        return rewards;
    }
}
