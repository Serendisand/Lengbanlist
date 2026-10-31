package org.leng.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.leng.Lengbanlist;
import org.leng.extension.ExtensionRegistry;
import org.leng.extension.lua.LuaExtension;
import org.leng.extension.lua.LuaLoader;
import org.leng.integration.ExtensionCloudManager;
import org.leng.util.ErrorLog;
import org.leng.util.SchedulerUtils;
import org.leng.util.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * {@code /lban ext} —— 扩展的管理入口。
 *
 * <pre>
 *   /lban ext list              列出已装的扩展与云端市场条目
 *   /lban ext reload            重新扫描 extensions/ 并全部重跑
 *   /lban ext refresh           拉取云端扩展索引
 *   /lban ext enable  &lt;id&gt;      打开扩展（Lua 写脚本目录，Java 写插件自己的 config.yml）
 *   /lban ext disable &lt;id&gt;      关掉扩展（同上）
 *   /lban ext install &lt;id|all&gt;  从云端下载安装
 * </pre>
 *
 * <p>命名与 {@code /lban models} 对齐：{@code list} 同时看本地与云端，{@code refresh}
 * 拉索引，{@code install} 下载。Lua 扩展的开关写 {@code extensions/<id>/config.yml}；
 * Java 扩展写它自己的 {@code plugins/<扩展名>/config.yml}，两边都不在核心配置里。
 */
