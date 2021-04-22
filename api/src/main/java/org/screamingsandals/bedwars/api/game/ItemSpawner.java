package org.screamingsandals.bedwars.api.game;

import org.screamingsandals.bedwars.api.team.Team;
import org.screamingsandals.bedwars.api.upgrades.Upgrade;
import org.bukkit.Location;

import java.util.Optional;

/**
 * @author Bedwars Team
 */
public interface ItemSpawner extends Upgrade {
    /**
     * @return
     */
    ItemSpawnerType getItemSpawnerType();

    /**
     * @return
     */
    Location getLocation();

    /**
     * @return
     */
    boolean hasCustomName();

    /**
     * @return
     */
    String getCustomName();

    /**
     * @return
     */
    double getStartLevel();

    /**
     * @return
     */
    double getCurrentLevel();

    /**
     * @return
     */
    boolean getHologramEnabled();

    /**
     * @return
     */
    boolean getFloatingEnabled();

    /**
     * Sets team of this upgrade
     *
     * @param team current team
     */
    void setTeam(Team team);

    /**
     *
     * @return registered team for this upgrade in optional or empty optional
     */
    Optional<Team> getTeam();

    /**
     * @param level
     */
    void setCurrentLevel(double level);

    default void addToCurrentLevel(double level) {
        setCurrentLevel(getCurrentLevel() + level);
    }

    default String getName() {
        return "spawner";
    }

    default String getInstanceName() {
        return getCustomName();
    }

    default double getLevel() {
        return getCurrentLevel();
    }

    default void setLevel(double level) {
        setCurrentLevel(level);
    }

    default void increaseLevel(double level) {
        addToCurrentLevel(level);
    }

    default double getInitialLevel() {
        return getStartLevel();
    }
}
