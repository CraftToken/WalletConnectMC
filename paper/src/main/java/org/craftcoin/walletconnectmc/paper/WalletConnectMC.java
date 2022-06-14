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

package org.craftcoin.walletconnectmc.paper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.craftcoin.walletconnectmc.Constants;
import org.craftcoin.walletconnectmc.DatabaseConnection;
import org.craftcoin.walletconnectmc.UuidToAddressMapping;
import org.craftcoin.walletconnectmc.paper.auth.AuthHandler;
import org.hibernate.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import me.dreamerzero.miniplaceholders.api.MiniPlaceholders;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class WalletConnectMC extends JavaPlugin implements Listener {
  /* package */ static final char COLON = ':';

  private final File hibernateConfigFile = new File(getDataFolder(), "hibernate.cfg.xml");
  private Web3j web3j;
  private long chainId;
  private final TransactionTransport transactionTransport = new TransactionTransport();
  private DatabaseConnection connection;

  public static WalletConnectMC getInstance() {
    return getPlugin(WalletConnectMC.class);
  }

  // It's ok to GC once on startup
  @SuppressWarnings("PMD.AvoidFileStream")
  @Override
  public void onEnable() {
    saveDefaultConfig();
    web3j = Web3j.build(new HttpService(getConfig().getString("rpc url")));
    web3j.ethChainId().sendAsync().thenAccept(res -> chainId = res.getChainId().longValue());

    try {
      if (!hibernateConfigFile.exists()) {
        hibernateConfigFile.createNewFile();
        try (InputStream hibernateConfig = getResource("default-hibernate.cfg.xml")) {
          hibernateConfig.transferTo(new FileOutputStream(hibernateConfigFile));
        }
      }
    } catch (IOException exception) {
      getLogger().severe(exception.toString());
    }
    connection = DatabaseConnection.connect(hibernateConfigFile);

    getServer().getPluginManager().registerEvents(this, this);
    getServer().getMessenger().registerIncomingPluginChannel(this,
        Constants.PMC_NAMESPACE + COLON + Constants.PMC_TX_RESPONSE,
        transactionTransport);
    getServer().getMessenger().registerOutgoingPluginChannel(this,
        Constants.PMC_NAMESPACE + COLON + Constants.PMC_TX_REQUEST);

    final AuthHandler authHandler = new AuthHandler();
    this.getServer().getMessenger().registerIncomingPluginChannel(this,
        Constants.PMC_NAMESPACE + COLON + Constants.PMC_AUTH_SHOW_QR_CODE,
        authHandler);
    this.getServer().getMessenger().registerIncomingPluginChannel(this,
        Constants.PMC_NAMESPACE + COLON + Constants.PMC_AUTH_APPROVED,
        authHandler);
    this.getServer().getMessenger().registerIncomingPluginChannel(this,
        Constants.PMC_NAMESPACE + COLON + Constants.PMC_AUTH_DONE,
        authHandler);
  }

  @Override
  public void onDisable() {
    connection.close();
    web3j.shutdown();
  }

  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  public byte[] getAddressBlocking(final UUID player) {
    try (Session session = connection.openSession()) {
      final UuidToAddressMapping entry = session.get(UuidToAddressMapping.class, player);
      return entry == null ? null : entry.getAddress();
    } catch (Exception exception) {
      getLogger().severe(exception.toString());
      throw exception;
    }
  }

  public Web3j getWeb3() {
    return web3j;
  }

  public long getChainId() {
    return chainId;
  }

  public TransactionTransport getTransactionTransport() {
    return transactionTransport;
  }

  @EventHandler
  public void onJoin(final PlayerJoinEvent event) {
    event.getPlayer().resetTitle();
  }

  public String getMessage(final String path) {
    return getConfig().getString("messages." + path);
  }

  public DatabaseConnection getConnection() {
    return connection;
  }

  @NotNull
  public static TagResolver getPlaceholders(@Nullable final Player player) {
    TagResolver resolver = TagResolver.empty();
    if (Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")) {
      resolver = player == null
          ? MiniPlaceholders.getGlobalPlaceholders()
          : MiniPlaceholders.getAudienceGlobalPlaceholders(player);
    }
    return resolver;
  }
}
