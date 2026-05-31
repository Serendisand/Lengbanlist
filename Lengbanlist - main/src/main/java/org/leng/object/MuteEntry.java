package org.leng.object;

public class MuteEntry {
    private final String target;
    private final String staff;
    private final long time;
    private final String reason;

    public MuteEntry(String target, String staff, long time, String reason) {
        this.target = target;
        this.staff = staff;
        this.time = time;
        this.reason = reason;
    }


    public String getTarget() {
        return target;
    }

    public String getStaff() {
        return staff;
    }

    public long getTime() {
        return time;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return target + ":" + staff + ":" + time + ":" + reason;
    }
}
