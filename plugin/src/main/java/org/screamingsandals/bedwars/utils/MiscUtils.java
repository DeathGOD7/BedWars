package org.screamingsandals.bedwars.utils;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.RunningTeam;
import org.screamingsandals.bedwars.api.OldTeamColor;
import org.screamingsandals.bedwars.api.game.Game;
import org.screamingsandals.bedwars.config.MainConfig;
import org.screamingsandals.bedwars.events.ApplyPropertyToItemEventImpl;
import org.screamingsandals.bedwars.lib.debug.Debug;
import org.screamingsandals.bedwars.player.PlayerManager;
import org.screamingsandals.lib.material.MaterialHolder;
import org.screamingsandals.lib.material.MaterialMapping;
import org.screamingsandals.lib.player.PlayerMapper;
import org.screamingsandals.lib.player.PlayerWrapper;
import org.screamingsandals.lib.sender.SenderMessage;

import java.util.*;
import java.util.stream.Collectors;

@UtilityClass
public class MiscUtils {
    /**
     * From BedWarsRel
     */
    public int randInt(int min, int max) {
        Random rand = new Random();
        return rand.nextInt((max - min) + 1) + min;
    }

    public BlockFace getCardinalDirection(Location location) {
        double rotation = (location.getYaw() - 90) % 360;
        if (rotation < 0) {
            rotation += 360.0;
        }
        if (0 <= rotation && rotation < 22.5) {
            return BlockFace.NORTH;
        } else if (22.5 <= rotation && rotation < 67.5) {
            return BlockFace.NORTH_EAST;
        } else if (67.5 <= rotation && rotation < 112.5) {
            return BlockFace.EAST;
        } else if (112.5 <= rotation && rotation < 157.5) {
            return BlockFace.SOUTH_EAST;
        } else if (157.5 <= rotation && rotation < 202.5) {
            return BlockFace.SOUTH;
        } else if (202.5 <= rotation && rotation < 247.5) {
            return BlockFace.SOUTH_WEST;
        } else if (247.5 <= rotation && rotation < 292.5) {
            return BlockFace.WEST;
        } else if (292.5 <= rotation && rotation < 337.5) {
            return BlockFace.NORTH_WEST;
        } else if (337.5 <= rotation && rotation < 360.0) {
            return BlockFace.NORTH;
        } else {
            return BlockFace.NORTH;
        }
    }
    /* End of BedWarsRel */

