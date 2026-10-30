package dev.taikin.mcplugins.mcmolview.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MolModelListGuiHolder implements InventoryHolder {

    private final UUID playerId;
    private Inventory inventory;

    public MolModelListGuiHolder(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
