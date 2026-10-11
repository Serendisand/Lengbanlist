package org.leng.extension.lua;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 脚本的事件订阅（{@code leng.on("player_join", function(ctx) ... end)}）。
 *
 * <p><b>只收同步事件</b>。这不是偷懒：Bukkit 的事件分同步/异步两种，异步事件
 * （如 {@code AsyncPlayerChatEvent}）在别的线程触发，而 Lua 沙箱的指令预算、
 * 以及所有 Bukkit API 调用都建立在"主线程"这个前提上——把脚本塞进异步线程
 * 既会撞 Globals 的线程安全，也会让预算形同虚设。要支持异步事件得先设计
 * 一套"投递回主线程"的模型，那是另一件事。
 *
 * <p>监听器<b>懒注册</b>：脚本不订阅就不占用事件总线。
 *
 * <p>一个事件可以被多个 handler 订阅，按注册顺序依次调用；单个 handler 出错
 * 只记日志，不影响后面的 handler，也不影响核心——异常在
 * {@link LuaExtension#runScript} 里就被挡住了。
 */
final class LuaEventBridge implements Listener {

    /** 支持订阅的事件名（同步）。 */
    private static final Set<String> SUPPORTED = Set.of("player_join", "player_quit", "player_command");

    /** 异步事件：明确拒绝，并给出理由，免得作者以为是拼错了名字。 */
    private static final Set<String> ASYNC_EVENTS = Set.of("player_chat", "async_player_chat", "player_pre_login");

    private final LuaExtension owner;
    private final Map<String, List<LuaValue>> handlers = new LinkedHashMap<>();
    private boolean registered;

    LuaEventBridge(LuaExtension owner) {
        this.owner = owner;
    }

    /** 订阅一个事件。只能在脚本加载期调用。 */
    void on(String name, LuaValue handler) {
        if (!handler.isfunction()) {
            throw new LuaError("leng.on 的第二个参数得是函数");
        }
        if (ASYNC_EVENTS.contains(name)) {
            throw new LuaError("事件 " + name + " 是异步事件，Lua 扩展不支持：它不在主线程触发，"
                    + "而脚本与 Bukkit API 都只能在主线程跑");
        }
        if (!SUPPORTED.contains(name)) {
            throw new LuaError("不支持的事件 " + name + "，可用：" + String.join("、", SUPPORTED));
        }
        handlers.computeIfAbsent(name, key -> new ArrayList<>()).add(handler);
        if (!registered) {
            owner.plugin().getServer().getPluginManager().registerEvents(this, owner.plugin());
            registered = true;
        }
    }

    /** 扩展停用时整体摘下；脚本里的句柄随之作废。 */
    void unregister() {
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
        handlers.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerJoin(PlayerJoinEvent event) {
        fire("player_join", event.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerQuit(PlayerQuitEvent event) {
        fire("player_quit", event.getPlayer(), null);
    }

    /** 唯一能干预流程的事件：补全 {@code ctx.command} 与 {@code ctx.set_cancelled}。 */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        fire("player_command", event.getPlayer(), context -> {
            context.set("command", LuaValue.valueOf(event.getMessage()));
            context.set("cancelled", LuaValue.valueOf(event.isCancelled()));
            context.set("set_cancelled", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    boolean cancelled = LuaValues.arg(args, 1).toboolean();
                    event.setCancelled(cancelled);
                    context.set("cancelled", LuaValue.valueOf(cancelled));
                    return NONE;
                }
            });
        });
    }

    private void fire(String name, Player player, Consumer<LuaTable> decorate) {
        List<LuaValue> subscribed = handlers.get(name);
        if (subscribed == null || subscribed.isEmpty()) {
            return;
        }
        for (LuaValue handler : subscribed) {
            LuaTable context = new LuaTable();
            context.set("name", LuaValue.valueOf(name));
            context.set("player", player == null ? LuaValue.NIL : LuaValues.playerProxy(player));
            if (decorate != null) {
                decorate.accept(context);
            }
            owner.runScript(handler, context, "事件 " + name);
        }
    }
}
