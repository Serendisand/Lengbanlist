package org.leng.utils;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.leng.Lengbanlist;

import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
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
        if (fileName == null) {
            return null;
        }
        int lastHyphen = fileName.lastIndexOf("-");
        if (lastHyphen > 0 && fileName.endsWith(".jar")) {
            return fileName.substring(0, lastHyphen) + ".jar";
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
        GitHubUpdateChecker.Platform platform = GitHubUpdateChecker.Platform.BUKKIT;
        String newFileName = GitHubUpdateChecker.generateNewFileName(currentFileName, version, platform);


        String downloadUrl = GitHubUpdateChecker.getLatestDownloadUrl(platform);


        File tempFile = new File(currentPluginFile.getParentFile(),
                               newFileName + ".temp");


        logger.info("正在从 " + downloadUrl + " 下载新版本...");
        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);

        // 下载并实时计算 SHA-256，带 64MB 上限防磁盘填满
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new Exception("SHA-256 不可用", e);
        }
        long bytesRead;
        try (InputStream rawIn = connection.getInputStream();
             DigestInputStream digestingIn = new DigestInputStream(rawIn, digest);
             ReadableByteChannel rbc = Channels.newChannel(digestingIn);
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            bytesRead = fos.getChannel().transferFrom(rbc, 0, MAX_DOWNLOAD_BYTES + 1);
            if (bytesRead > MAX_DOWNLOAD_BYTES) {
                tempFile.delete();
                throw new IOException("下载文件超过 " + (MAX_DOWNLOAD_BYTES / 1024 / 1024)
                        + "MB 上限，已中止（疑似下载源异常或被劫持）");
            }
        } finally {
            connection.disconnect();
        }
        String sha256 = toHex(digest.digest());

        logger.info("新版本已下载到临时文件: " + tempFile.getName() +
                   " (" + bytesRead + " bytes, SHA-256: " + sha256 + ")");

        // 校验官方 SHA-256（如可获取）
        try {
            String expectedSha256 = normalizeSha256(GitHubUpdateChecker.getLatestSha256(platform));
            if (expectedSha256 != null) {
                if (!expectedSha256.equalsIgnoreCase(sha256)) {
                    tempFile.delete();
                    throw new IOException("下载文件 SHA-256 与官方发布不一致（期望 " + expectedSha256 + "，实际 " + sha256 + "），已拒绝安装，请检查更新源是否被劫持。");
                }
                logger.info("SHA-256 校验通过：与官方发布一致");
            } else {
                logger.warning("无法获取官方 SHA-256（当前更新源未提供），已跳过哈希校验，仅完成结构校验。建议改用 GitHub 直连/代理镜像或手动下载更新。");
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new Exception("SHA-256 校验失败: " + e.getMessage(), e);
        }

        // 校验 jar 包结构（plugin.yml 主类 + manifest 防注入）
        try {
            validatePluginJar(tempFile);
        } catch (Exception e) {
            tempFile.delete();
            throw e;
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

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static String normalizeSha256(String digestValue) {
        if (digestValue == null || digestValue.trim().isEmpty()) {
            return null;
        }
        String value = digestValue.trim();
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1).trim();
        }
        if (!value.matches("^[0-9a-fA-F]{64}$")) {
            return null;
        }
        return value.toLowerCase();
    }

    /**
     * 校验下载的 jar 是否是可安装的 Lengbanlist 插件包：
     * 必须含可解析的 plugin.yml 且主类为 {@link #MANIFEST_MAIN_CLASS}。
     * 不校验 MANIFEST 的 Main-Class —— Bukkit 插件的 jar 从不写该字段。
     */
    static void validatePluginJar(File jarFile) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry pluginYml = jar.getJarEntry("plugin.yml");
            if (pluginYml == null) {
                throw new IOException("下载的 JAR 缺少 plugin.yml，已拒绝安装，请检查更新源。");
            }
            String mainClass = null;
            try (InputStream in = jar.getInputStream(pluginYml)) {
                mainClass = new PluginDescriptionFile(in).getMain();
            } catch (Exception e) {
                throw new IOException("下载的 JAR 中 plugin.yml 无法解析（" + e.getMessage() + "），已拒绝安装，请检查更新源。", e);
            }
            if (!MANIFEST_MAIN_CLASS.equals(mainClass)) {
                throw new IOException("下载的 JAR 的 plugin.yml 主类不是 " + MANIFEST_MAIN_CLASS + "（实际: " + mainClass + "），已拒绝安装，请检查更新源。");
            }
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String declared = manifest.getMainAttributes().getValue("Main-Class");
                if (declared != null && !MANIFEST_MAIN_CLASS.equals(declared)) {
                    throw new IOException("下载的 JAR 清单声明了冲突的主类 " + declared + "，已拒绝安装，请检查更新源。");
                }
            }
        }
    }
}
