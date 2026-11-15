package org.leng.api;

import org.leng.object.MuteEntry;

import java.util.List;

/**
 * 禁言读写服务。
 *
 * <p>与 {@link BanService} 同理：写操作走这里，核心会一并处理缓存与审计。
 */
public interface MuteService {

    /**
     * 禁言玩家或 IP 段。
     *
     * @return 实际生效的到期时刻（毫秒）；失败返回 {@code null}
     */
    Long mute(String target, String staff, long durationMillis, String reason);

    boolean unmute(String target, String actor);

    boolean isMuted(String target);

    List<MuteEntry> activeMutes();

    int activeMuteCount();

    /**
     * 用时长字符串禁言，规则与核心命令一致：{@code 30m}、{@code 7d}、{@code forever}……
     *
     * @return 实际生效的到期时刻（毫秒）；参数非法或失败返回 {@code null}
     */
    default Long mute(String target, String staff, String duration, String reason) {
        long millis = DurationParser.parseMillis(duration);
        return millis > 0 ? mute(target, staff, millis, reason) : null;
    }

    /** 永久禁言。 */
    default Long mutePermanent(String target, String staff, String reason) {
        return mute(target, staff, Long.MAX_VALUE, reason);
    }
}
