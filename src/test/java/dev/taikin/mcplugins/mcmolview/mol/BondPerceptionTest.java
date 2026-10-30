package dev.taikin.mcplugins.mcmolview.mol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.taikin.mcplugins.mcmolview.model.Molecule;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BondPerceptionTest {

    @Test
    void bondsWaterAndIgnoresDistantAtoms() {
        List<Molecule.Atom> atoms = List.of(
                new Molecule.Atom("O", 0.0, 0.0, 0.0),
                new Molecule.Atom("H", 0.757, 0.586, 0.0),
                new Molecule.Atom("H", -0.757, 0.586, 0.0),
                new Molecule.Atom("H", 5.0, 0.0, 0.0));
        List<Molecule.Bond> bonds = BondPerception.perceive(atoms);
        assertEquals(2, bonds.size());
        assertTrue(bonds.stream().allMatch(bond -> bond.first() == 0));
    }
}
