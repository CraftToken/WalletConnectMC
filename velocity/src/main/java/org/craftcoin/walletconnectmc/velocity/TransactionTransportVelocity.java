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

import java.math.BigInteger;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.craftcoin.walletconnectmc.Constants;
import org.craftcoin.walletconnectmc.Utils;
import org.walletconnect.Session;
import org.web3j.utils.Numeric;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;

public class TransactionTransportVelocity {
  private final WalletConnectVelocity plugin;

  public TransactionTransportVelocity(final WalletConnectVelocity plugin) {
    this.plugin = plugin;
  }

  @SuppressWarnings("checkstyle:MagicNumber")
  @Subscribe
  public void onPluginMessage(final PluginMessageEvent event) {
    if (event.getIdentifier().equals(MinecraftChannelIdentifier.create(
        Constants.PMC_NAMESPACE,
        Constants.PMC_TX_REQUEST))
        && event.getTarget() instanceof Player player
        && event.getSource() instanceof ServerConnection server) {
      final ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
      final int txId = in.readInt();
      final String from = in.readUTF();
      final String to = in.readUTF();
      final String gasLimit = in.readUTF();
      final String gasPrice = in.readUTF();
      final String value = in.readUTF();
      final String data = in.readUTF();
      final String nonce = in.readUTF();
      @SuppressWarnings("PMD.UseConcurrentHashMap")
      final Map<String, String> map = new HashMap<>();
      map.put("from", from);
      map.put("to", to);
      map.put("data", data.isEmpty() ? "0x" : data);
      // kotlin-walletconnect-lib uses "gasLimit" which is not correct, so use custom method call
      map.put("gas", gasLimit.isEmpty()
          ? Numeric.toHexStringWithPrefix(BigInteger.ZERO)
          : gasLimit);
      map.put("gasPrice", gasPrice.isEmpty()
          ? Numeric.toHexStringWithPrefix(BigInteger.ZERO)
          : gasPrice);
      map.put("value", value.isEmpty()
          ? Numeric.toHexStringWithPrefix(BigInteger.ZERO)
          : value);
      map.put("nonce", nonce);
      player.showTitle(Title.title(
          MiniMessage.miniMessage().deserialize(plugin
                  .getMessage("send-transaction-title"),
              plugin.getPlaceholders(player)),
          MiniMessage.miniMessage().deserialize(plugin
                  .getMessage("send-transaction-subtitle"),
              plugin.getPlaceholders(player)),
          Title.Times.times(Duration.ofMillis(300), Duration.ofDays(1), Duration.ZERO)
      ));
      plugin.getSession(player).performMethodCall(
          new Session.MethodCall.Custom(
              Utils.createCallId(),
              "eth_sendTransaction",
              List.of(map)
          ), response -> {
            handleResponse(response, player, txId, server);
            return null;
          });
      event.setResult(PluginMessageEvent.ForwardResult.handled());
    }
  }

  private void handleResponse(final Session.MethodCall.Response response,
                              final Player player,
                              final int txId,
                              final ServerConnection server) {
    player.resetTitle();
    final boolean success;
    final String transactionHashOrError;

    final Object res = response.component2();
    if (response.component3() != null) {
      success = false;
      transactionHashOrError = response.component3().getMessage();
    } else {
      success = true;
      transactionHashOrError = res.toString();
    }

    if (success) {
      player.sendMessage(MiniMessage.miniMessage().deserialize(plugin
              .getMessage("transaction-success"),
          Placeholder.unparsed("tx_hash", transactionHashOrError),
          plugin.getPlaceholders(player)));
    } else {
      player.sendMessage(MiniMessage.miniMessage().deserialize(plugin
              .getMessage("transaction-error"),
          Placeholder.unparsed("error", transactionHashOrError),
          plugin.getPlaceholders(player)));
    }

    final ByteArrayDataOutput out = ByteStreams.newDataOutput();
    out.writeInt(txId);
    out.writeBoolean(success);
    out.writeUTF(transactionHashOrError);
    server.sendPluginMessage(MinecraftChannelIdentifier.create(Constants.PMC_NAMESPACE,
        Constants.PMC_TX_RESPONSE), out.toByteArray());
  }
}
