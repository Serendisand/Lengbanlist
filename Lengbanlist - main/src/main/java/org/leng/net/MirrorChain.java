package org.leng.net;

import java.io.IOException;
import java.net.http.HttpConnectTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 镜像链：把一串镜像源组织成"按序尝试 + 失败记忆"的可复用单元。
 *
 * <p>核心语义（由 {@code ModelCloudSourceFallbackTest} 钉住，改动前请先看那个测试）：
 *
 * <ul>
 *   <li><b>按任务重置</b>：{@link #resetTask()} 在每个逻辑任务开始时调用，清空失败记忆。
 *       下一次任务仍会重新探测主源，避免一次抖动让主源被永久拉黑。</li>
 *   <li><b>按主机跳过</b>：某主机在本次任务内被判定为不可达后，后续候选里同一主机的
 *       地址会被过滤掉。按 <b>主机</b> 而非完整 URL 匹配是刻意的：
 *       {@code 127.0.0.1} 与 {@code localhost} 是不同主机，测试正是靠这一点区分
 *       "主源"和"镜像"。</li>
 *   <li><b>不把内容错误当成源故障</b>：HTTP 404 表示"这个资源不存在"，不是"这个源挂了"，
 *       绝不能因此拉黑整个镜像；只有无状态码的连接类失败与 HTTP ≥ 500 才算不可达。</li>
 * </ul>
 */
public final class MirrorChain {

    private static final Pattern HTTP_STATUS_FAILURE = Pattern.compile("^HTTP (\\d{3})");

    private final List<MirrorSpec> specs;
    private final Set<String> unreachableHosts = ConcurrentHashMap.newKeySet();

    public MirrorChain(List<MirrorSpec> specs) {
        List<MirrorSpec> usable = new ArrayList<>();
        if (specs != null) {
            for (MirrorSpec spec : specs) {
                if (spec != null && spec.usable()) {
                    usable.add(spec);
                }
            }
        }
        this.specs = List.copyOf(usable);
    }

    /** 镜像原始顺序，与配置一致，不做改动。 */
    public List<MirrorSpec> specs() {
        return specs;
    }

    public boolean isEmpty() {
        return specs.isEmpty();
    }

    /** 每个逻辑任务开始时调用，清空本次任务的失败记忆。 */
    public void resetTask() {
        unreachableHosts.clear();
    }

    /** 本任务内已被判定不可达的主机快照，供诊断/日志使用。 */
    public Set<String> unreachableHosts() {
        return Set.copyOf(unreachableHosts);
    }

    /**
     * 过滤掉本任务内已确认不可达的主机。
     *
     * <p>若过滤后为空则退回原始列表——宁可多试一次，也不要出现"没有任何候选"导致
     * 功能直接失败。
     */
    public List<String> filterUnreachable(List<String> urls) {
        if (urls == null || urls.isEmpty() || unreachableHosts.isEmpty()) {
            return urls;
        }
        List<String> kept = new ArrayList<>(urls.size());
        for (String url : urls) {
            String host = MirrorSpec.hostOf(url);
            if (host != null && unreachableHosts.contains(host)) {
                continue;
            }
            kept.add(url);
        }
        return kept.isEmpty() ? urls : kept;
    }

    /**
     * 记录一次候选失败。
     *
     * @return true 表示这次失败让某个主机<b>首次</b>被标记为不可达，调用方可据此打一条日志
     */
    public boolean noteFailure(String url, Exception error) {
        if (!isUnreachableFailure(error)) {
            return false;
        }
        String host = MirrorSpec.hostOf(url);
        if (host == null) {
            return false;
        }
        return unreachableHosts.add(host);
    }

    /**
     * 判定一次失败是否意味着"这个源不可达"（而非"这个资源不存在"）。
     *
     * <ul>
     *   <li>{@link InterruptedException}：视为不可达，调用方应立即中断</li>
     *   <li>非 {@link IOException}：内容/解析层问题，不算源故障</li>
     *   <li>{@code IOException} 消息为空：无法判断 → 算不可达</li>
     *   <li>消息形如 {@code HTTP nnn}：仅 nnn ≥ 500 算不可达（404 等不算）</li>
     *   <li>其余 {@code IOException}（连接被拒、超时等）：算不可达</li>
     * </ul>
     */
    public static boolean isUnreachableFailure(Exception error) {
        if (error instanceof InterruptedException) {
            return true;
        }
        if (error instanceof HttpConnectTimeoutException) {
            return true;
        }
        if (!(error instanceof IOException)) {
            return false;
        }
        String message = error.getMessage();
        if (message == null) {
            return true;
        }
        Matcher matcher = HTTP_STATUS_FAILURE.matcher(message);
        if (!matcher.find()) {
            return true;
        }
        try {
            return Integer.parseInt(matcher.group(1)) >= 500;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
