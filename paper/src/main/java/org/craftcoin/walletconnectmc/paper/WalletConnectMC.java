package org.craftcoin.walletconnectmc.paper;

import me.dreamerzero.miniplaceholders.api.MiniPlaceholders;
import org.craftcoin.walletconnectmc.Constants;
import org.craftcoin.walletconnectmc.DatabaseConnection;
import org.craftcoin.walletconnectmc.UuidToAddressMapping;
import org.craftcoin.walletconnectmc.paper.auth.AuthHandler;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

public class WalletConnectMC extends JavaPlugin implements Listener {
  private final File hibernateConfigFile = new File(getDataFolder(), "hibernate.cfg.xml");
  private Web3j web3j;
  private long chainId;
  private final TransactionTransport transactionTransport = new TransactionTransport();
  private DatabaseConnection connection;

  public static WalletConnectMC getInstance() {
    return getPlugin(WalletConnectMC.class);
  }

  @Override
  public void onEnable() {
    saveDefaultConfig();
    web3j = Web3j.build(new HttpService(getConfig().getString("rpc url")));
    web3j.ethChainId().sendAsync().thenAccept(res -> chainId = res.getChainId().longValue());

    try {
      if (!hibernateConfigFile.exists()) {
        hibernateConfigFile.createNewFile();
        InputStream hibernateConfig = getResource("default-hibernate.cfg.xml");
        assert hibernateConfig != null;
        hibernateConfig.transferTo(new FileOutputStream(hibernateConfigFile));
        hibernateConfig.close();
      }
    } catch (Exception exception) {
      exception.printStackTrace();
    }
    connection = DatabaseConnection.connect(hibernateConfigFile);

    getServer().getPluginManager().registerEvents(this, this);
    getServer().getMessenger().registerIncomingPluginChannel(this,
        Constants.PLUGIN_MESSAGE_CHANNEL_NAMESPACE + ":" + Constants.PLUGIN_MESSAGE_CHANNEL_TX_RESPONSE,
        transactionTransport);
    getServer().getMessenger().registerOutgoingPluginChannel(this,
        Constants.PLUGIN_MESSAGE_CHANNEL_NAMESPACE + ":" + Constants.PLUGIN_MESSAGE_CHANNEL_TX_REQUEST);

    AuthHandler authHandler = new AuthHandler();
    this.getServer().getMessenger().registerIncomingPluginChannel(this,
        Constants.PLUGIN_MESSAGE_CHANNEL_NAMESPACE + ":" + Constants.PLUGIN_MESSAGE_CHANNEL_AUTH_SHOW_QR_CODE,
        authHandler);
    this.getServer().getMessenger().registerIncomingPluginChannel(this,
        Constants.PLUGIN_MESSAGE_CHANNEL_NAMESPACE + ":" + Constants.PLUGIN_MESSAGE_CHANNEL_AUTH_APPROVED,
        authHandler);
    this.getServer().getMessenger().registerIncomingPluginChannel(this,
        Constants.PLUGIN_MESSAGE_CHANNEL_NAMESPACE + ":" + Constants.PLUGIN_MESSAGE_CHANNEL_AUTH_DONE,
        authHandler);
  }

  @Override
  public void onDisable() {
    connection.close();
    web3j.shutdown();
  }

  public byte[] getAddressBlocking(UUID player) {
    try {
      UuidToAddressMapping entry = connection.getSession().get(UuidToAddressMapping.class, player);
      return entry == null ? null : entry.getAddress();
    } catch (Exception exception) {
      exception.printStackTrace();
      throw new RuntimeException(exception);
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
  public void onJoin(PlayerJoinEvent event) {
    event.getPlayer().resetTitle();
  }

  public String getMessage(String path) {
    return getConfig().getString("messages." + path);
  }

  public DatabaseConnection getConnection() {
    return connection;
  }

  @NotNull
  public static TagResolver getPlaceholders(@Nullable Player player) {
    if(Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")) {
      return player == null ? MiniPlaceholders.getGlobalPlaceholders() : MiniPlaceholders.getAudienceGlobalPlaceholders(player);
    } else {
      return TagResolver.empty();
    }
  }
}
