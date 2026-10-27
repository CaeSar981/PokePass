package com.pokepass.gui;

import com.pokepass.config.Configs;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * gui.yml 的解析层：界面的标题、尺寸、底色、按钮、装饰物、内容区全部可配。
 *
 * <p>设计原则：gui.yml 里没写的键一律回落到出厂默认值，默认值就是这版界面的
 * 实际长相 —— 所以老配置不用动、新配置只写想改的那几行。写错的项不炸插件，
 * 记进 {@link #warnings()} 由启动自检暴露出来，同时退回默认值。</p>
 *
 * <p>可自定义的内容：
 * <ul>
 *   <li>标题与尺寸（原有）</li>
 *   <li>{@code filler}：铺底的填充物材质</li>
 *   <li>{@code buttons.<id>}：每个按钮的槽位 / 材质 / 数据值 / 名字 / 开关，
 *       名字支持 %level% 之类的占位符（见各界面渲染处的说明）</li>
 *   <li>{@code decor}：任意槽位摆纯装饰物品，支持 lore 和发光</li>
 *   <li>{@code content}：任务页 / 奖励页里「内容物品」占用的槽位区，
 *       改了它，分页会自动按新格数计算</li>
 * </ul></p>
 */
public class GuiConfig {

    /** 出厂默认的按钮表：菜单 -> 按钮 id -> 定义 */
    private static final Map<String, Map<String, Def>> DEFAULTS = new LinkedHashMap<String, Map<String, Def>>();

    private static final class Def {
        final int slot;
        final String material;
        final short data;
        final String name;

        Def(int slot, String material, short data, String name) {
            this.slot = slot;
            this.material = material;
            this.data = data;
            this.name = name;
        }
    }

    private static void def(String menu, String id, int slot, String material, short data, String name) {
        Map<String, Def> buttons = DEFAULTS.get(menu);
        if (buttons == null) {
            buttons = new LinkedHashMap<String, Def>();
            DEFAULTS.put(menu, buttons);
        }
        buttons.put(id, new Def(slot, material, data, name));
    }

    static {
        def("main", "level", 13, "NETHER_STAR", (short) 0, "&6&l战令等级 &f%level%");
        def("main", "season", 20, "WATCH", (short) 0, "&e&l赛季 &f#%season%");
        def("main", "daily", 11, "BOOK", (short) 0, "&b&l每日任务");
        def("main", "weekly", 15, "ENCHANTED_BOOK", (short) 0, "&d&l每周任务");
        def("main", "reward", 22, "CHEST", (short) 0, "&6&l等级奖励");
        def("main", "shop", 24, "EMERALD", (short) 0, "&a&l兑换商店");
        def("main", "close", 26, "BARRIER", (short) 0, "&c关闭");

        for (String menu : new String[]{"daily", "weekly", "reward"}) {
            def(menu, "back", 45, "ARROW", (short) 0, "&f返回");
            def(menu, "prev", 48, "SPECTRAL_ARROW", (short) 0, "&a上一页");
            def(menu, "next", 50, "SPECTRAL_ARROW", (short) 0, "&a下一页");
            def(menu, "close", 53, "BARRIER", (short) 0, "&c关闭");
        }
        def("reward", "claim-all", 49, "ENDER_CHEST", (short) 0, "&a&l一键领取");

        def("shop", "back", 18, "ARROW", (short) 0, "&f返回");
        def("shop", "close", 26, "BARRIER", (short) 0, "&c关闭");
    }

    /** 任务页 / 奖励页的内容区默认槽位 */
    private static final String DEFAULT_CONTENT = "10-16,19-25";

    private final JavaPlugin plugin;
    private volatile org.bukkit.configuration.file.YamlConfiguration config;
    private final List<String> warnings = new ArrayList<String>();
    /** 每次重载后缓存的菜单尺寸：size() 在渲染热路径上每帧都会被调，
     * 不缓存的话写错的 size 会把同一条 warning 每帧刷一遍 */
    private final Map<String, Integer> sizes = new java.util.HashMap<String, Integer>();

    public GuiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        warnings.clear();
        sizes.clear();
        this.config = Configs.load(plugin, "gui.yml");
        validate();
    }

    // ------------------------------------------------------------------
    //  基础项
    // ------------------------------------------------------------------

    public String title(String key, String fallback) {
        return config.getString(key + ".title", fallback);
    }

    public int size(String key, int fallback) {
        Integer cached = sizes.get(key);
        if (cached != null) {
            return cached;
        }
        int size = config.getInt(key + ".size", fallback);
        if (size < 9 || size > 54 || size % 9 != 0) {
            warnings.add("gui.yml: " + key + ".size 必须是 9~54 且为 9 的倍数，现用 " + size
                    + "，已退回 " + fallback);
            size = fallback;
        }
        sizes.put(key, size);
        return size;
    }

    /** 铺底填充物，写法 "材质" 或 "材质:数据值" */
    public ItemStack filler(String menu) {
        String raw = config.getString(menu + ".filler", "STAINED_GLASS_PANE:7");
        ItemStack item = parseItem(raw, menu + ".filler", 0);
        if (item == null) {
            item = GuiKit.item(Material.STAINED_GLASS_PANE, 1, (short) 7, " ", null);
        }
        return item;
    }

    // ------------------------------------------------------------------
    //  按钮
    // ------------------------------------------------------------------

    /**
     * 读一个按钮的定义。menu 形如 "main"，id 形如 "daily"。
     * 槽位越界、材质写错都退回默认值并记 warning。
     */
    public ButtonSpec button(String menu, String id) {
        Def def = DEFAULTS.get(menu).get(id);
        int size = size(menu, 54);
        String path = menu + ".buttons." + id;

        boolean enabled = config.getBoolean(path + ".enabled", true);
        // sprite：精灵头像（Pixelmon 的 pixelmon_sprite），创建失败会记 warning 并退回普通材质
        ItemStack sprite = null;
        String spriteText = config.getString(path + ".sprite", "");
        if (!spriteText.trim().isEmpty()) {
            sprite = com.pokepass.util.PixelmonSprites.create(spriteText,
                    config.getInt(path + ".form", 0),
                    config.getBoolean(path + ".shiny", false),
                    warnings, "gui.yml: " + path);
        }

        if (!enabled) {
            // 关掉的按钮仍返回默认定义，调用方按 enabled() 跳过渲染与点击
            return new ButtonSpec(menu, id, def.slot, parseMaterial(def.material, path),
                    def.data, config.getString(path + ".name", def.name), false, sprite);
        }

        int slot = def.slot;
        if (config.contains(path + ".slot")) {
            slot = config.getInt(path + ".slot");
            if (slot < 0 || slot >= size) {
                warnings.add("gui.yml: " + path + ".slot=" + slot
                        + " 超出界面范围（0~" + (size - 1) + "），已退回默认槽位 " + def.slot);
                slot = def.slot;
            }
        }
        // 默认槽位也可能被改小的界面挤出去
        if (slot < 0 || slot >= size) {
            warnings.add("gui.yml: " + menu + ".size=" + size
                    + " 放不下按钮 " + id + "（默认槽位 " + def.slot + "），该按钮被隐藏");
            return new ButtonSpec(menu, id, slot, parseMaterial(def.material, path),
                    def.data, config.getString(path + ".name", def.name), false, sprite);
        }

        String materialName = config.getString(path + ".material", def.material);
        short data = (short) config.getInt(path + ".data", def.data);
        return new ButtonSpec(menu, id, slot, parseMaterial(materialName, path),
                data, config.getString(path + ".name", def.name), true, sprite);
    }

    // ------------------------------------------------------------------
    //  内容区（任务 / 奖励列表占用的槽位）
    // ------------------------------------------------------------------

    /** 支持 "10-16,19-25" 这种写法；非法项剔除，全非法则退回默认 */
    public int[] content(String menu) {
        int size = size(menu, 54);
        String raw = config.getString(menu + ".content", DEFAULT_CONTENT);
        List<Integer> slots = GuiKit.slots(raw);
        List<Integer> ok = new ArrayList<Integer>();
        for (Integer slot : slots) {
            if (slot < 0 || slot >= size) {
                warnings.add("gui.yml: " + menu + ".content 里的槽位 " + slot
                        + " 超出界面范围，已剔除");
            } else {
                ok.add(slot);
            }
        }
        if (ok.isEmpty()) {
            warnings.add("gui.yml: " + menu + ".content 没有可用槽位，已退回默认 " + DEFAULT_CONTENT);
            ok = GuiKit.slots(DEFAULT_CONTENT);
        }
        // 去重保序
        Set<Integer> seen = new LinkedHashSet<Integer>(ok);
        int[] out = new int[seen.size()];
        int i = 0;
        for (Integer slot : seen) {
            out[i++] = slot;
        }
        return out;
    }

    // ------------------------------------------------------------------
    //  装饰物
    // ------------------------------------------------------------------

    /**
     * 纯装饰物品列表。名字 / lore 支持 %level% 之类占位符（界面渲染时替换）。
     * 与按钮、内容区撞槽位的装饰会被跳过并记 warning。
     */
    public List<DecorItem> decor(String menu) {
        List<DecorItem> out = new ArrayList<DecorItem>();
        int size = size(menu, 54);
        List<Map<?, ?>> raw = config.getMapList(menu + ".decor");
        if (raw.isEmpty()) {
            return out;
        }

        // 收集这个界面里已经被功能占用的槽位：按钮要用解析后的实际槽位
        // （服主可能把按钮挪了地方），内容区只对声明它的三个列表页生效
        Set<Integer> taken = new LinkedHashSet<Integer>();
        for (String id : DEFAULTS.get(menu).keySet()) {
            ButtonSpec spec = button(menu, id);
            if (spec.enabled() && spec.slot() >= 0 && spec.slot() < size) {
                taken.add(spec.slot());
            }
        }
        // 内容区只对真正渲染它的三个列表页生效 —— 主菜单/商店不渲染内容，
        // 不把它们算进占用，否则服主在主菜单摆装饰会被默认内容区误判撞位
        if (menu.equals("daily") || menu.equals("weekly") || menu.equals("reward")) {
            for (int slot : content(menu)) {
                taken.add(slot);
            }
        }

        int index = 0;
        for (Map<?, ?> entry : raw) {
            String path = menu + ".decor[" + index++ + "]";
            int slot = asInt(entry.get("slot"), -1);
            if (slot < 0 || slot >= size) {
                warnings.add("gui.yml: " + path + ".slot 无效（" + slot + "），已跳过");
                continue;
            }
            if (taken.contains(slot)) {
                warnings.add("gui.yml: " + path + " 与按钮/内容区撞槽位（" + slot + "），已跳过");
                continue;
            }
            // sprite：精灵头像优先；创建失败（精灵名不认识 / 反射失败）退回 material
            ItemStack item = null;
            Object spriteText = entry.get("sprite");
            if (spriteText != null && !String.valueOf(spriteText).trim().isEmpty()) {
                item = com.pokepass.util.PixelmonSprites.create(String.valueOf(spriteText),
                        asInt(entry.get("form"), 0),
                        Boolean.TRUE.equals(entry.get("shiny")),
                        warnings, "gui.yml: " + path);
            }
            if (item == null) {
                item = parseItem(String.valueOf(entry.get("material")),
                        path + ".material", asInt(entry.get("data"), 0));
            }
            if (item == null) {
                continue;
            }
            taken.add(slot);
            String name = entry.get("name") == null ? " " : String.valueOf(entry.get("name"));
            List<String> lore = new ArrayList<String>();
            if (entry.get("lore") instanceof List) {
                for (Object line : (List<?>) entry.get("lore")) {
                    lore.add(String.valueOf(line));
                }
            }
            out.add(new DecorItem(slot, item, name, lore,
                    Boolean.TRUE.equals(entry.get("glow"))));
        }
        return out;
    }

    /** 装饰物：槽位定死后，物品由界面按当前玩家的占位符重新生成 */
    public static final class DecorItem {
        private final int slot;
        private final ItemStack template;
        private final String name;
        private final List<String> lore;
        private final boolean glow;

        DecorItem(int slot, ItemStack template, String name, List<String> lore, boolean glow) {
            this.slot = slot;
            this.template = template;
            this.name = name;
            this.lore = lore;
            this.glow = glow;
        }

        public int slot() {
            return slot;
        }

        public ItemStack template() {
            return template;
        }

        public String name() {
            return name;
        }

        public List<String> lore() {
            return lore;
        }

        public boolean glow() {
            return glow;
        }
    }

    // ------------------------------------------------------------------
    //  解析小工具
    // ------------------------------------------------------------------

    private Material parseMaterial(String raw, String path) {
        Material material = match(raw);
        if (material == null) {
            warnings.add("gui.yml: " + path + " 的材质名无法识别: " + raw);
        }
        return material;
    }

    private ItemStack parseItem(String raw, String path, int data) {
        if (raw == null || raw.trim().isEmpty()) {
            warnings.add("gui.yml: " + path + " 缺少材质");
            return null;
        }
        String name = raw.trim();
        String mat = name;
        short dataValue = (short) data;
        int colon = name.indexOf(':');
        if (colon > 0) {
            mat = name.substring(0, colon).trim();
            try {
                dataValue = Short.parseShort(name.substring(colon + 1).trim());
            } catch (NumberFormatException e) {
                warnings.add("gui.yml: " + path + " 的数据值无效: " + name);
            }
        }
        Material material = match(mat);
        if (material == null) {
            warnings.add("gui.yml: " + path + " 的材质名无法识别: " + mat);
            return null;
        }
        return GuiKit.item(material, 1, dataValue, " ", null);
    }

    private Material match(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int asInt(Object raw, int fallback) {
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(String.valueOf(raw).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------------
    //  校验与自检
    // ------------------------------------------------------------------

    /**
     * 重载时把所有已知按钮都读一遍：既提前把槽位越界 / 材质写错暴露成 warning，
     * 也让自检不用等玩家打开界面就能发现问题。
     */
    private void validate() {
        for (String menu : DEFAULTS.keySet()) {
            size(menu, menu.equals("shop") ? 27 : 54);
            for (String id : DEFAULTS.get(menu).keySet()) {
                button(menu, id);
            }
            if (config.contains(menu + ".content")) {
                content(menu);
            }
            decor(menu);
        }
        // 撞槽位检查：同一菜单内按钮之间不能重叠
        for (String menu : DEFAULTS.keySet()) {
            Map<Integer, String> bySlot = new LinkedHashMap<Integer, String>();
            for (Map.Entry<String, Def> e : DEFAULTS.get(menu).entrySet()) {
                ButtonSpec spec = button(menu, e.getKey());
                if (spec.enabled() && bySlot.containsKey(spec.slot())) {
                    warnings.add("gui.yml: " + menu + " 的按钮 " + e.getKey() + " 与 "
                            + bySlot.get(spec.slot()) + " 撞了槽位 " + spec.slot());
                } else if (spec.enabled()) {
                    bySlot.put(spec.slot(), e.getKey());
                }
            }
        }
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }
}
