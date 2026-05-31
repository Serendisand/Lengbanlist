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
    private File currentPluginFile; // 存储当前插件文件的引用

    public AutoUpdateManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.currentPluginFile = getCurrentPluginFile();
    }

    /**
     * 获取当前插件文件
     */
    private File getCurrentPluginFile() {
        try {
            // 通过反射获取插件的File对象
            Method getFileMethod = JavaPlugin.class.getDeclaredMethod("getFile");
            getFileMethod.setAccessible(true);
            return (File) getFileMethod.invoke(plugin);
        } catch (Exception e) {
            logger.warning("获取当前插件文件失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从文件名中提取插件基础名称（去除版本号部分）
     */
    private String getPluginBaseName(String fileName) {
        // 匹配格式如：Lengbanlist - 1.8.0.jar
        // 或者：Lengbanlist.jar
        if (fileName.matches(".* - \\d+(\\.\\d+)*\\.jar$")) {
            // 如果有版本号，移除 " - 版本号"
            return fileName.substring(0, fileName.lastIndexOf(" - ")) + ".jar";
        }
        // 如果没有版本号，直接返回
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

        // 获取当前插件的基础名称（不带版本号）
        String currentFileName = currentPluginFile.getName();
        String baseName = getPluginBaseName(currentFileName);
        
        // 生成新版本的文件名（保持相同的命名格式）
        String newFileName;
        if (currentFileName.contains(" - ")) {
            // 如果当前文件名包含 " - "，则使用相同的格式
            String namePart = currentFileName.substring(0, currentFileName.lastIndexOf(" - "));
            newFileName = namePart + " - " + version + ".jar";
        } else {
            // 如果当前文件名没有版本号，直接添加版本号
            newFileName = "Lengbanlist - " + version + ".jar";
        }

        // 下载URL - 从 GitHub API 获取真实下载链接
        String downloadUrl = GitHubUpdateChecker.getLatestDownloadUrl();
        
        // 下载到临时文件
        File tempFile = new File(currentPluginFile.getParentFile(), 
                               newFileName + ".temp");
        
        // 下载新版本
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

        // 最终的新插件文件
        File newPluginFile = new File(currentPluginFile.getParentFile(), newFileName);
        
        // 如果目标文件已存在，先删除
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

        // 重命名临时文件
        if (tempFile.renameTo(newPluginFile)) {
            logger.info("临时文件已重命名为: " + newFileName);
        } else {
            // 如果重命名失败，尝试复制
            logger.info("重命名失败，尝试复制文件...");
            copyFile(tempFile, newPluginFile);
            tempFile.delete();
        }

        // 删除旧插件文件，避免重启后同时加载两个版本
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

    /**
     * 复制文件
     */
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