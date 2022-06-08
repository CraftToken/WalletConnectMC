package org.craftcoin.walletconnectmc.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.craftcoin.walletconnectmc.Constants;
import org.craftcoin.walletconnectmc.Utils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.walletconnect.Session;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionTransportVelocity {
  @Subscribe
  public void onPluginMessage(PluginMessageEvent event) {
    if (event.getIdentifier().equals(MinecraftChannelIdentifier.create(
        Constants.PLUGIN_MESSAGE_CHANNEL_NAMESPACE,
        Constants.PLUGIN_MESSAGE_CHANNEL_TX_REQUEST))
        && event.getTarget() instanceof Player player
        && event.getSource() instanceof ServerConnection server) {
      ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
      int txId = in.readInt();
      String from = in.readUTF();
      String to = in.readUTF();
      String gasLimit = in.readUTF();
      String gasPrice = in.readUTF();
      String value = in.readUTF();
      String data = in.readUTF();
      String nonce = in.readUTF();
      Map<String, String> map = new HashMap<>();
      map.put("from", from);
      map.put("to", to);
      map.put("data", data.isEmpty() ? "0x" : data);
      // kotlin-walletconnect-lib uses "gasLimit" which is not correct, so use custom method call
      map.put("gas", gasLimit.isEmpty() ? "0x0" : gasLimit);
      map.put("gasPrice", gasPrice.isEmpty() ? "0x0" : gasPrice);
      map.put("value", value.isEmpty() ? "0x0" : value);
      map.put("nonce", nonce);
      player.showTitle(Title.title(
          MiniMessage.miniMessage().deserialize(WalletConnectVelocity.getInstance()
                  .getMessage("send-transaction-title"),
              WalletConnectVelocity.getPlaceholders(player)),
          MiniMessage.miniMessage().deserialize(WalletConnectVelocity.getInstance()
                  .getMessage("send-transaction-subtitle"),
              WalletConnectVelocity.getPlaceholders(player)),
          Title.Times.times(Duration.ofMillis(300), Duration.ofDays(1), Duration.ZERO)
      ));
      WalletConnectVelocity.getInstance().getSession(player).performMethodCall(
          new Session.MethodCall.Custom(
              Utils.createCallId(),
              "eth_sendTransaction",
              List.of(map)
          ), response -> {
            player.resetTitle();
            boolean success;
            String transactionHashOrError;

            Object res = response.component2();
            if (response.component3() != null) {
              success = false;
              transactionHashOrError = response.component3().getMessage();
            } else {
              success = true;
              transactionHashOrError = res.toString();
            }

            if (success) {
              player.sendMessage(MiniMessage.miniMessage().deserialize(WalletConnectVelocity
                      .getInstance()
                      .getMessage("transaction-success"),
                  Placeholder.unparsed("tx_hash", transactionHashOrError),
                  WalletConnectVelocity.getPlaceholders(player)));
            } else {
              player.sendMessage(MiniMessage.miniMessage().deserialize(WalletConnectVelocity
                      .getInstance()
                      .getMessage("transaction-error"),
                  Placeholder.unparsed("error", transactionHashOrError),
                  WalletConnectVelocity.getPlaceholders(player)));
            }

            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeInt(txId);
            out.writeBoolean(success);
            out.writeUTF(transactionHashOrError);
            server.sendPluginMessage(MinecraftChannelIdentifier.create(Constants.PLUGIN_MESSAGE_CHANNEL_NAMESPACE,
                Constants.PLUGIN_MESSAGE_CHANNEL_TX_RESPONSE), out.toByteArray());
            return null;
          });
      event.setResult(PluginMessageEvent.ForwardResult.handled());
    }
  }
}
