package com.cmdprotect;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;

/**
 * 核心拦截逻辑,监听 Forge 的 {@link CommandEvent}(命令解析完成、执行之前触发)。
 *
 * <p>与原 Skript 的对应关系:
 * <pre>
 * on command:                                  ->  @SubscribeEvent onCommand(CommandEvent)
 * set {_p} to player                           ->  event.getParseResults().getContext().getSource().getEntity()
 * if {_p} is op: stop                          ->  OP 放行开关 general.bypass_op(默认 true 时 OP 直接返回)
 * if {_cmd} starts with "/kill"                ->  command 名 == "kill"(兼容 minecraft:kill 前缀)
 * if {_cmd} starts with "/tp" or "/teleport"   ->  command 名 == "tp" || "teleport"
 * set {_args} to split {_cmd} by " "           ->  input.split(" +")
 * set {_target} to {_args}[2]                  ->  parts[1](去掉前导 "/")
 * cancel event                                 ->  event.setCanceled(true)
 * kick {_p} due to "..."                       ->  player.connection.disconnect(Component.literal(...))
 * </pre>
 *
 * <p>本分支新增(相对于 main):
 * <ul>
 *   <li>general.bypass_op = false 时,服务器 OP 名单中的玩家同样受保护;</li>
 *   <li>间接执行(/execute as 他人)时处罚原始命令发起人,堵住 OP 的 execute 绕过;</li>
 *   <li>间接执行且目标为 @s(即让“别人”对自己动手)时,同样视为违规。</li>
 * </ul>
 */
public final class CommandProtectHandler {

    private CommandProtectHandler() {
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        Entity executor = source.getEntity();

        // 非玩家来源(控制台 / 命令方块等)不限制。
        // 原 Skript 中 {_p} 此时为 <none>,拦截逻辑同样不会命中玩家,这里明确放行。
        if (!(executor instanceof ServerPlayer)) {
            return;
        }

        // 确定“被检查/被处罚的玩家”:
        // 直接执行 -> 执行者本人;间接执行(/execute as 别人) -> 原始命令发起人,
        // 避免误踢被借用的无辜玩家,同时让发起人无法通过 execute 绕过。
        boolean indirect = executor != source.source;
        ServerPlayer subject;
        if (!indirect) {
            subject = (ServerPlayer) executor;
        } else if (source.source instanceof ServerPlayer root) {
            subject = root;
        } else {
            return; // 根来源不是玩家(如命令方块),不限制
        }

        // OP 放行开关(对应 "if {_p} is op: stop"):
        // bypass_op = true  -> 服务器 OP 名单中的玩家不拦截(与原脚本一致)
        // bypass_op = false -> OP 同样受保护,不能绕过 /kill 与 /tp 限制
        if (CmdProtectConfig.OP_BYPASS.get()
                && subject.getServer().getPlayerList().isOp(subject.getGameProfile())) {
            return;
        }

        String input = event.getParseResults().getReader().getString().trim();
        if (input.startsWith("/")) {
            input = input.substring(1);
        }
        String[] parts = input.split(" +");
        if (parts.length == 0) {
            return;
        }

        String command = parts[0].toLowerCase(Locale.ROOT);
        int colon = command.indexOf(':');
        if (colon >= 0) {
            command = command.substring(colon + 1); // 兼容 minecraft:kill 这类带命名空间的写法
        }

        switch (command) {
            case "kill" -> handleKill(event, subject, parts, indirect);
            case "tp", "teleport" -> handleTeleport(event, subject, parts, indirect);
            default -> {
                // 其他命令不处理
            }
        }
    }

    // ========== /kill ==========
    // 目标不是 @s 且不是自己的名字 -> 取消并踢出
    private static void handleKill(CommandEvent event, ServerPlayer player, String[] parts, boolean indirect) {
        if (!CmdProtectConfig.ENABLE_KILL_PROTECT.get() || parts.length < 2) {
            return;
        }
        String target = normalize(parts[1]);
        // 间接执行(/execute as 别人 run kill @s)时,@s 指的是“别人”自己,
        // 等于让发起人隔空弄死另一个玩家,同样视为违规
        if (indirect && "@s".equals(target)) {
            cancelAndKick(event, player, CmdProtectConfig.KILL_KICK_MESSAGE.get());
            return;
        }
        if (isSelf(player, target)) {
            return;
        }
        cancelAndKick(event, player, CmdProtectConfig.KILL_KICK_MESSAGE.get());
    }

    // ========== /tp /teleport ==========
    // 目标不是 @s 且不是自己的名字 -> 取消并踢出;
    // 改进:纯坐标传送(如 /tp 100 64 100)放行,原 Skript 会误伤这种正常用法
    private static void handleTeleport(CommandEvent event, ServerPlayer player, String[] parts, boolean indirect) {
        if (!CmdProtectConfig.ENABLE_TP_PROTECT.get() || parts.length < 2) {
            return;
        }
        String target = normalize(parts[1]);
        // 同 /kill:间接执行且目标为 @s 时,是让别人对自己传送,视为违规
        if (indirect && "@s".equals(target)) {
            cancelAndKick(event, player, CmdProtectConfig.TP_KICK_MESSAGE.get());
            return;
        }
        if (isSelf(player, target)) {
            return;
        }
        if (CmdProtectConfig.ALLOW_COORDINATE_TP.get() && looksLikeCoordinate(target)) {
            return;
        }
        cancelAndKick(event, player, CmdProtectConfig.TP_KICK_MESSAGE.get());
    }

    private static boolean isSelf(ServerPlayer player, String target) {
        if ("@s".equals(target)) {
            return true;
        }
        return target.equals(player.getGameProfile().getName().toLowerCase(Locale.ROOT));
    }

    /** 去掉首尾引号并转小写,兼容 /kill "Player Name" 这类写法 */
    private static String normalize(String raw) {
        String s = raw.trim();
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1);
        }
        return s.toLowerCase(Locale.ROOT);
    }

    /** 判断是否为纯坐标/相对坐标/局部坐标参数(数字、~、^ 开头) */
    private static boolean looksLikeCoordinate(String target) {
        if (target.startsWith("~") || target.startsWith("^")) {
            return true;
        }
        try {
            Double.parseDouble(target);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void cancelAndKick(CommandEvent event, ServerPlayer player, String message) {
        event.setCanceled(true);                                  // Skript: cancel event
        player.connection.disconnect(Component.literal(message)); // Skript: kick {_p} due to "..."
    }
}
