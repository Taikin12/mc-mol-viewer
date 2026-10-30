package dev.taikin.mcplugins.mcmolview.mol;

import dev.taikin.miviewer.common.ElementInfo;
import dev.taikin.mcplugins.mcmolview.model.Molecule;
import dev.taikin.mcplugins.mcmolview.model.MoleculeSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts Cartesian coordinate blocks from ORCA output. Geometry optimizations
 * produce one block per step, so they become frames. Energies are attached when
 * one {@code FINAL SINGLE POINT ENERGY} line is available per geometry.
 */
public final class OrcaParser {

    private static final double BOHR_TO_ANGSTROM = 0.529177210903;
    private static final double SAME_GEOMETRY_TOLERANCE = 1.0e-4;

    public MoleculeSet parse(String name, String text) {
        List<String> lines = text.lines().toList();
        List<List<Molecule.Atom>> geometries = blocks(lines,
                "CARTESIAN COORDINATES (ANGSTROEM)", 1.0);
        if (geometries.isEmpty()) {
            geometries = blocks(lines, "CARTESIAN COORDINATES (A.U.)", BOHR_TO_ANGSTROM);
        }
        if (geometries.isEmpty()) {
            throw new IllegalArgumentException("ORCA output contains no Cartesian coordinate block.");
        }
        List<Double> energies = energies(lines);

        List<Molecule> frames = new ArrayList<>();
        for (int index = 0; index < geometries.size(); index++) {
            List<Molecule.Atom> atoms = geometries.get(index);
            if (!frames.isEmpty() && sameGeometry(frames.getLast().atoms(), atoms)) {
                continue;
            }
            Double energy = index < energies.size() ? energies.get(index) : null;
            frames.add(new Molecule(name, atoms, BondPerception.perceive(atoms), energy));
        }
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("ORCA output contains no Cartesian coordinate block.");
        }
        return new MoleculeSet(name, frames, dipole(lines), nImag(lines));
    }

    private Double dipole(List<String> lines) {
        Double debye = null;
        for (String line : lines) {
            String text = line.trim();
            if (!text.startsWith("Magnitude (Debye)")) {
                continue;
            }
            int separator = text.indexOf(':');
            if (separator < 0) {
                continue;
            }
            try {
                debye = Double.parseDouble(text.substring(separator + 1).trim().split("\\s+")[0]);
            } catch (NumberFormatException ignored) {
            }
        }
        return debye;
    }

    private Integer nImag(List<String> lines) {
        Integer result = null;
        for (String line : lines) {
            String text = line.trim();
            if (!text.startsWith("NImag") && !text.startsWith("Number of imaginary frequencies")) {
                continue;
            }
            int separator = text.indexOf(':');
            if (separator < 0) {
                continue;
            }
            try {
                result = Integer.parseInt(text.substring(separator + 1).trim().split("\\s+")[0]);
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private List<List<Molecule.Atom>> blocks(List<String> lines, String header, double factor) {
        List<List<Molecule.Atom>> blocks = new ArrayList<>();
        for (int start = 0; start < lines.size(); start++) {
            if (!lines.get(start).contains(header)) {
                continue;
            }
            List<Molecule.Atom> atoms = parseBlock(lines, start + 1, factor);
            if (!atoms.isEmpty()) {
                blocks.add(atoms);
            }
        }
        return blocks;
    }

    private List<Molecule.Atom> parseBlock(List<String> lines, int start, double factor) {
        List<Molecule.Atom> atoms = new ArrayList<>();
        int index = start;
        while (index < lines.size() && lines.get(index).trim().startsWith("---")) {
            index++;
        }
        for (; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.isEmpty()) {
                break;
            }
            String[] parts = line.split("\\s+");
            int offset = 0;
            String element = ElementInfo.leadingElement(parts[0]);
            if (element == null && parts.length >= 5 && isInteger(parts[0])) {
                offset = 1;
                element = ElementInfo.leadingElement(parts[1]);
            }
            int coordinateBase = offset == 1 ? 5 : 1;
            if (element == null || parts.length < coordinateBase + 3) {
                continue;
            }
            double[] coordinates = new double[3];
            boolean valid = true;
            for (int axis = 0; axis < 3; axis++) {
                try {
                    coordinates[axis] = Double.parseDouble(parts[axis + coordinateBase]) * factor;
                } catch (NumberFormatException e) {
                    valid = false;
                    break;
                }
            }
            if (!valid) {
                continue;
            }
            atoms.add(new Molecule.Atom(element, coordinates[0], coordinates[1], coordinates[2]));
        }
        return atoms;
    }

    private List<Double> energies(List<String> lines) {
        List<Double> energies = new ArrayList<>();
        for (String line : lines) {
            int marker = line.indexOf("FINAL SINGLE POINT ENERGY");
            if (marker < 0) {
                continue;
            }
            String[] parts = line.substring(marker + "FINAL SINGLE POINT ENERGY".length())
                    .trim().split("\\s+");
            if (parts.length == 0 || parts[0].isEmpty()) {
                continue;
            }
            try {
                energies.add(Double.parseDouble(parts[0]));
            } catch (NumberFormatException ignored) {
            }
        }
        return energies;
    }

    private boolean sameGeometry(List<Molecule.Atom> first, List<Molecule.Atom> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            Molecule.Atom a = first.get(index);
            Molecule.Atom b = second.get(index);
            if (!a.element().equals(b.element())
                    || Math.abs(a.x() - b.x()) > SAME_GEOMETRY_TOLERANCE
                    || Math.abs(a.y() - b.y()) > SAME_GEOMETRY_TOLERANCE
                    || Math.abs(a.z() - b.z()) > SAME_GEOMETRY_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
