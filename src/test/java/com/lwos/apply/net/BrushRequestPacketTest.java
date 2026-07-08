package com.lwos.apply.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BrushRequestPacketTest {

    @Test
    void roundTripPreservesAllFields() {
        BrushRequestPacket before = new BrushRequestPacket(2, -1234, 5678, 9);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        BrushRequestPacket.encode(before, buf);
        BrushRequestPacket after = BrushRequestPacket.decode(buf);
        assertEquals(before, after);
    }

    @Test
    void decodeRejectsAnOutOfRangeOpOrdinal() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        BrushRequestPacket.encode(new BrushRequestPacket(99, 0, 0, 6), buf);
        assertThrows(IllegalArgumentException.class, () -> BrushRequestPacket.decode(buf));
    }
}
