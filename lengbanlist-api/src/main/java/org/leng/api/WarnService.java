package org.leng.api;

import org.bukkit.command.CommandSender;
import org.leng.object.WarnEntry;

import java.util.List;

/**
 * 警告服务。
 *
 * <p>注意核心内置的 LBAC 规则：30 天内累计 3 次警告会自动封禁，时长随触发次数递增，
 * 且撤销警告至阈值以下时自动解封。走 {@link #warn} 就会自动享有这套逻辑。
 */
public interface WarnService {

    void warn(String target, String staff, String reason);

    /**
     * 撤销一条警告。
     *
     * @param warnId 警告 ID；传 0 或负数表示撤销最近一条
     */
    boolean unwarn(String target, int warnId, CommandSender actor);

    int countActiveWarnings(String target);

    List<WarnEntry> activeWarnings(String target);

    /** 当前有生效警告的玩家名快照。 */
    List<String> warnedPlayers();
}
