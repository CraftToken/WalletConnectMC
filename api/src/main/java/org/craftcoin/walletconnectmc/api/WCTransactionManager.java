package org.craftcoin.walletconnectmc.api;

import org.bukkit.entity.Player;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.TransactionManager;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;

public class WCTransactionManager extends TransactionManager {
  private final WalletConnectMCApi api;
  private final Player player;

  WCTransactionManager(WalletConnectMCApi api, Player player, String playerAddress) {
    super(api.getWeb3(), playerAddress);
    this.api = api;
    this.player = player;
  }

  @Override
  public EthSendTransaction sendTransaction(BigInteger gasPrice,
                                            BigInteger gasLimit,
                                            String to,
                                            String data,
                                            BigInteger value,
                                            boolean constructor) throws IOException {
    // Object is either IOException or EthSendTransaction
    CompletableFuture<Object> future = new CompletableFuture<>();

    api
        .getWeb3()
        .ethGetTransactionCount(getFromAddress(), DefaultBlockParameterName.LATEST)
        .sendAsync()
        .thenAccept(nonce -> {
          Transaction tx = new Transaction(
              getFromAddress(),
              nonce.getTransactionCount(),
              gasPrice,
              gasLimit,
              to,
              value,
              data);
          api.getWeb3().ethEstimateGas(tx).sendAsync().thenAccept(estimation -> {
            // Here in games, we often deal with random, so the outcome of a transaction
            // (including gas used) can be unpredictable, so +100 just in case.
            BigInteger limit = estimation.getAmountUsed().add(BigInteger.valueOf(100));
            api.getWeb3().ethGasPrice().sendAsync().thenAccept(estimation2 -> {
              BigInteger price = estimation2.getGasPrice();
              Transaction txWithUpdatedGasPrices = new Transaction(
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
                      EthSendTransaction response = new EthSendTransaction();
                      response.setResult(transactionHashOrError);
                      future.complete(response);
                    } else {
                      future.completeExceptionally(new TransactionException(transactionHashOrError));
                    }
                  });
            });
          });
        });
    Object obj = future.exceptionally(IOException::new).join();
    if (obj instanceof EthSendTransaction res) return res;
    else throw (IOException) obj;
  }

  @Override
  public EthSendTransaction sendEIP1559Transaction(long chainId,
                                                   BigInteger maxPriorityFeePerGas,
                                                   BigInteger maxFeePerGas,
                                                   BigInteger gasLimit,
                                                   String to,
                                                   String data,
                                                   BigInteger value,
                                                   boolean constructor) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public String sendCall(String to,
                         String data,
                         DefaultBlockParameter defaultBlockParameter) throws IOException {
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
  public EthGetCode getCode(String contractAddress,
                            DefaultBlockParameter defaultBlockParameter) throws IOException {
    return api.getWeb3().ethGetCode(contractAddress, defaultBlockParameter).send();
  }
}
