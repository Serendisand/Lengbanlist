package org.leng.api;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.List;

/**
 * 扩展私有的配置文件，位于 {@code plugins/<扩展名>/config.yml}。
 *
 * <p>核心负责首次生成（从扩展 jar 里的 {@code config.yml} 复制）与目录归属，
 * 扩展只需读写 {@link #raw()} 并在改动后 {@link #save()}。
 */
public interface ExtensionConfig {

    /** 底层配置对象，直接用 Bukkit 的 get/set 系列方法读写。 */
    FileConfiguration raw();

    File file();

    void save();

    /** 从磁盘重新读取（用于 {@code /lban reload} 或扩展自己的重载命令）。 */
    void reload();

    // ------------------------------------------------------------ 常用类型化读写
    // 直接转发到 raw()，省掉每个扩展都写一遍 raw().getXxx(...)。

    default String getString(String path, String defaultValue) {
        return raw().getString(path, defaultValue);
    }

    default boolean getBoolean(String path, boolean defaultValue) {
        return raw().getBoolean(path, defaultValue);
    }

    default int getInt(String path, int defaultValue) {
        return raw().getInt(path, defaultValue);
    }

    default long getLong(String path, long defaultValue) {
        return raw().getLong(path, defaultValue);
    }

    default double getDouble(String path, double defaultValue) {
        return raw().getDouble(path, defaultValue);
    }

    default List<String> getStringList(String path) {
        return raw().getStringList(path);
    }

    default boolean contains(String path) {
        return raw().contains(path);
    }

    default void set(String path, Object value) {
        raw().set(path, value);
    }
}
