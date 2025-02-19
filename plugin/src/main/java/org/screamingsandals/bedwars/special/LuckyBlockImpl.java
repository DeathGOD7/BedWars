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

package org.screamingsandals.bedwars.special;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.screamingsandals.bedwars.api.special.LuckyBlock;
import org.screamingsandals.bedwars.game.GameImpl;
import org.screamingsandals.bedwars.game.TeamImpl;
import org.screamingsandals.bedwars.player.BedWarsPlayer;
import org.screamingsandals.lib.entity.EntityMapper;
import org.screamingsandals.lib.item.builder.ItemFactory;
import org.screamingsandals.lib.item.meta.PotionEffectHolder;
import org.screamingsandals.lib.player.PlayerWrapper;
import org.screamingsandals.lib.tasker.Tasker;
import org.screamingsandals.lib.tasker.TaskerTime;
import org.screamingsandals.lib.world.LocationHolder;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Getter
@EqualsAndHashCode(callSuper = true)
public class LuckyBlockImpl extends SpecialItem implements LuckyBlock<GameImpl, BedWarsPlayer, TeamImpl, LocationHolder> {
    private final List<Map<String, Object>> luckyBlockData;
    private LocationHolder blockLocation;
    private boolean placed;

    public LuckyBlockImpl(GameImpl game, BedWarsPlayer player, TeamImpl team, List<Map<String, Object>> luckyBlockData) {
        super(game, player, team);
        this.luckyBlockData = luckyBlockData;
        game.registerSpecialItem(this);
    }

    public void place(LocationHolder loc) {
        this.blockLocation = loc;
        this.placed = true;
    }

    public void process(PlayerWrapper broker) {
        game.unregisterSpecialItem(this);

        var rand = new Random();
        var element = rand.nextInt(luckyBlockData.size());

        var map = luckyBlockData.get(element);

        var type = (String) map.getOrDefault("type", "nothing");
        switch (type) {
            case "item":
                var stack = ItemFactory.build(map.get("stack")).orElseThrow();
                EntityMapper.dropItem(stack, blockLocation);
                break;
            case "potion":
                var potionEffect = PotionEffectHolder.of(map.get("effect"));
                broker.addPotionEffect(potionEffect);
                break;
            case "tnt":
                Tasker.build(() -> {
                    var tnt = EntityMapper.spawn("tnt", blockLocation).orElseThrow();
                    tnt.setMetadata("fuse_ticks", 0);
                })
                .delay(10, TaskerTime.TICKS)
                .start();
                break;
            case "teleport":
                broker.teleport(broker.getLocation().add(0, (int) map.get("height"), 0));
                break;
        }

        if (map.containsKey("message")) {
            broker.sendMessage((String) map.get("message"));
        }
    }
}
