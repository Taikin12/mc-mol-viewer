package dev.taikin.mcplugins.mcmolview.mol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.taikin.mcplugins.mcmolview.model.MoleculeSet;
import org.junit.jupiter.api.Test;

final class MolParserTest {

    private static final String WATER_V2000 = """
            water
              Mrv  1

              3  2  0  0  0  0            999 V2000
                0.0000    0.0000    0.0000 O   0  0  0  0  0  0  0  0  0  0  0  0
                0.7570    0.5860    0.0000 H   0  0  0  0  0  0  0  0  0  0  0  0
               -0.7570    0.5860    0.0000 H   0  0  0  0  0  0  0  0  0  0  0  0
              1  2  1  0  0  0  0
              1  3  1  0  0  0  0
            M  END
            """;

    @Test
    void parsesV2000() {
        MoleculeSet set = new MolParser().parse("water.mol", WATER_V2000);
        assertEquals(1, set.size());
        assertEquals(3, set.frame(0).atoms().size());
        assertEquals("O", set.frame(0).atoms().getFirst().element());
        assertEquals(0.757, set.frame(0).atoms().get(1).x(), 1.0e-6);
        assertEquals(2, set.frame(0).bonds().size());
        assertEquals("water", set.frame(0).name());
    }

    @Test
    void parsesV3000() {
        String text = """
                water
                  Mrv  1

                  0  0  0  0  0  0            999 V3000
                M  V30 BEGIN CTAB
                M  V30 COUNTS 3 2 0 0 0
                M  V30 BEGIN ATOM
                M  V30 1 O 0.0 0.0 0.0 0
                M  V30 2 H 0.757 0.586 0.0 0
                M  V30 3 H -0.757 0.586 0.0 0
                M  V30 END ATOM
                M  V30 BEGIN BOND
                M  V30 1 1 2 2
                M  V30 2 1 3 1
                M  V30 END BOND
                M  V30 END CTAB
                M  END
                """;
        MoleculeSet set = new MolParser().parse("water.mol", text);
        assertEquals(3, set.frame(0).atoms().size());
        assertEquals(2, set.frame(0).bonds().size());
        assertEquals(2, set.frame(0).bonds().getFirst().order());
    }

    @Test
    void parsesMultipleSdfRecords() {
        String text = WATER_V2000 + "$$$$\n" + WATER_V2000.replace("water", "water2") + "$$$$\n";
        MoleculeSet set = new MolParser().parse("water.sdf", text);
        assertEquals(2, set.size());
        assertEquals("water", set.frame(0).name());
        assertEquals("water2", set.frame(1).name());
    }
}
