package org.leng.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基类的编译期契约。
 *
 * <p>这里钉的是一个真踩过的坑：{@code onEnable()}（Bukkit 的无参生命周期）与
 * {@code onEnable(ExtensionContext)}（扩展契约的回调）是<b>重载</b>而不是覆盖。
 * 基类若漏实现后者，子类就得自己补——那样基类等于白写，而编译错误只会出现在子类那边，
 * 信息还指向一行看不出所以然的签名。
 *
 * <p>所以这个用例的价值在编译期：下面的子类只写 {@code id()} 与
 * {@code onExtensionEnable}，一旦基类缺了哪个抽象方法，本文件直接编译不过。
 */
class LengbanlistExtensionPluginTest {

    private static final class Sample extends LengbanlistExtensionPlugin {

        @Override
        public String id() {
            return "sample";
        }

        @Override
        protected void onExtensionEnable(ExtensionContext context) {
            // 示例子类不需要做什么
        }
    }

    @Test
    void subclassOnlyHasToProvideIdAndExtensionEnable() {
        // 不 new 它：JavaPlugin 的构造函数要求 PluginClassLoader，测试环境里造不出来。
        // 真正要钉的是"上面那个子类能编译过"——基类少一个抽象方法，这个文件就编译失败。
        assertTrue(LengbanlistExtensionPlugin.class.isAssignableFrom(Sample.class));
        assertTrue(LengbanlistExtension.class.isAssignableFrom(Sample.class),
                "基类必须同时是契约的实现，否则扩展注册表认不出它");
    }
}
