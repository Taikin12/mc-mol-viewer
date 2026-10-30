package dev.taikin.mcplugins.mcmolview;

import dev.taikin.miviewer.common.WandItems;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class MolWand {

    public enum WandType {
        MENU,
        VIEW,
        ATOM,
        FRAME
    }

    private final NamespacedKey legacyTypeKey;

    public MolWand(McMolViewPlugin plugin) {
        this.legacyTypeKey = new NamespacedKey(plugin, "wand-type");
    }

    public ItemStack create(WandType type) {
        String name = switch (type) {
            case MENU -> "分子メニュー棒";
            case VIEW -> "分子ビュー棒";
            case ATOM -> "分子原子棒";
            case FRAME -> "分子フレーム棒";
        };
        NamedTextColor color = switch (type) {
            case MENU -> NamedTextColor.GOLD;
            case VIEW -> NamedTextColor.YELLOW;
            case ATOM -> NamedTextColor.AQUA;
            case FRAME -> NamedTextColor.LIGHT_PURPLE;
        };
        List<Component> lore = switch (type) {
            case MENU -> List.of(
                    lore("右クリック: メニュー"),
                    lore("モデルをクリック: 選択 / メニュー"));
            case VIEW -> List.of(
                    lore("左クリック: 選択軸で+15°回転"),
                    lore("右クリック: -15°回転"),
                    lore("スニーク+左クリック(ブロック面): 回転軸変更"),
                    lore("スニーク+左クリック(空): 回転軸を切替"),
                    lore("スニーク+右クリック(ブロック面): 原点設定"),
                    lore("スニーク+スクロール: 全体サイズ"));
            case ATOM -> List.of(
                    lore("右クリック: 測定原子を選択/追加"),
                    lore("左クリック: 測定選択を解除"),
                    lore("2原子: 距離 / 3原子: 角度 / 4原子: 二面角"));
            case FRAME -> List.of(
                    lore("右クリック: 次の構造フレーム"),
                    lore("左クリック: 前の構造フレーム"),
                    lore("スニーク+右/左: 最後 / 最初のフレーム"));
        };
        return WandItems.create(shared(type), WandItems.ViewerTarget.MOL,
                Component.text(name, color), lore);
    }

    public WandType type(ItemStack item) {
        WandItems.WandType shared = WandItems.type(item);
        if (shared != null) {
            return local(shared);
        }
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String configured = item.getItemMeta().getPersistentDataContainer()
                .get(legacyTypeKey, PersistentDataType.STRING);
        if (configured == null) {
            return null;
        }
        try {
            return WandType.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public WandItems.ViewerTarget target(ItemStack item) {
        return WandItems.target(item);
    }

    public boolean isWand(ItemStack item) {
        return type(item) != null;
    }

    public boolean has(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isWand(item)) {
                return true;
            }
        }
        return false;
    }

    public boolean has(Player player, WandType type) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (type(item) == type) {
                return true;
            }
        }
        return false;
    }

    public boolean giveIfMissing(Player player) {
        return giveIfMissing(player, WandType.MENU);
    }

    public boolean giveIfMissing(Player player, WandType type) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (type(item) != type) {
                continue;
            }
            ItemStack updated = item.clone();
            WandItems.migrate(updated, shared(type), WandItems.ViewerTarget.MOL);
            player.getInventory().setItem(slot, updated);
            return false;
        }
        give(player, type);
        return true;
    }

    public void give(Player player) {
        give(player, WandType.MENU);
    }

    public void give(Player player, WandType type) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(create(type));
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    public void refreshAppearance(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            WandType type = type(item);
            if (type == null) {
                continue;
            }
            WandItems.ViewerTarget target = WandItems.target(item);
            if (target == null) {
                target = WandItems.ViewerTarget.MOL;
            }
            ItemStack updated = item.clone();
            WandItems.migrate(updated, shared(type), target);
            player.getInventory().setItem(slot, updated);
        }
    }

    private static WandItems.WandType shared(WandType type) {
        return switch (type) {
            case MENU -> WandItems.WandType.MENU;
            case VIEW -> WandItems.WandType.VIEW;
            case ATOM -> WandItems.WandType.ATOM;
            case FRAME -> WandItems.WandType.FRAME;
        };
    }

    private static WandType local(WandItems.WandType type) {
        return switch (type) {
            case MENU -> WandType.MENU;
            case VIEW -> WandType.VIEW;
            case ATOM -> WandType.ATOM;
            case FRAME -> WandType.FRAME;
        };
    }

    private Component lore(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
