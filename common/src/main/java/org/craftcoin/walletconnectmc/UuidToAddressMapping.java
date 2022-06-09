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

import java.util.Arrays;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@SuppressWarnings("PMD.DataClass")
@Entity
@Table(name = "addresses")
public class UuidToAddressMapping {
  @Id
  @Column(nullable = false, unique = true)
  private UUID player;

  @SuppressWarnings("checkstyle:MagicNumber")
  @Column(length = 20, nullable = false)
  private byte[] address;

  public UuidToAddressMapping() {
  }

  public UuidToAddressMapping(final UUID player, final byte[] address) {
    this.player = player;
    this.address = Arrays.copyOf(address, address.length);
  }

  public UUID getPlayer() {
    return player;
  }

  public byte[] getAddress() {
    return Arrays.copyOf(address, address.length);
  }

  public void setPlayer(final UUID player) {
    this.player = player;
  }

  public void setAddress(final byte[] address) {
    this.address = Arrays.copyOf(address, address.length);
  }
}
