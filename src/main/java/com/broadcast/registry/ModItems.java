package com.broadcast.registry;

import com.broadcast.BroadcastMod;
import com.broadcast.item.DebuggerItem;
import com.broadcast.item.PortableMicrophoneItem;
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
    public static final RegistryObject<BlockItem> COLUMN_SPEAKER =
            ITEMS.register("column_speaker", () -> new BlockItem(ModBlocks.COLUMN_SPEAKER.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> MICROPHONE =
            ITEMS.register("microphone", () -> new BlockItem(ModBlocks.MICROPHONE.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> INDICATOR_LIGHT =
            ITEMS.register("indicator_light", () -> new BlockItem(ModBlocks.INDICATOR.get(), new Item.Properties()));

    /** 调试器:450 点耐久,合成获取。 */
    public static final RegistryObject<DebuggerItem> DEBUGGER =
            ITEMS.register("debugger", () -> new DebuggerItem(new Item.Properties()
                    .durability(DebuggerItem.MAX_DURABILITY)
                    .rarity(Rarity.EPIC)));

    public static final RegistryObject<PortableMicrophoneItem> PORTABLE_MICROPHONE =
            ITEMS.register("portable_microphone", () -> new PortableMicrophoneItem(new Item.Properties()
                    .stacksTo(1)));
}
