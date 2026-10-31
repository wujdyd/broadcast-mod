package com.broadcast.registry;

import com.broadcast.BroadcastMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BroadcastMod.MODID);

    public static final RegistryObject<CreativeModeTab> BROADCAST_TAB = TABS.register("broadcast",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.broadcast"))
                    .icon(() -> new ItemStack(ModBlocks.STATION.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModBlocks.STATION.get());
                        output.accept(ModBlocks.WALL_SPEAKER.get());
                        output.accept(ModBlocks.CEILING_SPEAKER.get());
                        output.accept(ModBlocks.FLOOR_SPEAKER.get());
                        output.accept(ModBlocks.COLUMN_SPEAKER.get());
                        output.accept(ModBlocks.MICROPHONE.get());
                        output.accept(ModBlocks.INDICATOR.get());
                        output.accept(ModItems.DEBUGGER.get());
                        output.accept(ModItems.PORTABLE_MICROPHONE.get());
                    })
                    .build());
}
