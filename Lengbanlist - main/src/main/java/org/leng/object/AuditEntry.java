package org.leng.object;

public class AuditEntry {
    private final long id;
    private final long timestamp;
    private final String actor;
    private final String action;
    private final String target;
    private final String reason;
    private final boolean success;
    private final String prevHash;

    public AuditEntry(long timestamp, String actor, String action, String target, String reason, boolean success) {
        this(0, timestamp, actor, action, target, reason, success, "");
    }

    public AuditEntry(long id, long timestamp, String actor, String action, String target, String reason, boolean success, String prevHash) {
        this.id = id;
        this.timestamp = timestamp;
        this.actor = actor == null ? "" : actor;
        this.action = action == null ? "" : action;
        this.target = target == null ? "" : target;
        this.reason = reason == null ? "" : reason;
        this.success = success;
        this.prevHash = prevHash == null ? "" : prevHash;
    }

    public long getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getReason() {
        return reason;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getPrevHash() {
        return prevHash;
    }
}
