package org.leng.api;

import org.bukkit.command.CommandSender;

/**
 * 处罚决策钩子：回答"这个操作者能不能处罚这个目标"。
 *
 * <p>权重系统（immunity）实现本接口。核心只负责按功能键找到钩子并做默认兜底，
 * 具体的权重来源（LuckPerms 组权重、权限节点、配置默认值）完全由实现决定。
 *
 * <p><b>未安装实现时核心一律放行</b>——"没装免疫系统"的语义就是"谁都能罚"，
 * 而不是"谁都罚不了"。这条默认值是刻意选的：一个可选的增强功能绝不能在缺席时
 * 把服务器的处罚能力整体关掉。
 *
 * <p>注意 {@link #playerWeight} 与 {@link #ipWeight} 的区别必须保留：
 * 改造前 {@code canPunish} 走玩家权重，{@code canPunishTarget} 对含 {@code .}
 * 的目标走 IP 权重。两者的差异是有意的（IP 目标取该 IP 上在线玩家的最高权重），
 * 合并成一个方法会改变线上行为。
 */
public interface PunishmentDecisionHook {

    /** 操作者权重。控制台与 OP 通常为 {@link Integer#MAX_VALUE}。 */
    int operatorWeight(CommandSender sender);

    /** Web 面板操作者权重。 */
    int webOperatorWeight();

    /** 目标玩家权重；玩家不在线时返回 {@link Integer#MIN_VALUE}（离线玩家不享受免疫）。 */
    int playerWeight(String target);

    /** 目标 IP 的权重：该 IP 上所有在线玩家的最高权重；无人在线时返回 {@link Integer#MIN_VALUE}。 */
    int ipWeight(String ip);
}
