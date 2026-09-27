package org.leng.models;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomModelTest {

    private static YamlConfiguration resource(String path) throws Exception {
        try (InputStream in = CustomModelTest.class.getResourceAsStream(path)) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static CustomModel model(String name) throws Exception {
        return new CustomModel(name, resource("/models/" + name + ".yml"), resource("/models/_base.yml"));
    }

    @Test
    void addBanKeepsSubDayDurationAsIs() throws Exception {
        CustomModel model = model("default");
        assertEquals("§a玩家 苦力怕 已被封禁 1分钟，原因是：测试", model.addBan("苦力怕", "1分钟", "测试"));
        assertEquals("§a玩家 苦力怕 已被封禁 30秒，原因是：测试", model.addBan("苦力怕", "30秒", "测试"));
        assertEquals("§a玩家 苦力怕 已被封禁 6小时，原因是：测试", model.addBan("苦力怕", "6小时", "测试"));
    }

    @Test
    void addBanKeepsForeverAndDays() throws Exception {
        CustomModel model = model("default");
        assertEquals("§a玩家 苦力怕 已被封禁 永久，原因是：测试", model.addBan("苦力怕", "永久", "测试"));
        assertEquals("§a玩家 苦力怕 已被封禁 3天，原因是：测试", model.addBan("苦力怕", "3天", "测试"));
    }

    @Test
    void addBanIpRendersDuration() throws Exception {
        CustomModel model = model("default");
        assertEquals("§aIP 1.2.3.4 已被封禁 5分钟，原因是：测试", model.addBanIp("1.2.3.4", "5分钟", "测试"));
    }

    @Test
    void englishModelRendersDurationWithoutSuffixConfig() throws Exception {
        CustomModel model = model("english");
        assertEquals("§bEnglish Model: §aPlayer Steve has been banned 7 days, reason: cheat",
                model.addBan("Steve", "7 days", "cheat"));
        assertEquals("§bEnglish Model: §aIP 1.2.3.4 has been banned 5 minutes, reason: cheat",
                model.addBanIp("1.2.3.4", "5 minutes", "cheat"));
        assertEquals("§bEnglish Model: §aPlayer Steve has been banned permanently, reason: cheat",
                model.addBan("Steve", "permanently", "cheat"));
    }
}
