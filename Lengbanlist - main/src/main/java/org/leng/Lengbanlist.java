package org.leng;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.leng.command.CommandRegistry;
import org.leng.command.GuiCommands;
import org.leng.command.LengbanlistCommand;
import org.leng.extension.BuiltinExtensions;
import org.leng.extension.CoreService;
import org.leng.extension.DurationPolicy;
import org.leng.extension.ExtensionRegistry;
import org.leng.extension.ExtensionsConfig;
import org.leng.extension.PunishmentGate;
import org.leng.gui.GuiSessionManager;
import org.leng.gui.WizardManager;
import org.leng.integration.AutoUpdateManager;
import org.leng.integration.GitHubUpdateChecker;
import org.leng.integration.ModelCloudManager;
import org.leng.integration.ModelManager;
import org.leng.integration.ThemeManager;
import org.leng.integration.WebhookNotifier;
import org.leng.listener.ChatListener;
import org.leng.listener.FreezeListener;
import org.leng.listener.GuiCleanupListener;
import org.leng.listener.ModelChoiceListener;
import org.leng.listener.MuteCommandBlockListener;
import org.leng.listener.OpJoinListener;
import org.leng.listener.PlayerJoinListener;
import org.leng.listener.VanishListener;
import org.leng.models.Model;
import org.leng.net.DownloadService;
import org.leng.net.DownloadSettings;
import org.leng.service.AppealManager;
import org.leng.service.AuditManager;
import org.leng.service.BanManager;
import org.leng.service.BroadcastManager;
import org.leng.service.EscalationManager;
import org.leng.service.ExpiryReminderTask;
import org.leng.service.FreezeManager;
import org.leng.service.ImmunityManager;
import org.leng.service.IpAssociationManager;
import org.leng.service.MuteManager;
import org.leng.service.ReportManager;
import org.leng.service.SyncManager;
import org.leng.service.VanishManager;
import org.leng.service.WarnManager;
import org.leng.storage.DatabaseManager;
import org.leng.storage.PlayerIdentityResolver;
import org.leng.storage.StorageMigrationManager;
import org.leng.util.ConsoleText;
import org.leng.util.Metrics;
import org.leng.util.SchedulerUtils;
import org.leng.util.Utils;
import org.leng.web.WebServer;

import java.io.File;
import java.io.IOException;

public class Lengbanlist extends JavaPlugin {
    private static Lengbanlist instance;
    public BanManager banManager;
    public MuteManager muteManager;
    public volatile SyncManager syncManager;
    public WarnManager warnManager;
    public volatile AuditManager auditManager;
    public volatile ReportManager reportManager;
    public volatile IpAssociationManager ipAssociationManager;
    public WebServer webServer;
    public SchedulerUtils.SchedulerTask broadcastTask;
    private SchedulerUtils.SchedulerTask historyCleanupTask;
    private SchedulerUtils.SchedulerTask expiryReminderTask;
    private ImmunityManager immunityManager;
    private EscalationManager escalationManager;
    private GuiSessionManager guiSessionManager;
    private GuiCommands.Gui guiCommand;
    private volatile ModelCloudManager modelCloudManager;
    private GuiCommands.Alts altsCommand;
    private CommandRegistry commandRegistry;
    private ExtensionRegistry extensionRegistry;
    private CoreService coreService;
    private PunishmentGate punishmentGate;
    private DurationPolicy durationPolicy;
    private org.bukkit.configuration.file.FileConfiguration extensionsConfig;
    private boolean isBroadcast;
    private FileConfiguration broadcastFC;
    private FileConfiguration chatConfig;
    private ModelChoiceListener modelChoiceListener;
    private ChatListener chatListener;
    private String hitokoto;
    private ModelManager modelManager;
    private DatabaseManager databaseManager;
    private volatile ThemeManager themeManager;
    private volatile VanishManager vanishManager;
    private volatile FreezeManager freezeManager;
    private volatile WizardManager wizardManager;
    private volatile BroadcastManager broadCastManager;
    private volatile WebhookNotifier webhookNotifier;
    private volatile AppealManager appealManager;
    private FileConfiguration eulaFC;
    private FileConfiguration storageConfig;
    private Metrics metrics;

