package com.broadcast.registry;

import com.broadcast.BroadcastMod;
import com.broadcast.blockentity.BroadcastStationBlockEntity;
import com.broadcast.blockentity.IndicatorLightBlockEntity;
import com.broadcast.blockentity.MicrophoneBlockEntity;
import com.broadcast.blockentity.SpeakerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, BroadcastMod.MODID);

    public static final RegistryObject<BlockEntityType<BroadcastStationBlockEntity>> STATION =
            BLOCK_ENTITIES.register("station", () -> BlockEntityType.Builder
                    .of(BroadcastStationBlockEntity::new, ModBlocks.STATION.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<SpeakerBlockEntity>> SPEAKER =
            BLOCK_ENTITIES.register("speaker", () -> BlockEntityType.Builder
                    .of(SpeakerBlockEntity::new,
                            ModBlocks.WALL_SPEAKER.get(),
                            ModBlocks.CEILING_SPEAKER.get(),
                            ModBlocks.FLOOR_SPEAKER.get(),
                            ModBlocks.COLUMN_SPEAKER.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<MicrophoneBlockEntity>> MICROPHONE =
            BLOCK_ENTITIES.register("microphone", () -> BlockEntityType.Builder
                    .of(MicrophoneBlockEntity::new, ModBlocks.MICROPHONE.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<IndicatorLightBlockEntity>> INDICATOR =
            BLOCK_ENTITIES.register("indicator_light", () -> BlockEntityType.Builder
                    .of(IndicatorLightBlockEntity::new, ModBlocks.INDICATOR.get())
                    .build(null));
}
