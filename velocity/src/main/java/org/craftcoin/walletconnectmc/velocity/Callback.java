package org.craftcoin.walletconnectmc.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.craftcoin.walletconnectmc.Constants;
import org.craftcoin.walletconnectmc.Utils;
import org.craftcoin.walletconnectmc.UuidToAddressMapping;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.walletconnect.Session;
import org.web3j.utils.Numeric;
import org.craftcoin.walletconnectmc.velocity.events.WCSessionReadyEvent;
import org.craftcoin.walletconnectmc.velocity.events.WCSessionStatusChangeEvent;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public record Callback(Player player,
                       WalletConnectVelocity plugin,
                       Session session) implements Session.Callback {

  @Override
  public void onMethodCall(@NotNull Session.MethodCall methodCall) {

  }

  @Override
  public void onStatus(@NotNull Session.Status status) {
    plugin.getProxy().getEventManager().fire(new WCSessionStatusChangeEvent(player, status, session));

    if (status instanceof Session.Status.Closed
        || status instanceof Session.Status.Disconnected
        || status instanceof Session.Status.Error) {
      plugin.removeSession(player);
      if (player.isActive()) {
        player.disconnect(MiniMessage.miniMessage().deserialize(plugin.getMessage("rejected"),
            WalletConnectVelocity.getPlaceholders(player)));
      }
    } else if (status instanceof Session.Status.Approved) {
      player.getCurrentServer()
          .orElseThrow()
          .getServer()
          .sendPluginMessage(MinecraftChannelIdentifier.create(
              Constants.PLUGIN_MESSAGE_CHANNEL_NAMESPACE,
              Constants.PLUGIN_MESSAGE_CHANNEL_AUTH_APPROVED), new byte[0]);
      String now = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(LocalDateTime.now());
      if (session.approvedAccounts() == null) {
        player.disconnect(MiniMessage.miniMessage().deserialize(plugin.getMessage("rejected"),
            WalletConnectVelocity.getPlaceholders(player)));
        return;
      }
      List<byte[]> accounts = session.approvedAccounts()
          .stream()
          .map(Numeric::hexStringToByteArray)
          .toList();
      byte[] account = plugin.getAddressBlocking(player.getUniqueId());
      boolean register = false;
      if (account == null) {
        register = true;
        account = accounts.get(0);
      }
      byte[] finalAccount = account;
      if (accounts.stream().noneMatch(acc -> Arrays.equals(acc, finalAccount))) {
        player.disconnect(MiniMessage.miniMessage().deserialize(plugin.getMessage("different-account"),
            Placeholder.unparsed("account", Numeric.toHexString(finalAccount).substring(0, 8)),
            WalletConnectVelocity.getPlaceholders(player)));
        return;
      }
      String message = PlainTextComponentSerializer
          .plainText()
          .serialize(MiniMessage.builder()
              .editTags(tags -> tags
                  .resolver(Placeholder.unparsed("server", plugin.serverName))
                  .resolver(Placeholder.unparsed("time", now))
                  .resolver(WalletConnectVelocity.getPlaceholders(player)))
              .build().deserialize(plugin.getMessage("message-to-sign")));
      byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
      boolean finalRegister = register;
      session.performMethodCall(
          new Session.MethodCall.Custom(
              Utils.createCallId(),
              "personal_sign",
              Arrays.asList(Numeric.toHexString(bytes), Numeric.toHexString(account))
          ),
          response -> {
            if (response.component3() != null) {
              player.disconnect(MiniMessage.miniMessage().deserialize(plugin
                      .getMessage("sign-rejected"),
                  WalletConnectVelocity.getPlaceholders(player)));
              return null;
            }
            String signature = response.component2().toString();
            try {
              if (!Utils.checkSignature(finalAccount,
                  bytes,
                  Numeric.hexStringToByteArray(signature))) {
                player.disconnect(MiniMessage.miniMessage().deserialize(plugin
                        .getMessage("invalid-signature"),
                    WalletConnectVelocity.getPlaceholders(player)));
                return null;
              }
              WCSessionReadyEvent event = new WCSessionReadyEvent(player, session);
              plugin.getProxy().getEventManager().fire(event);
              if (!event.isCancelled()) {
                if (finalRegister) {
                  plugin.connection.getSession().getTransaction().begin();
                  plugin.connection.getSession().persist(new UuidToAddressMapping(player.getUniqueId(),
                      finalAccount));
                  plugin.connection.getSession().getTransaction().commit();
                }
                player.getCurrentServer()
                    .get()
                    .getServer()
                    .sendPluginMessage(MinecraftChannelIdentifier.create(
                        Constants.PLUGIN_MESSAGE_CHANNEL_NAMESPACE,
                        Constants.PLUGIN_MESSAGE_CHANNEL_AUTH_DONE), new byte[0]);
                plugin.loggedIn.add(player);
                plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                  player.createConnectionRequest(plugin.getProxy().getServer(plugin
                      .getAfterAuthServer()).orElseThrow()).connect();
                }).delay(1, TimeUnit.SECONDS).schedule();
              }
            } catch (Exception ignored) {
              player.disconnect(MiniMessage.miniMessage().deserialize(plugin
                      .getMessage("invalid signature"),
                  WalletConnectVelocity.getPlaceholders(player)));
            }
            return null;
          });
    }
  }
}