    private boolean eulaAgreed = false;
    private boolean initializationFailed = false;

@Override
public void onLoad() {
    instance = this;

    SchedulerUtils.init(this);

    File eulaFile = new File(getDataFolder(), "eula.yml");
    if (!eulaFile.exists()) {
        eulaFile.getParentFile().mkdirs();
        saveResource("eula.yml", false);
        eulaAgreed = false;
        return;
    }

    eulaFC = YamlConfiguration.loadConfiguration(eulaFile);
    Object agreementValue = eulaFC.get("I have read and agree to the above terms");
    String agreement = agreementValue == null ? "no" : String.valueOf(agreementValue).trim();
    eulaAgreed = "yes".equalsIgnoreCase(agreement) || "true".equalsIgnoreCase(agreement);

    if (!eulaAgreed) {
        return;
    }

    File configFile = new File(getDataFolder(), "config.yml");
    boolean firstLoad = !configFile.exists();
    saveDefaultConfig();
    loadExtensionsConfig();
    if (firstLoad && getConfig().getBoolean("model-auto-detect", true)) {
        String language = java.util.Locale.getDefault().getLanguage();
        String detectedModel = language != null && language.toLowerCase().startsWith("zh") ? "Default" : "English";
        getConfig().set("Model", detectedModel);
        try {
            getConfig().save(configFile);
        } catch (IOException e) {
            getLogger().warning(consoleText("model-detect-save-failed", "error", e.getMessage()));
        }
        getLogger().info(consoleText("model-detected", "language", language, "model", detectedModel));
    }

    if (!getConfig().contains("update-check.enabled")) {
        getConfig().set("update-check.enabled", getConfig().getBoolean("features.update-check", true));
        saveConfig();
    }

    loadStorageConfig();

    databaseManager = new DatabaseManager(this);
    try {
        databaseManager.initialize();
        new StorageMigrationManager(this, databaseManager).migrateYamlIfNeeded();
        muteManager = new MuteManager(this);
    } catch (Exception e) {
        org.leng.util.ErrorLog.record(this, "数据库初始化失败，插件将停止启用", e);
        initializationFailed = true;
        return;
    }

    banManager = new BanManager(this);
    warnManager = new WarnManager(this);
    immunityManager = new ImmunityManager(this);
    punishmentGate = new PunishmentGate(this);
    durationPolicy = new DurationPolicy(this);
    escalationManager = new EscalationManager(this);
    guiSessionManager = new GuiSessionManager();
    guiCommand = new GuiCommands.Gui(this);
    webServer = new WebServer(this);
    isBroadcast = getConfig().getBoolean("opensendtime");

    File modelsDir = new File(getDataFolder(), "models");
    if (!modelsDir.exists()) {
        modelsDir.mkdirs();
    }
    File baseModelFile = new File(modelsDir, "_base.yml");
    if (!baseModelFile.exists()) {
        saveResource("models/_base.yml", false);
        getLogger().info(consoleText("preset-base"));
    }
    for (String builtin : new String[]{"default", "english"}) {
        File target = new File(modelsDir, builtin + ".yml");
        if (!target.exists()) {
            saveResource("models/" + builtin + ".yml", false);
            getLogger().info(consoleText("preset-builtin", "name", builtin));
        }
    }

    modelManager = ModelManager.getInstance();

    File chatConfigFile = new File(getDataFolder(), "chatconfig.yml");
    if (!chatConfigFile.exists()) {
        chatConfigFile.getParentFile().mkdirs();
        saveResource("chatconfig.yml", false);
    }
    chatConfig = YamlConfiguration.loadConfiguration(chatConfigFile);

    File broadcastFile = new File(getDataFolder(), "broadcast.yml");
    if (!broadcastFile.exists()) {
        broadcastFile.getParentFile().mkdirs();
        saveResource("broadcast.yml", false);
    }
    broadcastFC = YamlConfiguration.loadConfiguration(broadcastFile);

}

private void loadStorageConfig() {
    File storageFile = new File(getDataFolder(), "storage.yml");
    if (!storageFile.exists()) {
        saveResource("storage.yml", false);
    }
    storageConfig = YamlConfiguration.loadConfiguration(storageFile);
    migrateLegacyStorageConfig(storageFile);
}

private void migrateLegacyStorageConfig(File storageFile) {
    boolean migrated = false;
    ConfigurationSection legacyDatabase = getConfig().getConfigurationSection("database");
    if (legacyDatabase != null) {
        for (String key : legacyDatabase.getKeys(true)) {
            if (legacyDatabase.isConfigurationSection(key)) {
                continue;
            }
            storageConfig.set("database." + key, legacyDatabase.get(key));
        }
        getConfig().set("database", null);
        migrated = true;
    }
    if (getConfig().contains("history-retention-days")) {
        storageConfig.set("database.retention.history-days", getConfig().getInt("history-retention-days", 7));
        getConfig().set("history-retention-days", null);
        migrated = true;
    }
    if (!migrated) {
        return;
    }
    try {
        storageConfig.save(storageFile);
        saveConfig();
        getLogger().info(consoleText("storage-migrated"));
    } catch (IOException e) {
        getLogger().warning(consoleText("storage-migrate-failed", "error", e.getMessage()));
    }
}

public FileConfiguration getStorageConfig() {
    return storageConfig;
}

public String getServerName() {
    if (storageConfig == null) {
        return "";
    }
    String name = storageConfig.getString("server-name", "");
    return name == null ? "" : name.trim();
}

public int historyRetentionDays() {
    if (storageConfig != null && storageConfig.contains("database.retention.history-days")) {
        return Math.max(1, storageConfig.getInt("database.retention.history-days", 7));
    }
    return Math.max(1, getConfig().getInt("history-retention-days", 7));
}

@Override
public void onEnable() {
    if (initializationFailed) {
        getLogger().severe("==================================================");
        getLogger().severe(consoleText("db-init-failed"));
        getLogger().severe("==================================================");
        Bukkit.getPluginManager().disablePlugin(Lengbanlist.this);
        return;
    }

    if (!eulaAgreed) {
        getLogger().severe("==================================================");
        getLogger().severe(consoleText("eula-required"));
        getLogger().severe(consoleText("eula-hint"));
        getLogger().severe("==================================================");
        Bukkit.getPluginManager().disablePlugin(Lengbanlist.this);
        return;
    }

    if (!Lengbanlist.this.isEnabled()) {
        return;
    }

    consoleMessage("loading");
    SchedulerUtils.runAsync(this, () -> {
        String fetchedHitokoto = getHitokoto();
        if (!Lengbanlist.this.isEnabled()) {
            return;
        }
        SchedulerUtils.runTask(this, () -> {
            if (!Lengbanlist.this.isEnabled()) {
                return;
            }
            hitokoto = fetchedHitokoto;
            // 只输出模型文案，不再前置模型名：模型自己的措辞（如"堂主悄悄告诉你"）
            // 已经带了口吻，再挂一个 "Hutao" 是重复；而且这是全插件唯一一条
            // 在 prefix() 之后又拼模型名的控制台消息，与 consoleMessage() 的约定不一致。
            consoleMessage("tip", hitokoto);
        });
    });

    getServer().getPluginManager().registerEvents(new PlayerJoinListener(Lengbanlist.this), Lengbanlist.this);
    chatListener = new ChatListener(Lengbanlist.this);
    getServer().getPluginManager().registerEvents(chatListener, Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new OpJoinListener(Lengbanlist.this), Lengbanlist.this);
    modelChoiceListener = new ModelChoiceListener(Lengbanlist.this);
    getServer().getPluginManager().registerEvents(modelChoiceListener, Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new MuteCommandBlockListener(this), Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new GuiCleanupListener(this), this);
    getServer().getPluginManager().registerEvents(guiCommand, Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new VanishListener(this), Lengbanlist.this);
    getServer().getPluginManager().registerEvents(new FreezeListener(this), Lengbanlist.this);

    LengbanlistCommand lbanCmd = new LengbanlistCommand("lban", Lengbanlist.this);
    PluginCommand lban = getCommand("lban");
    if (lban != null) {
        lban.setExecutor(lbanCmd);
        lban.setTabCompleter(lbanCmd);
    }
    altsCommand = new GuiCommands.Alts(this);

    // 扩展注册表先建好并装入内置提供者，命令表随后从它派生。
    extensionRegistry = new ExtensionRegistry(this);
    BuiltinExtensions.registerAll(this, extensionRegistry);
    BuiltinExtensions.registerHooks(this, extensionRegistry);
    BuiltinExtensions.registerFeatures(this, extensionRegistry);
    coreService = new CoreService(this, extensionRegistry);
    getServer().getServicesManager().register(org.leng.api.LengbanlistCore.class, coreService,
            this, org.bukkit.plugin.ServicePriority.Normal);

    commandRegistry = new CommandRegistry(this, extensionRegistry);
    // 回调必须在命令表就绪后才挂：注册内置提供者期间每次都会通知变更，
    // 提前挂上就会打到还是 null 的 commandRegistry。
    extensionRegistry.setOnChanged(this::refreshFeatureCommands);
    refreshFeatureCommands();

    getServer().getConsoleSender().sendMessage(consoleText("ready",
            "version", getPluginVersion(),
            "model", ModelManager.getInstance().getCurrentModelName(),
            "server", Bukkit.getServer().getVersion()));

    metrics = new Metrics(this, 33262);

    if (getConfig().getBoolean("features.auto-update", false)) {
        getLogger().info(consoleText("auto-update"));
        SchedulerUtils.runAsyncDelayed(this, this::checkUpdate, 5000);
    } else if (isUpdateCheckEnabled()) {
        SchedulerUtils.runAsync(this, GitHubUpdateChecker::checkUpdate);
    }

    if (isBroadcast && isFeatureActive("broadcast")) {
        startBroadcastTask();
    }

    if (getConfig().getBoolean("web.enabled", false)) {
        if (webServer.start()) {
            getThemeManager().refreshBingBackgroundAsync();
        }
    }

    if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
        new org.leng.integration.PlaceholderAPIHook(Lengbanlist.this).register();
        getServer().getConsoleSender().sendMessage(prefix() + consoleText("placeholder-hook"));
    }

