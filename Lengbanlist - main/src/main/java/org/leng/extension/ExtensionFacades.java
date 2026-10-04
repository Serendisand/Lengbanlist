package org.leng.extension;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.api.BanService;
import org.leng.api.DataStore;
import org.leng.api.Messages;
import org.leng.api.MuteService;
import org.leng.api.Services;
import org.leng.api.WarnService;
import org.leng.storage.DatabaseManager;
import org.leng.integration.ModelManager;
import org.leng.models.Model;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.WarnEntry;
import org.leng.util.TimeUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * 契约接口在核心侧的实现。
 *
 * <p>全部是<b>薄委托</b>：只把调用转给已有的 manager，不复制任何业务逻辑。
 * 这样核心的行为只有一处真相——manager；扩展拿到的门面永远与命令层一致。
 *
 * <p>放在同一个文件里是因为它们彼此高度相关、且都不对外暴露：外部只看到
 * {@code org.leng.api} 里的接口。
 */
final class ExtensionFacades {

    private ExtensionFacades() {
    }

    // ------------------------------------------------------------ 文案

    static final class MessagesImpl implements Messages {

        @Override
        public Model current() {
            return ModelManager.getCurrentModel();
        }

        @Override
        public String currentName() {
            String name = ModelManager.getCurrentModelName();
            return name == null ? "" : name;
        }
    }

    // ------------------------------------------------------------ 数据

    static final class DataStoreImpl implements DataStore {

        private final Lengbanlist plugin;
        private final String tablePrefix;

        DataStoreImpl(Lengbanlist plugin, String extensionId) {
            this.plugin = plugin;
            // 表名里把连字符换成下划线：扩展 id 允许连字符，但 SQL 标识符不方便带
            this.tablePrefix = "ext_" + extensionId.replace('-', '_') + "_";
        }

        @Override
        public Connection connection() throws SQLException {
            DatabaseManager database = plugin.getDatabaseManager();
            if (database == null) {
                throw new SQLException("数据库尚未初始化");
            }
            return database.getConnection();
        }

        @Override
        public String tablePrefix() {
            return tablePrefix;
        }

        @Override
        public boolean isNetworkDatabase() {
            DatabaseManager database = plugin.getDatabaseManager();
            return database != null && database.isNetworkDatabase();
        }
    }

    // ------------------------------------------------------------ 服务

    static final class ServicesImpl implements Services {

        private final BanService bans;
        private final MuteService mutes;
        private final WarnService warnings;

        ServicesImpl(Lengbanlist plugin) {
            this.bans = new BanServiceImpl(plugin);
            this.mutes = new MuteServiceImpl(plugin);
            this.warnings = new WarnServiceImpl(plugin);
        }

        @Override
        public BanService bans() {
            return bans;
        }

        @Override
        public MuteService mutes() {
            return mutes;
        }

        @Override
        public WarnService warnings() {
            return warnings;
        }
    }

    private static final class BanServiceImpl implements BanService {

        private final Lengbanlist plugin;

        private BanServiceImpl(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean ban(String target, String staff, long durationMillis, String reason, boolean silent) {
            BanEntry entry = new BanEntry(target, staff, TimeUtils.calculateEndTime(durationMillis), reason, false);
            return plugin.getBanManager().tryBanPlayer(entry, silent).isApplied();
        }

        @Override
        public boolean banIp(String ip, String staff, long durationMillis, String reason, boolean silent) {
            BanIpEntry entry = new BanIpEntry(ip, staff, TimeUtils.calculateEndTime(durationMillis), reason, false);
            return plugin.getBanManager().tryBanIp(entry, silent).isApplied();
        }

        @Override
        public boolean unban(String target, String actor) {
            return plugin.getBanManager().tryUnbanPlayer(target, actor, false).isApplied();
        }

        @Override
        public boolean unbanIp(String ip, String actor) {
            return plugin.getBanManager().tryUnbanIp(ip, actor, false).isApplied();
        }

        @Override
        public boolean isBanned(String target) {
            return plugin.getBanManager().isPlayerBanned(target);
        }

        @Override
        public boolean isIpBanned(String ip) {
            return plugin.getBanManager().isIpBanned(ip);
        }

        @Override
        public Optional<BanEntry> findBan(String target) {
            return Optional.ofNullable(plugin.getBanManager().getBanEntry(target));
        }

        @Override
        public Optional<BanIpEntry> findIpBan(String ip) {
            return Optional.ofNullable(plugin.getBanManager().getBanIpEntry(ip));
        }

        @Override
        public List<BanEntry> activeBans() {
            return plugin.getBanManager().getBanList();
        }

        @Override
        public List<BanIpEntry> activeIpBans() {
            return plugin.getBanManager().getBanIpList();
        }

        @Override
        public int activeBanCount() {
            return plugin.getBanManager().countActiveBans();
        }

        @Override
        public int activeIpBanCount() {
            return plugin.getBanManager().countActiveIpBans();
        }
    }

    private static final class MuteServiceImpl implements MuteService {

        private final Lengbanlist plugin;

        private MuteServiceImpl(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public Long mute(String target, String staff, long durationMillis, String reason) {
            MuteEntry entry = new MuteEntry(target, staff, TimeUtils.calculateEndTime(durationMillis), reason);
            return plugin.getMuteManager().mutePlayer(entry);
        }

        @Override
        public boolean unmute(String target, String actor) {
            return plugin.getMuteManager().unmutePlayerIfMuted(target, actor);
        }

        @Override
        public boolean isMuted(String target) {
            return plugin.getMuteManager().isPlayerMuted(target);
        }

        @Override
        public List<MuteEntry> activeMutes() {
            return plugin.getMuteManager().getMuteList();
        }

        @Override
        public int activeMuteCount() {
            return plugin.getMuteManager().countActiveMutes();
        }
    }

    private static final class WarnServiceImpl implements WarnService {

        private final Lengbanlist plugin;

        private WarnServiceImpl(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public void warn(String target, String staff, String reason) {
            plugin.getWarnManager().warnPlayer(target, staff, reason);
        }

        @Override
        public boolean unwarn(String target, int warnId, CommandSender actor) {
            return plugin.getWarnManager().unwarnPlayer(target, warnId, actor);
        }

        @Override
        public int countActiveWarnings(String target) {
            return plugin.getWarnManager().countActiveWarnings(target);
        }

        @Override
        public List<WarnEntry> activeWarnings(String target) {
            return plugin.getWarnManager().getActiveWarnings(target);
        }

        @Override
        public List<String> warnedPlayers() {
            return plugin.getWarnManager().getWarnedPlayers();
        }
    }
}
