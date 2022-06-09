// WalletConnectMC
// Copyright (C) 2022  CraftCoin
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package org.craftcoin.walletconnectmc.velocity.events;

import org.walletconnect.Session;

import com.velocitypowered.api.proxy.Player;

/**
 * Called when a player approves session and signs the authorization message.
 * Interrupts the login process if cancelled.
 */
@SuppressWarnings("PMD.DataClass")
public class WCSessionReadyEvent {
  private final Player player;
  private final Session session;
  private boolean cancelled;

  public WCSessionReadyEvent(final Player player, final Session session) {
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

  public void setCancelled(final boolean cancel) {
    this.cancelled = cancel;
  }
}
