package org.leng.api;

import java.util.List;

/**
 * 命令注册器。由核心提供，扩展只需调用 {@link #register(CommandSpec)}。
 *
 * <p>注册是<b>幂等</b>的：同一个扩展重复注册同名命令会覆盖而不是产生两条。
 * 扩展停用时核心会自动注销它注册过的全部命令。
 */
public interface CommandRegistrar {

    void register(CommandSpec spec);

    default void register(CommandSpec... specs) {
        if (specs == null) {
            return;
        }
        for (CommandSpec spec : specs) {
            register(spec);
        }
    }

    /** 本扩展当前已注册的命令名快照。 */
    List<String> registeredNames();
}
