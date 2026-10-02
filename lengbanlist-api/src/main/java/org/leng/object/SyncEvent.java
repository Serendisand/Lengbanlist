package org.leng.object;

public record SyncEvent(
        long id,
        long timestamp,
        String server,
        String scope,
        String target,
        String action
) {

    public static final String SCOPE_BAN = "ban";
    public static final String SCOPE_IP_BAN = "ip-ban";
    public static final String SCOPE_MUTE = "mute";
    public static final String SCOPE_FREEZE = "freeze";
    public static final String SCOPE_WARN = "warn";

    public static final String ACTION_ADD = "add";
    public static final String ACTION_REMOVE = "remove";
    public static final String ACTION_UPDATE = "update";

    public static final String TARGET_ALL = "*";

    public SyncEvent {
        server = server == null ? "" : server;
        scope = scope == null ? "" : scope;
        target = target == null ? "" : target;
        action = action == null ? "" : action;
    }
}