    /* Special items  - CEPH*/
    public void sendActionBarMessage(Player player, String message) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Main.isSpigot() && !Main.isLegacy() && MainConfig.getInstance().node("specials", "action-bar-messages").getBoolean()) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(message));
                } else {
                    player.sendMessage(message);
                }
            }
        }.runTask(Main.getInstance().getPluginDescription().as(JavaPlugin.class));
    }

    public void sendActionBarMessage(PlayerWrapper player, Component component) {
        if (MainConfig.getInstance().node("specials", "action-bar-messages").getBoolean()) {
            player.sendActionBar(component);
        } else {
            // TODO: custom game prefix
            player.sendMessage(component);
        }
    }

    public void sendActionBarMessage(PlayerWrapper player, SenderMessage senderMessage) {
        if (MainConfig.getInstance().node("specials", "action-bar-messages").getBoolean()) {
            player.sendActionBar(senderMessage);
        } else {
            // TODO: custom game prefix
            player.sendMessage(senderMessage);
        }
    }

    public int getIntFromProperty(String name, String fallback, ApplyPropertyToItemEventImpl event) {
        try {
            return event.getIntProperty(name);
        } catch (NullPointerException e) {
            return MainConfig.getInstance().node((Object[]) fallback.split("\\.")).getInt();
        }
    }

    public double getDoubleFromProperty(String name, String fallback, ApplyPropertyToItemEventImpl event) {
        try {
            return event.getDoubleProperty(name);
        } catch (NullPointerException e) {
            return MainConfig.getInstance().node((Object[]) fallback.split("\\.")).getDouble();
        }
    }

    public boolean getBooleanFromProperty(String name, String fallback, ApplyPropertyToItemEventImpl event) {
        try {
            return event.getBooleanProperty(name);
        } catch (NullPointerException e) {
            return MainConfig.getInstance().node((Object[]) fallback.split("\\.")).getBoolean();
        }
    }

    public String getStringFromProperty(String name, String fallback, ApplyPropertyToItemEventImpl event) {
        try {
            return event.getStringProperty(name);
        } catch (NullPointerException e) {
            return MainConfig.getInstance().node((Object[]) fallback.split("\\.")).getString();
        }
    }

    public String getMaterialFromProperty(String name, String fallback, ApplyPropertyToItemEventImpl event) {
        try {
            return event.getStringProperty(name);
        } catch (NullPointerException e) {
            return MainConfig.getInstance().node((Object[]) fallback.split("\\.")).getString(Main.isLegacy() ? "SANDSTONE" : "CUT_SANDSTONE");
        }
    }

    public MaterialHolder getMaterialFromString(String name, String fallback) {
        if (name != null) {
            var result = MaterialMapping.resolve(name);
            if (result.isEmpty()) {
                Debug.warn("Wrong material configured: " + name, true);
            } else {
                return result.get();
            }
        }

        return MaterialMapping.resolve(fallback).orElseThrow();
    }

    public Player findTarget(Game game, Player player, double maxDist) {
        Player playerTarget = null;
        RunningTeam team = game.getTeamOfPlayer(player);

        ArrayList<Player> foundTargets = new ArrayList<>(game.getConnectedPlayers());
        foundTargets.removeAll(team.getConnectedPlayers());


        for (Player p : foundTargets) {
            var gamePlayer = PlayerManager.getInstance().getPlayer(p.getUniqueId());
            if (gamePlayer.isEmpty()) {
                continue;
            }

            if (player.getWorld() != p.getWorld()) {
                continue;
            }

            if (gamePlayer.get().isSpectator) {
                continue;
            }

            double realDistance = player.getLocation().distance(p.getLocation());
            if (realDistance < maxDist) {
                playerTarget = p;
                maxDist = realDistance;
            }
        }
        return playerTarget;
    }

    /* End of Special Items */

    public Location readLocationFromString(World world, String location) {
        int lpos = 0;
        double x = 0;
        double y = 0;
        double z = 0;
        float yaw = 0;
        float pitch = 0;
        for (String pos : location.split(";")) {
            lpos++;
            switch (lpos) {
                case 1:
                    x = Double.parseDouble(pos);
                    break;
                case 2:
                    y = Double.parseDouble(pos);
                    break;
                case 3:
                    z = Double.parseDouble(pos);
                    break;
                case 4:
                    yaw = Float.parseFloat(pos);
                    break;
                case 5:
                    pitch = Float.parseFloat(pos);
                    break;
                default:
                    break;
            }
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    public String setLocationToString(Location location) {
        return location.getX() + ";" + location.getY() + ";" + location.getZ() + ";" + location.getYaw() + ";"
                + location.getPitch();
    }

    public String convertColorToNewFormat(String oldColor, boolean isNewColor) {
        String newColor = oldColor;

        if (isNewColor) {
            return oldColor;
        }

        switch (oldColor) {
            case "DARK_BLUE":
                newColor = "BLUE";
                break;
            case "DARK_GREEN":
                newColor = "GREEN";
                break;
            case "DARK_PURPLE":
                newColor = "MAGENTA";
                break;
            case "GOLD":
                newColor = "ORANGE";
                break;
            case "GRAY":
                newColor = "LIGHT_GRAY";
                break;
            case "BLUE":
                newColor = "LIGHT_BLUE";
                break;
            case "GREEN":
                newColor = "LIME";
                break;
            case "AQUA":
                newColor = "CYAN";
                break;
            case "LIGHT_PURPLE":
                newColor = "PINK";
                break;
            case "DARK_RED":
                newColor = "BROWN";
                break;
            case "DARK_GRAY":
            	newColor = "GRAY";
            	break;
        }
        return newColor;
    }
    
    public Vector getDirection(BlockFace face) {
    	int modX = face.getModX();
    	int modY = face.getModY();
    	int modZ = face.getModZ();
        Vector direction = new Vector(modX, modY, modZ);
        if (modX != 0 || modY != 0 || modZ != 0) {
            direction.normalize();
        }
        return direction;
    }

    public void giveItemsToPlayer(List<ItemStack> itemStackList, Player player, OldTeamColor teamColor) {
        for (ItemStack itemStack : itemStackList) {
            final String materialName = itemStack.getType().toString();
            final PlayerInventory playerInventory = player.getInventory();

            if (materialName.contains("HELMET")) {
                playerInventory.setHelmet(Main.getInstance().getColorChanger().applyColor(teamColor, itemStack));
            } else if (materialName.contains("CHESTPLATE")) {
                playerInventory.setChestplate(Main.getInstance().getColorChanger().applyColor(teamColor, itemStack));
            } else if (materialName.contains("LEGGINGS")) {
                playerInventory.setLeggings(Main.getInstance().getColorChanger().applyColor(teamColor, itemStack));
            } else if (materialName.contains("BOOTS")) {
                playerInventory.setBoots(Main.getInstance().getColorChanger().applyColor(teamColor, itemStack));
            } else {
                playerInventory.addItem(Main.getInstance().getColorChanger().applyColor(teamColor, itemStack));
            }
        }
    }

    public List<Player> getOnlinePlayers(Collection<UUID> uuids) {
        if (uuids == null) {
            return Collections.emptyList();
        }

        final List<Player> players = new ArrayList<>();

        uuids.forEach(uuid-> {
            final var pl = Bukkit.getPlayer(uuid);
            if (pl != null && pl.isOnline()) {
                players.add(pl);
            }
        });

        return players;
    }

    public List<PlayerWrapper> getOnlinePlayersW(Collection<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return Collections.emptyList();
        }

        return uuids.stream()
                .map(PlayerMapper::getPlayer)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public List<Location> getLocationsBetween(Location loc1, Location loc2){
        int lowX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int lowY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int lowZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());

        final var locationList = new ArrayList<Location>();

        for(int x = 0; x<Math.abs(loc1.getBlockX()-loc2.getBlockX()); x++){
            for(int y = 0; y<Math.abs(loc1.getBlockY()-loc2.getBlockY()); y++){
                for(int z = 0; z<Math.abs(loc1.getBlockZ()-loc2.getBlockZ()); z++){
                    locationList.add(new Location(loc1.getWorld(),lowX+x, lowY+y, lowZ+z));
                }
            }
        }

        return locationList;
    }
}
