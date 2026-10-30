package dev.taikin.mcplugins.mcmolview.render;

import dev.taikin.mcplugins.mcmolview.McMolViewConfig;
import dev.taikin.mcplugins.mcmolview.McMolViewPlugin;
import dev.taikin.miviewer.common.ElementInfo;
import dev.taikin.mcplugins.mcmolview.model.Molecule;
import dev.taikin.mcplugins.mcmolview.model.MoleculeSet;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import dev.taikin.miviewer.common.MolecularGeometry;

public final class MoleculeRenderer {

    public static final String TAG = "mcmolview";
    private static final int MAX_BONDS = 1500;
    private static final int MAX_LABELS = 300;
    private static final double BOND_SPACING = 0.14;
    private static final double BALL_STICK_RATIO = 0.30;
    private static final int DEFAULT_PLAYBACK_TICKS = 10;
    public static final int MIN_PLAYBACK_TICKS = 2;
    public static final int MAX_PLAYBACK_TICKS = 40;

    public record ModelRef(UUID playerId, int modelId) {
    }

    private final McMolViewPlugin plugin;
    private final java.nio.file.Path storageFile;
    private ModelRegistry registry = new ModelRegistry();
    private final Map<UUID, ModelRef> interactions = new HashMap<>();
    private final Map<UUID, Map<Long, Integer>> chunkTickets = new HashMap<>();
    private final Map<UUID, Playback> playbacks = new HashMap<>();
    private final Map<UUID, Integer> playbackSpeeds = new HashMap<>();
    private final Map<UUID, Boolean> playbackLoops = new HashMap<>();
    private boolean loaded;
    private boolean dirty;
    private boolean saveScheduled;

    public MoleculeRenderer(McMolViewPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = plugin.getDataFolder().toPath().resolve("models.yml");
    }

    public int view(Player player, MoleculeSet set, double blocksPerAngstrom) {
        McMolViewConfig config = plugin.config();
        if (!Double.isFinite(blocksPerAngstrom) || blocksPerAngstrom <= 0.0) {
            throw new IllegalArgumentException("Scale must be greater than zero.");
        }
        if (!Double.isFinite(blocksPerAngstrom) || blocksPerAngstrom < 0.1 || blocksPerAngstrom > 10.0) {
            throw new IllegalArgumentException("Scale must be between 0.1 and 10 blocks per angstrom.");
        }
        int atoms = 0;
        for (Molecule molecule : set.frames()) {
            atoms = Math.max(atoms, molecule.atoms().size());
        }
        if (atoms > config.maxAtoms()) {
            throw new IllegalArgumentException("Too many atoms to display (" + atoms + "). Limit is "
                    + config.maxAtoms() + ".");
        }
        if (registry.size() >= config.maxModels()) {
            throw new IllegalArgumentException("Too many models. Limit is " + config.maxModels() + ".");
        }

        Location origin = placement(player, config, registry.size());
        ViewSession session = new ViewSession(set, origin, blocksPerAngstrom, config.atomScale(),
                config.defaultShowBonds(), config.defaultShowLabels());
        registry.add(player.getUniqueId(), session);
        try {
            render(player, session);
        } catch (RuntimeException e) {
            registry.remove(session.id());
            throw e;
        }
        markDirty();
        return session.renderedAtoms();
    }

    public ViewSession session(Player player) {
        return registry.active(player.getUniqueId());
    }

    public int activeId(Player player) {
        return registry.activeId(player.getUniqueId());
    }

    public List<ViewSession> models(Player player) {
        return registry.models();
    }

    public ViewSession model(int id) {
        return registry.get(id);
    }

    public boolean select(Player player, int id) {
        boolean selected = registry.select(player.getUniqueId(), id);
        if (selected) {
            markDirty();
        }
        return selected;
    }

    public ModelRef modelRef(UUID entityId) {
        return interactions.get(entityId);
    }

    public boolean isAdmin(Player player) {
        return player.hasPermission("mcmolview.admin");
    }

    public boolean canEdit(Player player, ViewSession session) {
        if (session == null) {
            return false;
        }
        if (isAdmin(player) || player.getUniqueId().equals(session.owner())) {
            return true;
        }
        return session.sharedEdit() && player.hasPermission("mcmolview.edit.shared");
    }

    public boolean canDelete(Player player, ViewSession session) {
        return session != null && (isAdmin(player) || player.getUniqueId().equals(session.owner()));
    }

    public boolean canShare(Player player, ViewSession session) {
        return canDelete(player, session);
    }

    public boolean canDelete(Player player, int id) {
        return canDelete(player, registry.get(id));
    }

    public boolean canShare(Player player, int id) {
        return canShare(player, registry.get(id));
    }

    public void adjustScale(Player player, double multiplier) {
        ViewSession session = requireEditableSession(player);
        session.blocksPerAngstrom(clamp(session.blocksPerAngstrom() * multiplier, 0.1, 10.0));
        refresh(player, session);
    }

    public void rotate(Player player, double deltaX, double deltaY, double deltaZ) {
        ViewSession session = requireEditableSession(player);
        session.rotation(
                normalize(session.rotationX() + deltaX),
                normalize(session.rotationY() + deltaY),
                normalize(session.rotationZ() + deltaZ));
        refresh(player, session);
    }

