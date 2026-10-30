package dev.taikin.mcplugins.mcmolview.render;

import dev.taikin.mcplugins.mcmolview.model.Molecule;
import dev.taikin.mcplugins.mcmolview.model.MoleculeSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ModelStore {

    public record LoadResult(ModelRegistry registry, boolean migrated) {
    }

    private ModelStore() {
    }

    public static void save(Path file, ModelRegistry registry) throws IOException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("version", 1);
        configuration.set("next-id", registry.nextId());
        ConfigurationSection modelsSection = configuration.createSection("models");
        for (ViewSession session : registry.models()) {
            ConfigurationSection section = modelsSection.createSection(Integer.toString(session.id()));
            writeSession(section, session);
        }
        ConfigurationSection activeSection = configuration.createSection("active");
        for (Map.Entry<UUID, Integer> entry : registry.activeMap().entrySet()) {
            activeSection.set(entry.getKey().toString(), entry.getValue());
        }
        File parent = file.toFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        configuration.save(file.toFile());
    }

    public static LoadResult load(Path file, Logger logger) {
        ModelRegistry registry = new ModelRegistry();
        if (!file.toFile().isFile()) {
            return new LoadResult(registry, false);
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file.toFile());
        if (!configuration.isConfigurationSection("models")) {
            return new LoadResult(registry, false);
        }
        registry.nextId(configuration.getInt("next-id", 1));
        ConfigurationSection modelsSection = configuration.getConfigurationSection("models");
        if (modelsSection != null) {
            for (String modelKey : modelsSection.getKeys(false)) {
                int modelId;
                try {
                    modelId = Integer.parseInt(modelKey);
                } catch (NumberFormatException e) {
                    logger.warning("Invalid model id in models.yml: " + modelKey);
                    continue;
                }
                ConfigurationSection section = modelsSection.getConfigurationSection(modelKey);
                if (section == null) {
                    continue;
                }
                try {
                    registry.restore(modelId, readSession(section));
                } catch (RuntimeException e) {
                    logger.warning("Could not restore model " + modelKey + ": " + e.getMessage());
                }
            }
        }
        ConfigurationSection activeSection = configuration.getConfigurationSection("active");
        if (activeSection != null) {
            for (String playerKey : activeSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(playerKey);
                    registry.setActive(playerId, activeSection.getInt(playerKey));
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid player UUID in models.yml active section: " + playerKey);
                }
            }
        }
        return new LoadResult(registry, false);
    }

    private static void writeSession(ConfigurationSection section, ViewSession session) {
        section.set("owner", session.owner() == null ? "" : session.owner().toString());
        section.set("shared-edit", session.sharedEdit());
        section.set("molecule.name", session.moleculeSet().name());
        section.set("molecule.dipole-debye", session.moleculeSet().dipoleDebye());
        section.set("molecule.nimag", session.moleculeSet().nImag());
        List<Map<String, Object>> frames = new ArrayList<>(session.frameCount());
        for (Molecule molecule : session.moleculeSet().frames()) {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("name", molecule.name());
            frame.put("energy", molecule.energy());
            List<Map<String, Object>> atoms = new ArrayList<>(molecule.atoms().size());
            for (Molecule.Atom atom : molecule.atoms()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("element", atom.element());
                map.put("x", atom.x());
                map.put("y", atom.y());
                map.put("z", atom.z());
                map.put("charge", atom.charge());
                map.put("label", atom.label());
                atoms.add(map);
            }
            frame.put("atoms", atoms);
            List<Map<String, Object>> bonds = new ArrayList<>(molecule.bonds().size());
            for (Molecule.Bond bond : molecule.bonds()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("first", bond.first());
                map.put("second", bond.second());
                map.put("order", bond.order());
                bonds.add(map);
            }
            frame.put("bonds", bonds);
            frames.add(frame);
        }
        section.set("molecule.frames", frames);

        Location origin = session.origin();
        section.set("world", origin.getWorld().getUID().toString());
        section.set("origin.x", origin.getX());
        section.set("origin.y", origin.getY());
        section.set("origin.z", origin.getZ());
        section.set("blocks-per-angstrom", session.blocksPerAngstrom());
        section.set("atom-scale", session.atomScale());
        section.set("space-filling-scale", session.spaceFillingScale());
        section.set("frame", session.frameIndex());
        section.set("rotation-x", session.rotationX());
        section.set("rotation-y", session.rotationY());
        section.set("rotation-z", session.rotationZ());
        section.set("rotation-axis", session.rotationAxis().name());
        section.set("space-filling", session.spaceFilling());
        section.set("bonds", session.showBonds());
        section.set("show-labels", session.showLabels());
        section.set("radius-mode", session.radiusMode().name());
    }

    private static ViewSession readSession(ConfigurationSection section) {
        String worldId = section.getString("world", "");
        World world;
        try {
            world = world(UUID.fromString(worldId));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("invalid world UUID");
        }
        if (world == null) {
            throw new IllegalStateException("world is not loaded");
        }

        UUID owner = null;
        String ownerText = section.getString("owner", "");
        if (!ownerText.isBlank()) {
            try {
                owner = UUID.fromString(ownerText);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("invalid owner UUID");
            }
        }
        if (owner == null) {
            throw new IllegalStateException("missing owner");
        }

        String name = section.getString("molecule.name", "Molecule");
        List<Molecule> frames = new ArrayList<>();
        for (Map<?, ?> frameMap : section.getMapList("molecule.frames")) {
            String frameName = frameMap.get("name") == null
                    ? name : String.valueOf(frameMap.get("name"));
            List<Molecule.Atom> atoms = new ArrayList<>();
            Object atomValues = frameMap.get("atoms");
            if (atomValues instanceof List<?> list) {
                for (Object value : list) {
                    if (!(value instanceof Map<?, ?> map)) {
                        continue;
                    }
                    atoms.add(new Molecule.Atom(String.valueOf(map.get("element")),
                            number(map.get("x")), number(map.get("y")), number(map.get("z")),
                            (int) Math.round(number(map.get("charge"))),
                            map.get("label") == null ? null : String.valueOf(map.get("label"))));
                }
            }
            List<Molecule.Bond> bonds = new ArrayList<>();
            Object bondValues = frameMap.get("bonds");
            if (bondValues instanceof List<?> list) {
                for (Object value : list) {
                    if (!(value instanceof Map<?, ?> map)) {
                        continue;
                    }
                    bonds.add(new Molecule.Bond((int) Math.round(number(map.get("first"))),
                            (int) Math.round(number(map.get("second"))),
                            (int) Math.round(number(map.get("order")))));
                }
            }
            Double energy = frameMap.get("energy") instanceof Number number
                    ? number.doubleValue() : null;
            if (!atoms.isEmpty()) {
                frames.add(new Molecule(frameName, atoms, bonds, energy));
            }
        }
        if (frames.isEmpty()) {
            throw new IllegalStateException("no atoms");
        }
        Double dipole = section.isDouble("molecule.dipole-debye")
                ? section.getDouble("molecule.dipole-debye") : null;
        Integer nImag = section.isInt("molecule.nimag")
                ? section.getInt("molecule.nimag") : null;
        MoleculeSet set = new MoleculeSet(name, frames, dipole, nImag);

        Location origin = new Location(world,
                section.getDouble("origin.x"),
                section.getDouble("origin.y"),
                section.getDouble("origin.z"));
        ViewSession session = new ViewSession(set, origin,
                section.getDouble("blocks-per-angstrom", 2.0),
                section.getDouble("atom-scale", 1.0),
                section.getBoolean("bonds", true),
                section.getBoolean("show-labels", false));
        session.owner(owner);
        session.sharedEdit(section.getBoolean("shared-edit", false));
        session.frameIndex(section.getInt("frame", 0));
        session.rotation(
                section.getDouble("rotation-x"),
                section.getDouble("rotation-y"),
                section.getDouble("rotation-z"));
        try {
            session.rotationAxis(ViewSession.Axis.valueOf(
                    section.getString("rotation-axis", "Y").toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            session.rotationAxis(ViewSession.Axis.Y);
        }
        session.spaceFillingScale(section.getDouble("space-filling-scale", 1.0));
        session.spaceFilling(section.getBoolean("space-filling", false));
        try {
            session.radiusMode(ViewSession.RadiusMode.valueOf(
                    section.getString("radius-mode", "VDW").toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            session.radiusMode(ViewSession.RadiusMode.VDW);
        }
        return session;
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static World world(UUID id) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getUID().equals(id)) {
                return world;
            }
        }
        return null;
    }
}
