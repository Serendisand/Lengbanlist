package org.leng.api;

/**
 * 核心对扩展开放的服务集合。
 *
 * <p>只暴露<b>其它模块确实需要</b>的能力，不做大而全的门面。扩展若发现自己需要
 * 这里的服务直接操作某张核心表，通常说明该功能不该由扩展实现，或者需要新增一个
 * 明确的服务方法——两种情况都应该先提出来，而不是绕过去。
 */
public interface Services {

    BanService bans();

    MuteService mutes();

    WarnService warnings();
}
