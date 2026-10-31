package com.broadcast.media;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod("broadcast_media_support")
public final class MediaSupportMod {
    public MediaSupportMod() {
        MediaSupportConfig.register();
        MinecraftForge.EVENT_BUS.addListener(MediaSupportCommand::register);
    }
}
