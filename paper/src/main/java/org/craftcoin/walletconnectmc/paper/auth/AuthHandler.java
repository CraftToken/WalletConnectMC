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

import java.time.Duration;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.craftcoin.walletconnectmc.Constants;
import org.craftcoin.walletconnectmc.paper.WalletConnectMC;
import org.jetbrains.annotations.NotNull;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.zxing.WriterException;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;

public class AuthHandler implements PluginMessageListener {
  @SuppressWarnings({
      "checkstyle:MultipleStringLiterals",
      "checkstyle:MagicNumber",
      "PMD.AvoidLiteralsInIfCondition"
  })
  @Override
  public void onPluginMessageReceived(@NotNull final String channel,
                                      @NotNull final Player player,
                                      final byte @NotNull [] bytes) {
    final String[] strs = channel.split(":");
    if (strs.length != 2 || !Constants.PMC_NAMESPACE.equals(strs[0])) {
      return;
    }
    final ByteArrayDataInput in = ByteStreams.newDataInput(bytes);

    switch (strs[1]) {
      case Constants.PMC_AUTH_SHOW_QR_CODE -> {
        player.getInventory().clear();
        final String url = in.readUTF();
        // replacements in click events don't work
        player.sendMessage(MiniMessage.miniMessage().deserialize(WalletConnectMC
                .getInstance()
                .getMessage("url").replaceAll("<url>", url),
            Placeholder.unparsed("url", url), WalletConnectMC.getPlaceholders(player)));
        try {
          final ItemStack map = QRCodeGenerator.generate(player, url);
          player.getInventory().setItemInMainHand(map);
        } catch (WriterException exception) {
          WalletConnectMC.getInstance().getLogger().severe(exception.toString());
        }
      }
      case Constants.PMC_AUTH_APPROVED -> {
        player.getInventory().clear();
        player.showTitle(Title.title(
            MiniMessage.miniMessage().deserialize(WalletConnectMC
                .getInstance()
                .getMessage("sign title"), WalletConnectMC.getPlaceholders(player)),
            MiniMessage.miniMessage().deserialize(WalletConnectMC
                .getInstance()
                .getMessage("sign subtitle"), WalletConnectMC.getPlaceholders(player)),
            Title.Times.times(Duration.ofMillis(500),
                Duration.ofDays(1),
                Duration.ofMillis(1000))));
      }
      case Constants.PMC_AUTH_DONE -> player.showTitle(Title.title(
          MiniMessage.miniMessage().deserialize(WalletConnectMC
              .getInstance()
              .getMessage("logged in title"), WalletConnectMC.getPlaceholders(player)),
          MiniMessage.miniMessage().deserialize(WalletConnectMC
              .getInstance()
              .getMessage("logged in subtitle"), WalletConnectMC.getPlaceholders(player)),
          Title.Times.times(Duration.ofMillis(150),
              Duration.ofMillis(500),
              Duration.ofMillis(250))));
      default -> {
      }
    }
  }
}