    startHistoryCleanupTask();
    startIdentityBackfillTask();

    if (isFeatureActive("sync")) {
        getSyncManager().startAutoSync();
    }

    if (isFeatureEnabled("expiry-reminder")) {
        long periodTicks = Math.max(20L, getConfig().getInt("expiry-reminder.interval", 60) * 20L);
        expiryReminderTask = SchedulerUtils.runTaskTimerAsynchronously(this, new ExpiryReminderTask(this), 200L, periodTicks);
    }
}

public void refreshFeatureCommands() {
    commandRegistry.refresh();
    getLogger().fine("功能命令刷新完成(features.* 变更已生效)。");
}

public boolean reloadWebServer() {
    boolean enabled = getConfig().getBoolean("web.enabled", false);
    if (enabled && !webServer.isRunning()) {
        return webServer.start();
    } else if (!enabled && webServer.isRunning()) {
        webServer.stop();
        return true;
    } else if (enabled && webServer.isRunning()) {
        webServer.stop();
        return webServer.start();
    }
    return true;
}

public void reloadStorageConfig() {
    loadStorageConfig();
}

public void restartScheduledTasks() {
    isBroadcast = getConfig().getBoolean("opensendtime");
    if (broadcastTask != null) {
        broadcastTask.cancel();
        broadcastTask = null;
    }
    if (isBroadcast && isFeatureActive("broadcast")) {
        startBroadcastTask();
    }
    if (expiryReminderTask != null) {
        expiryReminderTask.cancel();
        expiryReminderTask = null;
    }
    if (isFeatureEnabled("expiry-reminder")) {
        long periodTicks = Math.max(20L, getConfig().getInt("expiry-reminder.interval", 60) * 20L);
        expiryReminderTask = SchedulerUtils.runTaskTimerAsynchronously(this, new ExpiryReminderTask(this), 200L, periodTicks);
    }

}

