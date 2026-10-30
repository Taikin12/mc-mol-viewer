package dev.taikin.mcplugins.mcmolview.mol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.taikin.mcplugins.mcmolview.model.MoleculeSet;
import org.junit.jupiter.api.Test;

final class OrcaParserTest {

    @Test
    void prefersLastAngstromBlock() {
        String text = """
                CARTESIAN COORDINATES (A.U.)
                ----------------------------
                  NO LB      ZA    FRAG     MASS         X           Y           Z
                   0 C     6.0000    0    12.011    0.000000    0.000000    0.000000

                CARTESIAN COORDINATES (ANGSTROEM)
                ---------------------------------
                  O      0.000000    0.000000    0.000000
                  H      0.757000    0.586000    0.000000
                  H     -0.757000    0.586000    0.000000

                FINAL SINGLE POINT ENERGY     -76.0
                """;
        MoleculeSet set = new OrcaParser().parse("water.out", text);
        assertEquals(1, set.size());
        assertEquals(3, set.frame(0).atoms().size());
        assertEquals("O", set.frame(0).atoms().getFirst().element());
        assertEquals(0.757, set.frame(0).atoms().get(1).x(), 1.0e-6);
        assertEquals(2, set.frame(0).bonds().size());
        assertEquals(-76.0, set.frame(0).energy(), 1.0e-9);
    }

    @Test
    void collectsOptimizationFramesAndEnergies() {
        String text = """
                CARTESIAN COORDINATES (ANGSTROEM)
                ---------------------------------
                  H      0.000000    0.000000    0.000000
                FINAL SINGLE POINT ENERGY      -1.000000000000

                CARTESIAN COORDINATES (ANGSTROEM)
                ---------------------------------
                  H      0.100000    0.000000    0.000000
                FINAL SINGLE POINT ENERGY      -1.100000000000
                NImag : 0
                """;
        MoleculeSet set = new OrcaParser().parse("h_opt.out", text);
        assertEquals(2, set.size());
        assertEquals(1, set.frame(0).atoms().size());
        assertEquals(-1.0, set.frame(0).energy(), 1.0e-9);
        assertEquals(-1.1, set.frame(1).energy(), 1.0e-9);
        assertEquals(0.1, set.frame(1).atoms().getFirst().x(), 1.0e-9);
        assertEquals(0, set.nImag());
    }

    @Test
    void convertsBohrFallback() {
        String text = """
                CARTESIAN COORDINATES (A.U.)
                ----------------------------
                  NO LB      ZA    FRAG     MASS         X           Y           Z
                   0 H     1.0000    0     1.008    0.000000    0.000000    0.000000
                   1 H     1.0000    0     1.008    1.400000    0.000000    0.000000

                """;
        MoleculeSet set = new OrcaParser().parse("h2.out", text);
        assertEquals(2, set.frame(0).atoms().size());
        assertEquals(1.4 * 0.529177210903, set.frame(0).atoms().get(1).x(), 1.0e-6);
    }
}
