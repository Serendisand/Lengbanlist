package org.leng.api;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

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
}
