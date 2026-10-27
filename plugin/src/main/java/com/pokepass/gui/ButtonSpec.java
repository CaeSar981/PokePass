package com.pokepass.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * 一个按钮在界面里的完整定义：放哪、长什么样、叫什么、要不要显示。
 *
 * <p>由 {@link GuiConfig} 从 gui.yml 解析出来，没写的键回落到出厂默认值，
 * 所以 gui.yml 只需要写想改的那几行。</p>
 */
public final class ButtonSpec {

    private final String menu;
    private final String id;
    private final int slot;
    private final Material material;
    private final short data;
    private final String name;
    private final boolean enabled;
    /** 精灵头像物品（gui.yml 的 sprite 键）；null 表示用普通材质 */
    private final ItemStack sprite;

    public ButtonSpec(String menu, String id, int slot, Material material,
                      short data, String name, boolean enabled) {
        this(menu, id, slot, material, data, name, enabled, null);
    }

    public ButtonSpec(String menu, String id, int slot, Material material,
                      short data, String name, boolean enabled, ItemStack sprite) {
        this.menu = menu;
        this.id = id;
        this.slot = slot;
        this.material = material;
        this.data = data;
        this.name = name;
        this.enabled = enabled;
        this.sprite = sprite;
    }

    public String menu() {
        return menu;
    }

    public String id() {
        return id;
    }

    public int slot() {
        return slot;
    }

    public Material material() {
        return material;
    }

    public short data() {
        return data;
    }

    /** 精灵头像物品；null 表示用普通材质渲染 */
    public ItemStack sprite() {
        return sprite;
    }

    /** 未套占位符的原始名字，占位符由界面渲染时替换 */
    public String name() {
        return name;
    }

    public boolean enabled() {
        return enabled;
    }
}