@Override
public void onDisable() {
    getServer().getConsoleSender().sendMessage(prefix() + consoleText("shutdown"));

    if (broadcastTask != null) broadcastTask.cancel();
    if (historyCleanupTask != null) historyCleanupTask.cancel();
    if (expiryReminderTask != null) expiryReminderTask.cancel();
    if (webhookNotifier != null) webhookNotifier.stop();
    // 先摘服务，避免其他插件拿着失效的门面继续调用
    getServer().getServicesManager().unregisterAll(this);
    if (extensionRegistry != null) {
        // 停服期间不再触发命令刷新，否则会在注销过程中反复重建命令
        extensionRegistry.setOnChanged(null);
        extensionRegistry.unregisterAll();
    }
    if (commandRegistry != null) {
        commandRegistry.unregisterAll();
    }
    if (syncManager != null) {
        syncManager.stopAutoSync();
    }
    if (webServer != null) webServer.stop();

    try {
        if (vanishManager != null) {
            vanishManager.restoreAll();
        }
    } catch (Exception e) {
        getLogger().warning("还原隐身状态时出错: " + e.getMessage());
    }

    try {
        if (metrics != null) {
            metrics.shutdown();
        }
    } catch (Exception e) {
        getLogger().warning("关闭 bStats 统计时出错: " + e.getMessage());
    }

    if (eulaAgreed) {
        shutdownStorage();
    }

    getServer().getConsoleSender().sendMessage(prefix() + consoleText("farewell"));
}

