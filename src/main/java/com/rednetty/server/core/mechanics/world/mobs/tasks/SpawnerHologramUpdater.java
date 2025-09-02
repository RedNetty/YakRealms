package com.rednetty.server.core.mechanics.world.mobs.tasks;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.spawners.MobSpawner;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

/**
 * Task for periodically updating spawner holograms
 */
public class SpawnerHologramUpdater extends BukkitRunnable {
    private static final int UPDATE_INTERVAL = 100; // 5 seconds (100 ticks)
    private static final int INITIAL_DELAY = 20;   // 1 second (20 ticks)

    private final MobSpawner spawnerManager;
    private final Logger logger;
    private final boolean debug;

    /**
     * Constructor
     */
    public SpawnerHologramUpdater() {
        this.spawnerManager = MobSpawner.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
        this.debug = YakRealms.getInstance().isDebugMode();
    }

    @Override
    public void run() {
    }

    /**
     * Start the hologram updater task
     */
    public static void startTask() {
        SpawnerHologramUpdater task = new SpawnerHologramUpdater();
        task.runTaskTimer(
                YakRealms.getInstance(),
                INITIAL_DELAY,
                UPDATE_INTERVAL
        );

        // Hologram update task started
    }
}