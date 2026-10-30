package dev.taikin.mcplugins.mcmolview;

import dev.taikin.miviewer.common.ElementInfo;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class McMolViewConfig {

    public enum RenderMode {
        MODEL,
        CIRCLE,
        SYMBOL
    }

    private final List<Path> sourceDirectories;
    private final int scanDepth;
    private final double blocksPerAngstrom;
    private final double atomScale;
    private final int maxAtoms;
    private final int maxModels;
    private final double placementDistance;
    private final boolean showInfo;
    private final boolean defaultShowLabels;
    private final boolean defaultShowBonds;
    private final double bondWidth;
    private final RenderMode renderMode;
    private final boolean sphereShadow;
    private final Map<String, int[]> atomColors;
    private final int[] defaultAtomColor;

    public McMolViewConfig(FileConfiguration configuration, File dataFolder, Logger logger) {
        List<String> configuredSources = configuration.getStringList("sources");
        if (configuredSources.isEmpty()) {
            configuredSources = List.of("molecules");
        }

        List<Path> sources = new ArrayList<>();
        for (String configured : configuredSources) {
            if (configured == null || configured.isBlank()) {
                continue;
            }
            String value = configured.trim();
            if (value.equals("~")) {
                value = System.getProperty("user.home");
            } else if (value.startsWith("~/")) {
                value = System.getProperty("user.home") + value.substring(1);
            }
            Path path = Path.of(value);
            if (!path.isAbsolute()) {
                path = dataFolder.toPath().resolve(path);
            }
            sources.add(path.normalize().toAbsolutePath());
        }
        this.sourceDirectories = List.copyOf(sources);

        this.scanDepth = clamp(configuration.getInt("scan-depth", 4), 1, 16);
        this.blocksPerAngstrom = clamp(configuration.getDouble("blocks-per-angstrom", 2.0), 0.1, 10.0);
        this.atomScale = clamp(configuration.getDouble("atom-scale", 1.0), 0.2, 6.0);
        this.maxAtoms = clamp(configuration.getInt("max-atoms", 1000), 1, 20000);
        this.maxModels = clamp(configuration.getInt("max-models", 8), 1, 27);
        this.placementDistance = clamp(configuration.getDouble("placement-distance", 6.0), 2.0, 32.0);
        this.showInfo = configuration.getBoolean("show-info", true);
        this.defaultShowLabels = configuration.getBoolean("show-atom-labels", false);
        this.defaultShowBonds = configuration.getBoolean("show-bonds", true);
        this.bondWidth = clamp(configuration.getDouble("bond-width", 0.08), 0.02, 0.3);
        String renderModeName = configuration.getString("render-mode", "sphere").trim();
        this.renderMode = switch (renderModeName.toLowerCase(Locale.ROOT)) {
            case "sphere", "model" -> RenderMode.MODEL;
            case "circle" -> RenderMode.CIRCLE;
            case "symbol", "text" -> RenderMode.SYMBOL;
            default -> {
                logger.warning("Unknown render-mode '" + renderModeName + "', using sphere.");
                yield RenderMode.MODEL;
            }
        };
        this.sphereShadow = configuration.getBoolean("sphere-shadow", true);

        Map<String, int[]> colors = new LinkedHashMap<>();
        ConfigurationSection colorSection = configuration.getConfigurationSection("atom-colors");
        if (colorSection != null) {
            for (String key : colorSection.getKeys(false)) {
                String element = ElementInfo.elementOf(key, key);
                if (element == null || !ElementInfo.isElement(element)) {
                    logger.warning("Unknown element in atom-colors: " + key);
                    continue;
                }
                int[] color = parseColor(colorSection.getString(key), logger, "atom-colors." + key);
                if (color != null) {
                    colors.put(element, color);
                }
            }
        }
        this.atomColors = Map.copyOf(colors);
        this.defaultAtomColor = parseColor(configuration.getString("default-atom-color", ""),
                logger, "default-atom-color");
    }

    public List<Path> sourceDirectories() {
        return sourceDirectories;
    }

    public int scanDepth() {
        return scanDepth;
    }

    public double blocksPerAngstrom() {
        return blocksPerAngstrom;
    }

    public double atomScale() {
        return atomScale;
    }

    public int maxAtoms() {
        return maxAtoms;
    }

    public int maxModels() {
        return maxModels;
    }

    public double placementDistance() {
        return placementDistance;
    }

    public boolean showInfo() {
        return showInfo;
    }

    public boolean defaultShowLabels() {
        return defaultShowLabels;
    }

    public boolean defaultShowBonds() {
        return defaultShowBonds;
    }

    public double bondWidth() {
        return bondWidth;
    }

    public RenderMode renderMode() {
        return renderMode;
    }

    public boolean sphereShadow() {
        return sphereShadow;
    }

    public int[] atomColor(String element) {
        int[] color = atomColors.get(element);
        if (color != null) {
            return color;
        }
        if (defaultAtomColor != null) {
            return defaultAtomColor;
        }
        return ElementInfo.color(element);
    }

    private static int[] parseColor(String value, Logger logger, String key) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }

        String hex = text;
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (hex.matches("[0-9a-fA-F]{6}")) {
            int rgb = Integer.parseInt(hex, 16);
            return new int[] {(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
        }

        NamedTextColor named = NamedTextColor.NAMES.value(text.toLowerCase(Locale.ROOT));
        if (named != null) {
            int rgb = named.value();
            return new int[] {(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
        }

        logger.warning("Invalid color for " + key + ": " + value);
        return null;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
