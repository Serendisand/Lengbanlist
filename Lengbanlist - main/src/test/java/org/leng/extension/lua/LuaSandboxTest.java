package org.leng.extension.lua;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 沙箱与指令预算。
 *
 * <p>这里钉的是两条底线：脚本<b>够不到</b>文件系统与 Java 反射；
 * 脚本写死循环<b>卡不住</b>服务器主线程。任何一条破了，Lua 扩展就不能上线。
 */
class LuaSandboxTest {

    private final List<String> printed = new ArrayList<>();
    private final LuaSandbox sandbox = new LuaSandbox(printed::add);

    private LuaValue run(String source) {
        return sandbox.run(sandbox.globals().load(source, "test"), LuaValue.NONE).arg1();
    }

    @Test
    void evaluatesScripts() {
        assertEquals(2, run("return 1 + 1").toint());
        assertEquals("ok", run("return 'o' .. 'k'").tojstring());
    }

    @Test
    void filesystemAndJavaBridgesAreNotReachable() {
        for (String name : new String[]{"io", "package", "require", "dofile", "loadfile",
                "debug", "luajava", "collectgarbage", "coroutine"}) {
            assertTrue(run("return " + name).isnil(), name + " 不该出现在沙箱里");
        }
        assertTrue(run("return os.execute").isnil(), "os.execute 必须摘掉");
        assertTrue(run("return os.remove").isnil());
        assertTrue(run("return os.getenv").isnil());
    }

    @Test
    void harmlessCapabilitiesRemain() {
        assertTrue(run("return type(string.format)").tojstring().equals("function"));
        assertTrue(run("return type(table.concat)").tojstring().equals("function"));
        assertTrue(run("return type(math.floor)").tojstring().equals("function"));
        assertTrue(run("return os.time() > 0").toboolean(), "os.time 要留着，脚本常用来打时间戳");
    }

    @Test
    void printGoesToTheSinkInsteadOfStdout() {
        run("print('hello', 42)");

        assertEquals(1, printed.size());
        assertEquals("hello\t42", printed.get(0));
    }

    @Test
    void scriptErrorIsReportedAsLuaError() {
        assertThrows(LuaError.class, () -> run("error('boom')"));
    }

    @Test
    void normalLoopsAreNotMistakenForRunaways() {
        assertEquals(4501500, run("local s = 0 for i = 1, 3000 do s = s + i end return s").toint());
    }

    @Test
    void infiniteLoopIsAborted() {
        assertAborted("while true do end");
    }

    @Test
    void pcallCannotSwallowTheAbort() {
        assertAborted("pcall(function() while true do end end)");
        assertAborted("xpcall(function() while true do end end, function() end)");
    }

    @Test
    void coroutinesAreAbsentOnPurpose() {
        // LuaJ 的协程是真 Java 线程，而且不是守护线程：一个 yield 挂起的协程会永久占住
        // 一个线程并钉住整个 Globals 堆，攒够几十个就能让 /stop 卡死。
        // 这个用例钉住"别顺手把 CoroutineLib 加回来"。
        assertTrue(run("return coroutine").isnil());
        assertThrows(LuaError.class, () -> run("coroutine.create(function() end)"));
    }

    @Test
    void budgetResetsBetweenCalls() {
        LuaValue chunk = sandbox.globals().load(
                "local s = 0 for i = 1, 5000 do s = s + i end return s", "loop");

        for (int i = 0; i < 12; i++) {
            assertEquals(12502500, sandbox.run(chunk, LuaValue.NONE).arg1().toint(),
                    "每次调用都要重置预算，否则正常脚本会在第 N 次被误杀");
        }
    }

    /**
     * 中断信号必须是 {@link Error} 而不是异常——LuaJ 的 pcall/xpcall 会吞掉 Exception。
     *
     * <p>用 preemptive 超时兜住"预算机制失效"的情况：真失效时表现为测试超时失败，
     * 而不是构建卡死。
     */
    private void assertAborted(String source) {
        LuaValue chunk = sandbox.globals().load(source, "runaway");

        LuaSandbox.Aborted aborted = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> assertThrows(LuaSandbox.Aborted.class,
                        () -> sandbox.run(chunk, LuaValue.NONE)));

        assertFalse(aborted.getMessage().isEmpty(), "中断原因要能写进日志");
    }

    @Test
    void argHelperAcceptsBothCallStyles() {
        Varargs dotStyle = LuaValue.varargsOf(
                new LuaValue[]{LuaValue.valueOf("key"), LuaValue.valueOf("fallback")});
        Varargs colonStyle = LuaValue.varargsOf(
                new LuaValue[]{new LuaTable(), LuaValue.valueOf("key"), LuaValue.valueOf("fallback")});

        assertEquals("key", LuaValues.arg(dotStyle, 2).tojstring());
        assertEquals("key", LuaValues.arg(colonStyle, 2).tojstring());
        assertEquals("fallback", LuaValues.arg(dotStyle, 1).tojstring());
        assertEquals("fallback", LuaValues.arg(colonStyle, 1).tojstring());
    }

    @Test
    void argHelperToleratesMissingArguments() {
        assertTrue(LuaValues.arg(LuaValue.NONE, 1).isnil());
        assertTrue(LuaValues.arg(LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf("only")}), 3).isnil());
    }
}
