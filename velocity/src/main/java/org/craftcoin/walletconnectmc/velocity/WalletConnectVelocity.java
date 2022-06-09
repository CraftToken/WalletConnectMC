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

package org.craftcoin.walletconnectmc.velocity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.craftcoin.walletconnectmc.Constants;
import org.craftcoin.walletconnectmc.DatabaseConnection;
import org.craftcoin.walletconnectmc.UuidToAddressMapping;
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
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import okhttp3.OkHttpClient;

@SuppressWarnings({"PMD.TooManyFields", "PMD.ExcessiveImports"})
@Plugin(id = "walletconnectmc",
    name = "WalletConnectMC",
    version = "0.1.0-SNAPSHOT",
    description = "WalletConnect integration",
    dependencies = @Dependency(id = "miniplaceholders", optional = true),
    authors = "Sliman4")
public class WalletConnectVelocity {
  private static final int SEND_PLUGIN_MESSAGE_DELAY = 500;
  private DatabaseConnection connection;
  private final Set<Player> loggedIn = new HashSet<>();
  private final Set<UUID> loggingOut = new HashSet<>();
  private String serverName;
  private final ProxyServer proxyServer;
  private final Path dataFolder;
  private final Moshi moshi = new Moshi.Builder().build();
  private final OkHttpClient client = new OkHttpClient.Builder().build();
  private final Map<Player, Session> sessions = new ConcurrentHashMap<>();
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
  private final Logger logger;
  private final Random random = new Random();

  @Inject
  public WalletConnectVelocity(final ProxyServer proxyServer,
                               @DataDirectory final Path dataFolder,
                               final Logger logger) {
    this.proxyServer = proxyServer;
    this.dataFolder = dataFolder;
    this.hibernateConfigFile = new File(dataFolder.toFile(), "hibernate.cfg.xml");
    this.logger = logger;
  }

  @SuppressWarnings("PMD.AvoidFileStream")
  @Subscribe
  public void onProxyInitialization(final ProxyInitializeEvent event) {
    try {
      proxyServer.getChannelRegistrar().register(MinecraftChannelIdentifier.create(
          Constants.PMC_NAMESPACE,
          Constants.PMC_TX_REQUEST));
      proxyServer.getEventManager().register(this, new TransactionTransportVelocity(this));

      if (!dataFolder.toFile().exists()) {
        dataFolder.toFile().mkdir();
      }

      final File configFile = new File(dataFolder.toFile(), "config.toml");
      if (!configFile.exists()) {
        configFile.createNewFile();
        try (InputStream config = getClass().getResourceAsStream("/velocity-config.toml")) {
          config.transferTo(new FileOutputStream(configFile));
        }
      }
      config = new Toml().read(configFile);

      if (!hibernateConfigFile.exists()) {
        hibernateConfigFile.createNewFile();
        try (InputStream hibernateConfig = getClass()
            .getResourceAsStream("/default-hibernate.cfg.xml")) {
          hibernateConfig.transferTo(new FileOutputStream(hibernateConfigFile));
        }
      }
      connection = DatabaseConnection.connect(hibernateConfigFile);

      serverName = config.getString("server.name");
      serverDescription = config.getString("server.description");
      projectId = config.getString("project.id");
      if (projectId == null) {
        projectId = "";
      }
      serverIcons = config.getList("server.icons");
      serverUrl = config.getString("server.url");
      authServer = config.getString("auth.server");
      afterAuthServer = config.getString("auth.after-auth-server");
      authTimeout = config.getLong("auth.timeout");

      web3j = Web3j.build(new HttpService(config.getString("rpc url")));
      web3j.ethChainId().sendAsync().thenAccept(res -> chainId = res.getChainId().longValue());
    } catch (IOException exception) {
      if (logger.isLoggable(Level.SEVERE)) {
        logger.severe(exception.toString());
      }
    }
  }

