package org.screamingsandals.bedwars.game;

import com.onarandombox.MultiverseCore.api.Core;
import io.papermc.lib.PaperLib;
import lombok.Getter;
import lombok.SneakyThrows;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.Nullable;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.ArenaTime;
import org.screamingsandals.bedwars.api.Region;
import org.screamingsandals.bedwars.api.RunningTeam;
import org.screamingsandals.bedwars.api.boss.BossBar;
import org.screamingsandals.bedwars.api.boss.StatusBar;
import org.screamingsandals.bedwars.api.config.ConfigurationContainer;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.api.special.SpecialItem;
import org.screamingsandals.bedwars.api.upgrades.UpgradeRegistry;
import org.screamingsandals.bedwars.api.upgrades.UpgradeStorage;
import org.screamingsandals.bedwars.api.utils.DelayFactory;
import org.screamingsandals.bedwars.boss.XPBar;
import org.screamingsandals.bedwars.commands.AdminCommand;
import org.screamingsandals.bedwars.commands.BedWarsPermission;
import org.screamingsandals.bedwars.commands.StatsCommand;
import org.screamingsandals.bedwars.config.GameConfigurationContainer;
import org.screamingsandals.bedwars.config.MainConfig;
import org.screamingsandals.bedwars.config.RecordSave;
import org.screamingsandals.bedwars.events.*;
import org.screamingsandals.bedwars.holograms.StatisticsHolograms;
import org.screamingsandals.bedwars.inventories.TeamSelectorInventory;
import org.screamingsandals.bedwars.lang.LangKeys;
import org.screamingsandals.bedwars.lib.debug.Debug;
import org.screamingsandals.bedwars.lib.nms.entity.EntityUtils;
import org.screamingsandals.bedwars.listener.Player116ListenerUtils;
import org.screamingsandals.bedwars.player.BedWarsPlayer;
import org.screamingsandals.bedwars.player.PlayerManager;
import org.screamingsandals.bedwars.region.FlatteningRegion;
import org.screamingsandals.bedwars.region.LegacyRegion;
import org.screamingsandals.bedwars.scoreboard.ScreamingScoreboard;
import org.screamingsandals.bedwars.statistics.PlayerStatisticManager;
import org.screamingsandals.bedwars.tab.TabManager;
import org.screamingsandals.bedwars.team.CurrentTeam;
import org.screamingsandals.bedwars.team.Team;
import org.screamingsandals.bedwars.utils.*;
import org.screamingsandals.lib.event.EventManager;
import org.screamingsandals.lib.hologram.HologramManager;
import org.screamingsandals.lib.lang.Message;
import org.screamingsandals.lib.material.MaterialHolder;
import org.screamingsandals.lib.material.builder.ItemFactory;
import org.screamingsandals.lib.player.PlayerMapper;
import org.screamingsandals.lib.player.PlayerWrapper;
import org.screamingsandals.lib.player.SenderWrapper;
import org.screamingsandals.lib.utils.AdventureHelper;
import org.screamingsandals.lib.world.BlockMapper;
import org.screamingsandals.lib.world.LocationHolder;
import org.screamingsandals.lib.world.LocationMapper;
import org.screamingsandals.lib.world.state.BlockStateMapper;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Game implements org.screamingsandals.bedwars.api.game.Game {
    public boolean gameStartItem;
    private String name;
    private Location pos1;
    private Location pos2;
    private Location lobbySpawn;
    private Location lobbyPos1;
    private Location lobbyPos2;
    private Location specSpawn;
    private final List<Team> teams = new ArrayList<>();
    private final List<ItemSpawner> spawners = new ArrayList<>();
    private final Map<Player, RespawnProtection> respawnProtectionMap = new HashMap<>();
    private final List<SavedGameElement<?>> elements = new LinkedList<>();
    private int pauseCountdown;
    private int gameTime;
    private int minPlayers;
    private final List<BedWarsPlayer> players = new ArrayList<>();
    private World world;
    private List<GameStore> gameStore = new ArrayList<>();
    private ArenaTime arenaTime = ArenaTime.WORLD;
    private WeatherType arenaWeather = null;
    private BarColor lobbyBossBarColor = null;
    private BarColor gameBossBarColor = null;
    private String customPrefix = null;
    private boolean preServerRestart = false;
    @Getter
    private File file;

    // STATUS
    private GameStatus previousStatus = GameStatus.DISABLED;
    private GameStatus status = GameStatus.DISABLED;
    private GameStatus afterRebuild = GameStatus.WAITING;
    private int countdown = -1, previousCountdown = -1;
    private int calculatedMaxPlayers;
    private BukkitTask task;
    private final List<CurrentTeam> teamsInGame = new ArrayList<>();
    private final Region region = Main.isLegacy() ? new LegacyRegion() : new FlatteningRegion();
    private TeamSelectorInventory teamSelectorInventory;
    private final Scoreboard gameScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    private StatusBar<PlayerWrapper> statusbar;
    private final Map<Location, ItemStack[]> usedChests = new HashMap<>();
    private final List<SpecialItem> activeSpecialItems = new ArrayList<>();
    private final List<DelayFactory> activeDelays = new ArrayList<>();
    private final Map<BedWarsPlayer, Inventory> fakeEnderChests = new HashMap<>();
    private int postGameWaiting = 3;
    private ScreamingScoreboard experimentalBoard = null;

    @Getter
    private final GameConfigurationContainer configurationContainer = new GameConfigurationContainer();

    private boolean preparing = false;

    private Game() {

    }

    public static Game loadGame(File file) {
        return loadGame(file, true);
    }

    public static Game loadGame(File file, boolean firstAttempt) {
        try {
            if (!file.exists()) {
                return null;
            }

            final var loader = YamlConfigurationLoader.builder()
                    .file(file)
                    .build();

            final ConfigurationNode configMap;
            try {
                configMap = loader.load();
            } catch (ConfigurateException e) {
                e.printStackTrace();
                return null;
            }

            final var game = new Game();
            game.file = file;
            game.name = configMap.node("name").getString();
            game.pauseCountdown = configMap.node("pauseCountdown").getInt();
            game.gameTime = configMap.node("gameTime").getInt();

            var worldName = Objects.requireNonNull(configMap.node("world").getString());
            game.world = Bukkit.getWorld(worldName);

            if (game.world == null) {
                if (Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[B" + ChatColor.WHITE + "W] " + ChatColor.RED + "World " + worldName
                            + " was not found, but we found Multiverse-Core, so we will try to load this world.");

                    Core multiverse = (Core) Bukkit.getPluginManager().getPlugin("Multiverse-Core");
                    if (multiverse != null && multiverse.getMVWorldManager().loadWorld(worldName)) {
                        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[B" + ChatColor.WHITE + "W] " + ChatColor.GREEN + "World " + worldName
                                + " was succesfully loaded with Multiverse-Core, continue in arena loading.");

                        game.world = Bukkit.getWorld(worldName);
                    } else {
                        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[B" + ChatColor.WHITE + "W] " + ChatColor.RED + "Arena " + game.name
                                + " can't be loaded, because world " + worldName + " is missing!");
                        return null;
                    }
                } else if (firstAttempt) {
                    Bukkit.getConsoleSender().sendMessage(
                            ChatColor.RED + "[B" + ChatColor.WHITE + "W] " + ChatColor.YELLOW + "Arena " + game.name + " can't be loaded, because world " + worldName + " is missing! We will try it again after all plugins will be loaded!");
                    Bukkit.getScheduler().runTaskLater(Main.getInstance().getPluginDescription().as(JavaPlugin.class), () -> loadGame(file, false), 10L);
                    return null;
                } else {
                    Bukkit.getConsoleSender().sendMessage(
                            ChatColor.RED + "[B" + ChatColor.WHITE + "W] " + ChatColor.RED + "Arena " + game.name + " can't be loaded, because world " + worldName + " is missing!");
                    return null;
                }
            }

            if (Main.getVersionNumber() >= 115) {
                game.world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            }

            game.pos1 = MiscUtils.readLocationFromString(game.world, Objects.requireNonNull(configMap.node("pos1").getString()));
            game.pos2 = MiscUtils.readLocationFromString(game.world, Objects.requireNonNull(configMap.node("pos2").getString()));


            if (MainConfig.getInstance().node("prevent-spawning-mobs").getBoolean(true)) {
                for (Entity e : game.world.getEntitiesByClass(Monster.class)) {
                    if (ArenaUtils.isInArea(e.getLocation(), game.pos1, game.pos2)) {
                        PaperLib.getChunkAtAsync(e.getLocation())
                                .thenAccept(chunk -> e.remove());
                    }
                }
            }

            game.specSpawn = MiscUtils.readLocationFromString(game.world, Objects.requireNonNull(configMap.node("specSpawn").getString()));
            var spawnWorld = configMap.node("lobbySpawnWorld").getString();
            var lobbySpawnWorld = Bukkit.getWorld(Objects.requireNonNull(spawnWorld));
            if (lobbySpawnWorld == null) {
                if (Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[B" + ChatColor.WHITE + "W] " + ChatColor.RED + "World " + spawnWorld
                            + " was not found, but we found Multiverse-Core, so we will try to load this world.");

                    Core multiverse = (Core) Bukkit.getPluginManager().getPlugin("Multiverse-Core");
                    if (multiverse != null && multiverse.getMVWorldManager().loadWorld(spawnWorld)) {
                        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[B" + ChatColor.WHITE + "W] " + ChatColor.GREEN + "World " + spawnWorld
                                + " was succesfully loaded with Multiverse-Core, continue in arena loading.");

                        lobbySpawnWorld = Bukkit.getWorld(spawnWorld);
                    } else {
                        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[B" + ChatColor.WHITE + "W] " + ChatColor.RED + "Arena " + game.name
                                + " can't be loaded, because world " + spawnWorld + " is missing!");
                        return null;
                    }
                } else if (firstAttempt) {
                    Bukkit.getConsoleSender().sendMessage(
                            ChatColor.RED + "[B" + ChatColor.WHITE + "W] " + ChatColor.YELLOW + "Arena " + game.name + " can't be loaded, because world " + worldName + " is missing! We will try it again after all plugins will be loaded!");
                    Bukkit.getScheduler().runTaskLater(Main.getInstance().getPluginDescription().as(JavaPlugin.class), () -> loadGame(file, false), 10L);
                    return null;
                } else {
                    Bukkit.getConsoleSender().sendMessage(
                            ChatColor.RED + "[B" + ChatColor.WHITE + "W] " + ChatColor.RED + "Arena " + game.name + " can't be loaded, because world " + spawnWorld + " is missing!");
                    return null;
                }
            }

            var lobbyPos1 = configMap.node("lobbyPos1").getString();
            var lobbyPos2 = configMap.node("lobbyPos2").getString();
            if (lobbyPos1 != null && lobbyPos2 != null) {
                game.lobbyPos1 = MiscUtils.readLocationFromString(lobbySpawnWorld, lobbyPos1);
                game.lobbyPos2 = MiscUtils.readLocationFromString(lobbySpawnWorld, lobbyPos2);
            }

            configMap.node("elements").childrenList().forEach(node -> {
                try {
                    var element = new SavedGameElement<>();
                    element.deserialize(node);
                    game.elements.add(element);
                } catch (ConfigurateException ex) {
                    ex.printStackTrace();
                }
            });

            game.lobbySpawn = MiscUtils.readLocationFromString(lobbySpawnWorld, Objects.requireNonNull(configMap.node("lobbySpawn").getString()));
            game.minPlayers = configMap.node("minPlayers").getInt(2);
            configMap.node("teams").childrenMap().forEach((teamN, team) -> {
                var t = new Team();
                var color = team.node("color");
                t.color = OldTeamColor.valueOf(MiscUtils.convertColorToNewFormat(team.node("color").getString(), team.node("isNewColor").getBoolean()));
                t.name = teamN.toString();
                t.bed = MiscUtils.readLocationFromString(game.world, Objects.requireNonNull(team.node("bed").getString()));
                t.maxPlayers = team.node("maxPlayers").getInt();
                t.spawn = MiscUtils.readLocationFromString(game.world, Objects.requireNonNull(team.node("spawn").getString()));
                t.game = game;

                game.teams.add(t);
            });
            configMap.node("spawners").childrenList().forEach(spawner -> {
                var spawnerType = spawner.node("type").getString();
                if (spawnerType == null || Main.getSpawnerType(spawnerType.toLowerCase()) == null) {
                    throw new UnsupportedOperationException("Wrongly configured spawner type!");
                }
                game.spawners.add(new ItemSpawner(
                        MiscUtils.readLocationFromString(game.world, Objects.requireNonNull(spawner.node("location").getString())),
                        Main.getSpawnerType(spawnerType.toLowerCase()),
                        spawner.node("customName").getString(),
                        spawner.node("hologramEnabled").getBoolean(true),
                        spawner.node("startLevel").getDouble(1),
                        game.getTeamFromName(spawner.node("team").getString()),
                        spawner.node("maxSpawnedResources").getInt(-1),
                        spawner.node("floatingEnabled").getBoolean(),
                        org.screamingsandals.lib.hologram.Hologram.RotationMode.valueOf(spawner.node("rotationMode").getString("Y"))
                ));
            }
            );
            configMap.node("stores").childrenList().forEach(store -> {
                if (store.isMap()) {
                    game.gameStore.add(new GameStore(
                            MiscUtils.readLocationFromString(game.world, Objects.requireNonNull(store.node("loc").getString())),
                            store.node("shop").getString(),
                            store.node("parent").getBoolean(true),
                            EntityType.valueOf(store.node("type").getString("VILLAGER").toUpperCase()),
                            store.node("name").getString(""),
                            !store.node("name").empty(),
                            store.node("isBaby").getBoolean(),
                            store.node("skin").getString()
                    ));
                } else {
                    game.gameStore.add(new GameStore(
                            MiscUtils.readLocationFromString(game.world, Objects.requireNonNull(store.getString())),
                            null,
                            true,
                            EntityType.VILLAGER,
                            "",
                            false,
                            false,
                            null
                    ));
                }
            });

            game.configurationContainer.applyNode(configMap.node("constant"));

            game.arenaTime = ArenaTime.valueOf(configMap.node("arenaTime").getString(ArenaTime.WORLD.name()).toUpperCase());
            game.arenaWeather = loadWeather(configMap.node("arenaWeather").getString("default").toUpperCase());

            game.postGameWaiting = configMap.node("postGameWaiting").getInt(3);
            game.customPrefix = configMap.node("customPrefix").getString();

            try {
                game.lobbyBossBarColor = loadBossBarColor(
                        configMap.node("lobbyBossBarColor").getString("default").toUpperCase());
                game.gameBossBarColor = loadBossBarColor(configMap.node("gameBossBarColor").getString("default").toUpperCase());
            } catch (Throwable t) {
                // We're using 1.8
            }

            game.start();
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[B" + ChatColor.WHITE + "W] " + ChatColor.GREEN + "Arena " + ChatColor.WHITE + game.name + " (" + file.getName() + ")" + ChatColor.GREEN + " loaded!");
            return game;
        } catch (Throwable throwable) {
            Debug.warn("Something went wrong while loading arena file " + file.getName() + ". Please report this to our Discord or GitHub!", true);
            throwable.printStackTrace();
            return null;
        }
    }

    public void removeEntityAsync(Entity e) {
        if (ArenaUtils.isInArea(e.getLocation(), pos1, pos2)) {
            PaperLib.getChunkAtAsync(e.getLocation())
                    .thenAccept(chunk -> e.remove());
        }
    }


    public static WeatherType loadWeather(String weather) {
        try {
            return WeatherType.valueOf(weather);
        } catch (Exception e) {
            return null;
        }
    }

    public static BarColor loadBossBarColor(String color) {
        try {
            return BarColor.valueOf(color);
        } catch (Exception e) {
            return null;
        }
    }

    public static Game createGame(String name) {
        Game game = new Game();
        game.name = name;
        game.pauseCountdown = 60;
        game.gameTime = 3600;
        game.minPlayers = 2;

        return game;
    }

    public static String bedExistString() {
        return MainConfig.getInstance().node("scoreboard", "bedExists").getString();
    }

    public static String bedLostString() {
        return MainConfig.getInstance().node("scoreboard", "bedLost").getString();
    }

    public static String anchorEmptyString() {
        return MainConfig.getInstance().node("scoreboard", "anchorEmpty").getString();
    }

    public static boolean isBungeeEnabled() {
        return MainConfig.getInstance().node("bungee", "enabled").getBoolean();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        if (this.world == null) {
            this.world = world;
        }
    }

    public Location getPos1() {
        return pos1;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
    }

    public Location getLobbySpawn() {
        return lobbySpawn;
    }

    public void setLobbySpawn(Location lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
    }

    public int getPauseCountdown() {
        return pauseCountdown;
    }

    public void setPauseCountdown(int pauseCountdown) {
        this.pauseCountdown = pauseCountdown;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    public boolean checkMinPlayers() {
        return players.size() >= getMinPlayers();
    }

    public int countPlayers() {
        return this.players.size();
    }

    public int countSpectators() {
        return (int) this.players.stream().filter(t -> t.isSpectator && getPlayerTeam(t) == null).count();
    }

    public int countSpectating() {
        return (int) this.players.stream().filter(t -> t.isSpectator).count();
    }

    public int countRespawnable() {
        return (int) this.players.stream().filter(t -> getPlayerTeam(t) != null).count();
    }

    public int countAlive() {
        return (int) this.players.stream().filter(t -> !t.isSpectator).count();
    }

    @Override
    public List<org.screamingsandals.bedwars.api.game.GameStore> getGameStores() {
        return new ArrayList<>(gameStore);
    }

    public void setGameStores(List<GameStore> gameStore) {
        this.gameStore = gameStore;
    }

    public List<GameStore> getGameStoreList() {
        return gameStore;
    }

    public Location getSpecSpawn() {
        return specSpawn;
    }

    public void setSpecSpawn(Location specSpawn) {
        this.specSpawn = specSpawn;
    }

    public int getGameTime() {
        return gameTime;
    }

    public void setGameTime(int gameTime) {
        this.gameTime = gameTime;
    }

    @Override
    public Team getTeamFromName(String name) {
        if (name == null) {
            return null;
        }

        Team team = null;
        for (Team t : getTeams()) {
            if (t.getName().equalsIgnoreCase(name)) {
                team = t;
            }
        }
        return team;
    }

    public List<Team> getTeams() {
        return teams;
    }

    public List<ItemSpawner> getSpawners() {
        return spawners;
    }

    public TeamSelectorInventory getTeamSelectorInventory() {
        return teamSelectorInventory;
    }

    public boolean isBlockAddedDuringGame(Location loc) {
        return status == GameStatus.RUNNING && region.isBlockAddedDuringGame(loc);
    }

    public boolean blockPlace(BedWarsPlayer player, Block block, BlockState replaced, ItemStack itemInHand) {
        if (status != GameStatus.RUNNING) {
            return false; // ?
        }
        if (player.isSpectator) {
            return false;
        }
        if (Main.isFarmBlock(block.getType())) {
            return true;
        }
        if (!ArenaUtils.isInArea(block.getLocation(), pos1, pos2)) {
            return false;
        }

        var event = new PlayerBuildBlockEventImpl(this, player, getPlayerTeam(player), BlockMapper.wrapBlock(block), BlockStateMapper.wrapBlockState(replaced).orElse(null), ItemFactory.build(itemInHand).orElse(ItemFactory.getAir()));
        EventManager.fire(event);

        if (event.isCancelled()) {
            return false;
        }

        if (replaced.getType() != Material.AIR) {
            if (region.isBlockAddedDuringGame(replaced.getLocation())) {
                return true;
            } else if (Main.isBreakableBlock(replaced.getType()) || region.isLiquid(replaced.getType())) {
                region.putOriginalBlock(block.getLocation(), replaced);
            } else {
                return false;
            }
        }
        region.addBuiltDuringGame(block.getLocation());

        return true;
    }

    public boolean blockBreak(BedWarsPlayer player, Block block, BlockBreakEvent event) {
        if (status != GameStatus.RUNNING) {
            return false; // ?
        }
        if (player.isSpectator) {
            return false;
        }
        if (Main.isFarmBlock(block.getType())) {
            return true;
        }
        if (!ArenaUtils.isInArea(block.getLocation(), pos1, pos2)) {
            return false;
        }

        var breakEvent = new PlayerBreakBlockEventImpl(this, player, getPlayerTeam(player), BlockMapper.wrapBlock(block), true);
        EventManager.fire(breakEvent);

        if (breakEvent.isCancelled()) {
            return false;
        }

        if (region.isBlockAddedDuringGame(block.getLocation())) {
            region.removeBlockBuiltDuringGame(block.getLocation());

            if (block.getType() == Material.ENDER_CHEST) {
                CurrentTeam team = getTeamOfChest(block);
                if (team != null) {
                    team.removeTeamChest(block);
                    var message = Message.of(LangKeys.SPECIALS_TEAM_CHEST_BROKEN).prefixOrDefault(getCustomPrefixComponent());
                    for (BedWarsPlayer gp : team.players) {
                        gp.sendMessage(message);
                    }

                    if (breakEvent.isDrops()) {
                        event.setDropItems(false);
                        player.getPlayerInventory().addItem(ItemFactory.build("ENDER_CHEST").orElse(ItemFactory.getAir()));
                    }
                }
            }

            if (!breakEvent.isDrops()) {
                try {
                    event.setDropItems(false);
                } catch (Throwable tr) {
                    block.setType(Material.AIR);
                }
            }
            return true;
        }

        Location loc = block.getLocation();
        if (region.isBedBlock(block.getState())) {
            if (!region.isBedHead(block.getState())) {
                loc = region.getBedNeighbor(block).getLocation();
            }
        }
        if (isTargetBlock(loc)) {
            if (region.isBedBlock(block.getState())) {
                if (getPlayerTeam(player).teamInfo.bed.equals(loc)) {
                    return false;
                }
                bedDestroyed(loc, player.as(Player.class), true, false, false);
                region.putOriginalBlock(block.getLocation(), block.getState());
                if (block.getLocation().equals(loc)) {
                    Block neighbor = region.getBedNeighbor(block);
                    region.putOriginalBlock(neighbor.getLocation(), neighbor.getState());
                } else {
                    region.putOriginalBlock(loc, region.getBedNeighbor(block).getState());
                }
                try {
                    event.setDropItems(false);
                } catch (Throwable tr) {
                    if (region.isBedHead(block.getState())) {
                        region.getBedNeighbor(block).setType(Material.AIR);
                    } else {
                        block.setType(Material.AIR);
                    }
                }
                return true;
            } else if (configurationContainer.getOrDefault(ConfigurationContainer.CAKE_TARGET_BLOCK_EATING, Boolean.class, false) && block.getType().name().contains("CAKE")) {
                return false; // when CAKES are in eating mode, don't allow to just break it
            } else {
                if (getPlayerTeam(player).teamInfo.bed.equals(loc)) {
                    return false;
                }
                bedDestroyed(loc, player.as(Player.class), false, "RESPAWN_ANCHOR".equals(block.getType().name()), block.getType().name().contains("CAKE"));
                region.putOriginalBlock(loc, block.getState());
                try {
                    event.setDropItems(false);
                } catch (Throwable tr) {
                    block.setType(Material.AIR);
                }
                return true;
            }
        }
        if (Main.isBreakableBlock(block.getType())) {
            region.putOriginalBlock(block.getLocation(), block.getState());
            return true;
        }
        return false;
    }

    public void targetBlockExplode(RunningTeam team) {
        Location loc = team.getTargetBlock();
        Block block = loc.getBlock();
        if (region.isBedBlock(block.getState())) {
            if (!region.isBedHead(block.getState())) {
                loc = region.getBedNeighbor(block).getLocation();
            }
        }
        if (isTargetBlock(loc)) {
            if (region.isBedBlock(block.getState())) {
                bedDestroyed(loc, null, true, false, false);
                region.putOriginalBlock(block.getLocation(), block.getState());
                if (block.getLocation().equals(loc)) {
                    Block neighbor = region.getBedNeighbor(block);
                    region.putOriginalBlock(neighbor.getLocation(), neighbor.getState());
                } else {
                    region.putOriginalBlock(loc, region.getBedNeighbor(block).getState());
                }
                if (region.isBedHead(block.getState())) {
                    region.getBedNeighbor(block).setType(Material.AIR);
                } else {
                    block.setType(Material.AIR);
                }
            } else {
                bedDestroyed(loc, null, false, "RESPAWN_ANCHOR".equals(block.getType().name()), block.getType().name().contains("CAKE"));
                region.putOriginalBlock(loc, block.getState());
                block.setType(Material.AIR);
            }
        }
    }

    private boolean isTargetBlock(Location loc) {
        for (CurrentTeam team : teamsInGame) {
            if (team.isBed && team.teamInfo.bed.equals(loc)) {
                return true;
            }
        }
        return false;
    }

    public Region getRegion() {
        return region;
    }

    public CurrentTeam getPlayerTeam(BedWarsPlayer player) {
        for (CurrentTeam team : teamsInGame) {
            if (team.players.contains(player)) {
                return team;
            }
        }
        return null;
    }

    public CurrentTeam getCurrentTeamFromTeam(org.screamingsandals.bedwars.api.team.Team team) {
        for (CurrentTeam currentTeam : teamsInGame) {
            if (currentTeam.teamInfo == team) {
                return currentTeam;
            }
        }
        return null;
    }

    public void bedDestroyed(Location loc, Player broker, boolean isItBedBlock, boolean isItAnchor, boolean isItCake) {
        if (status == GameStatus.RUNNING) {
            for (CurrentTeam team : teamsInGame) {
                if (team.teamInfo.bed.equals(loc)) {
                    Debug.info(name + ": target block of  " + team.teamInfo.getName() + " has been destroyed");
                    team.isBed = false;
                    updateScoreboard();
                    String colored_broker = "explosion";
                    if (broker != null) {
                        colored_broker = getPlayerTeam(PlayerManager.getInstance().getPlayer(broker.getUniqueId()).orElseThrow()).teamInfo.color.chatColor + broker.getDisplayName();
                    }
                    for (BedWarsPlayer player : players) {
                        Message
                                .of(isItBedBlock ? LangKeys.IN_GAME_TARGET_BLOCK_DESTROYED_BED : (isItAnchor ? LangKeys.IN_GAME_TARGET_BLOCK_DESTROYED_ANCHOR : (isItCake ? LangKeys.IN_GAME_TARGET_BLOCK_DESTROYED_CAKE : LangKeys.IN_GAME_TARGET_BLOCK_DESTROYED_ANY)))
                                .placeholder("team", AdventureHelper.toComponent(team.teamInfo.color.chatColor + team.teamInfo.name))
                                .placeholder("broker", AdventureHelper.toComponent(colored_broker))
                                .send(player)
                                .join(getPlayerTeam(player) == team ? LangKeys.IN_GAME_TARGET_BLOCK_DESTROYED_SUBTITLE_VICTIM : LangKeys.IN_GAME_GAME_START_SUBTITLE)
                                .times(TitleUtils.defaultTimes())
                                .title(player);

                        SpawnEffects.spawnEffect(this, player, "game-effects.beddestroy");
                        Sounds.playSound(player.as(Player.class), player.as(Player.class).getLocation(),
                                MainConfig.getInstance().node("sounds", "bed_destroyed").getString(),
                                Sounds.ENTITY_ENDER_DRAGON_GROWL, 1, 1);
                    }

                    if (team.hasBedHolo()) {
                        team.getBedHolo().firstLine(Message.of(isItBedBlock ? LangKeys.IN_GAME_TARGET_BLOCK_HOLOGRAM_DESTROYED_BED : (isItAnchor ? LangKeys.IN_GAME_TARGET_BLOCK_HOLOGRAM_DESTROYED_ANCHOR : (isItCake ? LangKeys.IN_GAME_TARGET_BLOCK_HOLOGRAM_DESTROYED_CAKE : LangKeys.IN_GAME_TARGET_BLOCK_HOLOGRAM_DESTROYED_ANY))).asTextEntry(null));
                        team.getConnectedPlayers().stream().map(PlayerMapper::wrapPlayer).forEach(team.getBedHolo()::addViewer);
                    }

                    if (team.hasProtectHolo()) {
                        team.getProtectHolo().destroy();
                        team.setProtectHolo(null);
                    }

                    var targetBlockDestroyed = new TargetBlockDestroyedEventImpl(this, broker != null ? PlayerManager.getInstance().getPlayer(broker.getUniqueId()).orElseThrow() : null, team);
                    EventManager.fire(targetBlockDestroyed);

                    if (broker != null) {
                        if (PlayerStatisticManager.isEnabled()) {
                            var statistic = PlayerStatisticManager.getInstance().getStatistic(PlayerMapper.wrapPlayer(broker));
                            statistic.addDestroyedBeds(1);
                            statistic.addScore(MainConfig.getInstance().node("statistics", "scores", "bed-destroy").getInt(25));
                        }

                        dispatchRewardCommands("player-destroy-bed", broker,
                                MainConfig.getInstance().node("statistics", "scores", "bed-destroy").getInt(25));
                    }
                }
            }
        }
    }

    public void internalJoinPlayer(BedWarsPlayer gamePlayer) {
        var joinEvent = new PlayerJoinEventImpl(this, gamePlayer);
        EventManager.fire(joinEvent);

        if (joinEvent.isCancelled()) {
            Debug.info(gamePlayer.getName() + " can't join to the game: event cancelled");
            String message = joinEvent.getCancelMessage();
            if (message != null && !message.equals("")) {
                gamePlayer.sendMessage(message);
            }
            gamePlayer.changeGame(null);
            return;
        }
        Debug.info(gamePlayer.getName() + " joined bedwars match " + name);

        boolean isEmpty = players.isEmpty();
        if (!players.contains(gamePlayer)) {
            players.add(gamePlayer);
        }
        updateSigns();

        if (PlayerStatisticManager.isEnabled()) {
            // Load
            PlayerStatisticManager.getInstance().getStatistic(gamePlayer);
        }

        if (arenaTime.time >= 0) {
            gamePlayer.as(Player.class).setPlayerTime(arenaTime.time, false);
        }

        if (arenaWeather != null) {
            gamePlayer.as(Player.class).setPlayerWeather(arenaWeather);
        }

        if (TabManager.isEnabled()) {
            players.forEach(TabManager.getInstance()::modifyForPlayer);
        }

        if (MainConfig.getInstance().node("tab", "enabled").getBoolean() && MainConfig.getInstance().node("tab", "hide-foreign-players").getBoolean()) {
            Bukkit.getOnlinePlayers().stream().filter(p -> PlayerManager.getInstance().getGameOfPlayer(p.getUniqueId()).orElse(null) != this).forEach(gamePlayer::hidePlayer);
            players.forEach(p -> p.showPlayer(gamePlayer.as(Player.class)));
        }

        if (status == GameStatus.WAITING) {
            Debug.info(gamePlayer.getName() + " moving to lobby");
            Message
                    .of(LangKeys.IN_GAME_JOIN)
                    .placeholder("name", AdventureHelper.toComponent(gamePlayer.as(Player.class).getDisplayName()))
                    .placeholder("players", players.size())
                    .placeholder("maxplayers", calculatedMaxPlayers)
                    .prefixOrDefault(getCustomPrefixComponent())
                    .send(getConnectedPlayers().stream().map(PlayerMapper::wrapPlayer).collect(Collectors.toList()));

            gamePlayer.teleport(lobbySpawn, () -> {
                gamePlayer.invClean(); // temp fix for inventory issues?
                SpawnEffects.spawnEffect(Game.this, gamePlayer, "game-effects.lobbyjoin");

                if (configurationContainer.getOrDefault(ConfigurationContainer.JOIN_RANDOM_TEAM_ON_JOIN, Boolean.class, false)) {
                    joinRandomTeam(gamePlayer);
                }

                if (configurationContainer.getOrDefault(ConfigurationContainer.COMPASS, Boolean.class, false)) {
                    int compassPosition = MainConfig.getInstance().node("hotbar", "selector").getInt(0);
                    if (compassPosition >= 0 && compassPosition <= 8) {
                        var compass = MainConfig.getInstance().readDefinedItem("jointeam", "COMPASS");
                        compass.setDisplayName(Message.of(LangKeys.IN_GAME_LOBBY_ITEMS_COMPASS_SELECTOR_TEAM).asComponent(gamePlayer));
                        gamePlayer.getPlayerInventory().setItem(compassPosition, compass);
                    }
                }

                int leavePosition = MainConfig.getInstance().node("hotbar", "leave").getInt(8);
                if (leavePosition >= 0 && leavePosition <= 8) {
                    var leave = MainConfig.getInstance().readDefinedItem("leavegame", "SLIME_BALL");
                    leave.setDisplayName(Message.of(LangKeys.IN_GAME_LOBBY_ITEMS_LEAVE_FROM_GAME_ITEM).asComponent(gamePlayer));
                    gamePlayer.getPlayerInventory().setItem(leavePosition, leave);
                }

                if (gamePlayer.hasPermission(BedWarsPermission.START_ITEM_PERMISSION.asPermission())) {
                    int vipPosition = MainConfig.getInstance().node("hotbar", "start").getInt(1);
                    if (vipPosition >= 0 && vipPosition <= 8) {
                        var startGame = MainConfig.getInstance().readDefinedItem("startgame", "DIAMOND");
                        startGame.setDisplayName(Message.of(LangKeys.IN_GAME_LOBBY_ITEMS_START_GAME_ITEM).asComponent(gamePlayer));
                        gamePlayer.getPlayerInventory().setItem(vipPosition, startGame);
                    }
                }
            });

            if (isEmpty) {
                runTask();
            } else {
                statusbar.addPlayer(gamePlayer);
            }
        }

        if (status == GameStatus.RUNNING || status == GameStatus.GAME_END_CELEBRATING) {
            if (MainConfig.getInstance().node("tab", "enabled").getBoolean() && MainConfig.getInstance().node("tab", "hide-spectators").getBoolean()) {
                players.stream().filter(p -> p.isSpectator && !isPlayerInAnyTeam(p.as(Player.class))).forEach(p -> gamePlayer.hidePlayer(p.as(Player.class)));
            }

            makeSpectator(gamePlayer, true);

            spawners.forEach(itemSpawner -> {
                if (itemSpawner.getHologram() != null) {
                    itemSpawner.getHologram().addViewer(gamePlayer);
                }
            });
            teamsInGame.forEach(currentTeam -> {
                if (currentTeam.hasBedHolo()) {
                    currentTeam.getBedHolo().addViewer(gamePlayer);
                }
            });
        }

        if (experimentalBoard != null) {
            experimentalBoard.addPlayer(gamePlayer);
        }

        EventManager.fire(new PlayerJoinedEventImpl(this, gamePlayer, getPlayerTeam(gamePlayer)));
    }

    public void internalLeavePlayer(BedWarsPlayer gamePlayer) {
        if (status == GameStatus.DISABLED) {
            return;
        }

        var playerLeaveEvent = new PlayerLeaveEventImpl(this, gamePlayer, getPlayerTeam(gamePlayer));
        EventManager.fire(playerLeaveEvent);
        Debug.info(name + ": player  " + gamePlayer.getName() + " is leaving the game");

        if (experimentalBoard != null) {
            experimentalBoard.removePlayer(gamePlayer);
        }

        if (!gamePlayer.isSpectator && !preServerRestart) {
            Message.of(LangKeys.IN_GAME_LEAVE)
                    .prefixOrDefault(getCustomPrefixComponent())
                    .placeholder("name", AdventureHelper.toComponent(gamePlayer.as(Player.class).getDisplayName()))
                    .placeholder("players", players.size())
                    .placeholder("maxplayers", calculatedMaxPlayers)
                    .send(players);
        }

        players.remove(gamePlayer);
        updateSigns();

        if (status == GameStatus.WAITING) {
            SpawnEffects.spawnEffect(this, gamePlayer, "game-effects.lobbyleave");
        }

        if (TabManager.isEnabled()) {
            TabManager.getInstance().clear(gamePlayer);
            players.forEach(TabManager.getInstance()::modifyForPlayer);
        }

        if (MainConfig.getInstance().node("tab", "enabled").getBoolean() && MainConfig.getInstance().node("tab", "hide-foreign-players").getBoolean()) {
            players.forEach(p -> p.hidePlayer(gamePlayer.as(Player.class)));
        }

        statusbar.removePlayer(gamePlayer);
        spawners.forEach(spawner -> {
            if (spawner.getHologram() != null) {
                spawner.getHologram().removeViewer(gamePlayer);
            }
        });
        teamsInGame.forEach(team -> {
            if (team.hasBedHolo() && team.getConnectedPlayers().contains(gamePlayer.as(Player.class))) {
                team.getBedHolo().removeViewer(gamePlayer);
            }
            if (team.hasProtectHolo() && team.getConnectedPlayers().contains(gamePlayer.as(Player.class))) {
                team.getProtectHolo().removeViewer(gamePlayer);
            }
        });
        gamePlayer.as(Player.class).setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        if (MainConfig.getInstance().node("mainlobby", "enabled").getBoolean()
                && !MainConfig.getInstance().node("bungee", "enabled").getBoolean()) {
            try {
                Location mainLobbyLocation = MiscUtils.readLocationFromString(
                        Bukkit.getWorld(MainConfig.getInstance().node("mainlobby", "world").getString()),
                        MainConfig.getInstance().node("mainlobby", "location").getString());
                gamePlayer.teleport(mainLobbyLocation);
                gamePlayer.mainLobbyUsed = true;
            } catch (Throwable t) {
                Bukkit.getLogger().severe("You didn't setup properly the mainlobby! Do it via commands not directly in config.yml");
            }
        }

        if (status == GameStatus.RUNNING || status == GameStatus.WAITING) {
            CurrentTeam team = getPlayerTeam(gamePlayer);
            if (team != null) {
                team.players.remove(gamePlayer);
                if (status == GameStatus.WAITING) {
                    team.getScoreboardTeam().removeEntry(gamePlayer.getName());
                    if (team.players.isEmpty()) {
                        teamsInGame.remove(team);
                        team.getScoreboardTeam().unregister();
                    }
                } else {
                    updateScoreboard();
                }
            }
        }

        if (PlayerStatisticManager.isEnabled()) {
            var playerStatisticManager = PlayerStatisticManager.getInstance();
            var statistic = playerStatisticManager.getStatistic(gamePlayer);
            playerStatisticManager.storeStatistic(statistic);

            playerStatisticManager.unloadStatistic(gamePlayer);
        }

        if (players.isEmpty()) {
            if (!preServerRestart) {
                EventManager.fire(new PlayerLastLeaveEventImpl(this, gamePlayer, playerLeaveEvent.getTeam()));
            }

            if (status != GameStatus.WAITING) {
                afterRebuild = GameStatus.WAITING;
                updateSigns();
                rebuild();
            } else {
                cancelTask();
            }
            countdown = -1;

            if (gameScoreboard.getObjective("display") != null) {
                gameScoreboard.getObjective("display").unregister();
            }
            if (gameScoreboard.getObjective("lobby") != null) {
                gameScoreboard.getObjective("lobby").unregister();
            }
            gameScoreboard.clearSlot(DisplaySlot.SIDEBAR);

            for (CurrentTeam team : teamsInGame) {
                team.getScoreboardTeam().unregister();
            }
            teamsInGame.clear();

            for (GameStore store : gameStore) {
                LivingEntity villager = store.kill();
                if (villager != null) {
                    Main.unregisterGameEntity(villager);
                }
            }
        }
    }

    @SneakyThrows
    public void saveToConfig() {
        var dir = Main.getInstance().getPluginDescription().getDataFolder().resolve("arenas").toFile();
        if (!dir.exists())
            dir.mkdirs();
        if (file == null) {
            do {
                file = new File(dir, UUID.randomUUID() + ".yml");
            } while (file.exists());
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        var loader = YamlConfigurationLoader.builder()
                .file(file)
                .build();

        var configMap = loader.createNode();
        configMap.node("name").set(name);
        configMap.node("pauseCountdown").set(pauseCountdown);
        configMap.node("gameTime").set(gameTime);
        configMap.node("world").set(world.getName());
        configMap.node("pos1").set(MiscUtils.setLocationToString(pos1));
        configMap.node("pos2").set(MiscUtils.setLocationToString(pos2));
        configMap.node("specSpawn").set(MiscUtils.setLocationToString(specSpawn));
        configMap.node("lobbySpawn").set(MiscUtils.setLocationToString(lobbySpawn));
        if (lobbyPos1 != null)
            configMap.node("lobbyPos1", MiscUtils.setLocationToString(lobbyPos1));
        if (lobbyPos2 != null)
            configMap.node("lobbyPos2", MiscUtils.setLocationToString(lobbyPos2));
        configMap.node("lobbySpawnWorld").set(lobbySpawn.getWorld().getName());
        configMap.node("minPlayers").set(minPlayers);
        configMap.node("postGameWaiting").set(postGameWaiting);
        configMap.node("customPrefix").set(customPrefix);
        if (!teams.isEmpty()) {
            for (var t : teams) {
                var teamNode = configMap.node("teams", t.name);
                teamNode.node("isNewColor").set(true);
                teamNode.node("color").set(t.color.name());
                teamNode.node("maxPlayers").set(t.maxPlayers);
                teamNode.node("bed").set(MiscUtils.setLocationToString(t.bed));
                teamNode.node("spawn").set(MiscUtils.setLocationToString(t.spawn));
            }
        }
        for (var spawner : spawners) {
            var spawnerNode = configMap.node("spawners").appendListNode();
            spawnerNode.node("location").set(MiscUtils.setLocationToString(spawner.loc));
            spawnerNode.node("type").set(spawner.type.getConfigKey());
            spawnerNode.node("customName").set(spawner.customName);
            spawnerNode.node("startLevel").set(spawner.startLevel);
            spawnerNode.node("hologramEnabled").set(spawner.hologramEnabled);
            spawnerNode.node("team").set(spawner.getTeam().map(org.screamingsandals.bedwars.api.team.Team::getName).orElse(null));
            spawnerNode.node("maxSpawnedResources").set(spawner.maxSpawnedResources);
            spawnerNode.node("floatingEnabled").set(spawner.maxSpawnedResources);
            spawnerNode.node("rotationMode").set(spawner.rotationMode);
        }
        for (var store : gameStore) {
            var storeNode = configMap.node("stores").appendListNode();
            storeNode.node("loc").set(MiscUtils.setLocationToString(store.getStoreLocation()));
            storeNode.node("shop").set(store.getShopFile());
            storeNode.node("parent").set(store.getUseParent() ? "true" : "false");
            storeNode.node("type").set(store.getEntityType().name());
            if (store.isShopCustomName()) {
                storeNode.node("name").set(store.getShopCustomName());
            }
            storeNode.node("isBaby").set(store.isBaby() ? "true" : "false");
            storeNode.node("skin").set(store.getSkinName());
        }

        configMap.node("constant").from(configurationContainer.getSaved());

        configMap.node("arenaTime").set(arenaTime.name());
        configMap.node("arenaWeather").set(arenaWeather == null ? "default" : arenaWeather.name());

        try {
            configMap.node("lobbyBossBarColor").set(lobbyBossBarColor == null ? "default" : lobbyBossBarColor.name());
            configMap.node("gameBossBarColor").set(gameBossBarColor == null ? "default" : gameBossBarColor.name());
        } catch (Throwable t) {
            // We're using 1.8
        }

        var elementsNode = configMap.node("elements");

        elements.forEach(savedGameElement -> {
            try {
                savedGameElement.serialize(elementsNode.appendListNode());
            } catch (ConfigurateException e) {
                e.printStackTrace();
            }
        });

        try {
            loader.save(configMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        if (status == GameStatus.DISABLED) {
            preparing = true;
            status = GameStatus.WAITING;
            countdown = -1;
            calculatedMaxPlayers = 0;
            for (Team team : teams) {
                calculatedMaxPlayers += team.maxPlayers;
            }
            new BukkitRunnable() {
                public void run() {
                    updateSigns();
                }
            }.runTask(Main.getInstance().getPluginDescription().as(JavaPlugin.class));

            if (MainConfig.getInstance().node("bossbar", "use-xp-bar").getBoolean(false)) {
                statusbar = new XPBar();
            } else {
                statusbar = new org.screamingsandals.bedwars.boss.BossBar();
            }
            preparing = false;
        }
    }

    public void stop() {
        if (status == GameStatus.DISABLED) {
            return; // Game is already stopped
        }
        List<BedWarsPlayer> clonedPlayers = (List<BedWarsPlayer>) ((ArrayList<BedWarsPlayer>) players).clone();
        for (BedWarsPlayer p : clonedPlayers)
            p.changeGame(null);
        if (status != GameStatus.REBUILDING) {
            status = GameStatus.DISABLED;
            updateSigns();
        } else {
            afterRebuild = GameStatus.DISABLED;
        }
    }

    public void joinToGame(Player player) {
        if (status == GameStatus.DISABLED) {
            return;
        }

        if (preparing) {
            Bukkit.getScheduler().runTaskLater(Main.getInstance().getPluginDescription().as(JavaPlugin.class), () -> joinToGame(player), 1L);
            return;
        }

        if (status == GameStatus.REBUILDING) {
            if (isBungeeEnabled()) {
                BungeeUtils.movePlayerToBungeeServer(player, false);
                BungeeUtils.sendPlayerBungeeMessage(player, AdventureHelper.toLegacy(Message
                                .of(LangKeys.IN_GAME_ERRORS_GAME_IS_REBUILDING)
                                .prefixOrDefault(getCustomPrefixComponent())
                                .placeholder("arena", this.name)
                                .asComponent(PlayerMapper.wrapPlayer(player))
                        ));
            } else {
                Message
                        .of(LangKeys.IN_GAME_ERRORS_GAME_IS_REBUILDING)
                        .placeholder("arena", this.name)
                        .prefixOrDefault(getCustomPrefixComponent())
                        .send(PlayerMapper.wrapPlayer(player));
            }
            return;
        }

        if ((status == GameStatus.RUNNING || status == GameStatus.GAME_END_CELEBRATING)
                && !configurationContainer.getOrDefault(ConfigurationContainer.SPECTATOR_JOIN, Boolean.class, false)) {
            if (isBungeeEnabled()) {
                BungeeUtils.movePlayerToBungeeServer(player, false);
                BungeeUtils.sendPlayerBungeeMessage(player, AdventureHelper.toLegacy(
                        Message
                                .of(LangKeys.IN_GAME_ERRORS_GAME_ALREADY_RUNNING)
                                .placeholder("arena", this.name)
                                .prefixOrDefault(getCustomPrefixComponent())
                                .asComponent(PlayerMapper.wrapPlayer(player))
                ));
            } else {
                Message
                        .of(LangKeys.IN_GAME_ERRORS_GAME_ALREADY_RUNNING)
                        .placeholder("arena", this.name)
                        .prefixOrDefault(getCustomPrefixComponent())
                        .send(PlayerMapper.wrapPlayer(player));
            }
            return;
        }

        if (players.size() >= calculatedMaxPlayers && status == GameStatus.WAITING) {
            if (PlayerManager.getInstance().getPlayerOrCreate(PlayerMapper.wrapPlayer(player)).canJoinFullGame()) {
                List<BedWarsPlayer> withoutVIP = getPlayersWithoutVIP();

                if (withoutVIP.size() == 0) {
                    Message
                            .of(LangKeys.IN_GAME_ERRORS_VIP_GAME_IS_FULL)
                            .prefixOrDefault(getCustomPrefixComponent())
                            .send(PlayerMapper.wrapPlayer(player));
                    return;
                }

                BedWarsPlayer kickPlayer;
                if (withoutVIP.size() == 1) {
                    kickPlayer = withoutVIP.get(0);
                } else {
                    kickPlayer = withoutVIP.get(MiscUtils.randInt(0, players.size() - 1));
                }

                if (isBungeeEnabled()) {
                    BungeeUtils.sendPlayerBungeeMessage(kickPlayer.as(Player.class), AdventureHelper.toLegacy(
                            Message
                                    .of(LangKeys.IN_GAME_ERRORS_GAME_KICKED_BY_VIP)
                                    .placeholder("arena", this.name)
                                    .prefixOrDefault(getCustomPrefixComponent())
                                    .asComponent(kickPlayer)
                    ));
                } else {
                    Message
                            .of(LangKeys.IN_GAME_ERRORS_GAME_KICKED_BY_VIP)
                            .placeholder("arena", this.name)
                            .prefixOrDefault(getCustomPrefixComponent())
                            .send(kickPlayer);
                }
                kickPlayer.changeGame(null);
            } else {
                if (isBungeeEnabled()) {
                    BungeeUtils.movePlayerToBungeeServer(player, false);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            BungeeUtils.sendPlayerBungeeMessage(player, AdventureHelper.toLegacy(
                                    Message
                                            .of(LangKeys.IN_GAME_ERRORS_GAME_IS_FULL)
                                            .placeholder("arena", Game.this.name)
                                            .prefixOrDefault(getCustomPrefixComponent())
                                            .asComponent(PlayerMapper.wrapPlayer(player))
                            ));
                        }
                    }.runTaskLater(Main.getInstance().getPluginDescription().as(JavaPlugin.class), 5L);
                } else {
                    Message
                            .of(LangKeys.IN_GAME_ERRORS_GAME_IS_FULL)
                            .placeholder("arena", this.name)
                            .prefixOrDefault(getCustomPrefixComponent())
                            .send(PlayerMapper.wrapPlayer(player));
                }
                return;
            }
        }

        BedWarsPlayer gPlayer = PlayerManager.getInstance().getPlayerOrCreate(PlayerMapper.wrapPlayer(player));
        gPlayer.changeGame(this);
    }

    public void leaveFromGame(Player player) {
        if (status == GameStatus.DISABLED) {
            return;
        }
        if (PlayerManager.getInstance().isPlayerInGame(player.getUniqueId())) {
            BedWarsPlayer gPlayer = PlayerManager.getInstance().getPlayerOrCreate(PlayerMapper.wrapPlayer(player));

            if (gPlayer.getGame() == this) {
                gPlayer.changeGame(null);
                if (status == GameStatus.RUNNING || status == GameStatus.GAME_END_CELEBRATING) {
                    updateScoreboard();
                }
            }
        }
    }

    public CurrentTeam getCurrentTeamByTeam(Team team) {
        for (CurrentTeam current : teamsInGame) {
            if (current.teamInfo == team) {
                return current;
            }
        }
        return null;
    }

    public Team getFirstTeamThatIsntInGame() {
        for (Team team : teams) {
            if (getCurrentTeamByTeam(team) == null) {
                return team;
            }
        }
        return null;
    }

    public CurrentTeam getTeamWithLowestPlayers() {
        CurrentTeam lowest = null;

        for (CurrentTeam team : teamsInGame) {
            if (lowest == null) {
                lowest = team;
            }

            if (lowest.players.size() > team.players.size()) {
                lowest = team;
            }
        }

        return lowest;
    }

    public List<BedWarsPlayer> getPlayersInTeam(Team team) {
        CurrentTeam currentTeam = null;
        for (CurrentTeam cTeam : teamsInGame) {
            if (cTeam.teamInfo == team) {
                currentTeam = cTeam;
            }
        }

        if (currentTeam != null) {
            return currentTeam.players;
        } else {
            return new ArrayList<>();
        }
    }

    private void internalTeamJoin(BedWarsPlayer player, Team teamForJoin) {
        CurrentTeam current = null;
        for (CurrentTeam t : teamsInGame) {
            if (t.teamInfo == teamForJoin) {
                current = t;
                break;
            }
        }

        CurrentTeam cur = getPlayerTeam(player);
        var event = new PlayerJoinTeamEventImpl(this, player, current, cur);
        EventManager.fire(event);

        if (event.isCancelled()) {
            return;
        }

        if (current == null) {
            current = new CurrentTeam(teamForJoin, this);
            org.bukkit.scoreboard.Team scoreboardTeam = gameScoreboard.getTeam(teamForJoin.name);
            if (scoreboardTeam == null) {
                scoreboardTeam = gameScoreboard.registerNewTeam(teamForJoin.name);
            }
            if (!Main.isLegacy()) {
                scoreboardTeam.setColor(teamForJoin.color.chatColor);
            } else {
                scoreboardTeam.setPrefix(teamForJoin.color.chatColor.toString());
            }
            scoreboardTeam.setAllowFriendlyFire(configurationContainer.getOrDefault(ConfigurationContainer.FRIENDLY_FIRE, Boolean.class, false));
            current.setScoreboardTeam(scoreboardTeam);
        }

        if (cur == current) {
            Message
                    .of(LangKeys.IN_GAME_TEAM_SELECTION_ALREADY_SELECTED)
                    .prefixOrDefault(getCustomPrefixComponent())
                    .placeholder("team", AdventureHelper.toComponent(teamForJoin.color.chatColor + teamForJoin.name))
                    .placeholder("players", current.players.size())
                    .placeholder("maxplayers", current.teamInfo.maxPlayers)
                    .send(player);
            return;
        }
        if (current.players.size() >= current.teamInfo.maxPlayers) {
            if (cur != null) {
                Message
                        .of(LangKeys.IN_GAME_TEAM_SELECTION_FULL_NO_CHANGE)
                        .prefixOrDefault(getCustomPrefixComponent())
                        .placeholder("team", AdventureHelper.toComponent(teamForJoin.color.chatColor + teamForJoin.name))
                        .placeholder("oldteam", AdventureHelper.toComponent(cur.teamInfo.color.chatColor + cur.teamInfo.name))
                        .send(player);
            } else {
                Message
                        .of(LangKeys.IN_GAME_TEAM_SELECTION_FULL)
                        .prefixOrDefault(getCustomPrefixComponent())
                        .placeholder("team", AdventureHelper.toComponent(teamForJoin.color.chatColor + teamForJoin.name))
                        .send(player);
            }
            return;
        }

        if (cur != null) {
            cur.players.remove(player);
            cur.getScoreboardTeam().removeEntry(player.getName());

            if (cur.players.isEmpty()) {
                teamsInGame.remove(cur);
                cur.getScoreboardTeam().unregister();
            }
            Debug.info(name + ": player " + player.getName() + " left the team " + cur.getName());
        }

        current.players.add(player);
        current.getScoreboardTeam().addEntry(player.getName());

        Debug.info(name + ": player " + player.getName() + " joined the team " + current.getName());

        Message
                .of(LangKeys.IN_GAME_TEAM_SELECTION_SELECTED)
                .prefixOrDefault(getCustomPrefixComponent())
                .placeholder("team", AdventureHelper.toComponent(teamForJoin.color.chatColor + teamForJoin.name))
                .placeholder("players", current.players.size())
                .placeholder("maxplayers", current.teamInfo.maxPlayers)
                .send(player);

        if (configurationContainer.getOrDefault(ConfigurationContainer.ADD_WOOL_TO_INVENTORY_ON_JOIN, Boolean.class, false)) {
            int colorPosition = MainConfig.getInstance().node("hotbar", "color").getInt(1);
            if (colorPosition >= 0 && colorPosition <= 8) {
                var item = ItemFactory.build(teamForJoin.color.material1_13 + "_WOOL").orElse(ItemFactory.getAir());
                item.setDisplayName(AdventureHelper.toComponent(teamForJoin.color.chatColor + teamForJoin.name));
                player.getPlayerInventory().setItem(colorPosition, item);
            }
        }

        if (configurationContainer.getOrDefault(ConfigurationContainer.COLORED_LEATHER_BY_TEAM_IN_LOBBY, Boolean.class, false)) {
            var chestplate = ItemFactory.build("LEATHER_CHESTPLATE").orElse(ItemFactory.getAir());
            chestplate.setColor(teamForJoin.color.getLeatherColor());
            player.getPlayerInventory().setChestplate(chestplate);
        }

        if (!teamsInGame.contains(current)) {
            teamsInGame.add(current);
        }

        EventManager.fire(new PlayerJoinedTeamEventImpl(this, player, current, cur));
    }

    public void joinRandomTeam(BedWarsPlayer player) {
        Team teamForJoin;
        if (teamsInGame.size() < 2) {
            teamForJoin = getFirstTeamThatIsntInGame();
        } else {
            CurrentTeam current = getTeamWithLowestPlayers();
            if (current.players.size() >= current.getMaxPlayers()) {
                teamForJoin = getFirstTeamThatIsntInGame();
            } else {
                teamForJoin = current.teamInfo;
            }
        }

        if (teamForJoin == null) {
            return;
        }

        internalTeamJoin(player, teamForJoin);
    }

    public Location makeSpectator(BedWarsPlayer gamePlayer, boolean leaveItem) {
        Debug.info(gamePlayer.getName() + " spawning as spectator");
        Player player = gamePlayer.as(Player.class);
        gamePlayer.isSpectator = true;
        gamePlayer.teleport(specSpawn, () -> {
            if (!configurationContainer.getOrDefault(ConfigurationContainer.KEEP_INVENTORY, Boolean.class, false) || leaveItem) {
                gamePlayer.invClean(); // temp fix for inventory issues?
            }
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setGameMode(GameMode.SPECTATOR);

            if (leaveItem) {
                if (MainConfig.getInstance().node("tab", "enabled").getBoolean() && MainConfig.getInstance().node("tab", "hide-spectators").getBoolean()) {
                    players.forEach(p -> p.hidePlayer(player));
                }

                int leavePosition = MainConfig.getInstance().node("hotbar", "leave").getInt(8);
                if (leavePosition >= 0 && leavePosition <= 8) {
                    var wrapper = PlayerMapper.wrapPlayer(player);
                    var leave = MainConfig.getInstance().readDefinedItem("leavegame", "SLIME_BALL");
                    leave.setDisplayName(Message.of(LangKeys.IN_GAME_LOBBY_ITEMS_LEAVE_FROM_GAME_ITEM).asComponent(wrapper));
                    wrapper.getPlayerInventory().setItem(leavePosition, leave);
                }
            }

            if (TabManager.isEnabled()) {
                players.forEach(TabManager.getInstance()::modifyForPlayer);
            }
        });

        return specSpawn;
    }

    @SuppressWarnings("unchecked")
    public void makePlayerFromSpectator(BedWarsPlayer gamePlayer) {
        Debug.info(gamePlayer.getName() + " changing spectator to regular player");
        Player player = gamePlayer.as(Player.class);
        CurrentTeam currentTeam = getPlayerTeam(gamePlayer);

        if (gamePlayer.getGame() == this && currentTeam != null) {
            gamePlayer.isSpectator = false;
            gamePlayer.teleport(currentTeam.getTeamSpawn(), () -> {
                player.setAllowFlight(false);
                player.setFlying(false);
                player.setGameMode(GameMode.SURVIVAL);

                if (MainConfig.getInstance().node("tab", "enabled").getBoolean() && MainConfig.getInstance().node("tab", "hide-spectators").getBoolean()) {
                    players.forEach(p -> p.showPlayer(player));
                }

                if (MainConfig.getInstance().node("respawn", "protection-enabled").getBoolean(true)) {
                    RespawnProtection respawnProtection = addProtectedPlayer(player);
                    respawnProtection.runProtection();
                }

                if (configurationContainer.getOrDefault(ConfigurationContainer.ENABLE_PLAYER_RESPAWN_ITEMS, Boolean.class, false)) {
                    var playerRespawnItems = MainConfig.getInstance().node("player-respawn-items", "items")
                            .childrenList()
                            .stream()
                            .map(ItemFactory::build)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .map(item -> item.as(ItemStack.class))
                            .collect(Collectors.toList());
                    if (!playerRespawnItems.isEmpty()) {
                        MiscUtils.giveItemsToPlayer(playerRespawnItems, player, currentTeam.getColor());
                    } else {
                        Debug.warn("You have wrongly configured player-respawn-items.items!", true);
                    }
                }
                MiscUtils.giveItemsToPlayer(gamePlayer.getPermaItemsPurchased(), player, currentTeam.getColor());

                if (configurationContainer.getOrDefault(ConfigurationContainer.KEEP_ARMOR, Boolean.class, false)) {
                    final var armorContents = gamePlayer.getGameArmorContents();
                    if (armorContents != null) {
                        player.getInventory().setArmorContents(armorContents);
                    }
                }

                if (TabManager.isEnabled()) {
                    players.forEach(TabManager.getInstance()::modifyForPlayer);
                }

                EventManager.fire(new PlayerRespawnedEventImpl(this, gamePlayer));
            });
        }
    }

    public void setBossbarProgress(int count, int max) {
        double progress = (double) count / (double) max;
        statusbar.setProgress((float) progress);
        if (statusbar instanceof XPBar) {
            XPBar xpbar = (XPBar) statusbar;
            xpbar.setSeconds(count);
        }
    }

    @SuppressWarnings("unchecked")
    public void run() {
        // Phase 1: Check if game is running
        if (status == GameStatus.DISABLED) { // Game is not running, why cycle is still running?
            cancelTask();
            return;
        }
        var statusE = new GameChangedStatusEventImpl(this);
        // Phase 2: If this is first tick, prepare waiting lobby
        if (countdown == -1 && status == GameStatus.WAITING) {
            Debug.info(name + ": preparing lobby");
            previousCountdown = countdown = pauseCountdown;
            previousStatus = GameStatus.WAITING;
            var title = AdventureHelper.toLegacy(Message.of(LangKeys.IN_GAME_BOSSBAR_WAITING).asComponent());
            statusbar.setProgress(0);
            statusbar.setVisible(configurationContainer.getOrDefault(ConfigurationContainer.LOBBY_BOSSBAR, Boolean.class, false));
            for (BedWarsPlayer p : players) {
                statusbar.addPlayer(p);
            }
            if (statusbar instanceof BossBar) {
                var bossbar = (BossBar) statusbar;
                bossbar.setMessage(title);
                bossbar.setColor(lobbyBossBarColor != null ? lobbyBossBarColor
                        : BarColor.valueOf(MainConfig.getInstance().node("bossbar", "lobby", "color").getString()));
                bossbar
                        .setStyle(BarStyle.valueOf(MainConfig.getInstance().node("bossbar", "lobby", "style").getString()));
            }
            if (teamSelectorInventory == null) {
                teamSelectorInventory = new TeamSelectorInventory(this);
            }

            if (experimentalBoard == null && MainConfig.getInstance().node("experimental", "new-scoreboard-system", "enabled").getBoolean(false)) {
                experimentalBoard = new ScreamingScoreboard(this);
            }
            updateSigns();
            Debug.info(name + ": lobby prepared");
        }

        // Phase 3: Prepare information about next tick for tick event and update
        // bossbar with scoreboard
        int nextCountdown = countdown;
        GameStatus nextStatus = status;

        if (status == GameStatus.WAITING) {
            // Game start item
            if (gameStartItem) {
                if (players.size() >= getMinPlayers()) {
                    for (BedWarsPlayer player : players) {
                        if (getPlayerTeam(player) == null) {
                            joinRandomTeam(player);
                        }
                    }
                }
                if (players.size() > 1) {
                    countdown = 0;
                    gameStartItem = false;
                }
            }

            if (players.size() >= getMinPlayers()
                    && (configurationContainer.getOrDefault(ConfigurationContainer.JOIN_RANDOM_TEAM_AFTER_LOBBY, Boolean.class, false) || teamsInGame.size() > 1)) {
                if (countdown == 0) {
                    nextCountdown = gameTime;
                    nextStatus = GameStatus.RUNNING;
                } else {
                    nextCountdown--;

                    if (countdown <= 10 && countdown >= 1 && countdown != previousCountdown) {

                        for (BedWarsPlayer player : players) {
                            TitleUtils.send(player, ChatColor.YELLOW + Integer.toString(countdown), "");
                            Sounds.playSound(player.as(Player.class), player.as(Player.class).getLocation(),
                                    MainConfig.getInstance().node("sounds", "countdown").getString(), Sounds.UI_BUTTON_CLICK,
                                    1, 1);
                        }
                    }
                }
            } else {
                nextCountdown = countdown = pauseCountdown;
            }
            setBossbarProgress(countdown, pauseCountdown);
            updateLobbyScoreboard();
        } else if (status == GameStatus.RUNNING) {
            if (countdown == 0) {
                nextCountdown = postGameWaiting;
                nextStatus = GameStatus.GAME_END_CELEBRATING;
            } else {
                nextCountdown--;
            }
            setBossbarProgress(countdown, gameTime);
            updateScoreboardTimer();
        } else if (status == GameStatus.GAME_END_CELEBRATING) {
            if (countdown == 0) {
                nextStatus = GameStatus.REBUILDING;
                nextCountdown = 0;
            } else {
                nextCountdown--;
            }
            setBossbarProgress(countdown, postGameWaiting);
        }

        // Phase 4: Call Tick Event
        var tick = new GameTickEventImpl(this, previousCountdown, previousStatus, countdown, status,
                nextCountdown, nextStatus, nextCountdown, nextStatus);
        EventManager.fire(tick);
        Debug.info(name + ": tick passed: " + tick.getPreviousCountdown() + "," + tick.getCountdown() + "," + tick.getNextCountdown() + " (" + tick.getPreviousStatus() + "," + tick.getStatus() + "," + tick.getNextStatus() + ")");

        // Phase 5: Update Previous information
        previousCountdown = countdown;
        previousStatus = status;

        // Phase 6: Process tick
        // Phase 6.1: If status changed
        if (status != tick.getNextStatus()) {
            // Phase 6.1.1: Prepare game if next status is RUNNING
            if (tick.getNextStatus() == GameStatus.RUNNING) {
                Debug.info(name + ": preparing game");
                preparing = true;
                var startE = new GameStartEventImpl(this);
                EventManager.fire(startE);
                EventManager.fire(statusE);

                if (startE.isCancelled()) {
                    tick.setNextCountdown(pauseCountdown);
                    tick.setNextStatus(GameStatus.WAITING);
                    preparing = false;
                } else {

                    if (configurationContainer.getOrDefault(ConfigurationContainer.JOIN_RANDOM_TEAM_AFTER_LOBBY, Boolean.class, false)) {
                        for (BedWarsPlayer player : players) {
                            if (getPlayerTeam(player) == null) {
                                joinRandomTeam(player);
                            }
                        }
                    }

                    statusbar.setProgress(0);
                    statusbar.setVisible(configurationContainer.getOrDefault(ConfigurationContainer.GAME_BOSSBAR, Boolean.class, false));
                    if (statusbar instanceof BossBar) {
                        var bossbar = (BossBar) statusbar;
                        bossbar.setMessage(AdventureHelper.toLegacy(Message.of(LangKeys.IN_GAME_BOSSBAR_RUNNING).asComponent()));
                        bossbar.setColor(gameBossBarColor != null ? gameBossBarColor
                                : BarColor.valueOf(MainConfig.getInstance().node("bossbar", "game", "color").getString()));
                        bossbar.setStyle(
                                BarStyle.valueOf(MainConfig.getInstance().node("bossbar", "game", "style").getString()));
                    }
                    if (teamSelectorInventory != null)
                        teamSelectorInventory.destroy();
                    teamSelectorInventory = null;

                    if (gameScoreboard.getObjective("lobby") != null) {
                        gameScoreboard.getObjective("lobby").unregister();
                    }
                    gameScoreboard.clearSlot(DisplaySlot.SIDEBAR);
                    Bukkit.getScheduler().runTaskLater(Main.getInstance().getPluginDescription().as(JavaPlugin.class), this::updateSigns, 3L);
                    for (GameStore store : gameStore) {
                        LivingEntity villager = store.spawn();
                        if (villager != null) {
                            Main.registerGameEntity(villager, this);
                            EntityUtils.disableEntityAI(villager);
                            villager.getLocation().getWorld().getNearbyEntities(villager.getLocation(), 1, 1, 1).forEach(entity -> {
                                if (entity.getType() == villager.getType() && entity.getLocation().getBlock().equals(villager.getLocation().getBlock()) && !villager.equals(entity)) {
                                    entity.remove();
                                }
                            });
                        }
                    }

                    for (ItemSpawner spawner : spawners) {
                        UpgradeStorage storage = UpgradeRegistry.getUpgrade("spawner");
                        if (storage != null) {
                            storage.addUpgrade(this, spawner);
                        }
                    }

                    if (configurationContainer.getOrDefault(ConfigurationContainer.SPAWNER_HOLOGRAMS, Boolean.class, false)) {
                        for (ItemSpawner spawner : spawners) {
                            CurrentTeam spawnerTeam = getCurrentTeamFromTeam(spawner.getTeam().orElse(null));
                            if (configurationContainer.getOrDefault(ConfigurationContainer.STOP_TEAM_SPAWNERS_ON_DIE, Boolean.class, false) && spawner.getTeam().isPresent() && spawnerTeam == null) {
                                continue; // team of this spawner is not available. Fix #147
                            }

                            spawner.spawnHologram(getConnectedPlayers(), configurationContainer.getOrDefault(ConfigurationContainer.SPAWNER_COUNTDOWN_HOLOGRAM, Boolean.class, false));
                        }
                    }

                    var title = Message
                                    .of(LangKeys.IN_GAME_GAME_START_TITLE)
                                    .join(LangKeys.IN_GAME_GAME_START_SUBTITLE)
                                    .placeholder("arena", this.name)
                                    .times(TitleUtils.defaultTimes());
                    for (BedWarsPlayer player : this.players) {
                        Debug.info(name + ": moving " + player.getName() + " into game");
                        CurrentTeam team = getPlayerTeam(player);
                        player.as(Player.class).getInventory().clear();
                        // Player still had armor on legacy versions
                        player.getPlayerInventory().setHelmet(null);
                        player.getPlayerInventory().setChestplate(null);
                        player.getPlayerInventory().setLeggings(null);
                        player.getPlayerInventory().setBoots(null);
                        player.showTitle(title);
                        if (team == null) {
                            makeSpectator(player, true);
                        } else {
                            player.teleport(team.teamInfo.spawn, () -> {
                                player.as(Player.class).setGameMode(GameMode.SURVIVAL);
                                if (configurationContainer.getOrDefault(ConfigurationContainer.ENABLE_GAME_START_ITEMS, Boolean.class, false)) {
                                    List<ItemStack> givedGameStartItems = MainConfig.getInstance().node("game-start-items", "items")
                                            .childrenList()
                                            .stream()
                                            .map(ItemFactory::build)
                                            .filter(Optional::isPresent)
                                            .map(Optional::get)
                                            .map(item -> item.as(ItemStack.class))
                                            .collect(Collectors.toList());
                                    if (!givedGameStartItems.isEmpty()) {
                                        MiscUtils.giveItemsToPlayer(givedGameStartItems, player.as(Player.class), team.getColor());
                                    } else {
                                        Debug.warn("You have wrongly configured game-start-items.items!", true);
                                    }
                                }
                                SpawnEffects.spawnEffect(this, player, "game-effects.start");
                            });
                        }
                        Sounds.playSound(player.as(Player.class), player.as(Player.class).getLocation(),
                                MainConfig.getInstance().node("sounds", "game_start").getString(),
                                Sounds.ENTITY_PLAYER_LEVELUP, 1, 1);
                    }

                    if (configurationContainer.getOrDefault(ConfigurationContainer.REMOVE_UNUSED_TARGET_BLOCKS, Boolean.class, false)) {
                        for (Team team : teams) {
                            CurrentTeam ct = null;
                            for (CurrentTeam curt : teamsInGame) {
                                if (curt.teamInfo == team) {
                                    ct = curt;
                                    break;
                                }
                            }
                            if (ct == null) {
                                Location loc = team.bed;
                                Block block = team.bed.getBlock();
                                if (region.isBedBlock(block.getState())) {
                                    region.putOriginalBlock(block.getLocation(), block.getState());
                                    Block neighbor = region.getBedNeighbor(block);
                                    region.putOriginalBlock(neighbor.getLocation(), neighbor.getState());
                                    neighbor.setType(Material.AIR, false);
                                    block.setType(Material.AIR);
                                } else {
                                    region.putOriginalBlock(loc, block.getState());
                                    block.setType(Material.AIR);
                                }
                            }
                        }
                    }

                    for (CurrentTeam team : teamsInGame) {
                        Block block = team.getTargetBlock().getBlock();
                        if (block != null && "RESPAWN_ANCHOR".equals(block.getType().name())) { // don't break the game for older servers
                            new BukkitRunnable() {
                                public void run() {
                                    RespawnAnchor anchor = (RespawnAnchor) block.getBlockData();
                                    anchor.setCharges(0);
                                    block.setBlockData(anchor);
                                    if (configurationContainer.getOrDefault(ConfigurationContainer.ANCHOR_AUTO_FILL, Boolean.class, false)) {
                                        new BukkitRunnable() {
                                            public void run() {
                                                anchor.setCharges(anchor.getCharges() + 1);
                                                Sounds.playSound(team.getTargetBlock(), MainConfig.getInstance().node("target-block", "respawn-anchor", "sound", "charge").getString(), Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 1, 1);
                                                block.setBlockData(anchor);
                                                if (anchor.getCharges() >= anchor.getMaximumCharges()) {
                                                    updateScoreboard();
                                                    this.cancel();
                                                }
                                            }
                                        }.runTaskTimer(Main.getInstance().getPluginDescription().as(JavaPlugin.class), 50L, 10L);
                                    }
                                }
                            }.runTask(Main.getInstance().getPluginDescription().as(JavaPlugin.class));
                        }
                    }

                    if (configurationContainer.getOrDefault(ConfigurationContainer.HOLOGRAMS_ABOVE_BEDS, Boolean.class, false)) {
                        for (CurrentTeam team : teamsInGame) {
                            Block bed = team.teamInfo.bed.getBlock();
                            Location loc = team.teamInfo.bed.clone().add(0.5, 1.5, 0.5);
                            boolean isBlockTypeBed = region.isBedBlock(bed.getState());
                            boolean isAnchor = "RESPAWN_ANCHOR".equals(bed.getType().name());
                            boolean isCake = bed.getType().name().contains("CAKE");
                            var enemies = getConnectedPlayers() // getConnectedPlayers is copy
                                    .stream()
                                    .filter(player -> !team.getConnectedPlayers().contains(player))
                                    .map(PlayerMapper::wrapPlayer)
                                    .collect(Collectors.toList());
                            var holo = HologramManager
                                    .hologram(LocationMapper.wrapLocation(loc))
                                    .firstLine(
                                            Message
                                                    .of(isBlockTypeBed ? LangKeys.IN_GAME_TARGET_BLOCK_HOLOGRAM_DESTROY_BED : (isAnchor ? LangKeys.IN_GAME_TARGET_BLOCK_HOLOGRAM_DESTROY_ANCHOR : (isCake ? LangKeys.IN_GAME_TARGET_BLOCK_HOLOGRAM_DESTROY_CAKE : LangKeys.IN_GAME_TARGET_BLOCK_HOLOGRAM_DESTROY_ANY)))
                                            .placeholder("teamcolor", AdventureHelper.toComponent(team.teamInfo.color.chatColor.toString()))
                                            .asTextEntry(null)
                                    );
                            enemies.forEach(holo::addViewer);
                            holo.show();
                            team.setBedHolo(holo);
                            var protectHolo = HologramManager
                                    .hologram(LocationMapper.wrapLocation(loc))
                                    .firstLine(
                                            Message
                                                    .of(isBlockTypeBed ? LangKeys.IN_GAME_TARGET_BLOCK_HOLOGRAM_PROTECT_BED : (isAnchor ? LangKeys.IN_GAME_TARGET_BLOCK_HOLOGRAM_PROTECT_ANCHOR : (isCake ? LangKeys.IN_GAME_TARGET_BLOCK_HOLOGRAM_PROTECT_CAKE : LangKeys.IN_GAME_TARGET_BLOCK_HOLOGRAM_PROTECT_ANY)))
                                                    .placeholder("teamcolor", AdventureHelper.toComponent(team.teamInfo.color.chatColor.toString()))
                                                    .asTextEntry(null)
                                    );
                            team.getConnectedPlayers().stream().map(PlayerMapper::wrapPlayer).forEach(protectHolo::addViewer);
                            protectHolo.show();
                            team.setProtectHolo(protectHolo);
                        }
                    }

                    // Check target blocks existence
                    for (CurrentTeam team : teamsInGame) {
                        Location targetLocation = team.getTargetBlock();
                        if (targetLocation.getBlock().getType() == Material.AIR) {
                            ItemStack stack = team.teamInfo.color.getWool();
                            Block placedBlock = targetLocation.getBlock();
                            placedBlock.setType(stack.getType());
                            if (!Main.isLegacy()) {
                                try {
                                    // The method is no longer in API, but in legacy versions exists
                                    Block.class.getMethod("setData", byte.class).invoke(placedBlock, (byte) stack.getDurability());
                                } catch (Exception e) {
                                }
                            }
                        }
                    }

                    if (Main.getVersionNumber() >= 115 && !MainConfig.getInstance().node("allow-fake-death").getBoolean()) {
                        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
                    }
                    preparing = false;

                    var startedEvent = new GameStartedEventImpl(this);
                    EventManager.fire(startedEvent);
                    EventManager.fire(statusE);
                    updateScoreboard();
                    Debug.info(name + ": game prepared");
                }
            }
            // Phase 6.2: If status is same as before
        } else {
            // Phase 6.2.1: On game tick (if not interrupted by a change of status)
            if (status == GameStatus.RUNNING && tick.getNextStatus() == GameStatus.RUNNING) {
                int runningTeams = 0;
                for (CurrentTeam t : teamsInGame) {
                    runningTeams += t.isAlive() ? 1 : 0;
                }
                if (runningTeams <= 1) {
                    if (runningTeams == 1) {
                        CurrentTeam winner = null;
                        for (CurrentTeam t : teamsInGame) {
                            if (t.isAlive()) {
                                winner = t;
                                String time = getFormattedTimeLeft(gameTime - countdown);
                                var message = Message
                                        .of(LangKeys.IN_GAME_END_TEAM_WIN)
                                        .prefixOrDefault(getCustomPrefixComponent())
                                        .placeholder("team", AdventureHelper.toComponent(OldTeamColor.fromApiColor(t.getColor()).chatColor + t.getName()))
                                        .placeholder("time", time);
                                boolean madeRecord = processRecord(t, gameTime - countdown);
                                for (BedWarsPlayer player : players) {
                                    player.sendMessage(message);
                                    if (getPlayerTeam(player) == t) {
                                        Message.of(LangKeys.IN_GAME_END_YOU_WON)
                                                .join(LangKeys.IN_GAME_END_TEAM_WIN)
                                                .placeholder("team", AdventureHelper.toComponent(OldTeamColor.fromApiColor(t.getColor()).chatColor + t.getName()))
                                                .placeholder("time", time)
                                                .times(TitleUtils.defaultTimes())
                                                .title(player);
                                        Main.depositPlayer(player.as(Player.class), Main.getVaultWinReward());

                                        SpawnEffects.spawnEffect(this, player, "game-effects.end");

                                        if (PlayerStatisticManager.isEnabled()) {
                                            var statistic = PlayerStatisticManager.getInstance()
                                                    .getStatistic(player);
                                            statistic.addWins(1);
                                            statistic.addScore(MainConfig.getInstance().node("statistics", "scores", "win").getInt(50));

                                            if (madeRecord) {
                                                statistic.addScore(MainConfig.getInstance().node("statistics", "scores", "record").getInt(100));
                                            }

                                            if (StatisticsHolograms.isEnabled()) {
                                                StatisticsHolograms.getInstance().updateHolograms(player);
                                            }

                                            if (MainConfig.getInstance().node("statistics", "show-on-game-end")
                                                    .getBoolean()) {
                                                StatsCommand.sendStats(player, PlayerStatisticManager.getInstance().getStatistic(player));
                                            }

                                        }

                                        if (MainConfig.getInstance().node("rewards", "enabled").getBoolean()) {
                                            final Player pl = player.as(Player.class);
                                            new BukkitRunnable() {

                                                @Override
                                                public void run() {
                                                    if (PlayerStatisticManager.isEnabled()) {
                                                        var statistic = PlayerStatisticManager.getInstance()
                                                                .getStatistic(player);
                                                        Game.this.dispatchRewardCommands("player-win", pl,
                                                                statistic.getScore());
                                                    } else {
                                                        Game.this.dispatchRewardCommands("player-win", pl, 0);
                                                    }
                                                }

                                            }.runTaskLater(Main.getInstance().getPluginDescription().as(JavaPlugin.class), (2 + postGameWaiting) * 20);
                                        }
                                    } else {
                                        Message.of(LangKeys.IN_GAME_END_YOU_LOST)
                                                .join(LangKeys.IN_GAME_END_TEAM_WIN)
                                                .placeholder("team", AdventureHelper.toComponent(OldTeamColor.fromApiColor(t.getColor()).chatColor + t.getName()))
                                                .placeholder("time", time)
                                                .times(TitleUtils.defaultTimes())
                                                .title(player);

                                        if (StatisticsHolograms.isEnabled()) {
                                            StatisticsHolograms.getInstance().updateHolograms(player);
                                        }
                                    }
                                }
                                break;
                            }
                        }

                        var endingEvent = new GameEndingEventImpl(this, winner);
                        EventManager.fire(endingEvent);
                        EventManager.fire(statusE);
                        Debug.info(name + ": game is ending");

                        tick.setNextCountdown(postGameWaiting);
                        tick.setNextStatus(GameStatus.GAME_END_CELEBRATING);
                    } else {
                        tick.setNextStatus(GameStatus.REBUILDING);
                        tick.setNextCountdown(0);
                    }
                } else if (countdown != gameTime /* Prevent spawning resources on game start */) {
                    for (ItemSpawner spawner : spawners) {

                        // TODO: Split spawners to async tasks with synchronized drops

                        CurrentTeam spawnerTeam = getCurrentTeamFromTeam(spawner.getTeam().orElse(null));
                        if (configurationContainer.getOrDefault(ConfigurationContainer.STOP_TEAM_SPAWNERS_ON_DIE, Boolean.class, false) && spawner.getTeam().isPresent() && spawnerTeam == null) {
                            continue; // team of this spawner is not available. Fix #147
                        }

                        ItemSpawnerType type = spawner.type;
                        int cycle = type.getInterval();
                        /*
                         * Calculate resource spawn from elapsedTime, not from remainingTime/countdown
                         */
                        int elapsedTime = gameTime - countdown;

                        if (spawner.getHologram() != null) {
                            if (configurationContainer.getOrDefault(ConfigurationContainer.SPAWNER_HOLOGRAMS, Boolean.class, false)
                                    && configurationContainer.getOrDefault(ConfigurationContainer.SPAWNER_COUNTDOWN_HOLOGRAM, Boolean.class, false)
                                    && !spawner.spawnerIsFullHologram) {
                                if (cycle > 1) {
                                    int modulo = cycle - elapsedTime % cycle;
                                    spawner.getHologram().replaceLine(1, Message.of(LangKeys.IN_GAME_SPAWNER_COUNTDOWN).placeholder("seconds", modulo).asTextEntry(null));
                                } else if (spawner.rerenderHologram) {
                                    spawner.getHologram().replaceLine(1, Message.of(LangKeys.IN_GAME_SPAWNER_EVERY_SECOND).asTextEntry(null));
                                    spawner.rerenderHologram = false;
                                }
                            }
                        }

                        if (spawnerTeam != null) {
                            if (configurationContainer.getOrDefault(ConfigurationContainer.STOP_TEAM_SPAWNERS_ON_DIE, Boolean.class, false) && (spawnerTeam.isDead())) {
                                continue;
                            }
                        }

                        if ((elapsedTime % cycle) == 0) {
                            int calculatedStack = 1;
                            double currentLevel = spawner.getCurrentLevel();
                            calculatedStack = (int) currentLevel;

                            /* Allow half level */
                            if ((currentLevel % 1) != 0) {
                                int a = elapsedTime / cycle;
                                if ((a % 2) == 0) {
                                    calculatedStack++;
                                }
                            }

                            var resourceSpawnEvent = new ResourceSpawnEventImpl(this, spawner, spawner.type,
                                    ItemFactory.build(type.getStack(calculatedStack)).orElseThrow());
                            EventManager.fire(resourceSpawnEvent);

                            if (resourceSpawnEvent.isCancelled()) {
                                continue;
                            }

                            ItemStack resource = resourceSpawnEvent.getResource().as(ItemStack.class);

                            resource.setAmount(spawner.nextMaxSpawn(resource.getAmount()));

                            if (resource.getAmount() > 0) {
                                Location loc = spawner.getLocation().clone().add(0, 0.05, 0);
                                Item item = loc.getWorld().dropItem(loc, resource);
                                double spread = type.getSpread();
                                if (spread != 1.0) {
                                    item.setVelocity(item.getVelocity().multiply(spread));
                                }
                                item.setPickupDelay(0);
                                spawner.add(item);
                            }
                        }
                    }
                }
            }
        }

        // Phase 7: Update status and countdown for next tick
        countdown = tick.getNextCountdown();
        status = tick.getNextStatus();

        // Phase 8: Check if game end celebrating started and remove title on bossbar
        if (status == GameStatus.GAME_END_CELEBRATING && previousStatus != status) {
            if (statusbar instanceof BossBar) {
                BossBar bossbar = (BossBar) statusbar;
                bossbar.setMessage(" ");
            }
        }

        // Phase 9: Check if status is rebuilding and rebuild game
        if (status == GameStatus.REBUILDING) {
            var event = new GameEndEventImpl(this);
            EventManager.fire(event);
            EventManager.fire(statusE);

            var message = Message
                    .of(LangKeys.IN_GAME_END_GAME_END)
                    .prefixOrDefault(getCustomPrefixComponent());
            for (BedWarsPlayer player : List.copyOf(players)) {
                player.sendMessage(message);
                player.changeGame(null);

                if (MainConfig.getInstance().node("rewards", "enabled").getBoolean()) {
                    final Player pl = player.as(Player.class);
                    new BukkitRunnable() {

                        @Override
                        public void run() {
                            if (PlayerStatisticManager.isEnabled()) {
                                var statistic = PlayerStatisticManager.getInstance()
                                        .getStatistic(player);
                                Game.this.dispatchRewardCommands("player-end-game", pl, statistic.getScore());
                            } else {
                                Game.this.dispatchRewardCommands("player-end-game", pl, 0);
                            }
                        }

                    }.runTaskLater(Main.getInstance().getPluginDescription().as(JavaPlugin.class), 40);
                }
            }

            if (status == GameStatus.REBUILDING) { // If status is still rebuilding
                rebuild();
            }

            if (isBungeeEnabled()) {
                preServerRestart = true;

                if (!getConnectedPlayers().isEmpty()) {
                    kickAllPlayers();
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (MainConfig.getInstance().node("bungee", "serverRestart").getBoolean()) {
                            EventManager.fire(new ServerRestartEventImpl());

                            Bukkit.getServer()
                                    .dispatchCommand(Bukkit.getServer().getConsoleSender(), "restart");
                        } else if (MainConfig.getInstance().node("bungee", "serverStop").getBoolean()) {
                            Bukkit.shutdown();
                        }
                    }

                }.runTaskLater(Main.getInstance().getPluginDescription().as(JavaPlugin.class), 30L);
            }
        }
    }

    public void rebuild() {
        if (experimentalBoard != null) {
            experimentalBoard.destroy();
            experimentalBoard = null;
        }
        Debug.info(name + ": rebuilding starts");
        teamsInGame.forEach(currentTeam -> {
            if (currentTeam.hasBedHolo()) {
                currentTeam.getBedHolo().destroy();
                currentTeam.setBedHolo(null);
            }

            if (currentTeam.hasProtectHolo()) {
                currentTeam.getProtectHolo().destroy();
                currentTeam.setProtectHolo(null);
            }
        });
        teamsInGame.clear();
        activeSpecialItems.clear();
        activeDelays.clear();

        EventManager.fire(new PreRebuildingEventImpl(this));

        for (ItemSpawner spawner : spawners) {
            spawner.currentLevel = spawner.startLevel;
            spawner.spawnedItems.clear();
            spawner.destroy();
        }
        for (GameStore store : gameStore) {
            LivingEntity villager = store.kill();
            if (villager != null) {
                Main.unregisterGameEntity(villager);
            }
        }

        region.regen();
        // Remove items
        for (Entity e : this.world.getEntities()) {
            if (ArenaUtils.isInArea(e.getLocation(), pos1, pos2)) {
                if (e instanceof Item) {
                    removeEntityAsync(e);
                }
            }
        }

        // Chest clearing
        for (Map.Entry<Location, ItemStack[]> entry : usedChests.entrySet()) {
            Location location = entry.getKey();
            Chunk chunk = location.getChunk();
            if (!chunk.isLoaded()) {
                chunk.load();
            }
            Block block = location.getBlock();
            ItemStack[] contents = entry.getValue();
            if (block.getState() instanceof InventoryHolder) {
                InventoryHolder chest = (InventoryHolder) block.getState();
                chest.getInventory().setContents(contents);
            }
        }
        usedChests.clear();

        // Clear fake ender chests
        for (Inventory inv : fakeEnderChests.values()) {
            inv.clear();
        }
        fakeEnderChests.clear();

        // Remove remaining entities registered by other plugins
        for (Entity entity : Main.getGameEntities(this)) {
            Chunk chunk = entity.getLocation().getChunk();
            if (!chunk.isLoaded()) {
                chunk.load();
            }
            entity.remove();
            Main.unregisterGameEntity(entity);
        }

        UpgradeRegistry.clearAll(this);

        EventManager.fire(new PostRebuildingEventImpl(this));

        this.status = this.afterRebuild;
        this.countdown = -1;
        updateSigns();
        cancelTask();
        Debug.info(name + ": rebuilding ends");

    }

    public boolean processRecord(CurrentTeam t, int wonTime) {
        var record = RecordSave.getInstance().getRecord(this.getName());
        if (record.map(RecordSave.Record::getTime).orElse(Integer.MAX_VALUE) > wonTime) {
            RecordSave.getInstance().saveRecord(RecordSave.Record.builder()
                    .game(this.getName())
                    .time(wonTime)
                    .team(t.teamInfo.color.chatColor + t.teamInfo.name)
                    .winners(t.players.stream().map(SenderWrapper::getName).collect(Collectors.toList()))
                    .build()
            );
            return true;
        }
        return false;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void runTask() {
        if (task != null) {
            if (Bukkit.getScheduler().isQueued(task.getTaskId())) {
                task.cancel();
            }
            task = null;
        }
        task = (new BukkitRunnable() {

            public void run() {
                Game.this.run();
            }

        }.runTaskTimer(Main.getInstance().getPluginDescription().as(JavaPlugin.class), 0, 20));
    }

    private void cancelTask() {
        if (task != null) {
            if (Bukkit.getScheduler().isQueued(task.getTaskId())) {
                task.cancel();
            }
            task = null;
        }
    }

    public void selectTeam(BedWarsPlayer playerGameProfile, String displayName) {
        if (status == GameStatus.WAITING) {
            displayName = ChatColor.stripColor(displayName);
            playerGameProfile.closeInventory();
            for (Team team : teams) {
                if (displayName.equals(team.name)) {
                    internalTeamJoin(playerGameProfile, team);
                    break;
                }
            }
        }
    }

    public void updateScoreboard() {
        if (!configurationContainer.getOrDefault(ConfigurationContainer.GAME_SCOREBOARD, Boolean.class, false)) {
            return;
        }

        if (MainConfig.getInstance().node("experimental", "new-scoreboard-system", "enabled").getBoolean(false)) {
            return;
        }

        Objective obj = this.gameScoreboard.getObjective("display");
        if (obj == null) {
            obj = this.gameScoreboard.registerNewObjective("display", "dummy");
        }

        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(this.formatScoreboardTitle());

        for (CurrentTeam team : teamsInGame) {
            this.gameScoreboard.resetScores(this.formatScoreboardTeam(team, false, false));
            this.gameScoreboard.resetScores(this.formatScoreboardTeam(team, false, true));
            this.gameScoreboard.resetScores(this.formatScoreboardTeam(team, true, false));

            Score score = obj.getScore(this.formatScoreboardTeam(team, !team.isBed, team.isBed && "RESPAWN_ANCHOR".equals(team.teamInfo.bed.getBlock().getType().name()) && Player116ListenerUtils.isAnchorEmpty(team.teamInfo.bed.getBlock())));
            score.setScore(team.players.size());
        }

        for (BedWarsPlayer player : players) {
            player.as(Player.class).setScoreboard(gameScoreboard);
        }
    }

    private String formatScoreboardTeam(CurrentTeam team, boolean destroy, boolean empty) {
        if (team == null) {
            return "";
        }

        return MainConfig.getInstance().node("scoreboard", "teamTitle").getString()
                .replace("%color%", team.teamInfo.color.chatColor.toString()).replace("%team%", team.teamInfo.name)
                .replace("%bed%", destroy ? bedLostString() : (empty ? anchorEmptyString() : bedExistString()));
    }

    private void updateScoreboardTimer() {
        if (this.status != GameStatus.RUNNING || !configurationContainer.getOrDefault(ConfigurationContainer.GAME_SCOREBOARD, Boolean.class, false)) {
            return;
        }

        if (MainConfig.getInstance().node("experimental", "new-scoreboard-system", "enabled").getBoolean(false)) {
            return;
        }

        Objective obj = this.gameScoreboard.getObjective("display");
        if (obj == null) {
            obj = this.gameScoreboard.registerNewObjective("display", "dummy");
        }

        obj.setDisplayName(this.formatScoreboardTitle());

        for (BedWarsPlayer player : players) {
            player.as(Player.class).setScoreboard(gameScoreboard);
        }
    }

    public String formatScoreboardTitle() {
        return Objects.requireNonNull(MainConfig.getInstance().node("scoreboard", "title").getString())
                .replace("%game%", this.name)
                .replace("%time%", this.getFormattedTimeLeft());
    }

    public String getFormattedTimeLeft() {
        return getFormattedTimeLeft(this.countdown);
    }

    public String getFormattedTimeLeft(int countdown) {
        int min;
        int sec;
        String minStr;
        String secStr;

        min = (int) Math.floor(countdown / 60);
        sec = countdown % 60;

        minStr = (min < 10) ? "0" + min : String.valueOf(min);
        secStr = (sec < 10) ? "0" + sec : String.valueOf(sec);

        return minStr + ":" + secStr;
    }

    public void updateSigns() {
        final var config = MainConfig.getInstance();
        final var gameSigns = BedWarsSignService.getInstance().getSignsForKey(this.name);

        if (gameSigns.isEmpty()) {
            return;
        }

        String[] statusLine;
        String[] playersLine;
        MaterialHolder blockBehindMaterial;
        switch (status) {
            case REBUILDING:
                statusLine = LangKeys.SIGN_STATUS_REBUILDING_STATUS;
                playersLine = LangKeys.SIGN_STATUS_REBUILDING_PLAYERS;
                blockBehindMaterial = MiscUtils.getMaterialFromString(config.node("sign", "block-behind", "rebuilding").getString(), "BROWN_STAINED_GLASS");
                break;
            case RUNNING:
            case GAME_END_CELEBRATING:
                statusLine = LangKeys.SIGN_STATUS_RUNNING_STATUS;
                playersLine = LangKeys.SIGN_STATUS_RUNNING_PLAYERS;
                blockBehindMaterial = MiscUtils.getMaterialFromString(config.node("sign", "block-behind", "in-game").getString(), "GREEN_STAINED_GLASS");
                break;
            case WAITING:
                statusLine = LangKeys.SIGN_STATUS_WAITING_STATUS;
                playersLine = LangKeys.SIGN_STATUS_WAITING_PLAYERS;
                blockBehindMaterial = MiscUtils.getMaterialFromString(config.node("sign", "block-behind", "waiting").getString(), "ORANGE_STAINED_GLASS");
                break;
            case DISABLED:
            default:
                statusLine = LangKeys.SIGN_STATUS_DISABLED_STATUS;
                playersLine = LangKeys.SIGN_STATUS_DISABLED_PLAYERS;
                blockBehindMaterial = MiscUtils.getMaterialFromString(config.node("sign", "block-behind", "game-disabled").getString(), "RED_STAINED_GLASS");
                break;
        }

        var statusMessage = Message.of(statusLine);
        var playerMessage = Message.of(playersLine)
                .placeholder("players", players.size())
                .placeholder("maxplayers", calculatedMaxPlayers);

        final var texts = MainConfig.getInstance().node("sign", "lines").childrenList().stream()
                .map(ConfigurationNode::getString)
                .map(s -> Objects.requireNonNullElse(s, "")
                        .replaceAll("%arena%", this.getName())
                        .replaceAll("%status%", AdventureHelper.toLegacy(statusMessage.asComponent()))
                        .replaceAll("%players%", AdventureHelper.toLegacy(playerMessage.asComponent())))
                .collect(Collectors.toList());

        final var finalBlockBehindMaterial = blockBehindMaterial;
        for (var signBlock : gameSigns) {
            signBlock.getLocation().asOptional(LocationHolder.class)
                    .flatMap(locationHolder -> locationHolder.asOptional(Location.class))
                    .ifPresent(location -> {
                        if (location.getChunk().isLoaded()) {
                            BlockState blockState = location.getBlock().getState();
                            if (blockState instanceof Sign) {
                                Sign sign = (Sign) blockState;
                                for (int i = 0; i < texts.size() && i < 4; i++) {
                                    sign.setLine(i, texts.get(i));
                                }
                                sign.update();
                            }

                            if (config.node("sign", "block-behind", "enabled").getBoolean(false)) {
                                final Optional<Block> optionalBlock = SignUtils.getBlockBehindSign(signBlock);
                                if (optionalBlock.isPresent()) {
                                    final Block glassBlock = optionalBlock.get();
                                    glassBlock.setType(finalBlockBehindMaterial.as(Material.class));
                                    if (Main.isLegacy()) {
                                        try {
                                            // The method is no longer in API, but in legacy versions exists
                                            Block.class.getMethod("setData", byte.class).invoke(glassBlock, (byte) finalBlockBehindMaterial.getDurability());
                                        } catch (Exception e) {
                                        }
                                    }
                                }
                            }
                        }
                    });
        }
    }

    private void updateLobbyScoreboard() {
        if (status != GameStatus.WAITING || !configurationContainer.getOrDefault(ConfigurationContainer.LOBBY_SCOREBOARD, Boolean.class, false)) {
            return;
        }

        if (MainConfig.getInstance().node("experimental", "new-scoreboard-system", "enabled").getBoolean(false)) {
            return;
        }

        Objective obj = gameScoreboard.getObjective("lobby");
        if (obj == null) {
            obj = gameScoreboard.registerNewObjective("lobby", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName(this.formatLobbyScoreboardString(
                    MainConfig.getInstance().node("lobby-scoreboard", "title").getString("§eBEDWARS")));
        }

        var rows = MainConfig.getInstance().node("lobby-scoreboard", "content").childrenList()
                .stream()
                .map(ConfigurationNode::getString)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (rows.isEmpty()) {
            return;
        }

        rows = resizeAndMakeUnique(rows);

        //reset only scores that are changed instead of resetting all entries every tick
        //helps resolve scoreboard flickering
        int i = 15;
        for (String row : rows) {
            try {
                final String element = formatLobbyScoreboardString(row);
                final Score score = obj.getScore(element);

                if (score.getScore() != i) {
                    score.setScore(i);
                    for (String entry : gameScoreboard.getEntries()) {
                        if (obj.getScore(entry).getScore() == i && !entry.equalsIgnoreCase(element)) {
                            gameScoreboard.resetScores(entry);
                        }
                    }
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                e.printStackTrace();
            }
            i--;
        }


        players.forEach(player -> player.as(Player.class).setScoreboard(gameScoreboard));
    }

    public List<String> resizeAndMakeUnique(List<String> lines) {
        final List<String> content = new ArrayList<>();

        lines.forEach(line -> {
            String copy = line;
            if (copy == null) {
                copy = " ";
            }

            //avoid exceptions returned by getScore()
            if (copy.length() > 40) {
                copy = copy.substring(40);
            }


            final StringBuilder builder = new StringBuilder(copy);
            while (content.contains(builder.toString())) {
                builder.append(" ");
            }
            content.add(builder.toString());
        });

        if (content.size() > 15) {
            return content.subList(0, 15);
        }
        return content;
    }

    public String formatLobbyScoreboardString(String str) {
        String finalStr = str;

        finalStr = finalStr.replace("%arena%", name);
        finalStr = finalStr.replace("%players%", String.valueOf(players.size()));
        finalStr = finalStr.replace("%maxplayers%", String.valueOf(calculatedMaxPlayers));

        return finalStr;
    }

    @Override
    public void selectPlayerTeam(Player player, org.screamingsandals.bedwars.api.team.Team team) {
        if (!PlayerManager.getInstance().isPlayerInGame(player.getUniqueId())) {
            return;
        }
        BedWarsPlayer profile = PlayerManager.getInstance().getPlayer(player.getUniqueId()).orElseThrow();
        if (profile.getGame() != this) {
            return;
        }

        selectTeam(profile, team.getName());
    }

    @Override
    public World getGameWorld() {
        return world;
    }

    @Override
    public Location getSpectatorSpawn() {
        return specSpawn;
    }

    @Override
    public int countConnectedPlayers() {
        return players.size();
    }

    @Override
    public List<Player> getConnectedPlayers() {
        List<Player> playerList = new ArrayList<>();
        for (BedWarsPlayer player : players) {
            playerList.add(player.as(Player.class));
        }
        return playerList;
    }

    @Override
    public List<org.screamingsandals.bedwars.api.team.Team> getAvailableTeams() {
        return new ArrayList<>(teams);
    }

    @Override
    public List<RunningTeam> getRunningTeams() {
        return new ArrayList<>(teamsInGame);
    }

    @Override
    public RunningTeam getTeamOfPlayer(Player player) {
        if (!PlayerManager.getInstance().isPlayerInGame(player.getUniqueId())) {
            return null;
        }
        return getPlayerTeam(PlayerManager.getInstance().getPlayer(player.getUniqueId()).orElseThrow());
    }

    @Override
    public boolean isLocationInArena(Location location) {
        return ArenaUtils.isInArea(location, pos1, pos2);
    }

    @Override
    public World getLobbyWorld() {
        if (lobbySpawn == null) return null;
        return lobbySpawn.getWorld();
    }

    @Override
    public int getLobbyCountdown() {
        return pauseCountdown;
    }

    @Override
    public CurrentTeam getTeamOfChest(Location location) {
        for (CurrentTeam team : teamsInGame) {
            if (team.isTeamChestRegistered(location)) {
                return team;
            }
        }
        return null;
    }

    @Override
    public CurrentTeam getTeamOfChest(Block block) {
        for (CurrentTeam team : teamsInGame) {
            if (team.isTeamChestRegistered(block)) {
                return team;
            }
        }
        return null;
    }

    public void addChestForFutureClear(Location loc, Inventory inventory) {
        if (!usedChests.containsKey(loc)) {
            ItemStack[] contents = inventory.getContents();
            ItemStack[] clone = new ItemStack[contents.length];
            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (stack != null)
                    clone[i] = stack.clone();
            }
            usedChests.put(loc, clone);
        }
    }

    @Override
    public int getMaxPlayers() {
        return calculatedMaxPlayers;
    }

    @Override
    public int countGameStores() {
        return gameStore.size();
    }

    @Override
    public int countAvailableTeams() {
        return teams.size();
    }

    @Override
    public int countRunningTeams() {
        return teamsInGame.size();
    }

    @Override
    public boolean isPlayerInAnyTeam(Player player) {
        return getTeamOfPlayer(player) != null;
    }

    @Override
    public boolean isPlayerInTeam(Player player, RunningTeam team) {
        return getTeamOfPlayer(player) == team;
    }

    @Override
    public int countTeamChests() {
        int total = 0;
        for (CurrentTeam team : teamsInGame) {
            total += team.countTeamChests();
        }
        return total;
    }

    @Override
    public int countTeamChests(RunningTeam team) {
        return team.countTeamChests();
    }

    @Override
    public List<SpecialItem> getActivedSpecialItems() {
        return new ArrayList<>(activeSpecialItems);
    }

    @Override
    public List<SpecialItem> getActivedSpecialItems(Class<? extends SpecialItem> type) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (type.isInstance(item)) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public List<SpecialItem> getActivedSpecialItemsOfTeam(org.screamingsandals.bedwars.api.team.Team team) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (item.getTeam() == team) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public List<SpecialItem> getActivedSpecialItemsOfTeam(org.screamingsandals.bedwars.api.team.Team team,
                                                          Class<? extends SpecialItem> type) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (type.isInstance(item) && item.getTeam() == team) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public SpecialItem getFirstActivedSpecialItemOfTeam(org.screamingsandals.bedwars.api.team.Team team) {
        for (SpecialItem item : activeSpecialItems) {
            if (item.getTeam() == team) {
                return item;
            }
        }
        return null;
    }

    @Override
    public SpecialItem getFirstActivedSpecialItemOfTeam(org.screamingsandals.bedwars.api.team.Team team,
                                                        Class<? extends SpecialItem> type) {
        for (SpecialItem item : activeSpecialItems) {
            if (item.getTeam() == team && type.isInstance(item)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public List<SpecialItem> getActivedSpecialItemsOfPlayer(Player player) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (item.getPlayer() == player) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public List<SpecialItem> getActivedSpecialItemsOfPlayer(Player player, Class<? extends SpecialItem> type) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (item.getPlayer() == player && type.isInstance(item)) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public SpecialItem getFirstActivedSpecialItemOfPlayer(Player player) {
        for (SpecialItem item : activeSpecialItems) {
            if (item.getPlayer() == player) {
                return item;
            }
        }
        return null;
    }

    @Override
    public SpecialItem getFirstActivedSpecialItemOfPlayer(Player player, Class<? extends SpecialItem> type) {
        for (SpecialItem item : activeSpecialItems) {
            if (item.getPlayer() == player && type.isInstance(item)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public void registerSpecialItem(SpecialItem item) {
        if (!activeSpecialItems.contains(item)) {
            activeSpecialItems.add(item);
        }
    }

    @Override
    public void unregisterSpecialItem(SpecialItem item) {
        activeSpecialItems.remove(item);
    }

    @Override
    public boolean isRegisteredSpecialItem(SpecialItem item) {
        return activeSpecialItems.contains(item);
    }

    @Override
    public List<DelayFactory> getActiveDelays() {
        return new ArrayList<>(activeDelays);
    }

    @Override
    public List<DelayFactory> getActiveDelaysOfPlayer(Player player) {
        List<DelayFactory> delays = new ArrayList<>();
        for (DelayFactory delay : activeDelays) {
            if (delay.getPlayer() == player) {
                delays.add(delay);
            }
        }
        return delays;
    }

    @Override
    public DelayFactory getActiveDelay(Player player, Class<? extends SpecialItem> specialItem) {
        for (DelayFactory delayFactory : getActiveDelaysOfPlayer(player)) {
            if (specialItem.isInstance(delayFactory.getSpecialItem())) {
                return delayFactory;
            }
        }
        return null;
    }

    @Override
    public void registerDelay(DelayFactory delayFactory) {
        if (!activeDelays.contains(delayFactory)) {
            activeDelays.add(delayFactory);
        }
    }

    @Override
    public void unregisterDelay(DelayFactory delayFactory) {
        activeDelays.remove(delayFactory);
    }

    @Override
    public boolean isDelayActive(Player player, Class<? extends SpecialItem> specialItem) {
        for (DelayFactory delayFactory : getActiveDelaysOfPlayer(player)) {
            if (specialItem.isInstance(delayFactory.getSpecialItem())) {
                return delayFactory.getDelayActive();
            }
        }
        return false;
    }

    @Override
    public ArenaTime getArenaTime() {
        return arenaTime;
    }

    public void setArenaTime(ArenaTime arenaTime) {
        this.arenaTime = arenaTime;
    }

    @Override
    public WeatherType getArenaWeather() {
        return arenaWeather;
    }

    public void setArenaWeather(WeatherType arenaWeather) {
        this.arenaWeather = arenaWeather;
    }

    @Override
    public BarColor getLobbyBossBarColor() {
        return this.lobbyBossBarColor;
    }

    public void setLobbyBossBarColor(BarColor color) {
        this.lobbyBossBarColor = color;
    }

    @Override
    public BarColor getGameBossBarColor() {
        return this.gameBossBarColor;
    }

    public void setGameBossBarColor(BarColor color) {
        this.gameBossBarColor = color;
    }

    @Override
    public List<org.screamingsandals.bedwars.api.game.ItemSpawner> getItemSpawners() {
        return new ArrayList<>(spawners);
    }

    public void dispatchRewardCommands(String type, Player player, int score) {
        if (!MainConfig.getInstance().node("rewards", "enabled").getBoolean()) {
            return;
        }

        MainConfig.getInstance().node("rewards", type).childrenList()
                .stream()
                .map(ConfigurationNode::getString)
                .filter(Objects::nonNull)
                .map(s -> s
                        .replaceAll("\\{player}", player.getName())
                        .replaceAll("\\{score}", Integer.toString(score))
                )
                .map(s -> s.startsWith("/") ? s.substring(1) : s)
                .forEach(s -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s));
    }

    @Override
    public void selectPlayerRandomTeam(Player player) {
        joinRandomTeam(PlayerManager.getInstance().getPlayerOrCreate(PlayerMapper.wrapPlayer(player)));
    }

    @Override
    public StatusBar getStatusBar() {
        return statusbar;
    }

    public void kickAllPlayers() {
        for (Player player : getConnectedPlayers()) {
            leaveFromGame(player);
        }
    }

    @Override
    public boolean getBungeeEnabled() {
        return MainConfig.getInstance().node("bungee", "enabled").getBoolean();
    }

    @Override
    public boolean isEntityShop(Entity entity) {
        for (GameStore store : gameStore) {
            if (store.getEntity().equals(entity)) {
                return true;
            }
        }
        return false;
    }

    public RespawnProtection addProtectedPlayer(Player player) {
        int time = MainConfig.getInstance().node("respawn", "protection-time").getInt(10);

        RespawnProtection respawnProtection = new RespawnProtection(this, player, time);
        respawnProtectionMap.put(player, respawnProtection);

        return respawnProtection;
    }

    public void removeProtectedPlayer(Player player) {
        RespawnProtection respawnProtection = respawnProtectionMap.get(player);
        if (respawnProtection == null) {
            return;
        }

        try {
            respawnProtection.cancel();
        } catch (Exception ignored) {
        }

        respawnProtectionMap.remove(player);
    }

    @Override
    public boolean isProtectionActive(Player player) {
        return (respawnProtectionMap.containsKey(player));
    }

    public List<BedWarsPlayer> getPlayersWithoutVIP() {
        List<BedWarsPlayer> gamePlayerList = new ArrayList<>(this.players);
        gamePlayerList.removeIf(BedWarsPlayer::canJoinFullGame);

        return gamePlayerList;
    }

    public Inventory getFakeEnderChest(BedWarsPlayer player) {
        if (!fakeEnderChests.containsKey(player)) {
            fakeEnderChests.put(player, Bukkit.createInventory(player.as(Player.class), InventoryType.ENDER_CHEST));
        }
        return fakeEnderChests.get(player);
    }

    @Override
    public int getPostGameWaiting() {
        return this.postGameWaiting;
    }

    public void setPostGameWaiting(int time) {
        this.postGameWaiting = time;
    }

    @Override
    public String getCustomPrefix() {
        return customPrefix;
    }

    @Override
    public Component getCustomPrefixComponent() {
        return AdventureHelper.toComponentNullable(customPrefix);
    }

    @Override
    public @Nullable Location getLobbyPos1() {
        return lobbyPos1;
    }

    @Override
    public @Nullable Location getLobbyPos2() {
        return lobbyPos2;
    }

    public void setLobbyPos1(Location pos1) {
        lobbyPos1 = pos1;
    }

    public void setLobbyPos2(Location pos2) {
        lobbyPos2 = pos2;
    }

    @Override
    public boolean isInEditMode() {
        return AdminCommand.gc.containsKey(name);
    }

    public void setCustomPrefix(String customPrefix) {
        this.customPrefix = customPrefix;
    }

    @SuppressWarnings("unchecked")
    public <T extends GameElement> Optional<SavedGameElement<T>> getGameElement(Class<T> type, UUID uuid) {
        return elements.stream().filter(saved -> type.isInstance(saved.getGameElement()) && uuid.equals(saved.getUuid())).map(el -> (SavedGameElement<T>) el).findFirst();
    }

    @SuppressWarnings("unchecked")
    public <T extends GameElement> Optional<SavedGameElement<T>> getGameElement(Class<T> type, String globalKey) {
        return elements.stream().filter(saved -> type.isInstance(saved.getGameElement()) && globalKey.equals(saved.getGlobalKey()) && saved.isSynchronize()).map(el -> (SavedGameElement<T>) el).findFirst();
    }

    public <T extends GameElement> SavedGameElement<T> putGameElement(T element) {
        return putGameElement(element, null, false);
    }


    public <T extends GameElement> SavedGameElement<T> putGameElement(T element, @Nullable String globalKey) {
        return putGameElement(element, globalKey, true);
    }

    public <T extends GameElement> SavedGameElement<T> putGameElement(T element, @Nullable String globalKey, boolean synchronize) {
        var uuid = UUID.randomUUID();
        var savedGameElement = new SavedGameElement<T>();
        savedGameElement.setUuid(uuid);
        savedGameElement.setGameElement(element);
        savedGameElement.setGlobalKey(globalKey);
        savedGameElement.setSynchronize(synchronize);
        elements.add(savedGameElement);
        return savedGameElement;
    }
}
