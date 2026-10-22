package net.nerdsmp.paper.expedition;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.expedition.content.Difficulty;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Room;
import net.nerdsmp.paper.expedition.content.Tier;
import net.nerdsmp.paper.presentation.Chat;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's guide to one expedition as a written book: rating and basics, the staff-written
 * overview and tips, each room, the boss, every mob with its possible drops, and the rewards.
 */
final class ExpeditionGuideBook {
    private static final int WIDTH = 19;
    private static final int LINES = 13;

    private final List<List<String>> pages = new ArrayList<>();
    private List<String> page = new ArrayList<>();

    private ExpeditionGuideBook() { }

    static Book build(NerdSmpPlugin plugin, ExpeditionService service, ExpeditionDefinition expedition) {
        ExpeditionGuideBook book = new ExpeditionGuideBook();
        int rating = Difficulty.rating(expedition);
        var guide = expedition.guide();
        book.heading(expedition.title());
        book.line("<gold>" + Difficulty.stars(rating) + "</gold>");
        book.text("dark_gray", Difficulty.label(rating) + " · " + switch (expedition.dimension()) {
            case NETHER -> "The Nether";
            case END -> "The End";
            case OVERWORLD -> "Overworld";
        });
        book.text("black", "Level " + expedition.level() + " · " + (expedition.minParty() == expedition.maxParty() ? expedition.maxParty()
            : expedition.minParty() + "-" + expedition.maxParty()) + " players · " + expedition.rooms().size() + " rooms · "
            + expedition.timeLimitMinutes() + " min");
        if (!guide.overview().isBlank()) {
            book.gap();
            book.text("black", guide.overview());
        } else if (!expedition.description().isBlank()) {
            book.gap();
            book.text("black", expedition.description());
        }
        if (!guide.lore().isBlank()) {
            book.newPage();
            book.heading("The story");
            book.text("dark_gray", guide.lore());
        }
        if (!guide.tips().isEmpty()) {
            book.newPage();
            book.heading("Tips");
            for (String tip : guide.tips()) book.text("black", "• " + tip);
        }
        book.newPage();
        book.heading("The route");
        int index = 1;
        for (Room room : expedition.roomList()) {
            String kind = switch (room.encounter().type()) {
                case WAVES -> room.encounter().waves().size() + " waves";
                case SURVIVE -> "survive " + room.encounter().surviveSeconds() + "s";
                case CLEAR -> "clear every enemy";
                case BOSS -> "boss";
                case COUNCIL -> room.encounter().bosses().size() + " bosses at once";
                case PUZZLE -> "puzzle";
                case NONE -> room.puzzles().isEmpty() ? "passage" : "puzzle";
            } + (room.optional() ? ", optional" : "");
            book.text("dark_blue", index++ + ". " + room.title() + " (" + kind + ")");
            String tip = guide.rooms().get(room.id());
            if (tip != null && !tip.isBlank()) book.text("black", tip);
        }
        if (!guide.boss().isBlank()) {
            book.newPage();
            book.heading("The boss");
            book.text("black", guide.boss());
        }
        for (ExpeditionBestiary.Entry entry : ExpeditionBestiary.of(plugin, expedition)) {
            book.newPage();
            book.heading(entry.name());
            book.text(entry.role().equals("Boss") ? "dark_red" : "dark_gray", entry.role() + " · " + String.join(", ", entry.rooms()));
            if (entry.health() != null) book.text("black", "Health " + Math.round(entry.health()) + (entry.damage() == null ? "" : " · hits " + Math.round(entry.damage())));
            if (!entry.ability().isBlank()) book.text("dark_purple", entry.ability());
            if (!entry.drops().isEmpty()) {
                book.text("dark_green", "Can drop:");
                for (var drop : entry.drops().subList(0, Math.min(5, entry.drops().size())))
                    book.text("black", "• " + drop.item() + " " + ExpeditionBestiary.amount(drop) + " " + ExpeditionBestiary.percent(drop.chance()));
            }
        }
        book.newPage();
        book.heading("Rewards");
        var loot = expedition.loot();
        if (loot.marks() > 0) book.text("black", loot.marks() + " Marks (more on harder tiers)");
        loot.items().forEach((item, amount) -> book.text("black", "• " + amount + "× " + itemName(plugin, item)));
        if (loot.profile() != null) book.text("black", "• Loot rolls from " + Chat.humanize(loot.profile()) + ", once per tier roll");
        var table = service.lootTable(expedition.id());
        if (!table.materials().isEmpty()) {
            book.newPage();
            book.heading("Expedition loot");
            book.text("dark_green", "Materials:");
            for (String material : table.materials()) book.text("black", "• " + ExpeditionBestiary.name(plugin, material));
            if (!table.gear().isEmpty()) {
                book.text("dark_green", "Set: " + table.gear().size() + " pieces, sealed until identified. Forge them from the materials with /expedition forge.");
            }
            if (!table.signatures().isEmpty())
                book.text("dark_purple", "Rare: " + String.join(", ", table.signatures().stream().map(id -> ExpeditionBestiary.name(plugin, id)).toList()));
            book.text("dark_gray", "Crates, chests and hidden caches pay every player once. Win for a reward chest graded D to S+ by speed, survival and secrets, and a bonus chest for Marks.");
        }
        book.newPage();
        for (Tier tier : service.tiersFor(expedition))
            book.text("dark_gray", tier.title() + ": level " + (expedition.level() + tier.levelBonus()) + ", " + tier.lives() + " lives, "
                + tier.lootRolls() + (tier.lootRolls() == 1 ? " roll" : " rolls"));
        book.flush();
        List<Component> rendered = book.pages.stream().map(lines -> Chat.render(String.join("\n", lines))).toList();
        return Book.book(Chat.render(expedition.title()), Chat.render("Aerborne"), rendered);
    }

    private static String itemName(NerdSmpPlugin plugin, String id) {
        try {
            var name = plugin.items().itemName(new net.nerdsmp.api.model.ItemId(id));
            if (name.isPresent()) return ExpeditionBestiary.plain(name.get());
        } catch (IllegalArgumentException invalid) {
            // Fall through to the material name.
        }
        return Chat.humanize(id);
    }

    private void heading(String text) {
        for (String line : wrap(text)) add("<dark_aqua><bold>" + Chat.escape(line) + "</bold></dark_aqua>");
    }

    private void text(String color, String text) {
        for (String line : wrap(text)) add("<" + color + ">" + Chat.escape(line) + "</" + color + ">");
    }

    private void line(String markup) { add(markup); }

    private void gap() { if (!page.isEmpty()) add(""); }

    private void add(String markup) {
        if (page.size() >= LINES) newPage();
        page.add(markup);
    }

    private void newPage() {
        if (!page.isEmpty()) pages.add(page);
        page = new ArrayList<>();
    }

    private void flush() { newPage(); }

    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) continue;
            while (word.length() > WIDTH) {
                if (!line.isEmpty()) { lines.add(line.toString()); line.setLength(0); }
                lines.add(word.substring(0, WIDTH));
                word = word.substring(WIDTH);
            }
            if (line.length() + word.length() + (line.isEmpty() ? 0 : 1) > WIDTH) { lines.add(line.toString()); line.setLength(0); }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
