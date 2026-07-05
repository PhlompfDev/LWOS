package com.lwos.organic.noise;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless proof that the noise primitives are deterministic per seed, diverge across seeds,
 * respect their stated output ranges, and (for cellular) actually clump (M5 plan, Task 1, Step 3).
 */
class NoiseTest {

    // ---- PerlinNoise -----------------------------------------------------------------------

    @Test
    void perlinIsDeterministicForSameSeedAndCoords() {
        PerlinNoise a = new PerlinNoise(42L);
        PerlinNoise b = new PerlinNoise(42L);
        for (double x = -3; x <= 3; x += 0.37) {
            for (double y = -3; y <= 3; y += 0.41) {
                assertEquals(a.noise(x, y), b.noise(x, y), 0.0,
                        "identical seed + coords must yield the identical value");
            }
        }
    }

    @Test
    void perlinDivergesForDifferentSeeds() {
        PerlinNoise a = new PerlinNoise(1L);
        PerlinNoise b = new PerlinNoise(2L);
        int differences = 0;
        for (double x = 0.5; x <= 20; x += 1.3) {
            for (double y = 0.5; y <= 20; y += 1.7) {
                if (Math.abs(a.noise(x, y) - b.noise(x, y)) > 1e-9) differences++;
            }
        }
        assertTrue(differences > 0, "different seeds must produce a different field");
    }

    @Test
    void perlinStaysWithinUnitRange() {
        PerlinNoise n = new PerlinNoise(7L);
        for (double x = -50; x <= 50; x += 0.53) {
            for (double y = -50; y <= 50; y += 0.53) {
                double v = n.noise(x, y);
                assertTrue(v >= -1.0 && v <= 1.0, "noise out of range at (" + x + "," + y + "): " + v);
            }
        }
    }

    @Test
    void perlinIsZeroAtIntegerLattice() {
        // Perlin noise is exactly zero at integer coordinates; a strong, seed-independent invariant.
        PerlinNoise n = new PerlinNoise(123L);
        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                assertEquals(0.0, n.noise(x, y), 1e-9, "expected zero at lattice point (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void perlinIsSmoothBetweenSamples() {
        // A tiny step must produce a tiny change — proves the field is continuous, not white noise.
        PerlinNoise n = new PerlinNoise(99L);
        double prev = n.noise(0, 0);
        for (double x = 0.01; x <= 5; x += 0.01) {
            double cur = n.noise(x, 0);
            assertTrue(Math.abs(cur - prev) < 0.2, "field jumped too far near x=" + x);
            prev = cur;
        }
    }

    @Test
    void perlinFractalIsDeterministicAndInRange() {
        PerlinNoise a = new PerlinNoise(5L);
        PerlinNoise b = new PerlinNoise(5L);
        for (double x = -4; x <= 4; x += 0.29) {
            double va = a.fractal(x, x * 0.5, 4, 0.5, 2.0);
            double vb = b.fractal(x, x * 0.5, 4, 0.5, 2.0);
            assertEquals(va, vb, 0.0);
            assertTrue(va >= -1.0 && va <= 1.0, "fractal out of range: " + va);
        }
    }

    // ---- CellularNoise ---------------------------------------------------------------------

    @Test
    void cellularIsDeterministicForSameSeed() {
        CellularNoise a = new CellularNoise(42L);
        CellularNoise b = new CellularNoise(42L);
        for (double x = -3; x <= 3; x += 0.37) {
            for (double y = -3; y <= 3; y += 0.41) {
                assertEquals(a.f1(x, y), b.f1(x, y), 0.0);
                assertEquals(a.cellValue(x, y), b.cellValue(x, y), 0.0);
            }
        }
    }

    @Test
    void cellularDivergesForDifferentSeeds() {
        CellularNoise a = new CellularNoise(10L);
        CellularNoise b = new CellularNoise(11L);
        int differences = 0;
        for (double x = 0.5; x <= 20; x += 1.3) {
            for (double y = 0.5; y <= 20; y += 1.7) {
                if (Math.abs(a.cellValue(x, y) - b.cellValue(x, y)) > 1e-9) differences++;
            }
        }
        assertTrue(differences > 0, "different seeds must produce different patches");
    }

    @Test
    void cellularF1IsNonNegativeAndBounded() {
        CellularNoise n = new CellularNoise(3L);
        for (double x = -20; x <= 20; x += 0.31) {
            for (double y = -20; y <= 20; y += 0.31) {
                double d = n.f1(x, y);
                assertTrue(d >= 0.0, "F1 distance must be non-negative");
                assertTrue(d < 2.0, "F1 distance unexpectedly large: " + d);
            }
        }
    }

    @Test
    void cellularValueStaysInUnitInterval() {
        CellularNoise n = new CellularNoise(8L);
        for (double x = -30; x <= 30; x += 0.29) {
            for (double y = -30; y <= 30; y += 0.29) {
                double v = n.cellValue(x, y);
                assertTrue(v >= 0.0 && v < 1.0, "cellValue out of [0,1): " + v);
            }
        }
    }

    @Test
    void cellularValueFormsContiguousPatches() {
        // Clumping proof: neighbouring samples very often share the same cell value. Pure per-point
        // randomness would almost never repeat; Worley cells make repeats the common case.
        CellularNoise n = new CellularNoise(77L);
        int total = 0, sameAsNeighbor = 0;
        double step = 0.1;
        for (double x = 0; x < 20; x += step) {
            for (double y = 0; y < 20; y += step) {
                total++;
                if (n.cellValue(x, y) == n.cellValue(x + step, y)) sameAsNeighbor++;
            }
        }
        double sharedFraction = (double) sameAsNeighbor / total;
        assertTrue(sharedFraction > 0.6,
                "expected large contiguous patches, but only " + sharedFraction + " of neighbors matched");
    }
}
