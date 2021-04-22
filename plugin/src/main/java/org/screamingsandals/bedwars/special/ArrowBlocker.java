package org.screamingsandals.bedwars.special;

import org.bukkit.plugin.java.JavaPlugin;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.game.Game;
import org.screamingsandals.bedwars.api.team.Team;
import org.screamingsandals.bedwars.lang.LangKeys;
import org.screamingsandals.bedwars.utils.MiscUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.screamingsandals.lib.lang.Message;
import org.screamingsandals.lib.player.PlayerMapper;

public class ArrowBlocker extends SpecialItem implements org.screamingsandals.bedwars.api.special.ArrowBlocker {
    private Game game;
    private Player player;

    private int protectionTime;
    private int usedTime;
    private boolean isActivated;
    private ItemStack item;

    public ArrowBlocker(Game game, Player player, Team team, ItemStack item, int protectionTime) {
        super(game, player, team);
        this.game = game;
        this.player = player;
        this.item = item;
        this.protectionTime = protectionTime;
    }

    @Override
    public int getProtectionTime() {
        return protectionTime;
    }

    @Override
    public int getUsedTime() {
        return usedTime;
    }

    @Override
    public boolean isActivated() {
        return isActivated;
    }

    @Override
    public void runTask() {
        new BukkitRunnable() {

            @Override
            public void run() {
                usedTime++;
                if (usedTime == protectionTime) {
                    isActivated = false;
                    MiscUtils.sendActionBarMessage(PlayerMapper.wrapPlayer(player), Message.of(LangKeys.SPECIALS_ARROW_BLOCKER_ENDED));

                    game.unregisterSpecialItem(ArrowBlocker.this);
                    this.cancel();
                }
            }
        }.runTaskTimer(Main.getInstance().getPluginDescription().as(JavaPlugin.class), 0L, 20L);
    }

    public void activate() {
        if (protectionTime > 0) {
            game.registerSpecialItem(this);
            runTask();

            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                try {
                    if (player.getInventory().getItemInOffHand().equals(item)) {
                        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                    } else {
                        player.getInventory().remove(item);
                    }
                } catch (Throwable e) {
                    player.getInventory().remove(item);
                }
            }
            player.updateInventory();

            MiscUtils.sendActionBarMessage(PlayerMapper.wrapPlayer(player), Message.of(LangKeys.SPECIALS_ARROW_BLOCKER_STARTED).placeholder("time", protectionTime));
        }
    }
}
