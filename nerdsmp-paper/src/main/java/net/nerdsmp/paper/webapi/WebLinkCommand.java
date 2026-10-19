package net.nerdsmp.paper.webapi;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /weblink: shows the player a private code that proves their Minecraft account to the website. */
public final class WebLinkCommand {
    private WebLinkCommand() {
    }

    public static boolean run(NerdSmpPlugin plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Chat.playersOnly(sender);
            return true;
        }
        WebApi api = plugin.webApi();
        if (api == null) {
            Chat.error(player, "Website linking isn't available on this server right now.");
            return true;
        }
        var account = plugin.sessions().account(player.getUniqueId());
        if (account.isEmpty()) {
            Chat.error(player, "Your profile is still loading. Try again in a moment.");
            return true;
        }
        WebLinkCodes.Link link;
        try {
            link = api.links().issue(account.get().toString(), player.getUniqueId(), player.getName());
        } catch (IllegalStateException busy) {
            Chat.error(player, "Too many link codes are waiting right now. Try again in a few minutes.");
            return true;
        }
        String code = link.code().substring(0, 4) + "-" + link.code().substring(4);
        Chat.success(player, "Your website link code is <gold><bold>" + code + "</bold></gold>.");
        Chat.info(player, "Enter it on the website within 10 minutes. Never share it: staff will never ask for it.");
        return true;
    }
}
