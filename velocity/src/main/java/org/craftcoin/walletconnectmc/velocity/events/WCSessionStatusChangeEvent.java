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
 * Called when a user approves/rejects the session request or disconnects.
 *
 * @param player The user.
 * @param session The user's session.
 * @param status The status.
 */
public record WCSessionStatusChangeEvent(Player player, Session.Status status, Session session) {
}
