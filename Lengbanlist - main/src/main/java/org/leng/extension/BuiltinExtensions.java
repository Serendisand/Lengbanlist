package org.leng.extension;

import org.bukkit.command.CommandExecutor;
import org.leng.Lengbanlist;
import org.leng.commands.BanCommands;
import org.leng.commands.MuteCommands;
import org.leng.commands.QueryCommands;
import org.leng.commands.ReportCommands;
import org.leng.commands.StaffCommands;
import org.leng.commands.WarnCommands;
import org.leng.api.CommandSpec;
import org.leng.api.ExtensionContext;
import org.leng.api.LengbanlistExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 内置功能提供者与<b>唯一的命令声明表</b>。
 *
 * <p>本类存在的意义不只是"把硬编码列表挪个地方"，而是让命令表只有一处真相：
 *
 * <ul>
 *   <li>{@link #declarations()} 是静态、不依赖 plugin 的声明表。命令注册
 *       （{@link #registerAll}）与帮助过滤（{@code CommandRegistry.featureForUsage}）
 *       都从它派生，因此<b>不可能出现两处漂移</b>——这正是改造前
 *       {@code CommandRegistry.HELP_FEATURES} 与 {@code specs()} 各自手工维护
 *       同一份 (usage → feature) 关系所埋下的隐患。</li>
 *   <li>功能键只在<b>声明里写一次</b>，由 {@link BuiltinProvider} 统一注入到该提供者的
 *       每条命令上（17 处声明 vs 改造前 20 处重复）。</li>
 * </ul>
 *
 * <p>{@code CommandRegistryTest} 会把这张表与 {@code plugin.yml} 的权限、
 * {@code config.yml} 的开关逐条对照——表写错或忘写，测试就红。
 *
 * <p>Phase 1-3 会把这里的提供者逐个搬到 {@code Lengbanlist-Extensions} 仓库，
 * 届时只需从 {@link #declarations()} 删除对应行，机制本身不再改动。
 */
public final class BuiltinExtensions {

    private BuiltinExtensions() {
    }

    /**
     * 一条内置命令声明。
     *
     * <p>执行器用工厂而不是实例，是为了让本表保持静态——没有 plugin 也能被帮助过滤与测试访问。
     */
    public record Declaration(String feature,
                              String name,
                              String permission,
                              String usage,
                              String description,
                              Function<Lengbanlist, CommandExecutor> executorFactory) {
    }

    /** 每个功能键的展示名，用于日志与未来的 {@code /lban ext list}。 */
    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
            Map.entry("ban", "封禁玩家"),
            Map.entry("ban-ip", "IP 封禁"),
            Map.entry("unban", "解封"),
            Map.entry("warn", "警告"),
            Map.entry("unwarn", "撤销警告"),
            Map.entry("check", "处罚状态查询"),
            Map.entry("report", "玩家举报"),
            Map.entry("admin", "举报管理"),
            Map.entry("info", "插件信息"),
            Map.entry("kick", "踢出玩家"),
            Map.entry("chat-filter", "聊天过滤"),
            Map.entry("setban", "设置封禁时长"),
            Map.entry("history", "处罚历史"),
            Map.entry("mute", "禁言"),
            Map.entry("getip", "IP 归属地"),
            Map.entry("staffchat", "管理员频道"),
            Map.entry("alts", "小号查询"),
            Map.entry("immunity", "权重免疫"),
            // 以下功能没有自己的命令或钩子，行为分散在核心各处（GUI 按钮、监听器、
            // 定时任务、Web 端点）。登记它们是为了让 isFeatureActive 能回答
            // "已安装 且 开启"，而不是回落到"未知功能只看开关"。
            Map.entry("vanish", "管理员隐身"),
            Map.entry("freeze", "冻结玩家"),
            Map.entry("appeal", "封禁申诉"),
            Map.entry("model", "角色模型"),
            Map.entry("tp", "传送"),
            Map.entry("chest-ui", "箱子 GUI"),
            Map.entry("broadcast", "封禁广播"),
            Map.entry("mute-command-block", "禁言期指令屏蔽"),
            Map.entry("ip-association", "IP 关联检测"),
            Map.entry("vpn-detection", "VPN 检测"),
            Map.entry("expiry-reminder", "封禁到期提醒"),
            Map.entry("offline-warn", "离线警告"),
            Map.entry("audit", "审计日志"),
            Map.entry("audit-chain", "审计哈希链"),
            Map.entry("export", "审计导出"),
            Map.entry("sync", "跨服数据同步"),
            Map.entry("rollback", "操作回滚"),
            Map.entry("auto-update", "自动更新"),
            Map.entry("webhook-events", "Webhook 推送"),
            Map.entry("reload", "配置重载"));

    private static final List<Declaration> DECLARATIONS = List.of(
            d("ban", "ban", "lengbanlist.ban",
                    "/ban [-s] <玩家名> <时间/auto> <理由>", "封禁玩家", BanCommands.Ban::new),
            d("ban-ip", "ban-ip", "lengbanlist.banip",
                    "/ban-ip [-s] <IP> <时间/auto> <理由>", "封禁 IP", BanCommands.BanIp::new),
            d("unban", "unban", "lengbanlist.unban",
                    "/unban <玩家名/IP>", "解封玩家", BanCommands.Unban::new),
            d("warn", "warn", "lengbanlist.warn",
                    "/warn <玩家名> <理由>", "警告玩家", WarnCommands.Warn::new),
            d("warn", "warnmsg", "lengbanlist.warnmsg",
                    "/warnmsg <玩家名>", "警告玩家发送违规消息", WarnCommands.WarnMsg::new),
            d("unwarn", "unwarn", "lengbanlist.unwarn",
                    "/unwarn <玩家名> [警告ID]", "移除警告", WarnCommands.Unwarn::new),
            d("check", "check", "lengbanlist.check",
                    "/check <玩家名/IP>", "检查玩家或 IP 的封禁状态", QueryCommands.Check::new),
            d("report", "report", "lengbanlist.report",
                    "/report <玩家名> <理由>", "举报玩家", ReportCommands.Report::new),
            d("admin", "admin", "lengbanlist.admin",
                    "/admin <子命令>", "管理举报", ReportCommands.AdminReport::new),
            d("info", "info", "lengbanlist.info",
                    "/info", "显示插件信息", QueryCommands.Info::new),
            d("kick", "kick", "lengbanlist.kick",
                    "/kick <玩家名> [理由]", "踢出玩家", BanCommands.Kick::new),
            d("chat-filter", "allowmsg", "lengbanlist.allowmsg",
                    "/allowmsg <玩家名>", "允许玩家发送消息", MuteCommands.AllowMsg::new),
            d("setban", "setban", "lengbanlist.setban",
                    "/setban <玩家名/IP> <时间/forever/auto> <理由>", "设置玩家的封禁时间", BanCommands.SetBan::new),
            d("history", "history", "lengbanlist.history",
                    "/history <玩家名>", "查询玩家的处罚历史", QueryCommands.History::new),
            d("mute", "mute", "lengbanlist.mute",
                    "/mute [-s] <玩家名> <时间/auto> <原因>", "禁言玩家", MuteCommands.Mute::new),
            d("mute", "unmute", "lengbanlist.mute",
                    "/unmute [-s] <玩家名>", "解除禁言", MuteCommands.Unmute::new),
            d("mute", "listmute", "lengbanlist.listmute",
                    "/listmute", "查看禁言列表", MuteCommands.ListMute::new),
            d("getip", "getip", "lengbanlist.getip",
                    "/getip [玩家名]", "查询玩家 IP 地理位置", QueryCommands.GetIp::new),
            d("staffchat", "sc", "lengbanlist.staffchat",
                    "/sc <内容>", "工作频道聊天", StaffCommands.StaffChat::new),
            d("alts", "alts", "lengbanlist.alts",
                    "/alts <玩家名>", "查询玩家同IP小号", Lengbanlist::getAltsCommand));

    private static Declaration d(String feature, String name, String permission, String usage,
                                 String description, Function<Lengbanlist, CommandExecutor> factory) {
        return new Declaration(feature, name, permission, usage, description, factory);
    }

    /** 唯一的命令声明表；顺序即注册顺序。 */
    public static List<Declaration> declarations() {
        return DECLARATIONS;
    }

    public static String displayName(String feature) {
        return DISPLAY_NAMES.getOrDefault(feature, feature);
    }

    /**
     * 把内置命令按功能键分组，注册为内置提供者。
     *
     * <p>执行器在此处一次性构造。改造前每次刷新都会重建全部执行器，而所有执行器都只在
     * 执行/补全时才读配置，因此构造一次不会改变行为，只是省掉重复分配。
     */
    public static void registerAll(Lengbanlist plugin, ExtensionRegistry registry) {
        Map<String, List<CommandSpec>> byFeature = new LinkedHashMap<>();
        for (Declaration declaration : DECLARATIONS) {
            CommandSpec spec = CommandSpec.of(
                    declaration.name(),
                    declaration.feature(),
                    declaration.permission(),
                    declaration.usage(),
                    declaration.description(),
                    declaration.executorFactory().apply(plugin));
            byFeature.computeIfAbsent(declaration.feature(), key -> new ArrayList<>()).add(spec);
        }
        for (Map.Entry<String, List<CommandSpec>> entry : byFeature.entrySet()) {
            try {
                registry.register(new BuiltinProvider(entry.getKey(), entry.getValue()));
            } catch (Exception e) {
                plugin.getLogger().warning("注册内置功能 " + entry.getKey() + " 失败: " + e);
            }
        }
    }
    /**
     * 注册<b>只提供钩子、没有命令</b>的内置功能。
     *
     * <p>免疫系统就是这一类：它不注册任何命令，只在处罚决策点插一条策略。
     * 把它登记为提供者，是为了让 {@code isFeatureActive("immunity")} 能正确回答
     * "已安装 且 开启"，而不是回落到"未知功能只看开关"。
     */
    public static void registerHooks(Lengbanlist plugin, ExtensionRegistry registry) {
        registerHook(plugin, registry, "immunity",
                org.leng.api.PunishmentDecisionHook.class, plugin.getImmunityManager());
        registerHook(plugin, registry, "escalation",
                org.leng.api.DurationPolicyHook.class, plugin.getEscalationManager());
    }

    private static <T> void registerHook(Lengbanlist plugin, ExtensionRegistry registry,
                                         String feature, Class<T> type, T hook) {
        try {
            registry.register(new HookProvider<>(feature, type, hook));
        } catch (Exception e) {
            plugin.getLogger().warning("注册内置钩子 " + feature + " 失败: " + e);
        }
    }

    /**
     * 把剩余的功能键登记为"只声明功能、不带任何注册项"的提供者。
     *
     * <p>这些功能的行为分散在核心各处（GUI 按钮、监听器、定时任务、Web 端点），
     * 但它们的开关必须是"已安装 且 开启"这条语义的一部分——否则
     * {@link ExtensionRegistry#isFeatureActive(String)} 会把这些键当作未知功能
     * 而只看向开关，Phase 3 把它们搬出去之后就会静默失效。
     *
     * <p>已经有命令或钩子提供者的功能键会按扩展 id 跳过。
     */
    public static void registerFeatures(Lengbanlist plugin, ExtensionRegistry registry) {
        for (Map.Entry<String, String> entry : DISPLAY_NAMES.entrySet()) {
            String feature = entry.getKey();
            if (registry.isRegistered(feature)) {
                continue;
            }
            try {
                registry.register(new FeatureOnlyProvider(feature));
            } catch (Exception e) {
                plugin.getLogger().warning("注册内置功能键 " + feature + " 失败: " + e);
            }
        }
    }

    /** 只声明功能键的提供者：没有任何命令或钩子。 */
    private static final class FeatureOnlyProvider implements LengbanlistExtension {

        private final String id;

        private FeatureOnlyProvider(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return displayName(id);
        }

        @Override
        public String version() {
            return "";
        }

        @Override
        public java.util.Set<String> features() {
            return java.util.Set.of(id);
        }

        @Override
        public void onEnable(ExtensionContext context) {
            // 无需注册任何东西：功能键本身就是要登记的全部内容
        }
    }

    /** 核心 jar 内的功能提供者：没有自己的 Plugin，配置落在核心数据目录下。 */
    private static final class BuiltinProvider implements LengbanlistExtension {

        private final String id;
        private final List<CommandSpec> commands;

        private BuiltinProvider(String id, List<CommandSpec> commands) {
            this.id = id;
            this.commands = List.copyOf(commands);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return displayName(id);
        }

        @Override
        public String version() {
            return "";
        }

        @Override
        public void onEnable(ExtensionContext context) {
            context.commands().register(commands.toArray(new CommandSpec[0]));
        }
    }

    /** 无命令、只挂一条钩子的内置提供者。 */
    private static final class HookProvider<T> implements LengbanlistExtension {

        private final String id;
        private final Class<T> hookType;
        private final T hook;

        private HookProvider(String id, Class<T> hookType, T hook) {
            this.id = id;
            this.hookType = hookType;
            this.hook = hook;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return displayName(id);
        }

        @Override
        public String version() {
            return "";
        }

        @Override
        public java.util.Set<String> features() {
            return java.util.Set.of(id);
        }

        @Override
        public void onEnable(ExtensionContext context) {
            context.hooks().register(hookType, hook);
        }
    }
}
