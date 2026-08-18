package org.leng.platform;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;

import org.leng.manager.AuditManager;
import org.leng.manager.BanManager;
import org.leng.manager.DatabaseManager;
import org.leng.manager.IpAssociationManager;
import org.leng.manager.ModelManager;
import org.leng.manager.MuteManager;
import org.leng.manager.ReportManager;
import org.leng.manager.WarnManager;

public interface LengbanlistPlatform {
    File getDataFolder();

    Logger getLogger();

    String getPluginVersion();

    String getConfigString(String path, String def);

    int getConfigInt(String path, int def);

    boolean getConfigBoolean(String path, boolean def);

    List<String> getConfigStringList(String path);

    boolean isConfigurationSection(String path);

    List<String> getConfigurationSectionKeys(String path);

    void setConfigValue(String path, Object value);

    void saveConfigFile();

    String prefix();

    boolean isFeatureEnabled(String feature);

    DatabaseManager getDatabaseManager();

    BanManager getBanManager();

    MuteManager getMuteManager();

    WarnManager getWarnManager();

    ReportManager getReportManager();

    IpAssociationManager getIpAssociationManager();

    ModelManager getModelManager();

    AuditManager getAuditManager();

    /**
     * 检查给定操作者权重是否能对目标玩家执行处罚。
     * Bukkit 端委托给 LuckPerms/权限节点；Fabric 等无权限后端的平台默认放行。
     */
    default boolean canPunish(int operatorWeight, String targetName) {
        return true;
    }

    /**
     * 检查给定操作者权重是否能对目标（玩家或 IP）执行处罚。
     * Bukkit 端委托给 LuckPerms/权限节点；Fabric 等无权限后端的平台默认放行。
     */
    default boolean canPunishTarget(int operatorWeight, String target) {
        return true;
    }

    /**
     * Web 面板操作者的权重。Bukkit 端读取 web.operator-weight；其他平台默认最大值。
     */
    default int getWebOperatorWeight() {
        return Integer.MAX_VALUE;
    }

    void broadcastMessage(String message);

    default void logMessage(String message) {
        getLogger().info(message);
    }

    void runSync(Runnable task);

    /** 在主线程执行任务并返回可取消句柄；不实现取消的平台返回 {@link CancellableTask#NOOP}。 */
    default CancellableTask runSyncCancellable(Runnable task) {
        runSync(task);
        return CancellableTask.NOOP;
    }

    /** 异步执行任务（平台无异步调度时降级为直接执行）。 */
    default void runAsync(Runnable task) {
        task.run();
    }

    void kickPlayerIfOnline(String playerName, String message);

    int getOnlinePlayerCount();

    int getMaxPlayers();

    String getBroadcastString(String path, String def);

    void reloadConfigFiles();

    void reloadWebServer();

    InputStream getResourceStream(String path);
}
