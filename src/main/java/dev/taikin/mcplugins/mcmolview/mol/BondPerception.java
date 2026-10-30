package dev.taikin.mcplugins.mcmolview.mol;

import dev.taikin.miviewer.common.ElementInfo;
import dev.taikin.mcplugins.mcmolview.model.Molecule;
import java.util.ArrayList;
import java.util.List;

public final class BondPerception {

    private static final double TOLERANCE = 1.25;
    private static final double MIN_DISTANCE = 0.1;

    private BondPerception() {
    }

    /**
     * Distance-based single-bond perception using covalent radii. Bond orders are
     * not inferred; use MOL/SDF input when orders matter.
     */
    public static List<Molecule.Bond> perceive(List<Molecule.Atom> atoms) {
        List<Molecule.Bond> bonds = new ArrayList<>();
        for (int first = 0; first < atoms.size(); first++) {
            Molecule.Atom a = atoms.get(first);
            double radiusA = ElementInfo.radius(a.element());
            for (int second = first + 1; second < atoms.size(); second++) {
                Molecule.Atom b = atoms.get(second);
                double dx = a.x() - b.x();
                double dy = a.y() - b.y();
                double dz = a.z() - b.z();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance < MIN_DISTANCE) {
                    continue;
                }
                double limit = (radiusA + ElementInfo.radius(b.element())) * TOLERANCE;
                if (distance <= limit) {
                    bonds.add(new Molecule.Bond(first, second, 1));
                }
            }
        }
        return bonds;
    }
}
