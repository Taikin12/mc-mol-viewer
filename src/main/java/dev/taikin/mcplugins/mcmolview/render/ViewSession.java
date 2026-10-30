package dev.taikin.mcplugins.mcmolview.render;

import dev.taikin.mcplugins.mcmolview.model.Molecule;
import dev.taikin.mcplugins.mcmolview.model.MoleculeSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public final class ViewSession {

    public enum Axis {
        X,
        Y,
        Z
    }

    public enum RadiusMode {
        VDW,
        COVALENT
    }

    public record AtomMarker(int index, String label, String element, Vector position, double radius,
            int[] rgb) {
    }

    private final UUID worldId;
    private final MoleculeSet moleculeSet;
    private int id;
    private UUID owner;
    private boolean sharedEdit;
    private Location origin;
    private double blocksPerAngstrom;
    private int frameIndex;
    private double rotationX;
    private double rotationY;
    private double rotationZ;
    private Axis rotationAxis = Axis.Y;
    private double atomScale;
    private double spaceFillingScale = 1.0;
    private boolean spaceFilling;
    private boolean showBonds;
    private boolean showLabels;
    private List<Integer> selectedAtoms = List.of();
    private List<AtomMarker> atomMarkers = List.of();
    private RadiusMode radiusMode = RadiusMode.VDW;
    private int renderedAtoms;
    private List<UUID> entities = List.of();
    private Set<Long> chunks = Set.of();

    ViewSession(MoleculeSet moleculeSet, Location origin, double blocksPerAngstrom, double atomScale,
            boolean showBonds, boolean showLabels) {
        this.worldId = origin.getWorld().getUID();
        this.moleculeSet = moleculeSet;
        this.origin = origin.clone();
        this.blocksPerAngstrom = blocksPerAngstrom;
        this.atomScale = atomScale;
        this.showBonds = showBonds;
        this.showLabels = showLabels;
    }

    public UUID worldId() {
        return worldId;
    }

    public int id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public boolean sharedEdit() {
        return sharedEdit;
    }

    public MoleculeSet moleculeSet() {
        return moleculeSet;
    }

    public Molecule molecule() {
        return moleculeSet.frame(frameIndex);
    }

    public int frameIndex() {
        return frameIndex;
    }

    public int frameCount() {
        return moleculeSet.size();
    }

    public Location origin() {
        return origin.clone();
    }

    public double blocksPerAngstrom() {
        return blocksPerAngstrom;
    }

    public double rotationX() {
        return rotationX;
    }

    public double rotationY() {
        return rotationY;
    }

    public double rotationZ() {
        return rotationZ;
    }

    public Axis rotationAxis() {
        return rotationAxis;
    }

    public double atomScale() {
        return atomScale;
    }

    public double spaceFillingScale() {
        return spaceFillingScale;
    }

    public boolean spaceFilling() {
        return spaceFilling;
    }

    public boolean showBonds() {
        return showBonds;
    }

    public boolean showLabels() {
        return showLabels;
    }

    public List<Integer> selectedAtoms() {
        return selectedAtoms;
    }

    List<AtomMarker> atomMarkers() {
        return atomMarkers;
    }

    public RadiusMode radiusMode() {
        return radiusMode;
    }

    public int renderedAtoms() {
        return renderedAtoms;
    }

    List<UUID> entities() {
        return entities;
    }

    Set<Long> chunks() {
        return chunks;
    }

    void chunks(Set<Long> chunks) {
        this.chunks = Set.copyOf(chunks);
    }

    void entities(List<UUID> entities) {
        this.entities = entities;
    }

    void renderedAtoms(int renderedAtoms) {
        this.renderedAtoms = renderedAtoms;
    }

    void id(int id) {
        this.id = id;
    }

    void owner(UUID owner) {
        this.owner = owner;
    }

    void sharedEdit(boolean sharedEdit) {
        this.sharedEdit = sharedEdit;
    }

    void origin(Location origin) {
        this.origin = origin.clone();
    }

    void blocksPerAngstrom(double blocksPerAngstrom) {
        this.blocksPerAngstrom = blocksPerAngstrom;
    }

    void frameIndex(int frameIndex) {
        this.frameIndex = Math.max(0, Math.min(frameCount() - 1, frameIndex));
    }

    void rotation(double x, double y, double z) {
        this.rotationX = x;
        this.rotationY = y;
        this.rotationZ = z;
    }

    void rotationAxis(Axis rotationAxis) {
        this.rotationAxis = rotationAxis;
    }

    void atomScale(double atomScale) {
        this.atomScale = atomScale;
    }

    void spaceFillingScale(double spaceFillingScale) {
        this.spaceFillingScale = spaceFillingScale;
    }

    void spaceFilling(boolean spaceFilling) {
        this.spaceFilling = spaceFilling;
    }

    void showBonds(boolean showBonds) {
        this.showBonds = showBonds;
    }

    void showLabels(boolean showLabels) {
        this.showLabels = showLabels;
    }

    void selectedAtoms(List<Integer> selectedAtoms) {
        this.selectedAtoms = List.copyOf(selectedAtoms);
    }

    void atomMarkers(List<AtomMarker> atomMarkers) {
        this.atomMarkers = List.copyOf(atomMarkers);
    }

    void radiusMode(RadiusMode radiusMode) {
        this.radiusMode = radiusMode;
    }
}