public final class LuaExtCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "lengbanlist.reload";
    private static final List<String> ACTIONS =
            List.of("list", "reload", "enable", "disable", "refresh", "install");

    private final Lengbanlist plugin;

    public LuaExtCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
            return true;
        }
        LuaLoader loader = plugin.getLuaLoader();
        if (loader == null) {
            Utils.sendMessage(sender, plugin.prefix() + "§cLua 扩展子系统还没起来喵。");
            return true;
        }

        if (args.length == 0) {
            usage(sender);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> list(sender, loader);
            case "reload" -> {
                loader.reload();
                Utils.sendMessage(sender, plugin.prefix() + "§a扩展已重新扫描加载喵~");
                list(sender, loader);
            }
            case "enable", "disable" -> toggle(sender, loader, action, args);
            case "refresh" -> cmdRefresh(sender);
            case "install" -> cmdInstall(sender, loader, args);
            default -> usage(sender);
        }
        return true;
    }

    private void toggle(CommandSender sender, LuaLoader loader, String action, String[] args) {
        if (args.length < 2) {
            usage(sender);
            return;
        }
        String id = args[1];
        boolean enabled = action.equals("enable");
        boolean handledByLua;
        try {
            handledByLua = loader.setEnabled(id, enabled);
        } catch (IOException e) {
            Utils.sendMessage(sender, plugin.prefix() + "§c写扩展配置失败: §f" + e.getMessage());
            return;
        }
        if (!handledByLua) {
            if (toggleJava(sender, id, enabled)) {
                return;
            }
            Utils.sendMessage(sender, plugin.prefix() + "§c没有这个扩展喵: §f" + id
                    + "§c（Lua 找 " + loader.directory() + "/" + id + ".lua，"
                    + "Java 扩展需要已经装在 plugins/ 里）");
            return;
        }
        // setEnabled 会重新加载，加载失败时状态就在 list 里，直接显示出来省得管理员再问一次
        if (enabled && !loader.isRunning(id)) {
            Utils.sendMessage(sender, plugin.prefix() + "§c扩展 " + id + " 启用了，但没能加载起来喵：");
            list(sender, loader);
            return;
        }
        Utils.sendMessage(sender, plugin.prefix() + (enabled ? "§a已启用扩展 " : "§e已禁用扩展 ") + "§f" + id);
    }

    /**
     * 不是 Lua 扩展时，试试当作 Java 扩展。
     *
     * <p>Java 扩展通过写它自己 {@code config.yml} 的 {@code enabled} 来开关；
     * 注册表会刷新命令表，让命令立刻消失/回来。
     */
    private boolean toggleJava(CommandSender sender, String id, boolean enabled) {
        ExtensionRegistry registry = plugin.getExtensionRegistry();
        if (registry == null) {
            return false;
        }
        if (registry.setExternalEnabled(id, enabled)) {
            Utils.sendMessage(sender, plugin.prefix()
                    + (enabled ? "§a已启用 Java 扩展 " : "§e已禁用 Java 扩展 ") + "§f" + id);
            return true;
        }
        if (registry.ids().contains(id)) {
            Utils.sendMessage(sender, plugin.prefix() + "§c扩展 §f" + id
                    + "§c 不能从命令开关，请改它自己的 config.yml。");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ 云端市场

    private <T> void runOffThread(CommandSender sender, Supplier<T> task, Consumer<T> onDone) {
        CompletableFuture.supplyAsync(task)
                .thenAccept(result -> SchedulerUtils.runTask(plugin, sender, () -> onDone.accept(result)))
                .exceptionally(throwable -> {
                    ErrorLog.record(plugin, "[ext] 异步任务执行失败", throwable);
                    SchedulerUtils.runTask(plugin, sender, () ->
                            Utils.sendMessage(sender, plugin.prefix() + "§c内部错误，请查看控制台日志"));
                    return null;
                });
    }

    private ExtensionCloudManager cloudOrNull(CommandSender sender) {
        ExtensionCloudManager cloud = plugin.getExtensionCloudManager();
        if (!cloud.enabled()) {
            Utils.sendMessage(sender, plugin.prefix()
                    + "§c云端扩展市场已在 config.yml 里关闭（extensions-cloud.enabled）");
            return null;
        }
        return cloud;
    }

    private void cmdRefresh(CommandSender sender) {
        ExtensionCloudManager cloud = cloudOrNull(sender);
        if (cloud == null) {
            return;
        }
        Utils.sendMessage(sender, plugin.prefix() + "§e正在拉取云端扩展索引喵…");
        runOffThread(sender, cloud::fetchIndex, result -> {
            if (result.isEmpty()) {
                Utils.sendMessage(sender, plugin.prefix() + "§c索引拉取失败，请检查网络或稍后重试。");
                return;
            }
            ExtensionCloudManager.ExtensionIndex index = result.get();
            Utils.sendMessage(sender, plugin.prefix() + "§a索引已更新：共 " + index.extensions().size()
                    + " 个扩展" + (index.updated() == null ? "" : "（更新于 " + index.updated() + "）"));
            Utils.sendMessage(sender, plugin.prefix()
                    + "§7输入 §f/lban ext list §7查看，§f/lban ext install <扩展名> §7安装");
        });
    }

    /** 云端条目：和 {@code /lban models list} 一样，同一个 list 里看本地 + 云端。 */
    private void listCloudExtensions(CommandSender sender) {
        ExtensionCloudManager cloud = plugin.getExtensionCloudManager();
        if (!cloud.enabled()) {
            return;
        }
        Utils.sendMessage(sender, plugin.prefix() + "§6§l云端扩展 §7(市场)");
        runOffThread(sender, cloud::getIndex, result -> {
            if (result.isEmpty()) {
                Utils.sendMessage(sender, "§7  索引不可用，输入 §f/lban ext refresh §7重试");
                return;
            }
            for (ExtensionCloudManager.ExtensionInfo info : result.get().extensions()) {
                String type = info.kind() == ExtensionCloudManager.Kind.JAR ? "§b[Jar]" : "§d[Lua]";
                String state = !info.ready() ? "§7[未上架]"
                        : cloud.isInstalled(info) ? "§a[已安装]" : "§7[未安装]";
                String name = info.name() == null || info.name().isBlank() ? info.id() : info.name();
                Utils.sendMessage(sender, type + " " + state + " §f" + name
                        + " §7(" + info.id() + " v" + info.version() + ")");
            }
        });
    }

    private void cmdInstall(CommandSender sender, LuaLoader loader, String[] args) {
        if (args.length < 2) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: §f/lban ext install <扩展名|all>");
            return;
        }
        ExtensionCloudManager cloud = cloudOrNull(sender);
        if (cloud == null) {
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (id.equals("all")) {
            Utils.sendMessage(sender, plugin.prefix() + "§e正在安装全部云端扩展喵…");
            runOffThread(sender, cloud::installAll, batch -> {
                if (batch.detail() != null && !batch.detail().isBlank()) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c" + batch.detail());
                    return;
                }
                Utils.sendMessage(sender, plugin.prefix() + "§a已安装 " + batch.installed()
                        + " 个，跳过 " + batch.skipped() + " 个，失败 " + batch.failed() + " 个");
                if (batch.luaInstalled() && loader != null) {
                    loader.reload();
                    Utils.sendMessage(sender, plugin.prefix() + "§aLua 扩展已重载");
                }
                Utils.sendMessage(sender, plugin.prefix() + "§7jar 扩展需要重启服务器才会生效");
            });
            return;
        }
        Utils.sendMessage(sender, plugin.prefix() + "§e正在安装扩展 " + id + " 喵…");
        runOffThread(sender, () -> cloud.install(id), outcome -> reportInstall(sender, loader, id, outcome));
    }

    private void reportInstall(CommandSender sender, LuaLoader loader, String id,
                               ExtensionCloudManager.InstallOutcome outcome) {
        switch (outcome.result()) {
            case INSTALLED -> {
                if (outcome.kind() == ExtensionCloudManager.Kind.LUA) {
                    if (loader != null) {
                        loader.reload();
                    }
                    Utils.sendMessage(sender, plugin.prefix() + "§a扩展 " + id
                            + " 已安装到 §f" + outcome.target() + " §a并已重载。");
                } else {
                    Utils.sendMessage(sender, plugin.prefix() + "§a扩展 " + id
                            + " 已下载到 §f" + outcome.target());
                    Utils.sendMessage(sender, plugin.prefix() + "§e重启服务器后生效。");
                }
                if (outcome.detail() != null && !outcome.detail().isBlank()) {
                    Utils.sendMessage(sender, "§7" + outcome.detail());
                }
            }
            case ALREADY_INSTALLED -> Utils.sendMessage(sender, plugin.prefix()
                    + "§e扩展 " + id + " 已是最新版本。");
            case NOT_READY -> Utils.sendMessage(sender, plugin.prefix()
                    + "§e扩展 " + id + " 云端尚未上架，过阵子再来看看喵。");
            case INCOMPATIBLE -> Utils.sendMessage(sender, plugin.prefix()
                    + "§c扩展 " + id + " 与当前版本不兼容：" + outcome.detail());
            case NOT_FOUND -> Utils.sendMessage(sender, plugin.prefix()
                    + "§c云端没有找到扩展 " + id + "，试试 §f/lban ext list");
            default -> Utils.sendMessage(sender, plugin.prefix()
                    + "§c扩展 " + id + " 安装失败：" + outcome.detail());
        }
    }

    /**
     * 统一的扩展列表：和 {@code /lban models list} 一样只有一张表——
     * 云端索引里的条目按本地状态标记，本地有、云端没有的补在后面。
     */
    private void list(CommandSender sender, LuaLoader loader) {
        ExtensionCloudManager cloud = plugin.getExtensionCloudManager();
        Utils.sendMessage(sender, plugin.prefix() + "§e正在读取扩展列表喵…");
        Supplier<Optional<ExtensionCloudManager.ExtensionIndex>> task;
        if (cloud.enabled()) {
            task = cloud::getIndex;
        } else {
            task = () -> Optional.<ExtensionCloudManager.ExtensionIndex>empty();
        }
        runOffThread(sender, task, result -> {
            Utils.sendMessage(sender, "§7--§b Lengbanlist 扩展列表 §7--");
            Map<String, LocalExtension> local = localExtensions(loader);
            if (result.isPresent()) {
                for (ExtensionCloudManager.ExtensionInfo info : result.get().extensions()) {
                    LocalExtension mine = local.remove(info.id());
                    if (mine != null) {
                        sendLocalLine(sender, mine);
                    } else if (!info.ready()) {
                        sendLine(sender, "§7[未上架]", info.name(), info.id(), info.version(), info.kind());
                    } else {
                        sendLine(sender, "§7[未安装]", info.name(), info.id(), info.version(), info.kind());
                    }
                }
            } else {
                Utils.sendMessage(sender, "§7云端索引不可用，仅显示本地扩展（§f/lban ext refresh§7 重试）");
            }
            for (LocalExtension mine : local.values()) {
                sendLocalLine(sender, mine);
            }
            for (Map.Entry<String, String> failure : loader.failures().entrySet()) {
                Utils.sendMessage(sender, "§c✖ §f" + failure.getKey() + " §7- " + failure.getValue());
            }
            if (result.isPresent() && result.get().extensions().isEmpty()
                    && local.isEmpty() && loader.failures().isEmpty()) {
                Utils.sendMessage(sender, "§7还没有安装任何扩展，输入 §f/lban ext install <扩展名> §7装一个");
            }
        });
    }

    /** 本地已装的扩展（Lua + Java），键为扩展 id。 */
    private Map<String, LocalExtension> localExtensions(LuaLoader loader) {
        Map<String, LocalExtension> result = new LinkedHashMap<>();
        for (LuaExtension extension : loader.extensions()) {
            result.put(extension.id(), new LocalExtension(extension.id(), extension.name(),
                    extension.version(), stateText(loader, extension), ExtensionCloudManager.Kind.LUA));
        }
        ExtensionRegistry registry = plugin.getExtensionRegistry();
        if (registry != null) {
            for (ExtensionRegistry.ExtensionInfo extension : registry.externalExtensions()) {
                result.put(extension.id(), new LocalExtension(extension.id(), extension.name(),
                        extension.version(),
                        extension.enabled() ? "§a[启用]" : "§e[已禁用]",
                        ExtensionCloudManager.Kind.JAR));
            }
        }
        return result;
    }

    private void sendLocalLine(CommandSender sender, LocalExtension extension) {
        sendLine(sender, extension.state(), extension.name(), extension.id(),
                extension.version(), extension.kind());
    }

    private void sendLine(CommandSender sender, String state, String name, String id,
                          String version, ExtensionCloudManager.Kind kind) {
        String type = kind == ExtensionCloudManager.Kind.JAR ? "§b[Jar]" : "§d[Lua]";
        String display = name == null || name.isBlank() ? id : name;
        Utils.sendMessage(sender, state + " §f" + display
                + " §7(" + id + " " + versionText(version) + ") " + type);
    }

    private record LocalExtension(String id, String name, String version, String state,
                                  ExtensionCloudManager.Kind kind) {
    }

    private static String versionText(String version) {
        return version == null || version.isEmpty() ? "v?" : "v" + version;
    }

    /**
     * 一个扩展在任何时刻只可能有这四种状态，按"最需要管理员处理"的排前面：
     * 被强制停用（脚本炸了）→ 被禁用（配置里关着）→ 没挂在注册表上 → 正常。
     */
    private static String stateText(LuaLoader loader, LuaExtension extension) {
        String reason = extension.disabledReason();
        if (!reason.isEmpty()) {
            return "§c[已停用] §7" + reason;
        }
        if (!extension.enabled()) {
            return "§e[已禁用]";
        }
        if (!loader.isRunning(extension.id())) {
            return "§c[未加载]";
        }
        return "§a[启用]";
    }

    private void usage(CommandSender sender) {
        Utils.sendMessage(sender, "§7--§b Lengbanlist 扩展管理 §7--");
        Utils.sendMessage(sender, "§6/lban ext list §7- §3列出本地 + 云端扩展");
        Utils.sendMessage(sender, "§6/lban ext refresh §7- §3拉取云端扩展索引");
        Utils.sendMessage(sender, "§6/lban ext install <扩展名|all> §7- §3下载安装");
        Utils.sendMessage(sender, "§6/lban ext reload §7- §3重新扫描 extensions/ 并全部重跑");
        Utils.sendMessage(sender, "§6/lban ext enable <扩展名> §7- §3打开扩展");
        Utils.sendMessage(sender, "§6/lban ext disable <扩展名> §7- §3关掉扩展");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission(PERMISSION)) {
            return completions;
        }
        LuaLoader loader = plugin.getLuaLoader();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            for (String action : ACTIONS) {
                if (action.startsWith(prefix)) {
                    completions.add(action);
                }
            }
            return completions;
        }
        if (loader != null && args.length == 2
                && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (LuaExtension extension : loader.extensions()) {
                if (extension.id().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    completions.add(extension.id());
                }
            }
            // 加载失败的扩展也要能补出来：正因为失败才更需要 enable/disable 去收拾
            for (String id : loader.failures().keySet()) {
                if (id.toLowerCase(Locale.ROOT).startsWith(prefix) && !completions.contains(id)) {
                    completions.add(id);
                }
            }
            ExtensionRegistry registry = plugin.getExtensionRegistry();
            if (registry != null) {
                for (ExtensionRegistry.ExtensionInfo extension : registry.externalExtensions()) {
                    String id = extension.id();
                    if (id.toLowerCase(Locale.ROOT).startsWith(prefix) && !completions.contains(id)) {
                        completions.add(id);
                    }
                }
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("install")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if ("all".startsWith(prefix)) {
                completions.add("all");
            }
            ExtensionCloudManager cloud = plugin.getExtensionCloudManager();
            if (cloud.enabled()) {
                cloud.cachedIndexOnly().ifPresent(index -> {
                    for (ExtensionCloudManager.ExtensionInfo info : index.extensions()) {
                        if (info.ready() && info.id().startsWith(prefix) && !completions.contains(info.id())) {
                            completions.add(info.id());
                        }
                    }
                });
            }
        }
        return completions;
    }
}
