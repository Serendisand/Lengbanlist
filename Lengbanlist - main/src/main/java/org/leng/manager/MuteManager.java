package org.leng.manager;

import org.leng.Lengbanlist;
import org.leng.object.MuteEntry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class MuteManager {
    private final Lengbanlist plugin;
    private final DatabaseManager db;
    private final Map<String, Long> muteCache = new ConcurrentHashMap<>();

    public MuteManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public void mutePlayer(MuteEntry muteEntry) {
        if (isPlayerMuted(muteEntry.getTarget())) {
            return;
        }
        db.upsertMute(muteEntry);
        muteCache.put(muteEntry.getTarget(), muteEntry.getTime());
    }

    public void unmutePlayer(String target) {
        db.deleteMute(target);
        muteCache.remove(target);
    }

    public List<MuteEntry> getMuteList() {
        return db.getMutes();
    }

    public boolean isPlayerMuted(String playerName) {
        Long cached = muteCache.get(playerName);
        if (cached != null) {
            if (cached == Long.MAX_VALUE || cached > System.currentTimeMillis()) {
                return true;
            }
            muteCache.remove(playerName);
            db.deleteMute(playerName);
            return false;
        }
        MuteEntry entry = db.getMute(playerName);
        if (entry == null) return false;
        if (entry.getTime() == Long.MAX_VALUE || entry.getTime() > System.currentTimeMillis()) {
            muteCache.put(playerName, entry.getTime());
            return true;
        }
        unmutePlayer(playerName);
        return false;
    }
}
