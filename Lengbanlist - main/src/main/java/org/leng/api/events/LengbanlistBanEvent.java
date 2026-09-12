package org.leng.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.leng.object.BanEntry;

public final class LengbanlistBanEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BanEntry entry;
    private final boolean silent;

    public LengbanlistBanEvent(BanEntry entry, boolean silent) {
        this.entry = entry;
        this.silent = silent;
    }

    public BanEntry getEntry() { return entry; }

    public boolean isSilent() { return silent; }

    public String getTarget() { return entry.target(); }

    public String getStaff() { return entry.staff(); }

    public String getReason() { return entry.reason(); }

    public long getDurationMillis() { return entry.time(); }

    @Override public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
