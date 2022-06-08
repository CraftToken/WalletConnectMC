package org.craftcoin.walletconnectmc;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "addresses")
public class UuidToAddressMapping {
  @Id
  @Column(nullable = false, unique = true)
  private UUID player;

  @Column(length = 20, nullable = false)
  private byte[] address;

  public UuidToAddressMapping() {
  }

  public UuidToAddressMapping(UUID player, byte[] address) {
    this.player = player;
    this.address = address;
  }

  public UUID getPlayer() {
    return player;
  }

  public byte[] getAddress() {
    return address;
  }

  public void setPlayer(UUID player) {
    this.player = player;
  }

  public void setAddress(byte[] address) {
    this.address = address;
  }
}
