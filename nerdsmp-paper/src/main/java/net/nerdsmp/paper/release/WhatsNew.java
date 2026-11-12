package net.nerdsmp.paper.release;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.ClientFeeds;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The "What's New" book. After each release, players who haven't read this version's notes get a nudge a few seconds
 * after they join; /whatsnew opens the book any time. The notes live in content/whats-new.yml.
 */
public final class WhatsNew implements Listener {
    /** A loaded set of notes: its version, title and book pages. */
    public record Notes(String version, String title, List<String> pages) { }

    static final String SEEN = "whats-new.seen";

    private final NerdSmpPlugin plugin;
    private final Notes notes;

    public WhatsNew(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        Notes loaded;
        try (var reader = new InputStreamReader(plugin.getResource("content/whats-new.yml"), StandardCharsets.UTF_8)) {
            loaded = parse(YamlConfiguration.loadConfiguration(reader));
        } catch (Exception missing) {
            plugin.getLogger().warning("What's New notes could not be read: " + missing);
            loaded = new Notes("", "What's New", List.of());
        }
        this.notes = loaded;
    }

    /** Reads the notes file: each page is its heading, a blank line, then its lines. */
    public static Notes parse(YamlConfiguration yaml) {
        List<String> pages = new ArrayList<>();
        for (var page : yaml.getMapList("pages")) {
            StringBuilder text = new StringBuilder(String.valueOf(page.get("heading") == null ? "" : page.get("heading")));
            Object lines = page.get("lines");
            if (lines instanceof List<?> list) {
                text.append("\n");
                for (Object line : list) text.append("\n").append(line == null ? "" : line);
            }
            pages.add(text.toString());
        }
        return new Notes(yaml.getString("version", ""), yaml.getString("title", "What's New"), List.copyOf(pages));
    }

    public Notes notes() { return notes; }

    public void open(Player player) {
        List<Component> pages = notes.pages().stream().map(Chat::render).toList();
        player.openBook(Book.book(Chat.render(notes.title()), Component.text("Aerborne"), pages.isEmpty() ? List.of(Component.text("Nothing new yet.")) : pages));
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account != null) plugin.playerData().update(account, yaml -> yaml.set(SEEN, notes.version()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (notes.version().isEmpty() || notes.pages().isEmpty()) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
            if (account == null || !player.hasPlayedBefore()) return;
            String seen = plugin.playerData().read(account, yaml -> yaml.getString(SEEN, ""));
            if (notes.version().equals(seen)) return;
            Chat.send(player, "<gold>✦ New on Aerborne:</gold> <white>" + notes.title() + "</white> <click:run_command:'/whatsnew'><yellow>[Read what's new]</yellow></click>");
            ClientFeeds.toast(player, "quest", Chat.render("<gold>What's New</gold>"), Chat.render("<white>" + notes.title() + "</white> <gray>· /whatsnew</gray>"));
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8F, 1F);
        }, 100L);
    }
}
