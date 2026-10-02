package org.leng.api;

/**
 * 扩展入口。
 *
 * <p>一个扩展本身就是一个独立的 Bukkit 插件：它的主类同时实现本接口，
 * 并在自己的 {@code onEnable()} 里调用
 * {@code Bukkit.getServicesManager().load(LengbanlistCore.class).enable(this)}。
 *
 * <p>扩展的 {@code plugin.yml} 必须声明 {@code depend: [Lengbanlist]} —— 这一行是
 * 扩展在运行时拿到本模块与核心类的唯一来源（Paper 的类加载访问按"直接与传递依赖"
 * 授权；legacy Spigot 插件可访问服务器上任意 classloader）。
 *
 * <p>扩展以 {@code provided} 作用域依赖 {@code lengbanlist-api}，
 * <b>绝不可</b>把 {@code org/leng/api} 与 {@code org/leng/object} 打进自己的 jar：
 * 否则会加载出第二份接口副本，对核心传回的对象做类型检查时报
 * {@code ClassCastException}，且极难定位。CI 应强制检查这一点。
 */
public interface LengbanlistExtension {

    /**
     * 扩展唯一标识，全小写、无连字符，例如 {@code vanish}、{@code audittools}。
     *
     * <p>它同时是 {@code extensions.yml} 里的开关键与市场 {@code index.json} 里的 id，
     * 一旦发布不应更改。
     */
    String id();

    /** 展示名，用于日志与 {@code /lban ext list}；默认与 {@link #id()} 相同。 */
    default String name() {
        return id();
    }

    /** 扩展自身版本，建议语义化版本，例如 {@code 1.2.0}。 */
    String version();

    /**
     * 所需的契约版本范围，语义化范围写法，例如 {@code [2.0,3.0)}。
     *
     * <p>留空表示不校验。核心在 {@link LengbanlistCore#enable} 时会校验，
     * 不满足则拒绝启用并给出可读原因，而不是等到运行时抛 {@code NoSuchMethodError}。
     */
    default String requiredApi() {
        return "";
    }

    /**
     * 核心启用该扩展时调用。此时应当：
     * 注册命令（{@link ExtensionContext#commands()}）、读配置、注册监听器与调度任务。
     *
     * <p>抛出异常表示启用失败：核心只会禁用这一个扩展并记录原因，不会影响服务器。
     */
    void onEnable(ExtensionContext context) throws Exception;

    /** 核心停用该扩展时调用。应当释放自己的资源（取消任务、保存配置）。 */
    default void onDisable() {
    }

    /**
     * 本扩展负责的功能键。
     *
     * <p>命令里声明的功能键会自动并入，这里只需列出<b>没有命令的功能</b>——
     * 例如只提供钩子的免疫系统、只写审计的哈希链。核心据此判断
     * "这个功能是否已安装"，也是 {@code extensions.yml} 与市场索引的对账依据。
     */
    default java.util.Set<String> features() {
        return java.util.Set.of();
    }
}
