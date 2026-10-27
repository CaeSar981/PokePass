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
    /** 化石机复活出一只精灵（PixelmonReceivedEvent 里 receiveType == Fossil） */
    RECEIVED_FOSSIL("ReceivedFossil", false),

    /**
     * 跨插件联动：这两个不是 Bukkit 事件驱动的，由特训 / 打工插件在「领取成功」时
     * 用控制台身份调 {@code /zl progress <玩家> training|working 1} 推进。
     *
     * <p>走命令而不是自定义事件，是为了不引入编译期依赖 —— 装了才联动，没装也不会报错。</p>
     */
    TRAINING("TrainingFinish", false),
    WORKING("WorkFinish", false),

    MAX_LEVEL_POKEMON("MaxLevelPokemon", true),
    /**
     * 某只精灵升到 100 级的那一刻记一次（原版叫 PokemonMaxLevel）。
     *
     * <p>和 {@link #MAX_LEVEL_POKEMON} 不是一回事：那个数的是背包里满级精灵的数量，
     * 这个是「养成」动作，一只精灵一生只会触发一次，所以走事件而不是快照。</p>
     */
    POKEMON_MAX_LEVEL("PokemonMaxLevel", false),
    POKEDEX("Pokedex", true),
    /** 图鉴完成度百分比（0~100），amount 写目标百分比 */
    POKEDEX_PERCENT("PokedexPercent", true),
    /** 玩家本人等级（原版叫 LevelUp，语义是「人物等级达到多少」），快照型 */
    PLAYER_LEVEL("LevelUp", true),
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
        // 原版配置里这条写成 MaxLevelPokmon（少一个 e），搬老配置过来必须认；
        // MaxLevel 是玩家手写时最常用的简写
        if ("MaxLevelPokmon".equalsIgnoreCase(key) || "MaxLevel".equalsIgnoreCase(key)
                || "满级数量".equals(key)) {
            return MAX_LEVEL_POKEMON;
        }
        if ("PokemonMaxLevel".equalsIgnoreCase(key) || "精灵满级".equals(key)) {
            return POKEMON_MAX_LEVEL;
        }
        if ("PokedexPercent".equalsIgnoreCase(key) || "DexPercent".equalsIgnoreCase(key)
                || "PokerusSpread".equalsIgnoreCase(key) || "图鉴百分比".equals(key)) {
            return POKEDEX_PERCENT;
        }
        if ("Fossil".equalsIgnoreCase(key) || "ReceivedFossil".equalsIgnoreCase(key)
                || "化石".equals(key)) {
            return RECEIVED_FOSSIL;
        }
        if ("LevelUp".equalsIgnoreCase(key) || "PlayerLevel".equalsIgnoreCase(key)
                || "人物等级".equals(key)) {
            return PLAYER_LEVEL;
        }
        if ("Training".equalsIgnoreCase(key) || "Train".equalsIgnoreCase(key)
                || "特训".equals(key)) {
            return TRAINING;
        }
        if ("Working".equalsIgnoreCase(key) || "Work".equalsIgnoreCase(key)
                || "打工".equals(key)) {
            return WORKING;
        }
        return null;
    }
}
