package com.lwos;

import com.lwos.apply.net.BrushRequestPacket;
import com.lwos.apply.net.EditRequestPacket;
import com.lwos.apply.net.RedoRequestPacket;
import com.lwos.apply.net.ShapeRequestPacket;
import com.lwos.apply.net.UndoRequestPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

import java.util.Optional;

@Mod(LwosMod.MODID)
public class LwosMod {
    public static final String MODID = "lwos";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String PROTOCOL_VERSION = "1";

    /** Mod network channel; carries the client's commit request to the server (spec §6). */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public LwosMod() {
        LOGGER.info("LWOS Builder Tools loading");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.SERVER, LwosServerConfig.SPEC);
        registerPackets();
        // Client-side registrations are wired by @Mod.EventBusSubscriber classes (guarded by Dist.CLIENT).
    }

    private static void registerPackets() {
        int id = 0;
        CHANNEL.registerMessage(id++, EditRequestPacket.class,
                EditRequestPacket::encode, EditRequestPacket::decode, EditRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, UndoRequestPacket.class,
                UndoRequestPacket::encode, UndoRequestPacket::decode, UndoRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, RedoRequestPacket.class,
                RedoRequestPacket::encode, RedoRequestPacket::decode, RedoRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, BrushRequestPacket.class,
                BrushRequestPacket::encode, BrushRequestPacket::decode, BrushRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ShapeRequestPacket.class,
                ShapeRequestPacket::encode, ShapeRequestPacket::decode, ShapeRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
