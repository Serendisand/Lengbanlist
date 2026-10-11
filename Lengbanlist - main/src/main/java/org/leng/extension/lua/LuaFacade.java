package org.leng.extension.lua;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.Locale;

/**
 * 脚本能看到的全部 Java 能力，就是这一张 {@code leng} 表。
 *
 * <p>白名单式的设计：表上没写的，脚本就够不到。加新能力时要同时想清楚
 * "这个能力出问题会波及什么"——处罚、数据库、文件系统、网络一律不进这张表。
 *
 * <p>没有 {@code leng.id()}：扩展 id 就是目录名。注册表在 {@code onEnable} 之前
 * 就要拿 id 建索引，而脚本是 {@code onEnable} 里才跑的，允许覆写会让索引与自报的 id
 * 对不上——"一个东西两个来源"正是刚被回退的那版设计栽的跟头。
 */
final class LuaFacade {

    private final LuaTable table = new LuaTable();

    LuaFacade(LuaExtension owner, LuaCommandBridge bridge) {
        table.set("name", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                owner.meta("name", LuaValues.arg(args, 1).tojstring());
                return NONE;
            }
        });
        table.set("version", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                owner.meta("version", LuaValues.arg(args, 1).tojstring());
                return NONE;
            }
        });

        table.set("command", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                bridge.register(LuaValues.arg(args, 1).checktable());
                return NONE;
            }
        });

        table.set("log", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                owner.log(LuaValues.optString(args, 2, "info"), LuaValues.arg(args, 1).tojstring());
                return NONE;
            }
        });

        table.set("config", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Object raw = owner.config().get(LuaValues.optString(args, 2, null));
                return raw == null ? LuaValues.arg(args, 1) : LuaValues.of(raw);
            }
        });

        table.set("player", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Player player = LuaValues.onlinePlayer(LuaValues.optString(args, 1, ""));
                return player == null ? NIL : LuaValues.playerProxy(player);
            }
        });

        table.set("online_players", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String prefix = LuaValues.optString(args, 1, "").toLowerCase(Locale.ROOT);
                LuaTable names = new LuaTable();
                int index = 1;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        names.set(index++, LuaValue.valueOf(player.getName()));
                    }
                }
                return names;
            }
        });

        table.set("task", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue body = LuaValues.arg(args, 1);
                if (!body.isfunction()) {
                    return NIL;
                }
                return valueOf(owner.scheduleTask(
                        LuaValues.arg(args, 3).optint(0), LuaValues.arg(args, 2).optint(0), body));
            }
        });

        table.set("cancel", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                owner.cancelTask(LuaValues.arg(args, 1).optint(-1));
                return NONE;
            }
        });
    }

    LuaTable table() {
        return table;
    }
}
