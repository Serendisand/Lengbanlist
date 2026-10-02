package org.leng.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约版本范围判断。
 *
 * <p>这是扩展生态里最容易出错、也最难排查的一环：范围写错会导致扩展被静默拒绝，
 * 或者更糟——被放进来之后在运行时抛 {@code NoSuchMethodError}。所以边界必须钉死。
 */
class ApiVersionTest {

    private static final String CORE = "2.1.6";

    @Test
    void emptyRangeAlwaysPasses() {
        assertTrue(ApiVersion.satisfies(null, CORE));
        assertTrue(ApiVersion.satisfies("", CORE));
        assertTrue(ApiVersion.satisfies("   ", CORE));
    }

    @Test
    void exactVersion() {
        assertTrue(ApiVersion.satisfies("2.1.6", CORE));
        assertFalse(ApiVersion.satisfies("2.1.5", CORE));
        assertFalse(ApiVersion.satisfies("2.1.7", CORE));
    }

    @Test
    void partialVersionsActAsPrefixMatch() {
        assertTrue(ApiVersion.satisfies("2", CORE), "2 应视为 2.x");
        assertTrue(ApiVersion.satisfies("2.1", CORE), "2.1 应视为 2.1.x，而不是'正好 2.1.0'");
        assertFalse(ApiVersion.satisfies("2.2", CORE), "2.2 不该匹配 2.1.6");
        assertFalse(ApiVersion.satisfies("1", CORE));
        assertTrue(ApiVersion.satisfies("2.1.6", CORE), "三位及以上按精确匹配");
        assertFalse(ApiVersion.satisfies("2.1.5", CORE));
    }

    @Test
    void wildcard() {
        assertTrue(ApiVersion.satisfies("2.x", CORE));
        assertTrue(ApiVersion.satisfies("2.*", CORE));
        assertTrue(ApiVersion.satisfies("2.1.x", CORE));
        assertFalse(ApiVersion.satisfies("3.x", CORE));
        assertFalse(ApiVersion.satisfies("2.2.x", CORE));
        assertFalse(ApiVersion.satisfies("1.x", CORE));
    }

    @Test
    void halfOpenRange() {
        assertTrue(ApiVersion.satisfies("[2.0,3.0)", CORE));
        assertTrue(ApiVersion.satisfies("[2.0,3.0)", "2.0.0"), "闭区间下界应包含");
        assertFalse(ApiVersion.satisfies("[2.0,3.0)", "3.0.0"), "开区间上界应排除");
        assertFalse(ApiVersion.satisfies("[2.0,3.0)", "1.9.9"));
    }

    @Test
    void closedRangeIncludesUpperBound() {
        assertTrue(ApiVersion.satisfies("[2.0,3.0]", "3.0.0"));
        assertFalse(ApiVersion.satisfies("[2.0,3.0]", "3.0.1"));
    }

    @Test
    void openLowerBoundExcludesIt() {
        assertFalse(ApiVersion.satisfies("(2.0,3.0)", "2.0.0"));
        assertTrue(ApiVersion.satisfies("(2.0,3.0)", "2.0.1"));
        assertTrue(ApiVersion.satisfies("(2.0,3.0]", "3.0.0"));
        assertFalse(ApiVersion.satisfies("(2.0,3.0]", "3.0.1"));
    }

    @Test
    void comparisonIsNumericNotLexicographic() {
        assertTrue(ApiVersion.compare("2.1.10", "2.1.6") > 0,
                "字符串比较会把 2.1.10 判成小于 2.1.6，版本比较必须按数字段");
        assertTrue(ApiVersion.compare("2.10.0", "2.9.0") > 0);
        assertEquals(0, ApiVersion.compare("2.1", "2.1.0"), "缺失的段应视为 0");
        assertTrue(ApiVersion.compare("2.1.6", "2.1.6") == 0);
    }

    @Test
    void toleratesCommonVersionDecoration() {
        assertTrue(ApiVersion.satisfies("2.1.6", "v2.1.6"));
        assertTrue(ApiVersion.satisfies("[2.0,3.0)", "2.1.6-SNAPSHOT"));
        assertEquals(0, ApiVersion.compare("2.1.6-SNAPSHOT", "2.1.6"));
    }

    @Test
    void unparsableActualIsRejected() {
        assertFalse(ApiVersion.satisfies("[2.0,3.0)", ""));
        assertFalse(ApiVersion.satisfies("[2.0,3.0)", null));
        assertFalse(ApiVersion.satisfies("2.x", "abc"));
    }

    @Test
    void malformedRangeIsRejected() {
        assertFalse(ApiVersion.satisfies("[2.0,3.0", CORE), "缺少右括号应判为不满足，而不是放行");
        assertFalse(ApiVersion.satisfies("[2.0-3.0)", CORE), "缺少逗号应判为不满足");
    }
}
