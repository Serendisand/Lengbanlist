package org.leng.extension;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code extensions.yml} 的加载与首次迁移。
 *
 * <p>它把"功能开关"从 {@code config.yml} 的 {@code features.*} 挪到独立文件，
 * 并保持向后兼容：
 *
 * <ul>
 *   <li>首次运行（文件不存在或没有 {@code enabled} 段）时，把 {@code features.*} 的
 *       现有选择逐条搬过来——用户升级后开关状态不变</li>
 *   <li>读取时 {@code extensions.yml} 优先；该键不存在则回退到 {@code features.*}。
 *       这样旧配置文件、以及未来新增的功能键都不会因为"没写进新文件"而丢开关</li>
 * </ul>
 *
 * <p><b>迁移判定必须来自文件本身</b>：{@link #load} 自己从磁盘读，而不是接受调用方
 * 传进来的配置对象。否则调用方只要传一个还没加载的实例，就会被判定成"尚未迁移"，
 * 于是把用户手动改过的开关重新覆盖成 {@code features.*} 的旧值——静默丢配置。
 */
public final class ExtensionsConfig {

    public static final String FILE_NAME = "extensions.yml";

    private static final String SECTION = "enabled";
    private static final String ROOT = SECTION + ".";

    private ExtensionsConfig() {
    }

    /**
     * 从磁盘加载；文件尚未迁移过时，把 {@code legacyFeatures} 的选择搬进去并落盘。
     *
     * @param legacyFeatures {@code config.yml} 的 {@code features} 段，可为 null
     */
    public static Loaded load(File file, ConfigurationSection legacyFeatures) {
        FileConfiguration extensions = YamlConfiguration.loadConfiguration(file);
        if (extensions.getConfigurationSection(SECTION) != null) {
            return new Loaded(extensions, 0, null);
        }

        List<String> migrated = new ArrayList<>();
        if (legacyFeatures != null) {
            for (String key : legacyFeatures.getKeys(false)) {
                extensions.set(ROOT + key, legacyFeatures.getBoolean(key, true));
                migrated.add(key);
            }
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            extensions.save(file);
        } catch (IOException e) {
            return new Loaded(extensions, 0, "写入 " + FILE_NAME + " 失败: " + e.getMessage());
        }
        return new Loaded(extensions, migrated.size(),
                migrated.isEmpty() ? null : "已把 config.yml 的 features.* 迁移到 " + FILE_NAME
                        + "（" + migrated.size() + " 项）");
    }

    /**
     * 功能开关是否开启。
     *
     * <p>注意它只回答"开关是不是 true"，不回答"这个功能能不能用"——后者还要看
     * 归属扩展是否已安装，见 {@link ExtensionRegistry#isFeatureActive(String)}。
     */
    public static boolean isEnabled(FileConfiguration extensions, FileConfiguration legacy, String feature) {
        if (feature == null || feature.isBlank()) {
            return false;
        }
        if (extensions != null && extensions.isSet(ROOT + feature)) {
            return extensions.getBoolean(ROOT + feature);
        }
        return legacy == null || legacy.getBoolean("features." + feature, true);
    }

    /**
     * 加载结果。
     *
     * @param config  可直接使用的配置对象
     * @param count   本次迁移的条目数；0 表示无需迁移
     * @param message 需要提示用户的信息；null 表示无需打扰
     */
    public record Loaded(FileConfiguration config, int count, String message) {
    }
}
