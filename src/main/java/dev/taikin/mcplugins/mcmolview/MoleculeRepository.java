package dev.taikin.mcplugins.mcmolview;

import dev.taikin.mcplugins.mcmolview.mol.MoleculeParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class MoleculeRepository {

    private final McMolViewPlugin plugin;
    private final Map<String, Path> files = new LinkedHashMap<>();
    private final List<Path> roots = new ArrayList<>();

    public MoleculeRepository(McMolViewPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        files.clear();
        roots.clear();

        McMolViewConfig config = plugin.config();
        for (Path source : config.sourceDirectories()) {
            if (Files.isRegularFile(source)) {
                roots.add(source);
                if (MoleculeParser.isSupported(source.getFileName().toString())) {
                    files.putIfAbsent(source.getFileName().toString(), source);
                }
                continue;
            }
            if (!Files.isDirectory(source)) {
                plugin.getLogger().warning("Molecule source does not exist or is not a directory: "
                        + source);
                continue;
            }
            roots.add(source);
            try (Stream<Path> stream = Files.walk(source, config.scanDepth())) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> MoleculeParser.isSupported(path.getFileName().toString()))
                        .sorted()
                        .forEach(path -> files.putIfAbsent(key(source, path), path));
            } catch (IOException e) {
                plugin.getLogger().warning("Could not scan molecule source " + source + ": "
                        + e.getMessage());
            }
        }
    }

    public List<String> keys() {
        return files.keySet().stream().sorted().toList();
    }

    public Path resolve(String name) {
        String normalized = name.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        Path exact = files.get(normalized);
        if (exact != null) {
            return exact;
        }

        String baseName = normalized.contains("/")
                ? normalized.substring(normalized.lastIndexOf('/') + 1)
                : normalized;
        List<Path> matches = files.entrySet().stream()
                .filter(entry -> entry.getKey().equals(baseName) || entry.getKey().endsWith("/" + baseName))
                .map(Map.Entry::getValue)
                .distinct()
                .toList();
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1) {
            return null;
        }

        try {
            Path absolute = Path.of(name).toAbsolutePath().normalize();
            if (!MoleculeParser.isSupported(absolute.getFileName().toString())
                    || !Files.isRegularFile(absolute)) {
                return null;
            }
            for (Path root : roots) {
                if (Files.isRegularFile(root) ? absolute.equals(root) : absolute.startsWith(root)) {
                    return absolute;
                }
            }
        } catch (InvalidPathException e) {
            return null;
        }
        return null;
    }

    private String key(Path root, Path file) {
        return root.relativize(file).toString().replace(File.separatorChar, '/');
    }
}
