package org.leng.object;

import java.util.Objects;
import java.util.UUID;

public record FreezeEntry(
        String uuid,
        String player,
        String staff,
        long time,
        String reason
) {

    public FreezeEntry {
        Objects.requireNonNull(uuid, "Uuid cannot be null");
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(staff, "Staff cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
    }

    public UUID uniqueId() {
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String getPlayer() { return player; }

    public String getStaff() { return staff; }

    public long getTime() { return time; }

    public String getReason() { return reason; }

    public String getUuid() { return uuid; }
}
