package com.mall.test.fixture;

import com.mall.test.config.TestConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 直连 MySQL 的灰盒辅助（mall 库 @ localhost:23306）。
 * 用于用例的数据准备与**副作用复核**（库存/订单/券/积分），见 context-pack/05 QG1。
 * 简单起见每次查询新建连接；测试场景足够。
 */
public final class Db {

    private Db() {}

    private static Connection conn() throws SQLException {
        return DriverManager.getConnection(TestConfig.dbUrl(), TestConfig.dbUsername(), TestConfig.dbPassword());
    }

    public static Map<String, Object> queryRow(String sql, Object... args) {
        List<Map<String, Object>> rows = queryList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static List<Map<String, Object>> queryList(String sql, Object... args) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                var meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) row.put(meta.getColumnLabel(i), rs.getObject(i));
                    out.add(row);
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB query failed: " + sql, e);
        }
    }

    public static long queryLong(String sql, Object... args) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("no row for: " + sql);
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB query failed: " + sql, e);
        }
    }

    public static int update(String sql, Object... args) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("DB update failed: " + sql, e);
        }
    }

    private static void bind(PreparedStatement ps, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
    }
}
