package dev.taikin.mcplugins.mcmolview.mol;

import dev.taikin.mcplugins.mcmolview.model.MoleculeSet;
import java.util.List;
import java.util.Locale;

public final class MoleculeParser {

    public static final List<String> EXTENSIONS = List.of("xyz", "mol", "sdf", "out", "log");

    public MoleculeSet parse(String name, String text) {
        String extension = extension(name);
        return switch (extension) {
            case "xyz" -> new XyzParser().parse(name, text);
            case "mol", "sdf" -> new MolParser().parse(name, text);
            case "out", "log" -> new OrcaParser().parse(name, text);
            default -> throw new IllegalArgumentException("Unsupported molecule file type: " + name);
        };
    }

    public static boolean isSupported(String name) {
        return EXTENSIONS.contains(extension(name));
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
