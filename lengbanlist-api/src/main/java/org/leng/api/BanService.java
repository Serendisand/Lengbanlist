package org.leng.api;

import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;

import java.util.List;
import java.util.Optional;

/**
 * 封禁读写服务。
 *
 * <p><b>写操作请优先走这里，不要自己写 bans / ip_bans 表</b>：核心在写入时会一并
 * 处理缓存失效、审计留痕、跨服事件广播与在服玩家的即时踢出，直接写表会绕过这些。
 *
 * <p>时间一律用毫秒；由核心负责换算成绝对到期时刻。
 */
public interface BanService {

    /** 封禁玩家。{@code durationMillis} 为 {@link Long#MAX_VALUE} 表示永久。 */
    boolean ban(String target, String staff, long durationMillis, String reason, boolean silent);

    /** 封禁 IP 或 IP 段（支持 {@code 1.2.3.x}、{@code 1.2.3.0/24}）。 */
    boolean banIp(String ip, String staff, long durationMillis, String reason, boolean silent);

    boolean unban(String target, String actor);

    boolean unbanIp(String ip, String actor);

    boolean isBanned(String target);

    boolean isIpBanned(String ip);

    /** 查询玩家的生效封禁记录。 */
    Optional<BanEntry> findBan(String target);

    /** 查询 IP 的生效封禁记录（含网段匹配）。 */
    Optional<BanIpEntry> findIpBan(String ip);

    List<BanEntry> activeBans();

    List<BanIpEntry> activeIpBans();

    int activeBanCount();

    int activeIpBanCount();
}
