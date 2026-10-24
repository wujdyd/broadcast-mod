package com.broadcast.registry;

import com.broadcast.BroadcastMod;
import com.broadcast.item.DebuggerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BroadcastMod.MODID);

    public static final RegistryObject<BlockItem> STATION =
            ITEMS.register("station", () -> new BlockItem(ModBlocks.STATION.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> WALL_SPEAKER =
            ITEMS.register("wall_speaker", () -> new BlockItem(ModBlocks.WALL_SPEAKER.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> CEILING_SPEAKER =
            ITEMS.register("ceiling_speaker", () -> new BlockItem(ModBlocks.CEILING_SPEAKER.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> FLOOR_SPEAKER =
            ITEMS.register("floor_speaker", () -> new BlockItem(ModBlocks.FLOOR_SPEAKER.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> MICROPHONE =
            ITEMS.register("microphone", () -> new BlockItem(ModBlocks.MICROPHONE.get(), new Item.Properties()));

    /** 调试器:不放进创造栏、无配方,仅 /? give TSQ 获取。 */
    public static final RegistryObject<DebuggerItem> DEBUGGER =
            ITEMS.register("debugger", () -> new DebuggerItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));
}
