package dev.taikin.mcplugins.mcmolview.mol;

import dev.taikin.miviewer.common.ElementInfo;
import dev.taikin.mcplugins.mcmolview.model.Molecule;
import dev.taikin.mcplugins.mcmolview.model.MoleculeSet;
import java.util.ArrayList;
import java.util.List;

public final class XyzParser {

    public MoleculeSet parse(String name, String text) {
        List<String> lines = text.lines().toList();
        List<Molecule> frames = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            while (index < lines.size() && lines.get(index).isBlank()) {
                index++;
            }
            if (index >= lines.size()) {
                break;
            }
            int atomCount;
            try {
                atomCount = Integer.parseInt(lines.get(index).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("XYZ atom count is not a number on line "
                        + (index + 1) + ".");
            }
            index++;
            if (atomCount <= 0) {
                throw new IllegalArgumentException("XYZ file contains an empty frame.");
            }
            String comment = index < lines.size() ? lines.get(index).trim() : "";
            index++;

            List<Molecule.Atom> atoms = new ArrayList<>(atomCount);
            for (int atomIndex = 0; atomIndex < atomCount; atomIndex++) {
                if (index >= lines.size()) {
                    throw new IllegalArgumentException("XYZ file ended after " + atomIndex
                            + " of " + atomCount + " atoms.");
                }
                String line = lines.get(index++);
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 4) {
                    throw new IllegalArgumentException("Invalid XYZ atom line " + index + ": " + line);
                }
                String element = ElementInfo.leadingElement(parts[0]);
                if (element == null) {
                    throw new IllegalArgumentException("Unknown element in XYZ line " + index + ": "
                            + parts[0]);
                }
                atoms.add(new Molecule.Atom(element, number(parts[1], index),
                        number(parts[2], index), number(parts[3], index)));
            }
            String title = comment.isEmpty() ? name : comment;
            frames.add(new Molecule(title, atoms, BondPerception.perceive(atoms)));
        }
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("XYZ file contains no frames.");
        }
        return new MoleculeSet(name, frames);
    }

    private double number(String value, int line) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid XYZ coordinate on line " + line + ": " + value);
        }
    }
}
