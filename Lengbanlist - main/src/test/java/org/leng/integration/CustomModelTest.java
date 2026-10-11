package org.leng.integration;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CustomModelTest {

    @Mock CommandSender sender;

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

    // ---------------------------------------------------------------- 扩展命令补行

    @Test
    void extensionCommandsGoInsideTheHelpBox() {
        List<String> lines = List.of("§b╔══════╗", "§2✦ §b/lban add …", "§b╚══════╝");
        List<String> additions = List.of("§6§l◆ 扩展", "§2✦ §b/sc <内容> §7- §3工作频道聊天");

        List<String> merged = CustomModel.insertBeforeFooter(lines, additions);

        assertEquals(List.of("§b╔══════╗", "§2✦ §b/lban add …", "§6§l◆ 扩展",
                "§2✦ §b/sc <内容> §7- §3工作频道聊天", "§b╚══════╝"), merged,
                "补的行要在框内——掉到底边下面就不像帮助了");
    }

    @Test
    void additionsFallToTheEndWhenTheModelDrawsNoBox() {
        List<String> merged = CustomModel.insertBeforeFooter(
                List.of("§2✦ §b/lban add …"), List.of("§6§l◆ 扩展"));

        assertEquals(List.of("§2✦ §b/lban add …", "§6§l◆ 扩展"), merged);
    }

    /**
     * 没有插件实例时（单元测试环境）也要照常打印，不能 NPE。
     *
     * <p>{@code showHelp} 会去问注册表"有没有扩展命令要补"，而注册表要插件才拿得到；
     * 这里顺带钉住那条降级路径。
     */
    @Test
    void showHelpStillPrintsStaticLinesWithoutAPluginInstance() throws Exception {
        List<String> sent = new ArrayList<>();
        lenient().doAnswer(call -> {
            sent.add(call.getArgument(0));
            return null;
        }).when(sender).sendMessage(anyString());

        model("default").showHelp(sender);

        assertFalse(sent.isEmpty());
        assertTrue(sent.stream().anyMatch(line -> line.contains("/lban add")), "静态帮助行照旧要发出来");
    }
}
