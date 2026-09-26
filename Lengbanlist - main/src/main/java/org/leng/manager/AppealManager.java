package org.leng.manager;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.object.AppealEntry;
import org.leng.object.BanEntry;
import org.leng.object.PlayerIdentity;
import org.leng.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AppealManager {

    private final Lengbanlist plugin;
    private final Map<String, PendingTicket> tickets = new ConcurrentHashMap<>();

    private record PendingTicket(String player, long banId, long expiresAt) { }

    public AppealManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.isFeatureEnabled("appeal") && plugin.getConfig().getBoolean("appeal.enabled", true);
    }

    public boolean isPublicEnabled() {
        return isEnabled() && plugin.getConfig().getBoolean("appeal.public-enabled", false);
    }

    public String publicUrl() {
        String url = plugin.getConfig().getString("appeal.public-url", "");
        return url == null ? "" : url.trim();
    }

    private int intSetting(String key, int fallback) {
        return plugin.getConfig().getInt(key, fallback);
    }

    private boolean booleanSetting(String key, boolean fallback) {
        return plugin.getConfig().getBoolean(key, fallback);
    }

    public record Precheck(boolean ok, String message, String player, String reason, long banEndsAt, String ticket) { }

    public record Submit(boolean ok, String message, String ticket) { }

    public Precheck precheck(String rawPlayer) {
        if (!isPublicEnabled()) {
            return new Precheck(false, "该服务器未开放网页申诉", "", "", 0L, "");
        }
        String player = rawPlayer == null ? "" : rawPlayer.trim();
        if (player.isEmpty() || !player.matches("[A-Za-z0-9_]{3,16}")) {
            return new Precheck(false, "请输入正确的玩家名", "", "", 0L, "");
        }
        DatabaseManager db = plugin.getDatabaseManager();
        BanEntry ban = db.getBan(player);
        if (ban == null) {
            PlayerIdentity identity = db.getIdentityResolver().resolve(player);
            if (identity.hasUuid()) {
                ban = db.getBanByUuid(identity.uuid());
            }
        }
        long now = System.currentTimeMillis();
        if (ban == null || ban.getTime() <= now) {
            return new Precheck(false, "没有查到该玩家生效中的封禁，无法申诉", player, "", 0L, "");
        }
        int maxPending = Math.max(1, intSetting("appeal.max-pending", 3));
        if (db.countAppealsByTargetAndStatus(player, AppealEntry.STATUS_PENDING) >= maxPending) {
            return new Precheck(false, "该玩家还有未处理的申诉，请等待管理员处理", player, "", 0L, "");
        }
        long cooldownSeconds = Math.max(0, intSetting("appeal.cooldown-seconds", 600));
        if (cooldownSeconds > 0 && db.countAppealsByTargetSince(player, now - cooldownSeconds * 1000L) > 0) {
            return new Precheck(false, "刚提交过申诉，请稍后再试", player, "", 0L, "");
        }
        purgeExpiredTickets(now);
        String ticket = UUID.randomUUID().toString();
        long ttl = Math.max(1, intSetting("appeal.ticket-ttl-minutes", 30)) * 60000L;
        tickets.put(ticket, new PendingTicket(player, db.getActiveBanId(ban.getTarget()), now + ttl));
        return new Precheck(true, "", player, ChatColor.stripColor(ban.getReason()), ban.getTime(), ticket);
    }

    public Submit submit(String ticket, String contact, String reason) {
        if (!isPublicEnabled()) {
            return new Submit(false, "该服务器未开放网页申诉", "");
        }
        long now = System.currentTimeMillis();
        PendingTicket pending = ticket == null ? null : tickets.remove(ticket);
        if (pending == null || pending.expiresAt() < now) {
            return new Submit(false, "申诉凭证已失效，请重新查询封禁信息", "");
        }
        String body = reason == null ? "" : reason.trim();
        int minLength = Math.max(1, intSetting("appeal.min-reason-length", 10));
        if (body.length() < minLength) {
            return new Submit(false, "申诉理由至少需要 " + minLength + " 个字", "");
        }
        String contactText = contact == null ? "" : contact.trim();
        if (booleanSetting("appeal.require-contact", false) && contactText.isEmpty()) {
            return new Submit(false, "请填写联系方式，方便管理员回复你", "");
        }
        DatabaseManager db = plugin.getDatabaseManager();
        PlayerIdentity identity = db.resolveIdentity(pending.player());
        AppealEntry entry = new AppealEntry(UUID.randomUUID().toString(), pending.banId(), pending.player(),
                identity.uuid(), contactText, body, AppealEntry.STATUS_PENDING, now, "", 0L, "",
                UUID.randomUUID().toString(), false);
        db.upsertAppeal(entry);
        plugin.getAuditManager().log("申诉提交", pending.player(), pending.player(), body);
        notifyWebhook("申诉", pending.player(), pending.player(), body);
        return new Submit(true, "", entry.ticket());
    }

    public AppealEntry findByTicket(String ticket) {
        if (!isPublicEnabled()) {
            return null;
        }
        return plugin.getDatabaseManager().getAppealByTicket(ticket);
    }

    public List<AppealEntry> list(String status, int limit) {
        return plugin.getDatabaseManager().getAppeals(status, limit);
    }

    public int countPending() {
        return plugin.getDatabaseManager().countPendingAppeals();
    }

    public boolean handle(String id, boolean approve, String operator, String response) {
        DatabaseManager db = plugin.getDatabaseManager();
        AppealEntry entry = db.getAppeal(id);
        if (entry == null || !entry.isPending()) {
            return false;
        }
        String reply = response == null ? "" : response.trim();
        if (approve) {
            BanEntry ban = db.getBan(entry.target());
            if (ban == null || ban.getTime() <= System.currentTimeMillis()) {
                return false;
            }
            BanManager.BanMutationResult result = plugin.getBanManager()
                    .tryUnbanPlayer(entry.target(), operator, false);
            if (!result.isApplied()) {
                return false;
            }
        }
        db.upsertAppeal(entry.handled(operator, approve ? AppealEntry.STATUS_APPROVED : AppealEntry.STATUS_REJECTED, reply, System.currentTimeMillis()));
        String action = approve ? "申诉通过" : "申诉驳回";
        plugin.getAuditManager().log(action, operator, entry.target(), reply);
        notifyWebhook(action, operator, entry.target(), reply);
        notifyOnline(entry.target(), approve, reply);
        return true;
    }

    public AppealEntry pendingResultFor(String player) {
        if (player == null || player.isEmpty()) {
            return null;
        }
        for (AppealEntry entry : plugin.getDatabaseManager().getAppealsByTarget(player, 20)) {
            if (entry.isPending() || entry.notified()) {
                continue;
            }
            return entry;
        }
        return null;
    }

    public void markNotified(String id) {
        plugin.getDatabaseManager().markAppealNotified(id);
    }

    private void notifyOnline(String player, boolean approve, String response) {
        Player online = plugin.getServer().getPlayerExact(player);
        if (online == null) {
            return;
        }
        String message = plugin.prefix() + (approve ? "§a你的申诉已通过，封禁已解除" : "§c你的申诉未通过")
                + (response.isEmpty() ? "" : "§7：§f" + response);
        Utils.sendMessage(online, message);
    }

    private void notifyWebhook(String action, String actor, String target, String reason) {
        if (plugin.getWebhookNotifier() == null) {
            return;
        }
        plugin.getWebhookNotifier().notifyEvent(action, actor, target, reason, true);
    }

    private void purgeExpiredTickets(long now) {
        if (tickets.size() < 256) {
            return;
        }
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, PendingTicket> entry : tickets.entrySet()) {
            if (entry.getValue().expiresAt() < now) {
                expired.add(entry.getKey());
            }
        }
        for (String key : expired) {
            tickets.remove(key);
        }
    }
}
