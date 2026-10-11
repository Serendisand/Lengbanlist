package org.leng.extension.lua;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.leng.api.CommandRegistrar;
import org.leng.api.CommandSpec;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * 把脚本里的 {@code leng.command{...}} 翻译成核心的 {@link CommandSpec}。
 *
 * <p>翻译完就交给 {@link CommandRegistrar}，与内置命令走同一条注册路径——权限、
 * 别名、补全、{@code /lban help} 过滤、子命令分派因此全部自动一致，
 * 不需要在 Lua 这边另起一套。
 *
 * <p>回调签名统一是 {@code run(ctx)} 与 {@code complete(ctx)}：参数、发送者、
 * 回复入口都在 {@code ctx} 上，补全与执行共享同一个形状。
 */
final class LuaCommandBridge {

    private final LuaExtension owner;
    private final CommandRegistrar registrar;

    LuaCommandBridge(LuaExtension owner, CommandRegistrar registrar) {
        this.owner = owner;
        this.registrar = registrar;
    }

    void register(LuaTable declaration) {
        String name = text(declaration, "name", "");
        if (name.isEmpty()) {
            throw new LuaError("leng.command 缺少 name");
        }
        LuaValue body = declaration.get("run");
        if (!body.isfunction()) {
            throw new LuaError("命令 " + name + " 缺少 run 函数");
        }

        String feature = text(declaration, "feature", owner.id());
        String permission = text(declaration, "permission", "lengbanlist." + feature);
        String usage = text(declaration, "usage", "/" + name);
        String description = text(declaration, "description", text(declaration, "desc", name));
        String parent = text(declaration, "parent", "");
        LuaValue completer = declaration.get("complete");
        boolean subcommand = !parent.isEmpty();

        registrar.register(new CommandSpec(name, feature, permission, usage, description,
                aliases(declaration.get("aliases")),
                new Executor(body, usage, subcommand),
                completer.isfunction() ? new Completer(completer, usage) : null,
                parent));
    }

    private static String text(LuaTable declaration, String key, String fallback) {
        LuaValue value = declaration.get(key);
        return value.isnil() ? fallback : value.tojstring();
    }

    private static List<String> aliases(LuaValue raw) {
        List<String> aliases = new ArrayList<>();
        if (!raw.istable()) {
            return aliases;
        }
        int count = raw.len().toint();
        for (int i = 1; i <= count; i++) {
            aliases.add(raw.get(i).tojstring());
        }
        return aliases;
    }

    private LuaTable context(CommandSender sender, String label, String[] args, String usage) {
        LuaTable context = new LuaTable();
        LuaTable arguments = new LuaTable();
        for (int i = 0; i < args.length; i++) {
            arguments.set(i + 1, LuaValue.valueOf(args[i]));
        }
        context.set("args", arguments);
        context.set("label", LuaValue.valueOf(label));
        context.set("sender", LuaValues.senderProxy(sender));
        context.set("reply", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs callArgs) {
                sender.sendMessage(owner.plugin().prefix() + LuaValues.arg(callArgs, 1).tojstring());
                return NONE;
            }
        });
        context.set("usage", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs callArgs) {
                sender.sendMessage(owner.plugin().prefix() + "§c用法: §f" + usage);
                return NONE;
            }
        });
        return context;
    }

    /**
     * 返回 false 表示"没处理"：顶层命令交给 Bukkit 回落到默认用法提示；
     * <b>子命令不能返回 false</b>——那会被 {@code /lban} 的分派当成"没被认领"，
     * 于是脚本明明执行了，玩家却看到"未知子命令"。
     */
    private final class Executor implements CommandExecutor {

        private final LuaValue body;
        private final String usage;
        private final boolean subcommand;

        private Executor(LuaValue body, String usage, boolean subcommand) {
            this.body = body;
            this.usage = usage;
            this.subcommand = subcommand;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            Varargs result = owner.runScript(body, context(sender, label, args, usage), label);
            LuaValue first = result.arg1();
            if (first.isboolean() && !first.toboolean()) {
                if (subcommand) {
                    sender.sendMessage(owner.plugin().prefix() + "§c用法: §f" + usage);
                    return true;
                }
                return false;
            }
            return true;
        }
    }

    private final class Completer implements TabCompleter {

        private final LuaValue body;
        private final String usage;

        private Completer(LuaValue body, String usage) {
            this.body = body;
            this.usage = usage;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            Varargs result = owner.runScript(body, context(sender, alias, args, usage), alias + " 补全");
            List<String> completions = new ArrayList<>();
            LuaValue first = result.arg1();
            if (first.istable()) {
                int count = first.len().toint();
                for (int i = 1; i <= count; i++) {
                    completions.add(first.get(i).tojstring());
                }
            }
            return completions;
        }
    }
}
