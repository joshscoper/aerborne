package net.nerdsmp.paper.webapi;

import com.google.gson.JsonObject;
import net.nerdsmp.paper.NerdSmpPlugin;

/**
 * The website's world feed (/api/v1/world): Bazaar prices, the Reach Calendar and the Wardens' Council. Built on the
 * game thread with the other snapshots; each part is left out when its service is off.
 */
final class WorldJson {
    private WorldJson() { }

    static String build(NerdSmpPlugin plugin, String sampled) {
        JsonObject root = new JsonObject();
        root.addProperty("apiVersion", 1);
        root.addProperty("ready", true);
        root.addProperty("sampledAt", sampled);
        if (plugin.bazaar() != null) {
            JsonObject bazaar = new JsonObject();
            bazaar.addProperty("taxPercent", plugin.bazaar().tax() * 100);
            bazaar.add("products", plugin.bazaar().prices());
            root.add("bazaar", bazaar);
        }
        if (plugin.calendar() != null) root.add("calendar", plugin.calendar().json());
        if (plugin.council() != null) root.add("council", plugin.council().json());
        return root.toString();
    }
}
