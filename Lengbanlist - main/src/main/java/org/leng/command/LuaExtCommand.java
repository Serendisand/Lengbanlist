package org.leng.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.leng.Lengbanlist;
import org.leng.extension.lua.LuaExtension;
import org.leng.extension.lua.LuaLoader;
import org.leng.util.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /lban ext} —— Lua 扩展的管理入口。
 *
 * <pre>
 *   /lban ext list              列出已加载的扩展、开关状态与加载失败原因
 *   /lban ext reload            重新扫描 extensions/ 并全部重跑
 *   /lban ext enable  &lt;id&gt;      打开扩展（写扩展自己的 config.yml）
 *   /lban ext disable &lt;id&gt;      关掉扩展（同上）
 * </pre>
 *
 * <p>开关写的是<b>扩展自己目录</b>里的 {@code config.yml}（{@code extensions/<id>/config.yml}），
 * 不碰核心配置：核心配置里根本没有它的键，也就不会出现"同一份开关存了两处"的僵尸配置。
 */
public final class LuaExtCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "lengbanlist.reload";
    private static final List<String> ACTIONS = List.of("list", "reload", "enable", "disable");

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

        String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> list(sender, loader);
            case "reload" -> {
                loader.reload();
                Utils.sendMessage(sender, plugin.prefix() + "§a扩展已重新扫描加载喵~");
                list(sender, loader);
            }
            case "enable", "disable" -> toggle(sender, loader, action, args);
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
        try {
            if (!loader.setEnabled(id, enabled)) {
                Utils.sendMessage(sender, plugin.prefix() + "§c没有这个扩展喵: §f" + id
                        + "§c（找不到 " + loader.directory() + "/" + id + ".lua）");
                return;
            }
        } catch (IOException e) {
            Utils.sendMessage(sender, plugin.prefix() + "§c写扩展配置失败: §f" + e.getMessage());
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

    private void list(CommandSender sender, LuaLoader loader) {
        List<LuaExtension> extensions = loader.extensions();
        Utils.sendMessage(sender, plugin.prefix() + "§6§lLua 扩展 §7(" + extensions.size() + " 个)");
        if (extensions.isEmpty()) {
            Utils.sendMessage(sender, "§7把脚本放进 §f" + loader.directory() + "/<扩展名>.lua§7 就能用了喵"
                    + "（它的配置会生成在 " + loader.directory() + "/<扩展名>/config.yml）");
        }
        for (LuaExtension extension : extensions) {
            Utils.sendMessage(sender, "§b✦ §f" + extension.id() + " §7" + versionText(extension)
                    + " " + stateText(loader, extension) + " §7" + extension.name());
        }
        for (Map.Entry<String, String> failure : loader.failures().entrySet()) {
            Utils.sendMessage(sender, "§c✖ §f" + failure.getKey() + " §7- " + failure.getValue());
        }
    }

    private static String versionText(LuaExtension extension) {
        String version = extension.version();
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
        Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: §f/lban ext <list|reload|enable|disable> [扩展名]");
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
        }
        return completions;
    }
}
