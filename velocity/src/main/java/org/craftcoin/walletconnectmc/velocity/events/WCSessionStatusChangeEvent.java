package org.craftcoin.walletconnectmc.velocity.events;

import com.velocitypowered.api.proxy.Player;
import org.walletconnect.Session;

/**
 * Called when a user approves/rejects the session request or disconnects
 */
public record WCSessionStatusChangeEvent(Player player, Session.Status status, Session session) {
}
