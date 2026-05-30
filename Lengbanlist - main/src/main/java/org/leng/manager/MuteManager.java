package org.leng.manager;

import org.leng.Lengbanlist;
import org.leng.object.MuteEntry;

import java.util.List;

/** 禁言管理，处理玩家禁言/解禁及状态查询。 */
public class MuteManager {
    private final Lengbanlist plugin;
    private final DatabaseManager db;

    public MuteManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public void mutePlayer(MuteEntry muteEntry) {
        if (isPlayerMuted(muteEntry.getTarget())) {
            return;
        }
        db.upsertMute(muteEntry);
    }

    public void unmutePlayer(String target) {
        db.deleteMute(target);
    }

    public List<MuteEntry> getMuteList() {
        return db.getMutes();
    }

    public boolean isPlayerMuted(String playerName) {
        MuteEntry entry = db.getMute(playerName);
        if (entry == null) return false;
        if (entry.getTime() == Long.MAX_VALUE || entry.getTime() > System.currentTimeMillis()) return true;
        unmutePlayer(playerName);
        return false;
    }
}
