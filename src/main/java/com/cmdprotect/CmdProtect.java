package com.cmdprotect;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * CmdProtect — cmdProtect.sk 的 Forge 1.20.1 移植版。
 *
 * <p>原 Skript 逻辑:
 * <ul>
 *   <li>OP 放行,不拦截;</li>
 *   <li>/kill 的目标不是 @s 或自己时:取消命令并踢出玩家;</li>
 *   <li>/tp、/teleport 的目标不是 @s 或自己时:取消命令并踢出玩家。</li>
 * </ul>
 */
@Mod(CmdProtect.MODID)
public final class CmdProtect {

    public static final String MODID = "cmdprotect";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CmdProtect() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CmdProtectConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(CommandProtectHandler.class);
        LOGGER.info("CmdProtect loaded. /kill and /tp(/teleport) are now protected for non-OP players.");
    }
}
