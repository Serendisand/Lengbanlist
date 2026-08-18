package org.leng.manager;

import org.leng.platform.MessageSink;

public final class BanMutationFeedback {

    private BanMutationFeedback() {
    }

    public static void sendFailure(MessageSink sender, BanManager.BanMutationResult result,
                                   String target, boolean ipTarget) {
        String label = ipTarget ? "IP " : "玩家 ";
        switch (result) {
            case NOT_ACTIVE:
                sender.sendMessage("§c" + label + target + " 未被封禁或封禁已过期，操作未完成。");
                break;
            case STATE_CHANGED:
                sender.sendMessage("§c数据状态已变化，请刷新后重试。");
                break;
            case REJECTED_PRIVATE_OR_RESERVED_IP:
                sender.sendMessage("§c操作被安全策略拒绝：不能封禁私有或保留 IP。");
                break;
            case DATABASE_ERROR:
                sender.sendMessage("§c数据库故障，操作未完成。");
                break;
            default:
                break;
        }
    }

    public static void sendFailure(org.bukkit.command.CommandSender sender,
                                   BanManager.BanMutationResult result,
                                   String target, boolean ipTarget) {
        sendFailure(message -> org.leng.utils.Utils.sendMessage(sender, message),
                result, target, ipTarget);
    }

}
