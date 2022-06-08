package org.craftcoin.walletconnectmc.paper;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.craftcoin.walletconnectmc.Constants;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import org.web3j.protocol.core.methods.request.Transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class TransactionTransport implements PluginMessageListener {
  private int transactionCounter = 0;
  private final Map<Integer, BiConsumer<Boolean, String>> transactionsQueued = new HashMap<>();

  public void sendTransaction(Player player, Transaction transaction, BiConsumer<Boolean, String> callback) {
    int txId = transactionCounter;
    transactionsQueued.put(txId, callback);
    transactionCounter++;

    ByteArrayDataOutput out = ByteStreams.newDataOutput();
    out.writeInt(txId);
    out.writeUTF(transaction.getFrom());
    out.writeUTF(transaction.getTo());
    out.writeUTF(emptyIfNull(transaction.getGas()));
    out.writeUTF(emptyIfNull(transaction.getGasPrice()));
    out.writeUTF(emptyIfNull(transaction.getValue()));
    out.writeUTF(emptyIfNull(transaction.getData()));
    out.writeUTF(emptyIfNull(transaction.getNonce()));
    player.sendPluginMessage(WalletConnectMC.getInstance(),
        Constants.PLUGIN_MESSAGE_CHANNEL_NAMESPACE + ":" + Constants.PLUGIN_MESSAGE_CHANNEL_TX_REQUEST,
        out.toByteArray());
  }

  @NotNull
  private String emptyIfNull(String s) {
    return s == null ? "" : s;
  }

  @Override
  public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
    if (!channel.equals(Constants.PLUGIN_MESSAGE_CHANNEL_NAMESPACE + ":" + Constants.PLUGIN_MESSAGE_CHANNEL_TX_RESPONSE))
      return;
    ByteArrayDataInput in = ByteStreams.newDataInput(message);
    int txId = in.readInt();
    boolean result = in.readBoolean();
    String transactionHashOrError = in.readUTF();
    if (!transactionsQueued.containsKey(txId)) {
      WalletConnectMC.getInstance().getLogger().warning("Cannot find transaction request with ID=" + txId + ", result" +
          ": " + transactionHashOrError);
      return;
    }
    transactionsQueued.remove(txId).accept(result, transactionHashOrError);
  }
}
