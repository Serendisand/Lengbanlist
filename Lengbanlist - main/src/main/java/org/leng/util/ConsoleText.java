package org.leng.util;

import java.util.Locale;

public enum ConsoleText {

    MODEL_DETECTED("首次加载，根据系统语言（{language}）自动选择模型: {model}",
            "First run: picked model {model} from system language ({language})"),
    MODEL_DETECT_SAVE_FAILED("写入模型自动检测结果失败: {error}",
            "Failed to save the auto-detected model: {error}"),
    PRESET_BASE("已预置全局默认文本: models/_base.yml（模型未覆写的字段自动沿用此处）",
            "Installed default texts: models/_base.yml (models inherit every field they don't override)"),
    PRESET_BUILTIN("已预置内置模型: models/{name}.yml", "Installed built-in model: models/{name}.yml"),
    STORAGE_MIGRATED("已把 config.yml 中的数据库配置迁移到 storage.yml",
            "Moved database settings from config.yml to storage.yml"),
    STORAGE_MIGRATE_FAILED("迁移数据库配置失败: {error}", "Failed to migrate database settings: {error}"),
    EULA_REQUIRED("插件启用被终止：您需要同意EULA才能使用本插件！",
            "Startup aborted: you must accept the EULA to use this plugin!"),
    EULA_HINT("请编辑 plugins/Lengbanlist/eula.yml 文件", "Edit plugins/Lengbanlist/eula.yml to accept it"),
    DB_INIT_FAILED("插件启用被终止：数据库初始化失败，请检查 database 配置和数据库连接。",
            "Startup aborted: database initialization failed — check database settings and connectivity."),

    LOADING("§f原神§2正在加载", "§fGenshin§2 is loading"),
    READY("§bLengbanlist §6干杯[]~(￣▽￣)~* §7v{version} §7| §3模型 {model} §7| §3服务端 {server}",
            "§bLengbanlist §6Cheers! []~(￣▽￣)~* §7v{version} §7| §3Model {model} §7| §3Server {server}"),
    TIP("§6偷偷告诉你: §e{tip}", "§6Psst, a little secret: §e{tip}"),
    PLACEHOLDER_HOOK("§a已接入 PlaceholderAPI，可使用 %lengbanlist_*% 占位符",
            "§aPlaceholderAPI hooked — %lengbanlist_*% placeholders are available"),
    AUTO_UPDATE("§a自动更新功能已启用，正在检查更新...", "§aAuto-update enabled, checking for updates..."),
    SHUTDOWN("§k§4正在收拾行李qwq...", "§k§4Packing our bags, qwq..."),
    FAREWELL("§f期待我们的下一次相遇！", "§fUntil we meet again!");

    private static final boolean CHINESE = detectChinese();

    private final String chinese;
    private final String english;

    ConsoleText(String chinese, String english) {
        this.chinese = chinese;
        this.english = english;
    }

    public String text(String... placeholders) {
        return fill(CHINESE ? chinese : english, placeholders);
    }

    String chinese() {
        return chinese;
    }

    String english() {
        return english;
    }

    public static String fill(String template, String... placeholders) {
        String result = template == null ? "" : template;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String value = placeholders[i + 1];
            result = result.replace("{" + placeholders[i] + "}", value == null ? "" : value);
        }
        return result;
    }

    public static ConsoleText of(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        for (ConsoleText entry : values()) {
            if (entry.name().equalsIgnoreCase(key.replace('-', '_'))) {
                return entry;
            }
        }
        return null;
    }

    public static String builtIn(String key, String... placeholders) {
        ConsoleText entry = of(key);
        return entry == null ? "" : entry.text(placeholders);
    }

    private static boolean detectChinese() {
        String language = Locale.getDefault().getLanguage();
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("zh");
    }
}
