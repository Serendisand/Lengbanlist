package org.leng.api;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据存储门面：让扩展能安全地使用核心已建好的数据库连接池。
 *
 * <p><b>扩展不应自建连接池</b>。核心已经按 storage.yml 配置好 SQLite / MySQL /
 * MariaDB / PostgreSQL 四种方言与连接池；扩展再建一个既浪费连接数，也会在
 * 单机 SQLite 场景下和核心抢同一个文件锁。
 *
 * <p>连接是<b>从池里借出</b>的，扩展必须自行归还：
 *
 * <pre>{@code
 * try (Connection c = data.connection();
 *      PreparedStatement ps = c.prepareStatement("SELECT 1")) {
 *     ...
 * } catch (SQLException e) {
 *     context.logger().warning("查询失败: " + e.getMessage());
 * }
 * }</pre>
 *
 * <p>扩展自己的表名请带上前缀（见 {@link #tablePrefix()}），避免与核心表或其它扩展
 * 撞名；并且只用 {@code CREATE TABLE IF NOT EXISTS} 一类可重复执行的语句，
 * 因为扩展可能在任意时刻被装上或移除。
 */
public interface DataStore {

    /** 从池中借出一个连接。<b>调用方负责关闭</b>，建议用 try-with-resources。 */
    Connection connection() throws SQLException;

    /**
     * 本扩展专属的表名前缀，形如 {@code ext_<扩展id>_}。
     *
     * <p>用它拼出的表名与核心表、其它扩展的表天然隔离。
     */
    String tablePrefix();

    /** 当前是否为共享数据库（MySQL / MariaDB / PostgreSQL）；SQLite 为 false。 */
    boolean isNetworkDatabase();

    /** 在借出的连接上执行一段逻辑并自动归还；无需手写 try-with-resources。 */
    @FunctionalInterface
    interface ConnectionAction<T> {
        T apply(Connection connection) throws SQLException;
    }

    /**
     * 自动关连接的便捷写法：
     *
     * <pre>{@code
     * int count = data.withConnection(c -> {
     *     try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + data.tablePrefix() + "log")) {
     *         try (ResultSet rs = ps.executeQuery()) {
     *             return rs.next() ? rs.getInt(1) : 0;
     *         }
     *     }
     * });
     * }</pre>
     */
    default <T> T withConnection(ConnectionAction<T> action) throws SQLException {
        try (Connection connection = connection()) {
            return action.apply(connection);
        }
    }
}
