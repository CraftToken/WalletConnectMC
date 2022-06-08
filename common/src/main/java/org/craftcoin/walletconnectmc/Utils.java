package org.craftcoin.walletconnectmc;

import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Random;

public class Utils {
  private static final String SIGN_PREFIX = "\u0019Ethereum Signed Message:\n";

  public static long createCallId() {
    return System.currentTimeMillis() * 1000 + new Random().nextInt(999);
  }

  public static boolean checkSignature(byte[] account, byte[] data, byte[] signature) throws SignatureException {
    return Arrays.equals(getAccountFromSignature(data, signature), account);
  }

  public static byte[] getAccountFromSignature(byte[] data, byte[] signature) throws SignatureException {
    byte[] r = Arrays.copyOfRange(signature, 0, 32);
    byte[] s = Arrays.copyOfRange(signature, 32, 64);

    byte v = signature[64];
    String signPrefix = SIGN_PREFIX + data.length;
    byte[] msgBytes = new byte[signPrefix.getBytes().length + data.length];
    byte[] prefixBytes = signPrefix.getBytes();
    System.arraycopy(prefixBytes, 0, msgBytes, 0, prefixBytes.length);
    System.arraycopy(data, 0, msgBytes, prefixBytes.length, data.length);
    BigInteger key = Sign.signedMessageToKey(msgBytes,
        new Sign.SignatureData(v,
            r,
            s));
    String keyString = key.toString(16);
    String addressString = Keys.getAddress(keyString);
    return Numeric.hexStringToByteArray(addressString);
  }
}
