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

public final class MolFrameGui {

    private static final int SIZE = 54;
    private static final int PLAY = 0;
    private static final int SPEED_DOWN = 1;
    private static final int SPEED_UP = 2;
    private static final int LOOP = 3;
    private static final int INFO = 4;
    private static final int FIRST = 5;
    private static final int PREV = 6;
    private static final int NEXT = 7;
    private static final int LAST = 8;
    private static final int FRAME_START = 9;
    private static final int FRAMES_PER_PAGE = 36;
    private static final int BACK = 45;
    private static final int PAGE_PREV = 46;
    private static final int PAGE_INFO = 49;
    private static final int PAGE_NEXT = 52;
    private static final int CLOSE = 53;

    private final McMolViewPlugin plugin;

    public MolFrameGui(McMolViewPlugin plugin) {
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
        MolFrameGuiHolder holder = new MolFrameGuiHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                Component.text("Mol: フレーム / 再生", NamedTextColor.DARK_AQUA));
        holder.inventory(inventory);
        populate(inventory, session, holder, player);
        player.openInventory(inventory);
    }

    public void handleClick(Player player, Inventory inventory, int slot, boolean shiftClick) {
        ViewSession session = plugin.renderer().session(player);
        if (session == null) {
            player.closeInventory();
            return;
        }
        MolFrameGuiHolder holder = (MolFrameGuiHolder) inventory.getHolder();
        int page = holder.page();
        try {
            switch (slot) {
                case PLAY -> {
                    boolean playing = plugin.renderer().togglePlayback(player);
                    player.sendActionBar(Component.text(playing ? "再生開始" : "再生停止",
                            NamedTextColor.AQUA));
                }
                case SPEED_DOWN -> plugin.renderer().setPlaybackInterval(player,
                        plugin.renderer().playbackInterval(player) + 2);
                case SPEED_UP -> plugin.renderer().setPlaybackInterval(player,
                        plugin.renderer().playbackInterval(player) - 2);
                case LOOP -> {
                    plugin.renderer().togglePlaybackLoop(player);
                    player.sendActionBar(Component.text("ループ: "
                            + (plugin.renderer().playbackLoop(player) ? "ON" : "OFF"),
                            NamedTextColor.AQUA));
                }
                case FIRST -> plugin.renderer().setFrame(player, 0);
                case PREV -> plugin.renderer().setFrame(player, session.frameIndex() - 1);
                case NEXT -> plugin.renderer().setFrame(player, session.frameIndex() + 1);
                case LAST -> plugin.renderer().setFrame(player, session.frameCount() - 1);
                case BACK -> {
                    plugin.controlGui().open(player);
                    return;
                }
                case PAGE_PREV -> {
                    holder.page(Math.max(0, page - 1));
                    populate(inventory, plugin.renderer().session(player), holder, player);
                    return;
                }
                case PAGE_NEXT -> {
                    int pages = pages(plugin.renderer().session(player));
                    holder.page(Math.min(pages - 1, page + 1));
                    populate(inventory, plugin.renderer().session(player), holder, player);
                    return;
                }
                case CLOSE -> {
                    player.closeInventory();
                    return;
                }
                default -> {
                    if (slot >= FRAME_START && slot < FRAME_START + FRAMES_PER_PAGE) {
                        int index = page * FRAMES_PER_PAGE + (slot - FRAME_START);
                        if (index < session.frameCount()) {
                            plugin.renderer().setFrame(player, index);
                            if (shiftClick) {
                                player.closeInventory();
                                return;
                            }
                        }
                    } else {
                        return;
                    }
                }
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
            ViewSession updated = plugin.renderer().session(player);
            if (updated != null) {
                populate(inventory, updated, holder, player);
            }
        } catch (RuntimeException e) {
            player.sendMessage(Component.text(e.getMessage() == null ? "操作できませんでした。"
                    : e.getMessage(), NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
        }
    }

    private void populate(Inventory inventory, ViewSession session, MolFrameGuiHolder holder, Player player) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler());
        }
        int pages = pages(session);
        int page = Math.min(holder.page(), pages - 1);
        holder.page(page);

        boolean playing = plugin.renderer().isPlaying(player);
        int interval = plugin.renderer().playbackInterval(player);
        boolean loop = plugin.renderer().playbackLoop(player);

        inventory.setItem(PLAY, button(playing ? Material.LIME_DYE : Material.GRAY_DYE,
                playing ? "再生中" : "停止中",
                List.of(value(playing ? "クリックで停止" : "クリックで自動再生"),
                        value(plugin.renderer().frameText(session)))));
        inventory.setItem(SPEED_DOWN, button(Material.FEATHER, "遅くする",
                List.of(value("現在: " + interval + " ticks/フレーム"),
                        value(String.format(Locale.ROOT, "%.2f 秒/フレーム", interval / 20.0)))));
        inventory.setItem(SPEED_UP, button(Material.SUGAR, "速くする",
                List.of(value("現在: " + interval + " ticks/フレーム"),
                        value(String.format(Locale.ROOT, "%.2f 秒/フレーム", interval / 20.0)))));
        inventory.setItem(LOOP, button(Material.REPEATER, "ループ: " + (loop ? "ON" : "OFF"),
                List.of(value("最後のフレームから先頭へ戻る"))));
        inventory.setItem(INFO, info(session, interval, player));
        inventory.setItem(FIRST, button(Material.SPECTRAL_ARROW, "最初へ",
                List.of(value("フレーム 1"))));
        inventory.setItem(PREV, button(Material.ARROW, "前へ",
                List.of(value(plugin.renderer().frameText(session)))));
        inventory.setItem(NEXT, button(Material.ARROW, "次へ",
                List.of(value(plugin.renderer().frameText(session)))));
        inventory.setItem(LAST, button(Material.SPECTRAL_ARROW, "最後へ",
                List.of(value("フレーム " + session.frameCount()))));

        for (int pageIndex = 0; pageIndex < FRAMES_PER_PAGE; pageIndex++) {
            int index = page * FRAMES_PER_PAGE + pageIndex;
            if (index >= session.frameCount()) {
                break;
            }
            boolean active = index == session.frameIndex();
            inventory.setItem(FRAME_START + pageIndex, frameButton(session, index, active));
        }

        inventory.setItem(BACK, button(Material.OAK_DOOR, "メニューへ", List.of(value("操作メニューに戻る"))));
        inventory.setItem(PAGE_PREV, button(page > 0 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
                "前のページ", List.of(value("ページ " + (page + 1) + "/" + pages))));
        inventory.setItem(PAGE_INFO, button(Material.BOOK, "フレーム一覧",
                List.of(value("ページ " + (page + 1) + "/" + pages),
                        value("表示中: " + session.frameCount() + " フレーム"))));
        inventory.setItem(PAGE_NEXT, button(page < pages - 1 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
                "次のページ", List.of(value("ページ " + (page + 1) + "/" + pages))));
        inventory.setItem(CLOSE, button(Material.BARRIER, "閉じる", List.of(value("メニューを閉じる"))));
    }

    private ItemStack frameButton(ViewSession session, int index, boolean active) {
        List<Component> lore = new ArrayList<>();
        Double energy = session.moleculeSet().frame(index).energy();
        if (energy != null) {
            lore.add(value(String.format(Locale.ROOT, "E = %.6f Ha", energy)));
            Double first = session.moleculeSet().frame(0).energy();
            if (first != null) {
                lore.add(value(String.format(Locale.ROOT, "dE = %+.1f kJ/mol",
                        (energy - first) * 2625.499639)));
            }
        }
        lore.add(value(active ? "表示中" : "クリックで表示"));
        lore.add(value("Shift+クリックで閉じる"));
        return button(active ? Material.ENCHANTED_BOOK : Material.PAPER,
                "フレーム " + (index + 1) + "/" + session.frameCount(), lore);
    }

    private ItemStack info(ViewSession session, int interval, Player player) {
        List<Component> lore = new ArrayList<>();
        lore.add(value("モデル: #" + session.id()));
        lore.add(value("フレーム: " + (session.frameIndex() + 1) + "/" + session.frameCount()));
        lore.add(value("再生間隔: " + interval + " ticks"));
        lore.add(value("ループ: " + (plugin.renderer().playbackLoop(player) ? "ON" : "OFF")));
        Double energy = session.molecule().energy();
        if (energy != null) {
            lore.add(value(String.format(Locale.ROOT, "E = %.6f Ha", energy)));
        }
        return button(Material.BOOK, "フレーム再生", lore);
    }

    private int pages(ViewSession session) {
        return Math.max(1, (session.frameCount() + FRAMES_PER_PAGE - 1) / FRAMES_PER_PAGE);
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
}
