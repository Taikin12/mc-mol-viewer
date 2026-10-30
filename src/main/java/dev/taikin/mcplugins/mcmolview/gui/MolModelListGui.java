package dev.taikin.mcplugins.mcmolview.gui;

import dev.taikin.mcplugins.mcmolview.McMolViewPlugin;
import dev.taikin.mcplugins.mcmolview.render.ViewSession;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MolModelListGui {

    private static final int SIZE = 36;
    private static final int CLEAR_ALL = 27;
    private static final int BACK = 35;

    private final McMolViewPlugin plugin;

    public MolModelListGui(McMolViewPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        List<ViewSession> models = plugin.renderer().models(player);
        if (models.isEmpty()) {
            player.sendMessage(Component.text("表示中のモデルはありません。", NamedTextColor.GRAY));
            return;
        }

        MolModelListGuiHolder holder = new MolModelListGuiHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                Component.text("Mol: モデル一覧", NamedTextColor.DARK_AQUA));
        holder.inventory(inventory);
        populate(inventory, player);
        player.openInventory(inventory);
    }

    public void handleClick(Player player, Inventory inventory, int slot, boolean shiftClick, boolean rightClick) {
        List<ViewSession> models = plugin.renderer().models(player);
        if (models.isEmpty()) {
            player.closeInventory();
            return;
        }

        if (slot >= 0 && slot < models.size()) {
            ViewSession session = models.get(slot);
            if (shiftClick) {
                if (!plugin.renderer().canDelete(player, session)) {
                    player.sendMessage(Component.text("このモデルを削除する権限がありません。",
                            NamedTextColor.RED));
                    return;
                }
                int removed = plugin.renderer().clear(player, session.id());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
                player.sendMessage(Component.text("モデル #" + session.id() + " を削除しました ("
                        + removed + " entities).", NamedTextColor.GREEN));
                if (plugin.renderer().models(player).isEmpty()) {
                    player.closeInventory();
                } else {
                    populate(inventory, player);
                }
                return;
            }
            plugin.renderer().select(player, session.id());
            if (rightClick) {
                try {
                    plugin.renderer().teleport(player, session.id());
                    player.closeInventory();
                    player.sendMessage(Component.text("モデル #" + session.id() + " の近くに移動しました。",
                            NamedTextColor.GREEN));
                } catch (RuntimeException e) {
                    player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
                }
                return;
            }
            plugin.controlGui().open(player);
            return;
        }

        if (slot == CLEAR_ALL) {
            if (!shiftClick) {
                player.sendMessage(Component.text("全削除するには Shift+クリックしてください。",
                        NamedTextColor.GRAY));
                return;
            }
            int removed = plugin.renderer().clearAll(player);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            player.sendMessage(Component.text("モデルを削除しました (" + removed + " entities).",
                    NamedTextColor.GREEN));
            if (plugin.renderer().models(player).isEmpty()) {
                player.closeInventory();
            } else {
                populate(inventory, player);
            }
            return;
        }

        if (slot == BACK) {
            plugin.controlGui().open(player);
        }
    }

    private void populate(Inventory inventory, Player player) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler());
        }

        List<ViewSession> models = plugin.renderer().models(player);
        int activeId = plugin.renderer().activeId(player);
        for (int index = 0; index < models.size() && index < CLEAR_ALL; index++) {
            ViewSession session = models.get(index);
            boolean active = session.id() == activeId;
            inventory.setItem(index, button(active ? Material.ENCHANTED_BOOK : Material.PAPER,
                    "モデル #" + session.id() + ": " + session.moleculeSet().name(),
                    List.of(
                            value("所有者: " + ownerName(session)),
                            value("共有編集: " + (session.sharedEdit() ? "ON" : "OFF")),
                            value("原子数: " + session.renderedAtoms()),
                            value("構造: " + plugin.renderer().frameText(session)),
                            value("座標: " + session.origin().getBlockX() + ", "
                                    + session.origin().getBlockY() + ", "
                                    + session.origin().getBlockZ()),
                            value(active ? "選択中" : "左クリック: 選択"),
                            value("右クリック: テレポート"),
                            value("Shift+左クリック: 削除"))));
        }

        String clearLabel = plugin.renderer().isAdmin(player) ? "全モデル消去" : "自分のモデル消去";
        inventory.setItem(CLEAR_ALL, button(Material.BARRIER, clearLabel,
                List.of(value("Shift+クリックで削除"))));
        inventory.setItem(BACK, button(Material.OAK_DOOR, "戻る", List.of(value("メインメニューへ"))));
    }

    private ItemStack button(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private Component value(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private String ownerName(ViewSession session) {
        if (session.owner() == null) {
            return "不明";
        }
        String name = Bukkit.getOfflinePlayer(session.owner()).getName();
        return name == null ? session.owner().toString() : name;
    }
}
