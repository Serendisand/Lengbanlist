package org.leng.extension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leng.Lengbanlist;
import org.leng.api.DurationPolicyHook;
import org.leng.api.ExtensionContext;
import org.leng.api.LengbanlistExtension;
import org.leng.service.EscalationManager;
import org.leng.service.WarnManager;
import org.leng.object.WarnEntry;
import org.leng.util.TimeUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * 自动时长闸门。
 *
 * <p>关键点是：钩子缺席时的兜底<b>不是</b>恒等，而是核心内置的"按警告数"规则——
 * 改造前 {@code features.escalation} 关闭时走的就是它。若误做成"用请求的时长"，
 * auto 场景下会直接判成 0 时长。
 *
 * <p>另外钉住一个已知例外：禁言时长不走累犯分级（与改造前一致）。
 */
@ExtendWith(MockitoExtension.class)
class DurationPolicyTest {

    @Mock Lengbanlist plugin;
    @Mock WarnManager warnManager;

    private ExtensionRegistry registry;
    private DurationPolicy policy;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getLogger()).thenReturn(Logger.getGlobal());
        lenient().when(plugin.getPluginVersion()).thenReturn("2.1.6");
        lenient().when(plugin.getWarnManager()).thenReturn(warnManager);
        registry = new ExtensionRegistry(plugin);
        lenient().when(plugin.getExtensionRegistry()).thenReturn(registry);
        policy = new DurationPolicy(plugin);
    }

    /** 让目标的未撤销警告数返回 n。 */
    private void warnCount(int n) {
        List<WarnEntry> warnings = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            warnings.add(null);   // 只用到 size()
        }
        lenient().when(warnManager.getActiveWarnings(anyString())).thenReturn(warnings);
    }

    private static final class FakeDuration implements DurationPolicyHook {
        @Override
        public Decision ban(String target) {
            return new Decision(111L, 3);
        }

        @Override
        public Decision ipBan(String ip) {
            return new Decision(222L, 4);
        }

        @Override
        public Decision mute(String target) {
            return new Decision(333L, 5);
        }
    }

    private void installHook(DurationPolicyHook hook) throws Exception {
        registry.register(new LengbanlistExtension() {
            @Override
            public String id() {
                return "escalation";
            }

            @Override
            public String version() {
                return "1.0.0";
            }

            @Override
            public Set<String> features() {
                return Set.of("escalation");
            }

            @Override
            public void onEnable(ExtensionContext context) {
                context.hooks().register(DurationPolicyHook.class, hook);
            }
        });
    }

    // ---------------------------------------------------------- 内置兜底

    @Test
    void withoutHookFallsBackToWarnBasedTable() {
        long[][] cases = {
                {0, 1}, {1, 3}, {2, 7}, {3, 14}, {4, 30},
        };
        for (long[] c : cases) {
            warnCount((int) c[0]);
            assertEquals(TimeUtils.daysToMillis(c[1]), policy.autoBan("Steve").durationMillis(),
                    "警告数 " + c[0] + " 的兜底时长不对");
        }
        warnCount(5);
        assertEquals(Long.MAX_VALUE, policy.autoBan("Steve").durationMillis(), "5 次及以上应为永久");
        assertEquals(0, policy.autoBan("Steve").offenseCount(), "兜底路径不该报累犯次数");
    }

    @Test
    void fallbackAlsoAppliesToIpBanAndMute() {
        warnCount(2);
        long expected = TimeUtils.daysToMillis(7);
        assertEquals(expected, policy.autoIpBan("1.2.3.4").durationMillis());
        assertEquals(expected, policy.autoMute("Steve").durationMillis());
    }

    @Test
    void switchOffStillUsesFallbackNotIdentity() {
        warnCount(1);
        lenient().when(plugin.isFeatureEnabled("escalation")).thenReturn(false);

        assertEquals(TimeUtils.daysToMillis(3), policy.autoBan("Steve").durationMillis(),
                "关掉升级开关不是'用请求时长'，而是内置按警告数兜底");
    }

    // ---------------------------------------------------------- 钩子生效

    @Test
    void hookTakesOverWhenInstalledAndSwitchedOn() throws Exception {
        installHook(new FakeDuration());
        lenient().when(plugin.isFeatureEnabled("escalation")).thenReturn(true);

        DurationPolicyHook.Decision ban = policy.autoBan("Steve");
        assertEquals(111L, ban.durationMillis());
        assertEquals(3, ban.offenseCount(), "累犯次数要透传给调用方用于播报升级提示");

        assertEquals(222L, policy.autoIpBan("1.2.3.4").durationMillis());
        assertEquals(333L, policy.autoMute("Steve").durationMillis());
    }

    @Test
    void uninstallingHookRestoresFallback() throws Exception {
        installHook(new FakeDuration());
        lenient().when(plugin.isFeatureEnabled("escalation")).thenReturn(true);
        warnCount(0);
        assertEquals(111L, policy.autoBan("Steve").durationMillis());

        registry.unregister("escalation");

        assertEquals(TimeUtils.daysToMillis(1), policy.autoBan("Steve").durationMillis(),
                "卸载后必须回到内置兜底");
    }

    // ---------------------------------------------------------- 已知例外

    @Test
    void escalationMuteStaysWarnBasedByDesign() {
        warnCount(3);
        EscalationManager escalation = new EscalationManager(plugin);

        DurationPolicyHook.Decision decision = escalation.mute("Steve");

        assertEquals(TimeUtils.daysToMillis(14), decision.durationMillis(),
                "禁言恒按警告数，不走累犯分级——与改造前 resolveMute 行为一致");
        assertEquals(0, decision.offenseCount());
    }
}
