package dev.taikin.mcplugins.mcmolview.mol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.taikin.mcplugins.mcmolview.model.MoleculeSet;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class OrcaSampleTest {

    @Test
    void parsesBenzeneOptimizationSample() throws Exception {
        String text = Files.readString(Path.of("samples", "benzene_opt.out"));
        MoleculeSet set = new OrcaParser().parse("benzene_opt.out", text);
        assertEquals(10, set.size());
        assertEquals(12, set.frame(0).atoms().size());
        assertTrue(set.frame(0).energy() > set.frame(9).energy());
        assertEquals(-232.183149030848, set.frame(9).energy(), 1.0e-9);
        assertEquals(6, set.frame(0).atoms().stream()
                .filter(atom -> atom.element().equals("C")).count());
        assertEquals(0.000570025, set.dipoleDebye(), 1.0e-12);
    }
}
