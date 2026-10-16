Lengbanlist Lua 扩展目录
=======================

一个 .lua 文件 = 一个扩展。脚本平铺在这一层，配置各回各家的子目录：

  extensions/<扩展名>.lua          扩展脚本（文件名就是扩展名，也是它的功能键）
  extensions/<扩展名>/config.yml   扩展自己的配置，enabled 就是开关（缺省 true）

删掉 .lua 文件 = 卸载扩展。剩下的配置目录不会再被加载，也不会报错；
重新放回同名脚本，原来的配置就接着用。

管理命令：
  /lban ext list              看装了哪些、开着没有
  /lban ext reload            改完脚本重跑一遍
  /lban ext disable <扩展名>   关掉（写的是 <扩展名>/config.yml 里的 enabled）
  /lban ext enable  <扩展名>   打开

缺配置目录的扩展，加载时会自动生成一份只含 enabled 的 config.yml，
扩展自己要用的键由脚本作者往里补。

注意：脚本跑在主线程上，超时（单次调用超过 5 秒或指令预算耗尽）会被强制停用并写进
服务端日志。写循环记得留出口。

脚本 API 说明见插件文档 docs/LuaExtensions.md。
官方扩展的源码在 https://github.com/Serendisand/Lengbanlist-Extensions 。
