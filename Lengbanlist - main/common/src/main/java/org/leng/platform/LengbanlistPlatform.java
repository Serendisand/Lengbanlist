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

    void broadcastMessage(String message);

    default void logMessage(String message) {
        getLogger().info(message);
    }

    void runSync(Runnable task);

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
