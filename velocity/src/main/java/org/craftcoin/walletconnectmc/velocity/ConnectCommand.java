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

package org.craftcoin.walletconnectmc.velocity;

import java.util.logging.Level;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

public final class ConnectCommand {
  private ConnectCommand() {
  }

  public static void register(final WalletConnectVelocity plugin) {
    plugin.getProxy().getCommandManager().register(new BrigadierCommand(
        LiteralArgumentBuilder.<CommandSource>literal("connect")
            .requires(sender -> sender.hasPermission("walletconnectmc.connect"))
            .requires(sender -> sender instanceof Player)
            .requires(sender -> {
              return !((Player) sender).getCurrentServer()
                  .orElseThrow()
                  .getServer().equals(plugin.getAuthServer());
            })
            .executes(ctx -> {
              ((Player) ctx.getSource())
                  .createConnectionRequest(plugin.getAuthServer())
                  .connect()
                  .exceptionally(exception -> {
                    if (plugin.getLogger().isLoggable(Level.SEVERE)) {
                      plugin.getLogger().severe(exception.toString());
                    }
                    ctx.getSource().sendMessage(Component.text(exception.getMessage()));
                    return null;
                  });
              return 0;
            })
    ));
  }
}
