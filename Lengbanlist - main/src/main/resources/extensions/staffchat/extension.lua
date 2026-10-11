-- /sc <内容> —— 管理员频道
--
-- 官方 Lua 扩展，随核心 jar 一起发布。
--
-- 目录名就是扩展名，也是它的功能键（这里是 staffchat）。命令名与目录名可以不同：
-- 目录叫 staffchat、命令叫 sc，权限节点沿用老的 lengbanlist.staffchat ——
-- 老用户在 LuckPerms 里配好的权限一个字都不用改。

leng.name("管理员频道")
leng.version("1.0.0")
leng.api("1.0")

local PERMISSION = "lengbanlist.staffchat"

leng.command{
    name = "sc",
    permission = PERMISSION,
    usage = "/sc <内容>",
    description = "工作频道聊天",

    run = function(ctx)
        local message = table.concat(ctx.args, " ")
        if message == "" then
            ctx.reply("§c用法喵: /sc <内容>")
            return true
        end
        local senderName = ctx.sender.is_player and ctx.sender.name or "§cConsole"
        local line = "§b<STAFF> §e" .. senderName .. "§f: " .. message
        for _, player in ipairs(leng.players()) do
            if player:has_permission(PERMISSION) then
                player:send(line)
            end
        end
        leng.console(line)     -- 控制台也要留一份记录
        return true
    end,
}
