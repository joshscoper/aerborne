package net.aerborne.client.protocol;

/** Presentation availability only. This never grants server permissions or trusts client gameplay state. */
public final class ClientAccess {
    private ClientAccess() { }
    public static boolean localToolsAvailable(boolean connected, String address, boolean advertised) {
        return connected && (advertised || ClientPreferences.isDefaultServer(address));
    }
}
