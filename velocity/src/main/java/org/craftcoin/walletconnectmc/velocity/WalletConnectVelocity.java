package org.craftcoin.walletconnectmc.velocity;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.squareup.moshi.Moshi;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.dreamerzero.miniplaceholders.api.MiniPlaceholders;
import org.craftcoin.walletconnectmc.Constants;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.walletconnect.Session;
import org.walletconnect.impls.MoshiPayloadAdapter;
import org.walletconnect.impls.OkHttpTransport;
import org.walletconnect.impls.WCSession;
import org.walletconnect.impls.WCSessionStore;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;
import org.craftcoin.walletconnectmc.DatabaseConnection;
import org.craftcoin.walletconnectmc.UuidToAddressMapping;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Plugin(id = "walletconnectmc",
    name = "WalletConnectMC",
    version = "0.1.0-SNAPSHOT",
    description = "WalletConnect integration",
    dependencies = {@Dependency(id = "miniplaceholders", optional = true)},
    authors = {"Sliman4"})
public class WalletConnectVelocity {
  private static WalletConnectVelocity instance;
  private final ProxyServer proxyServer;
  private final Path dataFolder;
  private final Moshi moshi = new Moshi.Builder().build();
  private final OkHttpClient client = new OkHttpClient.Builder().build();
  private final Map<Player, Session> sessions = new HashMap<>();
  DatabaseConnection connection;
  HashSet<Player> loggedIn = new HashSet<>();
  HashSet<UUID> loggingOut = new HashSet<>();
  String serverName;
  private final File hibernateConfigFile;
  private String serverDescription;
  private String projectId;
  private List<String> serverIcons;
  private String serverUrl;
  private String authServer;
  private String afterAuthServer;
  private long authTimeout;
  private Toml config;
  private Web3j web3j;
  private long chainId;

  @Inject
  public WalletConnectVelocity(ProxyServer proxyServer, @DataDirectory Path dataFolder) {
    this.proxyServer = proxyServer;
    this.dataFolder = dataFolder;
    this.hibernateConfigFile = new File(dataFolder.toFile(), "hibernate.cfg.xml");
    instance = this;
  }