void shutdownStorage() {
    try {
        if (broadcastFC != null) {
            saveBroadcastConfig();
        }
    } catch (Exception e) {
        getLogger().warning("保存配置文件时出错: " + e.getMessage());
    }
    try {
        if (databaseManager != null) {
            databaseManager.close();
        }
    } catch (Exception e) {
        getLogger().warning("关闭数据库时出错: " + e.getMessage());
    }
}

    private void startBroadcastTask() {
        long interval = Math.max(getConfig().getInt("sendtime") * 1200L, 1200L);
        long delay = 200L;
        broadcastTask = SchedulerUtils.runTaskTimer(this,
                getBroadCastManager(), delay, interval);
    }

    private void startIdentityBackfillTask() {
        if (storageConfig != null && !storageConfig.getBoolean("database.identity-backfill", true)) {
            return;
        }
        SchedulerUtils.runAsyncDelayed(this, () -> {
            long start = System.currentTimeMillis();
            try {
                int filled = databaseManager.backfillIdentityUuids(200);
                if (filled > 0) {
                    getLogger().info("身份层回填完成：为 " + filled + " 条历史记录补上了 UUID（耗时 "
                            + (System.currentTimeMillis() - start) + " 毫秒）");
                }
            } catch (Exception e) {
                getLogger().warning("身份层回填失败，下次启动会自动重试: " + e.getMessage());
            }
        }, 200L);
    }

    private void startHistoryCleanupTask() {
        historyCleanupTask = SchedulerUtils.runTaskTimerAsynchronously(this, () -> {
            try {
                databaseManager.deactivateExpiredBans();
                boolean removed = databaseManager.cleanupOldData(historyRetentionDays());

                if (removed) {
                    databaseManager.reclaimSpace();
                }
            } catch (Exception e) {

                getLogger().warning("历史数据维护任务执行出错: " + e.getMessage());
            }
        }, 6000L, 72000L);
    }

    public String prefix() {

        return getConfig().getString("prefix", "§b[Lengbanlist]§r ");
    }

    private String consoleText(String key, String... placeholders) {
        Model model = ModelManager.getCurrentModel();
        String template = model == null ? "" : model.getConsole(key);
        if (template == null || template.isEmpty()) {
            return ConsoleText.builtIn(key, placeholders);
        }
        return ConsoleText.fill(template, placeholders);
    }

    private void consoleMessage(String key, String... placeholders) {
        String text = consoleText(key, placeholders);
        if (!text.isEmpty()) {
            getServer().getConsoleSender().sendMessage(prefix() + text);
        }
    }

    public static Lengbanlist getInstance() {
        return instance;
    }

    public boolean isBroadcastEnabled() {
        return isBroadcast;
    }

    /**
     * 扩展注册表。门控与命令表都以它为准。
     *
     * <p>{@link #isFeatureEnabled(String)} 只回答"配置里的开关是不是 true"，
     * 不回答"这个功能现在能不能用"——后者必须问
     * {@code getExtensionRegistry().isFeatureActive(feature)}，
     * 否则未安装的扩展会被当成已启用。
     */
    public ExtensionRegistry getExtensionRegistry() {
        return extensionRegistry;
    }

    /**
     * 处罚放行闸门。命令与 Web 面板都必须经它判断"能不能罚"，
     * 不要直接调用权重实现——闸门才负责"策略缺席时一律放行"的兜底。
     */
    public PunishmentGate getPunishmentGate() {
        return punishmentGate;
    }

    /**
     * 自动时长闸门。使用 auto 时长的命令必须经它取时长，
     * 不要直接调用升级实现——闸门才负责"策略缺席时按警告数兜底"。
     */
    public DurationPolicy getDurationPolicy() {
        return durationPolicy;
    }

    /**
     * 统一门控：功能是否生效 = 归属扩展已安装 <b>且</b> {@code features.<key>} 为 true。
     *
     * <p>所有判断"这个功能现在能不能用"的地方都必须走这里，而不是
     * {@link #isFeatureEnabled(String)}——后者只回答"配置里的开关是不是 true"，
     * 对未安装的扩展会给出误导性的 true。注册表尚未建立时（onLoad 阶段）
     * 自动退回只看开关。
     */
    public boolean isFeatureActive(String feature) {
        ExtensionRegistry registry = extensionRegistry;
        return registry == null ? isFeatureEnabled(feature) : registry.isFeatureActive(feature);
    }

    public boolean isFeatureEnabled(String feature) {
        return ExtensionsConfig.isEnabled(extensionsConfig, getConfig(), feature);
    }

    /**
     * 读取 extensions.yml；文件缺失或尚无 enabled 段时，把 config.yml 的
     * {@code features.*} 现有选择迁移过来，保证升级后开关状态不变。
     */
    private void loadExtensionsConfig() {
        File file = new File(getDataFolder(), ExtensionsConfig.FILE_NAME);
        ExtensionsConfig.Loaded loaded = ExtensionsConfig.load(
                file, getConfig().getConfigurationSection("features"));
        extensionsConfig = loaded.config();
        if (loaded.message() != null) {
            getLogger().info(loaded.message());
        }
    }

    /** 重新读取 config.yml 与 extensions.yml（{@code /lban reload} 使用）。 */
    public void reloadExtensionsConfig() {
        reloadConfig();
        loadExtensionsConfig();
    }

    /** extensions.yml 的当前内容，供 {@code /lban ext list} 之类读取。 */
    public org.bukkit.configuration.file.FileConfiguration getExtensionsConfig() {
        return extensionsConfig;
    }

    public boolean isUpdateCheckEnabled() {
        return getConfig().getBoolean("update-check.enabled", true);
    }

    /** 命令注册表。{@code /lban} 用它分派扩展提供的子命令（见 {@code CommandSpec.parent}）。 */
    public CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    public void sendFeatureDisabled(CommandSender sender) {
        Utils.sendMessage(sender, prefix() + "§c该功能已被管理员禁用。");
    }

    public void setBroadcastEnabled(boolean broadcastEnabled) {
        this.isBroadcast = broadcastEnabled;
        getConfig().set("opensendtime", broadcastEnabled);
        saveConfig();
        if (isBroadcast && isFeatureActive("broadcast")) {
            if (broadcastTask != null) {
                broadcastTask.cancel();
                broadcastTask = null;
            }
            startBroadcastTask();
        } else {
            if (broadcastTask != null) {
                broadcastTask.cancel();
            }
        }
    }

    public String toggleBroadcast() {
        setBroadcastEnabled(!isBroadcastEnabled());
        return isBroadcastEnabled() ? "§a已开启" : "§c已关闭";
    }

    public ModelManager getModelManager() {
        return ModelManager.getInstance();
    }

    public String getPluginVersion() {
        return getDescription().getVersion();
    }

    public SyncManager getSyncManager() {
        if (syncManager == null) {
            synchronized (this) {
                if (syncManager == null) {
                    syncManager = new SyncManager(this);
                }
            }
        }
        return syncManager;
    }

    public BanManager getBanManager() {
        return banManager;
    }

    public MuteManager getMuteManager() {
        return muteManager;
    }

    public WarnManager getWarnManager() {
        return warnManager;
    }

    public ImmunityManager getImmunityManager() {
        return immunityManager;
    }

    public EscalationManager getEscalationManager() {
        return escalationManager;
    }

    public GuiSessionManager getGuiSessionManager() {
        return guiSessionManager;
    }

    public GuiCommands.Gui getGuiCommand() {
        return guiCommand;
    }

    public ModelCloudManager getModelCloudManager() {
        if (modelCloudManager == null) {
            synchronized (this) {
                if (modelCloudManager == null) {
                    modelCloudManager = new ModelCloudManager(this);
                }
            }
        }
        return modelCloudManager;
    }

    public GuiCommands.Alts getAltsCommand() {
        return altsCommand;
    }

    public AuditManager getAuditManager() {
        if (auditManager == null) {
            synchronized (this) {
                if (auditManager == null) {
                    auditManager = new AuditManager(this);
                }
            }
        }
        return auditManager;
    }

    public WebhookNotifier getWebhookNotifier() {
        if (webhookNotifier == null) {
            synchronized (this) {
                if (webhookNotifier == null) {
                    webhookNotifier = new WebhookNotifier(this);
                }
            }
        }
        return webhookNotifier;
    }

    public ReportManager getReportManager() {
        if (reportManager == null) {
            synchronized (this) {
                if (reportManager == null) {
                    reportManager = new ReportManager(this);
                }
            }
        }
        return reportManager;
    }

    public AppealManager getAppealManager() {
        if (appealManager == null) {
            synchronized (this) {
                if (appealManager == null) {
                    appealManager = new AppealManager(this);
                }
            }
        }
        return appealManager;
    }

    public IpAssociationManager getIpAssociationManager() {
        if (ipAssociationManager == null) {
            synchronized (this) {
                if (ipAssociationManager == null) {
                    ipAssociationManager = new IpAssociationManager(this);
                }
            }
        }
        return ipAssociationManager;
    }

    public WebServer getWebServer() {
        return webServer;
    }

    public ModelChoiceListener getModelChoiceListener() {
        return modelChoiceListener;
    }

    public ChatListener getChatListener() {
        return chatListener;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlayerIdentityResolver getIdentityResolver() {
        return databaseManager.getIdentityResolver();
    }

    public ThemeManager getThemeManager() {
        if (themeManager == null) {
            synchronized (this) {
                if (themeManager == null) {
                    themeManager = new ThemeManager(this);
                }
            }
        }
        return themeManager;
    }

    public VanishManager getVanishManager() {
        if (vanishManager == null) {
            synchronized (this) {
                if (vanishManager == null) {
                    vanishManager = new VanishManager(this);
                }
            }
        }
        return vanishManager;
    }

    public FreezeManager getFreezeManager() {
        if (freezeManager == null) {
            synchronized (this) {
                if (freezeManager == null) {
                    freezeManager = new FreezeManager(this);
                }
            }
        }
        return freezeManager;
    }

    public WizardManager getWizardManager() {
        if (wizardManager == null) {
            synchronized (this) {
                if (wizardManager == null) {
                    wizardManager = new WizardManager(this);
                }
            }
        }
        return wizardManager;
    }

    public BroadcastManager getBroadCastManager() {
        if (broadCastManager == null) {
            synchronized (this) {
                if (broadCastManager == null) {
                    broadCastManager = new BroadcastManager(this);
                }
            }
        }
        return broadCastManager;
    }

    public FileConfiguration getBroadcastFC() {
        return broadcastFC;
    }

    public FileConfiguration getChatConfig() {
        return chatConfig;
    }

    public void saveBroadcastConfig() {
        try {
            broadcastFC.save(new File(getDataFolder(), "broadcast.yml"));
        } catch (IOException e) {
            org.leng.util.ErrorLog.record(this, "保存 broadcast.yml 失败", e);
        }
    }

    public String getHitokoto() {
        try (DownloadService downloads = new DownloadService(
                new DownloadSettings(3000, 3000, "Mozilla/5.0", true))) {
            String jsonResponse = downloads.get("https://v1.hitokoto.cn/", "*/*");
            String hitokoto = jsonResponse.split("\"hitokoto\":\"")[1].split("\"")[0];
            String from = jsonResponse.split("\"from\":\"")[1].split("\"")[0];
            return hitokoto + " —— " + from;
        } catch (Exception e) {
            return "我不说了，嘿嘿~";
        }
    }

    public void checkUpdate() {
        new AutoUpdateManager(this).checkAndAutoUpdate();
    }

    public boolean isFolia() {
        return SchedulerUtils.isFolia();
    }
}
