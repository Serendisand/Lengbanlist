package org.leng.fabric;

public final class FabricChatBridge {
    private FabricChatBridge() {
    }

    public static void register(FabricLengbanlist plugin) {
        try {
            Class<?> events = Class.forName("net.fabricmc.fabric.api.message.v1.ServerMessageEvents");
            Object event = events.getField("ALLOW_CHAT_MESSAGE").get(null);
            Class<?> listener = Class.forName("net.fabricmc.fabric.api.message.v1.ServerMessageEvents$AllowChatMessage");
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    FabricChatBridge.class.getClassLoader(),
                    new Class[]{listener},
                    (proxy, method, args) -> {
                        if ("allowChatMessage".equals(method.getName())) {
                            Object message = args[0];
                            Object player = args[1];
                            return !plugin.handleChat(player, ReflectionSupport.chatMessageContent(message));
                        }
                        return true;
                    }
            );
            ReflectionSupport.registerCallback(event, callback);
        } catch (Throwable e) {
            plugin.getLogger().warning("Fabric聊天事件注册失败: " + e.getMessage());
        }
    }
}
