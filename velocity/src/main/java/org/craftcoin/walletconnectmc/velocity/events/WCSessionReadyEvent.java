package org.craftcoin.walletconnectmc.velocity.events;

import com.velocitypowered.api.proxy.Player;
import org.walletconnect.Session;

/**
 * Called when a player approves session and signs the authorization message.
 * Interrupts the login process if cancelled.
 */
public class WCSessionReadyEvent {
  private final Player player;
  private final Session session;
  private boolean cancelled;

  public WCSessionReadyEvent(Player player, Session session) {
    this.player = player;
    this.session = session;
  }

  public Player getPlayer() {
    return player;
  }

  public Session getSession() {
    return session;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public void setCancelled(boolean cancel) {
    this.cancelled = cancel;
  }
}
