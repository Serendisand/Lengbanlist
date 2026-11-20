package org.leng.api;

import org.bukkit.command.CommandSender;

/**
 * 封禁人数广播服务。
 *
 * <p>核心的箱子菜单与 Web 面板通过它触发广播、读取自动广播开关；
 * 扩展不在时这两处入口直接隐藏/拒绝，核心不会因为功能被搬走而报错。
 */
public interface BroadcastService {

    /** 自动广播是否开启（对应扩展配置的 {@code auto-broadcast}）。 */
    boolean isEnabled();

    void setEnabled(boolean enabled);

    /**
     * 立即广播一次。
     *
     * @param sender 触发者；控制台或 Web 触发时传 {@code null}，只发给在线玩家
     * @return 广播是否成功（消息没配置等情况下为 {@code false}）
     */
    boolean broadcastNow(CommandSender sender);
}
