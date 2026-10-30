package dev.taikin.mcplugins.mcmolview.gui;

import dev.taikin.mcplugins.mcmolview.McMolViewPlugin;
import dev.taikin.mcplugins.mcmolview.render.ViewSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

public final class MolControlGui {

    public static final int SIZE = 36;

    private static final int MODELS = 0;
    private static final int INFO = 4;
    private static final int FRAME_PREV = 6;
    private static final int FRAME_NEXT = 7;
    private static final int SHARE = 8;
    private static final int SCALE_UP = 9;
    private static final int ATOM_SCALE_UP = 10;
    private static final int ROTATE_X_UP = 11;
    private static final int ROTATE_Y_UP = 12;
    private static final int ROTATE_Z_UP = 13;
    private static final int BONDS = 14;
    private static final int LABELS = 15;
    private static final int SPACE_FILLING = 16;
    private static final int RADIUS_MODE = 17;
    private static final int SCALE_DOWN = 18;
    private static final int ATOM_SCALE_DOWN = 19;
    private static final int ROTATE_X_DOWN = 20;
    private static final int ROTATE_Y_DOWN = 21;
    private static final int ROTATE_Z_DOWN = 22;
    private static final int ROTATION_AXIS = 23;
    private static final int RECENTER = 24;
    private static final int RESET = 25;
    private static final int PACK = 26;
    private static final int CLEAR = 27;
    private static final int FRAMES = 28;
    private static final int CLOSE = 35;

    private final McMolViewPlugin plugin;

