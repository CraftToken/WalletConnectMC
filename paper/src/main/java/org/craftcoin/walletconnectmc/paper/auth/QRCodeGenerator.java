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

package org.craftcoin.walletconnectmc.paper.auth;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

public final class QRCodeGenerator {
  private static final int BITMAP_SIZE = 128;

  private QRCodeGenerator() {
  }

  public static ItemStack generate(final Player player,
                                   final String url) throws WriterException {
    final ItemStack map = new ItemStack(Material.FILLED_MAP);
    final MapMeta meta = (MapMeta) map.getItemMeta();
    final MapView mapView = Bukkit.createMap(player.getWorld());
    mapView.setUnlimitedTracking(true);
    for (final MapRenderer renderer : mapView.getRenderers()) {
      mapView.removeRenderer(renderer);
    }
    final BitMatrix matrix = new MultiFormatWriter().encode(new String(url
            .getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8),
        BarcodeFormat.QR_CODE,
        BITMAP_SIZE,
        BITMAP_SIZE);
    final BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
    mapView.addRenderer(new MapRenderer() {
      @Override
      public void render(@NotNull final MapView mapView,
                         @NotNull final MapCanvas mapCanvas,
                         @NotNull final Player player) {
        mapCanvas.drawImage(0, 0, image);
      }
    });
    meta.setMapView(mapView);
    map.setItemMeta(meta);
    return map;
  }
}
