package dev.taikin.mcplugins.mcmolview.mol;

import dev.taikin.miviewer.common.ElementInfo;
import dev.taikin.mcplugins.mcmolview.model.Molecule;
import dev.taikin.mcplugins.mcmolview.model.MoleculeSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MDL MOL V2000 / V3000 parser. Multi-record SDF input (records separated by
 * {@code $$$$}) becomes one frame per record.
 */
public final class MolParser {

    public MoleculeSet parse(String name, String text) {
        List<String> lines = text.lines().toList();
        List<Molecule> frames = new ArrayList<>();
        List<String> record = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().equals("$$$$")) {
                addRecord(frames, name, record);
                record.clear();
            } else {
                record.add(line);
            }
        }
        addRecord(frames, name, record);
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("MOL file contains no structure.");
        }
        return new MoleculeSet(name, frames);
    }

    private void addRecord(List<Molecule> frames, String name, List<String> lines) {
        if (lines.stream().allMatch(String::isBlank)) {
            return;
        }
        frames.add(parseRecord(name, lines));
    }

    private Molecule parseRecord(String fallbackName, List<String> lines) {
        boolean v3000 = lines.stream().anyMatch(line -> line.trim().startsWith("M  V30"));
        int countsIndex = -1;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String upper = line.toUpperCase(Locale.ROOT);
            if (v3000 ? line.trim().startsWith("M  V30 COUNTS") : upper.contains("V2000")) {
                countsIndex = index;
                break;
            }
        }
        if (countsIndex < 0) {
            throw new IllegalArgumentException("MOL file has no counts line (V2000/V3000).");
        }
        String title = lines.getFirst().trim().isEmpty() ? fallbackName : lines.getFirst().trim();
        return v3000
                ? parseV3000(title, lines, countsIndex)
                : parseV2000(title, lines, countsIndex);
    }

    private Molecule parseV2000(String title, List<String> lines, int countsIndex) {
        String counts = lines.get(countsIndex);
        int atomCount = fixedInt(counts, 0, 3);
        int bondCount = fixedInt(counts, 3, 6);
        if (atomCount <= 0) {
            throw new IllegalArgumentException("MOL file contains no atoms.");
        }

        List<Molecule.Atom> atoms = new ArrayList<>(atomCount);
        int[] charges = new int[atomCount];
        int lineIndex = countsIndex + 1;
        for (int atom = 0; atom < atomCount; atom++) {
            if (lineIndex >= lines.size()) {
                throw new IllegalArgumentException("MOL file ended while reading atoms.");
            }
            String line = lines.get(lineIndex++);
            String element = element(line);
            double x = fixedDouble(line, 0, 10, atom);
            double y = fixedDouble(line, 10, 20, atom);
            double z = fixedDouble(line, 20, 30, atom);
            atoms.add(new Molecule.Atom(element, x, y, z));
        }

        List<Molecule.Bond> bonds = new ArrayList<>(bondCount);
        for (int bond = 0; bond < bondCount; bond++) {
            if (lineIndex >= lines.size()) {
                throw new IllegalArgumentException("MOL file ended while reading bonds.");
            }
            String line = lines.get(lineIndex++);
            int first = fixedInt(line, 0, 3) - 1;
            int second = fixedInt(line, 3, 6) - 1;
            int order = fixedInt(line, 6, 9);
            addBond(bonds, first, second, order, atomCount);
        }

        while (lineIndex < lines.size()) {
            String line = lines.get(lineIndex++);
            if (line.startsWith("M  CHG")) {
                String[] parts = line.trim().split("\\s+");
                for (int part = 3; part + 1 < parts.length; part += 2) {
                    int atom = parseInt(parts[part]) - 1;
                    int charge = parseInt(parts[part + 1]);
                    if (atom >= 0 && atom < charges.length) {
                        charges[atom] = charge;
                    }
                }
            }
        }

        List<Molecule.Atom> charged = new ArrayList<>(atomCount);
        for (int atom = 0; atom < atomCount; atom++) {
            Molecule.Atom value = atoms.get(atom);
            charged.add(new Molecule.Atom(value.element(), value.x(), value.y(), value.z(),
                    charges[atom], null));
        }
        return new Molecule(title, charged, bonds);
    }

    private Molecule parseV3000(String title, List<String> lines, int countsIndex) {
        String[] counts = lines.get(countsIndex).trim().split("\\s+");
        int atomCount = parseInt(counts[3]);
        int bondCount = parseInt(counts[4]);
        if (atomCount <= 0) {
            throw new IllegalArgumentException("MOL file contains no atoms.");
        }

        List<Molecule.Atom> atoms = new ArrayList<>(atomCount);
        List<Molecule.Bond> bonds = new ArrayList<>(bondCount);
        boolean inAtoms = false;
        boolean inBonds = false;
        for (int index = countsIndex + 1; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (!line.startsWith("M  V30")) {
                continue;
            }
            String body = line.substring("M  V30".length()).trim();
            if (body.startsWith("BEGIN ATOM")) {
                inAtoms = true;
                continue;
            }
            if (body.startsWith("END ATOM")) {
                inAtoms = false;
                continue;
            }
            if (body.startsWith("BEGIN BOND")) {
                inBonds = true;
                continue;
            }
            if (body.startsWith("END BOND")) {
                inBonds = false;
                continue;
            }
            if (body.startsWith("END CTAB")) {
                break;
            }
            if (inAtoms) {
                String[] parts = body.split("\\s+");
                if (parts.length < 5) {
                    continue;
                }
                String element = ElementInfo.leadingElement(parts[1]);
                if (element == null) {
                    throw new IllegalArgumentException("Unknown element in MOL atom: " + parts[1]);
                }
                int charge = 0;
                for (String part : parts) {
                    if (part.startsWith("CHG=")) {
                        charge = parseInt(part.substring(4));
                    }
                }
                atoms.add(new Molecule.Atom(element, parseDouble(parts[2]),
                        parseDouble(parts[3]), parseDouble(parts[4]), charge, null));
            } else if (inBonds) {
                String[] parts = body.split("\\s+");
                if (parts.length < 4) {
                    continue;
                }
                addBond(bonds, parseInt(parts[1]) - 1, parseInt(parts[2]) - 1,
                        parseInt(parts[3]), atomCount);
            }
        }
        if (atoms.size() < atomCount) {
            throw new IllegalArgumentException("MOL V3000 atom block is incomplete.");
        }
        return new Molecule(title, atoms.subList(0, atomCount), bonds);
    }

    private void addBond(List<Molecule.Bond> bonds, int first, int second, int order, int atomCount) {
        if (first < 0 || second < 0 || first >= atomCount || second >= atomCount || first == second) {
            return;
        }
        int normalized = order >= 1 && order <= 3 ? order : 1;
        bonds.add(new Molecule.Bond(first, second, normalized));
    }

    private String element(String line) {
        String symbol = null;
        if (line.length() >= 34) {
            symbol = line.substring(31, 34).trim();
        }
        if (symbol == null || symbol.isEmpty()) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 4) {
                symbol = parts[3];
            }
        }
        String element = ElementInfo.leadingElement(symbol);
        if (element == null) {
            throw new IllegalArgumentException("Unknown element in MOL atom block: " + symbol);
        }
        return element;
    }

    private int fixedInt(String line, int from, int to) {
        if (line.length() < to) {
            return 0;
        }
        return parseInt(line.substring(from, to).trim());
    }

    private double fixedDouble(String line, int from, int to, int atom) {
        if (line.length() < to) {
            throw new IllegalArgumentException("Invalid MOL atom line for atom " + (atom + 1) + ".");
        }
        return parseDouble(line.substring(from, to).trim());
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
