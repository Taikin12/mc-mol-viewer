package dev.taikin.mcplugins.mcmolview;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.taikin.mcplugins.mcmolview.model.MoleculeSet;
import dev.taikin.mcplugins.mcmolview.mol.MoleculeParser;
import dev.taikin.mcplugins.mcmolview.render.ViewSession;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MolCommand {

    private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;
    private static final int PAGE_SIZE = 10;

    private final McMolViewPlugin plugin;

    public MolCommand(McMolViewPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("mol")
                .requires(source -> source.getSender().hasPermission("mcmolview.use"))
                .executes(context -> help(sender(context)))
                .then(Commands.literal("help")
                        .executes(context -> help(sender(context))))
                .then(Commands.literal("wand")
                        .executes(context -> wand(sender(context), MolWand.WandType.MENU))
                        .then(Commands.literal("menu")
                                .executes(context -> wand(sender(context), MolWand.WandType.MENU)))
                        .then(Commands.literal("view")
                                .executes(context -> wand(sender(context), MolWand.WandType.VIEW)))
                        .then(Commands.literal("atom")
                                .executes(context -> wand(sender(context), MolWand.WandType.ATOM)))
                        .then(Commands.literal("frame")
                                .executes(context -> wand(sender(context), MolWand.WandType.FRAME))))
                .then(Commands.literal("wands")
                        .executes(context -> wands(sender(context))))
                .then(Commands.literal("pack")
                        .executes(context -> pack(sender(context))))
                .then(Commands.literal("download")
                        .executes(context -> pack(sender(context))))
                .then(Commands.literal("list")
                        .executes(context -> list(sender(context), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> list(sender(context),
                                        IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("view")
                        .then(Commands.argument("file", StringArgumentType.greedyString())
                                .suggests(this::suggestFiles)
                                .executes(context -> view(sender(context),
                                        StringArgumentType.getString(context, "file"), null))))
                .then(Commands.literal("viewat")
                        .then(Commands.argument("scale", DoubleArgumentType.doubleArg(0.1, 10.0))
                                .then(Commands.argument("file", StringArgumentType.greedyString())
                                        .suggests(this::suggestFiles)
                                        .executes(context -> view(sender(context),
                                                StringArgumentType.getString(context, "file"),
                                                DoubleArgumentType.getDouble(context, "scale"))))))
                .then(Commands.literal("frame")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(context -> frame(sender(context),
                                        IntegerArgumentType.getInteger(context, "index")))))
                .then(Commands.literal("next")
                        .executes(context -> step(sender(context), 1)))
                .then(Commands.literal("prev")
                        .executes(context -> step(sender(context), -1)))
                .then(Commands.literal("play")
                        .executes(context -> play(sender(context))))
                .then(Commands.literal("loop")
                        .executes(context -> loop(sender(context))))
                .then(Commands.literal("speed")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(2, 40))
                                .executes(context -> speed(sender(context),
                                        IntegerArgumentType.getInteger(context, "ticks")))))
                .then(Commands.literal("frames")
                        .executes(context -> frames(sender(context))))
                .then(Commands.literal("models")
                        .executes(context -> models(sender(context))))
                .then(Commands.literal("select")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(context -> select(sender(context),
                                        IntegerArgumentType.getInteger(context, "id")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(context -> remove(sender(context),
                                        IntegerArgumentType.getInteger(context, "id")))))
                .then(Commands.literal("tp")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(context -> teleport(sender(context),
                                        IntegerArgumentType.getInteger(context, "id")))))
                .then(Commands.literal("share")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .then(Commands.literal("on")
                                        .executes(context -> share(sender(context),
                                                IntegerArgumentType.getInteger(context, "id"), true)))
                                .then(Commands.literal("off")
                                        .executes(context -> share(sender(context),
                                                IntegerArgumentType.getInteger(context, "id"), false)))))
                .then(Commands.literal("clear")
                        .executes(context -> clear(sender(context))))
                .then(Commands.literal("clearall")
                        .executes(context -> clearAll(sender(context))))
                .then(Commands.literal("reload")
                        .executes(context -> reload(sender(context))))
                .build();
    }

    private int help(CommandSender sender) {
        sender.sendMessage(Component.text("Mol Viewer ヘルプ", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("表示", NamedTextColor.AQUA));
        sender.sendMessage(command("/mol list", "分子ファイル一覧を表示", true));
        sender.sendMessage(command("/mol view ", "分子モデルを追加 (XYZ/MOL/SDF/ORCA out)", false));
        sender.sendMessage(command("/mol viewat ", "倍率を指定して追加 (blocks/Å)", false));
        sender.sendMessage(command("/mol frame ", "複数構造のフレームを指定", false));
        sender.sendMessage(command("/mol next", "次の構造フレーム", true));
        sender.sendMessage(command("/mol prev", "前の構造フレーム", true));
        sender.sendMessage(command("/mol play", "自動再生の開始 / 停止", true));
        sender.sendMessage(command("/mol loop", "ループ再生の ON/OFF", true));
        sender.sendMessage(command("/mol speed ", "再生間隔 (ticks, 2-40)", false));
        sender.sendMessage(command("/mol frames", "フレーム一覧・再生 GUI", true));
        sender.sendMessage(command("/mol models", "全モデル一覧", true));
        sender.sendMessage(command("/mol select ", "操作するモデルを選択", false));
        sender.sendMessage(command("/mol remove ", "指定モデルを削除（所有者/管理者）", false));
        sender.sendMessage(command("/mol tp ", "モデルの近くへ移動", false));
        sender.sendMessage(command("/mol share ", "共有編集の ON/OFF（所有者/管理者）", false));
        sender.sendMessage(command("/mol clear", "選択中モデルを削除", true));
        sender.sendMessage(command("/mol clearall", "自分のモデルを削除（管理者は全削除）", true));

        sender.sendMessage(Component.text("道具", NamedTextColor.AQUA));
        sender.sendMessage(command("/mol wand", "分子メニュー棒を取得", true));
        sender.sendMessage(command("/mol wand view", "分子ビュー棒を取得", false));
        sender.sendMessage(command("/mol wand atom", "分子原子棒を取得", false));
        sender.sendMessage(command("/mol wand frame", "分子フレーム棒を取得", false));
        sender.sendMessage(command("/mol wands", "全ての分子棒をまとめて取得", true));
        sender.sendMessage(command("/mol pack", "リソースパックを再適用", true));
        sender.sendMessage(command("/mol reload", "設定を再読み込み", true));

        sender.sendMessage(Component.text("分子メニュー棒", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("右クリック: メニュー", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("モデルをクリック: 選択 / メニュー", NamedTextColor.GRAY));

        sender.sendMessage(Component.text("分子ビュー棒", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("左クリック: 選択軸で+15°回転 / 右クリック: -15°回転",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("スニーク+左クリック(ブロック面): 回転軸を変更",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("スニーク+左クリック(空): 回転軸を切替",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("スニーク+右クリック(ブロック面): 原点（分子中心）を設定",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("スニーク+スクロール: 全体サイズを変更", NamedTextColor.GRAY));

        sender.sendMessage(Component.text("分子原子棒", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("右クリック: 原子を選択 / 追加（最大4）", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("左クリック: 選択解除", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("2原子: 距離 / 3原子: 角度 / 4原子: 二面角", NamedTextColor.GRAY));

        sender.sendMessage(Component.text("分子フレーム棒", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("右クリック: 次の構造 / 左クリック: 前の構造", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("スニーク+右/左: 最後 / 最初のフレーム", NamedTextColor.GRAY));
        return 1;
    }

    private Component command(String command, String description, boolean execute) {
        Component label = Component.text(command, NamedTextColor.WHITE)
                .clickEvent(execute ? ClickEvent.runCommand(command) : ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(execute ? "クリックで実行" : "クリックで入力")));
        return label.append(Component.text("  " + description, NamedTextColor.GRAY));
    }

    private int wand(CommandSender sender, MolWand.WandType type) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        plugin.wand().give(player, type);
        plugin.wand().refreshAppearance(player);
        String name = switch (type) {
            case MENU -> "分子メニュー棒を追加しました。右クリックでメニューを開けます。";
            case VIEW -> "分子ビュー棒を追加しました。回転・原点設定に使います。";
            case ATOM -> "分子原子棒を追加しました。右クリックで原子を選択できます。";
            case FRAME -> "分子フレーム棒を追加しました。右クリックで次の構造、左クリックで前の構造。";
        };
        sender.sendMessage(Component.text(name, NamedTextColor.GREEN));
        return 1;
    }

    private int wands(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        plugin.wand().giveIfMissing(player, MolWand.WandType.MENU);
        plugin.wand().giveIfMissing(player, MolWand.WandType.VIEW);
        plugin.wand().giveIfMissing(player, MolWand.WandType.ATOM);
        plugin.wand().giveIfMissing(player, MolWand.WandType.FRAME);
        plugin.wand().refreshAppearance(player);
        sender.sendMessage(Component.text("分子メニュー棒・分子ビュー棒・分子原子棒・分子フレーム棒を付与しました。",
                NamedTextColor.GREEN));
        return 1;
    }

    private int pack(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        return plugin.sendResourcePack(player) ? 1 : 0;
    }

    private int list(CommandSender sender, int page) {
        List<String> keys = plugin.repository().keys();
        if (keys.isEmpty()) {
            sender.sendMessage(Component.text("分子ファイルが見つかりません。設定の sources に "
                    + "XYZ/MOL/SDF/ORCA out を置いてください。", NamedTextColor.GRAY));
            return 0;
        }

        int pages = (keys.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int currentPage = Math.min(page, pages);
        int from = (currentPage - 1) * PAGE_SIZE;
        int to = Math.min(keys.size(), from + PAGE_SIZE);

        sender.sendMessage(Component.text("Molecular files (" + keys.size() + "):", NamedTextColor.GOLD));
        for (int index = from; index < to; index++) {
            sender.sendMessage(Component.text("  " + keys.get(index), NamedTextColor.WHITE));
        }
        if (pages > 1) {
            sender.sendMessage(Component.text("Page " + currentPage + "/" + pages + "  /mol list <page>",
                    NamedTextColor.GRAY));
        }
        return keys.size();
    }

    private int view(CommandSender sender, String file, Double scale) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }

        Path path = plugin.repository().resolve(file);
        if (path == null) {
            sender.sendMessage(Component.text("分子ファイルが見つからないか、候補が複数あります: " + file,
                    NamedTextColor.RED));
            return 0;
        }

        try {
            long size = Files.size(path);
            if (size > MAX_FILE_SIZE) {
                sender.sendMessage(Component.text("ファイルが大きすぎます (" + size + " bytes).",
                        NamedTextColor.RED));
                return 0;
            }
            String text = Files.readString(path, StandardCharsets.UTF_8);
            MoleculeSet set = new MoleculeParser().parse(path.getFileName().toString(), text);
            int count = plugin.renderer().view(player, set,
                    scale == null ? plugin.config().blocksPerAngstrom() : scale);
            int id = plugin.renderer().activeId(player);
            String frames = set.size() > 1 ? " / " + set.size() + " frames" : "";
            sender.sendMessage(Component.text("モデル #" + id + " を追加: " + set.name()
                    + " (" + count + " atoms" + frames + ").", NamedTextColor.GREEN));
            giveWand(player);
            return count;
        } catch (IOException e) {
            sender.sendMessage(Component.text("ファイルを読めません: " + e.getMessage(), NamedTextColor.RED));
            return 0;
        } catch (RuntimeException e) {
            sender.sendMessage(Component.text("分子を表示できません: " + message(e), NamedTextColor.RED));
            return 0;
        }
    }

    private int frame(CommandSender sender, int index) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        try {
            String text = plugin.renderer().setFrame(player, index - 1);
            sender.sendMessage(Component.text(text, NamedTextColor.GREEN));
            return 1;
        } catch (IllegalStateException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return 0;
        }
    }

    private int step(CommandSender sender, int delta) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        try {
            String text = plugin.renderer().adjustFrame(player, delta);
            sender.sendMessage(Component.text(text, NamedTextColor.GREEN));
            return 1;
        } catch (IllegalStateException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return 0;
        }
    }

    private int play(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        try {
            boolean playing = plugin.renderer().togglePlayback(player);
            sender.sendMessage(Component.text(playing ? "自動再生を開始しました。" : "自動再生を停止しました。",
                    NamedTextColor.GREEN));
            return 1;
        } catch (IllegalStateException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return 0;
        }
    }

    private int loop(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        plugin.renderer().togglePlaybackLoop(player);
        sender.sendMessage(Component.text("ループ再生: "
                + (plugin.renderer().playbackLoop(player) ? "ON" : "OFF"), NamedTextColor.GREEN));
        return 1;
    }

    private int speed(CommandSender sender, int ticks) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        plugin.renderer().setPlaybackInterval(player, ticks);
        sender.sendMessage(Component.text("再生間隔を " + plugin.renderer().playbackInterval(player)
                + " ticks にしました。", NamedTextColor.GREEN));
        return 1;
    }

    private int frames(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        plugin.frameGui().open(player);
        return 1;
    }

    private void giveWand(Player player) {
        if (plugin.wand().giveIfMissing(player)) {
            player.sendMessage(Component.text("分子メニュー棒を付与しました。右クリックでメニュー。",
                    NamedTextColor.GREEN));
        }
    }

    private int models(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        var models = plugin.renderer().models(player);
        if (models.isEmpty()) {
            sender.sendMessage(Component.text("表示中のモデルはありません。", NamedTextColor.GRAY));
            return 0;
        }
        int activeId = plugin.renderer().activeId(player);
        sender.sendMessage(Component.text("表示中モデル (" + models.size() + "):", NamedTextColor.GOLD));
        for (ViewSession session : models) {
            String marker = session.id() == activeId ? "* " : "  ";
            String shared = session.sharedEdit() ? " [共有]" : "";
            String frame = session.frameCount() > 1
                    ? " " + plugin.renderer().frameText(session) : "";
            sender.sendMessage(Component.text(marker + "#" + session.id() + " "
                    + session.moleculeSet().name() + " by " + ownerName(session) + shared + frame
                    + " (" + session.renderedAtoms() + " atoms)", NamedTextColor.WHITE));
        }
        return models.size();
    }

    private int select(CommandSender sender, int id) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        if (!plugin.renderer().select(player, id)) {
            sender.sendMessage(Component.text("モデル #" + id + " はありません。", NamedTextColor.RED));
            return 0;
        }
        sender.sendMessage(Component.text("モデル #" + id + " を選択しました。", NamedTextColor.GREEN));
        player.sendActionBar(Component.text("選択中: モデル #" + id, NamedTextColor.AQUA));
        return 1;
    }

    private int remove(CommandSender sender, int id) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        ViewSession session = plugin.renderer().model(id);
        if (session == null) {
            sender.sendMessage(Component.text("モデル #" + id + " はありません。", NamedTextColor.RED));
            return 0;
        }
        if (!plugin.renderer().canDelete(player, session)) {
            sender.sendMessage(Component.text("このモデルを削除する権限がありません。",
                    NamedTextColor.RED));
            return 0;
        }
        int removed = plugin.renderer().clear(player, id);
        sender.sendMessage(Component.text("モデル #" + id + " を削除しました (" + removed + " entities).",
                NamedTextColor.GREEN));
        return removed;
    }

    private int clear(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        ViewSession session = plugin.renderer().session(player);
        if (session == null) {
            sender.sendMessage(Component.text("表示中のモデルはありません。", NamedTextColor.GRAY));
            return 0;
        }
        if (!plugin.renderer().canDelete(player, session)) {
            sender.sendMessage(Component.text("このモデルを削除する権限がありません。",
                    NamedTextColor.RED));
            return 0;
        }
        int id = session.id();
        int removed = plugin.renderer().clear(player);
        sender.sendMessage(Component.text("モデル #" + id + " を削除しました (" + removed + " entities).",
                NamedTextColor.GREEN));
        return removed;
    }

    private int clearAll(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        int removed = plugin.renderer().clearAll(player);
        sender.sendMessage(Component.text("モデルを削除しました (" + removed + " entities).",
                NamedTextColor.GREEN));
        return removed;
    }

    private int teleport(CommandSender sender, int id) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        try {
            plugin.renderer().teleport(player, id);
            sender.sendMessage(Component.text("モデル #" + id + " の近くに移動しました。",
                    NamedTextColor.GREEN));
            return 1;
        } catch (IllegalStateException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return 0;
        }
    }

    private int share(CommandSender sender, int id, boolean shared) {
        Player player = player(sender);
        if (player == null) {
            return 0;
        }
        try {
            plugin.renderer().setSharedEdit(player, id, shared);
            sender.sendMessage(Component.text("モデル #" + id + " の共有編集を "
                    + (shared ? "ON" : "OFF") + " にしました。", NamedTextColor.GREEN));
            return 1;
        } catch (IllegalStateException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return 0;
        }
    }

    private String ownerName(ViewSession session) {
        if (session.owner() == null) {
            return "不明";
        }
        String name = Bukkit.getOfflinePlayer(session.owner()).getName();
        return name == null ? session.owner().toString() : name;
    }

    private int reload(CommandSender sender) {
        try {
            plugin.reloadPlugin();
            sender.sendMessage(Component.text("Reloaded McMolView (" + plugin.repository().keys().size()
                    + " file(s)).", NamedTextColor.GREEN));
            return 1;
        } catch (RuntimeException e) {
            sender.sendMessage(Component.text("Reload failed: " + message(e), NamedTextColor.RED));
            return 0;
        }
    }

    private CompletableFuture<Suggestions> suggestFiles(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        int added = 0;
        for (String key : plugin.repository().keys()) {
            if (key.toLowerCase(Locale.ROOT).contains(remaining)) {
                builder.suggest(key);
                added++;
                if (added >= 50) {
                    break;
                }
            }
        }
        return builder.buildFuture();
    }

    private CommandSender sender(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getExecutor() instanceof Player player) {
            return player;
        }
        return context.getSource().getSender();
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text("This command must be run by a player.", NamedTextColor.RED));
        return null;
    }

    private String message(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
