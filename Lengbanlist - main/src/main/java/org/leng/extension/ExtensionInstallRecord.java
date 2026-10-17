package org.leng.extension;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.leng.Lengbanlist;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;

/**
 * 扩展的"市场安装记录"：{@code extensions/.cache/installed.json}。
 *
 * <p>记录每个扩展安装时的版本、类型与文件 sha256。市场据此判断：
 *
 * <ul>
 *   <li>版本相同 → 已是最新；</li>
 *   <li>文件 sha 与记录不一致 → 用户改过，不覆盖；</li>
 *   <li>没有记录 → 不是市场装的，同样不覆盖。</li>
 * </ul>
 *
 * <p>随核心打包的官方 Lua 扩展在首次释放时也会登记进来，这样它们能被市场正常更新，
 * 而不是永远被当成"用户自己放的文件"。
 */
public final class ExtensionInstallRecord {

    public record Entry(String version, String kind, String sha256) {
    }

    private final Lengbanlist plugin;

    public ExtensionInstallRecord(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public Path file() {
        return plugin.getDataFolder().toPath().resolve("extensions/.cache/installed.json");
    }

    public Optional<Entry> get(String id) {
        JsonObject all = read();
        if (!all.has(id) || !all.get(id).isJsonObject()) {
            return Optional.empty();
        }
        JsonObject entry = all.getAsJsonObject(id);
        return Optional.of(new Entry(str(entry, "version"), str(entry, "kind"), str(entry, "sha256")));
    }

    public void put(String id, String version, String kind, String sha256) {
        try {
            Path p = file();
            Files.createDirectories(p.getParent());
            JsonObject all = read();
            JsonObject entry = new JsonObject();
            entry.addProperty("version", version == null ? "" : version);
            entry.addProperty("kind", kind == null ? "" : kind);
            entry.addProperty("sha256", sha256 == null ? "" : sha256);
            all.add(id, entry);
            Files.writeString(p, all.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[ExtCloud] 写入安装记录失败", e);
        }
    }

    private JsonObject read() {
        Path p = file();
        if (!Files.exists(p)) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "[ExtCloud] 读取安装记录失败", e);
            return new JsonObject();
        }
    }

    private static String str(JsonObject obj, String key) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString()
                : "";
    }
}
