package org.leng.extension.lua;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.Locale;

/**
 * Java 值 ↔ Lua 值的翻译，以及脚本侧的参数取用约定。
 *
 * <p>暴露给脚本的 Bukkit 对象一律是<b>代理表</b>而不是 Java 对象本身：
 * 脚本拿到的是一张带字段与函数的 Lua 表，Java 引用关在闭包里。
 * 这样脚本无法顺着对象图摸到别的东西，也不会因为玩家下线而拿到悬空引用。
 */
final class LuaValues {

    private LuaValues() {
    }

    /**
     * 取倒数第 n 个参数。
     *
     * <p>方法既能写成 {@code player:teleport(...)} 也能写成 {@code player.teleport(...)}——
     * 冒号调用时 self 只是挤在最前面，从后往前数就两种写法通吃。脚本作者少写一个冒号
     * 是很常见的事，为此报错不值得。
     */
    static LuaValue arg(Varargs args, int fromEnd) {
        int index = args.narg() - fromEnd + 1;
        return index < 1 ? LuaValue.NIL : args.arg(index);
    }

    static LuaValue of(Object raw) {
        if (raw == null) {
            return LuaValue.NIL;
        }
        if (raw instanceof Boolean bool) {
            return LuaValue.valueOf(bool);
        }
        if (raw instanceof Integer number) {
            return LuaValue.valueOf(number);
        }
        if (raw instanceof Number number) {
            return LuaValue.valueOf(number.doubleValue());
        }
        return LuaValue.valueOf(String.valueOf(raw));
    }

    /** 按名精确匹配，退而求其次按前缀匹配；同名玩家同时在线时取先命中的那个。 */
    static Player onlinePlayer(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Player prefixHit = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
            if (prefixHit == null && player.getName().toLowerCase(Locale.ROOT)
                    .startsWith(name.toLowerCase(Locale.ROOT))) {
                prefixHit = player;
            }
        }
        return prefixHit;
    }

    static LuaValue playerProxy(Player player) {
        LuaTable table = new LuaTable();
        Location location = player.getLocation();
        table.set("name", LuaValue.valueOf(player.getName()));
        table.set("uuid", LuaValue.valueOf(player.getUniqueId().toString()));
        table.set("world", LuaValue.valueOf(player.getWorld().getName()));
        table.set("x", LuaValue.valueOf(location.getX()));
        table.set("y", LuaValue.valueOf(location.getY()));
        table.set("z", LuaValue.valueOf(location.getZ()));
        table.set("is_online", LuaValue.valueOf(player.isOnline()));

        table.set("send", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                player.sendMessage(LuaValues.arg(args, 1).tojstring());
                return NONE;
            }
        });

        table.set("teleport", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                World target = Bukkit.getWorld(LuaValues.arg(args, 4).tojstring());
                if (target == null) {
                    return FALSE;
                }
                return valueOf(player.teleport(new Location(target,
                        LuaValues.arg(args, 3).todouble(), LuaValues.arg(args, 2).todouble(),
                        LuaValues.arg(args, 1).todouble())));
            }
        });

        table.set("give", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Material material = Material.matchMaterial(LuaValues.arg(args, 2).tojstring());
                if (material == null) {
                    return FALSE;
                }
                int amount = Math.max(1, LuaValues.arg(args, 1).toint(1));
                player.getInventory().addItem(new ItemStack(material, amount));
                return TRUE;
            }
        });
        return table;
    }

    static LuaValue senderProxy(CommandSender sender) {
        LuaTable table = new LuaTable();
        table.set("name", LuaValue.valueOf(sender.getName()));
        table.set("is_player", LuaValue.valueOf(sender instanceof Player));
        table.set("player", sender instanceof Player player ? playerProxy(player) : LuaValue.NIL);
        table.set("has_permission", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return valueOf(sender.hasPermission(LuaValues.arg(args, 1).tojstring()));
            }
        });
        return table;
    }
}
