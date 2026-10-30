package dev.taikin.mcplugins.mcmolview.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A single molecular structure with Cartesian coordinates in angstrom.
 */
public final class Molecule {

    private final String name;
    private final List<Atom> atoms;
    private final List<Bond> bonds;
    private final Double energy;

    public Molecule(String name, List<Atom> atoms, List<Bond> bonds) {
        this(name, atoms, bonds, null);
    }

    public Molecule(String name, List<Atom> atoms, List<Bond> bonds, Double energy) {
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException("Molecule contains no atoms.");
        }
        this.name = name == null || name.isBlank() ? "Molecule" : name;
        this.atoms = Collections.unmodifiableList(List.copyOf(atoms));
        this.bonds = Collections.unmodifiableList(List.copyOf(bonds));
        this.energy = energy;
        for (Bond bond : this.bonds) {
            if (bond.first() < 0 || bond.first() >= this.atoms.size()
                    || bond.second() < 0 || bond.second() >= this.atoms.size()) {
                throw new IllegalArgumentException("Bond refers to an atom that does not exist.");
            }
        }
    }

    public String name() {
        return name;
    }

    public List<Atom> atoms() {
        return atoms;
    }

    public List<Bond> bonds() {
        return bonds;
    }

    /**
     * Electronic energy in hartree when the source provides one (ORCA output).
     */
    public Double energy() {
        return energy;
    }

    public Map<String, Integer> elementCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        for (Atom atom : atoms) {
            counts.merge(atom.element(), 1, Integer::sum);
        }
        return new LinkedHashMap<>(counts);
    }

    public String contents() {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Integer> entry : elementCounts().entrySet()) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(entry.getKey());
            if (entry.getValue() > 1) {
                text.append(entry.getValue());
            }
        }
        return text.toString();
    }

    /**
     * Label used for display: the file label when present, otherwise element + 1-based index.
     */
    public String label(int index) {
        Atom atom = atoms.get(index);
        if (atom.label() != null && !atom.label().isBlank()) {
            return atom.label();
        }
        return atom.element() + (index + 1);
    }

    public record Atom(String element, double x, double y, double z, int charge, String label) {

        public Atom(String element, double x, double y, double z) {
            this(element, x, y, z, 0, null);
        }
    }

    public record Bond(int first, int second, int order) {

        public Bond(int first, int second) {
            this(first, second, 1);
        }
    }
}
