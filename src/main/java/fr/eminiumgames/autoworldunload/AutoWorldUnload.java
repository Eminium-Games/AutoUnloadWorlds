package fr.eminiumgames.autoworldunload;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoWorldUnload extends JavaPlugin implements Listener {

    private final Set<String> excludedWorlds = new HashSet<>();
    private boolean unloadChunks;
    private boolean unloadChunksInExcluded;
    private boolean unloadWorlds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        excludedWorlds.addAll(getConfig().getStringList("excluded-worlds"));
        unloadChunks = getConfig().getBoolean("unload-chunks", false);
        unloadChunksInExcluded = getConfig().getBoolean("unload-chunks-in-excluded", false);
        unloadWorlds = getConfig().getBoolean("unload-worlds", true);

        Bukkit.getPluginManager().registerEvents(this, this);

        // unload any world that has no players on startup
        for (World world : Bukkit.getWorlds()) {
            getLogger().info("Startup scan: evaluating world " + world.getName()
                    + " (" + world.getLoadedChunks().length + " chunks loaded)");
            // reuse the same check logic; it internally handles exclusions and scheduling
            checkWorld(world);
        }

        getLogger().info("AutoWorldUnload enabled. unloadWorlds=" + unloadWorlds
                + " unloadChunks=" + unloadChunks
                + " unloadChunksInExcluded=" + unloadChunksInExcluded);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        checkWorld(event.getPlayer().getWorld());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        checkWorld(event.getFrom());
    }

    private void checkWorld(World world) {
        String name = world.getName();
        boolean isExcluded = excludedWorlds.contains(name);
        if (isExcluded) {
            getLogger().fine("World " + name + " is excluded; will not unload");
        }

        int delaySeconds = getConfig().getInt("unload-delay-seconds", 60);
        getLogger().fine("Scheduling check for world " + name + " in " + delaySeconds + "s");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (world.getPlayers().isEmpty()) {
                getLogger().info("World " + name + " empty after delay (" + world.getLoadedChunks().length
                        + " chunks loaded)");

                if (!isExcluded && unloadWorlds) {
                    getLogger().info("Attempting to unload world " + name);
                    boolean success = Bukkit.unloadWorld(world, true);
                    if (success) {
                        getLogger().info("Unloaded world: " + name);
                    } else {
                        getLogger().warning("Failed to unload world: " + name);
                    }
                } else if (!unloadWorlds) {
                    getLogger().info("Global setting prevents world unload (unload-worlds=false)");
                } else {
                    getLogger().info("Excluded world " + name + " will not be unloaded");
                }

                // optionally unload chunks even if we didn't unload the world
                if (unloadChunks && (!isExcluded || unloadChunksInExcluded)) {
                    unloadLoadedChunks(world);
                }
            } else {
                getLogger().fine("World " + name + " has players, skipping unload");
            }
        }, 20L * delaySeconds);
    }

    /**
     * Unload every chunk currently loaded in the given world.  This is useful
     * when we cannot or do not unload the world itself (e.g. an excluded world)
     * but still want to reduce memory and the server's reported chunk count.
     */
    private void unloadLoadedChunks(World world) {
        int before = world.getLoadedChunks().length;
        if (before == 0) {
            getLogger().fine("No chunks to unload in world " + world.getName());
            return;
        }
        getLogger().info("Unloading " + before + " chunks from world " + world.getName());
        for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
            // false -> do not save before unloading
            world.unloadChunk(chunk.getX(), chunk.getZ(), false);
        }
        int after = world.getLoadedChunks().length;
        getLogger().info("Finished chunk unload for " + world.getName() + ": " + before + " -> " + after);
    }
}