    public MolControlGui(McMolViewPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        ViewSession session = plugin.renderer().session(player);
        if (session == null) {
            player.sendMessage(Component.text(
                    "分子が表示されていません。先に /mol view <file> を実行してください。",
                    NamedTextColor.RED));
            return;
        }

        MolGuiHolder holder = new MolGuiHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title(session));
        holder.inventory(inventory);
        populate(inventory, session);
        player.openInventory(inventory);
    }

    public void handleClick(Player player, Inventory inventory, int slot) {
        ViewSession session = plugin.renderer().session(player);
        if (session == null) {
            player.closeInventory();
            return;
        }

        try {
            switch (slot) {
                case SCALE_DOWN -> plugin.renderer().adjustScale(player, 1.0 / 1.15);
                case SCALE_UP -> plugin.renderer().adjustScale(player, 1.15);
                case ATOM_SCALE_UP -> plugin.renderer().adjustAtomScale(player, 1.15);
                case ATOM_SCALE_DOWN -> plugin.renderer().adjustAtomScale(player, 1.0 / 1.15);
                case ROTATE_X_DOWN -> plugin.renderer().rotate(player, -15.0, 0.0, 0.0);
                case ROTATE_X_UP -> plugin.renderer().rotate(player, 15.0, 0.0, 0.0);
                case ROTATE_Y_DOWN -> plugin.renderer().rotate(player, 0.0, -15.0, 0.0);
                case ROTATE_Y_UP -> plugin.renderer().rotate(player, 0.0, 15.0, 0.0);
                case ROTATE_Z_DOWN -> plugin.renderer().rotate(player, 0.0, 0.0, -15.0);
                case ROTATE_Z_UP -> plugin.renderer().rotate(player, 0.0, 0.0, 15.0);
                case FRAME_PREV -> {
                    player.sendActionBar(Component.text(plugin.renderer().adjustFrame(player, -1),
                            NamedTextColor.AQUA));
                }
                case FRAME_NEXT -> {
                    player.sendActionBar(Component.text(plugin.renderer().adjustFrame(player, 1),
                            NamedTextColor.AQUA));
                }
                case RECENTER -> plugin.renderer().replaceOrigin(player);
                case SPACE_FILLING -> plugin.renderer().toggleSpaceFilling(player);
                case BONDS -> plugin.renderer().toggleBonds(player);
                case SHARE -> plugin.renderer().toggleSharedEdit(player);
                case LABELS -> plugin.renderer().toggleLabels(player);
                case RADIUS_MODE -> plugin.renderer().cycleRadiusMode(player);
                case RESET -> plugin.renderer().reset(player);
                case ROTATION_AXIS -> plugin.renderer().cycleRotationAxis(player);
                case MODELS -> {
                    plugin.modelListGui().open(player);
                    return;
                }
                case FRAMES -> {
                    plugin.frameGui().open(player);
                    return;
                }
                case PACK -> {
                    plugin.sendResourcePack(player);
                    return;
                }
                case CLEAR -> {
                    int removed = plugin.renderer().clear(player);
                    if (removed == 0) {
                        player.sendMessage(Component.text("このモデルを削除する権限がありません。",
                                NamedTextColor.RED));
                        return;
                    }
                    player.closeInventory();
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
                    return;
                }
                case CLOSE -> {
                    player.closeInventory();
                    return;
                }
                default -> {
                    return;
                }
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
            ViewSession updated = plugin.renderer().session(player);
            if (updated != null) {
                populate(inventory, updated);
            }
        } catch (RuntimeException e) {
            player.sendMessage(Component.text(e.getMessage() == null ? "表示を更新できませんでした。"
                    : e.getMessage(), NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
        }
    }

    private void populate(Inventory inventory, ViewSession session) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler());
        }

        inventory.setItem(MODELS, button(Material.CHEST, "モデル一覧",
                List.of(value("複数モデルの選択 / 削除"), value("選択中: #" + session.id()))));
        inventory.setItem(INFO, info(session));
        inventory.setItem(FRAME_PREV, button(Material.SPECTRAL_ARROW, "前の構造",
                List.of(value(plugin.renderer().frameText(session)))));
        inventory.setItem(FRAME_NEXT, button(Material.SPECTRAL_ARROW, "次の構造",
                List.of(value(plugin.renderer().frameText(session)))));
        inventory.setItem(SHARE, shareButton(session));
        inventory.setItem(BONDS, bondsButton(session));
        inventory.setItem(LABELS, labelsButton(session));
        inventory.setItem(SPACE_FILLING, spaceFillingButton(session));
        inventory.setItem(RADIUS_MODE, radiusModeButton(session));

        inventory.setItem(SCALE_UP, button(Material.LIME_STAINED_GLASS_PANE, "全体サイズ +",
                List.of(value("ブロック/Å: " + number(session.blocksPerAngstrom())))));
        inventory.setItem(SCALE_DOWN, button(Material.RED_STAINED_GLASS_PANE, "全体サイズ -",
                List.of(value("ブロック/Å: " + number(session.blocksPerAngstrom())))));
        inventory.setItem(ATOM_SCALE_UP, button(Material.LIME_STAINED_GLASS_PANE, "原子サイズ +",
                List.of(value("原子倍率: " + number(displayAtomScale(session))))));
        inventory.setItem(ATOM_SCALE_DOWN, button(Material.RED_STAINED_GLASS_PANE, "原子サイズ -",
                List.of(value("原子倍率: " + number(displayAtomScale(session))))));

        inventory.setItem(ROTATE_X_UP, rotationButton(Material.LIME_STAINED_GLASS_PANE, "X軸回転 +",
                session.rotationX()));
        inventory.setItem(ROTATE_X_DOWN, rotationButton(Material.RED_STAINED_GLASS_PANE, "X軸回転 -",
                session.rotationX()));
        inventory.setItem(ROTATE_Y_UP, rotationButton(Material.LIME_STAINED_GLASS_PANE, "Y軸回転 +",
                session.rotationY()));
        inventory.setItem(ROTATE_Y_DOWN, rotationButton(Material.RED_STAINED_GLASS_PANE, "Y軸回転 -",
                session.rotationY()));
        inventory.setItem(ROTATE_Z_UP, rotationButton(Material.LIME_STAINED_GLASS_PANE, "Z軸回転 +",
                session.rotationZ()));
        inventory.setItem(ROTATE_Z_DOWN, rotationButton(Material.RED_STAINED_GLASS_PANE, "Z軸回転 -",
                session.rotationZ()));

        inventory.setItem(FRAMES, button(Material.CLOCK, "フレーム / 再生",
                List.of(value("フレーム一覧・再生・速度・ループ"),
                        value(plugin.renderer().frameText(session)))));
        inventory.setItem(ROTATION_AXIS, button(Material.ARROW, "回転軸: " + session.rotationAxis().name(),
                List.of(value("クリックで X → Y → Z"), value("ワンドの左クリックに適用"))));
        inventory.setItem(RECENTER, button(Material.ENDER_PEARL, "原点をここに",
                List.of(value("分子中心を目の前に配置"))));
        inventory.setItem(RESET, button(Material.COMPASS, "リセット",
                List.of(value("倍率・回転・表示をリセット"), value("構造フレームは先頭に戻ります"))));
        inventory.setItem(PACK, button(Material.KNOWLEDGE_BOOK, "リソースパック",
                List.of(value("クリックでダウンロード / 再適用"), value("3D球体モデル"))));
        inventory.setItem(CLEAR, button(Material.BARRIER, "このモデルを消去",
                List.of(value("選択中のモデルを削除"))));
        inventory.setItem(CLOSE, button(Material.OAK_DOOR, "閉じる", List.of(value("メニューを閉じる"))));
    }

    private ItemStack info(ViewSession session) {
        List<Component> lore = new ArrayList<>();
        lore.add(value(session.molecule().contents() + "  (" + session.molecule().atoms().size()
                + " 原子, " + session.molecule().bonds().size() + " 結合)"));
        lore.add(value("モデル: #" + session.id()));
        lore.add(value("所有者: " + ownerName(session)));
        lore.add(value("共有編集: " + (session.sharedEdit() ? "ON" : "OFF")));
        lore.add(value("表示原子数: " + session.renderedAtoms()));
        lore.add(value("表示形式: " + (session.spaceFilling() ? "空間充填" : "球棒")));
        lore.add(value("構造: " + plugin.renderer().frameText(session)));
        lore.add(value("ボンド: " + (session.showBonds() ? "ON" : "OFF")));
        lore.add(value("ラベル: " + (session.showLabels() ? "ON" : "OFF")));
        lore.add(value("選択原子: " + session.selectedAtoms().size()));
        lore.add(value("充填半径: " + radiusModeName(session.radiusMode())));
        lore.add(value("回転軸: " + session.rotationAxis().name()));
        lore.add(value("倍率: " + number(session.blocksPerAngstrom()) + " ブロック/Å"));
        lore.add(value("原子サイズ: " + number(displayAtomScale(session))));
        lore.add(value(String.format(Locale.ROOT, "回転: %.0f / %.0f / %.0f°",
                session.rotationX(), session.rotationY(), session.rotationZ())));
        return button(Material.BOOK, "Mol Viewer", lore);
    }

    private ItemStack spaceFillingButton(ViewSession session) {
        if (session.spaceFilling()) {
            return button(Material.SLIME_BALL, "表示: 空間充填",
                    List.of(value("クリックで球棒表示に変更"),
                            value("半径: " + radiusModeName(session.radiusMode()))));
        }
        return button(Material.SNOWBALL, "表示: 球棒",
                List.of(value("クリックで空間充填に変更"), value("半径: 共有結合半径ベース")));
    }

    private ItemStack labelsButton(ViewSession session) {
        if (session.showLabels()) {
            return button(Material.NAME_TAG, "ラベル: ON",
                    List.of(value("全原子のラベルを表示"), value("選択原子は常に表示")));
        }
        return button(Material.PAPER, "ラベル: OFF",
                List.of(value("全原子のラベルを非表示"), value("選択原子は常に表示")));
    }

    private ItemStack shareButton(ViewSession session) {
        if (session.sharedEdit()) {
            return button(Material.ENDER_EYE, "共有編集: ON",
                    List.of(value("他の人も操作できます"), value("所有者 / 管理者が変更可能")));
        }
        return button(Material.ENDER_PEARL, "共有編集: OFF",
                List.of(value("所有者のみ操作できます"), value("所有者 / 管理者が変更可能")));
    }

    private ItemStack bondsButton(ViewSession session) {
        if (session.spaceFilling()) {
            return button(Material.BLAZE_ROD, "ボンド: 空間充填では非表示",
                    List.of(value("球棒表示に切り替えると結合を表示"),
                            value("空間充填はvdW半径で球を描画")));
        }
        if (session.showBonds()) {
            return button(Material.BLAZE_ROD, "ボンド: ON",
                    List.of(value("クリックで非表示"), value("球棒表示時に結合を表示")));
        }
        return button(Material.STICK, "ボンド: OFF",
                List.of(value("クリックで表示"), value("球棒表示時に結合を表示")));
    }

    private ItemStack radiusModeButton(ViewSession session) {
        return switch (session.radiusMode()) {
            case VDW -> button(Material.AMETHYST_SHARD, "充填半径: vdW",
                    List.of(value("空間充填向け"), value("クリックで共有結合半径")));
            case COVALENT -> button(Material.AMETHYST_SHARD, "充填半径: 共有結合",
                    List.of(value("共有結合半径"), value("クリックでvdW半径")));
        };
    }

    private String ownerName(ViewSession session) {
        if (session.owner() == null) {
            return "不明";
        }
        String name = Bukkit.getOfflinePlayer(session.owner()).getName();
        return name == null ? session.owner().toString() : name;
    }

    private double displayAtomScale(ViewSession session) {
        return session.spaceFilling() ? session.spaceFillingScale() : session.atomScale();
    }

    private String radiusModeName(ViewSession.RadiusMode mode) {
        return switch (mode) {
            case VDW -> "vdW";
            case COVALENT -> "共有結合";
        };
    }

    private ItemStack rotationButton(Material material, String name, double angle) {
        return button(material, name, List.of(value("現在: " + number(angle) + "°")));
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

    private Component title(ViewSession session) {
        String name = session.moleculeSet().name();
        if (name.length() > 24) {
            name = name.substring(0, 23) + "...";
        }
        return Component.text("Mol: " + name, NamedTextColor.DARK_AQUA);
    }

    private String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
