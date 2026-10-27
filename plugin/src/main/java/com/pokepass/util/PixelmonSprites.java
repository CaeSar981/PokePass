package com.pokepass.util;

import com.pixelmonmod.pixelmon.config.PixelmonItems;
import com.pixelmonmod.pixelmon.enums.EnumSpecies;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 精灵头像物品（Pixelmon 自带的 pixelmon_sprite）。
 *
 * <p>这个物品在客户端被 {@code ItemPixelmonSpriteModel} 渲染成对应精灵的照片，
 * Pixelmon 自己的图鉴 / 界面用的就是它。NBT 只需要三个键：
 * {@code ndex}（图鉴号）、{@code form}（形态编号）、{@code Shiny}（是否闪光），
 * 我们直接拼 NBT，不用走 {@code getPhoto(Pokemon)}（那要先造一只完整 Pokemon）。</p>
 *
 * <p>NMS 类型不在编译期 classpath 里（CatServer universal 只带 Bukkit/CB API），
 * 所以这一整段用反射。任何一步失败都返回 null 并把原因记进 warnings，
 * 上层自动退回普通材质图标 —— 没装 Pixelmon 或版本变了也不会炸。</p>
 */
public final class PixelmonSprites {

    private PixelmonSprites() {
    }

    /** 解析 "皮卡丘" / "pikachu" / "25"（图鉴号），不认识返回 null */
    public static EnumSpecies parseSpecies(String text) {
        if (text == null) {
            return null;
        }
        text = text.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.matches("\\d{1,4}")) {
            int dex = Integer.parseInt(text);
            if (dex < 1 || dex > 1025) {
                return null;
            }
            try {
                return EnumSpecies.getFromDex(dex);
            } catch (Exception e) {
                return null;
            }
        }
        try {
            return EnumSpecies.getFromNameAnyCase(text);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按精灵字符串创建头像物品。
     *
     * @param speciesText 精灵名（任意大小写）或图鉴号
     * @param form        形态编号，普通精灵写 0
     * @param shiny       是否用闪光配色
     * @param warnings    失败原因写进这里
     * @param label       warning 里标明出处，如 "gui.yml: main.buttons.level"
     * @return 失败返回 null，上层退回普通材质图标
     */
    public static ItemStack create(String speciesText, int form, boolean shiny,
                                   List<String> warnings, String label) {
        EnumSpecies species = parseSpecies(speciesText);
        if (species == null) {
            warnings.add(label + ": 不认识的精灵 \"" + speciesText + "\"，已退回普通材质图标");
            return null;
        }
        try {
            Object item = PixelmonItems.class.getField("itemPixelmonSprite").get(null);
            Class<?> nmsStack = Class.forName("net.minecraft.item.ItemStack");
            Class<?> nbtTag = Class.forName("net.minecraft.nbt.NBTTagCompound");

            Object stack = nmsStack.getConstructor(Class.forName("net.minecraft.item.Item"))
                    .newInstance(item);
            Object tag = nbtTag.newInstance();
            // CatServer 运行时的 NMS 方法是 SRG 名（func_xxxxx），少数被 CatServer
            // 补了人类可读名 —— 两种都试，谁在用谁
            set(nbtTag, tag, new String[]{"setShort", "func_74771_c"},
                    new Class<?>[]{String.class, short.class},
                    "ndex", (short) species.getNationalPokedexInteger());
            set(nbtTag, tag, new String[]{"setByte", "func_74774_a"},
                    new Class<?>[]{String.class, byte.class},
                    "form", (byte) form);
            set(nbtTag, tag, new String[]{"setBoolean", "func_74757_a"},
                    new Class<?>[]{String.class, boolean.class},
                    "Shiny", shiny);
            set(nmsStack, stack, new String[]{"setTagCompound", "func_77982_d"},
                    new Class<?>[]{nbtTag}, tag);

            Method asBukkitCopy = CraftItemStack.class.getMethod("asBukkitCopy", nmsStack);
            return (ItemStack) asBukkitCopy.invoke(null, stack);
        } catch (Exception e) {
            warnings.add(label + ": 精灵图标创建失败（" + e + "），已退回普通材质图标");
            return null;
        }
    }

    /** 按 names 顺序找方法调用（人类可读名优先，找不到退 SRG 名） */
    private static void set(Class<?> declaring, Object target, String[] names,
                            Class<?>[] sig, Object... args) throws Exception {
        for (String name : names) {
            try {
                declaring.getMethod(name, sig).invoke(target, args);
                return;
            } catch (NoSuchMethodException ignored) {
                // 试下一个名字
            }
        }
        throw new NoSuchMethodException("这几个名字都找不到: " + String.join("/", names));
    }
}
