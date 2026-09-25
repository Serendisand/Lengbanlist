package org.leng.object;

public record AppealEntry(
        String id,
        long banId,
        String target,
        String uuid,
        String contact,
        String reason,
        String status,
        long createdAt,
        String handledBy,
        long handledAt,
        String response,
        String ticket,
        boolean notified
) {

    public static final String STATUS_PENDING = "待处理";
    public static final String STATUS_APPROVED = "已通过";
    public static final String STATUS_REJECTED = "已驳回";

    public AppealEntry {
        id = id == null ? "" : id;
        target = target == null ? "" : target;
        uuid = uuid == null ? "" : uuid;
        contact = contact == null ? "" : contact;
        reason = reason == null ? "" : reason;
        status = status == null || status.trim().isEmpty() ? STATUS_PENDING : status;
        handledBy = handledBy == null ? "" : handledBy;
        response = response == null ? "" : response;
        ticket = ticket == null ? "" : ticket;
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public AppealEntry handled(String by, String newStatus, String reply, long at) {
        return new AppealEntry(id, banId, target, uuid, contact, reason, newStatus, createdAt,
                by, at, reply, ticket, notified);
    }

    public AppealEntry markedNotified() {
        return new AppealEntry(id, banId, target, uuid, contact, reason, status, createdAt,
                handledBy, handledAt, response, ticket, true);
    }
}
