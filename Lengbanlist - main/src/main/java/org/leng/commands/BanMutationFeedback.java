package org.leng.commands;

import org.bukkit.command.CommandSender;
import org.leng.manager.BanManager;
import org.leng.utils.Utils;

final class BanMutationFeedback {
    private BanMutationFeedback() {
    }

    static void sendFailure(CommandSender sender, BanManager.BanMutationResult result, String target, boolean ipTarget) {
        String label = ipTarget ? "IP " : "玩家 ";
        switch (result) {
            case NOT_ACTIVE:
                Utils.sendMessage(sender, "§c" + label + target + " 未被封禁或状态已变化，操作未完成。");
                break;
            case STATE_CHANGED:
                Utils.sendMessage(sender, "§c数据状态已变化，请刷新后重试。");
                break;
            case REJECTED_PRIVATE_OR_RESERVED_IP:
                Utils.sendMessage(sender, "§c操作被安全策略拒绝：不能封禁私有或保留 IP。");
                break;
            case DATABASE_ERROR:
                Utils.sendMessage(sender, "§c数据库故障，操作未完成。");
                break;
            default:
                break;
        }
    }
}
