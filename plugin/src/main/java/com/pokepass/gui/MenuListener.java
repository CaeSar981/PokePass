package com.pokepass.gui;

import com.pokepass.PokePassPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** 界面点击分发。按 holder 类型判断是哪个界面，跟标题文字无关。 */
public class MenuListener implements Listener {

    private final PokePassPlugin plugin;

    public MenuListener(PokePassPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof PassMenus.BaseMenu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        // 只处理上半个界面；玩家自己背包里的点击直接吞掉
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        PassMenus.BaseMenu menu = (PassMenus.BaseMenu) holder;
        menu.handleClick(event.getSlot(), event.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PassMenus.BaseMenu) {
            event.setCancelled(true);
        }
    }
}
