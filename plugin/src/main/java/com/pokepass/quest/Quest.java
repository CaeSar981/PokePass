package com.pokepass.quest;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 一条战令任务。
 *
 * <p>配置里一条任务长这样：</p>
 * <pre>
 * d1:
 *   name: "&b捕捉 &f咚咚鼠"
 *   type: Capture          # 见 QuestType
 *   amount: 3              # 累积型是「要做几次 / 要累计多少」，快照型是「要达到多少」
 *   condition: Dedenne     # 逗号分隔可写多个，* 表示不限
 *   exp: 800               # 完成时给多少战令经验
 *   icon: PAPER
 *   lore: [...]
 * </pre>
 */
public class Quest {

    public enum Period {
        DAILY, WEEKLY
    }

    private final String id;
    private final Period period;
    private final String name;
    private final QuestType type;
    private final int amount;
    private final List<String> conditions;
    private final int exp;
    private final Material icon;
    private final short data;
    private final List<String> lore;
    /** 精灵头像图标（任务 yml 的 sprite 键）；null 表示用普通材质 */
    private final org.bukkit.inventory.ItemStack spriteIcon;

    public Quest(String id, Period period, String name, QuestType type, int amount,
                 List<String> conditions, int exp, Material icon, short data, List<String> lore) {
        this(id, period, name, type, amount, conditions, exp, icon, data, lore, null);
    }

    public Quest(String id, Period period, String name, QuestType type, int amount,
                 List<String> conditions, int exp, Material icon, short data, List<String> lore,
                 org.bukkit.inventory.ItemStack spriteIcon) {
        this.id = id;
        this.period = period;
        this.name = name;
        this.type = type;
        this.amount = Math.max(1, amount);
        this.conditions = conditions;
        this.exp = Math.max(0, exp);
        this.icon = icon;
        this.data = data;
        this.lore = lore;
        this.spriteIcon = spriteIcon;
    }

    public String getId() {
        return id;
    }

    public Period getPeriod() {
        return period;
    }

    public String getName() {
        return name;
    }

    public QuestType getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    public int getExp() {
        return exp;
    }

    public Material getIcon() {
        return icon;
    }

    public short getData() {
        return data;
    }

    /** 精灵头像图标；null 表示用普通材质渲染 */
    public org.bukkit.inventory.ItemStack getSpriteIcon() {
        return spriteIcon;
    }

    public List<String> getLore() {
        return lore;
    }

    public List<String> getConditions() {
        return conditions;
    }

    /** 条件是否是「不限」 */
    public boolean isWildcard() {
        return conditions.isEmpty() || conditions.contains("*");
    }

    /**
     * 目标是否匹配本次事件。
     *
     * <p>比较是大小写不敏感的：配置里写 {@code Dedenne} 还是 {@code dedenne}
     * 都能命中，中文名也能直接写。</p>
     */
    public boolean matches(String value) {
        if (isWildcard()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        for (String condition : conditions) {
            if (condition.trim().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    /** 把 "A,B,C" 拆成列表；空或 * 视为不限 */
    public static List<String> parseConditions(String raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        for (String part : Arrays.asList(raw.split(","))) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
