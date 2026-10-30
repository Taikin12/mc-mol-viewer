package dev.taikin.mcplugins.mcmolview.mol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.taikin.mcplugins.mcmolview.model.MoleculeSet;
import org.junit.jupiter.api.Test;

final class XyzParserTest {

    @Test
    void parsesSingleFrameAndPerceivesBonds() {
        String text = """
                3
                water
                O 0.000 0.000 0.000
                H 0.757 0.586 0.000
                H -0.757 0.586 0.000
                """;
        MoleculeSet set = new XyzParser().parse("water.xyz", text);
        assertEquals(1, set.size());
        assertEquals(3, set.frame(0).atoms().size());
        assertEquals(2, set.frame(0).bonds().size());
        assertEquals("H2 O", set.frame(0).contents());
    }

    @Test
    void parsesMultipleFrames() {
        String text = """
                1
                first
                H 0.0 0.0 0.0

                1
                second
                H 0.0 0.0 1.0
                """;
        MoleculeSet set = new XyzParser().parse("h.xyz", text);
        assertEquals(2, set.size());
        assertEquals(0.0, set.frame(0).atoms().getFirst().z());
        assertEquals(1.0, set.frame(1).atoms().getFirst().z());
    }
}
