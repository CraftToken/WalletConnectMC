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

package org.craftcoin.walletconnectmc.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.craftcoin.walletconnectmc.UuidToAddressMapping;
import org.craftcoin.walletconnectmc.paper.TransactionTransport;
import org.craftcoin.walletconnectmc.paper.WalletConnectMC;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.jetbrains.annotations.NotNull;
import org.web3j.protocol.Web3j;
import org.web3j.tx.TransactionManager;
import org.web3j.utils.Numeric;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

public class WalletConnectMCApi {
  private final WalletConnectMC plugin;

  /**
   * Creates an API instance.
   */
  public WalletConnectMCApi() {
    plugin = WalletConnectMC.getInstance();
  }

  /**
   * Makes an asynchronous request to the database to get player's address.
   *
   * @param player The player.
   * @return Player's address or null if none.
   */
  public CompletableFuture<byte[]> getAddress(final OfflinePlayer player) {
    return getAddress(player.getUniqueId());
  }

  /**
   * Makes an asynchronous request to the database to get player's address.
   *
   * @param player The player's UUID.
   * @return Player's address or null if none.
   */
  public CompletableFuture<byte[]> getAddress(final UUID player) {
    final CompletableFuture<byte[]> future = new CompletableFuture<>();
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      future.complete(plugin.getAddressBlocking(player));
    });
    return future;
  }

  /**
   * Gets the {@link Web3j}.
   *
   * @return The web3j instance.
   */
  public Web3j getWeb3() {
    return plugin.getWeb3();
  }

  /**
   * Gets the {@link TransactionManager} that can be used to
   * send transactions on behalf of the player.
   *
   * @param player The player who will send transactions.
   * @return The transaction manager.
   */
  public CompletableFuture<TransactionManager> getTransactionManager(final Player player) {
    return getAddress(player)
        .thenApply(address -> new WCTransactionManager(this, player, Numeric.toHexString(address)));
  }

  /**
   * Gets the low-level plugin message transport layer.
   *
   * @return The transaction transport.
   */
  public TransactionTransport getTransactionTransport() {
    return WalletConnectMC.getInstance().getTransactionTransport();
  }

  /**
   * Gets the single player by address.
   *
   * @param address Player's address.
   * @return Player's UUID if exactly one player. If 0 or 2+, returns empty.
   */
  public CompletableFuture<Optional<UUID>> getSinglePlayerByAddress(final byte @NotNull []
                                                                        address) {
    return getPlayerAccountsByAddress(address).thenApply(accounts -> {
      return Optional.ofNullable(accounts.size() == 1 ? accounts.get(0) : null);
    });
  }

  /**
   * Gets player accounts by address.
   *
   * @param address Player's address.
   * @return Account UUIDs.
   */
  public CompletableFuture<List<UUID>> getPlayerAccountsByAddress(final byte @NotNull [] address) {
    final CompletableFuture<List<UUID>> future = new CompletableFuture<>();
    Bukkit.getScheduler().runTaskAsynchronously(WalletConnectMC.getInstance(), () -> {
      final Session session = WalletConnectMC.getInstance().getConnection().getSession();
      final CriteriaBuilder builder = session.getCriteriaBuilder();
      final CriteriaQuery<UuidToAddressMapping> criteriaQuery = builder
          .createQuery(UuidToAddressMapping.class);
      final Root<UuidToAddressMapping> root = criteriaQuery.from(UuidToAddressMapping.class);
      criteriaQuery.select(root).where(builder.equal(root.get("address"), address));
      final Query<UuidToAddressMapping> query = session.createQuery(criteriaQuery);
      final List<UuidToAddressMapping> results = query.getResultList();
      future.complete(results.stream()
          .map(UuidToAddressMapping::getPlayer)
          .collect(Collectors.toList()));
    });
    return future;
  }
}
