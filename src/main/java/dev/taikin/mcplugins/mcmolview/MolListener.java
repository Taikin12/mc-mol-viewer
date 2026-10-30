package dev.taikin.mcplugins.mcmolview;

import dev.taikin.mcplugins.mcmolview.gui.MolFrameGuiHolder;
import dev.taikin.mcplugins.mcmolview.gui.MolGuiHolder;
import dev.taikin.mcplugins.mcmolview.gui.MolModelListGuiHolder;
import dev.taikin.mcplugins.mcmolview.render.MoleculeRenderer;
import dev.taikin.mcplugins.mcmolview.render.ViewSession;
import dev.taikin.miviewer.common.WandItems;
import io.papermc.paper.event.player.PlayerPickEntityEvent;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class MolListener implements Listener {

    private final McMolViewPlugin plugin;

    public MolListener(McMolViewPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.wand().refreshAppearance(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        MolWand.WandType wandType = plugin.wand().type(item);
        if (wandType == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!ownsWand(player, item)) {
            return;
        }
        WandItems.setHeldTarget(player, WandItems.ViewerTarget.MOL);
        Action action = event.getAction();
        if (wandType == MolWand.WandType.ATOM) {
            event.setCancelled(true);
            handleAtomInteract(player, action);
            return;
        }
        if (wandType == MolWand.WandType.FRAME) {
            event.setCancelled(true);
            boolean forward = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
            handleFrame(player, forward);
            return;
        }
        if (wandType == MolWand.WandType.MENU) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                plugin.controlGui().open(player);
            } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
            }
            return;
        }
        Block clicked = event.getClickedBlock();
        BlockFace face = event.getBlockFace();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            try {
                if (player.isSneaking()) {
                    if (clicked != null && face != null) {
                        Location origin = clicked.getRelative(face).getLocation().add(0.5, 0.5, 0.5);
                        plugin.renderer().setOrigin(player, origin);
                        player.sendMessage(Component.text("原点（分子中心）をブロック ("
                                + origin.getBlockX() + ", " + origin.getBlockY() + ", "
                                + origin.getBlockZ() + ") に設定しました。", NamedTextColor.GREEN));
                    } else {
                        player.sendActionBar(Component.text(
                                "ブロック面をスニーク+右クリックで原点を設定", NamedTextColor.AQUA));
                    }
                } else {
                    plugin.renderer().rotateAroundAxis(player, -15.0);
                }
            } catch (IllegalStateException e) {
                player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            }
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            try {
                if (player.isSneaking()) {
                    if (clicked != null && face != null) {
                        ViewSession.Axis axis = axisFor(face);
                        plugin.renderer().setRotationAxis(player, axis);
                        player.sendActionBar(Component.text("回転軸: " + axis.name(), NamedTextColor.AQUA));
                    } else {
                        plugin.renderer().cycleRotationAxis(player);
                        ViewSession current = plugin.renderer().session(player);
                        if (current != null) {
                            player.sendActionBar(Component.text("回転軸: " + current.rotationAxis().name(),
                                    NamedTextColor.AQUA));
                        }
                    }
                } else {
                    plugin.renderer().rotateAroundAxis(player, 15.0);
                }
            } catch (IllegalStateException e) {
                player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        MolWand.WandType heldType = plugin.wand().type(held);
        MoleculeRenderer.ModelRef ref = plugin.renderer().modelRef(event.getRightClicked().getUniqueId());
        if (heldType == MolWand.WandType.ATOM) {
            if (ref == null && !ownsWand(player, held)) {
                return;
            }
            event.setCancelled(true);
            if (ref != null) {
                WandItems.setHeldTarget(player, WandItems.ViewerTarget.MOL);
            }
            handleAtomInteract(player, Action.RIGHT_CLICK_AIR);
            return;
        }
        if (ref == null) {
            return;
        }
        event.setCancelled(true);
        if (heldType != null) {
            WandItems.setHeldTarget(player, WandItems.ViewerTarget.MOL);
        }
        if (heldType == MolWand.WandType.FRAME) {
            plugin.renderer().select(player, ref.modelId());
            handleFrame(player, true);
            return;
        }
        handleModelClick(player, ref, true, heldType);
    }

    @EventHandler
    public void onPickEntity(PlayerPickEntityEvent event) {
        MoleculeRenderer.ModelRef ref = plugin.renderer().modelRef(event.getEntity().getUniqueId());
        if (ref == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (plugin.wand().type(player.getInventory().getItemInMainHand()) != null) {
            WandItems.setHeldTarget(player, WandItems.ViewerTarget.MOL);
        }
        if (!plugin.renderer().select(player, ref.modelId())) {
            player.sendMessage(Component.text("モデル #" + ref.modelId() + " はありません。",
                    NamedTextColor.GRAY));
            return;
        }
        if (plugin.wand().type(player.getInventory().getItemInMainHand()) == MolWand.WandType.ATOM) {
            String text = plugin.renderer().addAtomSelection(player);
            player.sendActionBar(Component.text(text, NamedTextColor.AQUA));
            return;
        }
        ViewSession selected = plugin.renderer().session(player);
        if (selected != null) {
            String shared = selected.sharedEdit() ? " [共有]" : "";
            player.sendActionBar(Component.text("選択中: モデル #" + selected.id() + " "
                    + selected.moleculeSet().name() + shared, NamedTextColor.AQUA));
        }
        if (!plugin.wand().has(player)) {
            plugin.wand().give(player, MolWand.WandType.MENU);
            player.sendMessage(Component.text("分子メニュー棒を入手しました。", NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        MoleculeRenderer.ModelRef ref = plugin.renderer().modelRef(event.getEntity().getUniqueId());
        if (ref == null) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        if (plugin.wand().type(player.getInventory().getItemInMainHand()) != null) {
            WandItems.setHeldTarget(player, WandItems.ViewerTarget.MOL);
        }
        MolWand.WandType heldType = plugin.wand().type(player.getInventory().getItemInMainHand());
        if (heldType == MolWand.WandType.ATOM) {
            handleAtomInteract(player, Action.LEFT_CLICK_AIR);
            return;
        }
        if (heldType == MolWand.WandType.FRAME) {
            plugin.renderer().select(player, ref.modelId());
            handleFrame(player, false);
            return;
        }
        handleModelClick(player, ref, false, heldType);
    }

    private void handleModelClick(Player player, MoleculeRenderer.ModelRef ref, boolean rightClick,
            MolWand.WandType heldType) {
        if (plugin.wand().type(player.getInventory().getItemInMainHand()) != null) {
            WandItems.setHeldTarget(player, WandItems.ViewerTarget.MOL);
        }
        if (!plugin.renderer().select(player, ref.modelId())) {
            player.sendMessage(Component.text("モデル #" + ref.modelId() + " はありません。",
                    NamedTextColor.GRAY));
            return;
        }
        ViewSession selected = plugin.renderer().session(player);
        if (selected != null) {
            String shared = selected.sharedEdit() ? " [共有]" : "";
            player.sendActionBar(Component.text("選択中: モデル #" + selected.id() + " "
                    + selected.moleculeSet().name() + shared, NamedTextColor.AQUA));
        }
        if (heldType == null && !plugin.wand().has(player)) {
            plugin.wand().give(player, MolWand.WandType.MENU);
            player.sendMessage(Component.text("分子メニュー棒を入手しました。モデル #" + ref.modelId()
                    + " を選択しました。", NamedTextColor.GREEN));
            return;
        }
        if (rightClick || heldType == null || heldType == MolWand.WandType.MENU) {
            plugin.controlGui().open(player);
            return;
        }
        try {
            plugin.renderer().rotateAroundAxis(player, player.isSneaking() ? -15.0 : 15.0);
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleFrame(Player player, boolean forward) {
        try {
            ViewSession session = plugin.renderer().session(player);
            if (session == null) {
                throw new IllegalStateException("分子が表示されていません。先に /mol view <file> を実行してください。");
            }
            int target = player.isSneaking()
                    ? (forward ? session.frameCount() - 1 : 0)
                    : session.frameIndex() + (forward ? 1 : -1);
            String text = plugin.renderer().setFrame(player, target);
            player.sendActionBar(Component.text(text, NamedTextColor.AQUA));
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleAtomInteract(Player player, Action action) {
        try {
            String text;
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                text = plugin.renderer().addAtomSelection(player);
            } else {
                text = plugin.renderer().clearAtomSelection(player);
            }
            player.sendActionBar(Component.text(text, NamedTextColor.AQUA));
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
    }

    private boolean ownsWand(Player player, ItemStack item) {
        WandItems.ViewerTarget target = plugin.wand().target(item);
        if (target == null || target == WandItems.ViewerTarget.MOL) {
            return true;
        }
        return plugin.getServer().getPluginManager().getPlugin("CifViewer") == null;
    }

    private ViewSession.Axis axisFor(BlockFace face) {
        return switch (face) {
            case UP, DOWN -> ViewSession.Axis.Y;
            case NORTH, SOUTH -> ViewSession.Axis.Z;
            case EAST, WEST -> ViewSession.Axis.X;
            default -> ViewSession.Axis.Y;
        };
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack previous = player.getInventory().getItem(event.getPreviousSlot());
        ItemStack next = player.getInventory().getItem(event.getNewSlot());
        if (plugin.wand().type(previous) != MolWand.WandType.VIEW
                && plugin.wand().type(next) != MolWand.WandType.VIEW) {
            return;
        }

        event.setCancelled(true);
        int delta = event.getNewSlot() - event.getPreviousSlot();
        if (delta == 0) {
            return;
        }
        try {
            plugin.renderer().adjustScale(player, delta > 0 ? 1.1 : 1.0 / 1.1);
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.renderer().stopPlayback(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        UUID owner;
        if (holder instanceof MolGuiHolder guiHolder) {
            owner = guiHolder.playerId();
        } else if (holder instanceof MolModelListGuiHolder modelListHolder) {
            owner = modelListHolder.playerId();
        } else if (holder instanceof MolFrameGuiHolder frameHolder) {
            owner = frameHolder.playerId();
        } else {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!owner.equals(player.getUniqueId())) {
            return;
        }
        if (event.getClickedInventory() != top) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        if (holder instanceof MolGuiHolder) {
            plugin.controlGui().handleClick(player, top, slot);
        } else if (holder instanceof MolFrameGuiHolder) {
            plugin.frameGui().handleClick(player, top, slot, event.isShiftClick());
        } else {
            plugin.modelListGui().handleClick(player, top, slot, event.isShiftClick(),
                    event.isRightClick());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MolGuiHolder || holder instanceof MolModelListGuiHolder
                || holder instanceof MolFrameGuiHolder) {
            event.setCancelled(true);
        }
    }
}
