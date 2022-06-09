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

package org.craftcoin.walletconnectmc.api;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.TransactionManager;

public class WCTransactionManager extends TransactionManager {
  private static final BigInteger INCREASE_GAS_LIMIT = BigInteger.valueOf(100);
  private final WalletConnectMCApi api;
  private final Player player;

  /* package */ WCTransactionManager(final WalletConnectMCApi api,
                       final Player player,
                       final String playerAddress) {
    super(api.getWeb3(), playerAddress);
    this.api = api;
    this.player = player;
  }

  @Override
  public EthSendTransaction sendTransaction(final BigInteger gasPrice,
                                            final BigInteger gasLimit,
                                            final String to,
                                            final String data,
                                            final BigInteger value,
                                            final boolean constructor) throws IOException {
    // Object is either IOException or EthSendTransaction
    final CompletableFuture<Object> future = new CompletableFuture<>();

    api
        .getWeb3()
        .ethGetTransactionCount(getFromAddress(), DefaultBlockParameterName.LATEST)
        .sendAsync()
        .thenAccept(nonce -> {
          final Transaction tx = new Transaction(
              getFromAddress(),
              nonce.getTransactionCount(),
              gasPrice,
              gasLimit,
              to,
              value,
              data);
          api.getWeb3().ethEstimateGas(tx).sendAsync().thenAccept(estimation -> {
            // Here in games, we often deal with random, so the outcome of a transaction
            // (including gas used) can be unpredictable, so increase it just in case.
            final BigInteger limit = estimation.getAmountUsed().add(INCREASE_GAS_LIMIT);
            api.getWeb3().ethGasPrice().sendAsync().thenAccept(estimation2 -> {
              final BigInteger price = estimation2.getGasPrice();
              final Transaction txWithUpdatedGasPrices = new Transaction(
                  getFromAddress(),
                  nonce.getTransactionCount(),
                  price,
                  limit,
                  to,
                  value,
                  data);
              api.getTransactionTransport().sendTransaction(player,
                  txWithUpdatedGasPrices,
                  (isSuccess, transactionHashOrError) -> {
                    if (isSuccess) {
                      final EthSendTransaction response = new EthSendTransaction();
                      response.setResult(transactionHashOrError);
                      future.complete(response);
                    } else {
                      future
                          .completeExceptionally(new TransactionException(transactionHashOrError));
                    }
                  });
            });
          });
        });
    final Object obj = future.exceptionally(IOException::new).join();
    if (obj instanceof EthSendTransaction res) {
      return res;
    } else {
      throw (IOException) obj;
    }
  }

  @Override
  public EthSendTransaction sendEIP1559Transaction(final long chainId,
                                                   final BigInteger maxPriorityFeePerGas,
                                                   final BigInteger maxFeePerGas,
                                                   final BigInteger gasLimit,
                                                   final String to,
                                                   final String data,
                                                   final BigInteger value,
                                                   final boolean constructor) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public String sendCall(final String to,
                         final String data,
                         final DefaultBlockParameter defaultBlockParameter) throws IOException {
    return api.getWeb3().ethCall(Transaction.createFunctionCallTransaction(getFromAddress(),
            null,
            null,
            null,
            to,
            data
        ), defaultBlockParameter)
        .send().getResult();
  }

  @Override
  public EthGetCode getCode(final String contractAddress,
                            final DefaultBlockParameter defaultBlockParameter) throws IOException {
    return api.getWeb3().ethGetCode(contractAddress, defaultBlockParameter).send();
  }
}
