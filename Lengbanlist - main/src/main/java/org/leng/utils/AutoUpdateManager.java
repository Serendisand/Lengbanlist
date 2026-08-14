package org.leng.utils;

import org.bukkit.plugin.java.JavaPlugin;
import org.leng.Lengbanlist;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.jar.JarFile;
import java.util.logging.Logger;

public class AutoUpdateManager {
    private static final long MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024; // 64MB 上限，防磁盘填满
    private static final String MANIFEST_MAIN_CLASS = "org.leng.Lengbanlist";

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


        if (fileName.matches("Lengbanlist-\\d+(\\.\\d+)*\\.jar$")) {

            return fileName.substring(0, fileName.lastIndexOf("-")) + ".jar";
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
        if (currentFileName.startsWith("Lengbanlist-")) {

            String namePart = currentFileName.substring(0, currentFileName.lastIndexOf("-"));
            newFileName = namePart + version + ".jar";
        } else {

            newFileName = "Lengbanlist-" + version + ".jar";
        }


        String downloadUrl = GitHubUpdateChecker.getLatestDownloadUrl();


        File tempFile = new File(currentPluginFile.getParentFile(),
                               newFileName + ".temp");


        logger.info("正在从 " + downloadUrl + " 下载新版本...");
        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", GitHubUpdateChecker.getUserAgent());
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection https = (HttpsURLConnection) connection;
            if (!GitHubUpdateChecker.isSslVerifyEnabled()) {
                SSLSocketFactory factory = GitHubUpdateChecker.getInsecureSocketFactory();
                if (factory != null) {
                    https.setSSLSocketFactory(factory);
                }
                https.setHostnameVerifier((hostname, session) -> true);
            }
        }

        // 计算下载内容的 SHA-256 摘要，同时做文件头与大小校验。
        // 若镜像源被劫持，返回的不是合法 jar 时会在替换前被拒绝。
        byte[] jarHeader = new byte[4];
        String sha256;
        long bytesRead;
        try (InputStream rawIn = connection.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            DigestInputStream in = new DigestInputStream(rawIn, digest);
            int headerRead = in.read(jarHeader);
            if (headerRead < 4 || !isZipHeader(jarHeader)) {
                throw new IOException("下载内容不是有效的 JAR 文件（文件头异常），已拒绝安装，请检查更新源或镜像是否可信。");
            }
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(jarHeader, 0, headerRead);
                ReadableByteChannel rbc = Channels.newChannel(in);
                long position = headerRead;
                long transferred = 0;
                while (position < MAX_DOWNLOAD_BYTES) {
                    transferred = fos.getChannel().transferFrom(rbc, position, MAX_DOWNLOAD_BYTES - position);
                    if (transferred == 0) {
                        break; // 流已结束
                    }
                    position += transferred;
                }
                bytesRead = position;
                if (bytesRead >= MAX_DOWNLOAD_BYTES) {
                    throw new IOException("下载内容超过 " + MAX_DOWNLOAD_BYTES + " 字节，疑似非插件文件，已中断并拒绝安装。");
                }
                // transferFrom 返回 0 即流结束；再显式读一次确认没有剩余字节
                if (in.read() != -1) {
                    throw new IOException("下载内容超过 " + MAX_DOWNLOAD_BYTES + " 字节，疑似非插件文件，已中断并拒绝安装。");
                }
            }
            sha256 = toHex(digest.digest());
        } finally {
            connection.disconnect();
        }

        logger.info("新版本已下载到临时文件: " + tempFile.getName() +
                   " (" + bytesRead + " bytes, SHA-256: " + sha256 + ")");

        // 校验 jar 包结构（zip 完整性 + 主类清单），防止镜像返回被截断/篡改的文件
        try (JarFile jarFile = new JarFile(tempFile)) {
            String mainClass = jarFile.getManifest() == null
                    ? null : jarFile.getManifest().getMainAttributes().getValue("Main-Class");
            if (mainClass == null || !MANIFEST_MAIN_CLASS.equals(mainClass)) {
                throw new IOException("下载的 JAR 清单不含主类 " + MANIFEST_MAIN_CLASS + "（实际: " + mainClass + "），已拒绝安装，请检查更新源。");
            }
            if (jarFile.getEntry("plugin.yml") == null) {
                throw new IOException("下载的 JAR 缺少 plugin.yml，已拒绝安装，请检查更新源。");
            }
        }

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

    private static boolean isZipHeader(byte[] header) {
        return header != null && header.length >= 4
                && (header[0] & 0xFF) == 0x50
                && (header[1] & 0xFF) == 0x4B
                && ((header[2] & 0xFF) == 0x03 || (header[2] & 0xFF) == 0x05 || (header[2] & 0xFF) == 0x07);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
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
