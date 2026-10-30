package dev.taikin.mcplugins.mcmolview;

import dev.taikin.mcplugins.mcmolview.gui.MolControlGui;
import dev.taikin.mcplugins.mcmolview.gui.MolFrameGui;
import dev.taikin.mcplugins.mcmolview.gui.MolModelListGui;
import dev.taikin.mcplugins.mcmolview.render.MoleculeRenderer;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.packs.ResourcePack;
import org.bukkit.plugin.java.JavaPlugin;

public final class McMolViewPlugin extends JavaPlugin {

    private volatile McMolViewConfig config;
    private MoleculeRepository repository;
    private MoleculeRenderer renderer;
    private MolWand wand;
    private MolControlGui controlGui;
    private MolFrameGui frameGui;
    private MolModelListGui modelListGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            Files.createDirectories(getDataFolder().toPath().resolve("molecules"));
        } catch (IOException e) {
            getLogger().severe("Could not create molecules directory: " + e.getMessage());
        }

        this.config = new McMolViewConfig(getConfig(), getDataFolder(), getLogger());
        this.repository = new MoleculeRepository(this);
        this.repository.reload();
        this.renderer = new MoleculeRenderer(this);
        this.renderer.removeOrphans();
        getServer().getScheduler().runTaskLater(this, renderer::load, 20L);
        this.wand = new MolWand(this);
        this.controlGui = new MolControlGui(this);
        this.frameGui = new MolFrameGui(this);
        this.modelListGui = new MolModelListGui(this);

        MolCommand command = new MolCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.node(), "View molecular structures",
                        List.of("molview")));

        getServer().getPluginManager().registerEvents(new MolListener(this), this);

        getLogger().info("McMolView enabled: " + repository.keys().size() + " file(s).");
    }

    @Override
    public void onDisable() {
        if (renderer != null) {
            renderer.stopAllPlayback();
            renderer.save();
            renderer.clearAll();
        }
        getLogger().info("McMolView disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        this.config = new McMolViewConfig(getConfig(), getDataFolder(), getLogger());
        this.repository.reload();
    }

    public boolean sendResourcePack(Player player) {
        ResourcePack pack = getServer().getServerResourcePack();
        if (pack == null || pack.getUrl() == null || pack.getUrl().isBlank()) {
            player.sendMessage(Component.text("サーバーのリソースパックが設定されていません。",
                    NamedTextColor.RED));
            return false;
        }
        player.setResourcePack(pack.getUrl(), pack.getHash(), false,
                Component.text("Mol Viewer 3D球体モデル"));
        player.sendMessage(Component.text("リソースパック: ", NamedTextColor.GOLD)
                .append(Component.text(pack.getUrl(), NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.openUrl(pack.getUrl()))
                        .hoverEvent(HoverEvent.showText(Component.text("クリックでダウンロード")))));
        player.sendMessage(Component.text(".minecraft/resourcepacks にzipを入れても適用できます。",
                NamedTextColor.GRAY));
        return true;
    }

    public McMolViewConfig config() {
        return config;
    }

    public MoleculeRepository repository() {
        return repository;
    }

    public MoleculeRenderer renderer() {
        return renderer;
    }

    public MolWand wand() {
        return wand;
    }

    public MolControlGui controlGui() {
        return controlGui;
    }

    public MolFrameGui frameGui() {
        return frameGui;
    }

    public MolModelListGui modelListGui() {
        return modelListGui;
    }
}
