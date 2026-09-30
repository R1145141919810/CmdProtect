package com.cmdprotect;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模组配置(common 类型,文件位于 config/cmdprotect-common.toml)。
 * 所有选项服务端即可生效,修改后重启服务器 / 游戏生效。
 *
 * <p>类名使用 CmdProtectConfig 而不是 ModConfig,
 * 是为了避免与 net.minecraftforge.fml.config.ModConfig 同名冲突。
 */
public final class CmdProtectConfig {

    public static final ForgeConfigSpec SPEC;

    // 全局
    public static final ForgeConfigSpec.BooleanValue OP_BYPASS;

    // /kill 保护
    public static final ForgeConfigSpec.BooleanValue ENABLE_KILL_PROTECT;
    public static final ForgeConfigSpec.ConfigValue<String> KILL_KICK_MESSAGE;

    // /tp /teleport 保护
    public static final ForgeConfigSpec.BooleanValue ENABLE_TP_PROTECT;
    public static final ForgeConfigSpec.BooleanValue ALLOW_COORDINATE_TP;
    public static final ForgeConfigSpec.ConfigValue<String> TP_KICK_MESSAGE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("general");
        OP_BYPASS = builder
                .comment("Whether OPs bypass the protection.",
                        "true = players in the server OP list are unrestricted (original cmdProtect.sk behavior)",
                        "false = the /kill and /tp protections also apply to OPs")
                .define("bypass_op", true);
        builder.pop();

        builder.push("kill_protect");
        ENABLE_KILL_PROTECT = builder
                .comment("Block non-OP players from using /kill on other targets")
                .define("enabled", true);
        KILL_KICK_MESSAGE = builder
                .comment("Kick message when a player violates /kill protection")
                .define("kick_message", "§c禁止！不能对其他玩家执行kill指令");
        builder.pop();

        builder.push("tp_protect");
        ENABLE_TP_PROTECT = builder
                .comment("Block non-OP players from using /tp or /teleport on other players")
                .define("enabled", true);
        ALLOW_COORDINATE_TP = builder
                .comment("Allow /tp to pure coordinates (e.g. /tp 100 64 100). The original Skript would have blocked these.")
                .define("allow_coordinate_teleport", true);
        TP_KICK_MESSAGE = builder
                .comment("Kick message when a player violates /tp protection")
                .define("kick_message", "§c禁止！不能传送其他玩家");
        builder.pop();

        SPEC = builder.build();
    }

    private CmdProtectConfig() {
    }
}
