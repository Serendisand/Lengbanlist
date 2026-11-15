package org.leng.object;

import java.time.Duration;
import java.util.Objects;

public record BanIpEntry(
        String ip,
        String staff,
        long time,
        String reason,
        boolean isAuto,
        boolean active
) {

    public BanIpEntry {
        Objects.requireNonNull(ip, "IP cannot be null");
        Objects.requireNonNull(staff, "Staff cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
    }

    public BanIpEntry(String ip, String staff, long time, String reason, boolean isAuto) {
        this(ip, staff, time, reason, isAuto, true);
    }

    public BanIpEntry withEndTime(long newTime) {
        return new BanIpEntry(ip, staff, newTime, reason, isAuto, active);
    }

    public BanIpEntry withReason(String newReason) {
        return new BanIpEntry(ip, staff, time, newReason, isAuto, active);
    }

    public BanIpEntry withAuto(boolean newAuto) {
        return new BanIpEntry(ip, staff, time, reason, newAuto, active);
    }

    public BanIpEntry withActive(boolean newActive) {
        return new BanIpEntry(ip, staff, time, reason, isAuto, newActive);
    }

    public long getRemainingTime() {
        return Math.max(0, time - System.currentTimeMillis());
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > time;
    }

    /** 是否为永久封禁。 */
    public boolean isPermanent() {
        return time == Long.MAX_VALUE;
    }

    /** 剩余时长；永久封禁返回一个极大值，已过期返回 {@link Duration#ZERO}。 */
    public Duration remainingDuration() {
        if (isPermanent()) {
            return Duration.ofMillis(Long.MAX_VALUE);
        }
        return Duration.ofMillis(Math.max(0L, time - System.currentTimeMillis()));
    }

    @Override
    public String toString() {
        return ip + ":" + staff + ":" + time + ":" + reason + ":" + isAuto + ":" + active;
    }

    public String getIp() { return ip; }

    public String getStaff() { return staff; }

    public long getTime() { return time; }

    public long getEndTime() { return time; }

    public String getReason() { return reason; }

    public boolean isAuto() { return isAuto; }

    public boolean isActive() { return active; }
}
