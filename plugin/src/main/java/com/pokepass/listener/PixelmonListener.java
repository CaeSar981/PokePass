package com.pokepass.listener;

import com.pixelmonmod.pixelmon.api.events.CaptureEvent;
import com.pixelmonmod.pixelmon.api.events.EggHatchEvent;
import com.pixelmonmod.pixelmon.api.events.EvolveEvent;
import com.pixelmonmod.pixelmon.api.events.ExperienceGainEvent;
import com.pixelmonmod.pixelmon.api.events.PixelmonTradeEvent;
import com.pixelmonmod.pixelmon.api.events.PixelmonReceivedEvent;
import com.pixelmonmod.pixelmon.api.events.PlayerBattleEndedEvent;
import com.pixelmonmod.pixelmon.api.events.npc.NPCEvent;
import com.pixelmonmod.pixelmon.api.events.pokemon.EVsGainedEvent;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.battles.controller.BattleControllerBase;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.RaidPixelmonParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.TrainerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import com.pixelmonmod.pixelmon.entities.pixelmon.EntityPixelmon;
import com.pixelmonmod.pixelmon.enums.EnumSpecies;
import com.pixelmonmod.pixelmon.enums.battle.BattleResults;
import com.pokepass.PokePassPlugin;
import com.pokepass.quest.QuestType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.bukkit.entity.Player;

/**
 * Pixelmon 事件 → 任务进度。
 *
 * <p>只做三件事：从事件里挖出「谁、什么对象、多少量」，丢给 QuestService。
 * 所有判定逻辑在 QuestService 里，这里不写业务分支。</p>
 */
public class PixelmonListener {

    private final PokePassPlugin plugin;

    public PixelmonListener(PokePassPlugin plugin) {
        this.plugin = plugin;
    }

    @SubscribeEvent
    public void onCapture(CaptureEvent.SuccessfulCapture event) {
        Player player = bukkit(event.player);
        if (player == null) {
            return;
        }
        String species = speciesOf(event.getPokemon() == null ? null : event.getPokemon().getStoragePokemonData());
        if (species != null) {
            plugin.getProgressService().get(player).recordSpecies(species);
        }
        plugin.getQuestService().advance(player, QuestType.CAPTURE, species, 1);
    }

    @SubscribeEvent
    public void onBattleEnd(PlayerBattleEndedEvent event) {
        if (event.result != BattleResults.VICTORY) {
            return;
        }
        Player player = bukkit(event.player);
        if (player == null) {
            return;
        }
        plugin.getQuestService().advance(player, QuestType.BATTLE_WIN,
                opponentType(event.battleController, event.player), 1);
    }

    @SubscribeEvent
    public void onEvolve(EvolveEvent.PostEvolve event) {
        Player player = bukkit(event.player);
        if (player == null) {
            return;
        }
        String species = event.postEvo == null ? null : event.postEvo.name;
        plugin.getQuestService().advance(player, QuestType.EVOLVE, species, 1);
    }

    @SubscribeEvent
    public void onEggHatch(EggHatchEvent.Post event) {
        Player player = bukkit(event.getPlayer());
        if (player == null) {
            return;
        }
        plugin.getQuestService().advance(player, QuestType.EGG_HATCH, speciesOf(event.getPokemon()), 1);
    }

    @SubscribeEvent
    public void onExperienceGain(ExperienceGainEvent event) {
        if (event.pokemon == null) {
            return;
        }
        Player player = bukkit(event.pokemon.getPlayerOwner());
        if (player == null) {
            return;
        }
        plugin.getQuestService().advance(player, QuestType.EXPERIENCE_GAIN, "*", event.getExperience());
    }

    @SubscribeEvent
    public void onEvsGained(EVsGainedEvent event) {
        if (event.pokemon == null) {
            return;
        }
        Player player = bukkit(event.pokemon.getOwnerPlayer());
        if (player == null) {
            return;
        }
        int total = 0;
        if (event.evs != null) {
            for (int value : event.evs) {
                total += Math.abs(value);
            }
        }
        plugin.getQuestService().advance(player, QuestType.EVS_GAINED, "*", total);
    }

    @SubscribeEvent
    public void onTrade(PixelmonTradeEvent event) {
        // 交易双方都算完成一次
        Player first = event.player1 instanceof EntityPlayerMP ? bukkit((EntityPlayerMP) event.player1) : null;
        Player second = event.player2 instanceof EntityPlayerMP ? bukkit((EntityPlayerMP) event.player2) : null;
        if (first != null) {
            plugin.getQuestService().advance(first, QuestType.TRADE, "*", 1);
        }
        if (second != null) {
            plugin.getQuestService().advance(second, QuestType.TRADE, "*", 1);
        }
    }

    /**
     * 化石机复活。
     *
     * <p>Pixelmon 没有单独的「复活完成」事件，所有「玩家收到一只精灵」都走
     * {@link PixelmonReceivedEvent}，用 receiveType 区分来源。这里只认 Fossil，
     * 免得初始精灵、命令发放、交换也一并算成复活化石。</p>
     */
    @SubscribeEvent
    public void onPokemonReceived(PixelmonReceivedEvent event) {
        if (event.receiveType != com.pixelmonmod.pixelmon.api.enums.ReceiveType.Fossil) {
            return;
        }
        Player player = bukkit(event.player);
        if (player == null) {
            return;
        }
        plugin.getQuestService().advance(player, QuestType.RECEIVED_FOSSIL,
                speciesOf(event.pokemon), 1);
    }

    @SubscribeEvent
    public void onNpcInteract(NPCEvent.Interact event) {
        // NPCEvent 的 player 字段声明是 EntityPlayer，实际服务端里一定是 EntityPlayerMP
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        Player player = bukkit((EntityPlayerMP) event.player);
        if (player == null) {
            return;
        }
        String name = null;
        if (event.npc != null) {
            try {
                name = ((Entity) event.npc).getName();
            } catch (Throwable ignored) {
            }
        }
        plugin.getQuestService().advance(player, QuestType.NPC_INTERACT, name, 1);
    }

    // ---------- 辅助 ----------

    /** 判定对方是哪一类，供 BattleWin 的 condition 用 */
    private String opponentType(BattleControllerBase controller, EntityPlayerMP self) {
        if (controller == null || controller.participants == null) {
            return "*";
        }
        for (BattleParticipant participant : controller.participants) {
            if (participant instanceof PlayerParticipant) {
                PlayerParticipant playerParticipant = (PlayerParticipant) participant;
                if (playerParticipant.player != null && !playerParticipant.player.getUniqueID().equals(self.getUniqueID())) {
                    return "Player";
                }
            } else if (participant instanceof TrainerParticipant) {
                return "Trainer";
            } else if (participant instanceof WildPixelmonParticipant
                    || participant instanceof RaidPixelmonParticipant) {
                return "WildPokemon";
            }
        }
        return "*";
    }

    /** 物种英文名，任务条件就是拿它比的 */
    public static String speciesOf(Pokemon pokemon) {
        if (pokemon == null) {
            return null;
        }
        try {
            EnumSpecies species = pokemon.getSpecies();
            if (species != null) {
                return species.getPokemonName();
            }
        } catch (Throwable ignored) {
        }
        try {
            return pokemon.getDisplayName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String speciesOf(EntityPixelmon entity) {
        if (entity == null) {
            return null;
        }
        try {
            return speciesOf(entity.getStoragePokemonData());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Player bukkit(EntityPlayerMP mp) {
        if (mp == null) {
            return null;
        }
        try {
            return (Player) mp.getBukkitEntity();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
