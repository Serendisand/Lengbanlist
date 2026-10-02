package org.leng.api;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Objects;

/**
 * 一条命令的完整声明。
 *
 * <p>之所以连 {@code feature} 与 {@code usage} 都要显式声明，是因为它们不只是文档：
 *
 * <ul>
 *   <li>{@code feature} 是该命令的总开关键（对应 {@code config.yml} 的
 *       {@code features.<key>} / {@code extensions.yml}）。关闭后命令不会注册，
 *       名字可以留给别的插件。</li>
 *   <li>{@code usage} 会被 {@code /lban help} 用来做功能过滤；写法必须与帮助表一致，
 *       否则该命令在帮助里会永远显示（或永远不显示）。</li>
 * </ul>
 *
 * <p><b>{@link #parent} 决定命令挂在哪一层</b>，这直接影响用户体验：
 *
 * <ul>
 *   <li>空串（默认）：<b>顶层命令</b>，例如 {@code /ban}、{@code /getip}。
 *       核心会把它注册进 Bukkit 的命令表。</li>
 *   <li>非空：<b>子命令</b>，例如 {@code parent = "lban"} 表示 {@code /lban vanish}。
 *       核心<b>不会</b>为它注册独立的命令名，而是挂进父命令的分派表。</li>
 * </ul>
 *
 * <p>之所以要有子命令这一档，是因为核心历史上的命令分两种形态，而把功能搬进扩展时
 * 必须原样保留：{@code /ban} 这类是顶层命令，而 {@code /lban vanish} 这类是
 * {@code /lban} 的子命令。若强行让所有扩展都注册顶层命令，{@code /lban vanish}
 * 就会变成 {@code /vanish}——对老用户是可见的破坏性变更。
 *
 * @param name        命令名，不带斜杠，例如 {@code vanish}
 * @param feature     功能开关键，例如 {@code vanish}
 * @param permission  权限节点，例如 {@code lengbanlist.vanish}
 * @param usage       用法串，例如 {@code /lban vanish}
 * @param description 一句话描述，显示在帮助与 {@code /plugins} 里
 * @param aliases     别名，无别名传空列表
 * @param executor    执行器
 * @param tabCompleter 补全器；为 {@code null} 时若 {@code executor} 同时实现了
 *                    {@link TabCompleter}，会自动沿用它
 * @param parent      父命令名；空串表示顶层命令
 */
public record CommandSpec(String name,
                          String feature,
                          String permission,
                          String usage,
                          String description,
                          List<String> aliases,
                          CommandExecutor executor,
                          TabCompleter tabCompleter,
                          String parent) {

    public CommandSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(executor, "executor");
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        if (tabCompleter == null && executor instanceof TabCompleter completer) {
            tabCompleter = completer;
        }
        feature = feature == null ? "" : feature;
        permission = permission == null ? "" : permission;
        usage = usage == null ? "" : usage;
        description = description == null ? "" : description;
        parent = parent == null ? "" : parent;
    }

    /**
     * 兼容旧签名。
     *
     * <p>不带 {@code parent} 即顶层命令，与加入子命令支持之前的行为完全一致；
     * 保留它是为了让既有调用方与第三方扩展不必因为新增一个能力而改代码。
     */
    public CommandSpec(String name, String feature, String permission, String usage,
                       String description, List<String> aliases,
                       CommandExecutor executor, TabCompleter tabCompleter) {
        this(name, feature, permission, usage, description, aliases, executor, tabCompleter, "");
    }

    /** 常用写法：顶层命令、无别名、补全器从 executor 推导。 */
    public static CommandSpec of(String name, String feature, String permission,
                                 String usage, String description, CommandExecutor executor) {
        return new CommandSpec(name, feature, permission, usage, description,
                List.of(), executor, null, "");
    }

    /**
     * 常用写法：挂到 {@code parent} 下的子命令，例如 {@code sub("vanish", "lban", ...)}
     * 表示 {@code /lban vanish}。
     */
    public static CommandSpec sub(String name, String parent, String feature, String permission,
                                  String usage, String description, CommandExecutor executor) {
        return new CommandSpec(name, feature, permission, usage, description,
                List.of(), executor, null, parent);
    }

    /** 是否为子命令；{@code false} 表示顶层命令。 */
    public boolean isSubcommand() {
        return !parent.isEmpty();
    }

    /** 该声明是否完整；核心会拒绝注册不完整的命令，避免把问题推迟到运行时。 */
    public boolean isComplete() {
        return !name.isEmpty() && !feature.isEmpty() && !permission.isEmpty() && !usage.isEmpty();
    }
}