  @Subscribe
  public void onProxyInitialization(ProxyInitializeEvent event) {
    try {
      proxyServer.getChannelRegistrar().register(MinecraftChannelIdentifier.create(
          Constants.PLUGIN_MESSAGE_CHANNEL_NAMESPACE,
          Constants.PLUGIN_MESSAGE_CHANNEL_TX_REQUEST));
      proxyServer.getEventManager().register(this, new TransactionTransportVelocity());

      if (!dataFolder.toFile().exists()) {
        dataFolder.toFile().mkdir();
      }

      File configFile = new File(dataFolder.toFile(), "config.toml");
      if (!configFile.exists()) {
        configFile.createNewFile();
        InputStream config = getClass().getResourceAsStream("/velocity-config.toml");
        assert config != null;
        config.transferTo(new FileOutputStream(configFile));
        config.close();
      }
      config = new Toml().read(configFile);

      if (!hibernateConfigFile.exists()) {
        hibernateConfigFile.createNewFile();
        InputStream hibernateConfig = getClass().getResourceAsStream("/default-hibernate.cfg.xml");
        assert hibernateConfig != null;
        hibernateConfig.transferTo(new FileOutputStream(hibernateConfigFile));
        hibernateConfig.close();
      }
      connection = DatabaseConnection.connect(hibernateConfigFile);

      serverName = config.getString("server.name");
      serverDescription = config.getString("server.description");
      projectId = config.getString("project.id");
      if (projectId != null && projectId.isEmpty()) projectId = null;
      serverIcons = config.getList("server.icons");
      serverUrl = config.getString("server.url");
      authServer = config.getString("auth.server");
      afterAuthServer = config.getString("auth.after-auth-server");
      authTimeout = config.getLong("auth.timeout");

      web3j = Web3j.build(new HttpService(config.getString("rpc url")));
      web3j.ethChainId().sendAsync().thenAccept(res -> chainId = res.getChainId().longValue());
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  @Subscribe
  public void onDisable(ProxyShutdownEvent event) {
    sessions.clear();
    connection.close();
    web3j.shutdown();
  }

  public Web3j getWeb3() {
    return web3j;
  }

  public long getChainId() {
    return chainId;
  }

  @Subscribe
  public void onQuit(DisconnectEvent event) {
    loggingOut.add(event.getPlayer().getUniqueId());
    removeSession(event.getPlayer());
    proxyServer.getScheduler().buildTask(this, () -> {
      loggingOut.remove(event.getPlayer().getUniqueId());
    }).delay(60, TimeUnit.SECONDS).schedule();
  }

  @Subscribe
  public void onJoin(ServerConnectedEvent event) {
    if (sessions.containsKey(event.getPlayer())) {
      event.getPlayer()
          .createConnectionRequest(proxyServer.getServer(afterAuthServer).orElseThrow())
          .connect();
      return;
    }
    if (event.getServer().getServerInfo().getName().equals(authServer)) {
      proxyServer.getScheduler().buildTask(this, () -> {
        new Thread(() -> {
          String url = generateNewSessionURL(event.getPlayer());
          ByteArrayDataOutput out = ByteStreams.newDataOutput();
          out.writeUTF(url);
          event.getServer().sendPluginMessage(MinecraftChannelIdentifier.create(
                  Constants.PLUGIN_MESSAGE_CHANNEL_NAMESPACE,
                  Constants.PLUGIN_MESSAGE_CHANNEL_AUTH_SHOW_QR_CODE),
              out.toByteArray());
        }).start();
      }).delay(500, TimeUnit.MILLISECONDS).schedule();
      getProxy().getScheduler().buildTask(this, () -> {
        if (event.getPlayer().isActive()) {
          if (event.getPlayer()
              .getCurrentServer()
              .map(ServerConnection::getServer)
              .orElse(null)
              == event.getServer()) {
            event.getPlayer().disconnect(MiniMessage.miniMessage().deserialize(
                getMessage("timeout"),
                WalletConnectVelocity.getPlaceholders(event.getPlayer())));
          }
        }
      }).delay(authTimeout, TimeUnit.SECONDS).schedule();
    }
  }

  @Subscribe
  public void onServerChange(ServerPreConnectEvent event) {
    if (!loggedIn.contains(event.getPlayer())) {
      event.getResult().getServer().ifPresent(server -> {
        if (!server.getServerInfo().getName().equals(authServer)) {
          event.setResult(ServerPreConnectEvent.ServerResult.denied());
        }
      });
    }
  }

  public void removeSession(Player player) {
    loggedIn.remove(player);
    Session removed = sessions.remove(player);
    if (removed != null) {
      new Thread(removed::kill).start();
    }
    if (player.getCurrentServer().isPresent() && !player.getCurrentServer().get().getServer().getServerInfo().getName().equals(authServer)) {
      player.createConnectionRequest(proxyServer.getServer(authServer).orElseThrow()).connect();
    }
  }

  public String generateNewSessionURL(Player player) {
    removeSession(player);
    byte[] bytes = new byte[32];
    new Random().nextBytes(bytes);
    String key = Numeric.toHexString(bytes);
    Session.Config config = new Session.Config(UUID.randomUUID().toString(),
        "https://bridge.walletconnect.org",
        key,
        "wc",
        1);
    Session session = new WCSession(config,
        new MoshiPayloadAdapter(moshi),
        new MemoryStore(),
        new OkHttpTransport.Builder(client, moshi),
        new Session.PeerMeta(serverUrl, serverName, serverDescription, serverIcons),
        projectId
    );
    session.addCallback(new Callback(player, this, session));
    session.offer();
    sessions.put(player, session);
    return String.format("wc:%s@%d?bridge=%s&key=%s", config.getHandshakeTopic(), config.getVersion(),
        URLEncoder.encode(config.getBridge(), StandardCharsets.UTF_8), config.getKey());
  }

  public ProxyServer getProxy() {
    return proxyServer;
  }

  public String getAuthServer() {
    return authServer;
  }

  public String getAfterAuthServer() {
    return afterAuthServer;
  }

  public Toml getConfig() {
    return config;
  }

  public Session getSession(Player player) {
    return sessions.get(player);
  }

  public String getMessage(String path) {
    return getConfig().getString("messages." + path);
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

  @NotNull
  public static TagResolver getPlaceholders(@Nullable Player player) {
    if(getInstance().proxyServer.getPluginManager().isLoaded("miniplaceholders")) {
      return player == null ? MiniPlaceholders.getGlobalPlaceholders() : MiniPlaceholders.getAudienceGlobalPlaceholders(player);
    } else {
      return TagResolver.empty();
    }
  }

  public static WalletConnectVelocity getInstance() {
    return instance;
  }

  static class MemoryStore implements WCSessionStore {
    private final List<State> list = new ArrayList<>();

    @NotNull
    @Override
    public List<State> list() {
      return list;
    }

    @Nullable
    @Override
    public State load(@NotNull String handshakeTopic) {
      return list()
          .stream()
          .filter(state -> state.component1().component1().equals(handshakeTopic))
          .findAny()
          .orElse(null);
    }

    @Override
    public void remove(@NotNull String handshakeTopic) {
      list().removeIf(state -> state.component1().component1().equals(handshakeTopic));
    }

    @Override
    public void store(@NotNull String handshakeTopic, @NotNull State state) {
      list().add(state);
    }
  }
}
