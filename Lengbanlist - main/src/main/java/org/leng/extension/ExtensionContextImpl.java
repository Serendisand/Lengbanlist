package org.leng.extension;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.leng.Lengbanlist;
import org.leng.api.Cancellable;
import org.leng.api.CommandRegistrar;
import org.leng.api.ExtensionConfig;
import org.leng.api.ExtensionContext;
import org.leng.api.Scheduler;
import org.leng.utils.SchedulerUtils;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * {@link ExtensionContext} 的实现。
 *
 * <p>配置文件位置按提供者类型自动决定，扩展作者无需关心：
 *
 * <ul>
 *   <li><b>外部扩展</b>（主类同时是 Bukkit {@link Plugin}）：{@code plugins/<扩展名>/config.yml}；
 *       若扩展 jar 里带了 {@code config.yml}，首次会自动释放为默认配置。</li>
 *   <li><b>内置提供者</b>（核心 jar 内的代码，没有自己的 Plugin）：{@code plugins/Lengbanlist/extensions/<id>.yml}。</li>
 * </ul>
 */
final class ExtensionContextImpl implements ExtensionContext {

    private final Lengbanlist plugin;
    private final String extensionId;
    private final CommandRegistrar commands;
    private final HookRegistryImpl hooks;
    private final Plugin owner;
    private final Scheduler scheduler;
    private final Logger logger;
    private final org.leng.api.Messages messages;
    private final org.leng.api.DataStore data;
    private final org.leng.api.Services services;

    private volatile ExtensionConfigImpl config;

    ExtensionContextImpl(Lengbanlist plugin, String extensionId, CommandRegistrar commands,
                          HookRegistryImpl hooks, Plugin owner) {
        this.plugin = plugin;
        this.extensionId = extensionId;
        this.commands = commands;
        this.hooks = hooks;
        this.owner = owner;
        this.scheduler = new SchedulerAdapter(plugin);
        this.messages = new ExtensionFacades.MessagesImpl();
        this.data = new ExtensionFacades.DataStoreImpl(plugin, extensionId);
        this.services = new ExtensionFacades.ServicesImpl(plugin);
        this.logger = owner != null
                ? new PrefixedLogger(plugin.getLogger(), owner.getName())
                : new PrefixedLogger(plugin.getLogger(), extensionId);
    }

    @Override
    public org.leng.api.Messages messages() {
        return messages;
    }

    @Override
    public org.leng.api.DataStore data() {
        return data;
    }

    @Override
    public org.leng.api.Services services() {
        return services;
    }

    @Override
    public String extensionId() {
        return extensionId;
    }

    @Override
    public CommandRegistrar commands() {
        return commands;
    }

    @Override
    public org.leng.api.HookRegistry hooks() {
        return hooks;
    }

    @Override
    public Scheduler scheduler() {
        return scheduler;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public ExtensionConfig config() {
        ExtensionConfigImpl current = config;
        if (current == null) {
            synchronized (this) {
                current = config;
                if (current == null) {
                    current = new ExtensionConfigImpl(plugin, extensionId, owner);
                    config = current;
                }
            }
        }
        return current;
    }

    // ------------------------------------------------------------ 配置

    private static final class ExtensionConfigImpl implements ExtensionConfig {

        private final File file;
        private FileConfiguration config;

        private ExtensionConfigImpl(Lengbanlist plugin, String extensionId, Plugin owner) {
            if (owner != null) {
                this.file = new File(owner.getDataFolder(), "config.yml");
                if (!file.exists() && owner.getResource("config.yml") != null) {
                    owner.saveResource("config.yml", false);
                }
            } else {
                this.file = new File(plugin.getDataFolder(), "extensions/" + extensionId + ".yml");
            }
            this.config = YamlConfiguration.loadConfiguration(file);
        }

        @Override
        public FileConfiguration raw() {
            return config;
        }

        @Override
        public File file() {
            return file;
        }

        @Override
        public void save() {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    return;
                }
                config.save(file);
            } catch (Exception e) {
                throw new IllegalStateException("保存扩展配置失败: " + file, e);
            }
        }

        @Override
        public void reload() {
            config = YamlConfiguration.loadConfiguration(file);
        }
    }

    // ------------------------------------------------------------ 调度

    private static final class SchedulerAdapter implements Scheduler {

        private final Lengbanlist plugin;

        private SchedulerAdapter(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean isFolia() {
            return SchedulerUtils.isFolia();
        }

        @Override
        public Cancellable runSync(Runnable task) {
            return wrap(SchedulerUtils.runTask(plugin, task));
        }

        @Override
        public Cancellable runSyncLater(Runnable task, long delayTicks) {
            return wrap(SchedulerUtils.runTaskLater(plugin, task, delayTicks));
        }

        @Override
        public Cancellable runSyncRepeating(Runnable task, long delayTicks, long periodTicks) {
            return wrap(SchedulerUtils.runTaskTimer(plugin, task, delayTicks, periodTicks));
        }

        @Override
        public void runAsync(Runnable task) {
            SchedulerUtils.runAsync(plugin, task);
        }

        @Override
        public Cancellable runAsyncLater(Runnable task, long delayMillis) {
            return wrap(SchedulerUtils.runAsyncDelayed(plugin, task, delayMillis));
        }

        @Override
        public Cancellable runAsyncRepeating(Runnable task, long delayTicks, long periodTicks) {
            return wrap(SchedulerUtils.runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks));
        }

        private static Cancellable wrap(SchedulerUtils.SchedulerTask task) {
            return new Cancellable() {
                @Override
                public void cancel() {
                    task.cancel();
                }

                @Override
                public boolean isCancelled() {
                    return task.isCancelled();
                }
            };
        }
    }

    // ------------------------------------------------------------ 日志

    /** 给每条日志加上 {@code [扩展名] } 前缀，便于在控制台里分辨是谁在说话。 */
    private static final class PrefixedLogger extends Logger {

        private final Logger delegate;
        private final String prefix;

        private PrefixedLogger(Logger delegate, String label) {
            super(delegate.getName() + "." + label, null);
            this.delegate = delegate;
            this.prefix = "[" + label + "] ";
            setParent(delegate);
            setLevel(Level.ALL);
            setUseParentHandlers(false);
        }

        @Override
        public void log(LogRecord record) {
            record.setMessage(prefix + record.getMessage());
            delegate.log(record);
        }
    }
}
