package com.lwos.apply.net;

import com.lwos.config.PathStyle;
import com.lwos.geometry.Vec3d;
import com.lwos.plan.TerrainMode;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditRequestPacketStyleTest {

    @Test
    void roundTripPreservesStyleJson() {
        PathStyle style = PathStyle.defaults();
        EditRequestPacket before = new EditRequestPacket(
                List.of(new Vec3d(1, 2, 3), new Vec3d(4, 5, 6)), 7.0, TerrainMode.CUT_AND_FILL, style.toJson());

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        EditRequestPacket.encode(before, buf);
        EditRequestPacket after = EditRequestPacket.decode(buf);

        assertEquals(before.controlPoints(), after.controlPoints());
        assertEquals(before.width(), after.width());
        assertEquals(before.mode(), after.mode());
        assertEquals(style, PathStyle.fromJson(after.styleJson()), "style must survive the wire");
    }
}
