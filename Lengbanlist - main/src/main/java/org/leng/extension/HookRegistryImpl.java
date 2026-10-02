package org.leng.extension;

import org.leng.api.HookRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单个扩展持有的钩子集合。
 *
 * <p>按扩展隔离是刻意的：核心按"功能键 → 归属扩展 → 该扩展的钩子"三级查找，
 * 因此扩展停用时只需丢掉它自己这一份，核心自然回落到默认行为，
 * 不存在"钩子还在但实现已经失效"的中间态。
 */
final class HookRegistryImpl implements HookRegistry {

    private final Map<Class<?>, Object> hooks = new ConcurrentHashMap<>();

    @Override
    public <T> void register(Class<T> type, T hook) {
        if (type == null || hook == null) {
            return;
        }
        if (!type.isInstance(hook)) {
            throw new IllegalArgumentException("钩子实现 " + hook.getClass().getName()
                    + " 不是 " + type.getName() + " 的实例");
        }
        hooks.put(type, hook);
    }

    @Override
    public <T> void unregister(Class<T> type) {
        if (type != null) {
            hooks.remove(type);
        }
    }

    <T> T get(Class<T> type) {
        return type == null ? null : type.cast(hooks.get(type));
    }

    void clear() {
        hooks.clear();
    }
}
