package org.craftcoin.walletconnectmc.paper.auth;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.craftcoin.walletconnectmc.Constants;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import org.craftcoin.walletconnectmc.paper.WalletConnectMC;

import java.time.Duration;

public class AuthHandler implements PluginMessageListener {
  @Override
  public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] bytes) {
    String[] strs = channel.split(":");
    if (strs.length != 2) return;
    if (!strs[0].equals(Constants.PLUGIN_MESSAGE_CHANNEL_NAMESPACE)) return;
    ByteArrayDataInput in = ByteStreams.newDataInput(bytes);

    switch (strs[1]) {
      case Constants.PLUGIN_MESSAGE_CHANNEL_AUTH_SHOW_QR_CODE -> {
        player.getInventory().clear();
        String url = in.readUTF();
        player.sendMessage(MiniMessage.miniMessage().deserialize(WalletConnectMC
                .getInstance()
                .getMessage("url").replaceAll("<url>", url), // TODO replacements in click events don't work
            Placeholder.unparsed("url", url), WalletConnectMC.getPlaceholders(player)));
        try {
          ItemStack map = QRCodeGenerator.generate(player, url);
          player.getInventory().setItemInMainHand(map);
        } catch (Exception exception) {
          exception.printStackTrace();
        }
      }
      case Constants.PLUGIN_MESSAGE_CHANNEL_AUTH_APPROVED -> {
        player.getInventory().clear();
        player.showTitle(Title.title(
            MiniMessage.miniMessage().deserialize(WalletConnectMC
                .getInstance()
                .getMessage("sign title"), WalletConnectMC.getPlaceholders(player)),
            MiniMessage.miniMessage().deserialize(WalletConnectMC
                .getInstance()
                .getMessage("sign subtitle"), WalletConnectMC.getPlaceholders(player)),
            Title.Times.times(Duration.ofMillis(500), Duration.ofDays(1), Duration.ofMillis(1000))));
      }
      case Constants.PLUGIN_MESSAGE_CHANNEL_AUTH_DONE -> player.showTitle(Title.title(
          MiniMessage.miniMessage().deserialize(WalletConnectMC
              .getInstance()
              .getMessage("logged in title"), WalletConnectMC.getPlaceholders(player)),
          MiniMessage.miniMessage().deserialize(WalletConnectMC
              .getInstance()
              .getMessage("logged in subtitle"), WalletConnectMC.getPlaceholders(player)),
          Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(500), Duration.ofMillis(250))));
    }
  }
}
