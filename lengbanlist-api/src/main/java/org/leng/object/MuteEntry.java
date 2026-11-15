package org.leng.object;

import java.time.Duration;
import java.util.Objects;

public record MuteEntry(
        String target,
        String staff,
        long time,
        String reason
) {

    public MuteEntry {
        Objects.requireNonNull(target, "Target cannot be null");
        Objects.requireNonNull(staff, "Staff cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
    }

    public long getEndTime() { return time; }

    public long getTime() { return time; }

    /** 是否为永久禁言。 */
    public boolean isPermanent() { return time == Long.MAX_VALUE; }

    /** 是否已到期。 */
    public boolean isExpired() { return System.currentTimeMillis() > time; }

    /** 剩余时长；永久禁言返回一个极大值，已过期返回 {@link Duration#ZERO}。 */
    public Duration remainingDuration() {
        if (isPermanent()) {
            return Duration.ofMillis(Long.MAX_VALUE);
        }
        return Duration.ofMillis(Math.max(0L, time - System.currentTimeMillis()));
    }

    public String getTarget() { return target; }

    public String getStaff() { return staff; }

    public String getReason() { return reason; }

    @Override
    public String toString() {
        return target + ":" + staff + ":" + time + ":" + reason;
    }
}
