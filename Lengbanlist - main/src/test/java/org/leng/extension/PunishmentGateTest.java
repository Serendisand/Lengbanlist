package org.leng.extension;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leng.Lengbanlist;
import org.leng.api.ExtensionContext;
import org.leng.api.LengbanlistExtension;
import org.leng.api.PunishmentDecisionHook;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * 处罚放行闸门。
 *
 * <p>重点盯三件事：
 *
 * <ol>
 *   <li><b>策略缺席时一律放行</b>——免疫是可选增强，没装不能把处罚能力关掉</li>
 *   <li><b>开关关闭时放行</b>——与改造前 {@code features.immunity=false} 一致</li>
 *   <li><b>{@code canPunish} 与 {@code canPunishTarget} 的差异必须保留</b>——
 *       前者只看玩家权重，后者对含 {@code .} 的目标看 IP 权重。合并会改变线上行为</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class PunishmentGateTest {

    @Mock Lengbanlist plugin;
    @Mock CommandSender sender;

    private ExtensionRegistry registry;
    private PunishmentGate gate;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getLogger()).thenReturn(Logger.getGlobal());
        lenient().when(plugin.getPluginVersion()).thenReturn("2.1.6");
        registry = new ExtensionRegistry(plugin);
        lenient().when(plugin.getExtensionRegistry()).thenReturn(registry);
        gate = new PunishmentGate(plugin);
    }

    /** 可调权重的假策略。 */
    private static final class FakeDecision implements PunishmentDecisionHook {
        int operator = 5;
        int web = 9;
        int player = 3;
        int ip = 7;

        @Override
        public int operatorWeight(CommandSender sender) {
            return operator;
        }

        @Override
        public int webOperatorWeight() {
            return web;
        }

        @Override
        public int playerWeight(String target) {
            return player;
        }

        @Override
        public int ipWeight(String ip) {
            return this.ip;
        }
    }

    private void installHook(PunishmentDecisionHook hook) throws Exception {
        registry.register(new LengbanlistExtension() {
            @Override
            public String id() {
                return "immunity";
            }

            @Override
            public String version() {
                return "1.0.0";
            }

            @Override
            public Set<String> features() {
                return Set.of("immunity");
            }

            @Override
            public void onEnable(ExtensionContext context) {
                context.hooks().register(PunishmentDecisionHook.class, hook);
            }
        });
    }

    // ---------------------------------------------------------- 默认放行

    @Test
    void withoutAnyHookEverythingIsPermitted() {
        assertTrue(gate.canPunish(sender, "Steve"), "没有免疫策略时应放行");
        assertTrue(gate.canPunish(1, "Steve"));
        assertTrue(gate.canPunishTarget(1, "1.2.3.4"));
        assertEquals(Integer.MAX_VALUE, gate.staffWeight(sender), "策略缺席时操作者权重视为最高");
        assertEquals(Integer.MAX_VALUE, gate.webOperatorWeight());
    }

    @Test
    void switchOffStillPermitsEvenWithHookInstalled() throws Exception {
        installHook(new FakeDecision());
        lenient().when(plugin.isFeatureEnabled("immunity")).thenReturn(false);

        assertTrue(gate.canPunish(sender, "Steve"), "开关关闭时放行，与改造前一致");
        assertEquals(Integer.MAX_VALUE, gate.staffWeight(sender));
    }

    @Test
    void uninstallingTheExtensionFallsBackToPermissive() throws Exception {
        FakeDecision decision = new FakeDecision();
        decision.operator = 2;
        decision.player = 3;
        installHook(decision);
        lenient().when(plugin.isFeatureEnabled("immunity")).thenReturn(true);
        assertFalse(gate.canPunish(sender, "Steve"), "2 < 3，装了策略时应被拦下");

        registry.unregister("immunity");

        assertTrue(gate.canPunish(sender, "Steve"), "卸载后必须回到放行");
    }

    // ---------------------------------------------------------- 策略生效

    @Test
    void hookDecidesWhenInstalledAndSwitchedOn() throws Exception {
        FakeDecision decision = new FakeDecision();
        installHook(decision);
        lenient().when(plugin.isFeatureEnabled("immunity")).thenReturn(true);

        assertTrue(gate.canPunish(sender, "Steve"), "5 > 3 应放行");

        decision.player = 10;
        assertFalse(gate.canPunish(sender, "Steve"), "5 < 10 应拦下");
    }

    @Test
    void weightsComeFromTheHook() throws Exception {
        FakeDecision decision = new FakeDecision();
        decision.operator = 42;
        decision.web = 77;
        installHook(decision);
        lenient().when(plugin.isFeatureEnabled("immunity")).thenReturn(true);

        assertEquals(42, gate.staffWeight(sender));
        assertEquals(77, gate.webOperatorWeight());
    }

    // ---------------------------------------------------------- 玩家 vs IP（行为差异必须保留）

    @Test
    void canPunishTargetTreatsDottedTargetAsIp() throws Exception {
        FakeDecision decision = new FakeDecision();
        decision.operator = 5;
        decision.player = 3;
        decision.ip = 7;
        installHook(decision);
        lenient().when(plugin.isFeatureEnabled("immunity")).thenReturn(true);

        assertTrue(gate.canPunishTarget(5, "Steve"), "玩家目标比玩家权重 3 -> 放行");
        assertFalse(gate.canPunishTarget(5, "1.2.3.4"), "IP 目标比 IP 权重 7 -> 拦下");
    }

    @Test
    void canPunishNeverTreatsTargetAsIp() throws Exception {
        FakeDecision decision = new FakeDecision();
        decision.operator = 5;
        // 故意把玩家权重设成最低、IP 权重设得很高：
        // 若实现误把 target 当 IP 处理，这条断言就会挂。
        decision.player = Integer.MIN_VALUE;
        decision.ip = 999;
        installHook(decision);
        lenient().when(plugin.isFeatureEnabled("immunity")).thenReturn(true);

        assertTrue(gate.canPunish(5, "1.2.3.4"),
                "改造前 canPunish 只看玩家权重；把它改成 IP 感知会改变线上行为");
        assertFalse(gate.canPunishTarget(5, "1.2.3.4"), "而 canPunishTarget 仍是 IP 感知的");
    }

    @Test
    void nullTargetDoesNotThrow() throws Exception {
        installHook(new FakeDecision());
        lenient().when(plugin.isFeatureEnabled("immunity")).thenReturn(true);

        gate.canPunishTarget(5, null);
        gate.canPunish(5, null);
    }
}
