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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.craftcoin.walletconnectmc.Constants;
import org.jetbrains.annotations.NotNull;
import org.web3j.protocol.core.methods.request.Transaction;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class TransactionTransport implements PluginMessageListener {
  private int transactionCounter;
  private final Map<Integer, BiConsumer<Boolean, String>> transactionsQueued =
      new ConcurrentHashMap<>();

  public void sendTransaction(final Player player,
                              final Transaction transaction,
                              final BiConsumer<Boolean, String> callback) {
    final int txId = transactionCounter;
    transactionsQueued.put(txId, callback);
    transactionCounter++;

    final ByteArrayDataOutput out = ByteStreams.newDataOutput();
    out.writeInt(txId);
    out.writeUTF(transaction.getFrom());
    out.writeUTF(transaction.getTo());
    out.writeUTF(emptyIfNull(transaction.getGas()));
    out.writeUTF(emptyIfNull(transaction.getGasPrice()));
    out.writeUTF(emptyIfNull(transaction.getValue()));
    out.writeUTF(emptyIfNull(transaction.getData()));
    out.writeUTF(emptyIfNull(transaction.getNonce()));
    player.sendPluginMessage(WalletConnectMC.getInstance(),
        Constants.PMC_NAMESPACE + WalletConnectMC.COLON + Constants.PMC_TX_REQUEST,
        out.toByteArray());
  }

  @NotNull
  private String emptyIfNull(final String value) {
    return value == null ? "" : value;
  }

  @Override
  public void onPluginMessageReceived(@NotNull final String channel,
                                      @NotNull final Player player,
                                      final byte[] message) {
    if (!channel.equals(Constants.PMC_NAMESPACE
        + WalletConnectMC.COLON
        + Constants.PMC_TX_RESPONSE)) {
      return;
    }
    final ByteArrayDataInput in = ByteStreams.newDataInput(message);
    @SuppressWarnings("PMD.PrematureDeclaration")
    final int txId = in.readInt();
    final boolean result = in.readBoolean();
    final String transactionHashOrError = in.readUTF();
    if (transactionsQueued.containsKey(txId)) {
      transactionsQueued.remove(txId).accept(result, transactionHashOrError);
    } else {
      WalletConnectMC.getInstance().getLogger().warning("Cannot find transaction request with ID="
          + txId
          + ", result"
          + ": "
          + transactionHashOrError);
    }
  }
}