    public void adjustAtomScale(Player player, double multiplier) {
        ViewSession session = requireEditableSession(player);
        if (session.spaceFilling()) {
            session.spaceFillingScale(clamp(session.spaceFillingScale() * multiplier, 0.2, 8.0));
        } else {
            session.atomScale(clamp(session.atomScale() * multiplier, 0.2, 8.0));
        }
        refresh(player, session);
    }

    public void setRotationAxis(Player player, ViewSession.Axis axis) {
        requireEditableSession(player).rotationAxis(axis);
        markDirty();
    }

    public void cycleRotationAxis(Player player) {
        ViewSession session = requireEditableSession(player);
        session.rotationAxis(switch (session.rotationAxis()) {
            case X -> ViewSession.Axis.Y;
            case Y -> ViewSession.Axis.Z;
            case Z -> ViewSession.Axis.X;
        });
        markDirty();
    }

    public void rotateAroundAxis(Player player, double degrees) {
        ViewSession session = requireEditableSession(player);
        switch (session.rotationAxis()) {
            case X -> session.rotation(
                    normalize(session.rotationX() + degrees),
                    session.rotationY(),
                    session.rotationZ());
            case Y -> session.rotation(
                    session.rotationX(),
                    normalize(session.rotationY() + degrees),
                    session.rotationZ());
            case Z -> session.rotation(
                    session.rotationX(),
                    session.rotationY(),
                    normalize(session.rotationZ() + degrees));
        }
        refresh(player, session);
    }

    public String setFrame(Player player, int frame) {
        return setFrame(player, frame, true);
    }

    private String setFrame(Player player, int frame, boolean persist) {
        ViewSession session = requireEditableSession(player);
        int target = clamp(frame, 0, session.frameCount() - 1);
        if (target != session.frameIndex()) {
            int previousAtoms = session.molecule().atoms().size();
            session.frameIndex(target);
            if (session.molecule().atoms().size() != previousAtoms) {
                session.selectedAtoms(List.of());
            }
            refresh(player, session, persist);
        }
        return frameText(session);
    }

    public String adjustFrame(Player player, int delta) {
        ViewSession session = requireEditableSession(player);
        return setFrame(player, session.frameIndex() + delta);
    }

    public boolean isPlaying(Player player) {
        return playbacks.containsKey(player.getUniqueId());
    }

    public int playbackInterval(Player player) {
        Playback playback = playbacks.get(player.getUniqueId());
        return playback == null
                ? playbackSpeeds.getOrDefault(player.getUniqueId(), DEFAULT_PLAYBACK_TICKS)
                : playback.intervalTicks;
    }

    public boolean playbackLoop(Player player) {
        Playback playback = playbacks.get(player.getUniqueId());
        return playback == null
                ? playbackLoops.getOrDefault(player.getUniqueId(), true)
                : playback.loop;
    }

    public boolean togglePlayback(Player player) {
        if (isPlaying(player)) {
            stopPlayback(player);
            return false;
        }
        startPlayback(player);
        return true;
    }

    public void startPlayback(Player player) {
        stopPlayback(player);
        ViewSession session = requireEditableSession(player);
        if (session.frameCount() <= 1) {
            throw new IllegalStateException("このモデルは単一構造です。複数フレームのモデルで使ってください。");
        }
        int interval = playbackSpeeds.getOrDefault(player.getUniqueId(), DEFAULT_PLAYBACK_TICKS);
        boolean loop = playbackLoops.getOrDefault(player.getUniqueId(), true);
        Playback playback = new Playback(session.id(), interval, loop);
        playback.task = schedulePlayback(player, playback);
        playbacks.put(player.getUniqueId(), playback);
    }

    public void stopPlayback(Player player) {
        Playback playback = playbacks.remove(player.getUniqueId());
        if (playback != null && playback.task != null) {
            playback.task.cancel();
        }
    }

    public void stopAllPlayback() {
        for (Playback playback : List.copyOf(playbacks.values())) {
            if (playback.task != null) {
                playback.task.cancel();
            }
        }
        playbacks.clear();
    }

    public void setPlaybackInterval(Player player, int ticks) {
        int clamped = clamp(ticks, MIN_PLAYBACK_TICKS, MAX_PLAYBACK_TICKS);
        playbackSpeeds.put(player.getUniqueId(), clamped);
        Playback playback = playbacks.get(player.getUniqueId());
        if (playback == null) {
            return;
        }
        playback.intervalTicks = clamped;
        if (playback.task != null) {
            playback.task.cancel();
        }
        playback.task = schedulePlayback(player, playback);
    }

    public void togglePlaybackLoop(Player player) {
        boolean loop = !playbackLoop(player);
        playbackLoops.put(player.getUniqueId(), loop);
        Playback playback = playbacks.get(player.getUniqueId());
        if (playback != null) {
            playback.loop = loop;
        }
    }

