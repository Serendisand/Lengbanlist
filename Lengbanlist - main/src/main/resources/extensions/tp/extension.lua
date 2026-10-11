-- /lban tp <玩家名> —— 传送到玩家身边
--
-- 官方 Lua 扩展，随核心 jar 一起发布。想改就在这个目录里直接改，
-- 或者把目录整个复制一份换个名字，改那份（复制的那份是新扩展，与原版并存）。
--
-- 这是搬出核心的第二个功能：「命令搬走了，命令界面不变」——parent 让它可以
-- 继续挂在 /lban 底下，权限、补全、/lban help 过滤都走与内置命令同一条路径。

leng.name("传送")
leng.version("1.0.0")
leng.api("1.0")

leng.command{
    name = "tp",
    parent = "lban",                      -- 省略就是顶层命令；写了 lban 就是 /lban tp
    permission = "lengbanlist.admin",
    usage = "/lban tp <玩家名>",
    description = "传送到玩家身边",

    run = function(ctx)
        local targetName = ctx.args[1]
        if not targetName then
            ctx.reply("§c用法喵: /lban tp <玩家名>")
            return true
        end
        -- 目标用的是 sender 的快照：拿不到就说明是控制台（控制台没法"被传送"）
        local self = ctx.sender.player
        local target = leng.player(targetName)
        if target and self then
            self:teleport_to(target)
            ctx.reply("§a已传送到玩家 " .. target.name)
        else
            ctx.reply("§c玩家不在线")
        end
        return true
    end,

    complete = function(ctx)
        -- 补全给的是当前这一段的半截名字，按前缀筛在线玩家
        return leng.online_players(ctx.args[#ctx.args] or "")
    end,
}
