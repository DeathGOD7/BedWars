/*
 * Copyright (C) 2022 ScreamingSandals
 *
 * This file is part of Screaming BedWars.
 *
 * Screaming BedWars is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Screaming BedWars is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Screaming BedWars. If not, see <https://www.gnu.org/licenses/>.
 */

package org.screamingsandals.bedwars.commands.admin;

import cloud.commandframework.Command;
import cloud.commandframework.CommandManager;
import cloud.commandframework.arguments.standard.StringArgument;
import net.kyori.adventure.bossbar.BossBar;
import org.screamingsandals.bedwars.lang.LangKeys;
import org.screamingsandals.lib.lang.Message;
import org.screamingsandals.lib.sender.CommandSenderWrapper;
import org.screamingsandals.lib.utils.annotations.Service;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GameBossBarColorCommand extends BaseAdminSubCommand {
    public GameBossBarColorCommand() {
        super("gamebossbarcolor");
    }

    @Override
    public void construct(CommandManager<CommandSenderWrapper> manager, Command.Builder<CommandSenderWrapper> commandSenderWrapperBuilder) {
        manager.command(
                commandSenderWrapperBuilder
                        .argument(StringArgument
                                .<CommandSenderWrapper>newBuilder("barColor")
                                .withSuggestionsProvider((c, s) ->
                                        Stream.concat(Arrays.stream(BossBar.Color.values()).map(BossBar.Color::name), Stream.of("default")).collect(Collectors.toList())
                                )
                        )
                        .handler(commandContext -> editMode(commandContext, (sender, game) -> {
                            String barColor = commandContext.get("barColor");
                            if (!barColor.equalsIgnoreCase("default")) {
                                try {
                                    var c = BossBar.Color.valueOf(barColor.toUpperCase());
                                    game.setGameBossBarColor(c);

                                    sender.sendMessage(Message.of(LangKeys.ADMIN_ARENA_EDIT_SUCCESS_BAR_COLOR_SET).defaultPrefix().placeholder("color", c.name())
                                            .placeholder("type", "GAME"));
                                } catch (Exception e) {
                                    sender.sendMessage(Message.of(LangKeys.ADMIN_ARENA_EDIT_ERRORS_INVALID_BAR_COLOR).defaultPrefix());
                                }
                            } else {
                                game.setGameBossBarColor(null);

                                sender.sendMessage(Message.of(LangKeys.ADMIN_ARENA_EDIT_SUCCESS_BAR_COLOR_SET).placeholder("color", "default")
                                        .placeholder("type", "GAME").defaultPrefix());
                            }
                        }))
        );
    }
}
