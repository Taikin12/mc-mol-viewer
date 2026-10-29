package dev.taikin.mcplugins.mcmolview;

import java.io.IOException;
import java.nio.file.Files;
import org.bukkit.plugin.java.JavaPlugin;

public final class McMolViewPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            Files.createDirectories(getDataFolder().toPath().resolve("molecules"));
        } catch (IOException e) {
            getLogger().severe("Could not create molecules directory: " + e.getMessage());
        }
        getLogger().info("McMolView enabled.");
    }
}
