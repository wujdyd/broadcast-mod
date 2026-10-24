package com.broadcast;

import com.broadcast.config.BroadcastConfig;
import com.broadcast.network.NetworkHandler;
import com.broadcast.registry.ModBlockEntities;
import com.broadcast.registry.ModBlocks;
import com.broadcast.registry.ModCreativeTab;
import com.broadcast.registry.ModItems;
import com.broadcast.registry.ModMenuTypes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(BroadcastMod.MODID)
public class BroadcastMod {
    public static final String MODID = "broadcast";
    public static final Logger LOGGER = LogManager.getLogger("Broadcast");

    public BroadcastMod() {
        BroadcastConfig.register();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenuTypes.MENUS.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        NetworkHandler.register();
        MinecraftForge.EVENT_BUS.register(com.broadcast.event.CommonEvents.class);
        MinecraftForge.EVENT_BUS.addListener(com.broadcast.command.BroadcastCommands::register);
        LOGGER.info("广播站模组初始化完成");
    }
}
