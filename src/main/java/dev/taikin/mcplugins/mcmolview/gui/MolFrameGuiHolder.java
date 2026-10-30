package dev.taikin.mcplugins.mcmolview.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MolFrameGuiHolder implements InventoryHolder {

    private final UUID playerId;
    private int page;
    private Inventory inventory;

    public MolFrameGuiHolder(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public int page() {
        return page;
    }

    void page(int page) {
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
