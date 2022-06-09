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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.craftcoin.walletconnectmc.Constants;
import org.craftcoin.walletconnectmc.Utils;
import org.craftcoin.walletconnectmc.UuidToAddressMapping;
import org.craftcoin.walletconnectmc.velocity.events.WCSessionReadyEvent;
import org.craftcoin.walletconnectmc.velocity.events.WCSessionStatusChangeEvent;
import org.jetbrains.annotations.NotNull;
import org.walletconnect.Session;
import org.web3j.utils.Numeric;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public record Callback(Player player,
                       WalletConnectVelocity plugin,
                       Session session) implements Session.Callback {

  @Override
  public void onMethodCall(@NotNull final Session.MethodCall methodCall) {

  }

  @SuppressWarnings("checkstyle:IllegalCatch")
  @Override
  public void onStatus(@NotNull final Session.Status status) {
    plugin.getProxy().getEventManager().fire(new WCSessionStatusChangeEvent(player,
        status,
        session));

    if (status instanceof Session.Status.Closed
        || status instanceof Session.Status.Disconnected
        || status instanceof Session.Status.Error) {
      plugin.removeSession(player);
      if (player.isActive()) {
        player.disconnect(MiniMessage.miniMessage().deserialize(plugin.getMessage("rejected"),
            plugin.getPlaceholders(player)));
      }
    } else if (status instanceof Session.Status.Approved) {
      player.getCurrentServer()
          .orElseThrow()
          .getServer()
          .sendPluginMessage(MinecraftChannelIdentifier.create(
              Constants.PMC_NAMESPACE,
              Constants.PMC_AUTH_APPROVED), new byte[0]);
      final List<byte[]> accounts = session.approvedAccounts()
          .stream()
          .map(Numeric::hexStringToByteArray)
          .toList();
      byte[] account = plugin.getAddressBlocking(player.getUniqueId());
      boolean register = false;
      if (account == null) {
        register = true;
        account = accounts.get(0);
      }
      final byte[] finalAccount = account;
      if (accounts.stream().noneMatch(acc -> Arrays.equals(acc, finalAccount))) {
        player.disconnect(MiniMessage.miniMessage().deserialize(plugin
                .getMessage("different-account"),
            Placeholder.unparsed("account", Numeric.toHexString(finalAccount)),
            plugin.getPlaceholders(player)));
        return;
      }
      final String now = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
          .format(LocalDateTime.now());
      final String message = PlainTextComponentSerializer
          .plainText()
          .serialize(MiniMessage.builder()
              .editTags(tags -> {
                tags
                    .resolver(Placeholder.unparsed("server", plugin.getServerName()))
                    .resolver(Placeholder.unparsed("time", now))
                    .resolver(plugin.getPlaceholders(player));
              })
              .build().deserialize(plugin.getMessage("message-to-sign")));
      final byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
      final boolean finalRegister = register;
      session.performMethodCall(
          new Session.MethodCall.Custom(
              Utils.createCallId(),
              "personal_sign",
              Arrays.asList(Numeric.toHexString(bytes), Numeric.toHexString(account))
          ),
          response -> {
            handleResponse(response, bytes, finalAccount, finalRegister);
            return null;
          });
    }
  }

  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  private void handleResponse(final Session.MethodCall.Response response,
                              final byte[] message,
                              final byte[] account,
                              final boolean register) {
    final String signature = String.valueOf(response.component2());
    boolean valid = true;
    if (response.component3() != null) {
      player.disconnect(MiniMessage.miniMessage().deserialize(plugin
              .getMessage("sign-rejected"),
          plugin.getPlaceholders(player)));
      valid = false;
    }
    try {
      if (!Utils.checkSignature(account,
          message,
          Numeric.hexStringToByteArray(signature))) {
        player.disconnect(MiniMessage.miniMessage().deserialize(plugin
                .getMessage("invalid-signature"),
            plugin.getPlaceholders(player)));
        valid = false;
      }
      if (!valid) {
        return;
      }
      final WCSessionReadyEvent event = new WCSessionReadyEvent(player, session);
      plugin.getProxy().getEventManager().fire(event);
      if (!event.isCancelled()) {
        if (register) {
          plugin.getConnection().getSession().getTransaction().begin();
          plugin.getConnection().getSession().persist(new UuidToAddressMapping(player.getUniqueId(),
              account));
          plugin.getConnection().getSession().getTransaction().commit();
        }
        player.getCurrentServer()
            .get()
            .getServer()
            .sendPluginMessage(MinecraftChannelIdentifier.create(
                Constants.PMC_NAMESPACE,
                Constants.PMC_AUTH_DONE), new byte[0]);
        plugin.getLoggedIn().add(player);
        plugin.getProxy().getScheduler().buildTask(plugin, () -> {
          player.createConnectionRequest(plugin.getProxy().getServer(plugin
              .getAfterAuthServer()).orElseThrow()).connect();
        }).delay(1, TimeUnit.SECONDS).schedule();
      }
    } catch (Exception ignored) {
      player.disconnect(MiniMessage.miniMessage().deserialize(plugin
              .getMessage("invalid signature"),
          plugin.getPlaceholders(player)));
    }
  }
}