    private BukkitTask schedulePlayback(Player player, Playback playback) {
        UUID playerId = player.getUniqueId();
        return plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tickPlayback(playerId),
                playback.intervalTicks, playback.intervalTicks);
    }

    private void tickPlayback(UUID playerId) {
        Playback playback = playbacks.get(playerId);
        if (playback == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            stopPlayback(playerId);
            return;
        }
        ViewSession session = registry.get(playback.sessionId);
        if (session == null || registry.active(playerId) != session || !canEdit(player, session)
                || !player.getWorld().getUID().equals(session.worldId())) {
            stopPlayback(playerId);
            return;
        }
        int next = session.frameIndex() + 1;
        if (next >= session.frameCount()) {
            if (!playback.loop) {
                stopPlayback(playerId);
                return;
            }
            next = 0;
        }
        try {
            setFrame(player, next, false);
        } catch (IllegalStateException e) {
            stopPlayback(playerId);
        }
    }

    private void stopPlayback(UUID playerId) {
        Playback playback = playbacks.remove(playerId);
        if (playback != null && playback.task != null) {
            playback.task.cancel();
        }
    }

    public String frameText(ViewSession session) {
        if (session.frameCount() <= 1) {
            return "単一構造";
        }
        return "フレーム " + (session.frameIndex() + 1) + "/" + session.frameCount();
    }

    public void toggleSpaceFilling(Player player) {
        ViewSession session = requireEditableSession(player);
        session.spaceFilling(!session.spaceFilling());
        refresh(player, session);
    }

    public void toggleBonds(Player player) {
        ViewSession session = requireEditableSession(player);
        session.showBonds(!session.showBonds());
        refresh(player, session);
    }

    public void cycleRadiusMode(Player player) {
        ViewSession session = requireEditableSession(player);
        session.radiusMode(switch (session.radiusMode()) {
            case VDW -> ViewSession.RadiusMode.COVALENT;
            case COVALENT -> ViewSession.RadiusMode.VDW;
        });
        refresh(player, session);
    }

    public void replaceOrigin(Player player) {
        ViewSession session = requireEditableSession(player);
        session.origin(placement(player, plugin.config()));
        refresh(player, session);
    }

    public void setOrigin(Player player, Location origin) {
        ViewSession session = requireEditableSession(player);
        session.origin(origin);
        refresh(player, session);
    }

    public void reset(Player player) {
        ViewSession session = requireEditableSession(player);
        session.blocksPerAngstrom(plugin.config().blocksPerAngstrom());
        session.atomScale(plugin.config().atomScale());
        session.spaceFillingScale(1.0);
        session.spaceFilling(false);
        session.showBonds(plugin.config().defaultShowBonds());
        session.showLabels(plugin.config().defaultShowLabels());
        session.selectedAtoms(List.of());
        session.radiusMode(ViewSession.RadiusMode.VDW);
        session.frameIndex(0);
        session.rotation(0.0, 0.0, 0.0);
        session.rotationAxis(ViewSession.Axis.Y);
        session.origin(placement(player, plugin.config()));
        refresh(player, session);
    }

    public void teleport(Player player, int id) {
        ViewSession session = registry.get(id);
        if (session == null) {
            throw new IllegalStateException("モデル #" + id + " はありません。");
        }
        if (!player.hasPermission("mcmolview.use")) {
            throw new IllegalStateException("テレポートする権限がありません。");
        }
        player.teleport(session.origin().clone().add(0.0, 2.0, 0.0));
    }

    public void toggleSharedEdit(Player player) {
        ViewSession session = requireSession(player);
        if (!canShare(player, session)) {
            throw new IllegalStateException("共有設定を変更する権限がありません。");
        }
        session.sharedEdit(!session.sharedEdit());
        markDirty();
    }

    public void setSharedEdit(Player player, int id, boolean shared) {
        ViewSession session = registry.get(id);
        if (session == null) {
            throw new IllegalStateException("モデル #" + id + " はありません。");
        }
        if (!canShare(player, session)) {
            throw new IllegalStateException("共有設定を変更する権限がありません。");
        }
        session.sharedEdit(shared);
        markDirty();
    }

    public void toggleLabels(Player player) {
        ViewSession session = requireSession(player);
        session.showLabels(!session.showLabels());
        refresh(player, session);
    }

    public String addAtomSelection(Player player) {
        ViewSession session = requireSession(player);
        if (!player.getWorld().getUID().equals(session.worldId())) {
            return "このモデルは別のワールドにあります。";
        }
        ViewSession.AtomMarker marker = findAtom(player, session);
        if (marker == null) {
            return "原子が見つかりません。";
        }
        List<Integer> selected = new ArrayList<>(session.selectedAtoms());
        if (selected.size() >= 4) {
            selected.clear();
        }
        if (!selected.contains(marker.index())) {
            selected.add(marker.index());
        }
        session.selectedAtoms(selected);
        refresh(player, session);
        return selectionText(session);
    }

    public String clearAtomSelection(Player player) {
        ViewSession session = requireSession(player);
        if (session.selectedAtoms().isEmpty()) {
            return "測定用の原子選択はありません。";
        }
        session.selectedAtoms(List.of());
        refresh(player, session);
        return "測定用の原子選択を解除しました。";
    }

    private ViewSession.AtomMarker findAtom(Player player, ViewSession session) {
        Vector eye = player.getEyeLocation().toVector();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Vector origin = session.origin().toVector();
        ViewSession.AtomMarker best = null;
        double bestT = 64.0;
        for (ViewSession.AtomMarker marker : session.atomMarkers()) {
            Vector center = origin.clone().add(marker.position());
            double radius = Math.max(0.35, marker.radius());
            Vector oc = eye.clone().subtract(center);
            double b = oc.dot(direction);
            double c = oc.dot(oc) - radius * radius;
            double discriminant = b * b - c;
            if (discriminant < 0.0) {
                continue;
            }
            double t = -b - Math.sqrt(discriminant);
            if (t < 0.0) {
                t = -b + Math.sqrt(discriminant);
            }
            if (t >= 0.0 && t < bestT) {
                bestT = t;
                best = marker;
            }
        }
        if (best != null) {
            return best;
        }

        double bestDistance = 1.0;
        for (ViewSession.AtomMarker marker : session.atomMarkers()) {
            Vector center = origin.clone().add(marker.position());
            Vector toCenter = center.clone().subtract(eye);
            double t = toCenter.dot(direction);
            if (t < 0.0 || t > 64.0) {
                continue;
            }
            Vector closest = eye.clone().add(direction.clone().multiply(t));
            double distance = closest.distance(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = marker;
            }
        }
        return best;
    }

    private String selectionText(ViewSession session) {
        List<Integer> selected = session.selectedAtoms();
        List<ViewSession.AtomMarker> markers = session.atomMarkers();
        List<ViewSession.AtomMarker> atoms = new ArrayList<>();
        for (int index : selected) {
            if (index >= 0 && index < markers.size()) {
                atoms.add(markers.get(index));
            }
        }
        if (atoms.isEmpty()) {
            return "原子未選択";
        }
        if (atoms.size() == 1) {
            return "選択: " + atoms.getFirst().label();
        }
        if (atoms.size() == 2) {
            double distance = MolecularGeometry.distance(
                    atoms.get(0).position(), atoms.get(1).position(), session.blocksPerAngstrom());
            return String.format(Locale.ROOT, "距離 %s–%s: %.3f Å",
                    atoms.get(0).label(), atoms.get(1).label(), distance);
        }
        if (atoms.size() == 3) {
            double angle = MolecularGeometry.angle(
                    atoms.get(0).position(), atoms.get(1).position(), atoms.get(2).position());
            return String.format(Locale.ROOT, "角度 %s–%s–%s: %.1f°",
                    atoms.get(0).label(), atoms.get(1).label(), atoms.get(2).label(), angle);
        }
        double angle = MolecularGeometry.dihedral(
                atoms.get(0).position(), atoms.get(1).position(),
                atoms.get(2).position(), atoms.get(3).position());
        return String.format(Locale.ROOT, "二面角 %s–%s–%s–%s: %.1f°",
                atoms.get(0).label(), atoms.get(1).label(), atoms.get(2).label(),
                atoms.get(3).label(), angle);
    }

    public int clear(Player player) {
        ViewSession session = session(player);
        if (session == null) {
            return 0;
        }
        return clear(player, session.id());
    }

    public int clear(Player player, int id) {
        ViewSession session = registry.get(id);
        if (session == null || !canDelete(player, session)) {
            return 0;
        }
        registry.remove(id);
        int removed = removeEntities(session);
        releaseChunks(session);
        markDirty();
        return removed;
    }

    public int clearAll(Player player) {
        int removed = 0;
        for (ViewSession session : registry.models()) {
            if (isAdmin(player) || player.getUniqueId().equals(session.owner())) {
                registry.remove(session.id());
                removed += removeEntities(session);
                releaseChunks(session);
            }
        }
        markDirty();
        return removed;
    }

    public void clearAll() {
        for (ViewSession session : registry.models()) {
            removeEntities(session);
            releaseChunks(session);
        }
        registry.clear();
        removeTaggedEntities();
        interactions.clear();
    }

    public void removeOrphans() {
        removeTaggedEntities();
        interactions.clear();
    }

    private void markDirty() {
        dirty = true;
        if (saveScheduled) {
            return;
        }
        saveScheduled = true;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            saveScheduled = false;
            save();
        }, 100L);
    }

    public void save() {
        if (!loaded || !dirty) {
            return;
        }
        try {
            ModelStore.save(storageFile, registry);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save models.yml: " + e.getMessage());
        }
    }

    public void load() {
        if (registry.size() != 0) {
            return;
        }
        ModelStore.LoadResult result = ModelStore.load(storageFile, plugin.getLogger());
        registry = result.registry();
        for (ViewSession session : registry.models()) {
            try {
                render(session.origin().getWorld(), session.owner(), session);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Could not restore model #" + session.id() + ": "
                        + e.getMessage());
            }
        }
        this.loaded = true;
        this.dirty = result.migrated();
        if (result.migrated()) {
            markDirty();
        }
    }

    private void refresh(Player player, ViewSession session) {
        refresh(player, session, true);
    }

    private void refresh(Player player, ViewSession session, boolean persist) {
        if (!player.isOnline()) {
            return;
        }
        if (!player.getWorld().getUID().equals(session.worldId())) {
            player.sendMessage(Component.text("このモデルは別のワールドにあります。",
                    NamedTextColor.RED));
            return;
        }
        removeEntities(session);
        releaseChunks(session);
        render(player, session);
        if (persist) {
            markDirty();
        }
    }

    private void render(Player player, ViewSession session) {
        UUID owner = session.owner() == null ? player.getUniqueId() : session.owner();
        render(player.getWorld(), owner, session);
    }

    private void render(World world, UUID ownerId, ViewSession session) {
        Molecule molecule = session.molecule();
        double scale = session.blocksPerAngstrom();
        double centroidX = 0.0;
        double centroidY = 0.0;
        double centroidZ = 0.0;
        for (Molecule.Atom atom : molecule.atoms()) {
            centroidX += atom.x();
            centroidY += atom.y();
            centroidZ += atom.z();
        }
        int atomCount = molecule.atoms().size();
        centroidX /= atomCount;
        centroidY /= atomCount;
        centroidZ /= atomCount;

        double rotationX = Math.toRadians(session.rotationX());
        double rotationY = Math.toRadians(session.rotationY());
        double rotationZ = Math.toRadians(session.rotationZ());
        McMolViewConfig.RenderMode mode = plugin.config().renderMode();
        Location origin = session.origin();

        List<Vector> positions = new ArrayList<>(atomCount);
        List<UUID> entities = new ArrayList<>(atomCount * 3);
        double renderedMaxY = Double.NEGATIVE_INFINITY;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        List<ViewSession.AtomMarker> markers = new ArrayList<>(atomCount);
        Set<Integer> highlighted = new HashSet<>(session.selectedAtoms());

        try {
            for (int atomIndex = 0; atomIndex < atomCount; atomIndex++) {
                Molecule.Atom atom = molecule.atoms().get(atomIndex);
                Vector position = new Vector((atom.x() - centroidX) * scale,
                        (atom.y() - centroidY) * scale,
                        (atom.z() - centroidZ) * scale);
                rotate(position, rotationX, rotationY, rotationZ);
                positions.add(position);
                renderedMaxY = Math.max(renderedMaxY, position.getY());

                int[] rgb = plugin.config().atomColor(atom.element());
                double radiusBlocks;
                if (session.spaceFilling() && mode == McMolViewConfig.RenderMode.MODEL) {
                    double radiusAngstrom = switch (session.radiusMode()) {
                        case VDW -> ElementInfo.vanDerWaalsRadius(atom.element());
                        case COVALENT -> ElementInfo.radius(atom.element());
                    };
                    radiusBlocks = radiusAngstrom * scale * session.spaceFillingScale();
                } else {
                    radiusBlocks = ElementInfo.vanDerWaalsRadius(atom.element())
                            * scale * BALL_STICK_RATIO * session.atomScale();
                }
                double atomScale = 2.0 * radiusBlocks;
                if (mode == McMolViewConfig.RenderMode.CIRCLE) {
                    atomScale *= 1.15;
                }
                atomScale = Math.max(0.2, Math.min(session.spaceFilling() ? 20.0 : 6.0, atomScale));

                double radius = atomScale / 2.0;
                minX = Math.min(minX, position.getX() - radius);
                minY = Math.min(minY, position.getY() - radius);
                minZ = Math.min(minZ, position.getZ() - radius);
                maxX = Math.max(maxX, position.getX() + radius);
                maxY = Math.max(maxY, position.getY() + radius);
                maxZ = Math.max(maxZ, position.getZ() + radius);

                Location location = new Location(world,
                        origin.getX() + position.getX(),
                        origin.getY() + position.getY(),
                        origin.getZ() + position.getZ());
                Entity display;
                if (mode == McMolViewConfig.RenderMode.MODEL) {
                    display = spawnItem(world, location, rgb, (float) atomScale);
                } else {
                    int alpha = 200;
                    Color background;
                    TextColor textColor;
                    String glyph;
                    if (mode == McMolViewConfig.RenderMode.CIRCLE) {
                        background = Color.fromARGB(0, 0, 0, 0);
                        textColor = TextColor.color(rgb[0], rgb[1], rgb[2]);
                        glyph = "\u25CF";
                    } else {
                        background = Color.fromARGB(alpha, rgb[0], rgb[1], rgb[2]);
                        textColor = luminance(rgb) >= 145
                                ? TextColor.color(0, 0, 0)
                                : TextColor.color(255, 255, 255);
                        glyph = atom.element();
                    }
                    display = spawn(world, location, Component.text(glyph, textColor),
                            background, (float) atomScale,
                            mode == McMolViewConfig.RenderMode.CIRCLE && plugin.config().sphereShadow());
                }
                entities.add(display.getUniqueId());
                if (highlighted.contains(atomIndex)) {
                    display.setGlowing(true);
                }
                markers.add(new ViewSession.AtomMarker(atomIndex, molecule.label(atomIndex),
                        atom.element(), position.clone(), atomScale / 2.0, rgb));
            }
            session.atomMarkers(markers);

            if (!markers.isEmpty()) {
                int labelCount = 0;
                for (ViewSession.AtomMarker marker : markers) {
                    boolean isSelected = highlighted.contains(marker.index());
                    if (!isSelected && (!session.showLabels() || labelCount >= MAX_LABELS)) {
                        continue;
                    }
                    Location labelLocation = new Location(world,
                            origin.getX() + marker.position().getX(),
                            origin.getY() + marker.position().getY() + marker.radius() + 0.25,
                            origin.getZ() + marker.position().getZ());
                    TextDisplay label = spawn(world, labelLocation,
                            Component.text(marker.label(), TextColor.color(
                                    marker.rgb()[0], marker.rgb()[1], marker.rgb()[2])),
                            Color.fromARGB(0, 0, 0, 0), 0.6f, true);
                    label.setGlowing(isSelected);
                    entities.add(label.getUniqueId());
                    labelCount++;
                }
            }

            if (session.showBonds() && !session.spaceFilling()
                    && mode == McMolViewConfig.RenderMode.MODEL) {
                int renderedBonds = 0;
                for (Molecule.Bond bond : molecule.bonds()) {
                    if (renderedBonds >= MAX_BONDS) {
                        break;
                    }
                    if (bond.first() >= positions.size() || bond.second() >= positions.size()) {
                        continue;
                    }
                    Vector start = positions.get(bond.first()).clone();
                    Vector end = positions.get(bond.second()).clone();
                    Vector direction = end.clone().subtract(start);
                    double length = direction.length();
                    if (length < 1.0e-6) {
                        continue;
                    }
                    Vector perpendicular = direction.clone().crossProduct(new Vector(0, 1, 0));
                    if (perpendicular.lengthSquared() < 1.0e-8) {
                        perpendicular = direction.clone().crossProduct(new Vector(1, 0, 0));
                    }
                    perpendicular.normalize();
                    int order = Math.max(1, Math.min(3, bond.order()));
                    int[] firstColor = plugin.config().atomColor(
                            molecule.atoms().get(bond.first()).element());
                    int[] secondColor = plugin.config().atomColor(
                            molecule.atoms().get(bond.second()).element());
                    for (int line = 0; line < order; line++) {
                        Vector offset = perpendicular.clone()
                                .multiply((line - (order - 1) / 2.0) * BOND_SPACING);
                        Vector lineStart = start.clone().add(offset);
                        Vector lineEnd = end.clone().add(offset);
                        Vector midpoint = lineStart.clone().add(lineEnd).multiply(0.5);
                        entities.add(spawnStick(world, worldLocation(origin, lineStart, midpoint),
                                midpoint.clone().subtract(lineStart), lineStart.distance(midpoint),
                                plugin.config().bondWidth(), firstColor).getUniqueId());
                        entities.add(spawnStick(world, worldLocation(origin, midpoint, lineEnd),
                                lineEnd.clone().subtract(midpoint), midpoint.distance(lineEnd),
                                plugin.config().bondWidth(), secondColor).getUniqueId());
                    }
                    renderedBonds++;
                }
            }

            List<ViewSession.AtomMarker> selectedMarkers = new ArrayList<>();
            for (int index : session.selectedAtoms()) {
                if (index >= 0 && index < markers.size()) {
                    selectedMarkers.add(markers.get(index));
                }
            }
            if (selectedMarkers.size() >= 2) {
                for (int index = 1; index < selectedMarkers.size(); index++) {
                    Vector start = selectedMarkers.get(index - 1).position().clone();
                    Vector end = selectedMarkers.get(index).position().clone();
                    Vector direction = end.clone().subtract(start);
                    double length = direction.length();
                    if (length < 1.0e-6) {
                        continue;
                    }
                    entities.add(spawnStick(world, worldLocation(origin, start, end), direction, length,
                            0.05, new int[] {255, 235, 59}).getUniqueId());
                }
                String text;
                Vector labelPosition;
                if (selectedMarkers.size() == 2) {
                    text = String.format(Locale.ROOT, "%.3f Å",
                            MolecularGeometry.distance(selectedMarkers.get(0).position(),
                                    selectedMarkers.get(1).position(), scale));
                    labelPosition = selectedMarkers.get(0).position().clone()
                            .add(selectedMarkers.get(1).position()).multiply(0.5)
                            .add(new Vector(0, 0.35, 0));
                } else if (selectedMarkers.size() == 3) {
                    text = String.format(Locale.ROOT, "%.1f°",
                            MolecularGeometry.angle(selectedMarkers.get(0).position(),
                                    selectedMarkers.get(1).position(),
                                    selectedMarkers.get(2).position()));
                    labelPosition = selectedMarkers.get(1).position().clone().add(new Vector(0, 0.35, 0));
                } else {
                    text = String.format(Locale.ROOT, "%.1f°",
                            MolecularGeometry.dihedral(selectedMarkers.get(0).position(),
                                    selectedMarkers.get(1).position(),
                                    selectedMarkers.get(2).position(),
                                    selectedMarkers.get(3).position()));
                    labelPosition = selectedMarkers.get(1).position().clone()
                            .add(selectedMarkers.get(2).position()).multiply(0.5)
                            .add(new Vector(0, 0.35, 0));
                }
                Location labelLocation = new Location(world,
                        origin.getX() + labelPosition.getX(),
                        origin.getY() + labelPosition.getY(),
                        origin.getZ() + labelPosition.getZ());
                TextDisplay measurement = spawn(world, labelLocation,
                        Component.text(text, TextColor.color(255, 235, 59)),
                        Color.fromARGB(190, 0, 0, 0), 0.7f, false);
                entities.add(measurement.getUniqueId());
            }

            if (!positions.isEmpty()) {
                double width = Math.max(maxX - minX, maxZ - minZ);
                double height = maxY - minY;
                Location interactionLocation = new Location(world,
                        origin.getX() + (minX + maxX) / 2.0,
                        origin.getY() + minY,
                        origin.getZ() + (minZ + maxZ) / 2.0);
                Interaction interaction = spawnInteraction(world, interactionLocation, width, height,
                        ownerId, session.id());
                entities.add(interaction.getUniqueId());
            }

            if (plugin.config().showInfo()) {
                Location infoLocation = new Location(world, origin.getX(),
                        origin.getY() + renderedMaxY + 1.5, origin.getZ());
                StringBuilder info = new StringBuilder()
                        .append(session.moleculeSet().name()).append('\n')
                        .append(molecule.contents()).append("  (")
                        .append(atomCount).append(" atoms, ")
                        .append(molecule.bonds().size()).append(" bonds)");
                if (session.frameCount() > 1) {
                    info.append('\n').append(frameText(session));
                }
                Double energy = molecule.energy();
                if (energy != null) {
                    info.append('\n').append(String.format(Locale.ROOT, "E = %.6f Ha", energy));
                    Double firstEnergy = session.moleculeSet().frame(0).energy();
                    if (session.frameCount() > 1 && firstEnergy != null) {
                        info.append(String.format(Locale.ROOT, "  (dE = %+.1f kJ/mol)",
                                (energy - firstEnergy) * 2625.499639));
                    }
                }
                Double dipole = session.moleculeSet().dipoleDebye();
                Integer nImag = session.moleculeSet().nImag();
                if (dipole != null || nImag != null) {
                    info.append('\n');
                    if (dipole != null) {
                        info.append(String.format(Locale.ROOT, "|dipole| = %.3f D", dipole));
                    }
                    if (nImag != null) {
                        info.append(String.format(Locale.ROOT, "  NImag = %d", nImag));
                    }
                }
                info.append('\n').append(String.format(Locale.ROOT,
                        "%.2f blocks/A  atom scale %.2f", scale,
                        session.spaceFilling() ? session.spaceFillingScale() : session.atomScale()));
                TextDisplay infoDisplay = spawn(world, infoLocation, Component.text(info.toString()),
                        Color.fromARGB(210, 0, 0, 0), 0.9f, false);
                entities.add(infoDisplay.getUniqueId());
            }
        } catch (RuntimeException e) {
            for (UUID id : entities) {
                interactions.remove(id);
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
            }
            throw e;
        }

        retainChunks(world, session, origin, minX, maxX, minZ, maxZ);
        session.entities(List.copyOf(entities));
        session.renderedAtoms(atomCount);
    }

    private Location worldLocation(Location origin, Vector start, Vector end) {
        return new Location(origin.getWorld(),
                origin.getX() + (start.getX() + end.getX()) / 2.0,
                origin.getY() + (start.getY() + end.getY()) / 2.0,
                origin.getZ() + (start.getZ() + end.getZ()) / 2.0);
    }

    private void retainChunks(World world, ViewSession session, Location origin,
            double minX, double maxX, double minZ, double maxZ) {
        int minChunkX = (int) Math.floor((origin.getX() + minX) / 16.0);
        int maxChunkX = (int) Math.floor((origin.getX() + maxX) / 16.0);
        int minChunkZ = (int) Math.floor((origin.getZ() + minZ) / 16.0);
        int maxChunkZ = (int) Math.floor((origin.getZ() + maxZ) / 16.0);
        Map<Long, Integer> counts = chunkTickets.computeIfAbsent(world.getUID(), id -> new HashMap<>());
        Set<Long> chunks = new HashSet<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long key = chunkKey(chunkX, chunkZ);
                chunks.add(key);
                int count = counts.getOrDefault(key, 0);
                if (count == 0) {
                    world.addPluginChunkTicket(chunkX, chunkZ, plugin);
                }
                counts.put(key, count + 1);
            }
        }
        session.chunks(chunks);
    }

    private void releaseChunks(ViewSession session) {
        World world = session.origin().getWorld();
        if (world == null) {
            session.chunks(Set.of());
            return;
        }
        Map<Long, Integer> counts = chunkTickets.get(world.getUID());
        if (counts == null) {
            session.chunks(Set.of());
            return;
        }
        for (long key : session.chunks()) {
            int count = counts.getOrDefault(key, 0) - 1;
            if (count <= 0) {
                counts.remove(key);
                world.removePluginChunkTicket(chunkX(key), chunkZ(key), plugin);
            } else {
                counts.put(key, count);
            }
        }
        if (counts.isEmpty()) {
            chunkTickets.remove(world.getUID());
        }
        session.chunks(Set.of());
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private int chunkX(long key) {
        return (int) (key >> 32);
    }

    private int chunkZ(long key) {
        return (int) key;
    }

    private int removeEntities(ViewSession session) {
        int removed = 0;
        for (UUID id : session.entities()) {
            interactions.remove(id);
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
                removed++;
            }
        }
        session.entities(List.of());
        return removed;
    }

    private void removeTaggedEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : List.copyOf(world.getEntities())) {
                if (entity.getScoreboardTags().contains(TAG)) {
                    entity.remove();
                }
            }
        }
    }

    private Location placement(Player player, McMolViewConfig config) {
        return placement(player, config, 0);
    }

    private Location placement(Player player, McMolViewConfig config, int slot) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().clone();
        if (direction.lengthSquared() < 1.0e-9) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize();

        double distance = config.placementDistance();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, direction, distance,
                FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitPosition() != null) {
            double hitDistance = eye.toVector().distance(hit.getHitPosition());
            distance = Math.max(2.0, hitDistance - 1.0);
        }

        Location origin = eye.clone().add(direction.multiply(distance));
        origin.setY(player.getLocation().getY());
        if (slot > 0) {
            Vector horizontal = eye.getDirection().clone();
            horizontal.setY(0);
            if (horizontal.lengthSquared() < 1.0e-9) {
                horizontal = new Vector(0, 0, 1);
            }
            horizontal.normalize();
            Vector right = horizontal.clone().crossProduct(new Vector(0, 1, 0)).normalize();
            int column = slot % 3;
            int row = slot / 3;
            origin.add(right.multiply(12.0 * column));
            origin.add(horizontal.multiply(-12.0 * row));
        }
        return origin;
    }

    private void rotate(Vector vector, double rotationX, double rotationY, double rotationZ) {
        vector.rotateAroundX(rotationX);
        vector.rotateAroundY(rotationY);
        vector.rotateAroundZ(rotationZ);
    }

    private ItemDisplay spawnStick(World world, Location location, Vector direction, double length,
            double thickness, int[] rgb) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("mcmolview", "stick"));
        item.setData(DataComponentTypes.DYED_COLOR,
                DyedItemColor.dyedItemColor(Color.fromRGB(rgb[0], rgb[1], rgb[2])));
        return world.spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(item);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setViewRange(1.5f);
            entity.setPersistent(false);
            entity.setTransformation(new Transformation(
                    new Vector3f(),
                    rotationTo(direction),
                    new Vector3f((float) thickness, (float) Math.max(0.01, length), (float) thickness),
                    new AxisAngle4f()));
            entity.addScoreboardTag(TAG);
        });
    }

    private AxisAngle4f rotationTo(Vector direction) {
        Vector target = direction.clone().normalize();
        double dot = target.getY();
        if (dot > 1.0 - 1.0e-9) {
            return new AxisAngle4f();
        }
        if (dot < -1.0 + 1.0e-9) {
            return new AxisAngle4f((float) Math.PI, 1.0f, 0.0f, 0.0f);
        }
        Vector axis = new Vector(0, 1, 0).crossProduct(target).normalize();
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
        return new AxisAngle4f((float) angle,
                (float) axis.getX(), (float) axis.getY(), (float) axis.getZ());
    }

    private Interaction spawnInteraction(World world, Location location, double width, double height,
            UUID playerId, int modelId) {
        Interaction interaction = world.spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth((float) Math.max(0.5, width));
            entity.setInteractionHeight((float) Math.max(0.5, height));
            entity.setResponsive(true);
            entity.setPersistent(false);
            entity.addScoreboardTag(TAG);
        });
        interactions.put(interaction.getUniqueId(), new ModelRef(playerId, modelId));
        return interaction;
    }

    private ItemDisplay spawnItem(World world, Location location, int[] rgb, float scale) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("mcmolview", "sphere"));
        item.setData(DataComponentTypes.DYED_COLOR,
                DyedItemColor.dyedItemColor(Color.fromRGB(rgb[0], rgb[1], rgb[2])));
        return world.spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(item);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setViewRange(1.5f);
            entity.setPersistent(false);
            entity.setTransformation(new Transformation(
                    new Vector3f(),
                    new AxisAngle4f(),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f()));
            entity.addScoreboardTag(TAG);
        });
    }

    private TextDisplay spawn(World world, Location location, Component text, Color background, float scale,
            boolean shadowed) {
        return world.spawn(location, TextDisplay.class, entity -> {
            entity.text(text);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(false);
            entity.setShadowed(shadowed);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(background);
            entity.setLineWidth(300);
            entity.setViewRange(1.5f);
            entity.setPersistent(false);
            entity.setTransformation(new Transformation(
                    new Vector3f(),
                    new AxisAngle4f(),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f()));
            entity.addScoreboardTag(TAG);
        });
    }

    private ViewSession requireSession(Player player) {
        ViewSession session = session(player);
        if (session == null) {
            throw new IllegalStateException("分子が表示されていません。先に /mol view <file> を実行してください。");
        }
        return session;
    }

    private ViewSession requireEditableSession(Player player) {
        ViewSession session = requireSession(player);
        if (!canEdit(player, session)) {
            throw new IllegalStateException("このモデルを編集する権限がありません。");
        }
        return session;
    }

    private int luminance(int[] rgb) {
        return (int) Math.round(0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]);
    }

    private double normalize(double angle) {
        double normalized = angle % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }


    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Playback {
        private final int sessionId;
        private int intervalTicks;
        private boolean loop;
        private BukkitTask task;

        private Playback(int sessionId, int intervalTicks, boolean loop) {
            this.sessionId = sessionId;
            this.intervalTicks = intervalTicks;
            this.loop = loop;
        }
    }
}
