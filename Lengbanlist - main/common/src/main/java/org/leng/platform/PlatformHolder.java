package org.leng.platform;

public final class PlatformHolder {
    private static LengbanlistPlatform platform;

    private PlatformHolder() {
    }

    public static void set(LengbanlistPlatform value) {
        platform = value;
    }

    public static LengbanlistPlatform get() {
        return platform;
    }
}
