package org.leng.utils;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.leng.Lengbanlist;
import org.leng.manager.SyncManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class SyncChannel implements PluginMessageListener {
    private final Lengbanlist plugin;
    private static final String CHANNEL = "lengbanlist:sync";

    public static final byte TYPE_PLAYER_BAN = 0;
    public static final byte TYPE_IP_BAN = 1;
    public static final byte TYPE_PLAYER_MUTE = 2;
    public static final byte TYPE_IP_MUTE = 3;
    public static final byte TYPE_PLAYER_UNBAN = 4;
    public static final byte TYPE_IP_UNBAN = 5;
    public static final byte TYPE_PLAYER_UNMUTE = 6;

    public SyncChannel(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public void sendSyncNotification(byte type, String target) {
        if (target == null || target.isEmpty()) return;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeByte(type);
            dos.writeUTF(target);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to encode sync message: " + e.getMessage());
        }

        byte[] message = baos.toByteArray();
        java.util.Collection<? extends Player> online = plugin.getServer().getOnlinePlayers();
        if (online.isEmpty()) {
            plugin.getLogger().warning("跨服同步消息发送失败：当前服务器无在线玩家，消息已被丢弃 (type=" + type + ", target=" + target + ")");
            return;
        }
        for (Player player : online) {
            player.sendPluginMessage(plugin, CHANNEL, message);
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) return;

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(message);
            DataInputStream dis = new DataInputStream(bais);
            byte type = dis.readByte();
            String target = dis.readUTF();

            if (target != null && !target.isEmpty()) {
                plugin.getSyncManager().handleSync(type, target);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to parse sync message: " + e.getMessage());
        }
    }
}
