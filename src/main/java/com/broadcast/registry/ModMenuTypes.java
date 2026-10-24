package com.broadcast.registry;

import com.broadcast.BroadcastMod;
import com.broadcast.menu.BroadcastStationMenu;
import com.broadcast.menu.MicrophoneMenu;
import com.broadcast.menu.SpeakerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, BroadcastMod.MODID);

    public static final RegistryObject<MenuType<BroadcastStationMenu>> STATION =
            MENUS.register("station", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> new BroadcastStationMenu(windowId, inv, data)));

    public static final RegistryObject<MenuType<SpeakerMenu>> SPEAKER =
            MENUS.register("speaker", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> new SpeakerMenu(windowId, inv, data)));

    public static final RegistryObject<MenuType<MicrophoneMenu>> MICROPHONE =
            MENUS.register("microphone", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> new MicrophoneMenu(windowId, inv, data)));
}
