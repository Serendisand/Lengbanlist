package org.leng.object;

import java.util.Objects;

public record FreezeEntry(
        String player,
        String staff,
        long time,
        String reason
) {

    public FreezeEntry {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(staff, "Staff cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
    }

    public String key() {
        return player.toLowerCase(java.util.Locale.ROOT);
    }

    public String getPlayer() { return player; }

    public String getStaff() { return staff; }

    public long getTime() { return time; }

    public String getReason() { return reason; }
}
