package com.pokepass.reward;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * 点券与经济的软对接。
 *
 * <p>这个服务器没装 PlayerPoints，Vault 也只有 API 本体，所以两样都不能在编译期依赖，
 * 一律反射调。缺失时安静地返回 false，由调用方决定提示什么——绝不能让奖励发放
 * 因为某个前置没装就整体抛异常，那样玩家会卡在「已领取但没到账」的状态。</p>
 */
public class Economy {

    private final Logger logger;

    private Object pointsApi;
    private Method pointsGive;
    private Method pointsLook;

    private Object vaultEconomy;
    private Method vaultDeposit;

    public Economy(Logger logger) {
        this.logger = logger;
        probePoints();
        probeVault();
    }

    private void probePoints() {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (plugin == null) {
                return;
            }
            pointsApi = plugin.getClass().getMethod("getAPI").invoke(plugin);
            pointsGive = pointsApi.getClass().getMethod("give", java.util.UUID.class, int.class);
            pointsLook = pointsApi.getClass().getMethod("look", java.util.UUID.class);
        } catch (Throwable t) {
            logger.info("PlayerPoints 未就绪，点券奖励将不可用: " + t.getClass().getSimpleName());
            pointsApi = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void probeVault() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                return;
            }
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) {
                return;
            }
            vaultEconomy = registration.getProvider();
            vaultDeposit = vaultEconomy.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class);
        } catch (Throwable t) {
            logger.info("Vault 经济未就绪，金钱奖励将不可用: " + t.getClass().getSimpleName());
            vaultEconomy = null;
        }
    }

    public boolean pointsAvailable() {
        return pointsApi != null && pointsGive != null;
    }

    public boolean vaultAvailable() {
        return vaultEconomy != null && vaultDeposit != null;
    }

    public boolean givePoints(OfflinePlayer player, int amount) {
        if (!pointsAvailable() || amount <= 0) {
            return false;
        }
        try {
            pointsGive.invoke(pointsApi, player.getUniqueId(), amount);
            return true;
        } catch (Throwable t) {
            logger.warning("发放点券失败: " + t.getMessage());
            return false;
        }
    }

    public boolean giveMoney(OfflinePlayer player, double amount) {
        if (!vaultAvailable() || amount <= 0) {
            return false;
        }
        try {
            vaultDeposit.invoke(vaultEconomy, player, amount);
            return true;
        } catch (Throwable t) {
            logger.warning("发放金钱失败: " + t.getMessage());
            return false;
        }
    }

    public int lookPoints(OfflinePlayer player) {
        if (!pointsAvailable() || pointsLook == null) {
            return 0;
        }
        try {
            Object result = pointsLook.invoke(pointsApi, player.getUniqueId());
            return result instanceof Integer ? (Integer) result : 0;
        } catch (Throwable t) {
            return 0;
        }
    }
}
