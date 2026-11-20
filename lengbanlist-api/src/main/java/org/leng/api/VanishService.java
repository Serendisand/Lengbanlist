package org.leng.api;

import org.bukkit.entity.Player;

/**
 * 隐身服务：核心的查询、举报目标列表与箱子菜单通过它读取隐身状态。
 *
 * <p>由 vanish 扩展注册为钩子。扩展不在时核心拿不到实现，一律按
 * "没有人隐身"处理——功能被删掉，核心的其它部分照常工作。
 */
public interface VanishService {

    boolean isVanished(Player player);

    /** 该观察者能不能看见隐身玩家（有 {@code lengbanlist.vanish.see} 权限）。 */
    boolean canSee(Player viewer);

    int count();

    /** 切换隐身状态，返回切换后的状态。 */
    boolean toggle(Player player);

    /** 停用扩展或重载时把所有人恢复可见。 */
    void restoreAll();
}
