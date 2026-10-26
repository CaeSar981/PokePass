package com.pokepass.quest;

/**
 * 任务类型。
 *
 * <p>两类语义：</p>
 * <ul>
 *   <li><b>累积型</b>（{@link #isCheck} 为 false）：监听事件时把进度往上加，
 *       加到 amount 就算完成。经验、努力值、捕捉数量都走这条。</li>
 *   <li><b>快照型</b>（{@link #isCheck} 为 true）：不由事件推进，而是由定时任务
 *       每几秒扫一次玩家背包/图鉴，直接算出当前值。背包满级精灵数、图鉴收集数、
 *       持有物品这三类天生是「状态」而不是「动作」，用事件根本推不出来。</li>
 * </ul>
 */
public enum QuestType {

    CAPTURE("Capture", false),
    BATTLE_WIN("BattleWin", false),
    EVOLVE("Evolve", false),
    EGG_HATCH("EggHatch", false),
    EXPERIENCE_GAIN("ExperienceGain", false),
    EVS_GAINED("EVsGained", false),
    BLOCK_BREAK("BlockBreak", false),
    CRAFT("CraftItem", false),
    FISH("PlayerFish", false),
    PICKUP("PickupItem", false),
    ENCHANT("EnchantItem", false),
    CHAT("Chat", false),
    NPC_INTERACT("InteractEntity", false),
    TRADE("PixelmonTrade", false),

    MAX_LEVEL_POKEMON("MaxLevelPokemon", true),
    POKEDEX("Pokedex", true),
    CHECK_ITEM("CheckItem", true),
    DELETE_ITEM("DeleteItem", false);

    private final String configName;
    private final boolean check;

    QuestType(String configName, boolean check) {
        this.configName = configName;
        this.check = check;
    }

    /** 配置文件里写的名字 */
    public String configName() {
        return configName;
    }

    /** 快照型：进度由定时扫描算出，不累加 */
    public boolean isCheck() {
        return check;
    }

    /** 大小写不敏感查找，也接受简写 */
    public static QuestType of(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim();
        for (QuestType type : values()) {
            if (type.configName.equalsIgnoreCase(key) || type.name().equalsIgnoreCase(key)) {
                return type;
            }
        }
        if ("Fish".equalsIgnoreCase(key) || "PlayerFish".equalsIgnoreCase(key)) {
            return FISH;
        }
        if ("Evolve".equalsIgnoreCase(key) || "Evolution".equalsIgnoreCase(key)) {
            return EVOLVE;
        }
        if ("Trade".equalsIgnoreCase(key) || "PixelmonTrade".equalsIgnoreCase(key)) {
            return TRADE;
        }
        if ("InteractEntity".equalsIgnoreCase(key) || "NPC".equalsIgnoreCase(key)) {
            return NPC_INTERACT;
        }
        if ("Pokedex".equalsIgnoreCase(key) || "Dex".equalsIgnoreCase(key)) {
            return POKEDEX;
        }
        return null;
    }
}
