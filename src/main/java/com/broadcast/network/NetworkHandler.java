package com.broadcast.network;

import com.broadcast.BroadcastMod;
import com.broadcast.network.packet.DeleteMusicPacket;
import com.broadcast.network.packet.DeviceInfoPacket;
import com.broadcast.network.packet.MicAudioPacket;
import com.broadcast.network.packet.MicCaptureControlPacket;
import com.broadcast.network.packet.MicStatePacket;
import com.broadcast.network.packet.MicUploadPacket;
import com.broadcast.network.packet.MusicChunkPacket;
import com.broadcast.network.packet.MusicListPacket;
import com.broadcast.network.packet.PlayRequestPacket;
import com.broadcast.network.packet.PlaybackStatePacket;
import com.broadcast.network.packet.PortableMicControlPacket;
import com.broadcast.network.packet.PortableMicUploadPacket;
import com.broadcast.network.packet.RequestDataPacket;
import com.broadcast.network.packet.RequestDeviceInfoPacket;
import com.broadcast.network.packet.RequestFilePacket;
import com.broadcast.network.packet.SetMicSettingsPacket;
import com.broadcast.network.packet.SetPlaylistPacket;
import com.broadcast.network.packet.SetPlaylistSettingsPacket;
import com.broadcast.network.packet.SetPortableMicSettingsPacket;
import com.broadcast.network.packet.SetSettingsPacket;
import com.broadcast.network.packet.SetSpeakerFrequencyPacket;
import com.broadcast.network.packet.SetStationFrequenciesPacket;
import com.broadcast.network.packet.StationInfoPacket;
import com.broadcast.network.packet.StopPlaybackPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL = "0.0.3br2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BroadcastMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        // C2S
        CHANNEL.messageBuilder(PlayRequestPacket.class, id++)
                .encoder(PlayRequestPacket::encode).decoder(PlayRequestPacket::decode)
                .consumerMainThread(PlayRequestPacket::handle).add();
        CHANNEL.messageBuilder(StopPlaybackPacket.class, id++)
                .encoder(StopPlaybackPacket::encode).decoder(StopPlaybackPacket::decode)
                .consumerMainThread(StopPlaybackPacket::handle).add();
        CHANNEL.messageBuilder(com.broadcast.network.packet.MusicTrackEndPacket.class, id++)
                .encoder(com.broadcast.network.packet.MusicTrackEndPacket::encode)
                .decoder(com.broadcast.network.packet.MusicTrackEndPacket::decode)
                .consumerMainThread(com.broadcast.network.packet.MusicTrackEndPacket::handle).add();
        CHANNEL.messageBuilder(SetSettingsPacket.class, id++)
                .encoder(SetSettingsPacket::encode).decoder(SetSettingsPacket::decode)
                .consumerMainThread(SetSettingsPacket::handle).add();
        CHANNEL.messageBuilder(RequestDataPacket.class, id++)
                .encoder(RequestDataPacket::encode).decoder(RequestDataPacket::decode)
                .consumerMainThread(RequestDataPacket::handle).add();
        CHANNEL.messageBuilder(RequestFilePacket.class, id++)
                .encoder(RequestFilePacket::encode).decoder(RequestFilePacket::decode)
                .consumerMainThread(RequestFilePacket::handle).add();
        CHANNEL.messageBuilder(MicUploadPacket.class, id++)
                .encoder(MicUploadPacket::encode).decoder(MicUploadPacket::decode)
                .consumerMainThread(MicUploadPacket::handle).add();
        CHANNEL.messageBuilder(RequestDeviceInfoPacket.class, id++)
                .encoder(RequestDeviceInfoPacket::encode).decoder(RequestDeviceInfoPacket::decode)
                .consumerMainThread(RequestDeviceInfoPacket::handle).add();
        CHANNEL.messageBuilder(SetSpeakerFrequencyPacket.class, id++)
                .encoder(SetSpeakerFrequencyPacket::encode).decoder(SetSpeakerFrequencyPacket::decode)
                .consumerMainThread(SetSpeakerFrequencyPacket::handle).add();
        CHANNEL.messageBuilder(SetMicSettingsPacket.class, id++)
                .encoder(SetMicSettingsPacket::encode).decoder(SetMicSettingsPacket::decode)
                .consumerMainThread(SetMicSettingsPacket::handle).add();
        CHANNEL.messageBuilder(SetStationFrequenciesPacket.class, id++)
                .encoder(SetStationFrequenciesPacket::encode).decoder(SetStationFrequenciesPacket::decode)
                .consumerMainThread(SetStationFrequenciesPacket::handle).add();
        CHANNEL.messageBuilder(SetPlaylistPacket.class, id++)
                .encoder(SetPlaylistPacket::encode).decoder(SetPlaylistPacket::decode)
                .consumerMainThread(SetPlaylistPacket::handle).add();
        CHANNEL.messageBuilder(SetPlaylistSettingsPacket.class, id++)
                .encoder(SetPlaylistSettingsPacket::encode).decoder(SetPlaylistSettingsPacket::decode)
                .consumerMainThread(SetPlaylistSettingsPacket::handle).add();
        CHANNEL.messageBuilder(DeleteMusicPacket.class, id++)
                .encoder(DeleteMusicPacket::encode).decoder(DeleteMusicPacket::decode)
                .consumerMainThread(DeleteMusicPacket::handle).add();
        CHANNEL.messageBuilder(SetPortableMicSettingsPacket.class, id++)
                .encoder(SetPortableMicSettingsPacket::encode).decoder(SetPortableMicSettingsPacket::decode)
                .consumerMainThread(SetPortableMicSettingsPacket::handle).add();
        CHANNEL.messageBuilder(PortableMicUploadPacket.class, id++)
                .encoder(PortableMicUploadPacket::encode).decoder(PortableMicUploadPacket::decode)
                .consumerMainThread(PortableMicUploadPacket::handle).add();
        // S2C
        CHANNEL.messageBuilder(MusicListPacket.class, id++)
                .encoder(MusicListPacket::encode).decoder(MusicListPacket::decode)
                .consumerMainThread(MusicListPacket::handle).add();
        CHANNEL.messageBuilder(StationInfoPacket.class, id++)
                .encoder(StationInfoPacket::encode).decoder(StationInfoPacket::decode)
                .consumerMainThread(StationInfoPacket::handle).add();
        CHANNEL.messageBuilder(PlaybackStatePacket.class, id++)
                .encoder(PlaybackStatePacket::encode).decoder(PlaybackStatePacket::decode)
                .consumerMainThread(PlaybackStatePacket::handle).add();
        CHANNEL.messageBuilder(MicStatePacket.class, id++)
                .encoder(MicStatePacket::encode).decoder(MicStatePacket::decode)
                .consumerMainThread(MicStatePacket::handle).add();
        CHANNEL.messageBuilder(MicAudioPacket.class, id++)
                .encoder(MicAudioPacket::encode).decoder(MicAudioPacket::decode)
                .consumerMainThread(MicAudioPacket::handle).add();
        CHANNEL.messageBuilder(MusicChunkPacket.class, id++)
                .encoder(MusicChunkPacket::encode).decoder(MusicChunkPacket::decode)
                .consumerMainThread(MusicChunkPacket::handle).add();
        CHANNEL.messageBuilder(MicCaptureControlPacket.class, id++)
                .encoder(MicCaptureControlPacket::encode).decoder(MicCaptureControlPacket::decode)
                .consumerMainThread(MicCaptureControlPacket::handle).add();
        CHANNEL.messageBuilder(DeviceInfoPacket.class, id++)
                .encoder(DeviceInfoPacket::encode).decoder(DeviceInfoPacket::decode)
                .consumerMainThread(DeviceInfoPacket::handle).add();
        CHANNEL.messageBuilder(PortableMicControlPacket.class, id++)
                .encoder(PortableMicControlPacket::encode).decoder(PortableMicControlPacket::decode)
                .consumerMainThread(PortableMicControlPacket::handle).add();
    }

    public static void sendToServer(Object msg) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), msg);
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToAll(Object msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }

    /** 只发给同一维度的玩家(音源坐标跨维度没有意义)。 */
    public static void sendToLevel(ServerLevel level, Object msg) {
        if (level == null || level.getServer() == null) return;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p.level() == level) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
            }
        }
    }
}
