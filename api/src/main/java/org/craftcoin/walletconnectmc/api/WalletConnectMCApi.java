package org.craftcoin.walletconnectmc.api;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.web3j.protocol.Web3j;
import org.web3j.tx.TransactionManager;
import org.web3j.utils.Numeric;
import org.craftcoin.walletconnectmc.UuidToAddressMapping;
import org.craftcoin.walletconnectmc.paper.TransactionTransport;
import org.craftcoin.walletconnectmc.paper.WalletConnectMC;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class WalletConnectMCApi {
  final WalletConnectMC plugin;

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
  public CompletableFuture<byte[]> getAddress(OfflinePlayer player) {
    return getAddress(player.getUniqueId());
  }

  /**
   * Makes an asynchronous request to the database to get player's address.
   *
   * @param player The player's UUID.
   * @return Player's address or null if none.
   */
  public CompletableFuture<byte[]> getAddress(UUID player) {
    CompletableFuture<byte[]> future = new CompletableFuture<>();
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
   * @return The transaction manager.
   */
  public CompletableFuture<TransactionManager> getTransactionManager(Player player) {
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
  public CompletableFuture<Optional<UUID>> getSinglePlayerByAddress(byte[] address) {
    return getPlayerAccountsByAddress(address).thenApply(accounts -> {
      System.out.println(accounts);
      if (accounts.size() == 1) {
        return Optional.of(accounts.get(0));
      } else {
        return Optional.empty();
      }
    });
  }

  /**
   * Gets player accounts by address.
   *
   * @param address Player's address.
   * @return Account UUIDs.
   */
  public CompletableFuture<List<UUID>> getPlayerAccountsByAddress(byte[] address) {
    CompletableFuture<List<UUID>> future = new CompletableFuture<>();
    Bukkit.getScheduler().runTaskAsynchronously(WalletConnectMC.getInstance(), () -> {
      Session session = WalletConnectMC.getInstance().getConnection().getSession();
      CriteriaBuilder builder = session.getCriteriaBuilder();
      CriteriaQuery<UuidToAddressMapping> query = builder.createQuery(UuidToAddressMapping.class);
      Root<UuidToAddressMapping> root = query.from(UuidToAddressMapping.class);
      query.select(root).where(builder.equal(root.get("address"), address));
      Query<UuidToAddressMapping> q = session.createQuery(query);
      List<UuidToAddressMapping> results = q.getResultList();
      future.complete(results.stream().map(UuidToAddressMapping::getPlayer).collect(Collectors.toList()));
    });
    return future;
  }
}
