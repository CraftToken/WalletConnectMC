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

package org.craftcoin.walletconnectmc;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Random;

import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

public final class Utils {
  @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
  private static final String SIGN_PREFIX = "\u0019Ethereum Signed Message:\n";

  private static final Random RANDOM = new Random();

  private Utils() {
  }

  @SuppressWarnings("checkstyle:MagicNumber")
  public static long createCallId() {
    return System.currentTimeMillis() * 1000 + RANDOM.nextInt(999);
  }

  public static boolean checkSignature(final byte[] account,
                                       final byte[] data,
                                       final byte[] signature) throws SignatureException {
    return Arrays.equals(getAccountFromSignature(data, signature), account);
  }

  @SuppressWarnings({"PMD.ShortVariable", "checkstyle:MagicNumber"})
  public static byte[] getAccountFromSignature(final byte[] data,
                                               final byte[] signature) throws SignatureException {
    final byte[] r = Arrays.copyOfRange(signature, 0, 32);
    final byte[] s = Arrays.copyOfRange(signature, 32, 64);

    final byte v = signature[64];
    final String signPrefix = SIGN_PREFIX + data.length;
    final byte[] msgBytes = new byte[signPrefix.getBytes(StandardCharsets.UTF_8).length
        + data.length];
    final byte[] prefixBytes = signPrefix.getBytes(StandardCharsets.UTF_8);
    System.arraycopy(prefixBytes, 0, msgBytes, 0, prefixBytes.length);
    System.arraycopy(data, 0, msgBytes, prefixBytes.length, data.length);
    final BigInteger key = Sign.signedMessageToKey(msgBytes,
        new Sign.SignatureData(v,
            r,
            s));
    final String keyString = key.toString(16);
    final String addressString = Keys.getAddress(keyString);
    return Numeric.hexStringToByteArray(addressString);
  }
}
