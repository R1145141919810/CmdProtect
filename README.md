# CmdProtect (Forge 1.20.1)

把 **cmdProtect.sk**(Skript 脚本)移植并改造而成的 Forge 模组,适用于 **Minecraft 1.20.1 + Forge**。

## 原脚本做了什么

| Skript 行为 | 模组实现 |
| --- | --- |
| 普通玩家使用违规 /kill、/tp 指令:直接拦截并踢人 | 监听 Forge CommandEvent,拦截并踢出 |
| OP 无限制(if {_p} is op: stop) | player.getPermissionLevel() >= 2 直接放行 |
| /kill <目标>,目标不是 @s 也不是自己 → 取消并踢出 | handleKill:目标非自己 → setCanceled(true) + connection.disconnect(踢出消息) |
| /tp、/teleport 同样处理 | handleTeleport:目标非自己 → 取消并踢出 |
| 踢出消息:§c禁止！不能对其他玩家执行kill指令 | 配置项 kill_protect.kick_message(默认值相同) |
| 踢出消息:§c禁止！不能传送其他玩家 | 配置项 tp_protect.kick_message(默认值相同) |

## 相对原脚本的改造点

1. **纯坐标传送放行**:原脚本会把 /tp 100 64 100、/tp ~ ~ ~ 这种正常用法也踢掉(第二参数 100 既不是 @s 也不是自己名字)。模组默认放行数字、~、^ 开头的目标(配置项 tp_protect.allow_coordinate_teleport)。
2. **带命名空间写法兼容**:/minecraft:kill、/minecraft:tp 一样会被拦截。
3. **多重空格兼容**:原脚本 split by " ",连续空格会得到空目标导致误判;模组用 split(" +")。
4. **只处罚“直接执行者”**:/execute as <别人> run kill ... 这类间接执行不误踢被借用的玩家(原脚本此场景下 {_p} 语义模糊)。
5. **控制台 / 命令方块不限制**:与原脚本中 {_p} 为 <none> 时拦截条件无法命中的效果一致,这里明确放行。
6. **全部行为可配置**,配置文件:游戏目录 config/cmdprotect-common.toml。

### 配置项(config/cmdprotect-common.toml)

注意:编辑配置文件时请保存为 **UTF-8(无 BOM)** 编码,否则带 BOM 的文件会导致 Forge 读取配置失败(VSCode 右下角可选编码,记事本默认即为无 BOM 的 UTF-8)。

    [general]
        # Whether OPs bypass the protection.
        # true = players in the server OP list are unrestricted (original cmdProtect.sk behavior)
        # false = the /kill and /tp protections also apply to OPs
        bypass_op = true

    [kill_protect]
        # Block non-OP players from using /kill on other targets
        enabled = true
        # Kick message when a player violates /kill protection
        kick_message = "§c禁止！不能对其他玩家执行kill指令"

    [tp_protect]
        # Block non-OP players from using /tp or /teleport on other players
        enabled = true
        # Allow /tp to pure coordinates (e.g. /tp 100 64 100). The original Skript would have blocked these.
        allow_coordinate_teleport = true
        # Kick message when a player violates /tp protection
        kick_message = "§c禁止！不能传送其他玩家"

## 分支说明

- **main**:与原 cmdProtect.sk 行为一致 —— OP 一律放行(硬编码,不可配置)。
- **feature/no-op-bypass**(本分支):新增全局配置 **general.bypass_op**:
  - bypass_op = true(默认):OP 放行,行为与 main 一致;
  - bypass_op = false:OP 同样受保护 —— OP 对他人执行 /kill、/tp(/teleport) 会被拦截并踢出,同时堵住 OP 用 /execute as 他人 run ... 间接绕过的方式(间接执行时处罚原始命令发起人);
  - 无论怎么设置,控制台(非玩家来源)永远不受限制,管理员仍可从控制台执行任何指令。

## 构建

要求:**JDK 17** 及以上版本。

    gradlew build          (Windows 下运行 gradlew.bat)

产物在 build/libs/cmdprotect-1.1.0.jar,直接丢进服务端(或客户端)mods 文件夹即可。
本机没有 JDK/Gradle 时,首次构建会自动下载(需要网络)。

## 已知限制

- 拦截基于命令文本的**第二参数**,与原 Skript 一致。因此:
  - 名字恰好是纯数字的玩家会被当作坐标放行(极罕见);
  - /tp @p <坐标> 会被拦截(第二参数 @p 非自己),与原脚本一致;
- 普通玩家用不了 /execute(需权限等级 2);bypass_op = true 时 OP 不受限制,不存在绕过问题;
  bypass_op = false 时,OP 通过 /execute as 的间接执行同样会被拦截(处罚原始命令发起人)。

## 项目结构

    src/main/java/com/cmdprotect/
    ├── CmdProtect.java              # @Mod 入口:注册配置与事件监听
    ├── ModConfig.java               # ForgeConfigSpec 配置定义
    └── CommandProtectHandler.java   # CommandEvent 拦截核心逻辑
    src/main/resources/
    ├── META-INF/mods.toml           # 模组元数据
    └── pack.mcmeta
