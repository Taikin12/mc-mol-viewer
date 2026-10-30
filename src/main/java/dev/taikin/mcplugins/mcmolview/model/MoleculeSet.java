package dev.taikin.mcplugins.mcmolview.model;

import java.util.List;

/**
 * One file or calculation may contain several structures (optimization steps,
 * SDF records, multi-frame XYZ, ...). The set keeps them in input order.
 */
public final class MoleculeSet {

    private final String name;
    private final List<Molecule> frames;
    private final Double dipoleDebye;
    private final Integer nImag;

    public MoleculeSet(String name, List<Molecule> frames) {
        this(name, frames, null, null);
    }

    public MoleculeSet(String name, List<Molecule> frames, Double dipoleDebye, Integer nImag) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("MoleculeSet contains no frames.");
        }
        this.name = name == null || name.isBlank() ? frames.getFirst().name() : name;
        this.frames = List.copyOf(frames);
        this.dipoleDebye = dipoleDebye;
        this.nImag = nImag;
    }

    public String name() {
        return name;
    }

    public List<Molecule> frames() {
        return frames;
    }

    public int size() {
        return frames.size();
    }

    public Double dipoleDebye() {
        return dipoleDebye;
    }

    public Integer nImag() {
        return nImag;
    }

    public Molecule frame(int index) {
        return frames.get(index);
    }

    public static MoleculeSet single(Molecule molecule) {
        return new MoleculeSet(molecule.name(), List.of(molecule));
    }
}
