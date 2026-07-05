package com.lwos.plan;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlockStateRefTest {

    @Test
    void idOnlyConstructorHasEmptyProperties() {
        BlockStateRef ref = new BlockStateRef("minecraft:dirt_path");
        assertEquals("minecraft:dirt_path", ref.id());
        assertTrue(ref.properties().isEmpty());
    }

    @Test
    void withAddsPropertyWithoutMutatingOriginal() {
        BlockStateRef base = new BlockStateRef("minecraft:oak_stairs");
        BlockStateRef facing = base.with("facing", "east");

        assertTrue(base.properties().isEmpty(), "with() must not mutate the receiver");
        assertEquals(Map.of("facing", "east"), facing.properties());
    }

    @Test
    void withChainsMultipleProperties() {
        BlockStateRef ref = new BlockStateRef("minecraft:oak_stairs")
                .with("facing", "north")
                .with("half", "bottom");
        assertEquals(Map.of("facing", "north", "half", "bottom"), ref.properties());
    }

    @Test
    void propertiesMapIsDefensivelyCopiedAndImmutable() {
        Map<String, String> source = new HashMap<>();
        source.put("facing", "west");
        BlockStateRef ref = new BlockStateRef("minecraft:oak_stairs", source);

        source.put("facing", "east"); // mutating the source must not leak into the ref
        assertEquals("west", ref.properties().get("facing"));
        assertThrows(UnsupportedOperationException.class, () -> ref.properties().put("half", "top"));
    }

    @Test
    void equalityAndHashCodeAccountForProperties() {
        BlockStateRef a = new BlockStateRef("minecraft:oak_stairs").with("facing", "east");
        BlockStateRef b = new BlockStateRef("minecraft:oak_stairs").with("facing", "east");
        BlockStateRef c = new BlockStateRef("minecraft:oak_stairs").with("facing", "west");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void nullPropertiesNormalizeToEmpty() {
        BlockStateRef ref = new BlockStateRef("minecraft:stone", null);
        assertNotNull(ref.properties());
        assertTrue(ref.properties().isEmpty());
    }
}
