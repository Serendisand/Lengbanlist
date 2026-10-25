package org.leng.object;

import java.util.Objects;

/**
 * 举报功能禁用记录：在 {@code endTime} 之前该玩家不能提交举报。
 * {@code endTime == Long.MAX_VALUE} 表示永久禁用。
 */
public record ReportBanEntry(
        String target,
        String staff,
        long endTime,
        String reason
) {

    public ReportBanEntry {
        Objects.requireNonNull(target, "Target cannot be null");
        Objects.requireNonNull(staff, "Staff cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
    }

    public boolean isActive(long now) {
        return endTime == Long.MAX_VALUE || endTime > now;
    }
}
