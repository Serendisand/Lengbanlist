package org.leng.fabric;

public final class FabricServerLifecycleBridge {
    private FabricServerLifecycleBridge() {
    }

    public static void register(FabricLengbanlist plugin) {
        registerStarted(plugin);
        registerStopping(plugin);
    }

    private static void registerStarted(FabricLengbanlist plugin) {
        try {
            Class<?> events = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents");
            Object event = events.getField("SERVER_STARTED").get(null);
            Class<?> listener = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStarted");
            register(event, listener, (proxy, method, args) -> {
                if ("onServerStarted".equals(method.getName())) {
                    plugin.onServerStarted(args[0]);
                }
                return null;
            });
        } catch (Throwable e) {
            plugin.getLogger().warning("Fabric服务器启动事件注册失败: " + e.getMessage());
        }
    }

    private static void registerStopping(FabricLengbanlist plugin) {
        try {
            Class<?> events = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents");
            Object event = events.getField("SERVER_STOPPING").get(null);
            Class<?> listener = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStopping");
            register(event, listener, (proxy, method, args) -> {
                if ("onServerStopping".equals(method.getName())) {
                    plugin.onServerStopping();
                }
                return null;
            });
        } catch (Throwable e) {
            plugin.getLogger().warning("Fabric服务器停止事件注册失败: " + e.getMessage());
        }
    }

    private static void register(Object event, Class<?> listener, java.lang.reflect.InvocationHandler handler) throws Exception {
        Object callback = java.lang.reflect.Proxy.newProxyInstance(
                FabricServerLifecycleBridge.class.getClassLoader(),
                new Class[]{listener},
                handler
        );
        ReflectionSupport.registerCallback(event, callback);
    }
}
