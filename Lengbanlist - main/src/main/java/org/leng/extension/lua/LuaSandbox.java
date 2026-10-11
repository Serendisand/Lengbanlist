package org.leng.extension.lua;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.OsLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.function.Consumer;

/**
 * 一个 Lua 扩展的运行环境。
 *
 * <p><b>库是白名单式的</b>：只装 BaseLib / Table / String / Math / Bit32 / Coroutine 与
 * 裁剪过的 Os，不装 IoLib（文件）、PackageLib（require 任意路径）、LuajavaLib
 * （Java 反射，那是完全逃逸）。
 *
 * <p><b>调试库装了但不给脚本看</b>：指令预算挂在 {@link DebugLib} 的
 * {@code onInstruction} 上——{@code LuaClosure.execute()} 每条指令都会调它，
 * 这是 LuaJ 唯一可用的指令级中断点。但 {@code debug} 表本身会被摘掉，
 * 脚本拿不到 {@code sethook}。
 *
 * <p><b>超时是硬中断</b>：抛出的 {@link Aborted} 刻意继承 {@link Error}。
 * LuaJ 的 {@code pcall}/{@code xpcall} 捕获的是 {@code LuaError} 与
 * {@code java.lang.Exception}，若用 Exception，脚本里一个 {@code pcall} 就能把
 * 中断吞掉，预算形同虚设。
 */
public final class LuaSandbox {

    /** 单次进入脚本的指令上限。死循环靠它中断，所以不能是"差不多就行"的值。 */
    public static final int INSTRUCTION_BUDGET = 200_000;

    /** 单次进入脚本的墙钟上限，兜住"指令不多但每条都很慢"的情况。 */
    public static final long WALL_CLOCK_MILLIS = 5_000L;

    private static final int CLOCK_CHECK_INTERVAL = 1024;

    /** 会读文件系统或强制 GC 的全局函数，直接摘掉。 */
    private static final String[] REMOVED_GLOBALS = {"dofile", "loadfile", "collectgarbage", "debug"};

    /**
     * os 库里会碰进程与文件系统的成员。
     *
     * <p>装的是核心的 {@code OsLib}（真正的实现在 JseOsLib 里），这些方法在核心版本上
     * 本就不可用；再摘一遍是为了不依赖"那个实现恰好没实现"这个前提。
     */
    private static final String[] REMOVED_OS = {
            "execute", "exit", "getenv", "remove", "rename", "tmpname", "setlocale"};

    private final Globals globals;
    private final Budget budget = new Budget();

    /**
     * @param printSink 脚本里 {@code print} 的去处；接进插件日志，而不是服务端标准输出
     */
    public LuaSandbox(Consumer<String> printSink) {
        Globals built = new Globals();
        built.load(new BaseLib());
        built.load(new TableLib());
        built.load(new StringLib());
        built.load(new MathLib());
        built.load(new Bit32Lib());
        built.load(new CoroutineLib());
        built.load(new OsLib());
        LuaC.install(built);
        LoadState.install(built);

        budget.call(built, built);
        for (String name : REMOVED_GLOBALS) {
            built.set(name, LuaValue.NIL);
        }
        stripOs(built);
        built.set("print", printTo(printSink));

        this.globals = built;
    }

    public Globals globals() {
        return globals;
    }

    /**
     * 执行一次脚本调用，前后重置预算。
     *
     * <p>所有进入 Lua 的入口都必须走这里——命令回调、补全、卸载钩子、以及加载期的
     * 顶层代码。漏掉任何一处，那条路径上的死循环就没有上限。
     */
    public Varargs run(LuaValue function, Varargs args) {
        budget.reset();
        return function.invoke(args);
    }

    private static void stripOs(Globals globals) {
        LuaValue os = globals.get("os");
        if (!os.istable()) {
            return;
        }
        LuaTable table = os.checktable();
        for (String name : REMOVED_OS) {
            table.set(name, LuaValue.NIL);
        }
    }

    private static VarArgFunction printTo(Consumer<String> sink) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                StringBuilder line = new StringBuilder();
                for (int i = 1, n = args.narg(); i <= n; i++) {
                    if (i > 1) {
                        line.append('\t');
                    }
                    line.append(args.arg(i).tojstring());
                }
                sink.accept(line.toString());
                return NONE;
            }
        };
    }

    /** 脚本被强制中断的信号。继承 Error 的原因见类注释。 */
    public static final class Aborted extends Error {

        private static final long serialVersionUID = 1L;

        Aborted(String message) {
            super(message);
        }
    }

    /** 指令与墙钟双预算。两个都超了才算超，先到者中断。 */
    private static final class Budget extends DebugLib {

        private int remaining;
        private long deadlineNanos;
        private int untilClockCheck;

        void reset() {
            remaining = INSTRUCTION_BUDGET;
            deadlineNanos = System.nanoTime() + WALL_CLOCK_MILLIS * 1_000_000L;
            untilClockCheck = CLOCK_CHECK_INTERVAL;
        }

        @Override
        public void onInstruction(int pc, Varargs v, int top) {
            super.onInstruction(pc, v, top);
            if (--remaining <= 0) {
                throw new Aborted("脚本执行超出指令预算（" + INSTRUCTION_BUDGET + " 条）");
            }
            if (--untilClockCheck <= 0) {
                untilClockCheck = CLOCK_CHECK_INTERVAL;
                if (System.nanoTime() - deadlineNanos > 0) {
                    throw new Aborted("脚本执行超过 " + WALL_CLOCK_MILLIS + " 毫秒");
                }
            }
        }
    }
}
