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
}