  @Subscribe
  public void onDisable(final ProxyShutdownEvent event) {
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

  @SuppressWarnings("checkstyle:MagicNumber")
  @Subscribe
  public void onQuit(final DisconnectEvent event) {
    loggingOut.add(event.getPlayer().getUniqueId());
    removeSession(event.getPlayer());
    proxyServer.getScheduler().buildTask(this, () -> {
      loggingOut.remove(event.getPlayer().getUniqueId());
    }).delay(60, TimeUnit.SECONDS).schedule();
  }

  @Subscribe
  public void onJoin(final ServerConnectedEvent event) {
    if (sessions.containsKey(event.getPlayer())) {
      event.getPlayer()
          .createConnectionRequest(proxyServer.getServer(afterAuthServer).orElseThrow())
          .connect();
      return;
    }
    if (event.getServer().getServerInfo().getName().equals(authServer)) {
      proxyServer.getScheduler().buildTask(this, () -> {
        new Thread(() -> {
          final String url = generateNewSessionURL(event.getPlayer());
          final ByteArrayDataOutput out = ByteStreams.newDataOutput();
          out.writeUTF(url);
          event.getServer().sendPluginMessage(MinecraftChannelIdentifier.create(
                  Constants.PMC_NAMESPACE,
                  Constants.PMC_AUTH_SHOW_QR_CODE),
              out.toByteArray());
        }).start();
      }).delay(SEND_PLUGIN_MESSAGE_DELAY, TimeUnit.MILLISECONDS).schedule();
      getProxy().getScheduler().buildTask(this, () -> {
        if (event.getPlayer().isActive() && event.getPlayer()
            .getCurrentServer()
            .map(ServerConnection::getServer)
            .orElse(null)
            == event.getServer()) {
          event.getPlayer().disconnect(MiniMessage.miniMessage().deserialize(
              getMessage("timeout"),
              getPlaceholders(event.getPlayer())));
        }
      }).delay(authTimeout, TimeUnit.SECONDS).schedule();
    }
  }

  @Subscribe
  public void onServerChange(final ServerPreConnectEvent event) {
    if (!loggedIn.contains(event.getPlayer())) {
      event.getResult().getServer().ifPresent(server -> {
        if (!server.getServerInfo().getName().equals(authServer)) {
          event.setResult(ServerPreConnectEvent.ServerResult.denied());
        }
      });
    }
  }

  public void removeSession(final Player player) {
    loggedIn.remove(player);
    final Session removed = sessions.remove(player);
    if (removed != null) {
      new Thread(removed::kill).start();
    }
    if (player.getCurrentServer().isPresent()
        && !player.getCurrentServer()
        .get()
        .getServer()
        .getServerInfo()
        .getName()
        .equals(authServer)) {
      player.createConnectionRequest(proxyServer.getServer(authServer).orElseThrow()).connect();
    }
  }

  @SuppressWarnings("checkstyle:MagicNumber")
  public String generateNewSessionURL(final Player player) {
    removeSession(player);
    final byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    final String key = Numeric.toHexString(bytes);
    final Session.Config sessionConfig = new Session.Config(UUID.randomUUID().toString(),
        "https://bridge.walletconnect.org",
        key,
        "wc",
        1);
    final Session session = new WCSession(sessionConfig,
        new MoshiPayloadAdapter(moshi),
        new MemoryStore(),
        new OkHttpTransport.Builder(client, moshi),
        new Session.PeerMeta(serverUrl, serverName, serverDescription, serverIcons),
        projectId.isEmpty() ? null : projectId
    );
    session.addCallback(new Callback(player, this, session));
    session.offer();
    sessions.put(player, session);
    return String.format("wc:%s@%d?bridge=%s&key=%s",
        sessionConfig.getHandshakeTopic(),
        sessionConfig.getVersion(),
        URLEncoder.encode(sessionConfig.getBridge(), StandardCharsets.UTF_8),
        sessionConfig.getKey());
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

  public Session getSession(final Player player) {
    return sessions.get(player);
  }

  public String getMessage(final String path) {
    return getConfig().getString("messages." + path);
  }

  @SuppressWarnings({"checkstyle:IllegalCatch",
      "PMD.AvoidCatchingGenericException",
      "PMD.AvoidThrowingRawExceptionTypes"})
  public byte[] getAddressBlocking(final UUID player) {
    try {
      final UuidToAddressMapping entry = connection.getSession()
          .get(UuidToAddressMapping.class, player);
      return entry == null ? null : entry.getAddress();
    } catch (Exception exception) {
      if (logger.isLoggable(Level.SEVERE)) {
        logger.severe(exception.toString());
      }
      throw new RuntimeException(exception);
    }
  }

  @NotNull
  public TagResolver getPlaceholders(@Nullable final Player player) {
    TagResolver resolver = TagResolver.empty();
    if (proxyServer.getPluginManager().isLoaded("miniplaceholders")) {
      resolver = player == null
          ? MiniPlaceholders.getGlobalPlaceholders()
          : MiniPlaceholders.getAudienceGlobalPlaceholders(player);
    }
    return resolver;
  }

  public DatabaseConnection getConnection() {
    return connection;
  }

  public Set<Player> getLoggedIn() {
    return loggedIn;
  }

  public String getServerName() {
    return serverName;
  }

  private static class MemoryStore implements WCSessionStore {
    private final List<State> sessions = new ArrayList<>();

    @NotNull
    @Override
    public List<State> list() {
      return sessions;
    }

    @Nullable
    @Override
    public State load(@NotNull final String handshakeTopic) {
      return list()
          .stream()
          .filter(state -> state.component1().component1().equals(handshakeTopic))
          .findAny()
          .orElse(null);
    }

    @Override
    public void remove(@NotNull final String handshakeTopic) {
      list().removeIf(state -> state.component1().component1().equals(handshakeTopic));
    }

    @Override
    public void store(@NotNull final String handshakeTopic, @NotNull final State state) {
      list().add(state);
    }
  }
}
