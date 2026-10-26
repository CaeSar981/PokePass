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
 *
 * <p>探测必须是懒的。Vault 对接经济插件是异步完成的：它 onEnable 时只打印
 * {@code Essentials Economy found: Waiting}，要等 EssentialsX 真正注册 service 之后
 * 才会 {@code hooked}。如果只在插件构造时探测一次，就会永远停在「未就绪」。
 * 所以这里改成：查询时如果还没探到，隔几秒重探，最多探若干次。</p>
 */
public class Economy {

    /** 两次重探之间的最小间隔，避免界面刷新时把重试次数瞬间用光 */
    private static final long PROBE_COOLDOWN_MS = 3000L;
    /** 最多重探多少次（每次间隔 3 秒，覆盖启动后约一分钟） */
    private static final int MAX_PROBE_ATTEMPTS = 20;

    private final Logger logger;

    private Object pointsApi;
    private Method pointsGive;
    private Method pointsTake;
    private Method pointsLook;
    private int pointsProbeAttempts;
    private long lastPointsProbe;
    private boolean pointsFailureLogged;

    private Object vaultEconomy;
    private Method vaultDeposit;
    private Method vaultWithdraw;
    private Method vaultBalance;
    private int vaultProbeAttempts;
    private long lastVaultProbe;
    private boolean vaultFailureLogged;

    public Economy(Logger logger) {
        this.logger = logger;
        probePoints();
        probeVault();
    }

    private boolean shouldRetry(int attempts, long lastProbe) {
        return attempts < MAX_PROBE_ATTEMPTS
                && System.currentTimeMillis() - lastProbe >= PROBE_COOLDOWN_MS;
    }

    private void probePoints() {
        pointsProbeAttempts++;
        lastPointsProbe = System.currentTimeMillis();
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (plugin == null) {
                return;
            }
            Object api = plugin.getClass().getMethod("getAPI").invoke(plugin);
            if (api == null) {
                return;
            }
            pointsApi = api;
            pointsGive = api.getClass().getMethod("give", java.util.UUID.class, int.class);
            pointsTake = api.getClass().getMethod("take", java.util.UUID.class, int.class);
            pointsLook = api.getClass().getMethod("look", java.util.UUID.class);
            logger.info("已对接 PlayerPoints 点券");
        } catch (Throwable t) {
            // 找不到 API 时 PlayerPoints 可能还没 enable 完，重探即可，别刷日志
            if (!pointsFailureLogged) {
                pointsFailureLogged = true;
                logger.info("PlayerPoints 未就绪，点券奖励暂不可用: " + t.getClass().getSimpleName());
            }
            pointsApi = null;
            pointsGive = null;
            pointsTake = null;
            pointsLook = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void probeVault() {
        vaultProbeAttempts++;
        lastVaultProbe = System.currentTimeMillis();
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                return;
            }
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) {
                // Vault 还在等 EssentialsX 之类注册 service，过几秒再探
                return;
            }
            Object provider = registration.getProvider();
            // 从接口取方法而不是实现类：中介实现类未必是 public，走接口才能稳定 invoke
            Method deposit = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            Method withdraw = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            Method balance = economyClass.getMethod("getBalance", OfflinePlayer.class);
            vaultEconomy = provider;
            vaultDeposit = deposit;
            vaultWithdraw = withdraw;
            vaultBalance = balance;
            logger.info("已对接 Vault 经济: " + provider.getClass().getSimpleName());
        } catch (Throwable t) {
            if (!vaultFailureLogged) {
                vaultFailureLogged = true;
                logger.info("Vault 经济未就绪，金钱奖励暂不可用: " + t.getClass().getSimpleName());
            }
            vaultEconomy = null;
            vaultDeposit = null;
            vaultWithdraw = null;
            vaultBalance = null;
        }
    }

    public boolean pointsAvailable() {
        if (pointsApi == null && shouldRetry(pointsProbeAttempts, lastPointsProbe)) {
            probePoints();
        }
        return pointsApi != null && pointsGive != null;
    }

    public boolean vaultAvailable() {
        if (vaultEconomy == null && shouldRetry(vaultProbeAttempts, lastVaultProbe)) {
            probeVault();
        }
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

    /**
     * 扣点券。返回 false 表示没扣成（前置没装、余额不足、API 报错）。
     *
     * <p>PlayerPoints 的 take 自己会返回 boolean，余额不足时是 false 而不是抛异常，
     * 所以调用方拿到 false 时<b>不该</b>继续发货。</p>
     */
    public boolean takePoints(OfflinePlayer player, int amount) {
        if (!pointsAvailable() || pointsTake == null || amount <= 0) {
            return false;
        }
        try {
            Object result = pointsTake.invoke(pointsApi, player.getUniqueId(), amount);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable t) {
            logger.warning("扣除点券失败: " + t.getMessage());
            return false;
        }
    }

    public double moneyBalance(OfflinePlayer player) {
        if (!vaultAvailable() || vaultBalance == null) {
            return 0.0D;
        }
        try {
            Object result = vaultBalance.invoke(vaultEconomy, player);
            return result instanceof Number ? ((Number) result).doubleValue() : 0.0D;
        } catch (Throwable t) {
            return 0.0D;
        }
    }

    /** 扣钱。Vault 的 EconomyResponse 自带成败标记，不能只看有没有抛异常 */
    public boolean takeMoney(OfflinePlayer player, double amount) {
        if (!vaultAvailable() || vaultWithdraw == null || amount <= 0) {
            return false;
        }
        try {
            Object response = vaultWithdraw.invoke(vaultEconomy, player, amount);
            if (response == null) {
                return false;
            }
            try {
                Method success = response.getClass().getMethod("transactionSuccess");
                Object ok = success.invoke(response);
                return ok instanceof Boolean && (Boolean) ok;
            } catch (NoSuchMethodException e) {
                // 极少数实现是 EconomyResponse 的父接口方法，退回按「没抛异常就算成功」
                return true;
            }
        } catch (Throwable t) {
            logger.warning("扣除金钱失败: " + t.getMessage());
            return false;
        }
    }
}
