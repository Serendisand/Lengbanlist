Lengbanlist Lua 扩展目录
=======================

一个子目录 = 一个扩展：

  extensions/<扩展名>/extension.lua   入口脚本（必需，脚本名固定）
  extensions/<扩展名>/config.yml      扩展自己的配置，enabled 就是开关（可省略，默认开）

删掉目录 = 卸载扩展。目录存在但缺 extension.lua 的会被忽略。

管理命令：
  /lban ext list              看装了哪些、开着没有
  /lban ext reload            改完脚本重跑一遍
  /lban ext disable <扩展名>   关掉（写的是这个目录里的 config.yml）
  /lban ext enable  <扩展名>   打开

注意：脚本跑在主线程上，超时（单次调用超过 5 秒或指令预算耗尽）会被强制停用并写进
服务端日志。写循环记得留出口。

脚本 API 说明见插件文档 docs/LuaExtensions.md。
官方扩展的源码在 https://github.com/Serendisand/Lengbanlist-Extensions 。
