package org.craftcoin.walletconnectmc.paper.auth;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

public class QRCodeGenerator {
  public static ItemStack generate(Player player, String url) throws WriterException {
    ItemStack map = new ItemStack(Material.FILLED_MAP);
    MapMeta meta = (MapMeta) map.getItemMeta();
    MapView mapView = Bukkit.createMap(player.getWorld());
    mapView.setUnlimitedTracking(true);
    for (MapRenderer renderer : mapView.getRenderers()) {
      mapView.removeRenderer(renderer);
    }
    BitMatrix matrix = new MultiFormatWriter().encode(new String(url
            .getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8),
        BarcodeFormat.QR_CODE,
        128,
        128);
    BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
    mapView.addRenderer(new MapRenderer() {
      @Override
      public void render(@NotNull MapView mapView,
                         @NotNull MapCanvas mapCanvas,
                         @NotNull Player player) {
        mapCanvas.drawImage(0, 0, image);
      }
    });
    meta.setMapView(mapView);
    map.setItemMeta(meta);
    return map;
  }
}
