package org.leng.api;

/**
 * 审计日志的写入门面。
 *
 * <p>扩展执行了值得追责的管理动作时调用它。核心会一并处理开关判断、
 * 哈希链、Webhook 通知与数据库写入；扩展不需要也不应该直接写 {@code audit_log} 表。
 */
public interface AuditService {

    /**
     * 记一条审计日志。
     *
     * @param action  动作名，例如"开启隐身"
     * @param actor   操作人
     * @param target  目标；没有目标时传空串
     * @param details 附加说明；没有时传空串
     */
    void log(String action, String actor, String target, String details);
}
