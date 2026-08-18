package org.leng.fabric;

public final class FabricJoinBridge {
    private FabricJoinBridge() {
    }

    public static void register(FabricLengbanlist plugin) {
        try {
            Class<?> eventClass = Class.forName("net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents");
            Object joinEvent = eventClass.getField("JOIN").get(null);
            Class<?> joinInterface = Class.forName("net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents$Join");
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    FabricJoinBridge.class.getClassLoader(),
                    new Class[]{joinInterface},
                    (proxy, method, args) -> {
                        if ("onPlayReady".equals(method.getName()) || "onPlayReady".equalsIgnoreCase(method.getName())) {
                            Object handler = args[0];
                            Object server = args[2];
                            Object player = ReflectionSupport.playerFromHandler(handler);
                            if (player == null) {
                                plugin.getLogger().warning("Fabric进服事件无法获取玩家对象");
                                return null;
                            }
                            plugin.setServer(server);
                            plugin.handleJoin(player, server);
                        }
                        return null;
                    });
            ReflectionSupport.registerCallback(joinEvent, callback);
        } catch (Throwable e) {
            plugin.getLogger().warning("Fabric进服事件注册失败: " + e.getMessage());
        }
    }
}
