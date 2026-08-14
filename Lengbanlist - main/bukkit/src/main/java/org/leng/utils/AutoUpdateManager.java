package org.leng.utils;

import org.bukkit.plugin.java.JavaPlugin;
import org.leng.Lengbanlist;

import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.logging.Logger;

public class AutoUpdateManager {
    private final Lengbanlist plugin;
    private final Logger logger;
    private File currentPluginFile;

    public AutoUpdateManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.currentPluginFile = getCurrentPluginFile();
    }


    private File getCurrentPluginFile() {
        try {

            Method getFileMethod = JavaPlugin.class.getDeclaredMethod("getFile");
            getFileMethod.setAccessible(true);
            return (File) getFileMethod.invoke(plugin);
        } catch (Exception e) {
            logger.warning("获取当前插件文件失败: " + e.getMessage());
            return null;
        }
    }


    private String getPluginBaseName(String fileName) {


        if (fileName.matches(".* - \\d+(\\.\\d+)*\\.jar$")) {

            return fileName.substring(0, fileName.lastIndexOf(" - ")) + ".jar";
        }

        return fileName;
    }

    public void checkAndAutoUpdate() {
        try {
            String latestVersion = GitHubUpdateChecker.getLatestReleaseVersion();
            String currentVersion = plugin.getDescription().getVersion();
            if (GitHubUpdateChecker.isUpdateAvailable(currentVersion)) {
                logger.info("发现新版本：" + latestVersion + "，当前版本：" + currentVersion);
                downloadAndReplace(latestVersion);
            } else {
                logger.info("你正在使用最新版本：" + currentVersion);
            }
        } catch (Exception e) {
            logger.warning("检查更新时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void downloadAndReplace(String version) throws Exception {
        if (currentPluginFile == null) {
            throw new Exception("无法获取当前插件文件");
        }


        String currentFileName = currentPluginFile.getName();
        String baseName = getPluginBaseName(currentFileName);


        String newFileName;
        if (currentFileName.contains(" - ")) {

            String namePart = currentFileName.substring(0, currentFileName.lastIndexOf(" - "));
            newFileName = namePart + " - " + version + ".jar";
        } else {

            newFileName = "Lengbanlist - " + version + ".jar";
        }


        String downloadUrl = GitHubUpdateChecker.getLatestDownloadUrl();


        File tempFile = new File(currentPluginFile.getParentFile(),
                               newFileName + ".temp");


        logger.info("正在从 " + downloadUrl + " 下载新版本...");
        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);
        try (InputStream in = connection.getInputStream();
             ReadableByteChannel rbc = Channels.newChannel(in);
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } finally {
            connection.disconnect();
        }

        logger.info("新版本已下载到临时文件: " + tempFile.getName() +
                   " (" + tempFile.length() + " bytes)");


        File newPluginFile = new File(currentPluginFile.getParentFile(), newFileName);


        if (newPluginFile.exists()) {
            logger.info("删除已存在的文件: " + newPluginFile.getName());
            if (!newPluginFile.delete()) {
                logger.warning("无法删除已存在的文件，尝试重命名...");
                File backupFile = new File(newPluginFile.getParentFile(),
                                         newPluginFile.getName() + ".backup");
                if (newPluginFile.renameTo(backupFile)) {
                    logger.info("已将旧文件备份为: " + backupFile.getName());
                }
            }
        }


        if (tempFile.renameTo(newPluginFile)) {
            logger.info("临时文件已重命名为: " + newFileName);
        } else {

            logger.info("重命名失败，尝试复制文件...");
            copyFile(tempFile, newPluginFile);
            tempFile.delete();
        }


        if (!currentPluginFile.equals(newPluginFile) && currentPluginFile.exists()) {
            logger.info("删除旧插件文件: " + currentPluginFile.getName());
            if (currentPluginFile.delete()) {
                logger.info("旧插件文件已删除");
            } else {
                currentPluginFile.deleteOnExit();
                logger.warning("无法立即删除旧插件文件，将在服务器退出时删除: " + currentPluginFile.getName());
            }
        }

        installUpdate(newPluginFile);
    }


    private void copyFile(File source, File destination) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    private void installUpdate(File newPluginFile) {
        logger.info("新版本插件文件已安装: " + newPluginFile.getName());
        logger.info("请重启服务器以加载新版本。Paper 不支持安全地运行时替换并重载插件。");
    }
}
