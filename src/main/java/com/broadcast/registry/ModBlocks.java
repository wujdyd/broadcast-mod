package com.broadcast.registry;

import com.broadcast.BroadcastMod;
import com.broadcast.block.BroadcastStationBlock;
import com.broadcast.block.IndicatorLightBlock;
import com.broadcast.block.MicrophoneBlock;
import com.broadcast.block.SpeakerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, BroadcastMod.MODID);

    public static final RegistryObject<BroadcastStationBlock> STATION =
            BLOCKS.register("station", () -> new BroadcastStationBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5F, 6.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    public static final RegistryObject<SpeakerBlock> WALL_SPEAKER =
            BLOCKS.register("wall_speaker", () -> new SpeakerBlock(SpeakerBlock.Type.WALL,
                    baseSpeakerProps()));

    public static final RegistryObject<SpeakerBlock> CEILING_SPEAKER =
            BLOCKS.register("ceiling_speaker", () -> new SpeakerBlock(SpeakerBlock.Type.CEILING,
                    baseSpeakerProps()));

    public static final RegistryObject<SpeakerBlock> FLOOR_SPEAKER =
            BLOCKS.register("floor_speaker", () -> new SpeakerBlock(SpeakerBlock.Type.FLOOR,
                    baseSpeakerProps()));

    public static final RegistryObject<SpeakerBlock> COLUMN_SPEAKER =
            BLOCKS.register("column_speaker", () -> new SpeakerBlock(SpeakerBlock.Type.PILLAR,
                    baseSpeakerProps()));

    public static final RegistryObject<MicrophoneBlock> MICROPHONE =
            BLOCKS.register("microphone", () -> new MicrophoneBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0F, 4.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()));

    public static final RegistryObject<IndicatorLightBlock> INDICATOR =
            BLOCKS.register("indicator_light", () -> new IndicatorLightBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .strength(0.5F, 1.0F)
                            .sound(SoundType.GLASS)
                            .lightLevel(state -> state.getValue(IndicatorLightBlock.LIT) ? 15 : 0)
                            .noOcclusion()));

    private static BlockBehaviour.Properties baseSpeakerProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(2.0F, 4.0F)
                .sound(SoundType.METAL)
                .noOcclusion();
    }
}
