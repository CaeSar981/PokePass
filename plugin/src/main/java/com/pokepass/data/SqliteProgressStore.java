package com.pokepass.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SQLite 存储（pass.db）。
 *
 * <p>进度全部塞一张表：任务进度用 {@code id:value|id:value} 的字符串存，
 * 领取记录用逗号分隔的等级存。这类数据量级很小（一个人几百字节），
 * 拆成多表反而要多写几个 join，不划算。</p>
 */
public class SqliteProgressStore implements ProgressStore {

    private static final String TABLE = "pokepass_player";

    private final Logger logger;
    private final Connection connection;

    public SqliteProgressStore(File dataFolder, String fileName, Logger logger) throws Exception {
        this.logger = logger;
        Class.forName("org.sqlite.JDBC");
        File dbFile = new File(dataFolder, fileName == null || fileName.isEmpty() ? "pass.db" : fileName);
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "uuid TEXT PRIMARY KEY,"
                    + "name TEXT,"
                    + "total_exp INTEGER NOT NULL DEFAULT 0,"
                    + "daily_key TEXT,"
                    + "weekly_key TEXT,"
                    + "daily_progress TEXT,"
                    + "weekly_progress TEXT,"
                    + "claimed_free TEXT,"
                    + "claimed_premium TEXT,"
                    + "caught TEXT)");
        }
        // 老库升级：CREATE TABLE IF NOT EXISTS 不会给已存在的表补列，
        // 加赛季和商店时必须自己补，否则线上库一路报 "no such column"
        ensureColumn("season", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("shop", "TEXT");
        ensureColumn("pages", "TEXT");
    }

    /** 缺列才 ALTER，已有的跳过（SQLite 不支持 ADD COLUMN IF NOT EXISTS） */
    private void ensureColumn(String column, String type) {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + TABLE + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
            try (Statement alter = connection.createStatement()) {
                alter.executeUpdate("ALTER TABLE " + TABLE + " ADD COLUMN " + column + " " + type);
                logger.info("数据库表已补充字段: " + column);
            }
        } catch (Exception e) {
            logger.warning("补充字段 " + column + " 失败: " + e.getMessage());
        }
    }

    @Override
    public synchronized PlayerProgress load(UUID uuid) {
        PlayerProgress progress = new PlayerProgress(uuid);
        String sql = "SELECT * FROM " + TABLE + " WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return progress;
                }
                progress.setLastName(rs.getString("name"));
                progress.setTotalExp(rs.getLong("total_exp"));
                progress.setPeriodKeys(rs.getString("daily_key"), rs.getString("weekly_key"));
                PlayerProgress.decodeProgress(rs.getString("daily_progress"), progress.dailyMap());
                PlayerProgress.decodeProgress(rs.getString("weekly_progress"), progress.weeklyMap());
                PlayerProgress.decodeInts(rs.getString("claimed_free"), progress.getClaimedFree());
                PlayerProgress.decodeInts(rs.getString("claimed_premium"), progress.getClaimedPremium());
                PlayerProgress.decodeStrings(rs.getString("caught"), progress.getCaughtSpecies());
                progress.setSeasonId(rs.getInt("season"));
                PlayerProgress.decodeProgress(rs.getString("shop"), progress.shopMap());
                PlayerProgress.decodeInts(rs.getString("pages"), progress.getWeeklyPages());
                progress.clearDirty();
                return progress;
            }
        } catch (Exception e) {
            logger.warning("读取战令进度失败(" + uuid + "): " + e.getMessage());
            return progress;
        }
    }

    @Override
    public synchronized void save(PlayerProgress progress) {
        String sql = "REPLACE INTO " + TABLE + " (uuid, name, total_exp, daily_key, weekly_key,"
                + " daily_progress, weekly_progress, claimed_free, claimed_premium, caught, season, shop, pages)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, progress.getUuid().toString());
            ps.setString(2, progress.getLastName());
            ps.setLong(3, progress.getTotalExp());
            ps.setString(4, progress.getDailyKey());
            ps.setString(5, progress.getWeeklyKey());
            ps.setString(6, PlayerProgress.encodeProgress(progress.dailyMap()));
            ps.setString(7, PlayerProgress.encodeProgress(progress.weeklyMap()));
            ps.setString(8, PlayerProgress.encodeInts(progress.getClaimedFree()));
            ps.setString(9, PlayerProgress.encodeInts(progress.getClaimedPremium()));
            ps.setString(10, PlayerProgress.encodeStrings(progress.getCaughtSpecies()));
            ps.setInt(11, progress.getSeasonId());
            ps.setString(12, PlayerProgress.encodeProgress(progress.shopMap()));
            ps.setString(13, PlayerProgress.encodeInts(progress.getWeeklyPages()));
            ps.executeUpdate();
            progress.clearDirty();
        } catch (Exception e) {
            logger.warning("保存战令进度失败(" + progress.getUuid() + "): " + e.getMessage());
        }
    }

    @Override
    public void saveAll(Collection<PlayerProgress> progresses) {
        for (PlayerProgress progress : progresses) {
            save(progress);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public String describe() {
        return "SQLite";
    }
}
