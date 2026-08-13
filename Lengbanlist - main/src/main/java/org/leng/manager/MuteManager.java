package org.leng.manager;

import org.leng.Lengbanlist;
import org.leng.object.MuteEntry;
import org.leng.utils.IpMatcher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class MuteManager {
    private final Lengbanlist plugin;
    private final DatabaseManager db;
    private final Map<String, Long> muteCache = new ConcurrentHashMap<>();
    private final Map<String, Long> ipMuteCache = new ConcurrentHashMap<>();

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
        if (IpMatcher.isCidr(muteEntry.getTarget())) {
            ipMuteCache.put(muteEntry.getTarget(), muteEntry.getTime());
        }
        plugin.getAuditManager().log("禁言", muteEntry.getStaff(), muteEntry.getTarget(), muteEntry.getReason());
    }

    public void unmutePlayer(String target) {
        unmutePlayer(target, null);
    }

    public void unmutePlayer(String target, String actor) {
        boolean wasMuted = isPlayerMuted(target);
        db.deleteMute(target);
        muteCache.remove(target);
        ipMuteCache.remove(target);
        if (wasMuted) {
            plugin.getAuditManager().log("解除禁言", actor, target, "");
        }
    }

    public void clearMuteCache() {
        muteCache.clear();
        ipMuteCache.clear();
    }

    public void reloadMuteCache() {
        muteCache.clear();
        ipMuteCache.clear();
        for (MuteEntry entry : db.getMutes()) {
            if (entry.getTime() == Long.MAX_VALUE || entry.getTime() > System.currentTimeMillis()) {
                muteCache.put(entry.getTarget(), entry.getTime());
                if (IpMatcher.isCidr(entry.getTarget())) {
                    ipMuteCache.put(entry.getTarget(), entry.getTime());
                }
            } else {
                db.deleteMute(entry.getTarget());
            }
        }
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

    public boolean isIpMuted(String ip) {
        if (ip == null) return false;
        long now = System.currentTimeMillis();
        java.util.Iterator<Map.Entry<String, Long>> it = ipMuteCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            String cidr = entry.getKey();
            Long time = entry.getValue();
            if (IpMatcher.cidrMatches(ip, cidr)) {
                if (time == Long.MAX_VALUE || time > now) {
                    return true;
                } else {
                    it.remove();
                    db.deleteMute(cidr);
                }
            }
        }
        return false;
    }

    public boolean isPlayerMuted(org.bukkit.entity.Player player) {
        if (isPlayerMuted(player.getName())) return true;
        if (player.getAddress() != null) {
            String ip = player.getAddress().getAddress().getHostAddress();
            return isIpMuted(ip);
        }
        return false;
    }
}
