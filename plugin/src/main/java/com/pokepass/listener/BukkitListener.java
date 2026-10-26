package com.pokepass.listener;

import com.pokepass.PokePassPlugin;
import com.pokepass.quest.QuestType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 原版事件 → 任务进度。
 *
 * <p>聊天是异步事件，进度推进必须切回主线程：任务进度虽然是内存 Map，
 * 但完成时会发消息、可能触发送奖提示，这些都得在主线程做。</p>
 */
public class BukkitListener implements Listener {

    private final PokePassPlugin plugin;

    public BukkitListener(PokePassPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        plugin.getQuestService().advance(event.getPlayer(), QuestType.BLOCK_BREAK,
                event.getBlock().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        ItemStack result = event.getRecipe() == null ? null : event.getRecipe().getResult();
        if (result == null) {
            return;
        }
        int amount = Math.max(1, result.getAmount());
        plugin.getQuestService().advance((Player) event.getWhoClicked(), QuestType.CRAFT,
                result.getType().name(), amount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        String target = "*";
        if (event.getCaught() instanceof Item) {
            target = ((Item) event.getCaught()).getItemStack().getType().name();
        }
        plugin.getQuestService().advance(event.getPlayer(), QuestType.FISH, target, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        plugin.getQuestService().advance((Player) event.getEntity(), QuestType.PICKUP,
                event.getItem().getItemStack().getType().name(), event.getItem().getItemStack().getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        plugin.getQuestService().advance(event.getEnchanter(), QuestType.ENCHANT,
                event.getItem().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(final AsyncPlayerChatEvent event) {
        final String message = event.getMessage();
        final Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    plugin.getQuestService().advance(player, QuestType.CHAT, message, 1);
                }
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        plugin.getProgressService().get(player);
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    plugin.getRewardService().notifyUnclaimed(player,
                            plugin.getProgressService().get(player));
                }
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getProgressService().unload(event.getPlayer());
        plugin.getQuestService().forget(event.getPlayer().getUniqueId());
    }
}
