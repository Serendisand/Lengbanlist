package org.leng.net;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpConnectTimeoutException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 镜像链的纯逻辑测试（不联网）。
 *
 * <p>这些用例把 {@code ModelCloudSourceFallbackTest} 依赖的行为显式钉在新的公共抽象上：
 * 配置顺序不被改动、同一主机在单次任务内只被试一次、新任务重新探测、
 * 以及"内容错误不算源故障"。
 */
class MirrorChainTest {

    private static final String PRIMARY = "http://127.0.0.1:9/down";
    private static final String MIRROR = "http://localhost:9/index.json";

    private static MirrorChain chainOf(String... urls) {
        List<MirrorSpec> specs = new ArrayList<>();
        for (String url : urls) {
            specs.add(MirrorSpec.custom(url));
        }
        return new MirrorChain(specs);
    }

    @Test
    void configuredOrderIsPreserved() {
        MirrorChain chain = chainOf(PRIMARY, MIRROR);

        assertEquals(List.of(PRIMARY, MIRROR),
                chain.specs().stream().map(MirrorSpec::url).toList(),
                "镜像顺序必须与配置一致，不得被重排");
    }

    @Test
    void unusableEntriesAreDropped() {
        List<MirrorSpec> specs = new ArrayList<>();
        specs.add(MirrorSpec.custom(""));
        specs.add(null);
        specs.add(MirrorSpec.custom(MIRROR));

        MirrorChain chain = new MirrorChain(specs);

        assertEquals(1, chain.specs().size());
        assertFalse(chain.isEmpty());
    }

    @Test
    void reachableHostIsNotSkipped() {
        MirrorChain chain = chainOf(PRIMARY, MIRROR);

        assertEquals(List.of(PRIMARY, MIRROR), chain.filterUnreachable(List.of(PRIMARY, MIRROR)));
        assertTrue(chain.unreachableHosts().isEmpty());
    }

    @Test
    void unreachableHostIsSkippedForTheRestOfTheTask() {
        MirrorChain chain = chainOf(PRIMARY, MIRROR);

        boolean firstMark = chain.noteFailure(PRIMARY, new IOException("Connection refused"));
        assertTrue(firstMark, "首次标记应返回 true，供调用方打一条日志");
        assertFalse(chain.noteFailure(PRIMARY, new IOException("Connection refused")),
                "同一主机重复失败不应再次标记");

        List<String> filtered = chain.filterUnreachable(
                List.of(PRIMARY + "/models/a.yml", MIRROR + "/models/a.yml"));

        assertEquals(List.of(MIRROR + "/models/a.yml"), filtered, "同主机的候选应被过滤掉");
    }

    @Test
    void nextTaskProbesTheUnreachableHostAgain() {
        MirrorChain chain = chainOf(PRIMARY, MIRROR);
        chain.noteFailure(PRIMARY, new IOException("Connection refused"));

        chain.resetTask();

        assertTrue(chain.unreachableHosts().isEmpty(), "新任务必须清空失败记忆");
        assertEquals(List.of(PRIMARY, MIRROR), chain.filterUnreachable(List.of(PRIMARY, MIRROR)),
                "新任务应重新尝试主源");
    }

    @Test
    void differentHostsAreIndependent() {
        List<String> candidates = List.of("http://127.0.0.1:9/a", "http://localhost:9/a");

        MirrorChain chain = chainOf("http://127.0.0.1:9/down");
        chain.noteFailure("http://127.0.0.1:9/down", new IOException("Connection refused"));

        assertNotEquals(MirrorSpec.hostOf("http://127.0.0.1:9/a"),
                MirrorSpec.hostOf("http://localhost:9/a"),
                "127.0.0.1 与 localhost 必须是不同主机，否则主源与镜像会被一起拉黑");
        assertEquals(List.of("http://localhost:9/a"), chain.filterUnreachable(candidates),
                "只应过滤掉 127.0.0.1，localhost 仍需保留");
    }

    @Test
    void allFilteredFallsBackToOriginalCandidates() {
        MirrorChain chain = chainOf(PRIMARY);
        chain.noteFailure(PRIMARY, new IOException("Connection refused"));

        assertEquals(List.of(PRIMARY), chain.filterUnreachable(List.of(PRIMARY)),
                "全部候选都被过滤时应退回原始列表，避免出现'没有任何候选'");
    }

    @Test
    void connectionFailuresAndServerErrorsCountAsUnreachable() {
        assertTrue(MirrorChain.isUnreachableFailure(new IOException("Connection refused")));
        assertTrue(MirrorChain.isUnreachableFailure(new IOException((String) null)),
                "无法判断原因的 IOException 应保守视为不可达");
        assertTrue(MirrorChain.isUnreachableFailure(new InterruptedException()));
        assertTrue(MirrorChain.isUnreachableFailure(new HttpConnectTimeoutException("connect timed out")));
        assertTrue(MirrorChain.isUnreachableFailure(new IOException("HTTP 500: /index.json")));
        assertTrue(MirrorChain.isUnreachableFailure(new IOException("HTTP 503: /index.json")));
    }

    @Test
    void contentLevelErrorsDoNotBlacklistTheSource() {
        assertFalse(MirrorChain.isUnreachableFailure(new IOException("HTTP 404: /models/hutao/hutao.yml")),
                "404 表示资源不存在，不是源故障，拉黑整个镜像会误伤");
        assertFalse(MirrorChain.isUnreachableFailure(new IOException("HTTP 403: /index.json")));
        assertFalse(MirrorChain.isUnreachableFailure(new IllegalStateException("解析失败")));
    }

    @Test
    void fourOhFourKeepsTheHostUsable() {
        MirrorChain chain = chainOf(MIRROR);

        boolean marked = chain.noteFailure(MIRROR, new IOException("HTTP 404: /models/x/x.yml"));

        assertFalse(marked);
        assertTrue(chain.unreachableHosts().isEmpty());
        assertEquals(List.of(MIRROR), chain.filterUnreachable(List.of(MIRROR)));
    }

    @Test
    void hostIsParsedAndLowercased() {
        assertEquals("example.com", MirrorSpec.hostOf("https://EXAMPLE.com/a/b"));
        assertEquals("127.0.0.1", MirrorSpec.hostOf("http://127.0.0.1:8080/x"));
        org.junit.jupiter.api.Assertions.assertNull(MirrorSpec.hostOf("not a url"));
        org.junit.jupiter.api.Assertions.assertNull(MirrorSpec.hostOf(""));
    }

    @Test
    void mirrorTypeIsParsedFromConfig() {
        assertEquals(MirrorType.GITHUB, MirrorType.fromConfig("github"));
        assertEquals(MirrorType.GITHUB_PROXY, MirrorType.fromConfig("github-proxy"));
        assertEquals(MirrorType.GITHUB_PROXY, MirrorType.fromConfig("gh-proxy"));
        assertEquals(MirrorType.JSDELIVR, MirrorType.fromConfig("jsDelivr"));
        assertEquals(MirrorType.GITEE, MirrorType.fromConfig("gitee"));
        assertEquals(MirrorType.CUSTOM, MirrorType.fromConfig("whatever"));
        assertEquals(MirrorType.CUSTOM, MirrorType.fromConfig(null));
    }
}
