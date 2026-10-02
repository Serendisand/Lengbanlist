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
 * @param name        命令名，不带斜杠，例如 {@code vanish}
 * @param feature     功能开关键，例如 {@code vanish}
 * @param permission  权限节点，例如 {@code lengbanlist.vanish}
 * @param usage       用法串，例如 {@code /lban vanish}
 * @param description 一句话描述，显示在帮助与 {@code /plugins} 里
 * @param aliases     别名，无别名传空列表
 * @param executor    执行器
 * @param tabCompleter 补全器；为 {@code null} 时若 {@code executor} 同时实现了
 *                    {@link TabCompleter}，会自动沿用它
 */
public record CommandSpec(String name,
                          String feature,
                          String permission,
                          String usage,
                          String description,
                          List<String> aliases,
                          CommandExecutor executor,
                          TabCompleter tabCompleter) {

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
    }

    /** 常用写法：无别名、补全器从 executor 推导。 */
    public static CommandSpec of(String name, String feature, String permission,
                                String usage, String description, CommandExecutor executor) {
        return new CommandSpec(name, feature, permission, usage, description, List.of(), executor, null);
    }

    /** 该声明是否完整；核心会拒绝注册不完整的命令，避免把问题推迟到运行时。 */
    public boolean isComplete() {
        return !name.isEmpty() && !feature.isEmpty() && !permission.isEmpty() && !usage.isEmpty();
    }
}
